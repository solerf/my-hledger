package hledger

import (
	"encoding/json"
	"strings"
	"testing"

	"github.com/solerf/my-hledger/app/internal/dto"
)

func newTx(t *testing.T, date, from, to, amount, desc, comment string) dto.NewTransaction {
	t.Helper()
	amt, err := dto.ParseDecimal(amount)
	if err != nil {
		t.Fatalf("ParseDecimal(%q): %v", amount, err)
	}
	return dto.NewTransaction{
		Date: date, From: from, To: to, Amount: amt,
		Currency: "EUR", Description: desc, Comment: comment,
	}
}

func TestFromNewTransactionsGroupsByDate(t *testing.T) {
	txs, err := FromNewTransactions([]dto.NewTransaction{
		newTx(t, "2026-07-01", "assets:bank", "expenses:food", "42.50", "CAFE", ""),
		newTx(t, "2026-07-02", "assets:bank", "expenses:transport", "2.10", "METRO", "TRIP"),
		newTx(t, "2026-07-01", "assets:bank", "expenses:food", "10", "CAFE", ""),
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(txs) != 2 {
		t.Fatalf("got %d transactions, want 2", len(txs))
	}

	first := txs[0]
	if first.Date != "2026-07-01" || len(first.Postings) != 4 {
		t.Fatalf("first tx: date=%s postings=%d, want 2026-07-01 with 4", first.Date, len(first.Postings))
	}
	// Duplicate descriptions collapse.
	if first.Description != "CAFE" {
		t.Errorf("first description = %q, want CAFE", first.Description)
	}
	// The id tag keeps the entered scale and lives on the `to` posting.
	if got := first.Postings[0].Comment; got != "id:2026-07-01 42.50 CAFE" {
		t.Errorf("to-posting comment = %q", got)
	}
	if got := first.Postings[1].Comment; got != "" {
		t.Errorf("from-posting comment = %q, want empty", got)
	}

	second := txs[1]
	if got := second.Postings[0].Comment; got != "id:2026-07-02 2.10 METRO, TRIP" {
		t.Errorf("second to-posting comment = %q", got)
	}

	// to positive / from negative, mantissa scaled per stripTrailingZeros.
	q := first.Postings[0].Amounts[0].Quantity
	if q.DecimalMantissa != 425 || q.DecimalPlaces != 1 || q.FloatingPoint != 42.5 {
		t.Errorf("to quantity = %+v", q)
	}
	q = first.Postings[1].Amounts[0].Quantity
	if q.DecimalMantissa != -425 || q.DecimalPlaces != 1 || q.FloatingPoint != -42.5 {
		t.Errorf("from quantity = %+v", q)
	}
}

func TestAddTransactionJSONShape(t *testing.T) {
	txs, err := FromNewTransactions([]dto.NewTransaction{
		newTx(t, "2026-07-01", "assets:bank", "expenses:food", "42.50", "CAFE", ""),
	})
	if err != nil {
		t.Fatal(err)
	}
	raw, err := json.Marshal(txs[0])
	if err != nil {
		t.Fatal(err)
	}
	body := string(raw)

	// Optional slots must encode as null, list slots as [] (never null).
	for _, want := range []string{
		`"acost":null`,
		`"pbalanceassertion":null`,
		`"asdigitgroups":null`,
		`"ttags":[]`,
		`"ptags":[]`,
		`"tsourcepos":[{"sourceColumn":1,"sourceLine":1,"sourceName":""},{"sourceColumn":1,"sourceLine":1,"sourceName":""}]`,
		`"tstatus":"Unmarked"`,
		`"ptype":"RegularPosting"`,
	} {
		if !strings.Contains(body, want) {
			t.Errorf("payload missing %s:\n%s", want, body)
		}
	}
	if strings.Contains(body, "null,null") && strings.Contains(body, `"pamount":null`) {
		t.Errorf("unexpected null list in payload:\n%s", body)
	}
}
