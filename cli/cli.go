package main

import (
	"context"
	"fmt"
	"os"
	"os/signal"
	"path/filepath"
	"strings"
	"syscall"

	"github.com/felipesoler/my-hledger/cli/internal/docker"
)

const containerDataDir = "/opt/hledger_data"

type RunWebCmd struct {
	Journal  string `name:"journal" short:"j" required:"true" help:"Journal path inside the container."`
	DataDir  string `name:"data" short:"d" default:"data" help:"Host directory mounted again container /opt/hledger_data."`
	Image    string `name:"image" default:"hledger-app" help:"Docker image tag to run."`
	Port     int    `name:"port" short:"p" default:"8080" help:"Host port to publish for the viewer."`
	Detached bool   `name:"detach" short:"D" help:"Run container detached instead of foreground."`
}

func (c *RunWebCmd) Run() error {
	absData, err := filepath.Abs(c.DataDir)
	if err != nil {
		return fmt.Errorf("resolving data dir: %w", err)
	}
	if _, err := os.Stat(absData); err != nil {
		return fmt.Errorf("stat %q: %w", absData, err)
	}

	hostJournal, err := journalHostPath(c.Journal, absData)
	if err != nil {
		return err
	}
	if _, err := os.Stat(hostJournal); err != nil {
		return fmt.Errorf("journal %q (host: %q): %w", c.Journal, hostJournal, err)
	}

	cli := docker.New()
	opts := docker.RunOpts{
		Image:       c.Image,
		Remove:      true,
		Detach:      c.Detached,
		Interactive: !c.Detached,
		TTY:         !c.Detached,
		Env: map[string]string{
			"HLEDGER_JOURNAL": c.Journal,
		},
		Volumes: []docker.VolumeMount{
			{Host: absData, Container: containerDataDir},
		},
		Ports: []docker.PortMap{
			{Host: c.Port, Container: 8081},
		},
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	return cli.Run(ctx, opts)
}

func journalHostPath(containerPath, hostDataDir string) (string, error) {
	prefix := containerDataDir + "/"
	if !strings.HasPrefix(containerPath, prefix) {
		return "", fmt.Errorf("journal %q must live under %s", containerPath, containerDataDir)
	}
	rel := strings.TrimPrefix(containerPath, prefix)
	return filepath.Join(hostDataDir, filepath.FromSlash(rel)), nil
}

type ImportCmd struct {
	CSV     string `arg:"" help:"Path to the CSV statement to import."`
	Source  string `name:"source" short:"s" required:"" enum:"caixa,n26" help:"Rules source name."`
	Year    int    `name:"year" short:"y" help:"Target journal year (defaults to current)."`
	Journal string `name:"journal" help:"Target journal file (defaults to data/<year>.hledger.journal)."`
	DryRun  bool   `name:"dry-run" help:"Show what would be imported without modifying the journal."`
}

func (c *ImportCmd) Run() error {
	fmt.Printf("[running] import: csv=%s source=%s year=%d journal=%q dryRun=%v\n", c.CSV, c.Source, c.Year, c.Journal, c.DryRun)
	return nil
}

var cli struct {
	RunWeb RunWebCmd `cmd:"" name:"run-web" help:"Start the hledger-app in docker."`
	Import ImportCmd `cmd:"" help:"Import a CSV into the journal."`
}
