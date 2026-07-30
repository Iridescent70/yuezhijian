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

schema_initialized="$(docker compose --env-file "${env_file}" -f "${project_root}/infra/compose.yaml" exec -T \
  -e SQLCMDPASSWORD="${MSSQL_SA_PASSWORD}" sqlserver \
  /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -d "${DB_NAME}" -C -b -h -1 -W \
  -Q "SET NOCOUNT ON; SELECT CASE WHEN OBJECT_ID(N'dbo.yuezhijian_schema_baseline', N'U') IS NULL THEN 0 ELSE 1 END;" \
  | tr -d '[:space:]')"

if [[ "${schema_initialized}" == "0" ]]; then
  user_table_count="$(docker compose --env-file "${env_file}" -f "${project_root}/infra/compose.yaml" exec -T \
    -e SQLCMDPASSWORD="${MSSQL_SA_PASSWORD}" sqlserver \
    /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -d "${DB_NAME}" -C -b -h -1 -W \
    -Q "SET NOCOUNT ON; SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped = 0;" \
    | tr -d '[:space:]')"
  if [[ "${user_table_count}" != "0" ]]; then
    echo "Refusing to run the destructive Yudao baseline: ${DB_NAME} already contains ${user_table_count} user tables but has no baseline marker." >&2
    echo "Use a new empty DB_NAME. Never point this command at the main or client legacy database." >&2
    exit 1
  fi

  echo "Importing the Yudao SQL Server baseline into ${DB_NAME}..."
  docker compose --env-file "${env_file}" -f "${project_root}/infra/compose.yaml" exec -T \
    -e SQLCMDPASSWORD="${MSSQL_SA_PASSWORD}" sqlserver \
    /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -d "${DB_NAME}" -C -b \
    < "${project_root}/backend/sql/sqlserver/ruoyi-vue-pro.sql"

  baseline_sql="CREATE TABLE dbo.yuezhijian_schema_baseline
  (
      id bigint NOT NULL PRIMARY KEY,
      upstream_repository nvarchar(255) NOT NULL,
      upstream_commit char(40) NOT NULL,
      imported_at datetime2 NOT NULL DEFAULT CURRENT_TIMESTAMP
  );
  INSERT INTO dbo.yuezhijian_schema_baseline (id, upstream_repository, upstream_commit)
  VALUES (1, N'https://github.com/YunaiV/ruoyi-vue-pro', N'ec3f7cbf73e88514a70a6b59d365092ee470603d');"
  docker compose --env-file "${env_file}" -f "${project_root}/infra/compose.yaml" exec -T \
    -e SQLCMDPASSWORD="${MSSQL_SA_PASSWORD}" sqlserver \
    /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -d "${DB_NAME}" -C -b -Q "${baseline_sql}"
else
  echo "Yudao SQL Server baseline already exists; skipping the destructive upstream import."
fi

echo "Local database ${DB_NAME}, Yudao baseline and login ${DB_USERNAME} are ready."
