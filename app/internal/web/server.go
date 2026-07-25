// Package web serves the app: server-rendered views on top of html/template,
// the JSON API the manual-entry form talks to, and the static frontend assets.
package web

import (
	"embed"
	"html/template"
	"log/slog"
	"net/http"
	"runtime/debug"
	"time"

	"github.com/solerf/my-hledger/app/internal/service"
)

//go:embed templates/*.html
var templatesFS embed.FS

type Server struct {
	svc       *service.Expenses
	log       *slog.Logger
	assetsDir string
	pages     map[string]*template.Template
}

func New(svc *service.Expenses, assetsDir string, log *slog.Logger) *Server {
	pages := map[string]*template.Template{}
	for _, page := range []string{"monthly", "yeartonow", "manual", "error"} {
		pages[page] = template.Must(template.ParseFS(
			templatesFS,
			"templates/layout.html",
			"templates/panel.html",
			"templates/"+page+".html",
		))
	}
	return &Server{svc: svc, log: log, assetsDir: assetsDir, pages: pages}
}

func (s *Server) Router() http.Handler {
	mux := http.NewServeMux()

	mux.HandleFunc("GET /{$}", func(w http.ResponseWriter, req *http.Request) {
		http.Redirect(w, req, "/monthly", http.StatusFound)
	})
	mux.HandleFunc("GET /monthly", s.monthlyPage)
	mux.HandleFunc("GET /year-to-now", s.yearToNowPage)
	mux.HandleFunc("GET /manual-entry", s.manualEntryPage)

	mux.HandleFunc("GET /api/expenses/monthly", s.apiMonthly)
	mux.HandleFunc("GET /api/health", s.apiHealth)
	mux.HandleFunc("GET /api/accounts", s.apiAccounts)
	mux.HandleFunc("POST /api/transactions", s.apiAddTransactions)

	// Static frontend assets (styles, vendor bundles, page scripts).
	static := http.FileServer(http.Dir(s.assetsDir))
	for _, path := range []string{"GET /vendor/", "GET /css/", "GET /js/", "GET /app.css"} {
		mux.Handle(path, static)
	}

	return s.logRequests(s.recoverPanics(mux))
}

// statusRecorder captures the response status for the access log.
type statusRecorder struct {
	http.ResponseWriter
	status int
}

func (r *statusRecorder) WriteHeader(status int) {
	r.status = status
	r.ResponseWriter.WriteHeader(status)
}

func (s *Server) logRequests(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
		start := time.Now()
		rec := &statusRecorder{ResponseWriter: w, status: http.StatusOK}
		next.ServeHTTP(rec, req)
		s.log.Info("http",
			"method", req.Method,
			"path", req.URL.Path,
			"status", rec.status,
			"duration", time.Since(start),
		)
	})
}

func (s *Server) recoverPanics(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
		defer func() {
			if err := recover(); err != nil {
				if err == http.ErrAbortHandler {
					panic(err)
				}
				s.log.Error("panic", "err", err, "stack", string(debug.Stack()))
				http.Error(w, http.StatusText(http.StatusInternalServerError), http.StatusInternalServerError)
			}
		}()
		next.ServeHTTP(w, req)
	})
}
