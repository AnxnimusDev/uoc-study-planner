SHELL := /bin/bash
.DEFAULT_GOAL := help

-include .env
export

RAW_DIR := backend/src/test/resources/fixtures/raw
FIXTURE_DIR := backend/src/test/resources/fixtures/canvas

.PHONY: help up down test run login sync fixtures format

help: ## Muestra esta ayuda
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-10s %s\n", $$1, $$2}'

up: ## Arranca PostgreSQL en Docker
	docker compose up -d --wait

down: ## Para los contenedores
	docker compose down

test: ## Ejecuta todos los tests del backend
	cd backend && ./mvnw -B verify

run: up ## Arranca el backend en local (perfil dev)
	cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

login: up ## Abre el navegador para iniciar sesión en la UOC y guarda la cookie cifrada
	cd backend && ./mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments=--planner.command=login

sync: up ## Sincroniza Canvas con la sesión guardada
	cd backend && ./mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments=--planner.command=sync

fixtures: ## Anonimiza todos los JSON crudos de fixtures/raw en fixtures/canvas
	@mkdir -p $(FIXTURE_DIR)
	@for f in $(RAW_DIR)/*.json; do \
		[ -e "$$f" ] || { echo "No hay JSON en $(RAW_DIR)"; exit 0; }; \
		python3 scripts/anonymize_fixtures.py "$$f" "$(FIXTURE_DIR)/$$(basename $$f)"; \
	done

format: ## Formatea el código Java
	cd backend && ./mvnw spotless:apply
