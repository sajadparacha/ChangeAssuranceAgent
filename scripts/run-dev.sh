#!/usr/bin/env bash
# Start Change Assurance Agent backend (8080) and Angular frontend (4200).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BACKEND_PID=""
FRONTEND_PID=""
LOG_DIR="${ROOT}/.run-logs"
mkdir -p "$LOG_DIR"
BACKEND_LOG="${LOG_DIR}/backend.log"
FRONTEND_LOG="${LOG_DIR}/frontend.log"

cleanup() {
  echo ""
  echo "Stopping services..."
  if [[ -n "${FRONTEND_PID}" ]] && kill -0 "${FRONTEND_PID}" 2>/dev/null; then
    kill "${FRONTEND_PID}" 2>/dev/null || true
    wait "${FRONTEND_PID}" 2>/dev/null || true
  fi
  if [[ -n "${BACKEND_PID}" ]] && kill -0 "${BACKEND_PID}" 2>/dev/null; then
    # Kill Maven and its Spring Boot child process tree
    pkill -P "${BACKEND_PID}" 2>/dev/null || true
    kill "${BACKEND_PID}" 2>/dev/null || true
    wait "${BACKEND_PID}" 2>/dev/null || true
  fi
  echo "Stopped."
}
trap cleanup EXIT INT TERM

if [[ -z "${JAVA_HOME:-}" ]]; then
  if /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
  elif command -v /usr/libexec/java_home >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home)"
  fi
fi

command -v mvn >/dev/null 2>&1 || { echo "mvn not found on PATH"; exit 1; }
command -v npm >/dev/null 2>&1 || { echo "npm not found on PATH"; exit 1; }

# Load local env overrides (Spring Boot does not read .env by itself).
if [[ -f "${ROOT}/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${ROOT}/.env"
  set +a
  echo "Loaded environment from .env"
fi

# Default / enforce live Oracle catalog for local runs (no silent fake catalog).
export CHANGEASSURANCE_DB_METADATA_MODE="${CHANGEASSURANCE_DB_METADATA_MODE:-oracle}"
if [[ "${CHANGEASSURANCE_DB_METADATA_MODE}" == "fake" && "${ALLOW_FAKE_CATALOG:-}" != "1" ]]; then
  echo "Refusing to start with fake catalog." >&2
  echo "Set CHANGEASSURANCE_DB_METADATA_MODE=oracle (recommended), or ALLOW_FAKE_CATALOG=1 to override." >&2
  exit 1
fi
export CHANGEASSURANCE_DB_JDBC_URL="${CHANGEASSURANCE_DB_JDBC_URL:-jdbc:oracle:thin:@//localhost:1521/FREEPDB1}"
export CHANGEASSURANCE_DB_USERNAME="${CHANGEASSURANCE_DB_USERNAME:-APP}"
export CHANGEASSURANCE_DB_PASSWORD="${CHANGEASSURANCE_DB_PASSWORD:-AppDemoPass1}"
export CHANGEASSURANCE_DB_DEFAULT_OWNER="${CHANGEASSURANCE_DB_DEFAULT_OWNER:-APP}"

echo "Repo:     ${ROOT}"
echo "JAVA_HOME: ${JAVA_HOME:-"(unset)"}"
echo "DB metadata mode: ${CHANGEASSURANCE_DB_METADATA_MODE}"
echo "DB JDBC URL:      ${CHANGEASSURANCE_DB_JDBC_URL}"
echo "Backend log:  ${BACKEND_LOG}"
echo "Frontend log: ${FRONTEND_LOG}"
echo ""

echo "Starting backend (spring-boot:run)..."
(
  cd "$ROOT"
  mvn -q spring-boot:run -DskipTests
) >"${BACKEND_LOG}" 2>&1 &
BACKEND_PID=$!

echo "Waiting for backend health at http://localhost:8080/actuator/health ..."
for i in $(seq 1 90); do
  if curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; then
    echo "Backend is UP."
    break
  fi
  if ! kill -0 "${BACKEND_PID}" 2>/dev/null; then
    echo "Backend process exited early. Last log lines:"
    tail -n 40 "${BACKEND_LOG}" || true
    exit 1
  fi
  if [[ "$i" -eq 90 ]]; then
    echo "Timed out waiting for backend. Last log lines:"
    tail -n 40 "${BACKEND_LOG}" || true
    exit 1
  fi
  sleep 2
done

if [[ ! -d "${ROOT}/frontend/node_modules" ]]; then
  echo "Installing frontend dependencies (npm install)..."
  (cd "${ROOT}/frontend" && npm install)
fi

echo "Starting frontend (ng serve on :4200)..."
(
  cd "${ROOT}/frontend"
  npm start
) >"${FRONTEND_LOG}" 2>&1 &
FRONTEND_PID=$!

echo ""
echo "========================================"
echo " Frontend: http://localhost:4200/"
echo " Backend:  http://localhost:8080/"
echo " Health:   http://localhost:8080/actuator/health"
echo " Swagger:  http://localhost:8080/swagger-ui.html"
echo "========================================"
echo "Press Ctrl+C to stop both."
echo ""

# Stream both logs while processes run
tail -n 0 -F "${BACKEND_LOG}" "${FRONTEND_LOG}" &
TAIL_PID=$!

wait "${FRONTEND_PID}" "${BACKEND_PID}" || true
kill "${TAIL_PID}" 2>/dev/null || true
