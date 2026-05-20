# app

Scala web UI on top of `hledger-web`.

## Build

| Command                      | Effect                                                             |
|------------------------------|--------------------------------------------------------------------|
| `./sbtx compile`             | Compile all modules (`shared`, `backend`, `frontend`).             |
| `./sbtx frontend/fastLinkJS` | Link the frontend to `main.js` (dev, unminified).                  |
| `./sbtx frontend/fullLinkJS` | Link the frontend minified (production).                           |
| `./sbtx backend/assembly`    | Build the backend fat jar at `backend/target/scala-3.3.4/app.jar`. |
| `./sbtx clean`               | Remove all `target/` output.                                       |

## Run, no docker

Two shells, both in `app/`:

```sh
# Shell A — rebuild main.js on every save
./sbtx ~frontend/fastLinkJS

# Shell B — copy index.html next to the linker output (one-time), then run the backend
cp frontend/src/main/resources/index.html \
   frontend/target/scala-3.3.4/app-frontend-fastopt/

APP_ASSETS_DIR=frontend/target/scala-3.3.4/app-frontend-fastopt \
HLEDGER_WEB_URL=http://localhost:5000 \
./sbtx backend/run
```

Then refresh the browser at `http://localhost:8080` after `fastLinkJS` reruns.

`hledger-web` must be reachable at `HLEDGER_WEB_URL`. Start one with:

```sh
hledger-web --serve-api -f ../data/2026.hledger.journal --host 127.0.0.1 --port 5000
```

## Lint / Format

| Command                                        | Effect                                       |
|------------------------------------------------|----------------------------------------------|
| `./sbtx scalafmtAll`                           | Format all sources in-place.                 |
| `./sbtx scalafmtCheckAll`                      | Fail if anything is unformatted (use in CI). |
| `./sbtx "scalafixAll OrganizeImports"`         | Run scalafix rewrites.                       |
| `./sbtx "scalafixAll --check OrganizeImports"` | Check-only (use in CI).                      |

## Docker

From the repo root (not this directory):

```sh
docker build -f Dockerfile-hledger -t my-hledger .
./docker_run
```

`docker_run` expects a journal at `data/2026.hledger.journal` and publishes `:8080`. `HLEDGER_JOURNAL` is passed in via
`-e` and must always be supplied explicitly.
