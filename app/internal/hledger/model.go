// Package hledger talks to a hledger-web instance started with --serve-api:
// reading the journal (GET /transactions, /accountnames) and appending to it
// (PUT /add).
package hledger

// Read-side mirror of hledger-web's Transaction JSON. Only the fields the app
// consumes are declared; the rest of the payload is ignored on decode.

type Quantity struct {
	DecimalMantissa int64   `json:"decimalMantissa"`
	DecimalPlaces   int     `json:"decimalPlaces"`
	FloatingPoint   float64 `json:"floatingPoint"`
}

type Amount struct {
	Commodity string   `json:"acommodity"`
	Quantity  Quantity `json:"aquantity"`
}

type Posting struct {
	Account string   `json:"paccount"`
	Amounts []Amount `json:"pamount"`
	Comment string   `json:"pcomment"`
}

type Transaction struct {
	Date        string    `json:"tdate"`
	Description string    `json:"tdescription"`
	Comment     string    `json:"tcomment"`
	Postings    []Posting `json:"tpostings"`
}

// YearMonth is the transaction date truncated to YYYY-MM.
func (t Transaction) YearMonth() string {
	if len(t.Date) < 7 {
		return t.Date
	}
	return t.Date[:7]
}
