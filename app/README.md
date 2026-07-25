# app

Go web UI on top of `hledger-web`.

- `main.go` — kong CLI entrypoint (`my-hledger-app`).
- `internal/hledger/` — client for `hledger-web --serve-api` (read journal, `PUT /add`).
- `internal/service/` — expenses service (monthly mapping, account list, add grouping).
- `internal/web/` — chi router, JSON API, `html/template` views (embedded), static assets.
- `assets/` — CSS, page scripts (`js/charts.js`, `js/manual.js`) and the pnpm-vendored
  deps under `vendor/` (generated, not committed).
- `package.json` — frontend JS deps; `pnpm install` runs the vendor script.

## Build

```sh
go build -o my-hledger-app .
```

## Run locally (no docker)

You need two things running:

1. **`hledger-web`** reachable at `--hledger-web-url` (defaults to `http://localhost:5000`):
   ```sh
   hledger-web --serve-api -f ../data/2026.hledger.journal --host 127.0.0.1 --port 5000
   ```
2. **The app** (from this directory, so the default assets dir resolves):
   ```sh
   go run .
   ```

Then browse to `http://localhost:8081`.

Flags (each with an env fallback):

| Flag                | Env               | Default                       |
|---------------------|-------------------|-------------------------------|
| `--bind`            | `APP_BIND`        | `0.0.0.0:8081`                |
| `--assets-dir`      | `APP_ASSETS_DIR`  | `assets`                      |
| `--hledger-web-url` | `HLEDGER_WEB_URL` | `http://localhost:5000`       |

## Frontend deps

`package.json` vendors Chart.js and Bootstrap into `assets/vendor/` — run
`pnpm install` here after bumping versions. Everything else (styles under
`assets/css/`, `assets/app.css`, page scripts under `assets/js/`) is
committed as-is.

## Views

- **Monthly** (`/monthly?month=YYYY-MM`) — expense/liability pies, per-account and
  cumulative line charts, and the entries table for the selected month.
- **Year To Now** (`/year-to-now`) — cumulative line per (main account, currency).
- **Manual Entry** (`/manual-entry`) — draft entries, then save; the backend groups
  them into one hledger transaction per date via `hledger-web`'s `PUT /add`.

JSON API (used by the manual-entry page, also handy for scripting):
`GET /api/expenses/monthly[?month=]`, `GET /api/accounts`, `GET /api/health`,
`POST /api/transactions`.
