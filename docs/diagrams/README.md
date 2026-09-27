# Diagrams

The Mermaid source of every diagram in the docs. Most Git hosting sites render them directly, as do IntelliJ and VS Code with a Mermaid extension. All seventeen parse cleanly with Mermaid 11.

## Architecture (used in [../architecture.md](../architecture.md))

| File | Type | Shows |
|------|------|-------|
| [01-system-context.mmd](01-system-context.mmd) | flowchart | Context view: people and external systems |
| [17-containers.mmd](17-containers.mmd) | flowchart | Container view: runtime units and data stores |
| [02-components.mmd](02-components.mmd) | flowchart | Layered component view: packages by layer |
| [03-submission-sequence.mmd](03-submission-sequence.mmd) | sequence | Submission in three phases: intake, analysis outside any transaction, results |
| [04-review-sequence.mmd](04-review-sequence.mmd) | flowchart | The four decision paths: batch approve, field review, override, re-analyze |
| [05-ai-pipeline.mmd](05-ai-pipeline.mmd) | flowchart | Verification engine as Read → Locate → Judge |
| [06-label-status.mmd](06-label-status.mmd) | state | Label lifecycle, grouped by who is waiting |
| [07-erd.mmd](07-erd.mmd) | ER | Database schema (V1–V3, including image blobs and sessions) |
| [08-security.mmd](08-security.mmd) | flowchart | Filter chains, method security, data scoping |

## Infrastructure (used in [../infrastructure.md](../infrastructure.md))

| File | Type | Shows |
|------|------|-------|
| [09-infra-local.mmd](09-infra-local.mmd) | flowchart | Developer workstation, demo and standard modes |
| [10-infra-container.mmd](10-infra-container.mmd) | flowchart | Image build and compose topology |
| [11-infra-production.mmd](11-infra-production.mmd) | flowchart | Production reference topology (cloud-neutral) |
| [12-network-zones.mmd](12-network-zones.mmd) | flowchart | Zones and the numbered flows that are allowed |
| [13-cicd-pipeline.mmd](13-cicd-pipeline.mmd) | flowchart | Build → Verify → Release → Operate |
| [14-scaling-target.mmd](14-scaling-target.mmd) | sequence | Queue-based asynchronous analysis |
| [15-infra-railway.mmd](15-infra-railway.mmd) | flowchart | Railway deployment (used in [../deploy-railway.md](../deploy-railway.md)) |

## UI (used in [../ui.md](../ui.md))

| File | Type | Shows |
|------|------|-------|
| [16-ui-page-anatomy.mmd](16-ui-page-anatomy.mmd) | flowchart | How a page is assembled from shared fragments |

## Export

```bash
npx -y @mermaid-js/mermaid-cli -i docs/diagrams/11-infra-production.mmd -o docs/diagrams/11-infra-production.svg
```

The documents embed these sources as they are, minus the `%%` comment lines. When you change a diagram, update both the `.mmd` file and the document that embeds it.
