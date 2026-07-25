package hledger

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
)

// Client is a thin wrapper over hledger-web's JSON API.
type Client struct {
	base *url.URL
	hc   *http.Client
}

func NewClient(base *url.URL, hc *http.Client) *Client {
	if hc == nil {
		hc = http.DefaultClient
	}
	return &Client{base: base, hc: hc}
}

func (c *Client) Transactions(ctx context.Context) ([]Transaction, error) {
	var txs []Transaction
	if err := c.getJSON(ctx, "transactions", &txs); err != nil {
		return nil, err
	}
	return txs, nil
}

func (c *Client) AccountNames(ctx context.Context) ([]string, error) {
	var names []string
	if err := c.getJSON(ctx, "accountnames", &names); err != nil {
		return nil, err
	}
	return names, nil
}

// AddTransaction PUTs one transaction to hledger-web's /add endpoint.
func (c *Client) AddTransaction(ctx context.Context, tx AddTransaction) error {
	body, err := json.Marshal(tx)
	if err != nil {
		return fmt.Errorf("encoding add payload: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPut, c.endpoint("add"), bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")

	res, err := c.hc.Do(req)
	if err != nil {
		return fmt.Errorf("hledger-web add: %w", err)
	}
	defer func() { _ = res.Body.Close() }()

	if res.StatusCode < 200 || res.StatusCode > 299 {
		msg, _ := io.ReadAll(io.LimitReader(res.Body, 4096))
		return fmt.Errorf("failed hledger-web add: %s: %s", res.Status, bytes.TrimSpace(msg))
	}
	return nil
}

// Reachable is true when hledger-web answers, false on any failure (down,
// refused, non-2xx, …).
func (c *Client) Reachable(ctx context.Context) bool {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.endpoint("accountnames"), nil)
	if err != nil {
		return false
	}
	res, err := c.hc.Do(req)
	if err != nil {
		return false
	}
	defer func() { _ = res.Body.Close() }()
	_, _ = io.Copy(io.Discard, res.Body)
	return res.StatusCode >= 200 && res.StatusCode <= 299
}

func (c *Client) endpoint(path string) string {
	return c.base.JoinPath(path).String()
}

func (c *Client) getJSON(ctx context.Context, path string, out any) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.endpoint(path), nil)
	if err != nil {
		return err
	}
	res, err := c.hc.Do(req)
	if err != nil {
		return fmt.Errorf("hledger-web %s: %w", path, err)
	}
	defer func() { _ = res.Body.Close() }()

	if res.StatusCode < 200 || res.StatusCode > 299 {
		msg, _ := io.ReadAll(io.LimitReader(res.Body, 4096))
		return fmt.Errorf("hledger-web %s: %s: %s", path, res.Status, bytes.TrimSpace(msg))
	}
	if err := json.NewDecoder(res.Body).Decode(out); err != nil {
		return fmt.Errorf("decoding hledger-web %s: %w", path, err)
	}
	return nil
}
