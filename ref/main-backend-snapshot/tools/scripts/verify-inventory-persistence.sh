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
  echo "jq is required for the inventory persistence smoke test." >&2
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  echo "Docker is not available to the current shell. Re-login or run: sg docker -c 'make inventory-smoke'" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "${env_file}"
set +a

runtime_dir="${project_root}/.data/persistence-smoke"
mkdir -p "${runtime_dir}"
cookie_file="${runtime_dir}/inventory-cookie.txt"
trap 'rm -f "${cookie_file}"' EXIT

csrf_token="$(curl -fsS -c "${cookie_file}" "${api_base}/api/v1/auth/csrf" | jq -r '.data.token')"
login_payload="$(jq -n --arg username "${APP_BOOTSTRAP_USERNAME}" --arg password "${APP_BOOTSTRAP_PASSWORD}" \
  '{username: $username, password: $password}')"
login_response="$(curl -fsS -b "${cookie_file}" -c "${cookie_file}" \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: ${csrf_token}" \
  -d "${login_payload}" "${api_base}/api/v1/auth/login")"
csrf_token="$(curl -fsS -b "${cookie_file}" -c "${cookie_file}" \
  "${api_base}/api/v1/auth/csrf" | jq -r '.data.token')"

mapfile -t store_ids < <(printf '%s' "${login_response}" \
  | jq -r '[.data.stores[] | select(.status == "ACTIVE")][0:2][] | .id')
if [[ "${#store_ids[@]}" -ne 2 ]]; then
  echo "Inventory smoke test requires two active stores." >&2
  exit 1
fi
source_store_id="${store_ids[0]}"
target_store_id="${store_ids[1]}"

category_id="$(curl -fsS -b "${cookie_file}" \
  "${api_base}/api/v1/item-categories?type=GIFT&activeOnly=true" | jq -r '.data[0].id // empty')"
unit_id="$(curl -fsS -b "${cookie_file}" \
  "${api_base}/api/v1/units?activeOnly=true" | jq -r '[.data[] | select(.decimalPlaces == 0)][0].id // empty')"
if [[ ! "${category_id}" =~ ^[0-9]+$ ]] || [[ ! "${unit_id}" =~ ^[0-9]+$ ]]; then
  echo "Active gift category or integer unit is missing." >&2
  exit 1
fi

stamp="$(date +%s%N)"
gift_code="GFT-SQL-${stamp}"
gift_payload="$(jq -n --arg code "${gift_code}" --argjson categoryId "${category_id}" \
  --argjson unitId "${unit_id}" \
  '{code: $code, name: "SQL持久化验证礼品", categoryId: $categoryId, unitId: $unitId,
    pointPrice: 100, costPrice: 10, lowStockThreshold: 2, description: "自动化真实库验收"}')"
gift_response="$(curl -fsS -b "${cookie_file}" -H 'Content-Type: application/json' \
  -H "X-XSRF-TOKEN: ${csrf_token}" -d "${gift_payload}" "${api_base}/api/v1/gifts")"
gift_id="$(printf '%s' "${gift_response}" | jq -r '.data.id // empty')"
if [[ ! "${gift_id}" =~ ^[0-9]+$ ]]; then
  echo "Gift creation failed: ${gift_response}" >&2
  exit 1
fi

today="$(date +%F)"
count_payload="$(jq -n --argjson storeId "${source_store_id}" --arg name "SQL持久化验证盘点" \
  --arg date "${today}" --argjson giftId "${gift_id}" --arg key "count-${stamp}" \
  '{storeId: $storeId, name: $name, countDate: $date, giftIds: [$giftId],
    remarks: "自动化真实库验收", idempotencyKey: $key}')"
count_response="$(curl -fsS -b "${cookie_file}" -H 'Content-Type: application/json' \
  -H "X-XSRF-TOKEN: ${csrf_token}" -d "${count_payload}" "${api_base}/api/v1/inventory-counts")"
count_id="$(printf '%s' "${count_response}" | jq -r '.data.id // empty')"
count_line_id="$(printf '%s' "${count_response}" | jq -r '.data.lines[0].id // empty')"
count_version="$(printf '%s' "${count_response}" | jq -r '.data.version // empty')"
if [[ ! "${count_id}" =~ ^[0-9]+$ ]] || [[ ! "${count_line_id}" =~ ^[0-9]+$ ]] || [[ -z "${count_version}" ]]; then
  echo "Count creation failed: ${count_response}" >&2
  exit 1
fi

count_lines_payload="$(jq -n --arg version "${count_version}" --argjson lineId "${count_line_id}" \
  '{version: $version, lines: [{lineId: $lineId, actualQuantity: 10}]}')"
count_ready="$(curl -fsS -X PUT -b "${cookie_file}" -H 'Content-Type: application/json' \
  -H "X-XSRF-TOKEN: ${csrf_token}" -d "${count_lines_payload}" \
  "${api_base}/api/v1/inventory-counts/${count_id}/lines")"
count_ready_version="$(printf '%s' "${count_ready}" | jq -r '.data.version // empty')"
count_confirm_payload="$(jq -n --arg version "${count_ready_version}" \
  '{version: $version, reason: "真实库盘盈验收"}')"
curl -fsS -b "${cookie_file}" -H 'Content-Type: application/json' \
  -H "X-XSRF-TOKEN: ${csrf_token}" -d "${count_confirm_payload}" \
  "${api_base}/api/v1/inventory-counts/${count_id}/confirm" >/dev/null

transfer_payload="$(jq -n --argjson sourceStoreId "${source_store_id}" \
  --argjson targetStoreId "${target_store_id}" --arg date "${today}" --argjson giftId "${gift_id}" \
  --arg key "transfer-${stamp}" \
  '{sourceStoreId: $sourceStoreId, targetStoreId: $targetStoreId, transferDate: $date,
    remarks: "自动化真实库验收", idempotencyKey: $key,
    lines: [{giftId: $giftId, quantity: 3, note: "真实库调拨验收"}]}')"
transfer_response="$(curl -fsS -b "${cookie_file}" -H 'Content-Type: application/json' \
  -H "X-XSRF-TOKEN: ${csrf_token}" -d "${transfer_payload}" \
  "${api_base}/api/v1/inventory-transfers")"
transfer_id="$(printf '%s' "${transfer_response}" | jq -r '.data.id // empty')"
transfer_version="$(printf '%s' "${transfer_response}" | jq -r '.data.version // empty')"
if [[ ! "${transfer_id}" =~ ^[0-9]+$ ]] || [[ -z "${transfer_version}" ]]; then
  echo "Transfer creation failed: ${transfer_response}" >&2
  exit 1
fi
transfer_confirm_payload="$(jq -n --arg version "${transfer_version}" \
  '{version: $version, reason: "真实库调拨验收"}')"
curl -fsS -b "${cookie_file}" -H 'Content-Type: application/json' \
  -H "X-XSRF-TOKEN: ${csrf_token}" -d "${transfer_confirm_payload}" \
  "${api_base}/api/v1/inventory-transfers/${transfer_id}/confirm" >/dev/null

database_evidence="$(docker compose --env-file "${env_file}" -f "${project_root}/infra/compose.yaml" exec -T \
  -e SQLCMDPASSWORD="${DB_PASSWORD}" sqlserver \
  /opt/mssql-tools18/bin/sqlcmd -S localhost -U "${DB_USERNAME}" -d "${DB_NAME}" -C -b -h -1 -W \
  -Q "SET NOCOUNT ON;
      SELECT CONCAT(
        (SELECT CONVERT(varchar(32), on_hand_quantity) FROM dbo.inv_stock WHERE store_id = ${source_store_id} AND gift_id = ${gift_id}), '|',
        (SELECT CONVERT(varchar(32), on_hand_quantity) FROM dbo.inv_stock WHERE store_id = ${target_store_id} AND gift_id = ${gift_id}), '|',
        (SELECT status FROM dbo.inv_count WHERE id = ${count_id}), '|',
        (SELECT status FROM dbo.inv_transfer WHERE id = ${transfer_id}), '|',
        (SELECT COUNT(1) FROM dbo.inv_stock_ledger WHERE gift_id = ${gift_id}));" \
  | tr -d '\r' | sed '/^[[:space:]]*$/d')"

expected="7.0000|3.0000|CONFIRMED|CONFIRMED|3"
if [[ "${database_evidence}" != "${expected}" ]]; then
  echo "Inventory database evidence mismatch for gift ${gift_id}: expected ${expected}, got ${database_evidence}" >&2
  exit 1
fi

echo "SQL Server inventory persistence verified: giftId=${gift_id}, countId=${count_id}, transferId=${transfer_id}, evidence=${database_evidence}."
