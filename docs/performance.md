# Performance

These are the timings and throughput of the **local pipeline** (Tesseract OCR, with no cloud services), measured on a developer machine. Figures for the 512 MB container and Railway are in [deploy-railway.md](deploy-railway.md#measured-locally-under-the-same-limits).

## Summary

| Metric | Result |
|--------|--------|
| **Pre-fill** (read a label and suggest form values), server time | **p50 290 ms**, p95 333 ms, max 501 ms |
| **Full analysis** (OCR, find every field, compare, verdict), server time | **p50 532 ms**, p95 623 ms, max 793 ms |
| **Submit round trip** (upload, analysis, save, redirect), seen by the client | **p50 552 ms**, p95 656 ms, max 813 ms |
| First OCR after startup (cold) | 644 ms server, 672 ms round trip |
| Application startup | 2.96 s |
| Throughput, pre-fill | **≈ 6.2 labels/s** (limited by `OCR_MAX_CONCURRENT=2`) |
| Verdict accuracy (34 labels) | **33 / 34** as expected |
| Pre-fill field accuracy (34 labels) | 185 / 193 checked values correct (95.9%); beverage type 34 / 34 |
| Memory (JVM resident set) | 432 MB idle → 775 MB peak at 8 concurrent requests (default JVM, no heap cap) |

**In short:** a label is read and judged in about half a second, almost all of it OCR. The OCR concurrency limit keeps memory in check without failing requests, and images 1200–1600 px wide are the fastest to process.

## How it was measured

| Item | Value |
|------|-------|
| Machine | Apple M3 Pro, 11 cores, 18 GB RAM, macOS 26.5 |
| Runtime | OpenJDK 21.0.7, default JVM options (no `JAVA_OPTS`) |
| OCR | Tesseract 5.5.3 via Tess4J, `OCR_MAX_CONCURRENT=2` (default) |
| Application | Packaged jar, `demo` profile (in-memory H2), demo sign-in |
| Client | `curl` from the same machine, through the web endpoints a browser uses (session, CSRF, multipart) |
| Images | 34 synthetic 1600×2000 px labels (fictional brands), 54 KB – 4.1 MB, PNG and JPEG |
| Method | Pre-fill: median of 3 warm passes per image. Analysis: one submission per image, time read from the label page (`Analyzed by … in N ms`). The cold figure is the first request after startup. |

**The 34 labels:**

- **14 clean** labels: spirits, wine and malt beverages in a range of fonts and colors.
- **9 degraded** images: rotated 3°, 5×5 blur, JPEG quality 0.25, shrunk to 640 px, heavy noise, light text on dark, low contrast, a monospaced font, and 2° rotation combined with JPEG.
- **11 flawed on purpose:** a missing, title-case or truncated warning, an illegal container size, mismatched ABV, net contents, address or fanciful name, no sulfite line, and a degraded image with no warning.

## Why a full analysis takes about twice as long as pre-fill

| Step | OCR passes | Typical |
|------|------------|---------|
| Pre-fill | One pass with automatic layout (PSM 3), which also returns line heights to find the brand | ~290 ms |
| Analysis | Two passes, sparse text (PSM 11) and single block (PSM 6), merged for the best chance of reading both decorative front text and the small-print warning | ~530 ms |

Searching the text and comparing fields takes only a few milliseconds. Nearly all of the time goes to OCR.

## Results by image group

Server-side times, in milliseconds.

| Group | Labels | Pre-fill p50 / max | Analysis p50 / max | Submit round trip p50 / max | Values pre-filled (avg) | Verdicts as expected |
|-------|--------|--------------------|--------------------|-----------------------------|-------------------------|----------------------|
| Clean | 14 | 292 / 307 | 535 / 567 | 554 / 587 | 9.1 | 14 / 14 |
| Degraded | 9 | 307 / 501 | 558 / 793 | 576 / 813 | 8.3 | 9 / 9 |
| Flawed | 11 | 282 / 294 | 523 / 551 | 541 / 569 | 8.2 | 10 / 11 ¹ |
| **All** | **34** | **290 / 501** | **532 / 793** | **552 / 813** | **8.6** | **33 / 34** |

¹ A declared fanciful name that isn't printed on the label is still approved, because a missing *optional* field is ignored by design ([ai-pipelines.md](ai-pipelines.md#turning-field-results-into-a-verdict)).

## Results for each image

| # | Label | Condition | Size | Pre-fill ms | Values | Analysis ms | Round trip ms | Verdict |
|---|-------|-----------|------|------------:|-------:|------------:|--------------:|---------|
| 01 | Ember Ridge bourbon | clean | 157 KB | 307 | 10 | 567 | 587 | Approved |
| 02 | Coldharbor gin | clean | 125 KB | 280 | 8 | 513 | 532 | Approved |
| 03 | Saltmarsh rum | clean | 131 KB | 291 | 9 | 526 | 545 | Approved |
| 04 | Kestrel Peak rye | clean | 138 KB | 300 | 9 | 563 | 584 | Approved |
| 05 | Blue Fennel vodka | clean | 122 KB | 278 | 8 | 504 | 523 | Approved |
| 06 | Lantern Hill brandy | clean | 139 KB | 286 | 8 | 524 | 542 | Approved |
| 07 | Fernhollow pinot noir | clean | 143 KB | 305 | 12 | 549 | 568 | Approved |
| 08 | Stonewick cabernet | clean | 146 KB | 304 | 12 | 567 | 586 | Approved |
| 09 | Larkspur Vale sauvignon blanc | clean | 139 KB | 296 | 10 | 542 | 561 | Approved |
| 10 | Copperleaf riesling | clean | 131 KB | 291 | 10 | 528 | 547 | Approved |
| 11 | Granite Falls IPA | clean | 131 KB | 293 | 7 | 543 | 562 | Approved |
| 12 | Riverbend stout | clean | 142 KB | 293 | 9 | 550 | 569 | Approved |
| 13 | Pinecone pilsner | clean | 117 KB | 273 | 8 | 491 | 509 | Approved |
| 14 | Old Mill porter | clean | 120 KB | 276 | 8 | 504 | 522 | Approved |
| 15 | Harbor Light amber | rotated 3° | 474 KB | 330 | 6 | 571 | 591 | Approved |
| 16 | Wild Oat wheat | blurred | 272 KB | 271 | 8 | 495 | 513 | Approved |
| 17 | Oxbow Bend whiskey | JPEG q 0.25 | 122 KB | 298 | 8 | 547 | 565 | Approved |
| 18 | Halcyon Bluff merlot | 640 px wide | 54 KB | 501 | 10 | 793 | 813 | Approved |
| 19 | Marrow Creek tequila | light on dark | 141 KB | 420 | 9 | 781 | 799 | Approved |
| 20 | Brightwater rosé | heavy noise | 4.1 MB | 333 | 9 | 623 | 656 | Approved |
| 21 | Ashgrove gin | low contrast | 115 KB | 271 | 8 | 501 | 519 | Approved |
| 22 | Foxglove cider | monospaced font | 136 KB | 307 | 9 | 558 | 576 | Approved |
| 23 | Tall Pine whiskey | rotated 2° + JPEG | 120 KB | 288 | 8 | 540 | 557 | Approved |
| 24 | Greystone vodka | no warning | 71 KB | 161 | 8 | 283 | 302 | Rejected |
| 25 | Cinderbrook rum | title-case warning | 130 KB | 282 | 8 | 527 | 546 | Rejected |
| 26 | Salt Flats lager | warning missing clause (2) | 95 KB | 224 | 8 | 397 | 416 | Rejected |
| 27 | Mossgate chardonnay | 200 mL wine declared | 136 KB | 289 | 10 | 524 | 543 | Rejected |
| 28 | Iron Lantern bourbon | ABV 43% declared, 45% on label | 132 KB | 294 | 8 | 545 | 562 | Needs correction |
| 29 | Willowmere zinfandel | 1.5 L declared, 750 mL on label | 135 KB | 290 | 10 | 551 | 569 | Needs correction |
| 30 | Northern Tier pils | different fanciful name | 127 KB | 290 | 7 | 523 | 541 | Approved ¹ |
| 31 | Red Canyon lager | malt ABV 6.0% vs 5.5% | 126 KB | 282 | 8 | 514 | 534 | Conditionally approved |
| 32 | Birch Hollow pinot gris | no sulfite line | 128 KB | 288 | 7 | 537 | 556 | Needs correction |
| 33 | Silver Heron gin | different city | 118 KB | 270 | 8 | 492 | 509 | Needs correction |
| 34 | Dusk Harbor rum | rotated + JPEG, no warning | 88 KB | 165 | 8 | 308 | 326 | Rejected |

**What makes an image slower or faster:**

- **Upscaling matters most.** Anything under 1024 px is enlarged to 2048 px before OCR. #18 took 70% longer because of it.
- **Light text on a dark background** is slower. #19 took 45% longer.
- **Less text means less time.** Labels with no warning block finished in about half the time (#24, #34).
- **File size barely matters.** The 4.1 MB noisy PNG (#20) took only 15% longer than a clean 130 KB label.

## Concurrency and throughput

All 34 images were sent through pre-fill with 1, 2, 4 and 8 clients running in parallel.

| Parallel clients | Wall time (34 images) | Throughput | Latency p50 | Latency p95 | Max | Peak memory (RSS) |
|-----------------:|----------------------:|-----------:|------------:|------------:|----:|------------------:|
| 1 | 10.4 s | 3.3 /s | 305 ms | 364 ms | 515 ms | 685 MB |
| 2 | 5.6 s | 6.1 /s | 320 ms | 376 ms | 548 ms | 698 MB |
| 4 | 5.3 s | 6.4 /s | 621 ms | 766 ms | 886 ms | 714 MB |
| 8 | 5.4 s | 6.2 /s | 1,243 ms | 1,523 ms | 1,534 ms | 775 MB |

- **Throughput doubles going from 1 client to 2, then levels off.** `OCR_MAX_CONCURRENT=2` lets two OCR jobs run at once, and the others wait their turn in a fair queue.
- **Past that limit, latency rises in proportion** (about 2× with 4 clients, 4× with 8), but nothing fails or times out. That is the intended back-pressure, and it protects the OCR engine from memory spikes.
- On this 11-core machine, a higher `OCR_MAX_CONCURRENT` would buy throughput at the cost of memory. On a 512 MB container, keep it at 1, which is the `railway` profile's default ([deploy-railway.md](deploy-railway.md)).

## Effect of image width

Label #01 (Ember Ridge bourbon) was resized to each width, then pre-filled and fully submitted.

| Width | File | Pre-fill ms | Values | Verdict | Note |
|------:|-----:|------------:|-------:|---------|------|
| 400 px | 69 KB | 569 | 10 | Approved | Upscaled to 2048 px |
| 640 px | 133 KB | 532 | 10 | Approved | Upscaled |
| 800 px | 178 KB | 527 | 10 | Approved | Upscaled |
| 1000 px | 237 KB | 527 | 10 | Approved | Upscaled |
| 1200 px | 297 KB | 262 | 10 | Approved | Native size |
| 1600 px | 157 KB | 318 | 10 | Approved | Native size (original) |
| 2400 px | 586 KB | 456 | 10 | Approved | Native size |

- **1200–1600 px is the sweet spot.** Below 1024 px the upscaling roughly doubles the time, and above 1600 px OCR just has more pixels to work through.
- Sharp synthetic text survives even at 400 px, because the upscaler restores clean edges. **Real camera photos are less forgiving.** For real labels, stick to about 1000 px or wider so the small-print warning stays readable.

## Memory

| State | Resident set (RSS) |
|-------|-------------------:|
| After startup, before any OCR | 432 MB |
| Sequential load (1 client) | 685 MB peak |
| 8 parallel clients | 775 MB peak |
| After the run | 749 MB |

These numbers use the **default JVM**, which on this 18 GB machine can grow its heap to about a quarter of RAM, so memory expands freely. With the Dockerfile's container settings (`-Xmx256m`, serial GC, one OCR job at a time), a similar workload peaked at **402 MB** ([deploy-railway.md](deploy-railway.md#measured-locally-under-the-same-limits)).

## What these numbers don't show

- **Real photos.** Glare, curvature, perspective and embossing will make real labels slower and harder to read. Build a benchmark from real, consented submissions before committing to service levels ([production.md](production.md#5-ai-pipeline)).
- **Network time.** Everything ran on one machine over loopback. Uploading a 150 KB label over a typical connection adds roughly 50–200 ms.
- **Database time.** In-memory H2 makes it negligible. PostgreSQL adds a few milliseconds per submission.
- **The cloud pipeline.** Google Vision plus OpenAI usually takes 2–5 s per label, and it wasn't measured here.
