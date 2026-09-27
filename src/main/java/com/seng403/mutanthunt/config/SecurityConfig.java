package com.seng403.mutanthunt.config;

import com.seng403.mutanthunt.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(
            @Value("${app.admin-password}") String adminPassword,
            PasswordEncoder encoder) {
        var admin = User.withUsername("admin")
            .password(encoder.encode(adminPassword))
            .roles("ADMIN")
            .build();
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                            LoginRateLimitFilter loginRateLimitFilter) throws Exception {
        http
            .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin/login", "/admin/login/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/sse/admin").hasRole("ADMIN")
                .anyRequest().permitAll()
            )
            .formLogin(form -> form
                .loginPage("/admin/login")
                .loginProcessingUrl("/admin/login")
                .defaultSuccessUrl("/admin", true)
                .failureUrl("/admin/login?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/admin/logout")
                .logoutSuccessUrl("/admin/login?logout")
                .permitAll()
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**", "/sse/**")
            );

        return http.build();
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
                String ip = getClientIp(request);
                if (rateLimitService.isLoginBlocked(ip)) {
                    response.sendRedirect("/admin/login?blocked");
                    return;
                }
            }
            filterChain.doFilter(request, response);
        }

        private String getClientIp(HttpServletRequest request) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
            return request.getRemoteAddr();
        }
    }

    @Component
    static class AuthEventListener {
        private final RateLimitService rateLimitService;

        AuthEventListener(RateLimitService rateLimitService) {
            this.rateLimitService = rateLimitService;
        }

        @EventListener
        public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
            if (event.getAuthentication().getDetails() instanceof WebAuthenticationDetails details) {
                rateLimitService.recordLoginFailure(details.getRemoteAddress());
            }
        }

        @EventListener
        public void onSuccess(AuthenticationSuccessEvent event) {
            if (event.getAuthentication().getDetails() instanceof WebAuthenticationDetails details) {
                rateLimitService.resetLoginFailures(details.getRemoteAddress());
            }
        }
    }
}
