package com.seng403.mutanthunt.load;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seng403.mutanthunt.entity.*;
import com.seng403.mutanthunt.repository.*;
import com.seng403.mutanthunt.service.AdminService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Load test: 80 virtual students join and each submit 10 tests while the board is
 * connected via SSE. Verifies that the board's live scoreboard ends up complete and
 * correct (80 entries, 7/7 each, 10 tests each).
 * Run manually: mvn test -Pload
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("load")
class LoadTest {

    @LocalServerPort int port;
    @Autowired ObjectMapper om;
    @Autowired AdminService adminService;
    @Autowired SessionRepository sessionRepo;

    private String base;

    private static final int STUDENTS = 80;
    private static final int TESTS_PER_STUDENT = 10;

    @BeforeEach
    void setUp() {
        base = "http://localhost:" + port;
    }

    @Test @Order(1)
    void setupSessionAndRound() {
        // Use service directly (bypass CSRF issues)
        GameSession session = adminService.createSession("Load Test Session");
        adminService.activateSession(session.getId());
        adminService.updateSettings(session.getId(), Map.of("submissionRateLimitMs", "0"));
        Round round = adminService.createRound(session.getId(), "Load Round", "indexOf", 0, 0);
        adminService.openRound(round.getId());
    }

    /** Latest "scoreboard" event received by the simulated board. */
    private final AtomicReference<Map<String, Object>> lastBoardEvent = new AtomicReference<>();
    private final AtomicInteger boardEvents = new AtomicInteger();

    @Test @Order(2)
    void loadTest80Students10Tests() throws Exception {
        HttpClient boardClient = HttpClient.newHttpClient();
        HttpRequest sseReq = HttpRequest.newBuilder(URI.create(base + "/sse/board"))
            .header("Accept", "text/event-stream").build();
        HttpResponse<java.util.stream.Stream<String>> sse =
            boardClient.send(sseReq, HttpResponse.BodyHandlers.ofLines());
        assertEquals(200, sse.statusCode());
        assertEquals("no", sse.headers().firstValue("X-Accel-Buffering").orElse(null));
        Thread boardReader = Thread.ofVirtual().start(() -> {
            String event = null;
            for (java.util.Iterator<String> it = sse.body().iterator(); it.hasNext(); ) {
                String line = it.next();
                if (line.startsWith("event:")) event = line.substring(6).trim();
                else if (line.startsWith("data:") && "scoreboard".equals(event)) {
                    try {
                        lastBoardEvent.set(om.readValue(line.substring(5).trim(), new TypeReference<>() {}));
                        boardEvents.incrementAndGet();
                    } catch (Exception ignored) { }
                }
            }
        });

        ExecutorService pool = Executors.newFixedThreadPool(20);
        AtomicInteger successfulSubmissions = new AtomicInteger(0);
        AtomicInteger failedSubmissions = new AtomicInteger(0);
        AtomicInteger joinedStudents = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(STUDENTS);

        List<Map<String, String>> testCases = List.of(
            Map.of("list", "5", "target", "5", "expected", "0"),
            Map.of("list", "", "target", "3", "expected", "-1"),
            Map.of("list", "1,2,2", "target", "2", "expected", "1"),
            Map.of("list", "9,4", "target", "4", "expected", "1"),
            Map.of("list", "1000", "target", "1000", "expected", "0"),
            Map.of("list", "1,2,3", "target", "5", "expected", "-1"),
            Map.of("list", "1,2,3,4,5", "target", "3", "expected", "2"),
            Map.of("list", "10,20,30", "target", "10", "expected", "0"),
            Map.of("list", "7", "target", "7", "expected", "0"),
            Map.of("list", "3,1,4,1,5", "target", "1", "expected", "1")
        );

        long start = System.currentTimeMillis();

        for (int i = 0; i < STUDENTS; i++) {
            final int studentNum = i;
            pool.submit(() -> {
                try {
                    HttpClient studentClient = HttpClient.newBuilder()
                        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();

                    String joinBody = om.writeValueAsString(Map.of(
                        "fullName", "Student " + studentNum,
                        "studentNumber", String.format("%010d", studentNum)
                    ));

                    HttpRequest joinReq = HttpRequest.newBuilder()
                        .uri(URI.create(base + "/api/join"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(joinBody))
                        .build();

                    HttpResponse<String> joinRes = studentClient.send(joinReq,
                        HttpResponse.BodyHandlers.ofString());

                    if (joinRes.statusCode() == 200) {
                        joinedStudents.incrementAndGet();
                    } else {
                        System.err.println("Join failed for student " + studentNum + ": " + joinRes.statusCode());
                        latch.countDown();
                        return;
                    }

                    // Extract player_token from Set-Cookie header (Secure flag blocks CookieManager on HTTP)
                    String playerToken = null;
                    for (String header : joinRes.headers().allValues("set-cookie")) {
                        if (header.startsWith("player_token=")) {
                            playerToken = header.split(";")[0].substring("player_token=".length());
                            break;
                        }
                    }
                    if (playerToken == null) {
                        System.err.println("No player_token cookie for student " + studentNum);
                        latch.countDown();
                        return;
                    }

                    for (int t = 0; t < TESTS_PER_STUDENT; t++) {
                        Map<String, String> tc = testCases.get(t % testCases.size());
                        String submitBody = om.writeValueAsString(tc);

                        HttpRequest submitReq = HttpRequest.newBuilder()
                            .uri(URI.create(base + "/api/submit"))
                            .header("Content-Type", "application/json")
                            .header("Cookie", "player_token=" + playerToken)
                            .POST(HttpRequest.BodyPublishers.ofString(submitBody))
                            .build();

                        HttpResponse<String> submitRes = studentClient.send(submitReq,
                            HttpResponse.BodyHandlers.ofString());

                        if (submitRes.statusCode() == 200 && submitRes.body().contains("\"success\":true")) {
                            successfulSubmissions.incrementAndGet();
                        } else {
                            failedSubmissions.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Student " + studentNum + " error: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(120, TimeUnit.SECONDS), "Load test timed out");
        pool.shutdown();

        long elapsed = System.currentTimeMillis() - start;
        int total = STUDENTS * TESTS_PER_STUDENT;

        System.out.println("=== LOAD TEST RESULTS ===");
        System.out.println("Students joined: " + joinedStudents.get() + "/" + STUDENTS);
        System.out.println("Total submissions: " + total);
        System.out.println("Successful: " + successfulSubmissions.get());
        System.out.println("Failed: " + failedSubmissions.get());
        System.out.println("Time: " + elapsed + "ms");
        System.out.println("Throughput: " + String.format("%.1f", total * 1000.0 / elapsed) + " submissions/sec");

        // the board must converge to the final state shortly after the last submission
        long waitStart = System.currentTimeMillis();
        List<Map<String, Object>> entries = List.of();
        while (System.currentTimeMillis() - waitStart < 5000) {
            Map<String, Object> ev = lastBoardEvent.get();
            if (ev != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> e = (List<Map<String, Object>>) ev.get("entries");
                entries = e;
                if (entries.size() == STUDENTS && entries.stream().allMatch(x -> ((Number) x.get("testsUsed")).intValue() == TESTS_PER_STUDENT)) break;
            }
            Thread.sleep(100);
        }
        long convergeMs = System.currentTimeMillis() - waitStart;
        boardReader.interrupt();
        System.out.println("Board SSE scoreboard events: " + boardEvents.get()
            + ", converged " + convergeMs + "ms after the last submission");

        assertEquals(STUDENTS, entries.size(), "board scoreboard must list every student");
        assertTrue(entries.stream().allMatch(x -> ((Number) x.get("score")).intValue() == 7),
            "every student killed all 7 mutants");
        assertTrue(entries.stream().allMatch(x -> ((Number) x.get("testsUsed")).intValue() == TESTS_PER_STUDENT));
        assertFalse(entries.stream().anyMatch(x -> x.containsKey("participantId")), "no internal ids on the board");

        assertEquals(STUDENTS, joinedStudents.get(), "every student should join");
        assertEquals(total, successfulSubmissions.get(), "failed submissions: " + failedSubmissions.get());
    }
}
