set -e

docker compose build common
docker compose build $(docker com pose config --services | grep -v "common")