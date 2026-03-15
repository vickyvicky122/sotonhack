#!/usr/bin/env bash
# Deploy squish.space to Unraid
# Usage: ./deploy.sh
set -euo pipefail

UNRAID_HOST="root@100.85.82.61"
REMOTE_DIR="/mnt/user/appdata/squish-space/dist"
STACK_DIR="/mnt/user/appdata/repos/homelab/stacks/squish-space"

echo "=== Building production bundle ==="
./gradlew jsBrowserProductionWebpack "-Dorg.gradle.java.home=${JAVA_HOME:-/usr/lib/jvm/java-17-temurin-jdk}"

echo "=== Deploying to Unraid ==="
ssh "$UNRAID_HOST" "mkdir -p $REMOTE_DIR"
rsync -avz --delete build/dist/js/productionExecutable/ "$UNRAID_HOST:$REMOTE_DIR/"

echo "=== Restarting container ==="
ssh "$UNRAID_HOST" "docker restart squish-space 2>/dev/null || (cd $STACK_DIR && docker compose up -d)"

echo "=== Done! Live at https://squish.keanuc.net ==="
