# 🧬 Mutant Hunt

**SENG 403 – Software Quality Assurance & Testing**  
Live in-class mutation testing game where students design test cases to hunt down hidden mutants.

## Quick Start

```bash
# Build
mvn clean package -DskipTests

# Run
ADMIN_PASSWORD=YourSecretPassword java -jar target/mutant-hunt.jar

# With Cloudflare Tunnel (instant public URL)
cloudflared tunnel --url http://localhost:8080
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `ADMIN_PASSWORD` | `changeme` | Password for the admin panel (`/admin`) |
| `PUBLIC_BASE_URL` | `http://localhost:8080` | Public URL shown in QR code (set to your tunnel URL) |
| `PORT` | `8080` | Server port |
| `DATA_DIR` | `./data` | Directory for the H2 database file |
| `STUDENT_ID_LENGTH` | `10` | Default required student number length (0 = any) |
| `RATE_LIMIT_MS` | `2000` | Default submission cooldown per student (ms) |

## URLs

| URL | Purpose |
|---|---|
| `/` | Student page (mobile-first) |
| `/board` | Projector board – QR code + live scoreboard |
| `/admin` | Admin panel (password-protected) |

## Architecture

- **Java 21 + Spring Boot 3.3** – single executable JAR
- **H2 file database** – persistent, zero-config
- **Server-Sent Events** – live scoreboard, no WebSocket complexity
- **Vanilla JS + CSS** – no build step, no CDN dependency
- **ZXing** – QR code generation

## Instructor Guide

### Preparation

1. Set `ADMIN_PASSWORD` and `PUBLIC_BASE_URL` environment variables
2. Start the server: `java -jar mutant-hunt.jar`
3. Start a tunnel: `cloudflared tunnel --url http://localhost:8080`
4. Copy the tunnel URL and set it as `PUBLIC_BASE_URL` (or restart with it)
5. Go to `/admin`, log in, create a session, and activate it

### In-Class Flow

**Round 1 – Discovery (10 tests)**
1. Create a round: name it "Round 1", set test limit to **10**
2. Project `/board` on the screen – students scan the QR code
3. Open the round – students see the test form on their phones
4. Watch submissions flow in real-time on the admin panel
5. After ~10 minutes, **close** the round
6. Discuss: "Who scored 7/7? Who scored less? Why?"
7. **Reveal** – students see which mutants their tests killed

**Round 2 – Constrained (4 tests)**
1. Create a new round with test limit **4**
2. Challenge: "Can you kill all 7 mutants with just 4 tests?"
3. Students must think more carefully about test case design
4. Close → Reveal → Discuss the concept of **mutation score**

**Round 3 – Systematic (unlimited)**
1. Teach CFG, edge-pair coverage, and model-driven test design
2. Create a round with **unlimited** tests
3. Students apply systematic techniques
4. Compare scores across rounds

### Key Teaching Moments

- **INVALID tests**: "Your expected value is wrong – your oracle failed!"  
  → Teach the **RIPR model** (Reachability, Infection, Propagation, Revealability)
  
- **M7 (reference-equality)**: Integer cache bug only triggered with values > 127  
  → Teach boundary value analysis and Java-specific pitfalls

- **M3 (last-occurrence)**: Only caught by tests with duplicate values  
  → Teach the importance of equivalence class coverage

### Admin Features

- **Anonymity modes**: Full Name, Nickname, Anonymous, Masked Number
- **Live submission feed**: Watch tests arrive in real-time
- **Analytics**: Mutant kill rates, average scores, invalid test ratios
- **CSV export**: Participants + scores and all submissions (UTF-8 BOM for Excel)
- **Board control**: Toggle QR, fullscreen scoreboard from admin panel

## Building from Source

```bash
# Requires Java 21+ and Maven 3.8+
mvn clean package

# Run tests
mvn test

# The unit tests verify the kill matrix:
# [5], 5, 0       → kills M1, M2, M4
# [], 3, -1        → kills M5
# [1,2,2], 2, 1   → kills M3, M4
# [9,4], 4, 1     → kills M2, M4, M6
# [1000], 1000, 0 → kills M1, M2, M4, M7
# [1,2,3], 5, -1  → kills M5
# [5], 5, 1       → INVALID (oracle failure)
```

## Adding New Problems

Implement the `Problem` interface and register it in `ProblemRegistry`:

```java
public class MyProblem implements Problem {
    // getId(), getTitle(), getSpecification(), ...
    // getMutants() – define mutant implementations
    // executeReference() / executeMutant() – run test cases
}
```

## Security

- Admin panel is password-protected with brute-force protection (5 attempts → 15min IP block)
- No student code is executed – only integer values are parsed
- Input validation: max 20 list elements, Java int range, server-side enforcement
- Rate limiting: configurable per-student submission cooldown
- CSRF protection on admin forms
- httpOnly + Secure + SameSite=Lax cookies

## License

Academic use – SENG 403, Software Quality Assurance & Testing.
