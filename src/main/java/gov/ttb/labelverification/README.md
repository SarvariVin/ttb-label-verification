# `gov.ttb.labelverification` — package guide

Dependencies point downward: `web → service → ai / labels / regulatory → domain / repository / storage`. See [ADR-0002](../../../../../../docs/adr/0002-modular-monolith-with-pure-rule-packages.md).

| Package | Contents | Notes |
|---------|----------|-------|
| `regulatory` | `BeverageType`, `FieldName` + `MatchStrategy`, `HealthWarning`, `QualifyingPhrases`, `RegulatoryConstants` | Plain Java. Change regulatory rules here. |
| `labels` | `StatusDeterminer`, `EffectiveStatus`, `Deadlines`, `ExpectedFields`, `SlaStatus` | Plain Java; time via an injected `Clock` |
| `ai` | `ExtractionPipeline` contract, result records, `BeverageDetector` | |
| `ai.compare` | `FieldComparator`, `OcrTextSearch`, `TextNormalizer` | Plain Java; heavily unit-tested. Near misses are compared, not assumed ([ADR-0020](../../../../../../docs/adr/0020-near-misses-are-compared-not-assumed.md)) |
| `ai.prefill` | `LabelFieldExtractor`: OCR lines → suggested form values | Plain Java; unit-tested |
| `ai.ocr` | `OcrEngine`, `TesseractOcrEngine` (Tess4J), `GoogleVisionOcrEngine` (REST) | |
| `ai.local` | `LocalExtractionPipeline` | Default pipeline |
| `ai.cloud` | `CloudExtractionPipeline`, `OpenAiFieldClassifier`, `ClassificationPrompts`, `BoundingBoxMath` | Needs both API keys |
| `domain` | JPA entities and enums, `Ids`, `BaseEntity` | `HumanReview`, `StatusOverride` are append-only |
| `repository` | Spring Data repositories | Entity graphs for view queries |
| `storage` | `ImageStorage`, `LocalImageStorage`, `DatabaseImageStorage`, `ImageFileValidator` | `app.storage.type` picks filesystem or database; swap in object storage here |
| `service` | Submission, batch, analysis, extraction routing, review, queries, SLA, settings, applicants | Transactions and `@PreAuthorize` live here |
| `security` | `AppUserPrincipal`, `AppUserDetailsService`, `DemoLoginService` | Demo sign-in only when `APP_DEMO_LOGIN=true` |
| `config` | `SecurityConfig`, `AppProperties` (`app.*`), `DataSeeder`, `UserProvisioner` (`APP_USERS`), `DatabaseUrlEnvironmentPostProcessor`, `ClockConfig` | Sessions are stored in the database by Spring Session JDBC (no code; tables in Flyway `V3`) |
| `web.page` | Thymeleaf controllers, `ViewFormat` (`@fmt`), `PageExceptionHandler` | |
| `web.api` | REST controllers, DTOs, `ApiExceptionHandler` (RFC 9457) | |

## Conventions

- The API returns DTO records, never entities. Templates may read entities loaded through entity graphs (`open-in-view` is off).
- Network and AI calls never run inside a database transaction.
- Every specialist mutation writes an audit row before changing the label.
- Resources that belong to another applicant return 404.
- No credentials in code; configuration comes from `AppProperties` and the environment.
