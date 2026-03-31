# Build and Docker Scripts Documentation

## Overview

This directory contains unified build and Docker scripts for all services (lern, userorg, lms, notification). The scripts consolidate service-specific logic into single, parameterized scripts that accept a `--service` parameter.

**Note:** `build-local.sh` is specifically designed for **local development**. It handles Maven builds and Play distribution creation. For **CI/CD pipelines**, refer to `.github/workflows/deploy.yml` which uses the same script but orchestrates the entire build and push process.

## Scripts

### `build-local.sh` - Local Build Script (Development)

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

## Local Development vs CI/CD

### For Local Development
Use **`build-local.sh`** directly:
```bash
./scripts/build-local.sh --service lern --csp azure
./scripts/docker-build.sh --service lern
```

### For CI/CD / GitHub Actions
The `deploy.yml` workflow automatically calls `build-local.sh` as part of the automated build pipeline:
- ✅ Builds the distribution (`build-local.sh`)
- ✅ Builds Docker images (`docker/build-push-action`)
- ✅ Pushes to container registry (automatic with workflow)

**You do not need to manually run these scripts in CI/CD.** The workflow handles everything.

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

---

## GitHub Actions Integration

The `deploy.yml` workflow has been updated to use the unified scripts:

```yaml
- name: Build Lern Service
  run: ./scripts/build.sh --service lern

- name: Build UserOrg Service
  run: ./scripts/build.sh --service userorg

- name: Build LMS Service
  run: ./scripts/build.sh --service lms

- name: Build Notification Service
  run: ./scripts/build.sh --service notification
```

The CSP is automatically passed via the `CSP` environment variable, which defaults to `azure` if not set in repository variables.

---

## Troubleshooting

### "Distribution artifact not found" error
**Problem:** `docker-build.sh` says dist zip doesn't exist
**Solution:** Run `./scripts/build.sh --service <service>` first to create the artifact

### "Unknown service" error
**Problem:** Script doesn't recognize the service name
**Solution:** Use one of the valid service names:
- `lern`
- `userorg`
- `lms`
- `notification`

### Docker push fails
**Problem:** `docker push` fails
**Causes & Solutions:**
- No `--repo` specified: Image is built locally only; provide `--repo` and `--push` to push
- Not logged in: Run `docker login <registry>` before pushing
- Permission denied: Check your credentials and registry access

### Maven build fails with profile not found
**Problem:** `mvn clean install -P <service>,<csp>` fails
**Causes & Solutions:**
- The service profile doesn't exist in pom.xml: Verify the service name is correct
- The CSP profile doesn't exist: Verify `azure`, `aws`, `gcp`, or `oci` is used
- Ensure the parent pom.xml includes these profiles

---

## Migration from Old Scripts

If you have existing automation using the old per-service scripts:

**Old:**
```bash
./scripts/lern/build.sh
./scripts/userorg/build.sh
```

**New:**
```bash
./scripts/build-local.sh --service lern
./scripts/build-local.sh --service userorg
```

The environment variable approach remains the same:
```bash
CSP=aws ./scripts/build-local.sh --service lms
```

---

## Script Source

- **build-local.sh** - Lines 31-35 contain the service configuration mapping
- **docker-build.sh** - Lines 44-50 contain the service configuration mapping

To add a new service, add an entry to the corresponding `SERVICE_CONFIG` associative array in each script:

```bash
# In build-local.sh
declare -A SERVICE_CONFIG=(
  [your-service]="maven-profile|module/path|artifact-name.zip"
)

# In docker-build.sh
declare -A SERVICE_CONFIG=(
  [your-service]="default-image-name|build/path/Dockerfile|modules/path/target/artifact.zip"
)
```
