package web

import (
	"net/http"
	"slices"

	"github.com/solerf/my-hledger/app/internal/dto"
)

type navItem struct {
	Label  string
	Href   string
	Active bool
}

func navFor(current string) []navItem {
	items := []navItem{
		{Label: "Monthly", Href: "/monthly"},
		{Label: "Year To Now", Href: "/year-to-now"},
		{Label: "Manual Entry", Href: "/manual-entry"},
	}
	for i := range items {
		items[i].Active = items[i].Href == current
	}
	return items
}

type panelData struct {
	Title   string
	Message string
}

type errorPage struct {
	Nav   []navItem
	Panel panelData
}

// charts is the payload handed to js/charts.js via window.__CHARTS__.
type charts struct {
	Pies  []PieData  `json:"pies"`
	Lines []LineData `json:"lines"`
}

type monthlyPage struct {
	Nav           []navItem
	Months        []string
	SelectedMonth string
	Table         []DateGroup
	Charts        charts
}

type yearToNowPage struct {
	Nav    []navItem
	Charts charts
}

type manualEntryPage struct {
	Nav      []navItem
	Accounts []string
}

func (s *Server) renderPage(w http.ResponseWriter, page string, data any) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	if err := s.pages[page].ExecuteTemplate(w, "layout", data); err != nil {
		s.log.Error("rendering page", "page", page, "err", err)
	}
}

func (s *Server) renderPanel(w http.ResponseWriter, current, title, message string) {
	s.renderPage(w, "error", errorPage{
		Nav:   navFor(current),
		Panel: panelData{Title: title, Message: message},
	})
}

// hledgerGate renders the unreachable panel when hledger-web is down.
// Returns false when the page must not render its normal content.
func (s *Server) hledgerGate(w http.ResponseWriter, r *http.Request, current string) bool {
	if s.svc.HledgerReachable(r.Context()) {
		return true
	}
	s.renderPanel(w, current,
		"hledger-web unavailable",
		"Could not reach hledger-web. Make sure it is running, then reload the page.")
	return false
}

func (s *Server) monthlyPage(w http.ResponseWriter, r *http.Request) {
	if !s.hledgerGate(w, r, "/monthly") {
		return
	}

	all, err := s.svc.Monthly(r.Context(), "")
	if err != nil {
		s.renderPanel(w, "/monthly", "Journal not found", err.Error())
		return
	}

	var months []string
	for _, me := range all {
		if !slices.Contains(months, me.YearMonth) {
			months = append(months, me.YearMonth)
		}
	}
	slices.Sort(months)

	selected := r.URL.Query().Get("month")
	if selected == "" && len(months) > 0 {
		selected = months[len(months)-1]
	}

	var filtered []dto.MonthlyExpense
	for _, me := range all {
		if selected == "" || me.YearMonth == selected {
			filtered = append(filtered, me)
		}
	}

	p := partitionMonthlyEntries(filtered)
	s.renderPage(w, "monthly", monthlyPage{
		Nav:           navFor("/monthly"),
		Months:        months,
		SelectedMonth: selected,
		Table:         entriesTable(p),
		Charts: charts{
			Pies: []PieData{
				pieData("chart-pie-expenses", "doughnut", p.Expenses),
				pieData("chart-pie-liabilities", "pie", p.Liabilities),
			},
			Lines: []LineData{
				accountsLineData("chart-accounts-line", p.Expenses),
				cumulativeData("chart-line", p.Expenses, p.Revenues),
			},
		},
	})
}

func (s *Server) yearToNowPage(w http.ResponseWriter, r *http.Request) {
	if !s.hledgerGate(w, r, "/year-to-now") {
		return
	}

	all, err := s.svc.Monthly(r.Context(), "")
	if err != nil {
		s.renderPanel(w, "/year-to-now", "Journal not found", err.Error())
		return
	}

	s.renderPage(w, "yeartonow", yearToNowPage{
		Nav: navFor("/year-to-now"),
		Charts: charts{
			Pies:  []PieData{},
			Lines: []LineData{yearCumulativeData("chart-year-cumulative", all)},
		},
	})
}

func (s *Server) manualEntryPage(w http.ResponseWriter, r *http.Request) {
	if !s.hledgerGate(w, r, "/manual-entry") {
		return
	}

	// Account names power the autocomplete datalist — best-effort only.
	accounts, err := s.svc.Accounts(r.Context())
	if err != nil {
		s.log.Warn("accounts for datalist", "err", err)
		accounts = nil
	}

	s.renderPage(w, "manual", manualEntryPage{
		Nav:      navFor("/manual-entry"),
		Accounts: accounts,
	})
}
