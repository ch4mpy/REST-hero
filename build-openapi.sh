#!/usr/bin/env bash
# Builds every module with the "openapi" profile, which starts each service to read its OpenAPI
# spec and writes it in frontend/openapi. The services read their certificate in
# $REST_HERO_CERTS_DIR (see .env, written by build-0-env.sh): .env is loaded first.
cd "$(dirname "$0")/api"
set -a
source ../.env
set +a
./mvnw install -Popenapi,h2 -DskipTests
