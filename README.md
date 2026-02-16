# Lern Service

The **Lern Service** is a unified, scalable platform component that integrates core learning functionalities, user organization management, and notification services into a single deployable unit. This repository supports building both a monolithic "merged" service and individual microservices.

## Project Overview

This project consolidates the following services:
- **Lern Service (Merged)**: A single service combining all functionalities (LMS, UserOrg, Notification).
- **LMS Service**: Learning Management System capabilities.
- **UserOrg Service**: User and Organization management.
- **Notification Service**: System notifications and alerts.

## Prerequisites

Ensure you have the following installed:
- **Java 11**: Required for building the project.
- **Maven 3.6+**: Build tool.
- **Docker**: For building and running containerized images.

---

## Building the Merged Service (Recommended)

The merged service is the primary deployment artifact, combining all modules for streamlined operations.

### 1. Build the Artifact
Run the build script to compile all modules and create the distribution artifact:

```bash
./scripts/lern/build.sh
```

**Options:**
- `-t` or `--tests`: Run unit tests during the build (default: skipped).

**Output:**
- The distribution artifact will be created at: `modules/lern/service/target/lern-service-impl-1.0-SNAPSHOT-dist.zip`

### 2. Build and Push Docker Image
Create a Docker image from the built artifact:

```bash
./scripts/lern/docker-build-push.sh -r <your-docker-repo> -t <tag>
```

**Options:**
- `-r`, `--repo`: Docker repository (e.g., `sunbird`).
- `-n`, `--name`: Image name (default: `lern-service`).
- `-t`, `--tag`: Image tag (default: `latest`).
- `-p`, `--push`: Push the image to the registry after building.

**Example:**
```bash
./scripts/lern/docker-build-push.sh -r sunbird -t v1.0.0
```

---

## Building Individual Services

If you need to deploy specific components independently, use the following scripts.

### UserOrg Service
```bash
./scripts/userorg/build.sh
# Check modules/userorg/controller/target/ for the distribution
```

### LMS Service
```bash
./scripts/lms/build.sh
# Check modules/lms/service/target/ for the distribution
```

### Notification Service
```bash
./scripts/notification/build.sh
# Check modules/notification/service/target/ for the distribution
```

---

## Project Structure

```
├── core/                   # Shared utilities (Platform, Cassandra, ES, etc.)
├── modules/
│   ├── lern/               # Merged service implementation
│   ├── lms/                # LMS specific modules
│   ├── userorg/            # User & Organization modules
│   └── notification/       # Notification modules
├── scripts/                # Build and deployment scripts
│   ├── lern/
│   ├── lms/
│   ├── userorg/
│   └── notification/
├── build/                  # Dockerfiles for each service
└── pom.xml                 # Root Maven configuration
```