# Frame — Architecture Deep-Dive & Interview Defense Guide

Phase 2 of the project: now that the app exists, this document explains *how it actually works*
and *why it was built that way*, so every decision can be defended in an interview rather than
just pointed at. Everything below refers to real files in this repo — grep for the class/file name
if you want the source next to the explanation.

---

## Part 1: System Walkthrough

### 1.1 The async processing pipeline, mechanically

The core of the backend isn't the CRUD endpoints — it's the state machine and the async
execution behind `POST /api/videos`. Here's exactly what happens, file by file:

1. **`VideoController.upload`** receives the multipart request and calls `VideoService.upload`.
2. **`VideoService.upload`** stores the file via `StorageService`, inserts a `Video` row with
   `status = UPLOADING`, then calls `ProcessingOrchestrationService.enqueue(video)`. Notice this
   method is **not** `@Transactional`. That's deliberate: file I/O has no business inside a
   database transaction, and — more importantly — wrapping this in one transaction would create a
   race with step 4 below.
3. **`ProcessingOrchestrationService.enqueue`** flips the video to `QUEUED`, inserts a
   `ProcessingJob` row (`status = QUEUED`), broadcasts the status over WebSocket, and calls
   `processingWorker.runAsync(videoId, jobId)` — on the **injected `ProcessingWorker` bean**, not
   `this`.
4. **`ProcessingWorker.runAsync`** is annotated `@Async("processingExecutor")`. Because it's
   invoked *through the Spring proxy* (step 3 called it on an injected bean, not internally), the
   `AsyncAnnotationBeanPostProcessor`'s advice intercepts the call and submits it to the named
   executor instead of running it on the caller's thread. The HTTP request thread returns
   immediately with `201 Created`; the actual work happens on a background thread from the pool
   defined in `AsyncConfig`.

**Why two service classes instead of one?** The first draft had `enqueue()` and the `@Async`
method on the *same* class, with `enqueue()` calling `this.runAsync(...)`. That's a classic Spring
AOP trap: `@Async` (like `@Transactional`) is implemented via a proxy that wraps the *bean*, not
the *class*. A call from inside the class to `this.someAnnotatedMethod()` never goes through the
proxy, so the annotation is silently ignored and the "async" method just runs synchronously,
inline, on the calling thread. Splitting the enqueue-and-record step (`ProcessingOrchestrationService`)
from the actual async entry point (`ProcessingWorker`) forces every call to `runAsync` to cross a
bean boundary, which is the only way to guarantee it goes through the proxy.

**The executor itself** (`AsyncConfig.processingExecutor`) is a `ThreadPoolTaskExecutor` with
`corePoolSize=2`, `maxPoolSize=4`, `queueCapacity=50`, and a `CallerRunsPolicy`. This is a
deliberate departure from Spring's *default* `@Async` executor
(`SimpleAsyncTaskExecutor`), which spawns a brand-new thread per invocation with no upper bound —
fine for occasional fire-and-forget calls, dangerous for something as heavy as video processing
under concurrent uploads. `CallerRunsPolicy` matters specifically: once the queue AND the pool are
both full, instead of throwing `RejectedExecutionException` (dropping the job) or silently queuing
unboundedly (defeating the point of a bound), the rejected task runs **on the thread that tried to
submit it** — in this case, whatever HTTP thread called `enqueue()`. That thread blocks until the
task finishes. The effect: under sustained overload, the system slows down uploads (backpressure)
instead of losing work or exhausting memory. That's a genuine trade-off worth being able to state
plainly: bounded resource usage, at the cost of request latency under burst load.

### 1.2 How the mock processor talks back without knowing about persistence or WebSockets

`MockEventProcessor` implements the pure `EventProcessor` interface — it only knows about
`ProcessingContext.reportProgress(percent, stage)` and `ProcessingContext.emitEvent(draft)`. It
has zero dependencies on Spring Data, `SimpMessagingTemplate`, or entities. The actual wiring
happens in `ProcessingWorker.DefaultProcessingContext`, a private inner class that implements
`ProcessingContext` and, on every call, (a) writes to the DB via the repositories and (b) pushes a
STOMP frame. This separation is *the* reason swapping in a real ffmpeg/AI-backed processor later
is a config change (`frame.processing.provider`) plus one new `@Component`, not a rewrite: the new
processor would call the exact same two context methods and never need to know persistence or
WebSockets exist.

### 1.3 JPA → PostgreSQL mapping choices

- **UUID primary keys**, `@GeneratedValue` with no explicit strategy. Hibernate 6 generates these
  **in memory** before the INSERT is even sent — unlike an `IDENTITY`/auto-increment strategy,
  which requires a round trip to the DB to learn the generated value. This matters here because
  `VideoService.upload` needs the video's data available for `toResponse()` immediately after
  `save()`, with no extra query.
- **`Event.metadata` is `Map<String, Object>`, mapped via `@JdbcTypeCode(SqlTypes.JSON)` onto a
  Postgres `jsonb` column.** This is the single biggest schema decision in the app: `SCENE_CHANGE`,
  `OCR_TEXT`, `AUDIO_EVENT`, etc. all have genuinely different metadata shapes (a bounding box for
  OCR, a category label for audio events, nothing at all for a scene change). Modeling that as
  rigid columns would mean a wide table full of nulls, or an EAV (entity-attribute-value) side
  table with its own joins and type-unsafety. `jsonb` gives flexible per-type payloads in one
  column, queryable with Postgres's native JSON operators if that's ever needed, at the cost of
  losing compile-time/schema-level guarantees about what's inside it (the DTO layer — `EventDtos`
  — is where that gets re-established for API consumers).
- **`ddl-auto: validate`, schema owned entirely by Flyway** (`V1__init.sql`). Hibernate never
  creates or alters schema at runtime; it only checks that the entity mappings match what's
  already there and refuses to start if they don't. This is precisely the mechanism that would
  have caught the column-length mismatches a review pass found by hand (entity fields with no
  explicit `length` default to 255, which silently disagreed with several `varchar(N)` columns in
  the migration) — `ddl-auto: validate` turns that class of bug into a boot-time failure instead
  of a runtime surprise.
- **Composite indexes**: `(video_id, timestamp_ms)` on both `events` and `markers`. The app's
  hottest read is "all events for this video, in time order, optionally filtered by type/range" —
  a composite index leading with the foreign key and then the sort column lets Postgres satisfy
  that query with an index range scan instead of a full table scan followed by a sort.

### 1.4 WebSocket frame routing

`WebSocketConfig` enables STOMP over SockJS at `/ws` with a **simple in-memory broker** on the
`/topic` prefix (`registry.enableSimpleBroker("/topic")`). Concretely:

- The frontend's shared STOMP client (`lib/ws-client.ts`) connects once via `SockJS(".../ws?token=...")`.
  The JWT rides as a query parameter, not an `Authorization` header, because the browser's
  WebSocket/SockJS APIs give you no way to attach custom headers to the handshake — `JwtAuthFilter`
  explicitly falls back to reading a `token` request parameter for exactly this reason.
- Server-side, nothing responds to a client *message*; every broadcast is **server-initiated**.
  `ProcessingWorker` calls `SimpMessagingTemplate.convertAndSend("/topic/videos/{id}/progress", payload)`
  directly from inside the background processing thread, and Spring's simple broker fans that out
  to every session currently subscribed to that exact destination string.
- "Simple broker" means this all happens **in the same JVM's memory** — there's no external
  message broker (RabbitMQ, Redis pub/sub) relaying frames between instances. That's the right
  choice for one backend instance and is explicitly called out as a scaling limit in Part 2.
- **Known, documented gap**: the handshake is authenticated (a request without a valid token still
  completes the SockJS handshake today, since `/ws/**` is `permitAll` and only the JWT filter
  populates the security context — see `SecurityConfig`), but there's no per-subscription
  authorization check. Any signed-in user who knows or guesses a video's UUID can subscribe to its
  progress topic. Acceptable for a single-tenant demo; a real multi-tenant deployment would need a
  `ChannelInterceptor` validating ownership on `SUBSCRIBE` frames.

### 1.5 Layering and DTOs

Every request flows `Controller → Service → Repository`, and DTOs (`dto/*.java`, all Java records)
sit strictly at the controller boundary — services and controllers never return a JPA entity
directly. Two concrete reasons this isn't just ceremony: `User` has a `passwordHash` field that
must never leave the process, and `Video`'s DTO (`VideoResponse`) exposes `hasThumbnail: boolean`
computed from `thumbnailPath != null` rather than the path itself — the wire contract and the
persistence model are free to diverge.

### 1.6 Frontend: keeping a 60fps playhead out of React's way

This is the frontend's equivalent of the async-executor discussion — the one piece of the UI where
a naive implementation would visibly perform badly.

**The problem**: the native `<video>` element's `timeupdate` event does not fire at a reliable,
high rate — the HTML spec only says implementations *should* fire it "about" every 250ms while the
media element isn't paused, and real browsers throttle it further and inconsistently, especially
while a tab is backgrounded or during scrubbing. Driving a moving timeline playhead purely off
`timeupdate` (`onTimeUpdate={() => setCurrentTime(video.currentTime)}`) would produce a playhead
that visibly stutters, and — if you *did* have a way to fire it every frame — would still mean a
full React re-render 60 times a second for a component tree that includes potentially hundreds of
timeline event markers.

**The fix, concretely** (`hooks/usePlaybackSync.ts` + `components/timeline/Timeline.tsx`):

- `usePlaybackSync` runs its own `requestAnimationFrame` loop. Every frame, it reads
  `video.currentTime` and writes it into `currentTimeMsRef.current` — a plain mutable ref, **not**
  React state. Writing to a ref never schedules a re-render.
- `Timeline` receives that *same* ref object and runs its **own** independent `requestAnimationFrame`
  loop (inside the `useEffect` that owns the `<canvas>`) that reads `currentTimeMsRef.current` and
  redraws just the playhead line on the canvas. This is imperative canvas drawing, completely
  outside React's render cycle — the component's JSX/DOM never changes because of playhead motion.
- A **separate**, deliberately throttled piece of React state, `displayTimeMs`, is updated from the
  same rAF loop but gated to at most 4 times a second (`UI_UPDATE_INTERVAL_MS = 250`). This is the
  *only* thing that actually re-renders anything, and it feeds exactly one place that needs it: the
  `M:SS` text readout in the player controls. A human can't perceive a time readout updating faster
  than a few times a second anyway, so this costs nothing visually while eliminating ~57 unnecessary
  re-renders per second.
- Everything **else** on the timeline — the individual event markers, chapter/speaker range bars,
  user markers — is ordinary React-rendered DOM/SVG, positioned with a plain CSS percentage
  (`timestampMs / durationMs`) computed from data that's static once fetched. There's no reason to
  push those into canvas or avoid re-renders for them; they only re-render when the underlying
  event/marker list or filter set actually changes, which is rare. The canvas treatment is reserved
  specifically for the one thing that moves continuously.

This hybrid split — canvas for continuous/high-frequency (the playhead, the ruler background),
DOM for discrete/interactive (individual events, hover, click, tooltips, keyboard focus) — is the
deliberate reason the timeline isn't pure canvas (which would mean hand-rolling hit-testing, hover
states, and accessibility) or pure DOM (which would mean either a stuttery playhead or excessive
re-renders).

---

## Part 2: Interview Defense & Trade-off Cheat Sheet

### "Why Spring Boot over Node.js for this backend?"

The honest answer isn't "Node can't do this" — a Node backend with a proper job queue (BullMQ,
say) would be a perfectly reasonable choice too. What Spring Boot specifically bought for *this*
app:

- **Explicit, typed concurrency control.** The bounded `ThreadPoolTaskExecutor` with a named
  rejection policy (Section 1.1) is a first-class, statically-typed construct in Java's
  `java.util.concurrent` package that Spring just wires up declaratively. Achieving the same
  bounded-queue-with-backpressure behavior in Node means reaching for a separate library and
  losing some of that compile-time clarity, since Node's single-threaded event loop model isn't
  built around worker pools in the same way.
- **A relational domain that benefits from a real ORM and migration story.** `Video` →
  `ProcessingJob` (1:many), `Video` → `Event`/`Marker` (1:many), with a `jsonb` escape hatch for
  variable event metadata — this is squarely what JPA/Hibernate plus Flyway are built for, and
  `ddl-auto: validate` gives a real safety net (Section 1.3) that a hand-rolled Node/Prisma/Knex
  setup would need to reconstruct.
- **Strong typing across the whole request lifecycle** — DTOs as records, JPA entities, enum-backed
  status fields (`VideoStatus`, `JobStatus`, `EventType`) all checked at compile time. In a state
  machine with five statuses and a background job whose failure needs to be surfaced correctly,
  that's a real category of bug (typo'd status strings, mismatched fields) eliminated for free.

The failure mode of this answer in an interview is pretending Node is strictly worse — the honest
framing is "here's specifically what this app's shape (stateful pipeline, relational data, explicit
concurrency needs) made me reach for," which is a stronger answer than a blanket claim.

### "How do you handle high-frequency video timestamp updates in React without performance lags?"

Give the Section 1.6 answer: a `requestAnimationFrame` loop writes to a `ref` (no re-render), a
second independent rAF loop in the canvas component reads that ref to redraw the playhead
imperatively, and a throttled (4Hz) piece of React state serves only the numeric time readout that
actually needs to re-render. The key insight to state explicitly: **not all UI needs to be driven
by React state** — a ref plus direct DOM/canvas manipulation is the right tool specifically for
continuous, high-frequency values, while React stays responsible for everything discrete and
infrequent.

### "How would this system scale if 1,000 users uploaded videos simultaneously?"

Name what breaks first, in order, rather than giving a vague "add more servers" answer:

1. **The bounded processing executor** (`core=2, max=4, queue=50`) would saturate almost
   immediately — 1,000 concurrent jobs against 4 worker threads means most uploads sit queued (or,
   past 50 queued, block the submitting HTTP thread via `CallerRunsPolicy`) for a long time. First
   fix: this is a config value, not an architecture — bump pool size, and/or move off a
   single-instance in-process executor onto a real distributed queue (SQS, RabbitMQ) so processing
   work can be scaled independently of the HTTP-serving instances.
2. **The in-memory STOMP simple broker doesn't work across multiple backend instances.** If you
   horizontally scale the API behind a load balancer, a client connected to instance A never
   receives a progress broadcast generated by a job that happened to run on instance B. Fix:
   replace `enableSimpleBroker` with a relay to an external broker (RabbitMQ's STOMP plugin is the
   textbook Spring answer), or move progress delivery off WebSocket entirely onto something
   instance-agnostic like Redis pub/sub fanned out by a small dedicated gateway.
3. **Local filesystem storage doesn't work across multiple instances/containers** unless they share
   a network filesystem, which is exactly why `StorageService` is an interface (Section on storage
   in the README) — swap in an S3-backed implementation and this problem disappears, since S3 is
   inherently shared and durable.
4. **A real (non-mock) processor would be CPU/memory-heavy per job.** At that point you'd want
   processing workers as a *separate* deployable from the HTTP-serving Spring instance entirely —
   today they share a JVM and a thread pool, which is fine for a lightweight `Thread.sleep`-based
   mock but wouldn't be once real ffmpeg/AI work is CPU-bound.
5. **No crash-recovery story.** If the backend restarts mid-job, that `ProcessingJob` is stuck at
   `RUNNING` forever with nothing to resume or retry it — worth naming unprompted as the kind of
   gap a real distributed job queue (with visibility timeouts / dead-letter handling) closes for
   free.

### "Why JWT in localStorage instead of an httpOnly cookie?"

Simplicity trade-off, stated plainly: no server-side session store, no CSRF token plumbing, and
the token can ride as a query param for the two places (video streaming, WebSocket handshake) that
can't attach custom headers. The real cost is XSS exposure — any script that runs on the page can
read `localStorage` and steal the token, whereas an httpOnly cookie is invisible to JS. For a
single-tenant portfolio demo this is an accepted, explicitly-documented trade-off
(`frontend/lib/auth.ts` says so in a comment); a production deployment handling real user data
should move to httpOnly cookies plus CSRF protection, or a short-lived-token-plus-refresh pattern.

### "Why is video processing simulated instead of real?"

Time-to-a-working-end-to-end-system was the priority, and the *interesting* engineering here —
the state machine, the bounded async executor, the WebSocket progress plumbing, the event data
model, the timeline UI — is identical whether the events come from `Thread.sleep` and
`ThreadLocalRandom` or from real ffmpeg scene detection and a transcription/OCR model. Rather than
bolt "mock mode" on as an afterthought, the whole pipeline was built against one interface
(`EventProcessor`) from the start specifically so a real implementation is additive: a new
`@Component` behind `@ConditionalOnProperty("frame.processing.provider")`, not a refactor of
anything upstream (controllers, services, WebSocket wiring, or the frontend) — all of that already
only knows about `Event`/`ProcessingJob` rows and progress percentages, never about *how* they were
produced.

### "What's the weakest part of this design, and how would you fix it?"

A candid list, roughly in order of what I'd fix first with more time:

1. **No integration tests against a real database.** The backend unit tests (`JwtServiceTest`,
   `MockEventProcessorTest`, `AuthServiceTest`) are fast and real, but nothing exercises the Flyway
   migrations plus JPA mappings together — which is exactly the class of bug (column-length
   mismatches) a manual review had to catch by hand. Fix: Testcontainers-backed
   `@DataJpaTest`/`@SpringBootTest` runs against a real ephemeral Postgres in CI.
2. **No crash recovery for stuck jobs** (see the scaling answer above).
3. **No per-subscription WebSocket authorization** (Section 1.4).
4. **No server-side file-type validation on upload** — the app trusts the client-reported
   `Content-Type` from the multipart request rather than sniffing the actual file bytes; a
   malicious client could upload an arbitrary file with a spoofed video content-type.
5. **No pagination** on the video list or event list endpoints — entirely fine at demo scale (tens
   of videos, low hundreds of events each), a real problem at real scale.
6. **No rate limiting** on `/api/auth/login` or `/api/videos` — a real deployment needs both
   (brute-force protection on the former, abuse/cost control on the latter).

Being able to list these unprompted, with the *reason* each one is currently acceptable (demo
scope) and what closes the gap, reads as far stronger in an interview than pretending the design
has no weak points.
