#!/usr/bin/env bash
# Sets up the local Keycloak Docker image for Sunbird Lern development.
#
# What it does:
#   1. Sparse-clones Dockerfile, conf/, providers/, and imports/ from
#      scripts/keycloak-21.1.2 in sunbird-spark-installer (themes/ is skipped
#      to avoid fetching large assets; an empty placeholder is created so the
#      Dockerfile COPY instruction succeeds)
#   2. Generates sunbird-realm.local.json from the realm template via sed
#   3. Builds and tags the Docker image as sunbird-keycloak:local
#
# Usage: ./scripts/setup-keycloak.sh [BRANCH]
#   BRANCH: branch of sunbird-spark-installer to use (default: develop)
#
# Prerequisites: Docker Desktop must be running.

set -e

BRANCH="${1:-develop}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_URL="https://github.com/Sunbird-Spark/sunbird-spark-installer.git"
SPARSE_PATH="scripts/keycloak-21.1.2"
CLONE_DIR="$SCRIPT_DIR/.keycloak-setup"
BUILD_DIR="$SCRIPT_DIR/.keycloak-build"

# ── Sparse-checkout (themes/ excluded — too large; placeholder created below) ─
echo "Fetching Keycloak setup from sunbird-spark-installer (branch: ${BRANCH})..."
rm -rf "$CLONE_DIR"
git clone --depth 1 --branch "$BRANCH" --filter=blob:none --sparse "$REPO_URL" "$CLONE_DIR" 2>/dev/null
cd "$CLONE_DIR"
git sparse-checkout set "$SPARSE_PATH" 2>/dev/null
cd "$SCRIPT_DIR"

INSTALLER_KC="$CLONE_DIR/$SPARSE_PATH"

# ── Assemble Docker build context ────────────────────────────────────────────
echo "Assembling build context..."
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/providers" "$BUILD_DIR/imports" "$BUILD_DIR/themes"

cp "$INSTALLER_KC/Dockerfile" "$BUILD_DIR/Dockerfile"
cp -r "$INSTALLER_KC/conf" "$BUILD_DIR/conf"

# Apply two patches to the installer Dockerfile:
#   1. Copy ./imports/ to /opt/keycloak/data/import/ (where --import-realm reads)
#   2. Add 'scripts' to KC_FEATURES so JavaScript policies in the realm can import
#      (this must be a build-time feature for --optimized images)
sed \
  -e 's|/opt/keycloak/imports/|/opt/keycloak/data/import/|g' \
  -e 's|ENV KC_FEATURES=token-exchange|ENV KC_FEATURES=token-exchange,scripts|g' \
  "$BUILD_DIR/Dockerfile" > "$BUILD_DIR/Dockerfile.patched"
mv "$BUILD_DIR/Dockerfile.patched" "$BUILD_DIR/Dockerfile"

# ── Generate realm JSON from Helm template ───────────────────────────────────
echo "Generating sunbird-realm.local.json..."
sed \
  -e 's/{{ .Values.global.domain }}/localhost:8080/g' \
  -e 's/{{ .Values.global.random_string }}/localdevkey1234/g' \
  -e 's/{{ .Values.android_client_secret }}/android/g' \
  -e 's/{{ .Values.desktop_client_secret }}/desktop_client_secret/g' \
  -e 's/{{ .Values.direct_grant_client_secret }}/direct-grant/g' \
  -e 's/{{ .Values.google_auth_client_secret }}/google-auth/g' \
  -e 's/{{ .Values.google_android_client_secret }}/google-auth-android/g' \
  -e 's/{{ .Values.google_auth_desktop_client_secret }}/google-auth-desktop/g' \
  -e 's/{{ .Values.lms_client_secret }}/lms/g' \
  -e 's/{{ .Values.nodebb_client_secret }}/nodebb/g' \
  -e 's/{{ .Values.trampoline_client_secret }}/trampoline/g' \
  -e 's/{{ .Values.trampoline_android_client_secret }}/trampoline-android/g' \
  -e 's/{{ .Values.trampoline_desktop_client_secret }}/trampoline-desktop/g' \
  -e 's/{{ .Values.global.mail_server_password }}/password/g' \
  -e 's/{{ .Values.global.mail_server_host }}/smtp.example.com/g' \
  -e 's/{{ .Values.global.mail_server_from_email }}/admin@example.com/g' \
  -e 's/{{ .Values.global.mail_server_username }}/admin/g' \
  -e 's/{{ .Values.tenant_name }}/sunbird/g' \
  -e 's/{{ .Values.cassandra_federation_provider_id }}/cassandrafederationid/g' \
  "$INSTALLER_KC/imports/sunbird-realm.json" > "$BUILD_DIR/imports/sunbird-realm.local.json"
echo "OK: sunbird-realm.local.json generated"

# Strip JavaScript authorization policies (type=js) and any permissions that
# reference them from the generated realm JSON. These are trivial Keycloak
# defaults that trigger "Script upload is disabled" in Keycloak 21 optimized
# mode; they carry no Sunbird-specific configuration.
python3 - "$BUILD_DIR/imports/sunbird-realm.local.json" <<'PYEOF'
import json, sys
path = sys.argv[1]
with open(path) as f:
    realm = json.load(f)
for client in realm.get('clients', []):
    auth = client.get('authorizationSettings')
    if not auth:
        continue
    # Collect names of JS policies being removed so we can drop their dependents
    js_names = {p['name'] for p in auth.get('policies', []) if p.get('type') == 'js'}
    # Strip js-type policies from the policies array
    auth['policies'] = [p for p in auth.get('policies', []) if p.get('type') != 'js']
    # Drop any policies that exclusively reference removed js policies
    auth['policies'] = [
        p for p in auth['policies']
        if not all(ref in js_names
                   for ref in json.loads(p.get('config', {}).get('applyPolicies', '[]') or '[]'))
           or not json.loads(p.get('config', {}).get('applyPolicies', '[]') or '[]')
    ]
    # 'permissions' is not a valid field in KC 21 ResourceServerRepresentation;
    # remove it if present to avoid Jackson UnrecognizedPropertyException.
    auth.pop('permissions', None)
with open(path, 'w') as f:
    json.dump(realm, f, indent=2)
print("OK: removed js-type policies and cleaned authorizationSettings for KC 21")
PYEOF

# ── Copy provider JAR ────────────────────────────────────────────────────────
JAR_NAME="keycloak-email-phone-autthenticator-1.0-SNAPSHOT.jar"
echo "Copying provider JAR..."
cp "$INSTALLER_KC/providers/$JAR_NAME" "$BUILD_DIR/providers/$JAR_NAME"
echo "OK: $JAR_NAME"

# ── Cleanup sparse checkout ──────────────────────────────────────────────────
rm -rf "$CLONE_DIR"

# ── Build Docker image ───────────────────────────────────────────────────────
echo ""
echo "Building sunbird-keycloak:local (this takes a few minutes on first run)..."
docker build -t sunbird-keycloak:local "$BUILD_DIR"

# ── Cleanup build context ────────────────────────────────────────────────────
rm -rf "$BUILD_DIR"

echo ""
echo "Done. Start the full stack with:"
echo "  docker-compose up -d"
echo ""
echo "Then initialize Keycloak public keys:"
echo "  ./scripts/init-keycloak.sh"
