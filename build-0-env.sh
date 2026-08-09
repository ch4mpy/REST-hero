#!/usr/bin/env bash

HOST_NAME=host.docker.internal

# Default values for .env variables. Comments removed on purpose: entries are
# appended one by one below, existing lines are left untouched.
ENV_DEFAULTS=(
  "CN=${HOST_NAME}"
  "HOST_NAME=${HOST_NAME}"
  "MAIL_HOST=${HOST_NAME}"
  "MAIL_PORT=1025"
  "KC_DB_IMAGE=postgres:18"
  "KC_DB_HOST=host.docker.internal"
  "KC_DB_PORT=2632"
  "KC_DB_NAME=keycloak"
  "KC_DB_SCHEMA=public"
  "KC_DB_USERNAME=keycloak"
  'KC_DB_URL=jdbc:postgresql://${KC_DB_HOST}:${KC_DB_PORT}/${KC_DB_NAME}?currentSchema=${KC_DB_SCHEMA}'
  "KC_VERSION=26.6"
  "KC_DB=postgres"
  "KC_HTTP_ENABLED=false"
  "KC_HTTPS_PORT=3643"
  "KC_LOG_LEVEL=INFO"
  "ACCOUNTS_DB_PORT=2633"
  "CARDS_DB_PORT=2634"
  "CUSTOMERS_DB_PORT=2635"
  "RABBITMQ_PORT=5672"
  "RABBITMQ_MANAGEMENT_PORT=15672"
  "RABBITMQ_USER=rest-api"
  "RABBITMQ_PASSWORD=secret"
)

# Appends "KEY=value" to .env only if KEY is not already defined there.
add_env_var_if_missing() {
  local key="${1%%=*}"
  if ! grep -q "^${key}=" ./.env; then
    echo "$1" >> ./.env
  fi
}

if [ ! -f ./.env ]; then
  echo ".env file not found, creating one."
  touch ./.env
fi

for entry in "${ENV_DEFAULTS[@]}"; do
  add_env_var_if_missing "$entry"
done

source ./.env

if [ ! -d ./secrets ]; then
  mkdir ./secrets
fi

if [ ! -d ./secrets/ssl ]; then
  mkdir ./secrets/ssl
fi
if [ ! -f ./secrets/ssl/password.txt ]; then
  if [ -f ~/.ssh/${CN}.password.txt ]; then
    cp ~/.ssh/${CN}.password.txt ./secrets/ssl/password.txt
  else
    echo $(openssl rand -hex 16) > ./secrets/ssl/password.txt
    chmod 600 ./secrets/ssl/password.txt
    cp ./secrets/ssl/password.txt ~/.ssh/${CN}.password.txt
  fi
fi

if [ ! -d ./secrets/keycloak ]; then
  mkdir ./secrets/keycloak
fi
if [ ! -f ./secrets/keycloak/postgres_user.txt ]; then
  echo $KC_DB_USERNAME > ./secrets/keycloak/postgres_user.txt
  chmod 600 ./secrets/keycloak/postgres_user.txt
fi
if [ ! -f ./secrets/keycloak/postgres_password.txt ]; then
  echo $(openssl rand -hex 16) > ./secrets/keycloak/postgres_password.txt
  chmod 600 ./secrets/keycloak/postgres_password.txt
fi
if [ ! -f ./secrets/keycloak/admin_user.txt ]; then
  echo admin > ./secrets/keycloak/admin_user.txt
  chmod 600 ./secrets/keycloak/admin_user.txt
fi
if [ ! -f ./secrets/keycloak/admin_password.txt ]; then
  echo secret > ./secrets/keycloak/admin_password.txt
  chmod 600 ./secrets/keycloak/admin_password.txt
fi

if [ ! -d ./secrets/mail ]; then
  mkdir ./secrets/mail

fi
if [ ! -f ./secrets/mail/username.txt ]; then
  echo "mailpit" > ./secrets/mail/username.txt
  chmod 600 ./secrets/mail/username.txt
fi
if [ ! -f ./secrets/mail/password.txt ]; then
  echo $(openssl rand -hex 16) > ./secrets/mail/password.txt
  chmod 600 ./secrets/mail/password.txt
fi
if [ -f ./secrets/mail/auth.txt ]; then
  rm -f ./secrets/mail/auth.txt
fi
echo "$(cat ./secrets/mail/username.txt):$(cat ./secrets/mail/password.txt)" > ./secrets/mail/auth.txt
chmod 600 ./secrets/mail/auth.txt

if [ ! -d ./secrets/rest-api ]; then
  mkdir ./secrets/rest-api
fi
if [ ! -f ./secrets/rest-api/postgres_password.txt ]; then
  echo $(openssl rand -hex 16) > ./secrets/rest-api/postgres_password.txt
  chmod 600 ./secrets/rest-api/postgres_password.txt
fi
add_env_var_if_missing "SPRING_DATASOURCE_PASSWORD=$(cat ./secrets/rest-api/postgres_password.txt)"

# Generate self-signed SSL certificates
if [ ! -d ./certs ]; then
  mkdir ./certs
fi
if [ ! -f ./certs/$CN.crt ]; then
  if [ ! -f ~/.ssh/${CN}.crt ]; then
    export SSL_PASSWORD=$(cat ./secrets/ssl/password.txt)
    echo "Generating self-signed SSL certificates for ${CN} in ~/.ssh"
    bash ./certs/self-signed.sh $CN $SSL_PASSWORD `echo ~/.ssh`

    echo "Adding self-signed SSL certificates for ${CN} to JRE cacerts"
    bash ./certs/add-to-jre-cacerts.sh ~/.ssh/$CN.jks $SSL_PASSWORD $CN
  fi

  echo "Copying self-signed SSL certificates for ${CN} from ~/.ssh to ./certs"
  cp ~/.ssh/${CN}.* ./certs/
fi
