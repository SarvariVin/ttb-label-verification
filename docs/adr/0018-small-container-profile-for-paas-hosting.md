# ADR-0018: Small-container profile for PaaS hosting

- **Status:** Accepted
- **Date:** 2026-09-23

## Context

The application should be deployable on low-cost platforms such as Railway. Railway's free plan gives each service 0.5 GB of RAM and a project a single volume. Its proxy terminates TLS, it injects `PORT`, and it provides the database URL as `postgresql://user:pass@host:port/db`. With default JVM settings the app used 353 MB idle after OCR work, and its heap could grow to a quarter of host memory. Filesystem image storage would need a second volume.

## Decision

- **Container JVM defaults for 512 MB**, set in the Dockerfile and overridable through `JAVA_OPTS`:
  - `-Xmx256m`, `-XX:MaxMetaspaceSize=160m`, `-XX:+UseSerialGC`, `-XX:TieredStopAtLevel=1`, `-XX:+ExitOnOutOfMemoryError`
  - `OMP_THREAD_LIMIT=1` and `MALLOC_ARENA_MAX=2` to limit native memory
- **Bounded OCR concurrency** (`app.ocr.max-concurrent`, default 2): a fair semaphore in `TesseractOcrEngine`.
- **Database image storage** (`app.storage.type=database`, table `image_blobs`) alongside filesystem storage, selected by configuration.
- A **`railway` Spring profile**:
  - trust forwarded headers (`framework` strategy) and use secure cookies
  - 20 web threads, 4 DB connections
  - database storage, one OCR job at a time
- **Platform URL support:** `DatabaseUrlEnvironmentPostProcessor` converts `postgres(ql)://` URLs to JDBC and credentials; explicit variables win.
- **Railway detection:** when Railway's injected variables are present, the `railway` profile is activated if no profile was chosen. A missing `DATABASE_URL` stops startup with an actionable message. This came from a first deployment in which both variables were missing and the app kept retrying `localhost:5432`.
- **`railway.json`:** Dockerfile build, `/actuator/health` check, restart on failure, sleep when idle.

## Consequences

- Measured under these settings: 402 MB peak with 8 concurrent submissions, correct results, and 3.5 s startup ([deploy-railway.md](../deploy-railway.md#measured-locally-under-the-same-limits)).
- Throughput on the free plan is one OCR at a time. Bursts queue rather than fail.
- Images in PostgreSQL consume the single 0.5 GB volume; this is suitable for demos, not for large volumes of labels.
- `TieredStopAtLevel=1` trades peak CPU throughput for less memory and faster startup; larger hosts should override `JAVA_OPTS`.
- The same profile works on other platforms that inject a `postgres://` URL and terminate TLS (Heroku, Render, Fly.io).

## Alternatives considered

- Keep the default JVM settings: risks the container being killed for memory mid-OCR at 0.5 GB.
- Object storage (S3-compatible bucket) for images: better at scale, but needs another credential set. It remains the production recommendation ([production.md](../production.md#4-storage-and-records)).
- A platform-specific buildpack instead of the Dockerfile: it would not include Tesseract.
