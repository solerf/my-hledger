// my-hledger-app: web viewer for an hledger journal. Serves server-rendered
// views and a small JSON API on top of a running hledger-web (--serve-api)
// instance, plus the static frontend assets.
package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"net/url"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/alecthomas/kong"

	"github.com/solerf/my-hledger/app/internal/hledger"
	"github.com/solerf/my-hledger/app/internal/service"
	"github.com/solerf/my-hledger/app/internal/web"
)

type cli struct {
	Bind          string `name:"bind" default:"0.0.0.0:8081" env:"APP_BIND" help:"Address to serve the app on."`
	AssetsDir     string `name:"assets-dir" default:"assets" env:"APP_ASSETS_DIR" help:"Directory the static frontend assets are served from."`
	HledgerWebURL string `name:"hledger-web-url" default:"http://localhost:5000" env:"HLEDGER_WEB_URL" help:"Base URL of the hledger-web (--serve-api) instance."`
}

func (c *cli) Run() error {
	log := slog.New(slog.NewTextHandler(os.Stderr, nil))

	base, err := url.Parse(c.HledgerWebURL)
	if err != nil {
		return fmt.Errorf("invalid hledger-web url %q: %w", c.HledgerWebURL, err)
	}
	if _, err := os.Stat(c.AssetsDir); err != nil {
		return fmt.Errorf("assets dir: %w", err)
	}

	log.Info("starting app", "bind", c.Bind, "assets", c.AssetsDir, "hledger-web", base)

	api := hledger.NewClient(base, &http.Client{Timeout: 60 * time.Second})
	svc := service.NewExpenses(api, log)
	server := web.New(svc, c.AssetsDir, log)

	httpServer := &http.Server{
		Addr:              c.Bind,
		Handler:           server.Router(),
		ReadHeaderTimeout: 10 * time.Second,
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	errCh := make(chan error, 1)
	go func() {
		log.Info("server ready", "url", "http://"+c.Bind)
		errCh <- httpServer.ListenAndServe()
	}()

	select {
	case err := <-errCh:
		return err
	case <-ctx.Done():
		log.Info("shutting down")
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if err := httpServer.Shutdown(shutdownCtx); err != nil {
			return err
		}
		if err := <-errCh; !errors.Is(err, http.ErrServerClosed) {
			return err
		}
		return nil
	}
}

func main() {
	c := cli{}
	ctx := kong.Parse(&c,
		kong.Name("my-hledger-app"),
		kong.Description("hledger journal web viewer (talks to hledger-web --serve-api)"),
		kong.UsageOnError(),
	)
	ctx.FatalIfErrorf(ctx.Run())
}
