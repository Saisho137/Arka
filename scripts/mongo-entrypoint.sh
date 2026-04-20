#!/bin/bash
set -e

# Prepare the keyFile with correct ownership/permissions for mongod.
# The host-mounted file is read-only and owned by root; mongod requires
# the keyFile to be owned by the mongodb user (uid 999) with mode 400.
mkdir -p /etc/mongo
cp /tmp/mongo-keyfile-ro /etc/mongo/keyfile
chmod 400 /etc/mongo/keyfile
chown 999:999 /etc/mongo/keyfile

# Delegate to the official MongoDB entrypoint, which creates the admin user
# on a temporary mongod and then starts the real mongod with the provided args.
exec /usr/local/bin/docker-entrypoint.sh "$@"
