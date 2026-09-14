# Frame — Build Progress

Tracker for the two-phase build described in the project plan. Check items off as they land; keep this file honest so it doubles as a portfolio artifact (shows deliberate, staged engineering).

## Phase 1 — Full Implementation

### Milestone 1: Repo scaffold
- [x] Monorepo layout (`backend/`, `frontend/`, `docs/`)
- [x] `docs/PROGRESS.md` (this file)
- [x] `docker-compose.yml` (postgres + backend + frontend, healthchecks, named volumes)
- [x] Root `.gitignore`

### Milestone 2: Backend — schema & persistence
- [x] Maven project skeleton (`pom.xml`, Spring Boot 3.x, Java 17)
- [x] Flyway migrations: `users`, `videos`, `processing_jobs`, `events`, `markers`
- [x] JPA entities + enums (`VideoStatus`, `JobStatus`, `EventType`)
- [x] Spring Data repositories

### Milestone 3: Backend — auth & storage
- [x] DTOs (request/response records)
- [x] `StorageService` interface + `LocalStorageService` impl
- [x] JWT issuing/validation (`JwtService`, `JwtAuthFilter`)
- [x] `SecurityConfig` (stateless, JWT filter chain)
- [x] Auth service + controller (register/login/me)

### Milestone 4: Backend — core domain + processing
- [x] Video service + controller (upload, list, get, delete, stream with HTTP Range)
- [x] Event service + controller (list with filters, get by id)
- [x] Marker service + controller (CRUD)
- [x] Processing job service + controller
- [x] `EventProcessor` interface, `ProcessingContext`, `MockEventProcessor`
- [x] `AsyncConfig` (bounded `ThreadPoolTaskExecutor`)
- [x] `WebSocketConfig` (STOMP/SockJS), progress broadcasting
- [x] `GlobalExceptionHandler`, CORS config, logging
- [x] Unit tests: `JwtServiceTest`, `MockEventProcessorTest`, `AuthServiceTest`

Known deferrals (documented trade-offs, not oversights):
- No thumbnail generation (mock processor doesn't produce real frames); `/thumbnail` endpoint dropped, frontend relies on `<video preload="metadata">` for a first-frame poster.
- `/ws/**` is not authenticated at the STOMP layer (handshake permits all; no per-subscription ownership check). Acceptable for a single-tenant portfolio demo; flagged for the Phase 2 write-up as a real gap for a multi-tenant deployment.
- **Could not compile-check this backend**: no Java/Maven/Docker available in this sandbox. Code was hand-reviewed (including a dedicated static-review pass) but has not been built. Run `mvn -f backend/pom.xml test` yourself before trusting it fully.

### Milestone 5: Frontend — scaffold & auth
- [x] Next.js App Router + TypeScript + Tailwind + shadcn/ui scaffold
- [x] Typed API client (`lib/api-client.ts`), shared `types.ts`
- [x] Auth pages (login/register), token handling (`lib/auth.ts`)

### Milestone 6: Frontend — dashboard
- [x] Upload workspace (dropzone, progress indicators)
- [x] WebSocket hook (`useVideoWebSocket`) wired to `/topic/videos` + per-video progress
- [x] Live-updating video list (status badges)

Known deferral: no local Node build has been run yet against this code (coming once the review-workspace milestone lands too, so I can smoke-test the whole client at once) — flagging per the same "unverified" caveat as the backend.

### Milestone 7: Frontend — review workspace
- [x] Video player (custom controls, `usePlaybackSync` ref+rAF pattern)
- [x] Hybrid timeline (canvas ruler/playhead + DOM/SVG event markers)
- [x] Event filter bar + event inspector panel
- [x] Marker creation modal + marker rendering on timeline

**Frontend is actually verified** (unlike the backend): `npm run typecheck`, `npm run lint` (flat
ESLint config, `eslint-config-next` v16), and `npm run build` all pass clean, and the login/register
flow was smoke-tested live in a browser (dark theme renders correctly, form state and error
handling both work against a deliberately-unreachable API). Bumped to **Next.js 16.3.5 + React 19**
mid-build after `npm install` flagged the originally-planned Next 14.2.15 with a critical
RCE/SSRF/cache-poisoning CVE bundle with no 14.x fix available — 16.3.5 is the first patched line.

### Milestone 8: Integration
- [x] Seed data — demo login (`demo@frame.dev` / `password`) auto-created on first startup via a
  `DemoDataSeeder` (`ApplicationRunner`), through the real `PasswordEncoder` bean rather than a
  hardcoded bcrypt hash in SQL
- [ ] **Descoped: no pre-loaded sample video.** Shipping one convincingly needed either a real
  video file checked into the repo or a synthesized one; downloading a file from the internet
  autonomously isn't something this build does without asking first, and there was no ffmpeg/media
  toolchain available in this sandbox to synthesize one. Uploading a short clip after logging in
  exercises the full pipeline in well under 10 seconds, so the gap is minor — but it means
  `docker-compose up` alone doesn't give you a READY video to click into immediately.
- [ ] **Not run: `docker-compose up --build`.** No Docker in this sandbox (confirmed: `docker
  --version` → command not found). The compose file, both Dockerfiles, and the healthchecks are
  written and reviewed but not exercised. **This is the single most important thing to verify
  yourself before trusting the app works end-to-end.**
- [x] Top-level `README.md` (quickstart, architecture + sequence diagrams, feature summary, key
  design decisions, project structure)
- [x] `backend/.dockerignore` and `frontend/.dockerignore` (a review pass caught that without the
  frontend one, `COPY . .` in the Docker build stage would have layered this Windows host's
  already-installed `node_modules` over the container's Linux-built one — switched to `npm ci`
  against the committed lockfile instead of `npm install`, too)

## Phase 2 — Learning & Interview Prep

Started once Phase 1's code was complete and reviewed — not gated on an actual `docker-compose up`
run, since that verification requires Docker, which isn't available in the sandbox this was built
in (see Milestone 8). The docs below describe the code as written and reviewed, not as
runtime-verified.

- [x] `docs/ARCHITECTURE.md` — backend deep-dive (async executor + self-invocation trap, JPA/Postgres
  mapping choices, STOMP/WebSocket routing, layering) + frontend deep-dive (rAF-driven playhead vs
  `timeupdate`, ref-vs-state split, hybrid canvas/DOM timeline) — combines the originally-separate
  "deep-dive" and "interview cheat sheet" items into one document, organized in two parts
- [x] Interview defense / trade-off cheat sheet (Part 2 of `docs/ARCHITECTURE.md`): Spring Boot vs
  Node, React high-frequency-update handling, scaling to 1,000 concurrent users (named in the order
  things would actually break), JWT-in-localStorage trade-off, why processing is mocked, and an
  unprompted "weakest parts of this design" list
- [x] Mermaid architecture diagrams in root `README.md` (upload→ready sequence diagram + system
  flowchart)
