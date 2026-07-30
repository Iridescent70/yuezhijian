#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${project_root}/.env.local"
api_base="${API_BASE_URL:-http://localhost:8080}"

if [[ ! -f "${env_file}" ]]; then
  echo "Missing .env.local." >&2
  exit 1
fi
if ! command -v jq >/dev/null; then
  echo "jq is required for the persistence smoke test." >&2
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  echo "Docker is not available to the current shell. Re-login or run: sg docker -c 'make db-smoke'" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "${env_file}"
set +a

runtime_dir="${project_root}/.data/persistence-smoke"
mkdir -p "${runtime_dir}"
cookie_file="${runtime_dir}/cookie.txt"
trap 'rm -f "${cookie_file}"' EXIT
mobile_suffix="$(date +%s%N | tail -c 9)"
mobile="199${mobile_suffix}"

csrf_token="$(curl -fsS -c "${cookie_file}" "${api_base}/api/v1/auth/csrf" | jq -r '.data.token')"
login_payload="$(jq -n --arg username "${APP_BOOTSTRAP_USERNAME}" --arg password "${APP_BOOTSTRAP_PASSWORD}" \
  '{username: $username, password: $password}')"
login_response="$(curl -fsS -b "${cookie_file}" -c "${cookie_file}" \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: ${csrf_token}" \
  -d "${login_payload}" "${api_base}/api/v1/auth/login")"
csrf_token="$(curl -fsS -b "${cookie_file}" -c "${cookie_file}" \
  "${api_base}/api/v1/auth/csrf" | jq -r '.data.token')"
store_id="$(printf '%s' "${login_response}" | jq -r '[.data.stores[] | select(.status == "ACTIVE")][0].id // empty')"
if [[ ! "${store_id}" =~ ^[0-9]+$ ]]; then
  echo "Login did not return an active store." >&2
  exit 1
fi

member_payload="$(jq -n --arg mobile "${mobile}" --argjson storeId "${store_id}" \
  '{fullName: "SQL持久化验证", mobile: $mobile, gender: "UNKNOWN", sourceType: "MANUAL",
    joinStoreId: $storeId, ownerStoreId: $storeId}')"
member_response="$(curl -fsS -b "${cookie_file}" \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: ${csrf_token}" \
  -d "${member_payload}" "${api_base}/api/v1/members")"
member_id="$(printf '%s' "${member_response}" | jq -r '.data.memberId // empty')"
member_no="$(printf '%s' "${member_response}" | jq -r '.data.memberNo // empty')"

if [[ ! "${member_id}" =~ ^[0-9]+$ ]] || [[ -z "${member_no}" ]]; then
  echo "Member creation did not return a valid persistent identity: ${member_response}" >&2
  exit 1
fi

database_evidence="$(docker compose --env-file "${env_file}" -f "${project_root}/infra/compose.yaml" exec -T \
  -e SQLCMDPASSWORD="${DB_PASSWORD}" sqlserver \
  /opt/mssql-tools18/bin/sqlcmd -S localhost -U "${DB_USERNAME}" -d "${DB_NAME}" -C -b -h -1 -W \
  -Q "SET NOCOUNT ON; SELECT CONCAT(member.id, '|', (SELECT COUNT(1) FROM dbo.mem_membership_card card WHERE card.member_id = member.id), '|', (SELECT COUNT(1) FROM dbo.ast_balance_account balance WHERE balance.member_id = member.id), '|', (SELECT COUNT(1) FROM dbo.ast_point_account point_account WHERE point_account.member_id = member.id)) FROM dbo.mem_member member WHERE member.id = ${member_id};" \
  | tr -d '\r' | sed '/^[[:space:]]*$/d')"

if [[ "${database_evidence}" != "${member_id}|1|1|1" ]]; then
  echo "Database evidence mismatch for member ${member_id}: ${database_evidence}" >&2
  exit 1
fi

expected_migrations="$(find "${project_root}/backend/src/main/resources/db/migration" -maxdepth 1 -name 'V*.sql' | wc -l | tr -d ' ')"
applied_migrations="$(docker compose --env-file "${env_file}" -f "${project_root}/infra/compose.yaml" exec -T \
  -e SQLCMDPASSWORD="${DB_PASSWORD}" sqlserver \
  /opt/mssql-tools18/bin/sqlcmd -S localhost -U "${DB_USERNAME}" -d "${DB_NAME}" -C -b -h -1 -W \
  -Q "SET NOCOUNT ON; SELECT COUNT(1) FROM dbo.flyway_schema_history WHERE success = 1 AND version IS NOT NULL;" \
  | tr -d '\r[:space:]')"

if [[ "${applied_migrations}" != "${expected_migrations}" ]]; then
  echo "Flyway version count mismatch: expected ${expected_migrations}, applied ${applied_migrations}." >&2
  exit 1
fi

echo "SQL Server persistence verified: memberId=${member_id}, memberNo=${member_no}, member/card/balance/point=${database_evidence}, migrations=${applied_migrations}."
