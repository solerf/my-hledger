package hledger

import (
	"strings"

	"github.com/solerf/my-hledger/app/internal/dto"
)

// JSON-encodable mirror of hledger's `Transaction` type, shaped to match what
// hledger-web's `PUT /add` endpoint accepts (verified against hledger 1.33).
//
// Pointer fields encode to `null`, which the endpoint requires for the
// optional slots (`acost`, `pbalanceassertion`, `asdigitgroups`, …). Slice
// fields must be non-nil so they encode as `[]`, never `null`.

type AddQuantity struct {
	DecimalMantissa int64   `json:"decimalMantissa"`
	DecimalPlaces   int     `json:"decimalPlaces"`
	FloatingPoint   float64 `json:"floatingPoint"`
}

type AddAmountStyle struct {
	CommoditySide   string  `json:"ascommodityside"`
	CommoditySpaced bool    `json:"ascommodityspaced"`
	DecimalMark     string  `json:"asdecimalmark"`
	DigitGroups     *string `json:"asdigitgroups"`
	Precision       int     `json:"asprecision"`
	Rounding        string  `json:"asrounding"`
}

type AddAmount struct {
	Commodity string         `json:"acommodity"`
	Cost      *string        `json:"acost"`
	Quantity  AddQuantity    `json:"aquantity"`
	Style     AddAmountStyle `json:"astyle"`
}

type SourcePos struct {
	SourceColumn int    `json:"sourceColumn"`
	SourceLine   int    `json:"sourceLine"`
	SourceName   string `json:"sourceName"`
}

type AddPosting struct {
	Account          string      `json:"paccount"`
	Amounts          []AddAmount `json:"pamount"`
	BalanceAssertion *string     `json:"pbalanceassertion"`
	Comment          string      `json:"pcomment"`
	Date             *string     `json:"pdate"`
	Date2            *string     `json:"pdate2"`
	Original         *string     `json:"poriginal"`
	Status           string      `json:"pstatus"`
	Tags             []string    `json:"ptags"`
	Transaction      string      `json:"ptransaction_"`
	Type             string      `json:"ptype"`
}

type AddTransaction struct {
	Code             string       `json:"tcode"`
	Comment          string       `json:"tcomment"`
	Date             string       `json:"tdate"`
	Date2            *string      `json:"tdate2"`
	Description      string       `json:"tdescription"`
	Index            int          `json:"tindex"`
	Postings         []AddPosting `json:"tpostings"`
	PrecedingComment string       `json:"tprecedingcomment"`
	SourcePos        []SourcePos  `json:"tsourcepos"`
	Status           string       `json:"tstatus"`
	Tags             []string     `json:"ttags"`
}

// FromNewTransactions builds add payloads from drafted entries, grouped into
// one transaction per date (date order, then entry order preserved). Each
// entry contributes two postings — its `to` account positive, its `from`
// account negative — so the transaction balances, with postings kept in input
// order.
//
// The transaction description is the entries' descriptions joined (`cafe,
// metro`). Each entry's `id:` dedup tag lives on its `to` posting's comment,
// following the same `id:%date %amount %description` rule as the CLI import
// (see data/rules/translated.rules). Space-separated because hledger collapses
// adjacent `%a-%b` references (keeping only the last). Any free-text comment
// the user typed is appended after the tag, comma-separated so it doesn't
// bleed into the id value.
func FromNewTransactions(transactions []dto.NewTransaction) ([]AddTransaction, error) {
	var dates []string
	seen := map[string]bool{}
	for _, t := range transactions {
		if !seen[t.Date] {
			seen[t.Date] = true
			dates = append(dates, t.Date)
		}
	}

	txs := make([]AddTransaction, 0, len(dates))
	for _, date := range dates {
		var entries []dto.NewTransaction
		for _, t := range transactions {
			if t.Date == date {
				entries = append(entries, t)
			}
		}
		tx, err := groupedTransaction(date, entries)
		if err != nil {
			return nil, err
		}
		txs = append(txs, tx)
	}
	return txs, nil
}

func groupedTransaction(date string, entries []dto.NewTransaction) (AddTransaction, error) {
	var descs []string
	seenDesc := map[string]bool{}
	for _, e := range entries {
		d := strings.TrimSpace(e.Description)
		if d != "" && !seenDesc[d] {
			seenDesc[d] = true
			descs = append(descs, d)
		}
	}

	postings := make([]AddPosting, 0, 2*len(entries))
	for _, e := range entries {
		idTag := "id:" + e.Date + " " + e.Amount.String() + " " + strings.TrimSpace(e.Description)
		toComment := idTag
		if userComment := strings.TrimSpace(e.Comment); userComment != "" {
			toComment = idTag + ", " + userComment
		}

		to, err := posting(strings.TrimSpace(e.To), e.Amount, false, e.Currency, toComment)
		if err != nil {
			return AddTransaction{}, err
		}
		from, err := posting(strings.TrimSpace(e.From), e.Amount, true, e.Currency, "")
		if err != nil {
			return AddTransaction{}, err
		}
		postings = append(postings, to, from)
	}

	return AddTransaction{
		Code:             "",
		Comment:          "",
		Date:             date,
		Date2:            nil,
		Description:      strings.Join(descs, ", "),
		Index:            0,
		Postings:         postings,
		PrecedingComment: "",
		// The transaction's source span (start, end) — two entries regardless
		// of posting count, not one per posting.
		SourcePos: []SourcePos{{1, 1, ""}, {1, 1, ""}},
		Status:    "Unmarked",
		Tags:      []string{},
	}, nil
}

func posting(account string, amt dto.Decimal, negate bool, currency, comment string) (AddPosting, error) {
	amount, err := addAmount(amt, negate, currency)
	if err != nil {
		return AddPosting{}, err
	}
	return AddPosting{
		Account:          account,
		Amounts:          []AddAmount{amount},
		BalanceAssertion: nil,
		Comment:          comment,
		Date:             nil,
		Date2:            nil,
		Original:         nil,
		Status:           "Unmarked",
		Tags:             []string{},
		Transaction:      "",
		Type:             "RegularPosting",
	}, nil
}

func addAmount(amt dto.Decimal, negate bool, currency string) (AddAmount, error) {
	mantissa, places, err := amt.MantissaPlaces()
	if err != nil {
		return AddAmount{}, err
	}
	floating := amt.Float64()
	if negate {
		mantissa = -mantissa
		floating = -floating
	}
	return AddAmount{
		Commodity: currency,
		Cost:      nil,
		Quantity:  AddQuantity{DecimalMantissa: mantissa, DecimalPlaces: places, FloatingPoint: floating},
		Style: AddAmountStyle{
			// 3-letter codes render after the amount with a space, e.g. "42.50 EUR".
			CommoditySide:   "R",
			CommoditySpaced: true,
			DecimalMark:     ".",
			DigitGroups:     nil,
			Precision:       places,
			Rounding:        "NoRounding",
		},
	}, nil
}
