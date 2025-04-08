#!/bin/bash
set -e

docker compose build common
docker compose build $(docker compose config --services | grep -v "common")