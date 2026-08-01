#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
BASE="${API_BASE:-http://localhost:8080/api/v1}"

echo "Submitting management demo change package to ${BASE}/change-reviews ..."
RESP=$(curl -s -X POST "${BASE}/change-reviews" \
  -F "applicationName=billing-service" \
  -F "changeTitle=Package body performance tune" \
  -F "changeType=PLSQL" \
  -F "targetEnvironment=PROD" \
  -F "implementationWindow=2026-08-02 02:00-04:00 UTC" \
  -F "changeDescription=$(cat "${DIR}/01-change-description.txt")" \
  -F "deploymentPlan=$(cat "${DIR}/02-deployment-plan.txt")" \
  -F "rollbackPlan=$(cat "${DIR}/03-rollback-plan.txt")" \
  -F "testEvidence=$(cat "${DIR}/04-test-evidence.txt")" \
  -F "sqlFile=@${DIR}/05-deploy.sql;type=text/plain")

echo "$RESP" | python3 -m json.tool 2>/dev/null || echo "$RESP"
REVIEW_ID=$(echo "$RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('reviewId',''))" 2>/dev/null || true)

if [[ -z "${REVIEW_ID}" ]]; then
  echo "Submit failed — is the backend running on port 8080?"
  exit 1
fi

echo ""
echo "Review ID: ${REVIEW_ID}"
echo "UI progress: http://localhost:4200/reviews/${REVIEW_ID}"
echo "API review:  ${BASE}/change-reviews/${REVIEW_ID}"
echo "API gaps:    ${BASE}/change-reviews/${REVIEW_ID}/information-gaps"
echo "API report:  ${BASE}/change-reviews/${REVIEW_ID}/report"
echo ""
echo "Fetching review status..."
curl -s "${BASE}/change-reviews/${REVIEW_ID}" | python3 -m json.tool 2>/dev/null | head -40
