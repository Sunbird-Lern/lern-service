#!/usr/bin/env bash
# Downloads OS index and mapping definitions from sunbird-devops and applies them
# against the local OpenSearch container.
#
# Usage: ./scripts/init-opensearch.sh [BRANCH]
#   BRANCH: branch of sunbird-devops to use (default: release-8.0.0)
#
# Prerequisites: Docker must be running with the sunbird_os container healthy.
# Run this once after starting the containers for the first time.

set -e

BRANCH="${1:-release-8.0.0}"
OS_HOST="http://localhost:9200"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOWNLOADS_DIR="${SCRIPT_DIR}/.os-migrations"
REPO_URL="https://github.com/project-sunbird/sunbird-devops.git"
REPO_PATH="ansible/roles/es7-mapping/files"

echo "Downloading OS index/mapping definitions (branch: ${BRANCH})..."
rm -rf "${DOWNLOADS_DIR}"
git clone --depth 1 --branch "${BRANCH}" --filter=blob:none --sparse "${REPO_URL}" "${DOWNLOADS_DIR}" 2>/dev/null
cd "${DOWNLOADS_DIR}"
git sparse-checkout set "${REPO_PATH}" 2>/dev/null
cd "${SCRIPT_DIR}"

echo "Waiting for OpenSearch at ${OS_HOST}..."
until curl -s "${OS_HOST}/_cluster/health" > /dev/null 2>&1; do
    sleep 2
done
echo "OpenSearch is ready."

FAILED=0
for index_file in "${DOWNLOADS_DIR}/${REPO_PATH}/indices/"*.json; do
    [ -e "$index_file" ] || continue
    index=$(basename "${index_file}" .json)
    mapping_file="${DOWNLOADS_DIR}/${REPO_PATH}/mappings/${index}-mapping.json"

    if [ -f "${index_file}" ]; then
        STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "${OS_HOST}/${index}" \
            -H "Content-Type: application/json" \
            -d @"${index_file}")
        if [ "${STATUS}" = "200" ] || [ "${STATUS}" = "201" ]; then
            echo "OK: index ${index} created"
        elif [ "${STATUS}" = "400" ]; then
            echo "SKIP: index ${index} already exists"
        else
            echo "FAIL: index ${index} (HTTP ${STATUS})"
            FAILED=$((FAILED + 1))
            continue
        fi
    else
        echo "SKIP: ${index}.json not found"
        continue
    fi

    if [ -f "${mapping_file}" ]; then
        STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "${OS_HOST}/${index}/_mapping" \
            -H "Content-Type: application/json" \
            -d @"${mapping_file}")
        if [ "${STATUS}" = "200" ] || [ "${STATUS}" = "201" ]; then
            echo "OK: mapping for ${index} applied"
        else
            echo "FAIL: mapping for ${index} (HTTP ${STATUS})"
            FAILED=$((FAILED + 1))
        fi
    else
        echo "SKIP: ${index}-mapping.json not found"
    fi
done

rm -rf "${DOWNLOADS_DIR}"

echo ""
if [ ${FAILED} -gt 0 ]; then
    echo "${FAILED} operation(s) failed."
    exit 1
else
    echo "All OpenSearch indices and mappings created successfully."
fi
