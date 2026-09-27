# Deploying on Railway

How to run the application on [Railway](https://railway.com): what fits the free plan, the exact setup steps, and what was measured.

## What gets deployed

```mermaid
flowchart LR
    users(["Browser / API client"]) -->|"HTTPS"| edge["Railway edge<br/>TLS termination · *.up.railway.app"]
    subgraph project["Railway project"]
        subgraph appsvc["Service: app (Dockerfile)"]
            app["Spring Boot · profile railway<br/>JVM heap 256 MB · Tesseract 1 job at a time<br/>images stored in PostgreSQL"]
        end
        subgraph dbsvc["Service: Postgres (template)"]
            pg[("PostgreSQL")]
            vol[("Volume (the plan's one volume)")]
        end
        vars["Variables<br/>DATABASE_URL = reference to Postgres<br/>APP_SEED_PASSWORD · SPRING_PROFILES_ACTIVE"]
    end
    edge -->|"X-Forwarded-Proto: https<br/>PORT injected"| app
    app -->|"private network :5432"| pg
    pg --- vol
    vars -. injected .-> app
    app -. "optional HTTPS" .-> ai["Google Vision / OpenAI"]
```

<sub>Source: [diagrams/15-infra-railway.mmd](diagrams/15-infra-railway.mmd)</sub>

Two services in one project: the **app**, built from the repository's `Dockerfile`, and **PostgreSQL** from Railway's template. No other infrastructure is needed.

## Free plan fit

Plan limits below are as published on Railway's pricing page at the time of writing; check it for current values.

| Free-plan limit (after the 30-day trial) | How the app handles it |
|---|---|
| 0.5 GB RAM per service | The Dockerfile's JVM flags cap the heap at 256 MB with the serial GC. The `railway` profile allows **one OCR job at a time** and small thread/connection pools. **Measured peak: 402 MB** (see below). |
| 1 volume per project | PostgreSQL uses it. Label images are stored **in PostgreSQL** (`APP_STORAGE_TYPE=database`, the `railway` profile default), so the app needs no volume. |
| 1 project, 3 services | Two services are used. |
| $5 trial credit, then $1/month | At the listed per-second rates, an always-on app (0.5 GB) plus PostgreSQL uses about **$7–8/month**. `railway.json` enables **sleep when idle**, so a demo mostly costs only while in use; the first request after sleeping waits for startup (about 4 s measured locally, more on shared CPU). |
| 0.5 GB volume | Stores the database *and* images. Budget roughly 0.15–0.5 MB per synthetic label image and up to 10 MB per real photo. Hundreds of labels fit; thousands do not. |
| 10-minute build | The two-stage Docker build downloads Maven dependencies, then compiles. Normally well inside the limit. |

**Recommendation.** The free plan suits a demo or trial. For regular use, the **Hobby plan** removes the memory squeeze and allows up to 10 volumes. There you can switch to filesystem storage and raise the heap, for example `JAVA_OPTS=-XX:MaxRAMPercentage=75` and `OCR_MAX_CONCURRENT=2`.

## Files that configure Railway

| File | Purpose |
|---|---|
| [`railway.json`](../railway.json) | Dockerfile build, health check `/actuator/health` (180 s timeout), restart on failure, sleep when idle |
| [`Dockerfile`](../Dockerfile) | JRE 21 + Tesseract, non-root user, 512 MB-sized `JAVA_OPTS`, `OMP_THREAD_LIMIT=1`, `MALLOC_ARENA_MAX=2` |
| [`application-railway.yml`](../src/main/resources/application-railway.yml) | Trust Railway's HTTPS proxy headers, secure session cookie, 20 web threads, 4 DB connections, database image storage, 1 OCR job at a time |
| `DatabaseUrlEnvironmentPostProcessor` | Accepts Railway's `postgresql://user:pass@host:port/db` URL directly |
| `V2__image_blobs.sql` | Table for database-backed image storage |
| `V3__http_sessions.sql` | Session tables, so sign-ins survive sleep/wake and redeploys ([ADR-0019](adr/0019-http-sessions-stored-in-the-database.md)) |

## Step by step

### 1. Create the project

Push this folder to a Git repository you control. In Railway, choose **New Project → Deploy from repo** and select it. Railway detects `railway.json` and builds the Dockerfile.

Alternatively, deploy from this folder with the Railway CLI:

```bash
railway login
```

```bash
railway init
```

```bash
railway up
```

### 2. Add PostgreSQL

In the project, choose **New → Database → PostgreSQL**. It gets the project's volume.

### 3. Set the app's variables

In the app service's **Variables** tab:

| Variable | Value |
|---|---|
| `DATABASE_URL` | **Required.** A reference to the Postgres service's `DATABASE_URL`, set on the **app** service. In **New Variable**, use the reference picker; it appears as `${{Postgres.DATABASE_URL}}` (use your Postgres service's actual name). |
| `SPRING_PROFILES_ACTIVE` | Optional. On Railway the `railway` profile is switched on automatically when no profile is set. |
| `APP_SEED_PASSWORD` | A long random password for the two bootstrap accounts |
| `APP_SEED_SPECIALIST_EMAIL`, `APP_SEED_APPLICANT_EMAIL` | Optional: your own addresses |
| `APP_DEMO_LOGIN` | Optional, **demo only**: `true` adds an account picker to the login page that signs in without a password. Anyone can then use it ([user-accounts.md](user-accounts.md#demo-mode-pick-an-account-on-the-login-page)) |
| `APP_USERS` | Optional: more specialists and applicants, as a JSON array with bcrypt hashes ([user-accounts.md](user-accounts.md)) |
| `GOOGLE_VISION_API_KEY`, `OPENAI_API_KEY` | Optional: enable the cloud pipeline |

Do **not** set `PORT`; Railway injects it and the app reads it.

`DATABASE_URL` in `postgresql://…` form is converted automatically. You can instead set a JDBC URL plus `DATABASE_USERNAME` and `DATABASE_PASSWORD`.

If `DATABASE_URL` is missing, the app stops at startup with the message *"DATABASE_URL is not set on this Railway service…"* and the fix, instead of trying to reach `localhost:5432`.

### 4. Expose it

In the app service's **Settings → Networking**, choose **Generate Domain**. Railway serves it over HTTPS.

### 5. First sign-in

Sign in with the specialist email and `APP_SEED_PASSWORD`.

> [!TIP]
> With `APP_DEMO_LOGIN=true`, you don't need a password. Click the **Email** field, **select a user** from the **Demo accounts** list, and the email and password fill in automatically. Then press **Sign in**.

Then, so a later empty database is never seeded again with that password:

1. Set `APP_SEED=false`.
2. Redeploy.

### Lost or unknown bootstrap password

The accounts are created **once**, on the first successful start. Changing `APP_SEED_PASSWORD` later does not change them. To reset:

1. Set `APP_SEED_PASSWORD` to a new strong password, and set `APP_SEED_RESET_PASSWORD=true`. `APP_SEED` must not be `false`.
2. Deploy. The log shows *"Reset the password of 2 bootstrap account(s)…"*.
3. Sign in, then **remove** `APP_SEED_RESET_PASSWORD` and deploy again. While it is set, the reset re-applies on every restart.

## Verification

Before relying on the deployment:

1. `https://<your-domain>/actuator/health` returns `{"status":"UP"}`.
2. Sign in, submit `test-labels/aldercrest-bourbon/front.png` as the applicant (the form pre-fills), and confirm the label shows **Approved** as the AI proposal.
3. Open the label as the specialist and confirm the image displays; it is served from the database.
4. Watch **Metrics → Memory** during a few submissions. It should stay below 512 MB.
5. Check that the login redirect stays on `https://`.
6. Redeploy (or wait for the app to sleep and wake), then reload a page. You should still be signed in, because sessions are stored in the database.

## Measured locally under the same limits

The jar was run with the Dockerfile's `JAVA_OPTS`, `OMP_THREAD_LIMIT=1`, and the `railway` profile (H2 standing in for PostgreSQL). It processed 4 pre-fills, 4 sequential submissions, and **8 concurrent submissions**. Every verdict was correct and the app stayed healthy.

| Measurement | Value |
|---|---|
| Startup | 3.5 s |
| Idle resident memory | 268 MB |
| **Peak resident memory** | **402 MB** (limit 512 MB) |
| Pre-fill time per label | 0.37–0.77 s |

Limits of this measurement:

- It ran on macOS. Linux containers can use somewhat more native memory, which `MALLOC_ARENA_MAX=2` counters.
- Railway's shared CPUs may be slower.
- No Docker image was built during this check, because Docker was not installed on the machine used. The Dockerfile is exercised on the first Railway build.

## Troubleshooting

Every startup logs one line that shows what the app received, without credentials:

```
Hosting check: railway=true, DATABASE_URL=set (postgresql://postgres.railway.internal:5432/railway), profile=railway (automatic)
```

If it says `DATABASE_URL=not set`, the variable is missing on the **app** service, still staged (click **Deploy**), or references a service name that doesn't exist. If the line is absent, the deployment is running an older build.

| Symptom | Likely cause / fix |
|---|---|
| Log shows `No active profile set` and `Connection to localhost:5432 refused` | Build from before automatic Railway detection, with `DATABASE_URL` not set on the **app** service. Add the variable as a reference to the Postgres service, or redeploy the current code to get the explicit message |
| Log shows `DATABASE_URL is not set on this Railway service` | Add `DATABASE_URL` on the app service as a reference to the Postgres service's `DATABASE_URL` |
| Database connection refused or unknown host with `DATABASE_URL` set | The reference points to the wrong service name, or PostgreSQL is still starting (it retries on restart) |
| Restarts with `OutOfMemoryError` | Too much concurrent load for 0.5 GB. Keep `OCR_MAX_CONCURRENT=1`, or move to Hobby and raise the heap |
| Login redirects to `http://` | A different profile was forced through `SPRING_PROFILES_ACTIVE`. Include `railway`, e.g. `railway,custom` |
| "The label could not be read automatically" | Tesseract missing from the image. Build from the repository `Dockerfile`, not a buildpack |
| Volume full | Images live in the database. Delete old demo data, or move to Hobby with filesystem or object storage |
