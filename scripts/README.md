# Scripts

## Overview

This directory contains scripts for local development, CI/CD builds, and Docker image creation. All scripts are designed to be run from the **project root**, not from within this directory.

**Note:** `build-local.sh` is specifically designed for **local development**. It handles Maven builds and Play distribution creation. For **CI/CD pipelines**, refer to `.github/workflows/deploy.yml` which uses the same script but orchestrates the entire build and push process.

## Scripts

### `setup-keycloak.sh` — Keycloak Docker Image Build

Sparse-clones `scripts/keycloak-21.1.2` from [sunbird-spark-installer](https://github.com/Sunbird-Spark/sunbird-spark-installer), generates `sunbird-realm.local.json` from the Helm template, and builds the `sunbird-keycloak:local` Docker image. Run this **once** before starting the stack for the first time.

**Usage:**
```bash
./scripts/setup-keycloak.sh [BRANCH]
```

**Parameters:**
- `BRANCH`: Branch of sunbird-spark-installer to use (default: `develop`)

**Example:**
```bash
# Build with defaults
./scripts/setup-keycloak.sh

# Build from a specific branch
./scripts/setup-keycloak.sh release-6.0.0
```

**What it does:**
1. Sparse-clones the Keycloak setup directory from `sunbird-spark-installer`
2. Patches the Dockerfile — corrects the realm import path for Keycloak 21 and enables the `scripts` KC feature
3. Generates `sunbird-realm.local.json` from the Helm template via `sed` (substitutes domain, secrets, SMTP, tenant values)
4. Strips Keycloak 21-incompatible JS policies and removes the unrecognized `permissions` field via a Python post-processing step
5. Copies the custom SPI JAR (`keycloak-email-phone-autthenticator-1.0-SNAPSHOT.jar`)
6. Builds and tags the image as `sunbird-keycloak:local`
7. Cleans up all temporary clone and build directories

**Prerequisites:** Docker Desktop must be running.

---

### `init-yugabyte.sh` — YugabyteDB Schema Initialization

Clones CQL migration scripts from [sunbird-spark-installer](https://github.com/Sunbird-Spark/sunbird-spark-installer) via sparse checkout and applies them to the local YugabyteDB container. Run this **once** after starting the containers for the first time.

**Usage:**
```bash
./scripts/init-yugabyte.sh [ENVIRONMENT] [BRANCH]
```

**Parameters:**
- `ENVIRONMENT`: Keyspace prefix (default: `dev`)
- `BRANCH`: Branch of sunbird-spark-installer to use (default: `develop`)

**Example:**
```bash
# Initialize with defaults
./scripts/init-yugabyte.sh

# Initialize with a specific environment prefix and branch
./scripts/init-yugabyte.sh dev release-6.0.0
```

**Prerequisites:** Docker must be running with the `yugabyte` container healthy (started via `docker-compose up -d`).

---

### `init-elasticsearch.sh` — Elasticsearch Index Initialization

Clones ES index definitions and mappings from [sunbird-devops](https://github.com/project-sunbird/sunbird-devops) via sparse checkout and applies them to the local Elasticsearch container. Run this **once** after starting the containers for the first time.

**Usage:**
```bash
./scripts/init-elasticsearch.sh [BRANCH]
```

**Parameters:**
- `BRANCH`: Branch of sunbird-devops to use (default: `release-8.0.0`)

**Example:**
```bash
# Initialize with defaults
./scripts/init-elasticsearch.sh

# Initialize from a specific branch
./scripts/init-elasticsearch.sh release-7.0.0
```

**Prerequisites:** Docker must be running with the `sunbird_es` container healthy (started via `docker-compose up -d`).

---

### `init-keycloak.sh` — Keycloak Public Key Extraction

Waits for Keycloak to be ready, fetches the RSA public key and key ID (`kid`) from the `sunbird` realm, and writes the PEM-formatted key to `keys/{kid}` at the project root. Run this **once** after starting the containers for the first time.

In production, Kubernetes mounts the key as a ConfigMap volume at `/keys/{kid}`. This script replicates that locally so `KeyManager` can load the public key at service startup.

**Usage:**
```bash
./scripts/init-keycloak.sh
```

**Parameters:** None.

**Prerequisites:**
- Keycloak container is running and the `sunbird` realm is fully imported (`docker-compose ps` shows `keycloak` as healthy)
- `jq` is installed (`brew install jq` on macOS)

---

### `env-variables.example` — Environment Variables Template

A fully-annotated template for the `.env` file that configures the service at startup. Copy it to the project root and fill in the values before sourcing.

```bash
cp scripts/env-variables.example .env
# Edit .env with your local credentials
source .env
```

See the main [README.md](../README.md) for the key variables required for local development.

---

### `build-local.sh` — Local Build Script (Development)

Consolidates the Maven build process for all services. **This is the primary script for local development.**

**Usage:**
```bash
./scripts/build-local.sh --service <service> [options]
```

**Parameters:**
- `-s, --service` (required): Service name - `lern`, `userorg`, `lms`, or `notification`
- `-c, --csp`: Cloud Storage Provider (default: `azure`)
  - Valid values: `azure`, `aws`, `gcp`, `oci`
  - Can also be set via `CSP` environment variable
- `-t, --tests`: Enable tests (default: skip tests)
- `-h, --help`: Show help message

**Examples:**
```bash
# Build lern service with default azure CSP
./scripts/build-local.sh --service lern

# Build userorg with AWS CSP
./scripts/build-local.sh --service userorg --csp aws

# Build lms with tests enabled
./scripts/build-local.sh --service lms --tests

# Build notification using environment variable
CSP=gcp ./scripts/build-local.sh --service notification
```

**What it does:**
1. Validates service name and parameters
2. Looks up service-specific Maven profile and module paths
3. Runs: `mvn clean install -P <profile>,<csp> [-DskipTests]`
4. Runs: `mvn play2:dist` in the service's Play module directory
5. Outputs the distribution zip location

**Service Configuration:**

| Service | Maven Profile | Play Module | Artifact |
|---------|---------------|-------------|----------|
| lern | lern | modules/lern/service | lern-service-impl-1.0-SNAPSHOT-dist.zip |
| userorg | userorg | modules/userorg/controller | userorg-service-1.0-SNAPSHOT-dist.zip |
| lms | lms | modules/lms/service | lms-service-1.0-SNAPSHOT-dist.zip |
| notification | notification | modules/notification/service | notification-service-1.0-SNAPSHOT-dist.zip |

---

### `docker-build.sh` - Unified Docker Build Script

Consolidates the Docker build process for all services with optional push support.

**Usage:**
```bash
./scripts/docker-build.sh --service <service> [options]
```

**Parameters:**
- `-s, --service` (required): Service name - `lern`, `userorg`, `lms`, or `notification`
- `-r, --repo`: Docker registry/repository (e.g., `ghcr.io/myorg`)
  - Optional; if omitted, image will not be pushed
- `-n, --name`: Image name (default: `<service>-service`)
- `-t, --tag`: Image tag (default: `latest`)
- `-c, --csp`: Cloud Storage Provider (default: `azure`)
  - Valid values: `azure`, `aws`, `gcp`, `oci`
  - Can also be set via `CSP` environment variable
- `-p, --push`: Push image to registry after successful build
- `-h, --help`: Show help message

**Examples:**
```bash
# Build lern service locally (no push)
./scripts/docker-build.sh --service lern

# Build and push to registry with custom tag
./scripts/docker-build.sh --service userorg --repo ghcr.io/myorg --tag v1.0.0 --push

# Build lms with custom image name
./scripts/docker-build.sh --service lms --repo ghcr.io/myorg --name my-lms-service --tag latest

# Build with different CSP
CSP=aws ./scripts/docker-build.sh --service notification --repo ghcr.io/myorg --push
```

**What it does:**
1. Validates service name and parameters
2. Looks up service-specific Dockerfile and distribution artifact paths
3. Verifies the distribution zip exists (from prior `build.sh` run)
4. Runs: `docker build -f <dockerfile> -t <image> --build-arg CSP=<csp> .`
5. If `--push` is specified and `--repo` is provided:
   - Runs: `docker push <image>`
6. Outputs the final image name and push status

**Service Configuration:**

| Service | Default Name | Dockerfile | Dist Zip |
|---------|--------------|------------|----------|
| lern | lern-service | build/lern/Dockerfile | modules/lern/service/target/lern-service-impl-1.0-SNAPSHOT-dist.zip |
| userorg | userorg-service | build/userorg/Dockerfile | modules/userorg/controller/target/userorg-service-1.0-SNAPSHOT-dist.zip |
| lms | lms-service | build/lms/Dockerfile | modules/lms/service/target/lms-service-1.0-SNAPSHOT-dist.zip |
| notification | notification-service | build/notification/Dockerfile | modules/notification/service/target/notification-service-1.0-SNAPSHOT-dist.zip |

---

## Cloud Storage Provider (CSP) Support

The scripts support multi-cloud deployment via Maven profiles:

- **azure** (default): Azure Storage
- **aws**: Amazon S3
- **gcp**: Google Cloud Storage
- **oci**: Oracle Cloud Storage

The CSP is passed to:
1. **Maven**: `mvn clean install -P <service>,<csp>`
   - This includes the appropriate cloud SDK dependency for the selected provider
2. **Docker**: `docker build --build-arg CSP=<csp>`
   - The Dockerfile uses this to set `ENV sunbird_cloud_service_provider=${CSP}`
