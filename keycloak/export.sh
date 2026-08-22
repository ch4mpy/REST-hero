#!/usr/bin/env bash

cd "$(dirname "$0")/.."

source ./.env
export KC_DB_USERNAME=$(cat ./secrets/keycloak/postgres_user.txt)
export KC_DB_PASSWORD=$(cat ./secrets/keycloak/postgres_password.txt)

docker exec \
  -e KC_DB=postgres \
  -e KC_DB_HOST="${KC_DB_HOST}" \
  -e KC_DB_PORT="${KC_DB_PORT}" \
  -e KC_DB_NAME="${KC_DB_NAME}" \
  -e KC_DB_SCHEMA="${KC_DB_SCHEMA}" \
  -e KC_DB_URL="${KC_DB_URL}" \
  -e KC_DB_USERNAME="${KC_DB_USERNAME}" \
  -e KC_DB_PASSWORD="${KC_DB_PASSWORD}" \
  rest-hero.keycloak-server \
  /opt/keycloak/bin/kc.sh export --dir /tmp/keycloak/ --users realm_file

tmp_dir=$(mktemp -d)
docker cp rest-hero.keycloak-server:/tmp/keycloak/. "${tmp_dir}/"
cp "${tmp_dir}"/*-realm.json ./keycloak/import/
rm -rf "${tmp_dir}"