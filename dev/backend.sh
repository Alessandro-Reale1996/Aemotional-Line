#!/bin/sh
# Starts the backend for development (dev/README.md): the database password from dev/.env, the settings of
# dev/application.properties. Arguments go to the application, e.g. --create-admin=you@example.org --name=Operatore
set -eu
cd "$(dirname "$0")/.."

if [ ! -f dev/.env ]
then
	echo "dev/.env is missing: copy dev/.env.example to dev/.env and choose the passwords." >&2
	exit 1
fi

set -a
. ./dev/.env
set +a

exec ./mvnw -q spring-boot:run -Dspring-boot.run.arguments="--spring.config.additional-location=file:dev/application.properties $*"
