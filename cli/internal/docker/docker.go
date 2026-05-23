package docker

import (
	"context"
	"fmt"
	"maps"
	"os"
	"os/exec"
	"slices"
	"sort"
)

type Client struct {
	Bin string
}

func New() *Client {
	return &Client{Bin: "docker"}
}

type VolumeMount struct {
	Host      string
	Container string
}

func (v VolumeMount) spec() string {
	return v.Host + ":" + v.Container
}

type PortMap struct {
	Host      int
	Container int
	Proto     string
}

func (p PortMap) spec() string {
	proto := p.Proto
	if proto == "" {
		proto = "tcp"
	}
	return fmt.Sprintf("%d:%d/%s", p.Host, p.Container, proto)
}

type RunOpts struct {
	Image       string
	Name        string
	User        string // --user (e.g. "1000:1000"); empty = container default.
	Detach      bool
	Remove      bool // --rm
	Interactive bool // -i
	TTY         bool // -t
	Env         map[string]string
	Volumes     []VolumeMount
	Ports       []PortMap
	Args        []string // appended after the image name and passed as the container command.
}

// CurrentUser returns the host's uid:gid as a string suitable for --user.
func CurrentUser() string {
	return fmt.Sprintf("%d:%d", os.Getuid(), os.Getgid())
}

func (c *Client) Run(ctx context.Context, opts RunOpts) error {
	if opts.Image == "" {
		return fmt.Errorf("docker run: image is required")
	}
	args := []string{"run"}
	if opts.Remove {
		args = append(args, "--rm")
	}
	if opts.Detach {
		args = append(args, "-d")
	}
	if opts.Interactive {
		args = append(args, "-i")
	}
	if opts.TTY {
		args = append(args, "-t")
	}
	if opts.Name != "" {
		args = append(args, "--name", opts.Name)
	}
	if opts.User != "" {
		args = append(args, "--user", opts.User)
	}
	// Sort env keys so the command line is deterministic
	for _, k := range sortedKeys(opts.Env) {
		args = append(args, "-e", k+"="+opts.Env[k])
	}
	for _, v := range opts.Volumes {
		args = append(args, "-v", v.spec())
	}
	for _, p := range opts.Ports {
		args = append(args, "-p", p.spec())
	}
	args = append(args, opts.Image)
	args = append(args, opts.Args...)
	return c.runCmd(ctx, args)
}

func (c *Client) ImageExists(ctx context.Context, image string) (bool, error) {
	cmd := exec.CommandContext(ctx, c.Bin, "image", "inspect", image)
	cmd.Stdout = nil
	cmd.Stderr = nil
	err := cmd.Run()
	if err == nil {
		return true, nil
	}
	if _, ok := err.(*exec.ExitError); ok {
		return false, nil
	}
	return false, err
}

func sortedKeys(kenv map[string]string) []string {
	if len(kenv) == 0 {
		return nil
	}
	keys := slices.Collect(maps.Keys(kenv))
	sort.Strings(keys)
	return keys
}

func (c *Client) runCmd(ctx context.Context, args []string) error {
	cmd := exec.CommandContext(ctx, c.Bin, args...)
	// streaming stdio
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	cmd.Stdin = os.Stdin
	return cmd.Run()
}
