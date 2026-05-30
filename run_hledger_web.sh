#! /usr/bin/env sh
set -eu

usage() {
  echo "usage: $0 -h <host dir abs path to map at docker volume> -j <name of journal from host dir> [-d]" >&2
  exit 2
}

host_volume=""
journal=""
run_mode=""

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

journal_path="$host_volume/$journal"
[ -f "$journal_path" ] || { echo "error: journal not found: $journal_path" >&2; exit 1; }

docker rm -f c_hledger > /dev/null 2>&1

if [[ "$run_mode" == "-d" ]]; then
  docker run -d \
    --name c_hledger \
    -v "$host_volume:/opt/hledger_data" \
    -e "LEDGER_FILE=/opt/hledger_data/$journal" \
    -p 5000:5000 \
    --entrypoint hledger-web \
    hledger --serve --host=0.0.0.0 --port=5000 "$@"

  sleep 1
  docker logs c_hledger
else
  docker run --rm -it \
    --name c_hledger \
    -v "$host_volume:/opt/hledger_data" \
    -e "LEDGER_FILE=/opt/hledger_data/$journal" \
    -p 5000:5000 \
    --entrypoint hledger-web \
    hledger --serve --host=0.0.0.0 --port=5000 "$@"
fi
