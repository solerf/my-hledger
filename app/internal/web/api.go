package web

import (
	"encoding/json"
	"net/http"

	"github.com/solerf/my-hledger/app/internal/dto"
)

func (s *Server) apiMonthly(w http.ResponseWriter, r *http.Request) {
	month := r.URL.Query().Get("month")
	s.log.Info("GET /api/expenses/monthly", "month", month)

	expenses, err := s.svc.Monthly(r.Context(), month)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, expenses)
}

func (s *Server) apiHealth(w http.ResponseWriter, r *http.Request) {
	s.log.Info("GET /api/health")
	if s.svc.HledgerReachable(r.Context()) {
		w.WriteHeader(http.StatusOK)
		return
	}
	w.WriteHeader(http.StatusServiceUnavailable)
}

func (s *Server) apiAccounts(w http.ResponseWriter, r *http.Request) {
	s.log.Info("GET /api/accounts")
	accounts, err := s.svc.Accounts(r.Context())
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if accounts == nil {
		accounts = []string{}
	}
	writeJSON(w, http.StatusOK, accounts)
}

func (s *Server) apiAddTransactions(w http.ResponseWriter, r *http.Request) {
	var txns []dto.NewTransaction
	if err := json.NewDecoder(r.Body).Decode(&txns); err != nil {
		http.Error(w, "invalid body: "+err.Error(), http.StatusBadRequest)
		return
	}
	s.log.Info("POST /api/transactions", "count", len(txns))

	if err := s.svc.Add(r.Context(), txns); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.WriteHeader(http.StatusCreated)
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}
