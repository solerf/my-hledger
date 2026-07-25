package dto

import (
	"fmt"
	"strconv"
	"strings"
)

// Decimal is an exact decimal number decoded from a JSON number literal. It
// preserves the wire representation (scale included) the way the old backend's
// BigDecimal did, so the `id:` dedup tag and the hledger quantity payload stay
// byte-identical with what the Scala version produced.
type Decimal struct {
	neg    bool
	digits string // significant digits, no sign, no leading zeros ("0" for zero)
	scale  int    // digits after the decimal point, >= 0
}

func ParseDecimal(s string) (Decimal, error) {
	var d Decimal

	rest := s
	switch {
	case strings.HasPrefix(rest, "-"):
		d.neg = true
		rest = rest[1:]
	case strings.HasPrefix(rest, "+"):
		rest = rest[1:]
	}

	exp := 0
	if i := strings.IndexAny(rest, "eE"); i >= 0 {
		e, err := strconv.Atoi(rest[i+1:])
		if err != nil {
			return Decimal{}, fmt.Errorf("invalid decimal %q: %w", s, err)
		}
		exp = e
		rest = rest[:i]
	}

	intPart, fracPart, _ := strings.Cut(rest, ".")
	digits := intPart + fracPart
	if digits == "" {
		return Decimal{}, fmt.Errorf("invalid decimal %q", s)
	}
	for _, c := range digits {
		if c < '0' || c > '9' {
			return Decimal{}, fmt.Errorf("invalid decimal %q", s)
		}
	}

	digits = strings.TrimLeft(digits, "0")
	if digits == "" {
		digits = "0"
	}

	scale := len(fracPart) - exp
	if scale < 0 {
		if digits != "0" {
			digits += strings.Repeat("0", -scale)
		}
		scale = 0
	}

	d.digits = digits
	d.scale = scale
	if digits == "0" {
		d.neg = false
	}
	return d, nil
}

func (d *Decimal) UnmarshalJSON(data []byte) error {
	s := strings.Trim(string(data), `"`)
	parsed, err := ParseDecimal(s)
	if err != nil {
		return err
	}
	*d = parsed
	return nil
}

func (d Decimal) MarshalJSON() ([]byte, error) {
	return []byte(d.String()), nil
}

// String renders the value without an exponent, keeping the decoded scale —
// the equivalent of BigDecimal.toPlainString ("42.50" stays "42.50").
func (d Decimal) String() string {
	ds := d.digits
	if d.scale > 0 {
		if len(ds) <= d.scale {
			ds = strings.Repeat("0", d.scale-len(ds)+1) + ds
		}
		p := len(ds) - d.scale
		ds = ds[:p] + "." + ds[p:]
	}
	if d.neg {
		return "-" + ds
	}
	return ds
}

// Abs returns the value with the sign dropped.
func (d Decimal) Abs() Decimal {
	d.neg = false
	return d
}

func (d Decimal) IsZero() bool { return d.digits == "0" }

// MantissaPlaces reports the unscaled value and decimal places after dropping
// trailing fraction zeros — mirroring stripTrailingZeros + setScale in the
// Scala payload builder ("42.50" -> 425, 1; "100" -> 100, 0).
func (d Decimal) MantissaPlaces() (int64, int, error) {
	if d.digits == "0" {
		return 0, 0, nil
	}
	digits, scale := d.digits, d.scale
	for scale > 0 && strings.HasSuffix(digits, "0") {
		digits = digits[:len(digits)-1]
		scale--
	}
	if digits == "" {
		digits = "0"
	}
	if d.neg {
		digits = "-" + digits
	}
	mantissa, err := strconv.ParseInt(digits, 10, 64)
	if err != nil {
		return 0, 0, fmt.Errorf("decimal %s out of range: %w", d, err)
	}
	return mantissa, scale, nil
}

func (d Decimal) Float64() float64 {
	f, _ := strconv.ParseFloat(d.String(), 64)
	return f
}
