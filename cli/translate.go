package main

import (
	"encoding/csv"
	"fmt"
	"os"
	"strings"
	"time"
)

var accountTypeMap = map[string]string{
	"a": "assets",
	"l": "liabilities",
	"e": "equity",
	"r": "revenues",
	"x": "expenses",
}

func translateCSV(src, dst string) error {
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()

	r := csv.NewReader(in)
	r.FieldsPerRecord = -1
	r.TrimLeadingSpace = true

	records, err := r.ReadAll()
	if err != nil {
		return fmt.Errorf("parse csv: %w", err)
	}
	if len(records) == 0 {
		return fmt.Errorf("csv is empty")
	}

	out := make([][]string, 0, len(records))
	out = append(out, records[0])

	var prevDate string
	for i, row := range records[1:] {
		lineno := i + 2
		for j := range row {
			row[j] = strings.TrimSpace(row[j])
		}
		if isBlankRow(row) {
			continue
		}

		var date, desc, from, to, amount string
		switch len(row) {
		case 5:
			date, desc, from, to, amount = row[0], row[1], row[2], row[3], row[4]
			prevDate = date
		case 4:
			if prevDate == "" {
				return fmt.Errorf("line %d: missing date column and no previous date to inherit", lineno)
			}
			date = prevDate
			desc, from, to, amount = row[0], row[1], row[2], row[3]
		default:
			return fmt.Errorf("line %d: got %d columns, expected 4 or 5", lineno, len(row))
		}

		parsedDate, err := translateDate(date)
		if err != nil {
			return fmt.Errorf("line %d: date: %w", lineno, err)
		}
		fromAcct, err := translateAccount(from)
		if err != nil {
			return fmt.Errorf("line %d: from: %w", lineno, err)
		}
		toAcct, err := translateAccount(to)
		if err != nil {
			return fmt.Errorf("line %d: to: %w", lineno, err)
		}

		out = append(out, []string{
			strings.TrimSpace(parsedDate),
			strings.ToUpper(strings.TrimSpace(desc)),
			fromAcct,
			toAcct,
			strings.TrimSpace(strings.ReplaceAll(amount, ",", ".")),
		})
	}

	f, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer f.Close()
	w := csv.NewWriter(f)
	if err := w.WriteAll(out); err != nil {
		return err
	}
	return w.Error()
}

func isBlankRow(row []string) bool {
	for _, c := range row {
		if c != "" {
			return false
		}
	}
	return true
}

func translateAccount(raw string) (string, error) {
	parts := strings.Split(strings.ToLower(strings.TrimSpace(raw)), ".")
	for i := range parts {
		parts[i] = strings.TrimSpace(parts[i])
	}
	if len(parts) == 0 || parts[0] == "" {
		return "", fmt.Errorf("empty reason")
	}
	typeName, ok := accountTypeMap[parts[0]]
	if !ok {
		return "", fmt.Errorf("unknown account type prefix %q in reason %q (want one of a, l, e, r, x)", parts[0], raw)
	}
	parts[0] = typeName
	clean := parts[:0]
	for _, p := range parts {
		if p != "" {
			clean = append(clean, p)
		}
	}
	return strings.Join(clean, ":"), nil
}

func translateDate(rawDate string) (string, error) {
	// just ensure it is a valid date and return it in expected format
	_, err := time.Parse("2006-01-02", rawDate)
	if err == nil {
		return rawDate, nil
	}

	var parsed time.Time
	parsed, err = time.Parse("20060102", rawDate)
	if err == nil {
		return parsed.Format("2006-01-02"), nil
	}
	return "", fmt.Errorf("date in invalid format: %q", rawDate)
}
