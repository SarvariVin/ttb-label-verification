# Deploying on Railway

This guide runs the application on [Railway](https://railway.com). It covers what fits within the free plan, the exact setup steps, how to check the result, and what has been measured.

## The deployment at a glance

```mermaid
flowchart TB
    users(["Browser / API client"]) -->|"HTTPS"| edge["Railway edge<br/>TLS termination · *.up.railway.app"]

    subgraph project["Railway project"]
        direction LR
        subgraph appsvc["Service: app — built from Dockerfile"]
            app["Spring Boot · profile railway (automatic)<br/>256 MB heap · one OCR job at a time<br/>images stored in PostgreSQL"]
            vars["Variables<br/>DATABASE_URL = ${{Postgres.DATABASE_URL}}<br/>APP_SEED_PASSWORD · APP_DEMO_LOGIN (optional)"]
        end
        subgraph dbsvc["Service: Postgres — template"]
            pg[("PostgreSQL")]
            vol[("The plan's one volume")]
        end
    end

    edge -->|"X-Forwarded-Proto: https · PORT injected"| app
    vars -. "injected at start" .-> app
    app -->|"private network :5432"| pg
    pg --- vol
    app -. "optional HTTPS" .-> ai["Google Vision / OpenAI"]
```

<sub>Source: [diagrams/15-infra-railway.mmd](diagrams/15-infra-railway.mmd)</sub>

A single Railway project holds two services: the **app**, built from this repository's `Dockerfile`, and **PostgreSQL**, from Railway's template. No other infrastructure is involved.

## Will it fit the free plan?

The limits below are the ones on Railway's pricing page at the time of writing. Check the page for current values.

| Free-plan limit (after the 30-day trial) | How the app stays within it |
|---|---|
| 0.5 GB RAM per service | The Dockerfile's JVM flags cap the heap at 256 MB with the serial GC. The `railway` profile allows **one OCR job at a time** and keeps thread and connection pools small. **Measured peak: 402 MB** (details below). |
| 1 volume per project | PostgreSQL takes it. Label images go **into PostgreSQL** too (`APP_STORAGE_TYPE=database`, the `railway` default), so the app needs no volume of its own. |
| 1 project, 3 services | Two services are used. |
| $5 trial credit, then $1/month | At the listed per-second prices, an always-on app (0.5 GB) plus PostgreSQL comes to about **$7–8 a month**. `railway.json` turns on **sleep when idle**, so a demo mostly costs money while someone is using it. The first request after a sleep waits for startup (about 4 s locally, longer on shared CPU). |
| 0.5 GB volume | Holds the database *and* the images. Allow roughly 0.15–0.5 MB per synthetic label image and up to 10 MB per real photo. Hundreds of labels fit; thousands don't. |
| 10-minute build | The two-stage Docker build downloads Maven dependencies, then compiles, which normally finishes well within the limit. |

**Our advice:** the free plan is fine for a demo or a trial. For everyday use, the **Hobby plan** removes the memory squeeze and allows up to 10 volumes. There you can switch to filesystem storage and give the JVM more room, for example `JAVA_OPTS=-XX:MaxRAMPercentage=75` and `OCR_MAX_CONCURRENT=2`.

## Files that configure the deployment

| File | What it does |
|---|---|
| [`railway.json`](../railway.json) | Builds from the Dockerfile, health-checks `/actuator/health` with a 180 s timeout, restarts on failure, sleeps when idle |
| [`Dockerfile`](../Dockerfile) | JRE 21 plus Tesseract, a non-root user, `JAVA_OPTS` sized for 512 MB, `OMP_THREAD_LIMIT=1`, `MALLOC_ARENA_MAX=2` |
| [`.dockerignore`](../.dockerignore) | Keeps `target/`, `data/`, `test-labels/`, `docs/` and `.git/` out of the build upload |
| [`.mvn/wrapper/maven-wrapper.properties`](../.mvn/wrapper/maven-wrapper.properties) | Tells `mvnw` which Maven version to fetch. The Docker build copies it, so it must be committed. |
| [`application-railway.yml`](../src/main/resources/application-railway.yml) | Trusts Railway's HTTPS proxy headers, secures the session cookie, and uses 20 web threads, 4 DB connections, database image storage and one OCR job at a time |
| `DatabaseUrlEnvironmentPostProcessor` | Reads Railway's `postgresql://user:pass@host:port/db` URL directly |
| `V2__image_blobs.sql` | The table that holds images when storage is `database` |
| `V3__http_sessions.sql` | Session tables, so sign-ins survive sleep, wake and redeploys ([ADR-0019](adr/0019-http-sessions-stored-in-the-database.md)) |

## Setting it up

### 1. Create the project

Push this folder to a Git repository you control. In Railway, pick **New Project → Deploy from repo** and choose it. Railway finds `railway.json` and builds the Dockerfile.

Or deploy straight from this folder with the Railway CLI:

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

Inside the project, choose **New → Database → PostgreSQL**. It takes the project's volume.

### 3. Give the app its variables

Open the **app** service's **Variables** tab (not the Postgres one):

| Variable | Value |
|---|---|
| `DATABASE_URL` | **Required.** A reference to the Postgres service's `DATABASE_URL`. In **New Variable**, use the reference picker. It shows up as `${{Postgres.DATABASE_URL}}`, with your Postgres service's actual name. |
| `APP_SEED_PASSWORD` | A long random password shared by the three bootstrap accounts (one specialist, two test applicants) |
| `APP_SEED_SPECIALIST_EMAIL`, `APP_SEED_APPLICANT_EMAIL`, `APP_SEED_APPLICANT2_EMAIL` | Optional: your own addresses for those accounts |
| `SPRING_PROFILES_ACTIVE` | Optional. When no profile is set, the `railway` profile turns on by itself on Railway. |
| `APP_DEMO_LOGIN` | Optional and **for demos only**. `true` adds a passwordless account picker to the login page, which anyone can use ([user-accounts.md](user-accounts.md#demo-mode-choosing-an-account-on-the-login-page)). |
| `APP_USERS` | Optional: more specialists and applicants as a JSON array with bcrypt hashes ([user-accounts.md](user-accounts.md)) |
| `GOOGLE_VISION_API_KEY`, `OPENAI_API_KEY` | Optional: turn on the cloud pipeline |

Leave `PORT` alone. Railway injects it, and the app picks it up.

A `postgresql://…` URL is converted automatically. If you prefer, set a JDBC URL together with `DATABASE_USERNAME` and `DATABASE_PASSWORD`.

If `DATABASE_URL` is missing, the app doesn't quietly try `localhost:5432`. It stops at startup with *"DATABASE_URL is not set on this Railway service…"* and says how to fix it.

Variable changes are **staged**. Click **Deploy** to apply them.

### 4. Make it reachable

In the app service, go to **Settings → Networking** and choose **Generate Domain**. Railway serves it over HTTPS.

### 5. Sign in for the first time

Use `specialist@example.gov` (or your override) with `APP_SEED_PASSWORD`. The two test applicants use the same password.

> [!TIP]
> With `APP_DEMO_LOGIN=true`, there's nothing to type. Click **Email**, **pick a user** from **Demo accounts**, and press **Sign in**.

Afterwards, stop any future empty database from being seeded with the same password:

1. Set `APP_SEED=false`.
2. Redeploy.

### Lost or unknown bootstrap password

The bootstrap accounts are created **once**, on the first successful start, and changing `APP_SEED_PASSWORD` later doesn't touch them. To reset them:

1. Set `APP_SEED_PASSWORD` to a new strong password, and add `APP_SEED_RESET_PASSWORD=true`. `APP_SEED` must not be `false`.
2. Deploy. The log reports *"Reset the password of 3 bootstrap account(s)…"*.
3. Sign in, then **delete** `APP_SEED_RESET_PASSWORD` and deploy again. While it is set, the reset runs on every restart.

## Checking the deployment

1. `https://<your-domain>/actuator/health` answers `{"status":"UP"}`.
2. Sign in as a test applicant and submit `test-labels/aldercrest-bourbon/front.png` (the form pre-fills). The AI proposal should be **Approved**.
3. Open that label as the specialist and confirm the image loads. It is served from the database.
4. Keep an eye on **Metrics → Memory** over a few submissions. It should stay under 512 MB.
5. Make sure the sign-in redirect stays on `https://`.
6. Redeploy, or let the app sleep and wake, then reload. You should still be signed in, because sessions are in the database.

## Measured locally under the same limits

The jar ran with the Dockerfile's `JAVA_OPTS`, `OMP_THREAD_LIMIT=1` and the `railway` profile, with H2 standing in for PostgreSQL. It handled 4 pre-fills, 4 back-to-back submissions and **8 simultaneous submissions**. Every verdict was right, and the app stayed healthy throughout.

| Measurement | Value |
|---|---|
| Startup | 3.5 s |
| Resident memory when idle | 268 MB |
| **Peak resident memory** | **402 MB** (limit 512 MB) |
| Pre-fill time per label | 0.37–0.77 s |

Caveats:

- The run was on macOS. Linux containers can use a little more native memory, which `MALLOC_ARENA_MAX=2` offsets.
- Railway's shared CPUs may be slower.
- No Docker image was built for this measurement, because Docker wasn't installed on the machine. The first Railway build is what exercises the Dockerfile.

## Troubleshooting

Each startup logs one line describing what the app received, with credentials left out:

```
Hosting check: railway=true, DATABASE_URL=set (postgresql://postgres.railway.internal:5432/railway), profile=railway (automatic)
```

`DATABASE_URL=not set` means the variable is missing from the **app** service, is still staged (click **Deploy**), or references a service name that doesn't exist. If the line isn't there at all, an older build is running.

| What you see | Likely cause and fix |
|---|---|
| Build fails with `failed to calculate checksum … "/.mvn": not found` | The `.mvn/wrapper/` folder isn't in the repository. Commit `.mvn/wrapper/maven-wrapper.properties`. |
| The build uploads 100 MB or more | `target/` or `data/` is being sent. Make sure `.dockerignore` is committed. |
| `DATABASE_URL is not set on this Railway service` | Add `DATABASE_URL` to the app service as a reference to the Postgres service's `DATABASE_URL`, then **Deploy** |
| `No active profile set` followed by `Connection to localhost:5432 refused` | A build from before automatic Railway detection, with no `DATABASE_URL` on the app service. Add the reference, or redeploy current code to get the clearer message. |
| Connection refused or unknown host even though `DATABASE_URL` is set | The reference names the wrong service, or PostgreSQL is still starting (the app retries on restart) |
| Restarts with `OutOfMemoryError` | More concurrent load than 0.5 GB allows. Keep `OCR_MAX_CONCURRENT=1`, or move to Hobby and raise the heap. |
| Sign-in redirects to `http://` | `SPRING_PROFILES_ACTIVE` forces a profile list without `railway`. Include it, for example `railway,custom`. |
| "The label could not be read automatically" | Tesseract isn't in the image. Build from the repository `Dockerfile`, not a buildpack. |
| Volume full | Images are stored in the database. Remove old demo data, or move to Hobby with filesystem or object storage. |
