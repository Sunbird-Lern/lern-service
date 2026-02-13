#!/bin/bash

# Default values
REPO=""
IMAGE_NAME="notification-service"
IMAGE_TAG="latest"

# Parse command line arguments
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -r|--repo) REPO="$2"; shift ;;
        -n|--name) IMAGE_NAME="$2"; shift ;;
        -t|--tag) IMAGE_TAG="$2"; shift ;;
        *) echo "Unknown parameter passed: $1"; exit 1 ;;
    esac
    shift
done

if [ -n "$REPO" ]; then
    FULL_IMAGE_NAME="${REPO}/${IMAGE_NAME}:${IMAGE_TAG}"
else
    FULL_IMAGE_NAME="${IMAGE_NAME}:${IMAGE_TAG}"
fi

echo "========================================="
echo "Building Docker Image: $FULL_IMAGE_NAME"
echo "========================================="

# Get the script directory and navigate to project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# The script is in scripts/notification/, so project root is two levels up
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../" && pwd)"
cd "$PROJECT_ROOT"

# Check if the distribution zip exists
DIST_ZIP="modules/notification/service/target/notification-service-1.0-SNAPSHOT-dist.zip"
if [ ! -f "$DIST_ZIP" ]; then
    echo "Error: Distribution artifact not found at $DIST_ZIP"
    echo "Please run ./scripts/notification/build.sh first."
    exit 1
fi

echo "Building Docker image..."
docker build -f build/notification/Dockerfile -t "$FULL_IMAGE_NAME" .

echo ""
echo "========================================="
echo "Docker build completed successfully!"
echo "Image: $FULL_IMAGE_NAME"
echo "========================================="
echo ""
echo "To push the image:"
echo "  docker push $FULL_IMAGE_NAME"
echo ""

