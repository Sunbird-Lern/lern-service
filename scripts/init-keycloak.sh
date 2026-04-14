#!/usr/bin/env bash
# Extracts the Keycloak RSA public key and saves it to keys/{kid} so that
# KeyManager can load it at service startup.
#
# In production, Kubernetes mounts the Keycloak public key as a ConfigMap
# volume at /keys/{kid}. This script replicates that locally.
#
# Usage: ./scripts/init-keycloak.sh
#
# Prerequisites:
#   - Keycloak is running (docker-compose up -d)
#   - jq is installed (brew install jq)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
KEYS_DIR="$PROJECT_ROOT/keys"
KC_BASE="http://localhost:8080/auth"
REALM="sunbird"

if ! command -v jq &>/dev/null; then
    echo "ERROR: jq is required. Install it with: brew install jq"
    exit 1
fi

echo "Waiting for Keycloak at $KC_BASE/realms/$REALM..."
until curl -sf "$KC_BASE/realms/$REALM" >/dev/null 2>&1; do
    sleep 3
done
echo "Keycloak is ready."

# Fetch base64-encoded public key from the realm endpoint
PUBLIC_KEY_B64=$(curl -sf "$KC_BASE/realms/$REALM" | jq -r '.public_key')

# Fetch the key ID (kid) from the JWKS endpoint — match the active RSA signing key
KID=$(curl -sf "$KC_BASE/realms/$REALM/protocol/openid-connect/certs" \
    | jq -r '[.keys[] | select(.use == "sig" and .kty == "RSA")] | .[0].kid')

if [ -z "$PUBLIC_KEY_B64" ] || [ "$PUBLIC_KEY_B64" = "null" ] || \
   [ -z "$KID" ]             || [ "$KID" = "null" ]; then
    echo "ERROR: Failed to fetch public key or kid from Keycloak."
    echo "Check that the sunbird realm is imported and Keycloak is healthy."
    exit 1
fi

# Wrap in PEM headers (KeyManager strips headers, so format is flexible,
# but PEM is the standard expected by loadPublicKey)
PEM="-----BEGIN PUBLIC KEY-----
$(echo "$PUBLIC_KEY_B64" | fold -w 64)
-----END PUBLIC KEY-----"

mkdir -p "$KEYS_DIR"
printf '%s\n' "$PEM" > "$KEYS_DIR/$KID"

echo "OK: Public key saved to keys/$KID"
echo ""
echo "Continue with the README — Step 5: Configure Environment, then Step 6: Build."
