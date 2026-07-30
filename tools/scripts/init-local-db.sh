#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${project_root}/.env.local"

if [[ ! -f "${env_file}" ]]; then
  echo "Missing .env.local. Copy .env.example and replace every placeholder first." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "${env_file}"
set +a

: "${MSSQL_SA_PASSWORD:?MSSQL_SA_PASSWORD is required}"
: "${DB_NAME:?DB_NAME is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

if [[ ! "${DB_NAME}" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "DB_NAME may only contain letters, numbers and underscore." >&2
  exit 1
fi
if [[ ! "${DB_USERNAME}" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "DB_USERNAME may only contain letters, numbers and underscore." >&2
  exit 1
fi

escaped_app_password=${DB_PASSWORD//\'/\'\'}
server_sql="IF DB_ID(N'${DB_NAME}') IS NULL CREATE DATABASE [${DB_NAME}];
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'${DB_USERNAME}')
    CREATE LOGIN [${DB_USERNAME}] WITH PASSWORD = N'${escaped_app_password}', CHECK_POLICY = ON;"
database_sql="IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'${DB_USERNAME}')
    CREATE USER [${DB_USERNAME}] FOR LOGIN [${DB_USERNAME}];
ALTER ROLE db_datareader ADD MEMBER [${DB_USERNAME}];
ALTER ROLE db_datawriter ADD MEMBER [${DB_USERNAME}];
ALTER ROLE db_ddladmin ADD MEMBER [${DB_USERNAME}];"

docker compose --env-file "${env_file}" -f "${project_root}/infra/compose.yaml" exec -T \
  -e SQLCMDPASSWORD="${MSSQL_SA_PASSWORD}" sqlserver \
  /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b -Q "${server_sql}"

docker compose --env-file "${env_file}" -f "${project_root}/infra/compose.yaml" exec -T \
  -e SQLCMDPASSWORD="${MSSQL_SA_PASSWORD}" sqlserver \
  /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -d "${DB_NAME}" -C -b -Q "${database_sql}"

echo "Local database ${DB_NAME} and login ${DB_USERNAME} are ready."
