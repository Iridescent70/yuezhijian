SHELL := /usr/bin/env bash
.DEFAULT_GOAL := help

.PHONY: help doctor bootstrap infra-up infra-down infra-status db-init backend-dev backend-dev-db frontend-dev test build verify

help:
	@echo "make doctor        Check local toolchain"
	@echo "make bootstrap     Install project dependencies and Maven Wrapper"
	@echo "make infra-up      Start SQL Server, Redis and MinIO"
	@echo "make db-init       Create database and import the Yudao SQL Server baseline once"
	@echo "make backend-dev   Build and start the Yudao monolith with the yuezhijian profile"
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
	@./mvnw -f backend/pom.xml --version
	@pnpm install

infra-up:
	@test -f .env.local || (echo "Copy .env.example to .env.local and replace placeholders." >&2; exit 1)
	@docker compose --env-file .env.local -f infra/compose.yaml up -d sqlserver redis minio

infra-down:
	@test -f .env.local || (echo "Missing .env.local" >&2; exit 1)
	@docker compose --env-file .env.local -f infra/compose.yaml down

infra-status:
	@test -f .env.local || (echo "Missing .env.local" >&2; exit 1)
	@docker compose --env-file .env.local -f infra/compose.yaml ps

db-init:
	@bash tools/scripts/init-local-db.sh

backend-dev:
	@test -f .env.local || (echo "Missing .env.local; copy .env.example and set local secrets first." >&2; exit 1)
	@./mvnw -f backend/pom.xml -pl yudao-server -am -DskipTests package
	@set -a; source .env.local; set +a; java -jar backend/yudao-server/target/yudao-server.jar --spring.profiles.active=yuezhijian

backend-dev-db:
	@$(MAKE) backend-dev

frontend-dev:
	@pnpm --filter @yuezhijian/admin dev

test:
	@./mvnw -f backend/pom.xml test
	@pnpm typecheck

build:
	@./mvnw -f backend/pom.xml -DskipTests package
	@pnpm build

verify:
	@./mvnw -f backend/pom.xml test
	@pnpm typecheck
	@pnpm build
