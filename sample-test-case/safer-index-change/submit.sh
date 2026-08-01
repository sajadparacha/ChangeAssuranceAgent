#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
BASE="${API_BASE:-http://localhost:8080/api/v1}"

curl -s -X POST "${BASE}/change-reviews" \
  -F "applicationName=payments" \
  -F "changeTitle=Add payments id index" \
  -F "changeType=DATABASE_SCHEMA" \
  -F "targetEnvironment=UAT" \
  -F "implementationWindow=2026-08-03 01:00-02:00 UTC" \
  -F "changeDescription=$(cat "${DIR}/01-change-description.txt")" \
  -F "deploymentPlan=$(cat "${DIR}/02-deployment-plan.txt")" \
  -F "rollbackPlan=$(cat "${DIR}/03-rollback-plan.txt")" \
  -F "testEvidence=$(cat "${DIR}/04-test-evidence.txt")" \
  -F "sqlFile=@${DIR}/05-deploy.sql;type=text/plain" | python3 -m json.tool
