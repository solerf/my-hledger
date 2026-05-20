package main

import (
	"github.com/alecthomas/kong"
)

func main() {
	ctx := kong.Parse(&cli,
		kong.Name("my-hledger-cli"),
		kong.Description("my-hledger controller"),
		kong.UsageOnError(),
	)
	ctx.FatalIfErrorf(ctx.Run())
}
