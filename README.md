# Sunbird Lern Service

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Target](https://img.shields.io/badge/Target-Java%2011-orange.svg)](https://www.oracle.com/java/technologies/javase-jdk11-downloads.html)
[![Framework](https://img.shields.io/badge/Framework-Play%203.0.5-green.svg)](https://www.playframework.com/)

Sunbird Lern is a comprehensive Play Framework service for learning infrastructure. It provides REST APIs for managing user identities, learning workflows, and notifications within the Sunbird ecosystem. The service integrates with YugabyteDB, OpenSearch, Kafka, and Redis to deliver high-scale educational capabilities.

---

## Table of Contents
1. [Key Modules](#key-modules)
2. [Technical Stack & Prerequisites](#technical-stack--prerequisites)
3. [Local Development Setup](#local-development-setup)
4. [Optional Features](#optional-features)
5. [System Dependencies](#system-dependencies--external-integrations)

---

## Key Modules

The service is composed of four primary functional modules:

| Module | Description |
| :--- | :--- |
| **UserOrg** | Identity and Organization management — SSO, RBAC, managed users, bulk onboarding. |
| **LMS** | Learning Management — course batches, enrollment, progress tracking, competency assessment. |
| **Notification** | Multi-channel communication — Email, in-app feeds, SMS triggers with dynamic templating. |
| **Lern Service** | Unified aggregator bundling UserOrg, LMS, and Notification into a single runtime. |

Shared abstractions across modules reside in **core**: telemetry, Pekko actors, database drivers, and Elasticsearch utilities.

---

## Technical Stack & Prerequisites

Lern is engineered using a reactive, non-blocking architecture. Ensure the following are installed before proceeding:

*   **Java 11** (LTS) — `java -version`
*   **Maven 3.8.0+** — `mvn -version`
*   **Docker Desktop** — `docker --version` (6 GB RAM minimum recommended)
*   **Git** — `git --version`

Runtime and framework details:

*   **Web Engine:** Play Framework 3.0.5
*   **Reactive Core:** Apache Pekko 1.0.3 (Distributed Actor System)
*   **Data Persistence:**
    *   **YugabyteDB:** Dual-driver architecture — Cassandra (9042) for document stores, Postgres (5433) for relational data.
    *   **OpenSearch:** Distributed full-text search engine (9200).
    *   **Redis:** Optional distributed cache layer.
*   **Messaging:** Apache Kafka (9092) for event-driven workflows and notification dispatch.

---

## Local Development Setup

### Step 1 — Clone the Repository
```bash
git clone https://github.com/sunbird-lern/lern-service.git
cd lern-service
```

### Step 2 — Set Up Keycloak

The Keycloak Docker image must be built before starting the stack. This script sparse-clones the Keycloak setup from [sunbird-spark-installer](https://github.com/Sunbird-Spark/sunbird-spark-installer), generates the local realm configuration, and builds the image:

```bash
chmod +x scripts/setup-keycloak.sh
./scripts/setup-keycloak.sh
```

> This fetches ~10 MB from GitHub and builds the image — expect a few minutes on the first run. Subsequent builds reuse the Docker layer cache and are much faster. The `develop` branch of the installer is used by default; pass a branch name as the first argument to override.

### Step 3 — Start Infrastructure

Spin up YugabyteDB, OpenSearch, Kafka, and Keycloak using Docker Compose:
```bash
docker-compose up -d
docker-compose ps
```
**On first boot, Keycloak takes approximately 4–5 minutes** to run its Liquibase schema migrations against YugabyteDB and import the Sunbird realm. Subsequent starts are much faster (under 30 seconds) since the schema and realm are already persisted. Wait until all services show `Up` before running the init scripts.

### Step 4 — Initialize Services

Run these once after the containers are up for the first time.

**YugabyteDB schema migrations:**
```bash
chmod +x scripts/init-yugabyte.sh
./scripts/init-yugabyte.sh
```

**OpenSearch index setup:**
```bash
chmod +x scripts/init-opensearch.sh
./scripts/init-opensearch.sh
```

**Keycloak public key extraction:**

In production, the Keycloak RSA public key is mounted as a Kubernetes ConfigMap volume at `/keys/{kid}`. This script replicates that locally — it waits for Keycloak to be ready, then saves the public key to `keys/{kid}` at the project root:
```bash
chmod +x scripts/init-keycloak.sh
./scripts/init-keycloak.sh
```

> Requires `jq`. Install with `brew install jq` on macOS. You only need to run all three scripts once — re-running on an already-initialized instance is safe.

### Step 5 — Configure Environment
Copy the template and fill in your local values:
```bash
cp scripts/env-variables.example .env
```
Edit `.env` with your local credentials, then source it in the **same terminal session** you will use to run the service:
```bash
source .env
```

The service reads all configuration from environment variables at startup. Key variables to verify:

OpenSearch — these must be **lowercase**:
```bash
sunbird_es_host="localhost"
sunbird_es_port="9200"
sunbird_es_cluster="sunbird"
```

YugabyteDB:
```bash
SUNBIRD_CASSANDRA_HOST="localhost"
SUNBIRD_CASSANDRA_PORT="9042"
SUNBIRD_POSTGRES_HOST="localhost"
SUNBIRD_POSTGRES_PORT="5433"
```

Kafka:
```bash
SUNBIRD_KAFKA_URL="localhost:9092"
```

### Step 6 — Build & Run

The service reads its Keycloak public key from the directory set by `accesstoken.publickey.basepath`. This config key contains dots, making it invalid as a bash variable name — use the `env` command to inject it at the OS level.

#### Linux

**Build:**
```bash
chmod +x scripts/build-local.sh
./scripts/build-local.sh --service lern
```
> For all build options (CSP, running tests, building other services), see [scripts/README.md](scripts/README.md).

**Run:**
```bash
# From the project root
env "accesstoken.publickey.basepath=$(pwd)/keys/" \
  mvn -f modules/lern/service/pom.xml play2:run
```

#### macOS

The `application.conf` files are bundled into the distribution at build time. Before building, change `transport` from `"native"` to `"jdk"` in all four module configs — Netty's epoll transport is Linux-only and will fail at startup on macOS:
- [modules/lern/service/conf/application.conf:376](modules/lern/service/conf/application.conf#L376)
- [modules/lms/service/conf/application.conf:278](modules/lms/service/conf/application.conf#L278)
- [modules/notification/service/conf/application.conf:83](modules/notification/service/conf/application.conf#L83)
- [modules/userorg/controller/conf/application.conf:720](modules/userorg/controller/conf/application.conf#L720)

**Build:**
```bash
chmod +x scripts/build-local.sh
./scripts/build-local.sh --service lern
```
> For all build options (CSP, running tests, building other services), see [scripts/README.md](scripts/README.md).

**Run** — the Play2 Maven plugin's file watcher fails on macOS; run via the built distribution instead:
```bash
# From the project root
DIST="modules/lern/service/target/lern-service-impl-1.0-SNAPSHOT"
env "accesstoken.publickey.basepath=$(pwd)/keys/" "$DIST/start" -Dhttp.port=9000
```

### Step 7 — Verify
```bash
curl http://localhost:9000/health
```
Expected response:
```json
{
  "id": "api.all.health",
  "ver": "health",
  "params": { "status": "SUCCESS", "err": null },
  "responseCode": "OK",
  "result": {
    "response": {
      "checks": [
        { "name": "Learner service", "healthy": true },
        { "name": "Actor service", "healthy": true },
        { "name": "Cassandra service", "healthy": true },
        { "name": "OpenSearch service", "healthy": true }
      ],
      "healthy": true,
      "name": "Complete health check api"
    }
  }
}
```

---

## Optional Features

### Redis Caching
Redis is optional and disabled by default. To enable it, set the following environment variables:

```bash
export sunbird_redis_host=localhost
export sunbird_redis_port=6379
```

Then change `redis.enabled` from `false` to `true` in [modules/lern/service/conf/application.conf](modules/lern/service/conf/application.conf):

```
redis.enabled = true
```

This config key cannot be set via an environment variable (dots are invalid in bash variable names), so the `application.conf` edit is required.

### Cloud Storage Configuration

Lern requires cloud storage for certificates, user artifacts, and content objects. Configure one of the following providers before deployment.

> For local development, set `SUNBIRD_CLOUD_STORAGE_AUTH_TYPE=ACCESS_KEY` and provide the account credentials. The default auth type is `OIDC` (Kubernetes Workload Identity), which does not require a key but is only applicable in a Kubernetes environment.

#### Azure Blob Storage (Default)
```bash
export SUNBIRD_CLOUD_SERVICE_PROVIDER=azure
export SUNBIRD_CLOUD_STORAGE_AUTH_TYPE=ACCESS_KEY
export SUNBIRD_ACCOUNT_NAME=your-account-name
export SUNBIRD_ACCOUNT_KEY=your-account-key
export SUNBIRD_CONTENT_CLOUD_STORAGE_CONTAINER=your-container-name
```

#### AWS S3
```bash
export SUNBIRD_CLOUD_SERVICE_PROVIDER=aws
export SUNBIRD_CLOUD_STORAGE_AUTH_TYPE=ACCESS_KEY
export SUNBIRD_ACCOUNT_NAME=your-access-key-id
export SUNBIRD_ACCOUNT_KEY=your-secret-access-key
export SUNBIRD_CONTENT_CLOUD_STORAGE_CONTAINER=your-s3-bucket-name
```

#### Google Cloud Storage
```bash
export SUNBIRD_CLOUD_SERVICE_PROVIDER=gcloud
export SUNBIRD_CLOUD_STORAGE_AUTH_TYPE=ACCESS_KEY
export SUNBIRD_ACCOUNT_NAME=your-client-email
export SUNBIRD_ACCOUNT_KEY=/path/to/service-account-key.json
export SUNBIRD_CONTENT_CLOUD_STORAGE_CONTAINER=your-gcs-bucket-name
```

---

## System Dependencies & External Integrations

Lern integrates with the following external systems, configured via environment variables (see `scripts/env-variables.example`):

*   **Primary Data Store:** YugabyteDB with dual-driver support (Cassandra for document-oriented workloads, Postgres for relational).
*   **Search & Analytics:** OpenSearch (9200) for full-text indexing and aggregated queries.
*   **Event Streaming:** Apache Kafka (9092) for asynchronous notifications, certificate issuance, and audit trails.
*   **Caching Layer:** Redis (optional, 6379) for session storage and query result caching.
*   **Identity Provider:** Keycloak for OIDC/SAML federation and secure authentication.
*   **Email Transport:** SMTP server for transactional email delivery.
*   **Cloud Storage:** Azure Blob Storage, AWS S3, or Google Cloud Storage for content, certificates, and artifacts.
*   **Knowlg & Search Service:** Lern depends on the Knowlg knowledge platform for content reads and search. Refer to the [Sunbird Knowlg — knowledge-platform](https://github.com/Sunbird-Knowlg/knowledge-platform) repository for setup instructions.

---

## License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.
