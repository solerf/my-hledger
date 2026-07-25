// Package service exposes the journal as expense DTOs and forwards drafted
// transactions to hledger-web — the Go port of the old ExpensesService.
package service

import (
	"context"
	"fmt"
	"log/slog"
	"slices"
	"strings"

	"github.com/solerf/my-hledger/app/internal/dto"
	"github.com/solerf/my-hledger/app/internal/hledger"
)

type Expenses struct {
	api *hledger.Client
	log *slog.Logger
}

func NewExpenses(api *hledger.Client, log *slog.Logger) *Expenses {
	return &Expenses{api: api, log: log}
}

// Monthly maps journal transactions to MonthlyExpense rows, optionally
// filtered to a single YYYY-MM month ("" keeps every month).
func (s *Expenses) Monthly(ctx context.Context, month string) ([]dto.MonthlyExpense, error) {
	s.log.Info("monthly", "month", orDash(month))

	txs, err := s.api.Transactions(ctx)
	if err != nil {
		return nil, err
	}

	expenses := make([]dto.MonthlyExpense, 0, len(txs))
	for _, t := range txs {
		if month != "" && t.YearMonth() != month {
			continue
		}
		entries := make([]dto.ExpenseEntry, 0, len(t.Postings))
		for _, p := range t.Postings {
			if len(p.Amounts) == 0 {
				continue
			}
			comment := strings.TrimSpace(p.Comment)
			if comment == "" {
				comment = strings.TrimSpace(t.Comment)
			}
			entries = append(entries, dto.ExpenseEntry{
				Date:     t.Date,
				Account:  p.Account,
				Amount:   p.Amounts[0].Quantity.FloatingPoint,
				Currency: p.Amounts[0].Commodity,
				Comment:  comment,
			})
		}
		expenses = append(expenses, dto.MonthlyExpense{
			YearMonth:   t.YearMonth(),
			Comment:     strings.TrimSpace(t.Comment),
			Description: strings.TrimSpace(t.Description),
			Entries:     entries,
		})
	}
	return expenses, nil
}

func (s *Expenses) Accounts(ctx context.Context) ([]string, error) {
	s.log.Info("accounts: listing accountnames")
	names, err := s.api.AccountNames(ctx)
	if err != nil {
		return nil, err
	}
	slices.Sort(names)
	return slices.Compact(names), nil
}

// Add groups drafted entries into one hledger transaction per date (see
// hledger.FromNewTransactions), so the journal gets one multi-posting entry
// per day rather than one transaction per drafted row.
func (s *Expenses) Add(ctx context.Context, transactions []dto.NewTransaction) error {
	grouped, err := hledger.FromNewTransactions(transactions)
	if err != nil {
		return fmt.Errorf("building add payload: %w", err)
	}
	s.log.Info("add: posting transactions", "transactions", len(grouped), "entries", len(transactions))
	for _, tx := range grouped {
		if err := s.api.AddTransaction(ctx, tx); err != nil {
			return err
		}
	}
	return nil
}

func (s *Expenses) HledgerReachable(ctx context.Context) bool {
	ok := s.api.Reachable(ctx)
	s.log.Info("hledgerReachable", "ok", ok)
	return ok
}

func orDash(s string) string {
	if s == "" {
		return "-"
	}
	return s
}
