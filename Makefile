SHELL := /usr/bin/env bash
.DEFAULT_GOAL := help

.PHONY: help doctor bootstrap infra-up infra-down infra-status db-init backend-dev backend-dev-db frontend-dev test build verify

help:
	@echo "make doctor        Check local toolchain"
	@echo "make bootstrap     Install project dependencies and Maven Wrapper"
	@echo "make infra-up      Start SQL Server and MinIO"
	@echo "make db-init       Create the local database and application login"
	@echo "make backend-dev   Start backend with in-memory development profile"
	@echo "make backend-dev-db Start backend with SQL Server and Flyway"
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

backend-dev:
	@./mvnw -pl backend spring-boot:run

backend-dev-db:
	@test -f .env.local || (echo "Missing .env.local" >&2; exit 1)
	@set -a; source .env.local; set +a; SPRING_PROFILES_ACTIVE=sqlserver ./mvnw -pl backend spring-boot:run

frontend-dev:
	@pnpm --filter @yuezhijian/frontend dev

test:
	@./mvnw test
	@pnpm test

build:
	@./mvnw -DskipTests package
	@pnpm build

verify:
	@./mvnw test
	@pnpm typecheck
	@pnpm test
	@pnpm build
