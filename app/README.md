# app

Scala web UI on top of `hledger-web`.

- `frontend/` — Scala.js + Laminar + Chart.js viewer.
- `backend/` — http4s server that proxies `hledger-web` JSON and serves the frontend assets.
- `shared/` — DTOs shared between the two.

Scala 3.8.3 (see `frontend/target/scala-3.8.3/`, `backend/target/scala-3.8.3/`).

## Build

| Command                      | Effect                                                             |
|------------------------------|--------------------------------------------------------------------|
| `./sbtx compile`             | Compile all modules (`shared`, `backend`, `frontend`).             |
| `./sbtx frontend/fastLinkJS` | Link the frontend to `main.js` (dev, unminified).                  |
| `./sbtx frontend/fullLinkJS` | Link the frontend minified (production).                           |
| `./sbtx backend/assembly`    | Build the backend fat jar at `backend/target/scala-3.8.3/app.jar`. |
| `./sbtx clean`               | Remove all `target/` output.                                       |

## Run locally (no docker)

You need two things running:

1. **`hledger-web`** reachable at `HLEDGER_WEB_URL` (defaults to `http://localhost:5000`):
   ```sh
   hledger-web --serve-api -f ../data/2026.hledger.journal --host 127.0.0.1 --port 5000
   ```
2. **The sbt `dev` command**, which forks the backend via sbt-revolver and watches both modules — any change to backend or frontend sources re-links `main.js` and restarts the JVM:
   ```sh
   APP_ASSETS_DIR=frontend/src/main/resources \
   HLEDGER_WEB_URL=http://localhost:5000 \
   ./sbtx dev
   ```

   Required env vars:
   - `APP_ASSETS_DIR` — directory the backend serves static assets from (point at the frontend resources dir so `main.js` lands next to `index.html`).
   - `HLEDGER_WEB_URL` — base URL of the `hledger-web` instance to proxy.

   Stop the forked backend with `devStop` inside the sbt shell.

Then browse to `http://localhost:8081`. Backend changes restart automatically; frontend changes just need a refresh.

### Manual two-shell setup (alternative)

```sh
# Shell A — rebuild main.js on every save
./sbtx ~frontend/fastLinkJS

# Shell B — copy index.html next to the linker output (one-time), then run the backend
cp frontend/src/main/resources/index.html \
   frontend/target/scala-3.8.3/app-frontend-fastopt/

APP_ASSETS_DIR=frontend/target/scala-3.8.3/app-frontend-fastopt \
HLEDGER_WEB_URL=http://localhost:5000 \
./sbtx backend/run
```

## Lint / Format

| Command                                        | Effect                                       |
|------------------------------------------------|----------------------------------------------|
| `./sbtx scalafmtAll`                           | Format all sources in-place.                 |
| `./sbtx scalafmtCheckAll`                      | Fail if anything is unformatted (use in CI). |
| `./sbtx "scalafixAll OrganizeImports"`         | Run scalafix rewrites.                       |
| `./sbtx "scalafixAll --check OrganizeImports"` | Check-only (use in CI).                      |

## Docker

Two images live at the repo root:

- `Dockerfile-hledger` — minimal Alpine image with the `hledger` / `hledger-web` CLIs only.
- `Dockerfile-hledgerapp` — full stack: builds the Scala frontend + backend, vendors Chart.js with pnpm, builds the Go CLI (`cli/`), and ships them on a Temurin 25 JRE alongside `hledger-web`. Only `:8081` is exposed; `hledger-web` runs internally on `:5000`.

Build and run the full stack:

```sh
# From the repo root.
docker build -f Dockerfile-hledgerapp -t hledger-app .

# Use the Go CLI to launch it (handles bind mounts, port publish, signal forwarding).
go run ./cli run-web \
  --data ./data \
  --journal /opt/hledger_data/2026.hledger.journal \
  --port 8081
```

The journal path is the container path under `/opt/hledger_data`; `--data` is the host directory bind-mounted there. The CLI validates that the journal exists on the host before launching docker. See `cli/README` (TBD) or `cli/cli.go` for all flags.

`HLEDGER_JOURNAL` is intentionally not baked into the image — it is set from `--journal` at run time so the image stays journal-agnostic.
