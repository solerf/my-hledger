# cli

```
Usage: my-hledger-cli <command>

my-hledger controller

Flags:
  -h, --help    Show context-sensitive help.

Commands:
  run-web --journal=STRING [flags]
    Start the hledger-app in docker.

  import --journal=STRING <csv> [flags]
    Import a CSV into the journal.

Run "my-hledger-cli <command> --help" for more information on a command.
```

## run-web

```
Usage: my-hledger-cli run-web --journal=STRING [flags]

Start the hledger-app in docker.

Flags:
  -h, --help                   Show context-sensitive help.

  -j, --journal=STRING         Journal file name (resolved under --data on the
                               host and /opt/hledger_data in the container).
  -d, --data="data"            Host directory mounted again container
                               /opt/hledger_data.
      --image="hledger-app"    Docker image tag to run.
  -p, --port=8081              Host port to publish for the viewer.
  -D, --detach                 Run container detached instead of foreground.
```

## import

```
Usage: my-hledger-cli import --journal=STRING <csv> [flags]

Import a CSV into the journal.

Arguments:
  <csv>    Host path to the CSV statement to import (must live under --data).

Flags:
  -h, --help               Show context-sensitive help.

  -j, --journal=STRING     Host path to the target journal (must live under
                           --data).
  -d, --data="data"        Host directory mounted at container
                           /opt/hledger_data.
      --image="hledger"    Docker image tag providing the hledger CLI.
      --no-backup          Skip writing the .BKP copy of the journal before
                           import.
      --dry-run            Print the docker command and skip mutations (no
                           backup, no import, no truncate).
```
