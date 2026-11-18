# SWEN3 Document Management – Documentation

## Overview

The system lets users upload documents, browse them in a paginated list, label them and delete them. Uploading a document also sends a job to a message queue, where a background worker picks it up. The worker fetches the PDF from MinIO, extracts its text with Ghostscript and Tesseract and stores the text in Elasticsearch.

| Part                 | Technology                                                           |
| -------------------- | -------------------------------------------------------------------- |
| REST API             | Spring Boot 4.1.1, Java 25, Spring Data JPA                          |
| Web frontend         | React (Vite), served by nginx                                        |
| Database             | PostgreSQL 17                                                        |
| Message broker       | RabbitMQ 4                                                           |
| Background worker    | Spring Boot (AMQP listeners), Ghostscript + Tesseract                |
| File storage, search | MinIO (original PDFs), Elasticsearch (OCR text)                      |
| Deployment           | Docker Compose                                                       |

On upload, the API stores the file in MinIO, saves the document row and publishes a job message to RabbitMQ in one transaction; the worker consumes the message asynchronously. The search endpoint reads the text index the worker fills. The `docker-compose.yml` starts every service together.

## Architecture

The REST API never calls the OCR worker directly. It leaves a job on a queue, and the worker takes it when it is ready. All services run as containers from one `docker-compose.yml`.

## Design decisions

OCR runs in a separate worker behind a queue, so an upload stays fast and OCR failures cannot break the API.

| Decision                                                   | Why                                                                                             | Trade-off                                           |
| ---------------------------------------------------------- | ----------------------------------------------------------------------------------------------- | --------------------------------------------------- |
| Queue (RabbitMQ) between REST and worker                   | Upload returns immediately; the worker can crash, restart or be scaled without touching the API | One more service to run and monitor                 |
| Separate `ocr-worker` module and container                 | Independent deployment and failure; OCR will be CPU-heavy                                       | Message class is duplicated in both projects        |
| JSON message `{documentId, filename}`                      | Readable in the RabbitMQ UI, no Java serialization coupling between services                    | Both sides must agree on field names                |
| Message carries only the ID and filename                   | Small messages; the worker loads the file itself later                                          | Worker needs access to storage and database         |
| Durable queue `ocr.queue`, declared by both services       | Survives a broker restart; declaration is idempotent, so start order does not matter            | Queue name must be kept in sync in two config files |
| Publisher in its own class (`OcrJobPublisher`)             | Service stays small; easy to mock in unit and `@WebMvcTest` tests                               | None significant                                    |
| Service layer behind interfaces (`DocumentService`)        | Controllers only handle HTTP; business rules are testable and the storage can change later      | One more layer and class per feature                |
| Entities never leave the service (DTO records + MapStruct) | API shape is independent of the database model; mapping code is generated, not hand-written     | Mapper must be kept in sync with the DTOs           |
| Upload is `@Transactional` and publishes inside it         | If the job cannot be published, the insert is rolled back: no document without a job            | See open point below                                |
| Host name `rabbitmq` set through `SPRING_RABBITMQ_HOST`    | Inside Docker, `localhost` is the container itself; local runs keep the `localhost` default     | Needs the environment variable in compose           |

Open point: the job is published before the transaction commits. If RabbitMQ is down, `POST /api/documents` fails with an error and the inserted row is rolled back, so no document exists without a job. The remaining risk is the reverse: the message is sent but the commit then fails, or the worker reads the document before it is visible. The usual fix is to publish after commit (transaction synchronization or the outbox pattern); this matters once the worker loads the document in Sprint 4.

## Components

**swen3rest** (port 8081) is the REST API, built in layers: controller (HTTP only) → service (business logic, transactions) → repository (Spring Data JPA for `Document`, `Label` and `DocumentLabel`). Controllers under `/api/documents` and for labels depend on service interfaces and return DTO records; MapStruct mappers convert between entities and DTOs. The document list is paginated (default 20, maximum 100) and sortable by `id`, `filename` or `uploadDate`; unknown sort fields return 400. Deleting a document removes its label links in the same transaction. The `messaging` package holds `OcrJobMessage`, `RabbitConfig` and `OcrJobPublisher`; the `storage` package holds `FileStorage` and its MinIO implementation; the `search` package holds `DocumentSearchClient` and its Elasticsearch implementation.

Upload flow: `DocumentController.upload` validates the file with the custom `@NotEmptyFile` constraint, calls `DocumentService.upload`, which saves the `Document`, publishes an `OcrJobMessage` and returns a `DocumentDto`; the controller answers `201 Created` with a `Location` header. The service saves the row (the id is needed for the file name), stores the file in MinIO under `<documentId>/<filename>` in the bucket `documents` (created on first use) and queues the OCR job; if storing or queueing fails, the row is rolled back and a stored file is removed again. Deleting a document also removes its file.

Search: `GET /api/documents/search?q=term` asks Elasticsearch (`multi_match` over the OCR text and the filename), then loads the matching rows from the database. Hits whose document was deleted since are dropped, so the database stays the source of truth. A blank term returns 400.

Errors: services throw `NotFoundException`, `ConflictException` and `InvalidRequestException`; an unreachable MinIO or Elasticsearch becomes `503 Service Unavailable` without internal details; one `@RestControllerAdvice` (`ApiExceptionHandler`) maps them and validation failures to 404, 409 and 400 as RFC 9457 problem details and logs every error response in one place.

The service layer, the exception handler and the OCR job publisher write log messages for uploads, error responses and publish failures.

**swen3ui** (port 80) is the React frontend, built with Vite and served by nginx. CORS for `localhost` and the Vite dev server is configured in the REST service.

**ocr-worker** has no web server and runs two steps connected by a second queue:

1. `OcrMessageListener` reads `OcrJobMessage` objects from `ocr.queue`, downloads the PDF from MinIO (`MinioDocumentStorage`, bucket `documents`, key `<documentId>/<filename>` unless the message carries a `storagePath`), and extracts the text with `TesseractOcrEngine`. Ghostscript renders each page to a PNG, Tesseract recognises each PNG, and the page texts are joined in order. The external programs are started through a `CommandRunner`, so tests replace them with a fake. The result goes to `index.queue` as an `IndexJobMessage`.
2. `IndexMessageListener` reads `index.queue` and `ElasticsearchDocumentIndexer` stores the text in the Elasticsearch index `documents` under the database id (`PUT /documents/_doc/<id>`), so indexing a document twice replaces the entry.

A failing message is retried three times with a growing delay, then rejected without requeue and dropped with an error log. Missing files and OCR errors are rejected at once because retrying cannot fix them. Its Jackson dependency is explicit because the AMQP starter does not bring one.

**Infrastructure** in `docker-compose.yml`:

| Service       | Port        | Role                               |
| ------------- | ----------- | ---------------------------------- |
| postgres      | 5433 (host) | Document and label metadata        |
| rabbitmq      | 5672, 15672 | Queue; management UI on 15672      |
| minio         | 9000, 9001  | Object storage for the PDFs        |
| elasticsearch | 9200        | Full-text index of the OCR text    |

The `swen3rest` and `ocr-worker` containers wait for RabbitMQ's healthcheck before starting; `swen3rest` also waits for PostgreSQL and starts after MinIO, and `ocr-worker` waits for Elasticsearch. The worker image installs Ghostscript and Tesseract (English and German language packs).

## Testing strategy

Tests run without any external service: web-layer tests mock the data layer, and persistence tests use an in-memory H2 database instead of PostgreSQL.

| Level      | What exists                                                                                                                  | Tooling                                            |
| ---------- | ---------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------- |
| Repository | `DocumentRepositoryTest`, `LabelRepositoryTest`, `DocumentLabelRepositoryTest`                                               | Spring Data JPA test slice, H2                     |
| Service    | `DocumentServiceImplTest` and the other service tests: upload, sort validation, delete, not-found cases                      | JUnit 5, Mockito                                   |
| Storage    | `MinioFileStorageTest` (bucket creation, failures, quiet delete), mock MinIO client                                            | JUnit 5, Mockito                                   |
| Search     | `ElasticsearchDocumentSearchClientTest` against a local HTTP server: hit order, escaping of the term, missing index, outages  | JUnit 5, JDK `HttpServer`                          |
| Mapper     | `MapperTest`: entity to DTO mapping                                                                                          | JUnit 5                                            |
| Controller | `DocumentControllerTest`, `DocumentLabelControllerTest`, `LabelControllerTest`: validation, status codes, error bodies, CORS | `@WebMvcTest`, `MockMvc`, Mockito (`@MockitoBean`) |
| Context    | `Swen3restApplicationTests.contextLoads`                                                                                     | `@SpringBootTest` with H2                          |

`OcrJobPublisher` is mocked in the service tests and the controller tests mock the service, so no test needs a RabbitMQ broker.

Messaging tests, and what is still planned:

- Covered: `OcrJobPublisherTest` checks the queue name and payload sent to `RabbitTemplate`, and that a failure is logged and rethrown when the broker is unreachable. No broker is needed.
- Covered: upload cases: a saved upload publishes one job, and an empty file returns 400 and publishes nothing.
- Covered: `OcrMessageListenerTest`, `IndexMessageListenerTest`, `TesseractOcrEngineTest`, `ElasticsearchDocumentIndexerTest` and `MinioDocumentStorageTest` in `ocr-worker`: the happy path, permanent failures rejected without requeue, temporary failures propagated for retry, cleanup of temporary files, page order, JSON escaping. Ghostscript and Tesseract are replaced by a fake `CommandRunner`, Elasticsearch by a local HTTP server, MinIO by a mock.
- Planned: an end-to-end test of upload to queue to worker, for example with Testcontainers.

Manual end-to-end check: upload a file in the UI, then read `docker logs swen3-ocr-worker` for the line `Received OCR job: documentId=…`.

## Development workflow

Work happens on branches and reaches `main` through pull requests. The repository history shows this, for example pull request #3 merging `chore/cleanup-duplicates`, and feature work is done on separate branches.

| Area           | Current state                                                                                                                  | To complete                                                                                              |
| -------------- | ------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------- |
| GitFlow        | `main` plus feature and chore branches, merged by pull request                                                                 | Name the branch types the team uses (`feature/`, `chore/`, `fix/`) and whether a `develop` branch exists |
| Issue tracking | Notion workspace with a sprint tracker, a task breakdown per person and a checklist per sprint                                 | Keep the sprint tracker statuses current and link the Notion page                                        |
| CI/CD          | GitHub Actions runs on pull requests and pushes: tests for both Maven services, UI lint and build, then the Docker image build | Add a status badge to the README and require the checks on `main`                                        |
| Linting        | ESLint runs in the pipeline                                                                                                    | Fix the two missing-dependency warnings in the React hooks                                               |

Commit messages are short and imperative, for example "Remove duplicate CSS, config and unused files".

## Lessons learned

Four problems while adding the queue and worker each came down to how services are configured and packaged, not to the messaging code itself.

| Problem                                                                                          | Cause                                                                                                                               | Fix                                                                                   |
| ------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| `swen3rest` image failed with "Child module /build/messaging does not exist"                     | A generated `pom.xml` had `<packaging>pom</packaging>` and a `<modules>` entry, but the Dockerfile only copies `pom.xml` and `src/` | Remove both; the messaging code is a package inside the service, not a Maven module   |
| `ocr-worker` crashed at startup with `NoClassDefFoundError: tools/jackson/core/JacksonException` | The AMQP starter does not include Jackson, and the JSON message converter needs it                                                  | Add `spring-boot-starter-jackson` to the worker                                       |
| `POST /api/documents` returned 500 with `AmqpConnectException: Connection refused`               | The REST container tried `localhost:5672`, which is itself                                                                          | Set `SPRING_RABBITMQ_HOST: rabbitmq` in `docker-compose.yml`                          |
| Failed uploads still created documents                                                           | The row was saved before the message was sent                                                                                       | Upload is `@Transactional`; a failed publish rolls the insert back (open point above) |

General takeaways:

- Read the first `Caused by:` line of a stack trace; the visible error is often a wrapper.
- Inside Docker Compose, use service names as host names and set them through environment variables.
- Build each service from a single directory context, so anything it needs must live inside that directory.
- A queue gives clean separation, but sender and receiver must agree on the message format; a shared record or contract test would remove the duplicated class.

## Tracked work and repository

Repository: [swen3_lamthi_mehmeti_markus](https://github.com/lamthimateo/swen3_lamthi_mehmeti_markus)

Issue Tracking: [Notion](https://app.notion.com/p/SWEN-3-Project-3db95a51409480b4a2b5f74d70324ced?source=copy_link)

To run everything:

```bash
docker compose up --build
```

- UI: http://localhost
- REST API: http://localhost:8081/api/documents
- RabbitMQ management UI: http://localhost:15672 (guest / guest)
- Worker output: `docker logs swen3-ocr-worker`

Links still to add: issue board, CI run, architecture diagram source.
