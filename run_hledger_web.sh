#! /usr/bin/env sh
set -eu

usage() {
  echo "usage: $0 -h <host dir to map at docker volume> -j <name of journal from host dir> [-d]" >&2
  exit 2
}

host_volume=""
journal=""
run_mode="-it"

while getopts "h:j:d" opt; do
  case "$opt" in
    h) host_volume=$OPTARG ;;
    j) journal=$OPTARG ;;
    d) run_mode="-d" ;;
    *) usage ;;
  esac
done
shift $((OPTIND - 1))

[ -n "$host_volume" ] && [ -n "$journal" ] || usage

docker run --rm "$run_mode" \
  -v "$host_volume:/opt/hledger_data" \
  -e "LEDGER_FILE=/opt/hledger_data/$journal" \
  -p 5000:5000 \
  --entrypoint hledger-web \
  hledger \
  --serve --host=0.0.0.0 --port=5000 "$@"
