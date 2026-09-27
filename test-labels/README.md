# Test labels

Made-up labels for demos and pipeline benchmarks. Every brand, company and address here is **fictional**. Each folder contains a `front.png` (1600×2000 px) and an `application.json` holding the Form 5100.31 values an applicant would declare for it.

The images use the second label design (2026-09-27): a rounded frame with corner diamonds, a top accent band, a sans-serif brand, a diamond divider, ABV and net contents on tinted pills, and the health warning in a ruled panel. The text is the same as in the first design, and the real-OCR end-to-end test gives the same verdicts. Pills are tinted rather than outlined because Tesseract's automatic layout analysis skips text inside a dark outline.

| Folder | Type | Expected outcome |
|--------|------|------------------|
| `aldercrest-bourbon/` | Distilled spirits, 750 mL | Approved — all 9 fields match |
| `quillmoor-chardonnay/` | Wine, 750 mL | Approved — all 8 fields match |
| `tidewater-lager/` | Malt beverage, 355 mL | Approved — all 8 fields match |
| `northvale-vodka-flawed/` | Distilled spirits, 750 mL | **Rejected** — label says 40% (application 42%), and the warning prefix is not in capitals |

To regenerate them (Java 21 is all you need):

```bash
java scripts/SampleLabelGenerator.java test-labels
```

## Submitting one through the API

Export a test applicant's credentials first (see [docs/api.md](../docs/api.md)), then run:

```bash
curl -u "$API_USER:$API_PASSWORD" -X POST http://localhost:8080/api/v1/labels -F images=@test-labels/aldercrest-bourbon/front.png -F beverageType=DISTILLED_SPIRITS -F containerSizeMl=750 -F brandName=Aldercrest -F fancifulName="Small Batch" -F classType="Kentucky Straight Bourbon Whiskey" -F alcoholContent="45% Alc./Vol." -F netContents="750 mL" -F qualifyingPhrase="Distilled and Bottled by" -F nameAndAddress="Aldercrest Distilling Co., Bardstown, Kentucky" -F ageStatement="Aged 6 Years"
```

## Uploading all four as a batch

[`batch-example.csv`](batch-example.csv) submits all four labels in one go. Filenames within a batch must be unique, so first copy the images into one folder under the names the CSV expects:

```bash
mkdir -p /tmp/batch && for d in aldercrest-bourbon tidewater-lager quillmoor-chardonnay northvale-vodka-flawed; do cp test-labels/$d/front.png /tmp/batch/$d.png; done
```

Then open **Batch upload**, and choose the CSV and the four images from `/tmp/batch`.
