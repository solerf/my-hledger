package main

import (
	"bufio"
	"bytes"
	"context"
	"crypto/sha256"
	"fmt"
	"io"
	"os"
	"os/signal"
	"path"
	"path/filepath"
	"regexp"
	"strings"
	"syscall"

	"github.com/solerf/my-hledger/cli/internal/docker"
)

const containerDataDir = "/opt/hledger_data"

type RunWebCmd struct {
	Journal  string `name:"journal" short:"j" required:"true" help:"Journal file name (resolved under --data on the host and /opt/hledger_data in the container)."`
	DataDir  string `name:"data" short:"d" default:"data" help:"Host directory mounted again container /opt/hledger_data."`
	Image    string `name:"image" default:"hledger-app" help:"Docker image tag to run."`
	Port     int    `name:"port" short:"p" default:"8081" help:"Host port to publish for the viewer."`
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

	if c.Journal == "" || strings.ContainsRune(c.Journal, '/') || strings.ContainsRune(c.Journal, filepath.Separator) {
		return fmt.Errorf("journal %q must be a bare file name (no path separators); it is resolved under --data", c.Journal)
	}
	hostJournal := filepath.Join(absData, c.Journal)
	if _, err := os.Stat(hostJournal); err != nil {
		return fmt.Errorf("journal %q (host: %q): %w", c.Journal, hostJournal, err)
	}
	ctrJournal := path.Join(containerDataDir, c.Journal)

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	cli := docker.New()
	exists, err := cli.ImageExists(ctx, c.Image)
	if err != nil {
		return fmt.Errorf("checking image %q: %w", c.Image, err)
	}
	if !exists {
		return fmt.Errorf("image %q not found locally; build it first (e.g. `docker build -f Dockerfile-hledgerapp -t %s .`)", c.Image, c.Image)
	}

	opts := docker.RunOpts{
		Image:       c.Image,
		User:        docker.CurrentUser(),
		Remove:      true,
		Detach:      c.Detached,
		Interactive: !c.Detached,
		TTY:         !c.Detached,
		Env: map[string]string{
			"HLEDGER_JOURNAL": ctrJournal,
		},
		Volumes: []docker.VolumeMount{
			{Host: absData, Container: containerDataDir},
		},
		Ports: []docker.PortMap{
			{Host: c.Port, Container: 8081},
		},
	}

	return cli.Run(ctx, opts)
}

type ImportCmd struct {
	CSV      string `arg:"" help:"Host path to the CSV statement to import (must live under --data)."`
	Journal  string `name:"journal" short:"j" required:"" help:"Host path to the target journal (must live under --data)."`
	DataDir  string `name:"data" short:"d" default:"data" help:"Host directory mounted at container /opt/hledger_data."`
	Image    string `name:"image" default:"hledger" help:"Docker image tag providing the hledger CLI."`
	NoBackup bool   `name:"no-backup" help:"Skip writing the .BKP copy of the journal before import."`
	DryRun   bool   `name:"dry-run" help:"Print the docker command and skip mutations (no backup, no import, no truncate)."`
}

func (c *ImportCmd) Run() error {
	absData, err := filepath.Abs(c.DataDir)
	if err != nil {
		return fmt.Errorf("resolving data dir: %w", err)
	}
	if _, err := os.Stat(absData); err != nil {
		return fmt.Errorf("stat %q: %w", absData, err)
	}

	absCSV, err := absUnder(c.CSV, absData, "csv")
	if err != nil {
		return err
	}
	absJournal, err := absUnder(c.Journal, absData, "journal")
	if err != nil {
		return err
	}

	rulesPath := absCSV + ".rules"
	if _, err := os.Stat(rulesPath); err != nil {
		return fmt.Errorf("rules file %q: %w", rulesPath, err)
	}

	absTranslated := absCSV + ".translated.csv"
	if err := translateCSV(absCSV, absTranslated); err != nil {
		return fmt.Errorf("translate csv: %w", err)
	}

	ctrTranslated, err := containerPath(absTranslated, absData)
	if err != nil {
		return err
	}
	ctrRules, err := containerPath(rulesPath, absData)
	if err != nil {
		return err
	}
	ctrJournal, err := containerPath(absJournal, absData)
	if err != nil {
		return err
	}

	cli := docker.New()

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	exists, err := cli.ImageExists(ctx, c.Image)
	if err != nil {
		return fmt.Errorf("checking image %q: %w", c.Image, err)
	}
	if !exists {
		return fmt.Errorf("image %q not found locally; build it first (e.g. `docker build -f Dockerfile-hledger -t %s .`)", c.Image, c.Image)
	}

	opts := docker.RunOpts{
		Image:  c.Image,
		User:   docker.CurrentUser(),
		Remove: true,
		Env: map[string]string{
			"LEDGER_FILE": ctrJournal,
		},
		Volumes: []docker.VolumeMount{
			{Host: absData, Container: containerDataDir},
		},
		Args: []string{"import", "--verbose-tags", "--rules-file", ctrRules, ctrTranslated},
	}

	if c.DryRun {
		fmt.Printf("[dry-run] translated csv written to %s\n", absTranslated)
		fmt.Printf("[dry-run] would back up %s -> %s.BKP\n", absJournal, absJournal)
		fmt.Printf("[dry-run] would run: docker run --rm -e LEDGER_FILE=%s -v %s:%s %s %s\n",
			ctrJournal, absData, containerDataDir, c.Image, strings.Join(opts.Args, " "))
		fmt.Printf("[dry-run] would truncate %s to its header on success\n", absCSV)
		return nil
	}

	if !c.NoBackup {
		if err := copyFile(absJournal, absJournal+".BKP"); err != nil {
			return fmt.Errorf("backup journal: %w", err)
		}
	}

	preHash, err := fileHash(absJournal)
	if err != nil {
		return fmt.Errorf("hash journal pre-import: %w", err)
	}

	if err := cli.Run(ctx, opts); err != nil {
		return fmt.Errorf("docker import failed (translated csv kept at %s, journal backup at %s.BKP): %w", absTranslated, absJournal, err)
	}

	postHash, err := fileHash(absJournal)
	if err != nil {
		return fmt.Errorf("hash journal post-import: %w", err)
	}
	if bytes.Equal(preHash, postHash) {
		fmt.Fprintf(os.Stderr, "no new transactions imported; keeping %s and translated copy at %s\n", absCSV, absTranslated)
		return nil
	}

	_ = os.Remove(absTranslated)

	if err := truncateCSVKeepHeader(absCSV); err != nil {
		return fmt.Errorf("truncate csv: %w", err)
	}
	return nil
}

func fileHash(path string) ([]byte, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	h := sha256.New()
	if _, err := io.Copy(h, f); err != nil {
		return nil, err
	}
	return h.Sum(nil), nil
}

func absUnder(p, dataDir, label string) (string, error) {
	abs, err := filepath.Abs(p)
	if err != nil {
		return "", fmt.Errorf("resolving %s path %q: %w", label, p, err)
	}
	if _, err := os.Stat(abs); err != nil {
		return "", fmt.Errorf("%s %q: %w", label, abs, err)
	}
	rel, err := filepath.Rel(dataDir, abs)
	if err != nil || strings.HasPrefix(rel, "..") || filepath.IsAbs(rel) {
		return "", fmt.Errorf("%s %q must live under %s", label, abs, dataDir)
	}
	return abs, nil
}

func containerPath(hostPath, hostDataDir string) (string, error) {
	rel, err := filepath.Rel(hostDataDir, hostPath)
	if err != nil {
		return "", fmt.Errorf("relpath %q under %q: %w", hostPath, hostDataDir, err)
	}
	return path.Join(containerDataDir, filepath.ToSlash(rel)), nil
}

func copyFile(src, dst string) error {
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()
	out, err := os.Create(dst)
	if err != nil {
		return err
	}
	if _, err := io.Copy(out, in); err != nil {
		out.Close()
		return err
	}
	return out.Close()
}

func truncateCSVKeepHeader(path string) error {
	f, err := os.Open(path)
	if err != nil {
		return err
	}
	scanner := bufio.NewScanner(f)
	scanner.Buffer(make([]byte, 64*1024), 1024*1024)
	var header string
	if scanner.Scan() {
		header = scanner.Text()
	}
	f.Close()
	if err := scanner.Err(); err != nil {
		return err
	}
	return os.WriteFile(path, []byte(header+"\n"), 0o644)
}

type LastEntryCmd struct {
	Account string `arg:"" help:"Full account name (exact match, e.g. assets:n26:main)."`
	Journal string `name:"journal" short:"j" required:"" help:"Host path to the journal file."`
}

var (
	journalDateRe   = regexp.MustCompile(`^(\d{4}[-./]\d{2}[-./]\d{2})`)
	accountSplitter = regexp.MustCompile(`\s{2,}|\t`)
)

func (c *LastEntryCmd) Run() error {
	absJournal, err := filepath.Abs(c.Journal)
	if err != nil {
		return fmt.Errorf("resolving journal path: %w", err)
	}
	f, err := os.Open(absJournal)
	if err != nil {
		return fmt.Errorf("open journal %q: %w", absJournal, err)
	}
	defer f.Close()

	scanner := bufio.NewScanner(f)
	scanner.Buffer(make([]byte, 64*1024), 1024*1024)
	var lines []string
	for scanner.Scan() {
		lines = append(lines, scanner.Text())
	}
	if err := scanner.Err(); err != nil {
		return fmt.Errorf("read journal: %w", err)
	}

	date := findLastEntryDate(lines, c.Account)
	if date == "" {
		fmt.Fprintf(os.Stderr, "no entries found for account %q\n", c.Account)
		return nil
	}
	fmt.Println(date)
	return nil
}

func findLastEntryDate(lines []string, account string) string {
	matched := false
	for i := len(lines) - 1; i >= 0; i-- {
		line := lines[i]
		if m := journalDateRe.FindStringSubmatch(line); m != nil {
			if matched {
				return strings.ReplaceAll(strings.ReplaceAll(m[1], "/", "-"), ".", "-")
			}
			matched = false
			continue
		}
		if line == "" || (line[0] != ' ' && line[0] != '\t') {
			continue
		}
		body := line
		if idx := strings.Index(body, ";"); idx >= 0 {
			body = body[:idx]
		}
		body = strings.TrimSpace(body)
		if body == "" {
			continue
		}
		acct := accountSplitter.Split(body, 2)[0]
		if acct == account {
			matched = true
		}
	}
	return ""
}

var cli struct {
	RunWeb    RunWebCmd    `cmd:"" name:"run-web" help:"Start the hledger-app in docker."`
	Import    ImportCmd    `cmd:"" help:"Import a CSV into the journal."`
	LastEntry LastEntryCmd `cmd:"" name:"last-entry" help:"Print the most recent date in the journal for a given account."`
}
