#!/bin/bash

# Default values
REPO=""
IMAGE_NAME="lern-service"
IMAGE_TAG="latest"
PUSH=false
CSP=${CSP:-azure}  # Default to azure if not provided via environment or parameter

# Help message
function show_help {
    echo "Usage: $0 [options]"
    echo ""
    echo "Options:"
    echo "  -r, --repo    Docker repository/registry (optional)"
    echo "  -n, --name    Image name (default: lern-service)"
    echo "  -t, --tag     Image tag (default: latest)"
    echo "  -c, --csp     Cloud Storage Provider (default: azure)"
    echo "  -p, --push    Push image to repository"
    echo "  -h, --help    Show this help message"
}

# Parse command line arguments
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -r|--repo) REPO="$2"; shift ;;
        -n|--name) IMAGE_NAME="$2"; shift ;;
        -t|--tag) IMAGE_TAG="$2"; shift ;;
        -c|--csp) CSP="$2"; shift ;;
        -p|--push) PUSH=true ;;
        -h|--help) show_help; exit 0 ;;
        *) echo "Unknown parameter passed: $1"; show_help; exit 1 ;;
    esac
    shift
done

if [ -n "$REPO" ]; then
    FULL_IMAGE_NAME="${REPO}/${IMAGE_NAME}:${IMAGE_TAG}"
else
    FULL_IMAGE_NAME="${IMAGE_NAME}:${IMAGE_TAG}"
fi

echo "========================================="
echo "Docker Operation for: $FULL_IMAGE_NAME"
echo "Cloud Storage Provider (CSP): ${CSP}"
echo "Push after build: $PUSH"
echo "========================================="

# Get the script directory and navigate to project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../" && pwd)"
cd "$PROJECT_ROOT"

# Check if the distribution zip exists
DIST_ZIP="modules/lern/service/target/lern-service-impl-1.0-SNAPSHOT-dist.zip"
if [ ! -f "$DIST_ZIP" ]; then
    echo "Error: Distribution artifact not found at $DIST_ZIP"
    echo "Please run ./scripts/lern/build.sh first."
    exit 1
fi

echo "Step 1: Building Docker image..."
docker build -f build/lern/Dockerfile -t "$FULL_IMAGE_NAME" --build-arg CSP="${CSP}" .

if [ $? -ne 0 ]; then
    echo "Docker build failed!"
    exit 1
fi

echo ""
echo "Docker build completed successfully!"

if [ "$PUSH" = true ]; then
    if [ -z "$REPO" ]; then
        echo "Warning: No repository specified for push. Only local build performed."
    else
        echo "Step 2: Pushing image to $REPO..."
        docker push "$FULL_IMAGE_NAME"
        if [ $? -ne 0 ]; then
            echo "Docker push failed!"
            exit 1
        fi
        echo "Docker push completed successfully!"
    fi
fi

echo ""
echo "========================================="
echo "Operation completed successfully!"
echo "Image: $FULL_IMAGE_NAME"
echo "========================================="
echo ""
