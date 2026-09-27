# Test labels

Synthetic, **fictional** labels for demos and pipeline benchmarks. Every brand, company and address is invented. Each folder holds `front.png` (1600×2000 px) and `application.json`, the Form 5100.31 values an applicant would declare.

| Folder | Type | Expected outcome |
|--------|------|------------------|
| `aldercrest-bourbon/` | Distilled spirits, 750 mL | Approved — all 9 fields match |
| `quillmoor-chardonnay/` | Wine, 750 mL | Approved — all 8 fields match |
| `tidewater-lager/` | Malt beverage, 355 mL | Approved — all 8 fields match |
| `northvale-vodka-flawed/` | Distilled spirits, 750 mL | **Rejected** — label says 40% (application 42%), and the warning prefix is not in capitals |

Regenerate them (no dependencies beyond Java 21):

```bash
java scripts/SampleLabelGenerator.java test-labels
```

## Submit one through the API

Export the applicant credentials first (see [docs/api.md](../docs/api.md)); then:

```bash
curl -u "$API_USER:$API_PASSWORD" -X POST http://localhost:8080/api/v1/labels -F images=@test-labels/aldercrest-bourbon/front.png -F beverageType=DISTILLED_SPIRITS -F containerSizeMl=750 -F brandName=Aldercrest -F fancifulName="Small Batch" -F classType="Kentucky Straight Bourbon Whiskey" -F alcoholContent="45% Alc./Vol." -F netContents="750 mL" -F qualifyingPhrase="Distilled and Bottled by" -F nameAndAddress="Aldercrest Distilling Co., Bardstown, Kentucky" -F ageStatement="Aged 6 Years"
```

## Batch upload

[`batch-example.csv`](batch-example.csv) submits all four at once. Image filenames in a batch must be unique, so copy the images to one folder under the names the CSV uses:

```bash
mkdir -p /tmp/batch && for d in aldercrest-bourbon tidewater-lager quillmoor-chardonnay northvale-vodka-flawed; do cp test-labels/$d/front.png /tmp/batch/$d.png; done
```

Then, on **Batch upload**, choose the CSV and the four images from `/tmp/batch`.
