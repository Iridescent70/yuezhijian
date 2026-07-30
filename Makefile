SHELL := /usr/bin/env bash
.DEFAULT_GOAL := help

.PHONY: help doctor bootstrap infra-up infra-down infra-status db-init db-smoke inventory-smoke backend-dev backend-dev-db backend-dev-memory frontend-dev test build verify

help:
	@echo "make doctor        Check local toolchain"
	@echo "make bootstrap     Install project dependencies and Maven Wrapper"
	@echo "make infra-up      Start SQL Server and MinIO"
	@echo "make db-init       Create the local database and application login"
	@echo "make db-smoke      Prove API writes member/card/accounts to SQL Server"
	@echo "make inventory-smoke Prove count/transfer balances and ledgers in SQL Server"
	@echo "make backend-dev   Start backend with SQL Server and Flyway (persistent)"
	@echo "make backend-dev-memory Start disposable in-memory backend explicitly"
	@echo "make frontend-dev  Start Vue development server"
	@echo "make verify        Run backend tests and frontend checks/build"

doctor:
	@java -version
	@mvn --version
	@node --version
	@pnpm --version
	@docker --version
	@docker compose version

bootstrap:
	@./mvnw --version
	@pnpm install

infra-up:
	@test -f .env.local || (echo "Copy .env.example to .env.local and replace placeholders." >&2; exit 1)
	@docker compose --env-file .env.local -f infra/compose.yaml up -d sqlserver minio

infra-down:
	@test -f .env.local || (echo "Missing .env.local" >&2; exit 1)
	@docker compose --env-file .env.local -f infra/compose.yaml down

infra-status:
	@test -f .env.local || (echo "Missing .env.local" >&2; exit 1)
	@docker compose --env-file .env.local -f infra/compose.yaml ps

db-init:
	@bash tools/scripts/init-local-db.sh

db-smoke:
	@bash tools/scripts/verify-sqlserver-persistence.sh

inventory-smoke:
	@bash tools/scripts/verify-inventory-persistence.sh

backend-dev:
	@test -f .env.local || (echo "Missing .env.local; persistent development will not fall back to memory." >&2; exit 1)
	@set -a; source .env.local; set +a; SPRING_PROFILES_ACTIVE=sqlserver ./mvnw -pl backend spring-boot:run

backend-dev-db:
	@$(MAKE) backend-dev

backend-dev-memory:
	@echo "WARNING: memory profile is disposable; all changes disappear when the process stops." >&2
	@SPRING_PROFILES_ACTIVE=memory ./mvnw -pl backend spring-boot:run

frontend-dev:
	@pnpm --filter @yuezhijian/admin dev

test:
	@./mvnw test
	@pnpm typecheck

build:
	@./mvnw -DskipTests package
	@pnpm build

verify:
	@./mvnw test
	@pnpm typecheck
	@pnpm build
