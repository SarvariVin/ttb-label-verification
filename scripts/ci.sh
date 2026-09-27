#!/usr/bin/env bash
# Vendor-neutral CI entry point: any CI system (Jenkins, GitLab, Azure DevOps,
# Bamboo, TeamCity…) can run this script on an agent with Java 21, Tesseract and Docker.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "==> Build, unit + integration tests"
./mvnw -B -ntp clean verify

echo "==> Container image"
if command -v docker >/dev/null 2>&1; then
  docker build -t "label-verification:${BUILD_TAG:-local}" .
else
  echo "docker not found — skipping image build"
fi
