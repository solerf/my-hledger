package dto

import "testing"

func TestDecimalString(t *testing.T) {
	cases := []struct{ in, want string }{
		{"42.50", "42.50"},
		{"42.5", "42.5"},
		{"100", "100"},
		{"0.001", "0.001"},
		{"-12.30", "-12.30"},
		{"4.25E+2", "425"},
		{"1e3", "1000"},
		{"2.5e-2", "0.025"},
		{"0.00", "0.00"},
		{"0", "0"},
	}
	for _, c := range cases {
		d, err := ParseDecimal(c.in)
		if err != nil {
			t.Fatalf("ParseDecimal(%q): %v", c.in, err)
		}
		if got := d.String(); got != c.want {
			t.Errorf("ParseDecimal(%q).String() = %q, want %q", c.in, got, c.want)
		}
	}
}

func TestDecimalMantissaPlaces(t *testing.T) {
	cases := []struct {
		in       string
		mantissa int64
		places   int
	}{
		{"42.50", 425, 1},
		{"42.5", 425, 1},
		{"100", 100, 0},
		{"100.00", 100, 0},
		{"0.001", 1, 3},
		{"-12.30", -123, 1},
		{"0.00", 0, 0},
	}
	for _, c := range cases {
		d, err := ParseDecimal(c.in)
		if err != nil {
			t.Fatalf("ParseDecimal(%q): %v", c.in, err)
		}
		m, p, err := d.MantissaPlaces()
		if err != nil {
			t.Fatalf("MantissaPlaces(%q): %v", c.in, err)
		}
		if m != c.mantissa || p != c.places {
			t.Errorf("MantissaPlaces(%q) = (%d, %d), want (%d, %d)", c.in, m, p, c.mantissa, c.places)
		}
	}
}

func TestDecimalInvalid(t *testing.T) {
	for _, in := range []string{"", "abc", "1.2.3", "--1", "1e", "12,5"} {
		if _, err := ParseDecimal(in); err == nil {
			t.Errorf("ParseDecimal(%q): expected error", in)
		}
	}
}
