<div align="center">

# 🧬 Mutant Hunt

**A live, in-class mutation testing game for Software Testing courses**

Students design test cases on their phones → the server runs them against hidden faulty versions (mutants) → a live scoreboard is projected on the classroom screen.

Built for **SENG 403 – Software Quality Assurance & Testing**, but works for any introductory testing course.

[![Java 21](https://img.shields.io/badge/Java-21-orange)](#prerequisites)
[![Spring Boot 3.3](https://img.shields.io/badge/Spring%20Boot-3.3-green)](#architecture)
[![Tests: 277](https://img.shields.io/badge/tests-277%20passing-brightgreen)](#testing)
[![License: Unlicense](https://img.shields.io/badge/license-Unlicense-blue)](#license)

</div>

---

## Table of Contents

- [What Is This?](#what-is-this)
- [Pedagogical Goals](#pedagogical-goals)
- [Quick Start](#quick-start)
- [Screenshots & Flow](#screenshots--flow)
- [Environment Variables](#environment-variables)
- [Instructor Guide](#instructor-guide)
  - [Before Class](#before-class)
  - [Round 1 — Discovery (10 tests)](#round-1--discovery-10-tests)
  - [Round 2 — Constrained (4 tests)](#round-2--constrained-4-tests)
  - [Round 3 — Systematic (unlimited)](#round-3--systematic-unlimited)
  - [After Class](#after-class)
- [The indexOf Problem & Mutants](#the-indexof-problem--mutants)
- [Game Mechanics & Rules](#game-mechanics--rules)
- [Screens Overview](#screens-overview)
- [Architecture](#architecture)
- [Adding Problems](#adding-problems)
- [Internationalization (i18n)](#internationalization-i18n)
- [Security](#security)
- [Testing](#testing)
- [Deployment Options](#deployment-options)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)

---

## What Is This?

Mutant Hunt is a web application that turns **mutation testing** into an interactive classroom competition. The instructor projects a QR code; students scan it on their phones, join the game, and start submitting test cases for a given function. The server silently runs each test against the correct implementation *and* seven hidden faulty versions ("mutants"). A live scoreboard shows who has killed the most mutants.

The twist: students don't see *which* mutants they killed until the instructor reveals results. This creates a powerful "aha!" moment — "I wrote 10 tests but only killed 3 mutants?!" — which sets up the lecture on systematic test design beautifully.

**No student code is ever executed.** Students only enter input values and expected outputs through a simple mobile form.

## Pedagogical Goals

| Moment | Concept taught |
|---|---|
| Student writes a test with wrong expected value → **INVALID** | **Test oracle** problem |
| 10 tests but only 3/7 mutants killed | **RIPR model** (Reachability, Infection, Propagation, Revealability) |
| M7 (`Integer ==` bug) only triggered by values > 127 | **Boundary value analysis**, language-specific pitfalls |
| M3 (last-occurrence) only caught with duplicate values | **Equivalence class** partitioning |
| Round 2 forces 4 tests → same score as 10 | **Coverage criteria** eliminate redundancy |
| Round 3 after CFG lecture → near-perfect scores | **Model-driven test design** works |
| Reveal phase: side-by-side comparison of all rounds | **Quantitative evidence** that systematic > ad-hoc |

## Quick Start

### Prerequisites

- **Java 21** or newer ([download](https://adoptium.net/))
- **Maven 3.9+** ([download](https://maven.apache.org/download.cgi))
- Optionally: [`cloudflared`](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/) for instant HTTPS tunnel

### Build & Run

```bash
# Clone
git clone https://github.com/alarmsdk/mutant-hunt.git
cd mutant-hunt

# Build (runs 277 tests)
mvn clean package

# Run
ADMIN_PASSWORD='choose-something-strong' \
PUBLIC_BASE_URL='https://your-tunnel.trycloudflare.com' \
java -jar target/mutant-hunt.jar
```

### With Cloudflare Tunnel (recommended for class)

```bash
# Terminal 1: start the app
ADMIN_PASSWORD=secret java -jar target/mutant-hunt.jar

# Terminal 2: create a tunnel (prints a public URL)
cloudflared tunnel --url http://localhost:8080

# Copy the https://….trycloudflare.com URL and restart:
ADMIN_PASSWORD=secret PUBLIC_BASE_URL='https://abc-xyz.trycloudflare.com' \
java -jar target/mutant-hunt.jar
```

> **Tip:** Use a [named Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/get-started/) with a fixed hostname so the URL never changes between lectures.

## Screenshots & Flow

```
  ┌─────────────┐    QR scan     ┌──────────────┐
  │  PROJECTOR   │ ◀──────────── │   STUDENT     │
  │  /board      │               │   / (phone)   │
  │              │               │               │
  │ ┌──────────┐ │               │ ┌───────────┐ │
  │ │ QR Code  │ │               │ │ Join Form │ │
  │ └──────────┘ │               │ └───────────┘ │
  │ ┌──────────┐ │   SSE live    │ ┌───────────┐ │
  │ │Scoreboard│ │◀──────────────│ │ Test Form │ │
  │ └──────────┘ │               │ └───────────┘ │
  │ ┌──────────┐ │               │ ┌───────────┐ │
  │ │ Mutants  │ │               │ │  Results  │ │
  │ └──────────┘ │               │ └───────────┘ │
  └─────────────┘               └──────────────┘
         ▲                              ▲
         │          SSE live            │
  ┌──────┴──────┐                       │
  │   ADMIN     │───────────────────────┘
  │   /admin    │    manages rounds,
  │             │    settings, reveals
  └─────────────┘
```

| URL | Who | Purpose |
|---|---|---|
| `/` | Students (phone) | Join, submit tests, see own score, revealed results |
| `/board` | Projector (no login) | QR code, live scoreboard, timer, mutant panel |
| `/admin` | Instructor (password) | Sessions, rounds, settings, participants, live feed, analytics, CSV export |

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `ADMIN_PASSWORD` | `changeme` | Password for `/admin`. **Always set this.** |
| `PUBLIC_BASE_URL` | `http://localhost:8080` | The URL encoded in the QR code and shown on the board. Set to your tunnel/domain URL. |
| `PORT` | `8080` | HTTP listen port. |
| `DATA_DIR` | `./data` | Directory for the H2 database file. All data survives restarts. |
| `STUDENT_ID_LENGTH` | `10` | Default required student-number length for new sessions (0 = any length). |
| `RATE_LIMIT_MS` | `2000` | Default per-student submission cooldown in milliseconds. |

## Instructor Guide

### Before Class

1. **Start** the app and the Cloudflare tunnel; set `PUBLIC_BASE_URL` to the tunnel URL.
2. Open `/admin` → **Sessions** → create e.g. *"Week 2 – Section A"* → **Activate**.
3. **Settings**: choose board display mode (full name / nickname / anonymous / masked number), student-number length, and mutant panel visibility (start with **Hidden**).
4. **Rounds**: prepare three DRAFT rounds:

   | Round | Test Limit | Purpose |
   |---|---|---|
   | Round 1 | 10 | Discovery — lots of tests, few kills |
   | Round 2 | 4 | Constraint — forces careful selection |
   | Round 3 | 0 (unlimited) | After systematic test design lecture |

5. Open `/board` on the projector. Press **Q** for a large QR code while students join.

### Round 1 — Discovery (10 tests)

1. **Open** the round — students' phones switch to the test form automatically (via SSE).
2. Watch the **Submissions** tab in admin — tests arrive in real-time.
3. Expect many **INVALID** tests (wrong expected value). Use them to discuss the **test oracle** problem.
4. **Close** the round after ~8 minutes (or set a timer when creating the round).
5. *Before revealing*, ask: **"How many of you used all 10 tests? How many mutants did you kill?"**
6. **Reveal** — students see per-test kill details; discuss the **RIPR model**.
7. Switch the mutant panel to **Kill Counts** to see which mutants were hardest (usually M3 and M7).

### Round 2 — Constrained (4 tests)

1. **Open** with only 4 tests allowed.
2. A perfect 7/7 score is possible with just 4 tests:

   | list | target | expected | Kills |
   |---|---|---|---|
   | *(empty)* | 3 | -1 | M5 |
   | 1000 | 1000 | 0 | M1, M2, M4, M7 |
   | 1,2,2 | 2 | 1 | M3, M4 |
   | 9,4 | 4 | 1 | M2, M4, M6 |

3. **Reveal** and compare best 4-test suites. Introduce **coverage criteria** (statement → branch → edge-pair).

### Round 3 — Systematic (unlimited)

1. Draw the **control flow graph** of `indexOf` on the board.
2. Derive **edge-pair / prime-path** test requirements.
3. Students convert requirements into test cases — model-driven test design.
4. **Open** with unlimited tests. Ties are broken by *fewer tests used*, so systematic suites win.
5. **Reveal** and compare with Rounds 1 & 2 in **Analytics**.

### After Class

- **Export** → CSV for the session or individual rounds:
  - *Participants + Scores*: name, student number, score per round
  - *All Submissions*: every test with reference result, validity, per-mutant output and kill status
- Files are **UTF-8 with BOM** so Turkish characters display correctly in Excel.
- INVALID tests are preserved for analysis (common misconceptions about the function).

### Board Keyboard Shortcuts

| Key | Action |
|---|---|
| **Q** | Large QR code view |
| **S** | Normal view (QR + scoreboard) |
| **F** | Fullscreen scoreboard only |

These can also be triggered from **Admin → Board & Export**.

## The indexOf Problem & Mutants

### Specification

```java
/**
 * Returns the index of the first occurrence of target in list,
 * or -1 if target is not present.
 */
int indexOf(List<Integer> list, int target)
```

### The Seven Mutants

Each mutant differs from the correct implementation in exactly one place:

| ID | Name | Mutation | What it breaks |
|---|---|---|---|
| **M1** | `start-at-one` | Loop starts at `i = 1` instead of `i = 0` | First element is never checked |
| **M2** | `skip-last` | Loop condition `i < list.size() - 1` | Last element is never checked |
| **M3** | `last-occurrence` | Returns last match index instead of first | Only caught with duplicate values |
| **M4** | `index-plus-one` | Returns `i + 1` instead of `i` | Off-by-one in the result |
| **M5** | `zero-not-found` | Returns `0` instead of `-1` when not found | Confuses "not found" with index 0 |
| **M6** | `greater-equal` | Uses `>=` instead of `==` | Matches the first value ≥ target |
| **M7** | `reference-equality` | Simulates Java `Integer ==` bug: only matches when equal AND in [-128, 127] | Values > 127 are "invisible" |

> M7 is deliberately **simulated** (not dependent on JVM cache settings) so behavior is 100% reproducible.

### Kill Matrix

This table is verified by unit tests:

| Test: `list`, `target`, `expected` | Ref | M1 | M2 | M3 | M4 | M5 | M6 | M7 |
|---|---|---|---|---|---|---|---|---|
| `[5], 5, 0` | 0 | **-1** ☠ | **-1** ☠ | 0 | **1** ☠ | 0 | 0 | 0 |
| `[], 3, -1` | -1 | -1 | -1 | -1 | -1 | **0** ☠ | -1 | -1 |
| `[1,2,2], 2, 1` | 1 | 1 | 1 | **2** ☠ | **2** ☠ | 1 | 1 | 1 |
| `[9,4], 4, 1` | 1 | 1 | **-1** ☠ | 1 | **2** ☠ | 1 | **0** ☠ | 1 |
| `[1000], 1000, 0` | 0 | **-1** ☠ | **-1** ☠ | 0 | **1** ☠ | 0 | 0 | **-1** ☠ |
| `[1,2,3], 5, -1` | -1 | -1 | -1 | -1 | -1 | **0** ☠ | -1 | -1 |
| `[5], 5, 1` | 0 ≠ 1 | — | — | — | — | — | — | — |

☠ = killed (output ≠ expected). Last row is **INVALID** (student's oracle is wrong).

## Game Mechanics & Rules

- **A test** consists of three fields: `list` (comma-separated integers, may be empty, ≤ 20 elements), `target` (integer), and `expected` (integer). All values must be in Java `int` range.
- The server runs the **reference implementation** first. If `expected ≠ reference result`, the test is **INVALID** — it's stored and counts toward the test limit, but earns zero points. The student sees: *"Your expected value is wrong — your oracle failed!"*
- A **VALID** test is run against all mutants. A mutant is **killed** if its result differs from `expected` or it throws an exception.
- **Score** = number of distinct mutants killed by the student's valid tests in the current round.
- **Tiebreakers**: (1) fewer total tests used, (2) reached the score earlier.
- Students see only their own score and VALID/INVALID status until the round is **REVEALED**.
- One round can be **OPEN** per session at a time. Round lifecycle: `DRAFT → OPEN → CLOSED → REVEALED`.
- Time limits auto-close the round when the countdown expires.

## Screens Overview

### Student (`/`)
- **Join form**: full name, student number (numeric, length validated), optional nickname (when anonymity mode is NICKNAME)
- **Waiting screen**: shown between rounds, auto-transitions via SSE
- **Test form**: specification, input fields with numeric keyboards, submit button, remaining tests counter, score, submission history
- **Revealed**: per-test mutant kill details with mutant descriptions

### Board (`/board`)
- **QR code**: generated from `PUBLIC_BASE_URL` via ZXing; URL shown as text below
- **Live scoreboard**: top 10 shown, "+N more" for rest; SSE updates
- **Timer**: countdown when round has time limit
- **Mutant panel**: configurable — Hidden / Kill Counts / Full Reveal
- **Participant counter**: live count of connected students

### Admin (`/admin`)
- **Sessions**: create, activate, archive, delete (with confirmation)
- **Rounds**: create with problem, test limit, time limit; open/close/reveal lifecycle
- **Settings**: anonymity mode, join open/closed, student ID length, mutant panel, rate limit — all take effect immediately
- **Participants**: full list with names, numbers, display names; kick or rename
- **Submissions**: live feed of all tests; filter by round, student, validity
- **Analytics**: per-mutant kill rates, average score, invalid test rate, bar charts
- **Board & Export**: board display commands; CSV export (participants+scores, all submissions)

## Architecture

```
mutant-hunt/
├── pom.xml                          # Maven (Spring Boot 3.3, Java 21)
├── src/main/java/com/seng403/mutanthunt/
│   ├── MutantHuntApplication.java   # Entry point
│   ├── config/SecurityConfig.java   # Admin auth, CSRF, brute-force filter
│   ├── problem/                     # Problem interface + indexOf implementation
│   │   ├── Problem.java             # Interface for adding new problems
│   │   ├── IndexOfProblem.java      # Reference + 7 mutant implementations
│   │   └── ProblemRegistry.java     # Registration of available problems
│   ├── entity/                      # JPA entities (GameSession, Round, Participant, Submission, MutantResult)
│   ├── repository/                  # Spring Data JPA repositories
│   ├── service/                     # Business logic
│   │   ├── GameService.java         # Join, submit, score computation, notifications
│   │   ├── AdminService.java        # Session/round/participant management
│   │   ├── SseService.java          # Server-Sent Events broadcasting
│   │   ├── ExportService.java       # CSV export with BOM
│   │   ├── RateLimitService.java    # Per-student cooldown + login brute-force
│   │   └── QrCodeService.java       # ZXing QR generation
│   ├── controller/                  # HTTP endpoints
│   │   ├── StudentApiController.java # /api/join, /api/submit, /api/status
│   │   ├── AdminApiController.java   # /admin/api/* (REST)
│   │   ├── SseController.java        # /sse/student, /sse/board, /sse/admin
│   │   └── PageController.java       # HTML page routes
│   └── dto/                         # Response records
├── src/main/resources/
│   ├── application.properties       # Configuration
│   ├── messages.properties          # All UI strings (English)
│   ├── static/css/style.css         # Dark theme, mobile-first
│   ├── static/js/{student,board,admin,i18n}.js
│   └── templates/{student,board,admin,admin-login}.html
├── src/test/java/                   # 277 tests
│   ├── problem/IndexOfProblemTest.java    # Kill matrix verification
│   ├── integration/                       # Full API flow tests
│   ├── load/LoadTest.java                 # 80 students × 10 tests
│   └── ...                                # Service, controller, entity tests
└── docs/mutant-guide.html           # Standalone mutant reference (printable)
```

### Key Technology Choices

| Concern | Choice | Rationale |
|---|---|---|
| Language | Java 21 | Course teaches Java-based testing |
| Framework | Spring Boot 3.3 | Single JAR, batteries included |
| Database | H2 (file mode) | Zero setup, survives restarts, portable |
| Frontend | Vanilla JS + CSS | No build step, no CDN, works offline |
| Real-time | Server-Sent Events | Simpler than WebSocket, works through proxies |
| QR Code | ZXing | Pure Java, no external service |
| Templates | Thymeleaf | Server-side rendering, i18n integration |

## Adding Problems

Implement the `Problem` interface and register it:

```java
public class MyProblem implements Problem {
    @Override public String getId() { return "myProblem"; }
    @Override public String getTitle() { return "My Problem"; }
    @Override public String getSpecification() { return "<p>...</p>"; }
    @Override public List<InputFieldDef> getFunctionInputFields() { /* ... */ }
    @Override public InputFieldDef getExpectedField() { /* ... */ }
    @Override public List<MutantDefinition> getMutants() { /* ... */ }
    @Override public void validate(Map<String, String> rawInputs) { /* ... */ }
    @Override public String executeReference(Map<String, String> rawInputs) { /* ... */ }
    @Override public ExecutionResult executeMutant(String mutantId, Map<String, String> rawInputs) { /* ... */ }
}
```

Register in `ProblemRegistry`:
```java
public ProblemRegistry() {
    register(new IndexOfProblem());
    register(new MyProblem());  // ← add here
}
```

The admin "New Round" form automatically lists all registered problems.

## Internationalization (i18n)

All UI text lives in `src/main/resources/messages.properties`. To add Turkish:

1. Copy `messages.properties` → `messages_tr.properties`
2. Translate the values (keys stay the same)
3. Browsers that send `Accept-Language: tr` get Turkish automatically

Both Thymeleaf templates and JavaScript share the same message keys.

## Security

| Layer | Protection |
|---|---|
| Admin login | BCrypt password, brute-force protection (5 attempts in 5 min → 15 min IP block) |
| CSRF | Tokens on all admin forms and API calls |
| Student input | No code execution — only integers parsed; length/range/count validation server-side |
| Rate limiting | Configurable per-student submission cooldown (default 2s) |
| Test limits | Enforced server-side with row locking |
| Data privacy | Student numbers/names only visible in admin panel and exports, never on board or to other students |
| Cookies | `HttpOnly`, `Secure`, `SameSite=Lax` |
| Proxy headers | `X-Forwarded-*` honored (`server.forward-headers-strategy=framework`) |
| CSV injection | Export neutralizes spreadsheet formula characters (`=`, `+`, `-`, `@`) in names |

## Testing

```bash
# Run all tests (unit + integration, excludes load test)
mvn test

# Run load test separately (80 students × 10 tests)
mvn test -Pload

# Generate coverage report (after mvn test)
open target/site/jacoco/index.html
```

### Test Breakdown (277 tests)

| Category | Tests | What's covered |
|---|---|---|
| Kill matrix | 23 | Reference + all 7 mutants against spec's exact kill matrix |
| Input parsing | 8 | Edge cases: empty, overflow, too many elements, non-numeric |
| Game rules integration | 15 | Join → submit → score → close → reveal → export API flow |
| Admin API | 22 | Session/round CRUD, settings, participant management |
| Controller security | 12 | Auth, CSRF, rate limiting, unauthorized access |
| Services | 50+ | GameService, AdminService, ExportService, SseService, etc. |
| Entities & DTOs | 30+ | Constructors, getters, equality, masked numbers |
| Load test | 2 | 80 concurrent students, 800 submissions, SSE connectivity |

## Deployment Options

### Option 1: Cloudflare Quick Tunnel (easiest)

```bash
ADMIN_PASSWORD=secret java -jar mutant-hunt.jar &
cloudflared tunnel --url http://localhost:8080
```

- Free, instant, no account needed
- URL changes each time (use a named tunnel for stability)

### Option 2: Named Cloudflare Tunnel (recommended)

```bash
# One-time setup
cloudflared tunnel create mutant-hunt
cloudflared tunnel route dns mutant-hunt mutant.yourdomain.com

# Run
cloudflared tunnel run mutant-hunt &
ADMIN_PASSWORD=secret PUBLIC_BASE_URL=https://mutant.yourdomain.com \
java -jar mutant-hunt.jar
```

### Option 3: Docker

```dockerfile
FROM eclipse-temurin:21-jre
COPY target/mutant-hunt.jar /app/mutant-hunt.jar
WORKDIR /app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "mutant-hunt.jar"]
```

```bash
docker build -t mutant-hunt .
docker run -p 8080:8080 \
  -e ADMIN_PASSWORD=secret \
  -e PUBLIC_BASE_URL=https://your-domain.com \
  -v mutant-hunt-data:/app/data \
  mutant-hunt
```

### Option 4: University Server

```bash
# Run as a systemd service
sudo cp mutant-hunt.service /etc/systemd/system/
sudo systemctl enable --now mutant-hunt
```

Example `mutant-hunt.service`:
```ini
[Unit]
Description=Mutant Hunt – SENG 403 Game
After=network.target

[Service]
Type=simple
User=www-data
Environment=ADMIN_PASSWORD=secret
Environment=PUBLIC_BASE_URL=https://mutant.cs.university.edu
Environment=DATA_DIR=/var/lib/mutant-hunt
ExecStart=/usr/bin/java -jar /opt/mutant-hunt/mutant-hunt.jar
Restart=always

[Install]
WantedBy=multi-user.target
```

## Troubleshooting

| Problem | Solution |
|---|---|
| QR code shows `localhost` | Set `PUBLIC_BASE_URL` to your tunnel/domain URL |
| Students can't connect | Check firewall allows the port; verify tunnel is running |
| SSE disconnects frequently | Normal behind some proxies; clients reconnect automatically |
| Board scoreboard doesn't update | Check SSE connection (browser console); board auto-reconnects |
| "No active session" on student page | Admin must create and **activate** a session first |
| Timer doesn't auto-close round | Timer auto-close has a 2s grace period for network lag |
| CSV has garbled characters in Excel | Files include UTF-8 BOM — use "Open with encoding" if needed |
| Admin login blocked | Brute-force protection: wait 15 minutes or restart the app |

## Contributing

Contributions are welcome! Some ideas:

- **New problems**: `binarySearch`, `isPrime`, `sort` — implement `Problem` interface
- **Team mode**: students form teams, aggregate scores
- **More analytics**: graphs, round-to-round comparisons, exportable charts
- **i18n**: add `messages_tr.properties`, `messages_de.properties`, etc.
- **Accessibility**: screen reader improvements, high-contrast theme
- **Pre-built releases**: GitHub Actions to build and publish JARs

To contribute:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/new-problem`)
3. Commit your changes
4. Open a Pull Request

## Acknowledgments

- Designed for **SENG 403 – Software Quality Assurance & Testing**
- Inspired by mutation testing frameworks like [PIT](https://pitest.org/) and [Stryker](https://stryker-mutator.io/)
- Built with [Spring Boot](https://spring.io/projects/spring-boot), [ZXing](https://github.com/zxing/zxing), [H2 Database](https://h2database.com/)

## License

This is free and unencumbered software released into the public domain.

Anyone is free to copy, modify, publish, use, compile, sell, or distribute this software, either in source code form or as a compiled binary, for any purpose, commercial or non-commercial, and by any means.

See [UNLICENSE](UNLICENSE) for the full text.

---

<div align="center">

**Made with 🧬 for software testing education**

*If you use Mutant Hunt in your course, we'd love to hear about it! Open an issue to share your experience.*

</div>
