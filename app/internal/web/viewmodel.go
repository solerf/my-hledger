package web

import (
	"math"
	"slices"
	"strconv"
	"strings"

	"github.com/solerf/my-hledger/app/internal/dto"
)

// Row is one journal posting flattened for rendering — the Go port of the old
// frontend's Row. Comment/Description come from the transaction, TxComment
// from the posting.
type Row struct {
	YearMonth   string
	Account     string
	Comment     string
	Description string
	TxComment   string
	Date        string
	Amount      float64
}

// Partition splits rows by top-level account: expenses, liabilities and
// revenues. Income postings are stored as negatives in hledger (credits) —
// revenues are inverted so they plot as positive.
type Partition struct {
	Expenses    []Row
	Liabilities []Row
	Revenues    []Row
}

func partitionMonthlyEntries(monthly []dto.MonthlyExpense) Partition {
	var p Partition
	for _, me := range monthly {
		for _, e := range me.Entries {
			if !strings.HasPrefix(e.Account, "expense") &&
				!strings.HasPrefix(e.Account, "revenues") &&
				!strings.HasPrefix(e.Account, "liabilities") {
				continue
			}
			row := Row{
				YearMonth:   me.YearMonth,
				Account:     e.Account,
				Comment:     me.Comment,
				Description: me.Description,
				TxComment:   e.Comment,
				Date:        e.Date,
				Amount:      e.Amount,
			}
			switch accountHead(e.Account) {
			case "expenses":
				p.Expenses = append(p.Expenses, row)
			case "liabilities":
				p.Liabilities = append(p.Liabilities, row)
			case "revenues":
				row.Amount = -row.Amount
				p.Revenues = append(p.Revenues, row)
			}
		}
	}
	return p
}

func accountHead(account string) string {
	head, _, _ := strings.Cut(account, ":")
	return head
}

func accountTail(account string) string {
	_, tail, _ := strings.Cut(account, ":")
	return tail
}

// ── Chart payloads (serialized into the page for Chart.js) ──────────

type PieData struct {
	CanvasID string    `json:"canvasId"`
	Type     string    `json:"type"`
	Labels   []string  `json:"labels"`
	Values   []float64 `json:"values"`
}

type Dataset struct {
	Label string    `json:"label"`
	Data  []float64 `json:"data"`
	Color string    `json:"color,omitempty"`
}

type LineData struct {
	CanvasID string    `json:"canvasId"`
	Labels   []string  `json:"labels"`
	Datasets []Dataset `json:"datasets"`
}

// pieData sums rows per account, largest first.
func pieData(canvasID, chartType string, rows []Row) PieData {
	labels, values := sumBy(rows, func(r Row) string { return r.Account })
	// sortBy(-total)
	idx := make([]int, len(labels))
	for i := range idx {
		idx[i] = i
	}
	slices.SortStableFunc(idx, func(a, b int) int {
		switch {
		case values[a] > values[b]:
			return -1
		case values[a] < values[b]:
			return 1
		}
		return 0
	})
	sl := make([]string, len(labels))
	sv := make([]float64, len(values))
	for i, j := range idx {
		sl[i], sv[i] = labels[j], values[j]
	}
	return PieData{CanvasID: canvasID, Type: chartType, Labels: sl, Values: sv}
}

// accountsLineData plots each account's per-date totals on a shared,
// sorted date axis.
func accountsLineData(canvasID string, rows []Row) LineData {
	dates := sortedDistinct(rows, func(r Row) string { return r.Date })
	accounts := sortedDistinct(rows, func(r Row) string { return r.Account })

	type key struct{ account, date string }
	totals := map[key]float64{}
	for _, r := range rows {
		totals[key{r.Account, r.Date}] += r.Amount
	}

	datasets := make([]Dataset, 0, len(accounts))
	for _, acct := range accounts {
		series := make([]float64, len(dates))
		for i, d := range dates {
			series[i] = totals[key{acct, d}]
		}
		datasets = append(datasets, Dataset{Label: acct, Data: series})
	}
	return LineData{CanvasID: canvasID, Labels: dates, Datasets: datasets}
}

// cumulativeData plots running totals of daily spend vs earnings on a unified
// date axis.
func cumulativeData(canvasID string, spend, earnings []Row) LineData {
	spendByDate := dailyTotals(spend)
	earningsByDate := dailyTotals(earnings)

	labelSet := map[string]bool{}
	var labels []string
	for d := range spendByDate {
		if !labelSet[d] {
			labelSet[d] = true
			labels = append(labels, d)
		}
	}
	for d := range earningsByDate {
		if !labelSet[d] {
			labelSet[d] = true
			labels = append(labels, d)
		}
	}
	slices.Sort(labels)

	cumulative := func(byDate map[string]float64) []float64 {
		out := make([]float64, len(labels))
		acc := 0.0
		for i, d := range labels {
			acc += byDate[d]
			out[i] = acc
		}
		return out
	}

	return LineData{
		CanvasID: canvasID,
		Labels:   labels,
		Datasets: []Dataset{
			{Label: "Spend", Data: cumulative(spendByDate), Color: "#c10b0b"},
			{Label: "Earnings", Data: cumulative(earningsByDate), Color: "#1f7a1f"},
		},
	}
}

func dailyTotals(rows []Row) map[string]float64 {
	totals := map[string]float64{}
	for _, r := range rows {
		totals[r.Date] += r.Amount
	}
	return totals
}

// yearCumulativeData draws one line per (main account type, currency) — the
// first account segment split by commodity — plotting the running cumulative
// total by month. Currencies are never summed together. Amounts keep hledger's
// sign convention, so credit-normal types (revenues, liabilities) trend
// negative.
func yearCumulativeData(canvasID string, rows []dto.MonthlyExpense) LineData {
	var months []string
	monthSet := map[string]bool{}
	for _, me := range rows {
		if !monthSet[me.YearMonth] {
			monthSet[me.YearMonth] = true
			months = append(months, me.YearMonth)
		}
	}
	slices.Sort(months)

	type key struct{ tpe, cur, month string }
	totals := map[key]float64{}
	type seriesKey struct{ tpe, cur string }
	seriesSet := map[seriesKey]bool{}
	var series []seriesKey
	for _, me := range rows {
		for _, e := range me.Entries {
			tpe := accountHead(e.Account)
			totals[key{tpe, e.Currency, me.YearMonth}] += e.Amount
			sk := seriesKey{tpe, e.Currency}
			if !seriesSet[sk] {
				seriesSet[sk] = true
				series = append(series, sk)
			}
		}
	}
	slices.SortFunc(series, func(a, b seriesKey) int {
		if c := strings.Compare(a.tpe, b.tpe); c != 0 {
			return c
		}
		return strings.Compare(a.cur, b.cur)
	})

	datasets := make([]Dataset, 0, len(series))
	for _, sk := range series {
		data := make([]float64, len(months))
		acc := 0.0
		for i, m := range months {
			acc += totals[key{sk.tpe, sk.cur, m}]
			data[i] = acc
		}
		datasets = append(datasets, Dataset{Label: sk.tpe + " (" + sk.cur + ")", Data: data})
	}
	return LineData{CanvasID: canvasID, Labels: months, Datasets: datasets}
}

// ── Entries table ────────────────────────────────────────────────────

type TableEntry struct {
	Label     string // sub-account plus optional "(description)"
	Amount    string
	IsExpense bool
}

type AccountGroup struct {
	Header    string // parent account plus optional "(description | comment)"
	Sum       string
	IsExpense bool
	Entries   []TableEntry
}

type DateGroup struct {
	Date   string
	Groups []AccountGroup
}

// entriesTable groups rows by date (newest first), then by parent account
// within each date: a header row with the account total followed by one row
// per posting.
func entriesTable(p Partition) []DateGroup {
	rows := slices.Concat(p.Expenses, p.Revenues, p.Liabilities)

	byDate := map[string][]Row{}
	for _, r := range rows {
		byDate[r.Date] = append(byDate[r.Date], r)
	}
	dates := make([]string, 0, len(byDate))
	for d := range byDate {
		dates = append(dates, d)
	}
	slices.SortFunc(dates, func(a, b string) int { return strings.Compare(b, a) })

	out := make([]DateGroup, 0, len(dates))
	for _, d := range dates {
		out = append(out, DateGroup{Date: d, Groups: accountGroups(byDate[d])})
	}
	return out
}

func accountGroups(rows []Row) []AccountGroup {
	byParent := map[string][]Row{}
	for _, r := range rows {
		parent := accountHead(r.Account)
		byParent[parent] = append(byParent[parent], r)
	}
	parents := make([]string, 0, len(byParent))
	for p := range byParent {
		parents = append(parents, p)
	}
	slices.Sort(parents)

	groups := make([]AccountGroup, 0, len(parents))
	for _, parent := range parents {
		group := byParent[parent]
		isExpense := parent == "expenses" || parent == "liabilities"

		sum := 0.0
		for _, r := range group {
			sum += r.Amount
		}

		header := parent + " " + parenthesized(joinNonEmpty(" | ", group[0].Description, group[0].Comment))

		entries := make([]TableEntry, 0, len(group))
		for _, r := range group {
			entries = append(entries, TableEntry{
				Label:     accountTail(r.Account) + " " + parenthesized(r.Description),
				Amount:    formatAmount(r.Amount),
				IsExpense: isExpense,
			})
		}
		groups = append(groups, AccountGroup{
			Header:    header,
			Sum:       formatAmount(sum),
			IsExpense: isExpense,
			Entries:   entries,
		})
	}
	return groups
}

func parenthesized(s string) string {
	if s == "" {
		return ""
	}
	return "(" + s + ")"
}

func joinNonEmpty(sep string, parts ...string) string {
	kept := parts[:0:0]
	for _, p := range parts {
		if p != "" {
			kept = append(kept, p)
		}
	}
	return strings.Join(kept, sep)
}

func formatAmount(v float64) string {
	return strconv.FormatFloat(math.Round(v*100)/100, 'f', 2, 64)
}

// ── Small helpers ────────────────────────────────────────────────────

func sortedDistinct(rows []Row, get func(Row) string) []string {
	seen := map[string]bool{}
	var out []string
	for _, r := range rows {
		if v := get(r); !seen[v] {
			seen[v] = true
			out = append(out, v)
		}
	}
	slices.Sort(out)
	return out
}

func sumBy(rows []Row, get func(Row) string) ([]string, []float64) {
	totals := map[string]float64{}
	var order []string
	for _, r := range rows {
		k := get(r)
		if _, ok := totals[k]; !ok {
			order = append(order, k)
		}
		totals[k] += r.Amount
	}
	values := make([]float64, len(order))
	for i, k := range order {
		values[i] = totals[k]
	}
	return order, values
}
