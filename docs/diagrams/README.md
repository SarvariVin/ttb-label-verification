# Diagrams

Mermaid sources for every diagram in the documentation. They render natively in most Git hosting UIs, in IntelliJ, and in VS Code with a Mermaid extension. All fifteen parse cleanly with Mermaid 11.

## Architecture (used in [../architecture.md](../architecture.md))

| File | Type | Shows |
|------|------|-------|
| [01-system-context.mmd](01-system-context.mmd) | flowchart | Actors and external systems |
| [02-components.mmd](02-components.mmd) | flowchart | Packages and their dependencies |
| [03-submission-sequence.mmd](03-submission-sequence.mmd) | sequence | Submission, transaction boundaries, fallback |
| [04-review-sequence.mmd](04-review-sequence.mmd) | sequence | Batch approve, field review, override, re-analyze |
| [05-ai-pipeline.mmd](05-ai-pipeline.mmd) | flowchart | Local and cloud pipelines, comparison, verdict |
| [06-label-status.mmd](06-label-status.mmd) | state | Label lifecycle including lazy transitions |
| [07-erd.mmd](07-erd.mmd) | ER | Database schema |
| [08-security.mmd](08-security.mmd) | flowchart | Filter chains, method security, data scoping |

## Infrastructure (used in [../infrastructure.md](../infrastructure.md))

| File | Type | Shows |
|------|------|-------|
| [09-infra-local.mmd](09-infra-local.mmd) | flowchart | Developer workstation |
| [10-infra-container.mmd](10-infra-container.mmd) | flowchart | Container topology (compose) |
| [11-infra-production.mmd](11-infra-production.mmd) | flowchart | Production reference topology (cloud-neutral) |
| [12-network-zones.mmd](12-network-zones.mmd) | flowchart | Zones and allowed flows |
| [13-cicd-pipeline.mmd](13-cicd-pipeline.mmd) | flowchart | Build, scan, deploy, verify |
| [14-scaling-target.mmd](14-scaling-target.mmd) | sequence | Queue-based asynchronous analysis |
| [15-infra-railway.mmd](15-infra-railway.mmd) | flowchart | Railway deployment (used in [../deploy-railway.md](../deploy-railway.md)) |

## Export

```bash
npx -y @mermaid-js/mermaid-cli -i docs/diagrams/11-infra-production.mmd -o docs/diagrams/11-infra-production.svg
```

`architecture.md` and `infrastructure.md` embed these sources verbatim (without `%%` comment lines). Update both when a diagram changes.
