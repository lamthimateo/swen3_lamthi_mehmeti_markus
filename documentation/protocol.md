# SWEN3 Document Management – Documentation

## Overview

The system lets users upload documents, browse them in a paginated list, label them and delete them. Uploading a document also sends a job to a message queue, where a background worker picks it up. The worker currently only logs each job; text extraction itself is not implemented yet.

| Part                 | Technology                                                           |
| -------------------- | -------------------------------------------------------------------- |
| REST API             | Spring Boot 4.1.1, Java 25, Spring Data JPA                          |
| Web frontend         | React (Vite), served by nginx                                        |
| Database             | PostgreSQL 17                                                        |
| Message broker       | RabbitMQ 4                                                           |
| Background worker    | Spring Boot (AMQP listener)                                          |
| File storage, search | MinIO, Elasticsearch (in the compose file, not yet used by the code) |
| Deployment           | Docker Compose                                                       |

On upload, the API stores the document row and publishes a job message to RabbitMQ; the worker consumes it asynchronously. The `docker-compose.yml` starts every service together.

## Architecture

The REST API never calls the OCR worker directly. It leaves a job on a queue, and the worker takes it when it is ready. All services run as containers from one `docker-compose.yml`.

## Design decisions

OCR runs in a separate worker behind a queue, so an upload stays fast and OCR failures cannot break the API.

| Decision                                                | Why                                                                                             | Trade-off                                           |
| ------------------------------------------------------- | ----------------------------------------------------------------------------------------------- | --------------------------------------------------- |
| Queue (RabbitMQ) between REST and worker                | Upload returns immediately; the worker can crash, restart or be scaled without touching the API | One more service to run and monitor                 |
| Separate `ocr-worker` module and container              | Independent deployment and failure; OCR will be CPU-heavy                                       | Message class is duplicated in both projects        |
| JSON message `{documentId, filename}`                   | Readable in the RabbitMQ UI, no Java serialization coupling between services                    | Both sides must agree on field names                |
| Message carries only the ID and filename                | Small messages; the worker loads the file itself later                                          | Worker needs access to storage and database         |
| Durable queue `ocr.queue`, declared by both services    | Survives a broker restart; declaration is idempotent, so start order does not matter            | Queue name must be kept in sync in two config files |
| Publisher in its own class (`OcrJobPublisher`)          | Controller stays small; easy to mock in `@WebMvcTest`                                           | None significant                                    |
| Host name `rabbitmq` set through `SPRING_RABBITMQ_HOST` | Inside Docker, `localhost` is the container itself; local runs keep the `localhost` default     | Needs the environment variable in compose           |

Open point: if RabbitMQ is down, `POST /api/documents` currently returns a 500 after the document row was saved. Whether OCR is required for an upload to count as successful is still open: the publisher logs the failure and rethrows it, so the upload currently fails visibly.

## Components

**swen3rest** (port 8081) is the REST API. Controllers under `/api/documents` and for labels use Spring Data repositories for `Document`, `Label` and `DocumentLabel`. The document list is paginated (default 20, maximum 100) and sortable by `id`, `filename` or `uploadDate`; unknown sort fields return 400. Deleting a document removes its label links in the same transaction. The `messaging` package holds `OcrJobMessage`, `RabbitConfig` and `OcrJobPublisher`.

The REST controllers and the OCR job publisher write log messages for requests and for publish failures.

**swen3ui** (port 80) is the React frontend, built with Vite and served by nginx. CORS for `localhost` and the Vite dev server is configured in the REST service.

**ocr-worker** has no web server. `OcrMessageListener` reads `OcrJobMessage` objects from `ocr.queue` and logs the document ID and filename. Its Jackson dependency is explicit because the AMQP starter does not bring one.

**Infrastructure** in `docker-compose.yml`:

| Service       | Port        | Role                               |
| ------------- | ----------- | ---------------------------------- |
| postgres      | 5433 (host) | Document and label metadata        |
| rabbitmq      | 5672, 15672 | Queue; management UI on 15672      |
| minio         | 9000, 9001  | Object storage for files (planned) |
| elasticsearch | 9200        | Full-text search (planned)         |

The `swen3rest` and `ocr-worker` containers wait for RabbitMQ's healthcheck before starting; `swen3rest` also waits for PostgreSQL.

## Testing strategy

Tests run without any external service: web-layer tests mock the data layer, and persistence tests use an in-memory H2 database instead of PostgreSQL.

| Level      | What exists                                                                                              | Tooling                                            |
| ---------- | -------------------------------------------------------------------------------------------------------- | -------------------------------------------------- |
| Repository | `DocumentRepositoryTest`, `LabelRepositoryTest`, `DocumentLabelRepositoryTest`                           | Spring Data JPA test slice, H2                     |
| Controller | `DocumentControllerTest`, `DocumentLabelControllerTest`: pagination, sort validation, delete order, CORS | `@WebMvcTest`, `MockMvc`, Mockito (`@MockitoBean`) |
| Context    | `Swen3restApplicationTests.contextLoads`                                                                 | `@SpringBootTest` with H2                          |

`OcrJobPublisher` is a `@MockitoBean` in `DocumentControllerTest`, so controller tests do not need a RabbitMQ broker.

Messaging tests, and what is still planned:

- Covered: `OcrJobPublisherTest` checks the queue name and payload sent to `RabbitTemplate`, and that a failure is logged and rethrown when the broker is unreachable. No broker is needed.
- Covered: `DocumentControllerTest` upload cases: a saved upload publishes one job, and an empty file returns 400 and publishes nothing.
- Covered: `OcrMessageListenerTest` in `ocr-worker` checks that the listener logs the document id and filename.
- Planned: an end-to-end test of upload to queue to worker, for example with Testcontainers.
- Planned: unit tests for a service layer, once it exists.

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

| Problem                                                                                          | Cause                                                                                                                               | Fix                                                                                        |
| ------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| `swen3rest` image failed with "Child module /build/messaging does not exist"                     | A generated `pom.xml` had `<packaging>pom</packaging>` and a `<modules>` entry, but the Dockerfile only copies `pom.xml` and `src/` | Remove both; the messaging code is a package inside the service, not a Maven module        |
| `ocr-worker` crashed at startup with `NoClassDefFoundError: tools/jackson/core/JacksonException` | The AMQP starter does not include Jackson, and the JSON message converter needs it                                                  | Add `spring-boot-starter-jackson` to the worker                                            |
| `POST /api/documents` returned 500 with `AmqpConnectException: Connection refused`               | The REST container tried `localhost:5672`, which is itself                                                                          | Set `SPRING_RABBITMQ_HOST: rabbitmq` in `docker-compose.yml`                               |
| Failed uploads still created documents                                                           | The row is saved before the message is sent                                                                                         | Decide whether publishing should be inside the transaction or non-fatal (open point above) |

General takeaways:

- Read the first `Caused by:` line of a stack trace; the visible error is often a wrapper.
- Inside Docker Compose, use service names as host names and set them through environment variables.
- Build each service from a single directory context, so anything it needs must live inside that directory.
- A queue gives clean separation, but sender and receiver must agree on the message format; a shared record or contract test would remove the duplicated class.

## Tracked work and repository

Repository: [swen3_lamthi_mehmeti_markus](https://github.com/lamthimateo/swen3_lamthi_mehmeti_markus)

Issue Tracking: [Notion] (https://app.notion.com/p/SWEN-3-Project-3db95a51409480b4a2b5f74d70324ced?source=copy_link)

To run everything:

```bash
docker compose up --build
```

- UI: http://localhost
- REST API: http://localhost:8081/api/documents
- RabbitMQ management UI: http://localhost:15672 (guest / guest)
- Worker output: `docker logs swen3-ocr-worker`

Links still to add: issue board, CI run, architecture diagram source.
