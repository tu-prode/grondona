if [ -z "$(docker images -q grondona-linter:latest 2> /dev/null)" ]; then
  echo "Linter image does not exist. Building..."
  docker build -t grondona-linter:latest -f ./infra/linter/ktlint.Dockerfile .
else
  echo "Linter image already exists. Skipping build."
fi
