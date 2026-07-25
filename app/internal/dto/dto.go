// Package dto holds the JSON shapes shared between the HTTP API and the
// hledger-web integration — the Go port of the old Scala `shared/dtos`.
package dto

type ExpenseEntry struct {
	Date     string  `json:"date"`
	Account  string  `json:"account"`
	Amount   float64 `json:"amount"`
	Currency string  `json:"currency"`
	Comment  string  `json:"comment"`
}

type MonthlyExpense struct {
	YearMonth   string         `json:"yearMonth"`
	Comment     string         `json:"comment"`
	Description string         `json:"description"`
	Entries     []ExpenseEntry `json:"entries"`
}

// NewTransaction is a transaction drafted in the manual-entry form. `from` is
// the account the money leaves (posted negative); `to` is the account that
// receives it (posted positive).
type NewTransaction struct {
	Date        string  `json:"date"`
	From        string  `json:"from"`
	To          string  `json:"to"`
	Amount      Decimal `json:"amount"`
	Currency    string  `json:"currency"`
	Description string  `json:"description"`
	Comment     string  `json:"comment"`
}
