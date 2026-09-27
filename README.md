# 🧬 Mutant Hunt

**SENG 403 – Software Quality Assurance & Testing**
A live, in-class mutation-testing competition. Students design test cases for `indexOf` on their phones;
the server runs every test against hidden faulty versions (mutants) and a live scoreboard is projected on the board.

| URL | Who | Purpose |
|---|---|---|
| `/` | students (phone) | join, submit tests, see own score; results after reveal |
| `/board` | projector (no login) | QR code, live scoreboard, timer, mutant panel |
| `/admin` | instructor (password) | sessions, rounds, settings, participants, live submissions, analytics, CSV |

## Quick start

```bash
# Build (Java 21+, Maven 3.9+) – produces target/mutant-hunt.jar
mvn clean package

# Run
ADMIN_PASSWORD='choose-a-strong-one' PUBLIC_BASE_URL='https://<your-tunnel>.trycloudflare.com' \
  java -jar target/mutant-hunt.jar

# Instant public HTTPS URL via Cloudflare (in a second terminal)
cloudflared tunnel --url http://localhost:8080
```

`cloudflared` prints a `https://….trycloudflare.com` address. Restart the app with that address in
`PUBLIC_BASE_URL` (the QR code and the join URL on the board are generated from it), or use a named
tunnel with a fixed hostname so the URL never changes.

## Environment variables

| Variable | Default | Description |
|---|---|---|
| `ADMIN_PASSWORD` | `changeme` | Password for `/admin`. **Set it** – a warning is logged when the default is used. |
| `PUBLIC_BASE_URL` | `http://localhost:8080` | Public URL encoded in the QR code and shown on the board. |
| `PORT` | `8080` | HTTP port. |
| `DATA_DIR` | `./data` | Directory of the H2 database file (`mutanthunt.mv.db`). Everything survives restarts. |
| `STUDENT_ID_LENGTH` | `10` | Default required student-number length for new sessions (0 = any). |
| `RATE_LIMIT_MS` | `2000` | Default submission cooldown per student for new sessions. |

Behind the tunnel the app honours `X-Forwarded-*` headers (`server.forward-headers-strategy=framework`).
Cookies are `HttpOnly`, `Secure` and `SameSite=Lax`; browsers accept secure cookies on `http://localhost`,
so local testing works too.

## Instructor guide (one 50-minute lecture)

**Before class**
1. Start the app and the tunnel, set `PUBLIC_BASE_URL` to the tunnel URL.
2. `/admin` → *Sessions* → create e.g. “Week 2 – Section A” → **Make live**.
3. *Settings*: choose the board name display (full name / nickname / anonymous / masked number),
   the required student-number length, and the mutant panel mode (start with *Hidden*).
4. *Rounds*: prepare three DRAFT rounds (you can edit limits later):
   - **Round 1** – test limit **10**, optional time limit (e.g. 8 min)
   - **Round 2** – test limit **4**
   - **Round 3** – test limit **0 = unlimited**
5. Open `/board` on the projector PC (no login needed). Press **Q** for a large QR code while students join.

**Round 1 – “write tests” (10 tests).** Open the round; phones switch to the test form automatically.
Watch *Submissions* live. Expect many INVALID tests (“Your expected value is wrong – your oracle failed!”) –
use them to talk about the **test oracle** problem. Close the round (or let the timer close it).
*Before revealing*, ask: “Many of you wrote 10 tests. Why did most of you kill only 3–4 mutants?”
→ **RIPR**: a test must *Reach* the fault, *Infect* the state, *Propagate* to the output, and the oracle must *Reveal* it.
Then **Reveal**: every student sees which mutant each test killed, the mutant descriptions and code.
Switch the mutant panel to *Kill counts* / *Names* to discuss which mutants survived (M3 and M7 usually).

**Round 2 – only 4 tests.** Scarcity forces thinking: which inputs exercise the empty list, first/last
position, duplicates, and values outside −128…127? A perfect score of 7/7 with 4 tests is possible
(e.g. `[] 3 → -1`, `[1000] 1000 → 0`, `[1,2,2] 2 → 1`, `[9,4] 4 → 1`). Reveal right after closing and
compare the best 4-test suites; introduce **coverage criteria** (statement → branch → edge-pair).

**Round 3 – after CFG & edge-pair coverage (unlimited).** Draw the CFG of `indexOf`, derive
edge-pair / prime-path test requirements, and let students turn them into tests (model-driven test
design). Unlimited tests, but ties are broken by *fewer tests used*, so systematic suites win. Reveal
at the end and compare with Rounds 1–2 in *Analytics*.

**Reveal timing.** Keep a round CLOSED while you discuss the surprise (“10 tests, 3 kills”); reveal only
afterwards, because students (and the board, if the mutant panel is set to *Names*) see the mutant
descriptions after reveal. Mutant names/descriptions are never sent to phones or the public board before
the round is REVEALED.

**After class.** *Board & Export* → CSV for the whole session or a single round: participants + scores,
and all submissions (including INVALID ones and each mutant's output). Files are UTF-8 with BOM for Excel.

### Rules implemented

- A test = `list` (≤ 20 ints, may be empty; `1,2,3`, `[1, 2, 3]` or `1 2 3`), `target`, `expected` (Java `int` range).
- The reference runs first. If `expected` differs, the test is **INVALID**: stored, counts as a used test, earns nothing.
- A VALID test kills every mutant whose result differs from `expected` or that throws.
- **Score** = number of distinct mutants killed by the student's VALID tests in the round.
  Ties: fewer tests used (VALID + INVALID) first, then whoever reached the score earlier.
- Students see only their own score and VALID/INVALID until the round is revealed.
- One OPEN round per session; opening another round closes the current one.
- With a time limit the round closes automatically when the countdown ends (2 s grace for network lag).
- Joining again with the same student number (reload, second device) binds to the same player and is logged
  (and counted in the *Rejoins* column); a removed student cannot rejoin.

### Board controls

From *Admin → Board & Export* or with the keyboard on the board PC:
**Q** large QR code · **S** normal view · **F** fullscreen scoreboard.

## Architecture

- Java 21, Spring Boot 3.3, single executable jar; Thymeleaf + vanilla JS/CSS, no CDN, no build step
- H2 file database (`DATA_DIR`), JPA
- Server-Sent Events for students, board and admin; 15 s heartbeat; `X-Accel-Buffering: no`, `Cache-Control: no-cache`;
  clients reconnect automatically and re-fetch their state; scoreboard broadcasts are coalesced (≤ ~3/s)
- ZXing QR code generated from `PUBLIC_BASE_URL`
- All UI texts in `src/main/resources/messages.properties`

### Adding Turkish

Copy `messages.properties` to `messages_tr.properties` and translate the values. Browsers that prefer
Turkish (`Accept-Language: tr`) get it automatically – templates and JavaScript share the same file.

### Adding a problem

Implement `com.seng403.mutanthunt.problem.Problem` (id, title, specification, input fields,
reference implementation, mutants with id / short name / description / code, `executeMutant`) and register it in
`ProblemRegistry`. The admin *New Round* form lists all registered problems.

## Security

- Admin: password from `ADMIN_PASSWORD`, BCrypt; brute-force protection (5 failures in 5 min → 15 min block per IP,
  using Cloudflare's `CF-Connecting-IP` behind the tunnel); CSRF tokens on all admin forms and API calls;
  logout is a POST.
- Students never send code – only integers are parsed. Server-side validation of lengths, element count, int range and
  unknown fields; submission cooldown (default 2 s per student) and test limit are enforced on the server
  (row lock, so double taps cannot exceed the limit).
- Student numbers and real names appear only in the admin panel and exports, never on the board or in other students' data.
- CSV exports neutralise spreadsheet formulas in names (`=`, `+`, `-`, `@`).

## Testing

```bash
mvn test            # unit + integration tests (kill matrix, parsing, rules, API, export, security)
mvn test -Pload     # load test: 80 students × 10 tests with the board connected via SSE
```

The kill matrix from the specification is verified both on the problem level (`IndexOfProblemTest`) and end-to-end
through the HTTP API with the stored mutant results (`GameRulesIntegrationTest`):

| list | target | expected | killed |
|---|---|---|---|
| [5] | 5 | 0 | M1, M2, M4 |
| [] | 3 | -1 | M5 |
| [1,2,2] | 2 | 1 | M3, M4 |
| [9,4] | 4 | 1 | M2, M4, M6 |
| [1000] | 1000 | 0 | M1, M2, M4, M7 |
| [1,2,3] | 5 | -1 | M5 |
| [5] | 5 | 1 | INVALID |

## License

Academic use – SENG 403, Software Quality Assurance & Testing.
