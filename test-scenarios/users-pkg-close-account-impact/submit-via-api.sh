#!/usr/bin/env bash
# Submit the USERS_PKG.CLOSE_USER_ACCOUNT impact scenario to a local Change Assurance Agent.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API_BASE="${API_BASE:-http://localhost:8080/api/v1}"
SQL_FILE="${1:-${DIR}/02-change-close-user-account.sql}"

if [[ ! -f "${SQL_FILE}" ]]; then
  echo "SQL file not found: ${SQL_FILE}" >&2
  exit 1
fi

echo "Submitting ${SQL_FILE} to ${API_BASE}/change-reviews ..."
echo "  packageName=USERS_PKG schemaOwner=APP"

RESPONSE="$(curl -sS -X POST "${API_BASE}/change-reviews" \
  -F "applicationName=APP" \
  -F "changeTitle=USERS_PKG close-account body tweak" \
  -F "changeDescription=$(cat "${DIR}/change-description.txt")" \
  -F "changeType=PLSQL" \
  -F "targetEnvironment=UAT" \
  -F "implementationWindow=after hours" \
  -F "packageName=USERS_PKG" \
  -F "schemaOwner=APP" \
  -F "deploymentPlan=$(cat "${DIR}/deployment-plan.txt")" \
  -F "rollbackPlan=$(cat "${DIR}/rollback-plan.txt")" \
  -F "testEvidence=$(cat "${DIR}/test-evidence.txt")" \
  -F "sqlFile=@${SQL_FILE};type=text/plain;filename=$(basename "${SQL_FILE}")")"

echo "${RESPONSE}"
REVIEW_ID="$(printf '%s' "${RESPONSE}" | sed -n 's/.*"reviewId"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"

if [[ -z "${REVIEW_ID}" ]]; then
  echo "Could not parse reviewId from response." >&2
  exit 1
fi

echo ""
echo "Review ID: ${REVIEW_ID}"
echo "UI:        http://localhost:4200/reviews/${REVIEW_ID}"
echo "JSON:      ${API_BASE}/change-reviews/${REVIEW_ID}/report"
echo "HTML:      ${API_BASE}/change-reviews/${REVIEW_ID}/report.html"
echo ""
echo "Waiting for review to finish..."
for _ in $(seq 1 60); do
  STATUS="$(curl -sS "${API_BASE}/change-reviews/${REVIEW_ID}" \
    | sed -n 's/.*"status"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' \
    | head -1)"
  if [[ "${STATUS}" == "COMPLETED" || "${STATUS}" == "FAILED" || "${STATUS}" == "WAITING_FOR_INFORMATION" ]]; then
    echo "Status: ${STATUS}"
    break
  fi
  sleep 2
done

echo ""
echo "Fetching JSON report (truncated)..."
curl -sS "${API_BASE}/change-reviews/${REVIEW_ID}/report" | head -c 5000
echo ""
