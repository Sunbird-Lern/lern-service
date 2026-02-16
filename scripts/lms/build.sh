#!/bin/bash

# Default value for skipping tests
SKIP_TESTS=true

# Parse command line arguments
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -t|--tests) SKIP_TESTS=false ;;
        *) echo "Unknown parameter passed: $1"; exit 1 ;;
    esac
    shift
done

echo "========================================="
echo "Building LMS Service"
if [ "$SKIP_TESTS" = true ]; then
    echo "Tests: SKIPPED"
else
    echo "Tests: ENABLED"
fi
echo "========================================="

# Get the script directory and navigate to project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../" && pwd)"
cd "$PROJECT_ROOT"

echo ""
echo "Step 1: Building core dependencies and LMS modules..."

if [ "$SKIP_TESTS" = true ]; then
    mvn clean install -P lms -DskipTests
else
    mvn clean install -P lms
fi

echo ""
echo "Step 2: Creating Play distribution..."
(cd modules/lms/service && mvn play2:dist)

echo ""
echo "========================================="
echo "Build completed successfully!"
echo "========================================="
echo ""
echo "Distribution artifact created at:"
echo "  modules/lms/service/target/lms-service-1.0-SNAPSHOT-dist.zip"
echo ""
echo "To build Docker image:"
echo "  docker build -f build/lms/Dockerfile -t lms-service:latest ."
echo ""
