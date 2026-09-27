package com.seng403.mutanthunt.config;

import com.seng403.mutanthunt.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    static final String DEFAULT_PASSWORD = "changeme";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(
            @Value("${app.admin-password}") String adminPassword,
            PasswordEncoder encoder) {
        if (DEFAULT_PASSWORD.equals(adminPassword)) {
            log.warn("**************************************************************");
            log.warn("* ADMIN_PASSWORD is not set – using the default 'changeme'.  *");
            log.warn("* Set ADMIN_PASSWORD before exposing the app via a tunnel!   *");
            log.warn("**************************************************************");
        }
        var admin = User.withUsername("admin")
            .password(encoder.encode(adminPassword))
            .roles("ADMIN")
            .build();
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           LoginRateLimitFilter loginRateLimitFilter,
                                           RateLimitService rateLimitService) throws Exception {
        var adminApi = new OrRequestMatcher(
            new AntPathRequestMatcher("/admin/api/**"), new AntPathRequestMatcher("/sse/admin"));
        var apiEntryPoint = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
        var loginEntryPoint = new LoginUrlAuthenticationEntryPoint("/admin/login");
        http
            .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin/login").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/sse/admin").hasRole("ADMIN")
                .anyRequest().permitAll()
            )
            .formLogin(form -> form
                .loginPage("/admin/login")
                .loginProcessingUrl("/admin/login")
                .successHandler((req, res, auth) -> {
                    rateLimitService.resetLoginFailures(ClientIp.of(req));
                    res.sendRedirect(req.getContextPath() + "/admin");
                })
                .failureHandler((req, res, ex) -> {
                    String ip = ClientIp.of(req);
                    rateLimitService.recordLoginFailure(ip);
                    log.warn("Failed admin login from {}", ip);
                    res.sendRedirect(req.getContextPath() + "/admin/login?error");
                })
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/admin/logout")          // POST with CSRF token
                .logoutSuccessUrl("/admin/login?logout")
                .permitAll()
            )
            // JSON/SSE calls from the admin page get 401 instead of an HTML login redirect
            .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, e) -> {
                if (adminApi.matches(req)) apiEntryPoint.commence(req, res, e);
                else loginEntryPoint.commence(req, res, e);
            }))
            // student endpoints are cookie-token based and carry no admin authority;
            // all admin forms and /admin/api calls require the CSRF token
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/sse/**"));

        return http.build();
    }

    /**
     * Client IP for brute-force protection. Behind Cloudflare the edge overwrites
     * {@code CF-Connecting-IP}, whereas the first {@code X-Forwarded-For} entry can be
     * forged by the client.
     */
    static final class ClientIp {
        private ClientIp() {}

        static String of(HttpServletRequest request) {
            String cf = request.getHeader("CF-Connecting-IP");
            if (cf != null && !cf.isBlank()) return cf.trim();
            return request.getRemoteAddr();
        }
    }

    @Component
    static class LoginRateLimitFilter extends OncePerRequestFilter {
        private final RateLimitService rateLimitService;

        LoginRateLimitFilter(RateLimitService rateLimitService) {
            this.rateLimitService = rateLimitService;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            if ("/admin/login".equals(request.getServletPath()) && "POST".equals(request.getMethod())) {
                String ip = ClientIp.of(request);
                if (rateLimitService.isLoginBlocked(ip)) {
                    long minutes = Math.max(1, (rateLimitService.getBlockRemainingSeconds(ip) + 59) / 60);
                    response.sendRedirect(request.getContextPath() + "/admin/login?blocked=" + minutes);
                    return;
                }
            }
            filterChain.doFilter(request, response);
        }
    }
}
