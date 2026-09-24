#!/usr/bin/env python3
"""gen_calc_cases.py -- the independent host oracle for docs/plan/qa/phase-15/calc-cases.tsv.

Writes every expectation of the Calculator fixture table (phase 15 E11, E12, E26, E29) from Windows
Calculator's own rules, computed on this host. Nothing here reads, runs or imitates the app's engine:
the rules are read from microsoft/calculator at 4fd3fc53bade573ea2ac1e1ebad9bf603713f85e (MIT) and
ported literally (Python `fractions` for exact rational arithmetic, `mpmath` 1.3.0 at 120 digits for
transcendentals, `datetime` for dates). Every table line's `source` column cites the Microsoft
file:line whose rule gives its expectation. Schema and key vocabulary: docs/plan/qa/phase-15/calc-keys.md.

Run:  python3 docs/plan/qa/phase-15/scripts/gen_calc_cases.py      (rewrites calc-cases.tsv, prints a summary)

HOW THE EXPECTATIONS ARE MADE
  * Number display: a line-for-line port of ratpak NumberToString (Ratpack/conv.cpp:1076-1264) fed by a port
    of RatToNumber / divnum (conv.cpp:1296-1318, num.cpp:381-477). Precision 16 / 32 / 64 per mode
    (CalculatorManager.h:24-26, CalculatorManager.cpp:170/189/207). Overflow is scidisp.cpp:117-120 +
    IsNumberInvalid :129-193 (exponent over 4 digits). Grouping is GroupDigits scidisp.cpp:246-384 with the en-US
    "," every 3 (calc.cpp:20-21); Programmer HEX/BIN groups of 4 and OCT groups of 3 with a space; the BIN main
    display is zero-padded to the nibble by the view model (StandardCalculatorViewModel.cs:1206-1217, 1941-1958).
  * Key sequences: a port of CCalcEngine::ProcessCommandWorker (CEngine/scicomm.cpp:100-881), DoOperation
    (scioper.cpp:10-175), SciCalcFunctions (scifunc.cpp:27-291), CalcInput (CalcInput.cpp), the radix / word
    size code (sciset.cpp, scidisp.cpp:54-76) and CalculatorManager's memory list (CalculatorManager.cpp:326-445).
    Standard executes immediately, Scientific and Programmer respect precedence (CalculatorManager.cpp:164/183/201).
  * Transcendentals are computed with mpmath at 120 digits. The engine's own constants (pi, ln 10, e, ...) are
    exact to about 2e-47 (checked against Ratpack/ratconst.h), so every non-integer display is also formatted with
    the value nudged by +-1e-(P+6) relative and with one extra divnum digit; a case whose display would change is
    refused by the generator (GuardError) instead of being guessed. None is refused in the committed case list.
  * Unit converter: UnitConverter.cpp:865-925 (double arithmetic, "%.*f" with the precision rule, trailing zeros
    trimmed) over the factor tables of UnitConverterDataLoader.cs:476-688, then the view model's grouped
    DecimalFormatter (UnitConverterViewModel.cs:1202-1330). Unit names are the en-US Resources.resw strings.
  * Date calculation: DateCalculator.cs (Add: years, months, days; Subtract: days, months, years -- :86-88,
    :113-115; month-end clamping of Windows.Globalization.Calendar as pinned by the repo's own tests,
    Calculator.Tests/DateCalculatorTests.cs:44-104) and the wording of DateCalculatorViewModel.cs:229-332 with the
    Resources.resw Date_* strings (:2963-2987).
  * Tess: T15-2's grammar evaluated through the same Scientific port (precedence, DEG, precision 32); reply text
    from T15-52's rule ("15 % of 80 is 12.", "√81 is 9.", "5 miles is 8.04672 kilometers.", errors as
    "<Windows string>.").

FINDINGS THE TABLE ENCODES (they differ from phase-15 text; the source wins for what a key computes)
  * "Result not defined" (CEngineStrings.resw:140-142, id 108 = IDS_ERRORS_FIRST 99 + SCODE_CODE(CALC_E_NORESULT 9),
    CalcErr.h:87, scifunc.cpp:298) is produced by exactly one input family: a Programmer Lsh / Rsh whose shift count
    is >= the word size (scioper.cpp:41-44, 65-68, 74-77). So E11's "1 Lsh 64 (QWORD) -> 0" is NOT what Windows
    shows: it shows "Result not defined" (programmer lines below). A shift of 63 is the largest that gives a number.
  * "Not enough memory" (id 105) is unreachable by a fixture: CALC_E_OUTOFMEMORY is thrown only when zmalloc fails
    (Ratpack/conv.cpp:204-207, 237-240). It is left out.
  * In DEC BYTE the digits 2 5 5 cannot be typed (the third digit is refused: CalcInput.cpp:124-160 against
    "127"), so E11's "BYTE 255 + 1 -> 0" is written in HEX (F F + 1 = -> 0) and in DEC as 127 + 1 = -> -128.
  * Scientific Mod by 0 returns the dividend (logic.cpp:229-235, "modrat(X, 0) must return X"); Programmer Mod by
    0 is "Result is undefined" (remrat, logic.cpp:192-198).

DRIVER NOTES (state the engine keeps across `clear`; none of it is reset by C, scicomm.cpp:434-462)
  * Memory survives C: every memory line starts with `mc` (a no-op when memory is already empty).
  * The F-E format flag survives C in the engine (scicomm.cpp:819-823); the view model only unchecks the toggle
    (StandardCalculatorViewModel.cs:1453-1461). The single `fe` line is the LAST scientific line for that reason.
  * The source's 2nd-function toggle is sticky on the main rows (CalculatorScientificOperators.xaml.cs:60-70) and
    resets after a trig pick (:81-87). Each `inv` / `hyp` line uses the toggle once; the driver must see ↑ and HYP
    off before the next line (read the key labels from the dump), because `clear` does not reset them.
  * `setup` values contain spaces (unit and category names): split `setup` on the regex
    r"\\s+(?=(?:angle|radix|word|category|from|to|op)=)".

CASES OMITTED (ambiguous or unreachable in the source -- omitted rather than guessed)
  * "Not enough memory": unreachable (above).
  * An e-notation switch in Programmer (precision 64): unreachable -- the widest value, 64 BIN digits, equals the
    precision and conv.cpp:1083 switches only when there are MORE integer digits than the precision; the table
    carries the 64-digit boundary line instead.
  * Scientific `reciprocal`: calc-keys.md places it "under inv, per the source" without naming the base key on the
    10586 layout; the 4fd3fc5 layout has a separate 1/x key. Omitted.
  * Non-integer factorial (gamma, fact.cpp:56-180): its accuracy at 32 digits is not pinned by the source. Omitted.
  * Converter results that take the e-notation branch (UnitConverter.cpp:898-900, `out << scientific`): the
    exponent width is MSVC-runtime formatting, not in the repo. Omitted.
  * Converter values that are exact binary ties at the "%.*f" rounding digit (runtime tie rule not in the repo):
    the generator refuses them; none is in the list.
  * Date add/subtract lines render with Windows' en-US "longdate" pattern "dddd, MMMM d, yyyy"
    (DateCalculatorViewModel.cs:292-297 asks WinRT for "longdate"; the pattern itself is Windows' regional data,
    not in the repo). WinRT inserts no visible characters beyond that pattern in en-US; invisible U+200E marks, if a
    platform adds them, are not part of the expectation.
  * Engine states the phone UI cannot reach (MR / MC with an empty list: disabled, r11/calculator.md 2.17) and the
    paste row of E11 (driver-level; its expectation is host arithmetic 12 + 3 = 15) are not table lines.
  * Standard `%` alone after C: kept (it is 0, scifunc.cpp:98-111 with m_lastVal 0).
"""

import math
import re
import sys
from datetime import date, timedelta
from decimal import Decimal
from fractions import Fraction
from pathlib import Path

import mpmath

mpmath.mp.dps = 120
sys.set_int_max_str_digits(0)  # 10^10000-sized integers reach the formatter (the Overflow lines)

OUT = Path(__file__).resolve().parent.parent / "calc-cases.tsv"
SRC_COMMIT = "4fd3fc53bade573ea2ac1e1ebad9bf603713f85e"


class GuardError(Exception):
    """The display would depend on digits the source does not pin down: the case is refused."""


class CalcErr(Exception):
    def __init__(self, code):
        super().__init__(code)
        self.code = code


# CEngineStrings.resw ids 99 + SCODE_CODE(err) (scifunc.cpp:296-305, CalcErr.h:46-87)
ERR_TEXT = {
    "DIVIDEBYZERO": "Cannot divide by zero",  # 0x80000000 -> 99, CEngineStrings.resw:416-418
    "DOMAIN": "Invalid input",  # 0x80000001 -> 100, :124-126
    "INDEFINITE": "Result is undefined",  # 0x80000002 -> 101, :128-130
    "OUTOFMEMORY": "Not enough memory",  # 0x80000007 -> 105, :132-134 (unreachable)
    "OVERFLOW": "Overflow",  # 0x80000008 -> 107, :136-138
    "NORESULT": "Result not defined",  # 0x80000009 -> 108, :140-142
}

# ----------------------------------------------------------------------------------------------------------------
# 1. ratpak NUMBER and NumberToString (Ratpack/conv.cpp:1026-1264), radix 10 only (Programmer integers are
#    printed exactly, which is what NumberToString yields for an integer of <= 64 digits at precision 64).
# ----------------------------------------------------------------------------------------------------------------

MAX_ZEROS_AFTER_DECIMAL = 2  # conv.cpp:28


class Num:
    __slots__ = ("sign", "exp", "mant")

    def __init__(self, sign, exp, mant):
        self.sign = sign
        self.exp = exp
        self.mant = mant  # digits, least significant first (MANTTYPE order)

    @property
    def cdigit(self):
        return len(self.mant)

    def copy(self):
        return Num(self.sign, self.exp, list(self.mant))


def zernum(n):
    return all(d == 0 for d in n.mant)


def stripzeroesnum(n, starting):  # conv.cpp:1026-1059
    cd = n.cdigit
    idx = 0
    cdigits = cd
    if cd > starting:
        idx = cd - starting
        cdigits = starting
    fstrip = False
    while cdigits > 0 and n.mant[idx] == 0:
        idx += 1
        cdigits -= 1
        fstrip = True
    if fstrip:
        n.mant = n.mant[idx : idx + cdigits]
        n.exp += cd - cdigits
    return fstrip


def addnum_same_sign(a, b):  # num.cpp:62-172 for operands of one sign (the round has pnum's sign)
    if zernum(b):
        return a
    if zernum(a):
        return b.copy()
    mexp = min(a.exp, b.exp)
    va = int("".join(str(d) for d in reversed(a.mant))) * 10 ** (a.exp - mexp)
    vb = int("".join(str(d) for d in reversed(b.mant))) * 10 ** (b.exp - mexp)
    s = va + vb
    return Num(a.sign, mexp, [int(c) for c in reversed(str(s))])


FLOAT, SCI = "float", "sci"


def number_to_string(n, fmt, precision):  # conv.cpp:1076-1264, radix 10
    stripzeroesnum(n, precision + 2)
    length = n.cdigit
    exponent = n.exp + length
    old_fmt = fmt
    if exponent > precision and fmt == FLOAT:
        fmt = SCI  # :1083-1087
    if length > precision:
        length = precision
    rnd = None
    if not zernum(n) and (
        n.cdigit >= precision or (length - exponent > precision and exponent >= -MAX_ZEROS_AFTER_DECIMAL)
    ):
        rnd = Num(n.sign, 0, [5])  # radix / 2
        if exponent > 0 or fmt == FLOAT:
            rnd.exp = n.exp + n.cdigit - rnd.cdigit - precision
        else:
            rnd.exp = n.exp + n.cdigit - rnd.cdigit - precision - exponent
            length = precision + exponent
    if fmt == FLOAT:
        if (length - exponent > precision) or (exponent > precision + 3):
            if exponent >= -MAX_ZEROS_AFTER_DECIMAL:
                rnd.exp -= exponent
                length = precision + exponent
            else:
                fmt = SCI  # :1129-1134
        elif length + abs(exponent) < precision and rnd is not None:
            rnd.exp -= exponent
    if rnd is not None:
        n = addnum_same_sign(n, rnd)
        offset = (n.cdigit + n.exp) - (rnd.cdigit + rnd.exp)
        if stripzeroesnum(n, offset):
            return number_to_string(n, old_fmt, precision)
    else:
        stripzeroesnum(n, precision)
    use_sci = False
    eout = exponent - 1
    idx = n.cdigit - 1
    if fmt == SCI:
        use_sci = True
        if eout != 0:
            exponent = 1
    else:
        eout = 0
    out = ""
    if n.sign == -1 and length > 0:
        out = "-"
    if exponent <= 0 and not use_sci:
        out += "0."
    while exponent < 0:
        out += "0"
        exponent += 1
    while length > 0:
        exponent -= 1
        if idx < 0:
            raise GuardError("digit underrun")
        out += str(n.mant[idx])
        idx -= 1
        length -= 1
        if exponent == 0:
            out += "."
    while exponent > 0:
        out += "0"
        exponent -= 1
        if exponent == 0:
            out += "."
    if use_sci:
        out += "e" + ("-" if eout < 0 else "+") + str(abs(eout))
    if out.endswith("."):
        out = out[:-1]
    return out


def rat_to_number(v, precision, extra=0):  # RatToNumber conv.cpp:1296-1318 + divnum num.cpp:381-477
    """divnum's quotient: thismax digits from 10^(c_exp-1) down, truncated, stopping early when the remainder is 0."""
    sign = -1 if v < 0 else 1
    p, q = abs(v.numerator), v.denominator
    if p == 0:
        return Num(1, 0, [0])
    if q == 1:  # divnum with a divisor of one only sets the sign (num.cpp:367-378)
        return Num(sign, 0, [int(c) for c in reversed(str(p))])
    lp, lq = len(str(p)), len(str(q))
    thismax = max(precision + 2, lp, lq) + extra
    c_exp = lp - lq + 1
    n = thismax
    q2 = q
    a = b = 0
    while q2 % 2 == 0:
        q2 //= 2
        a += 1
    while q2 % 5 == 0:
        q2 //= 5
        b += 1
    if q2 == 1:  # terminating: the remainder reaches 0 after c_exp + max(a, b) digits
        n = min(n, c_exp + max(a, b))
    k = n - c_exp
    t = (p * 10**k) // q if k >= 0 else p // (q * 10 ** (-k))
    return Num(sign, c_exp - n, [int(c) for c in reversed(str(t))])


def fmt_plain(v, precision, fmt=FLOAT, extra=0):
    return number_to_string(rat_to_number(v, precision, extra), fmt, precision)


def fmt_guarded(v, precision, fmt=FLOAT):
    """NumberToString of v, refusing any value whose display depends on digits below ~P+6."""
    d = Fraction(1, 10 ** (precision + 6))
    outs = {fmt_plain(vv, precision, fmt, extra) for vv in (v, v * (1 + d), v * (1 - d)) for extra in (0, 1)}
    if len(outs) != 1:
        raise GuardError("unstable display %r for %s" % (sorted(outs), v))
    return outs.pop()


# IsNumberInvalid scidisp.cpp:129-193 (radix 10)
_INVALID_RX = re.compile(r"[+-]?(\d*)[.]?(\d*)(?:e[+-]?(\d*))?")


def is_number_invalid(s, max_exp, max_mantissa):
    m = _INVALID_RX.fullmatch(s)
    if not m:
        return True
    if len(m.group(3) or "") > max_exp:
        return True
    return len(m.group(1).lstrip("0")) + len(m.group(2)) > max_mantissa


def group_digits(delim, grouping, s, is_neg=False):  # scidisp.cpp:286-384
    if not delim or not grouping:
        return s
    e = s.find("e")
    dec = s.find(".")
    if dec != -1:
        end = dec
    elif e != -1:
        end = e
    else:
        end = len(s)
    stop = 1 if is_neg else 0
    out = []
    gi = 0
    curr = grouping[0]
    size = 0
    i = end - 1
    while i >= stop:
        out.append(s[i])
        i -= 1
        size += 1
        if curr != 0 and size % curr == 0 and i >= stop:
            out.append(delim)
            size = 0
            if gi < len(grouping):
                gi += 1
                curr = 0
                while gi < len(grouping):
                    if grouping[gi] != 0:
                        curr = grouping[gi]
                        break
                    curr = grouping[gi - 1]
                    gi += 1
    if is_neg:
        out.append(s[0])
    res = "".join(reversed(out))
    if dec != -1:
        res += s[dec:]
    elif e != -1:
        res += s[e:]
    return res


def group_per_radix(s, radix):  # scidisp.cpp:246-265
    if not s:
        return s
    if radix == 10:
        return group_digits(",", [3, 0], s, s[0] == "-")
    if radix == 8:
        return group_digits(" ", [3, 0], s)
    return group_digits(" ", [4, 0], s)


def add_padding(s):  # StandardCalculatorViewModel.cs:1941-1958
    if not s or s == "0":
        return s
    n = len(s.replace(" ", ""))
    pad = 4 - n % 4
    if pad == 4:
        pad = 0
    return "0" * pad + s


# ----------------------------------------------------------------------------------------------------------------
# 2. Rational helpers and transcendentals (RationalMath.cpp, Ratpack/*)
# ----------------------------------------------------------------------------------------------------------------


def mpf(x):
    return mpmath.mpf(x.numerator) / x.denominator


def frac(m):
    sign, man, exp, _ = mpmath.mpf(m)._mpf_
    v = Fraction(int(man)) * (Fraction(2) ** exp)
    return -v if sign else v


def trunc(x):
    return Fraction(int(x))  # int() truncates toward zero: intrat (support.cpp:291-317)


def is_int(x):
    return x.denominator == 1


class Math:
    """RationalMath for one precision P (rat_smallest = 10^-P, support.cpp:175-176)."""

    def __init__(self, precision):
        self.P = precision
        self.smallest = Fraction(1, 10**precision)

    def add(self, a, b):  # addrat + _snaprat (rat.cpp:224-233, 346-388)
        r = a + b
        if abs(r) < max(abs(a), abs(b)) * self.smallest:
            return Fraction(0)
        return r

    def sub(self, a, b):  # subrat + _snaprat (rat.cpp:188-197)
        r = a - b
        if abs(r) < max(abs(a), abs(b)) * self.smallest:
            return Fraction(0)
        return r

    @staticmethod
    def div(a, b):  # divrat rat.cpp:135-164
        if a != 0:
            if b == 0:
                raise CalcErr("DIVIDEBYZERO")
            return a / b
        if b == 0:
            raise CalcErr("INDEFINITE")
        return Fraction(0)

    def invert(self, a):  # RationalMath::Invert = 1 / rat
        return self.div(Fraction(1), a)

    # -- logs / exp (exp.cpp) --
    def ln(self, x):  # lograt exp.cpp:155-233
        if x <= 0:
            raise CalcErr("DOMAIN")
        r = frac(mpmath.log(mpf(x)))
        if abs(r) < abs(x) * self.smallest:  # _snaprat(px, a) exp.cpp:231
            return Fraction(0)
        return r

    def log10(self, x):  # Log(rat) / ln_ten, RationalMath.cpp
        return self.ln(x) / frac(mpmath.log(10))

    @staticmethod
    def exp(x):  # exprat exp.cpp:61-70
        if x > 100000 or x < -100000:
            raise CalcErr("DOMAIN")
        return frac(mpmath.exp(mpf(x)))

    def powcomp(self, x, y):  # powratcomp exp.cpp:414-529
        sign = -1 if x < 0 else 1
        ax = abs(x)
        if ax == 0:
            if y < 0:
                raise CalcErr("DOMAIN")
            return Fraction(1) if y == 0 else Fraction(0)
        if abs(ax - 1) < self.smallest and sign == 1:
            return Fraction(1)
        if is_int(y):
            iy = int(y)
            if abs(mpmath.log(mpf(ax)) * iy) > 100000:
                raise CalcErr("DOMAIN")
            r = ax**iy
            if iy % 2 == 0:
                sign = 1
            return sign * r
        if sign == -1:
            n, d = abs(y.numerator), y.denominator
            while n % 2 == 0 and d % 2 == 0:
                n //= 2
                d //= 2
            if d % 2 == 0:
                raise CalcErr("DOMAIN")
            if n % 2 == 0:
                sign = 1
        else:
            sign = 1
        t = y * frac(mpmath.log(mpf(ax)))
        return sign * self.exp(t)

    def pow(self, x, y):  # powrat exp.cpp:278-303 + powratNumeratorDenominator :305-412
        if x == 0 or y == 0:
            return self.powcomp(x, y)
        if y == 1:
            return x
        try:
            n, d = y.numerator, y.denominator
            px = x if n == 1 else self.powcomp(x, Fraction(n))
            if d == 1:
                return px
            orig = self.powcomp(px, Fraction(1, d))
            rounded = trunc(orig - Fraction(1, 2)) if orig < 0 else trunc(orig + Fraction(1, 2))
            if self.powcomp(rounded, Fraction(d)) == px:
                return rounded
            return orig
        except CalcErr:
            return self.powcomp(x, y)

    def root(self, x, n):  # RationalMath::Root = Pow(base, Invert(root))
        return self.pow(x, self.invert(n))

    @staticmethod
    def fact(x):  # factrat fact.cpp:187-246
        if x > 3249 or x < -1000:
            raise CalcErr("OVERFLOW")
        if not is_int(x):
            raise GuardError("non-integer factorial is not pinned by the source")
        if x < 0:
            raise CalcErr("DOMAIN")
        return Fraction(math.factorial(int(x)))

    # -- trig (trans.cpp, itrans.cpp, transh.cpp, itransh.cpp) --
    def _unit_snap(self, m):  # inbetween + "epsilon near zero" (trans.cpp:84-93, 179-188)
        if m > 1:
            m = mpmath.mpf(1)
        if m < -1:
            m = mpmath.mpf(-1)
        r = frac(m)
        if abs(r) <= self.smallest:
            return Fraction(0)
        return r

    @staticmethod
    def _scale(x, full):  # scale() support.cpp:473-493: x - trunc(x/full)*full
        return x - trunc(x / full) * full

    def _radians(self, x, ang, kind):
        if ang == "rad":
            return mpf(x)
        full, half = (Fraction(360), Fraction(180)) if ang == "deg" else (Fraction(400), Fraction(200))
        x = self._scale(x, full)
        if x > half:
            if kind == "sin":
                x -= full
            elif kind == "cos":
                x = full - x
            else:
                x -= half
        return mpf(x) * mpmath.pi / mpf(half)

    def sin(self, x, ang):
        return self._unit_snap(mpmath.sin(self._radians(x, ang, "sin")))

    def cos(self, x, ang):
        return self._unit_snap(mpmath.cos(self._radians(x, ang, "cos")))

    def tan(self, x, ang):  # _tanrat trans.cpp:242-257
        r = self._radians(x, ang, "tan")
        s = self._unit_snap(mpmath.sin(r))
        c = self._unit_snap(mpmath.cos(r))
        if c == 0:
            raise CalcErr("DOMAIN")
        return s / c

    @staticmethod
    def _ascale(r, ang):  # ascalerat itrans.cpp
        if ang == "deg":
            return frac(r * 360 / (2 * mpmath.pi))
        if ang == "grad":
            return frac(r * 400 / (2 * mpmath.pi))
        return frac(r)

    def asin(self, x, ang):  # asinrat itrans.cpp
        if abs(x) > 1:
            if abs(x) - 1 > self.smallest:
                raise CalcErr("DOMAIN")
            x = Fraction(1 if x > 0 else -1)
        return self._ascale(mpmath.asin(mpf(x)), ang)

    def acos(self, x, ang):
        if abs(x) > 1 and abs(x) - 1 > self.smallest:
            raise CalcErr("DOMAIN")
        return self._ascale(mpmath.acos(mpf(max(min(x, Fraction(1)), Fraction(-1)))), ang)

    def atan(self, x, ang):
        return self._ascale(mpmath.atan(mpf(x)), ang)

    @staticmethod
    def sinh(x):
        if x < -10000:
            raise CalcErr("DOMAIN")
        return frac(mpmath.sinh(mpf(x)))

    @staticmethod
    def cosh(x):
        if x < -10000:
            raise CalcErr("DOMAIN")
        return frac(mpmath.cosh(mpf(x)))

    @staticmethod
    def tanh(x):
        return frac(mpmath.tanh(mpf(x)))

    @staticmethod
    def asinh(x):
        return frac(mpmath.asinh(mpf(x)))

    @staticmethod
    def acosh(x):  # itransh.cpp:104-122
        if x < 1:
            raise CalcErr("DOMAIN")
        return frac(mpmath.acosh(mpf(x)))


# ----------------------------------------------------------------------------------------------------------------
# 3. CalcInput (CEngine/CalcInput.cpp)
# ----------------------------------------------------------------------------------------------------------------

HEXDIGITS = "0123456789ABCDEF"


class CalcInput:
    def __init__(self):
        self.clear()

    def clear(self):
        self.base, self.base_neg = "", False
        self.expo, self.expo_neg = "", False
        self.has_exp = False
        self.has_dec = False
        self.dec_idx = 0

    def try_toggle_sign(self, is_int_mode, max_str):  # :28-57
        if self.base == "":
            self.base_neg = False
            self.expo_neg = False
        elif self.has_exp:
            self.expo_neg = not self.expo_neg
        else:
            if is_int_mode and self.base_neg:
                if len(self.base) >= len(max_str) and self.base[-1] > max_str[-1]:
                    return False
            self.base_neg = not self.base_neg
        return True

    def try_add_digit(self, value, radix, is_int_mode, max_str, bits, max_digits):  # :59-170
        ch = HEXDIGITS[value]
        if self.has_exp:
            cur, max_count = self.expo, 4
        else:
            cur = self.base
            max_count = max_digits
            if self.has_dec:
                max_count += 1
            if cur != "" and cur[0] == "0":
                max_count += 1
        if cur == "" and value == 0:
            return True
        ok = False
        if len(cur) < max_count:
            ok = True
        elif is_int_mode and len(cur) == max_count and not self.has_exp:
            if radix == 8:
                m = bits % 3
                if m == 1:
                    ok = cur[0] == "1"
                elif m == 2:
                    ok = cur[0] <= "3"
            elif radix == 10 and len(cur) < len(max_str):
                prefix = max_str[: len(cur)]
                if cur < prefix:
                    ok = True
                elif cur == prefix:
                    last = max_str[len(cur)]
                    if ch <= last or (self.base_neg and ord(ch) <= ord(last) + 1):
                        ok = True
        if ok:
            if self.has_exp:
                self.expo += ch
            else:
                self.base += ch
        return ok

    def try_add_decimal_pt(self):  # :172-190
        if self.has_dec or self.has_exp:
            return False
        if self.base == "":
            self.base = "0"
        self.dec_idx = len(self.base)
        self.base += "."
        self.has_dec = True
        return True

    def try_begin_exponent(self):  # :197-209
        self.try_add_decimal_pt()
        if self.has_exp:
            return False
        self.has_exp = True
        return True

    def backspace(self):  # :211-252
        if self.has_exp:
            if self.expo != "":
                self.expo = self.expo[:-1]
                if self.expo == "":
                    self.expo_neg = False
            else:
                self.has_exp = False
        else:
            if self.base != "":
                self.base = self.base[:-1]
                if self.base == "0":
                    self.base = ""
            if len(self.base) <= self.dec_idx:
                self.has_dec = False
                self.dec_idx = 0
            if self.base == "":
                self.base_neg = False

    def to_string(self, radix):  # :273-325
        r = "-" if self.base_neg else ""
        r += "0" if self.base == "" else self.base
        if self.has_exp:
            if not self.has_dec:
                r += "."
            r += "e" if radix == 10 else "^"
            r += "-" if self.expo_neg else "+"
            r += "0" if self.expo == "" else self.expo
        return r

    def to_rational(self, radix):  # :327-339 -> StringToRat
        if self.base in ("", "0."):
            mant = Fraction(0)
        else:
            ip, _, fp = self.base.partition(".")
            mant = Fraction(int(ip or "0", radix))
            if fp:
                mant += Fraction(int(fp, radix), radix ** len(fp))
        if self.base == "" and self.has_exp:
            mant = Fraction(1)
        if self.base_neg:
            mant = -mant
        e = int(self.expo, radix) if self.expo else 0
        if self.expo_neg:
            e = -e
        return mant * Fraction(radix) ** e


# ----------------------------------------------------------------------------------------------------------------
# 4. CCalcEngine + CalculatorManager (CEngine/scicomm.cpp, scioper.cpp, scifunc.cpp, sciset.cpp, scidisp.cpp)
# ----------------------------------------------------------------------------------------------------------------

IDC_SIGN, IDC_CLEAR, IDC_CENTR, IDC_BACK, IDC_PNT = 80, 81, 82, 83, 84
IDC_AND, IDC_OR, IDC_XOR, IDC_LSHF, IDC_RSHF, IDC_DIV, IDC_MUL, IDC_ADD, IDC_SUB, IDC_MOD, IDC_ROOT, IDC_PWR = range(86, 98)
(IDC_CHOP, IDC_ROL, IDC_ROR, IDC_COM, IDC_SIN, IDC_COS, IDC_TAN, IDC_SINH, IDC_COSH, IDC_TANH, IDC_LN, IDC_LOG,
 IDC_SQRT, IDC_SQR, IDC_CUB, IDC_FAC, IDC_REC, IDC_DMS, IDC_CUBEROOT, IDC_POW10, IDC_PERCENT) = range(98, 119)
IDC_FE, IDC_PI, IDC_EQU, IDC_MCLEAR, IDC_RECALL, IDC_STORE, IDC_MPLUS, IDC_MMINUS, IDC_EXP, IDC_OPENP, IDC_CLOSEP = range(119, 130)
IDC_0, IDC_INV = 130, 146
IDM_HEX, IDM_DEC, IDM_OCT, IDM_BIN, IDM_QWORD, IDM_DWORD, IDM_WORD, IDM_BYTE, IDM_DEG, IDM_RAD, IDM_GRAD = range(313, 324)
MAXPRECDEPTH = 25  # History.h:15
TRIG = {IDC_SIN, IDC_COS, IDC_TAN, IDC_SINH, IDC_COSH, IDC_TANH}
INV_RESET = {IDC_CHOP, IDC_SIN, IDC_COS, IDC_TAN, IDC_LN, IDC_DMS, IDC_SINH, IDC_COSH, IDC_TANH}


def is_bin(op):
    return IDC_AND <= op <= IDC_PWR


def is_unary(op):
    return IDC_CHOP <= op <= IDC_PERCENT


def is_digit(op):
    return IDC_0 <= op <= IDC_0 + 15


def is_gui(op):  # CalcUtils.cpp IsGuiSettingOpCode
    return IDM_HEX <= op <= IDM_GRAD or op in (IDC_INV, IDC_FE, IDC_MCLEAR, IDC_BACK, IDC_EXP, IDC_STORE, IDC_MPLUS, IDC_MMINUS)


def prec_of(op):  # scicomm.cpp:31-58
    if op in (IDC_AND,):
        return 1
    if op in (IDC_ADD, IDC_SUB):
        return 2
    if op in (IDC_LSHF, IDC_RSHF, IDC_MOD, IDC_DIV, IDC_MUL):
        return 3
    if op in (IDC_PWR, IDC_ROOT):
        return 4
    return 0


MODES = {"standard": (False, False, 16), "scientific": (True, False, 32), "programmer": (True, True, 64)}
RADIX = {"hex": 16, "dec": 10, "oct": 8, "bin": 2}
WIDTHS = {"qword": 64, "dword": 32, "word": 16, "byte": 8}


class Calc:
    def __init__(self, mode, angle="deg", radix="dec", word="qword"):
        self.fPrecedence, self.fInt, self.precision = MODES[mode]
        self.M = Math(self.precision)
        self.mode = mode
        self.radix = 10
        self.bits = 64
        self.angle = angle
        self.nFE = FLOAT
        self.input = CalcInput()
        self.bRecord = False
        self.currentVal = self.lastVal = self.holdVal = Fraction(0)
        self.nOpCode = self.nPrevOpCode = 0
        self.bChangeOp = False
        self.bNoPrevEqu = True
        self.nLastCom = self.nTempCom = 0
        self.precVals = [Fraction(0)] * MAXPRECDEPTH
        self.nPrecOp = [0] * MAXPRECDEPTH
        self.precCount = 0
        self.parenVals = [Fraction(0)] * MAXPRECDEPTH
        self.nOp = [0] * MAXPRECDEPTH
        self.openParen = 0
        self.bError = False
        self.bInv = False
        self.carry = 0
        self.mem = Fraction(0)  # engine m_memoryValue
        self.mem_list = []  # CalculatorManager m_memorizedNumbers
        self.display = "0"
        self.ui_shift = False
        self.ui_hyp = False
        self.update_max_int_digits()
        self.process(IDM_DEC)
        self.process(IDC_CLEAR)
        if self.fInt:
            if radix != "dec":
                self.process(IDM_HEX + list(RADIX).index(radix))
            if word != "qword":
                self.process(IDM_QWORD + list(WIDTHS).index(word))
        self.process({"deg": IDM_DEG, "rad": IDM_RAD, "grad": IDM_GRAD}[angle])
        self.process(IDC_CLEAR)  # the driver presses clear first (calc-keys.md)

    # -- word size helpers --
    @property
    def chop(self):
        return (1 << self.bits) - 1

    def max_dec_str(self):  # calc.cpp:123-132
        return str(self.chop // 2)

    def update_max_int_digits(self):  # sciset.cpp:144-164
        if self.radix == 10:
            self.cIntDigits = len(self.max_dec_str()) - 1 if self.fInt else self.precision
        else:
            self.cIntDigits = self.bits // {16: 4, 8: 3, 2: 1}[self.radix]

    def truncate(self, v):  # scidisp.cpp:54-76
        if not self.fInt:
            return v
        r = int(trunc(v))
        if r < 0:
            r = (-r - 1) ^ self.chop
        return Fraction(r & self.chop)

    # -- display --
    def string_for_display(self, v):  # scicomm.cpp:1161-1193
        if not self.fInt:
            return fmt_guarded(v, self.precision, self.nFE)
        t = int(self.truncate(v))
        if self.radix == 10 and (t >> (self.bits - 1)) & 1:
            t = -((t ^ self.chop) + 1)
        if self.radix == 10:
            return str(t)
        s = ""
        u = t
        while True:
            s = HEXDIGITS[u % self.radix] + s
            u //= self.radix
            if u == 0:
                break
        return s

    def set_display(self, s, is_error=False):
        if self.fInt and self.radix == 2:
            s = add_padding(s)  # the view model pads the BIN display (StandardCalculatorViewModel.cs:1206-1217)
        self.display = s

    def display_num(self):  # scidisp.cpp:78-127
        if self.bRecord:
            s = self.input.to_string(self.radix)
        else:
            if self.fInt:
                self.currentVal = self.truncate(self.currentVal)
            s = self.string_for_display(self.currentVal)
        if self.radix == 10 and is_number_invalid(s, 4, self.precision):
            self.display_error("OVERFLOW")
        else:
            self.set_display(group_per_radix(s, self.radix))

    def display_error(self, code):  # scifunc.cpp:296-305
        self.set_display(ERR_TEXT[code], True)
        self.bError = True

    def handle_error_command(self, op):  # scicomm.cpp:65-72
        if not is_gui(op):
            self.nTempCom = self.nLastCom

    def clear_temporary(self):  # scicomm.cpp:82-90
        self.bInv = False
        self.input.clear()
        self.bRecord = True
        self.display_num()
        self.bError = False

    # -- radix / word (sciset.cpp:12-51) --
    def set_radix_width(self, radix=None, bits=None):
        if self.fInt:
            w = int(self.currentVal)
            if (w >> (self.bits - 1)) & 1:
                self.currentVal = -(Fraction(w ^ self.chop) + 1)
        if radix:
            self.radix = radix
        if bits:
            self.bits = bits
        self.update_max_int_digits()
        self.display_num()

    # -- DoOperation (scioper.cpp:10-175) --
    def do_operation(self, op, lhs, rhs):
        M = self.M
        result = lhs if lhs != 0 else Fraction(0)
        try:
            if op == IDC_AND:
                result = Fraction(int(trunc(result)) & int(trunc(rhs)))
            elif op == IDC_OR:
                result = Fraction(int(trunc(result)) | int(trunc(rhs)))
            elif op == IDC_XOR:
                result = Fraction(int(trunc(result)) ^ int(trunc(rhs)))
            elif op == IDC_RSHF:
                if self.fInt and result >= self.bits:
                    raise CalcErr("NORESULT")
                w = int(rhs)
                msb = (w >> (self.bits - 1)) & 1
                hold = result
                result = trunc(rhs) / Fraction(2) ** int(hold)
                if msb:
                    result = trunc(result)
                    temp = trunc(Fraction(self.chop) / Fraction(2) ** int(hold))
                    result = Fraction(int(result) | (int(temp) ^ self.chop))
            elif op == IDC_LSHF:
                if self.fInt and result >= self.bits:
                    raise CalcErr("NORESULT")
                result = trunc(rhs) * Fraction(2) ** int(result)
            elif op == IDC_ADD:
                result = M.add(result, rhs)
            elif op == IDC_SUB:
                result = M.sub(rhs, result)
            elif op == IDC_MUL:
                result = result * rhs
            elif op in (IDC_DIV, IDC_MOD):
                ns = ds = 1
                temp = result
                result = rhs
                if self.fInt:
                    if (int(rhs) >> (self.bits - 1)) & 1:
                        result = Fraction(int(rhs) ^ self.chop) + 1
                        ns = -1
                    if (int(temp) >> (self.bits - 1)) & 1:
                        temp = Fraction(int(temp) ^ self.chop) + 1
                        ds = -1
                if op == IDC_DIV:
                    result = M.div(result, temp)
                    if self.fInt and ns * ds == -1:
                        result = -trunc(result)
                else:
                    if self.fInt:
                        if temp == 0:
                            raise CalcErr("INDEFINITE")  # remrat logic.cpp:192-198
                        result = result - temp * trunc(result / temp)
                        if ns == -1:
                            result = -trunc(result)
                    else:
                        if temp != 0:  # modrat logic.cpp:229-253: sign of the divisor, X mod 0 = X
                            result = result % temp
            elif op == IDC_PWR:
                result = M.pow(rhs, result)
            elif op == IDC_ROOT:
                result = M.root(rhs, result)
        except CalcErr as e:
            self.display_error(e.code)
            result = lhs
        return result

    # -- SciCalcFunctions (scifunc.cpp:27-291) --
    def sci_functions(self, rat, op):
        M = self.M
        result = Fraction(0)
        try:
            if op == IDC_COM:
                if self.radix == 10 and not self.fInt:
                    result = -(trunc(rat) + 1)
                else:
                    result = Fraction(int(rat) ^ self.chop)
            elif op in (IDC_ROL, IDC_ROR):
                if self.fInt:
                    w = int(trunc(rat))
                    if op == IDC_ROL:
                        msb = (w >> (self.bits - 1)) & 1
                        w = ((w << 1) | msb) & 0xFFFFFFFFFFFFFFFF
                    else:
                        lsb = w & 1
                        w = (w >> 1) | (lsb << (self.bits - 1))
                    result = Fraction(w)
            elif op == IDC_PERCENT:
                if self.nOpCode in (IDC_MUL, IDC_DIV):
                    result = rat / 100
                else:
                    result = rat * (self.lastVal / 100)
            elif op == IDC_SIN:
                result = M.asin(rat, self.angle) if self.bInv else M.sin(rat, self.angle)
            elif op == IDC_COS:
                result = M.acos(rat, self.angle) if self.bInv else M.cos(rat, self.angle)
            elif op == IDC_TAN:
                result = M.atan(rat, self.angle) if self.bInv else M.tan(rat, self.angle)
            elif op == IDC_SINH:
                result = M.asinh(rat) if self.bInv else M.sinh(rat)
            elif op == IDC_COSH:
                result = M.acosh(rat) if self.bInv else M.cosh(rat)
            elif op == IDC_TANH:
                if self.bInv:
                    raise GuardError("atanh not in the case list")
                result = M.tanh(rat)
            elif op == IDC_REC:
                result = M.invert(rat)
            elif op == IDC_SQR:
                result = M.pow(rat, Fraction(2))
            elif op == IDC_SQRT:
                result = M.root(rat, Fraction(2))
            elif op == IDC_CUB:
                result = M.pow(rat, Fraction(3))
            elif op == IDC_LOG:
                result = M.log10(rat)
            elif op == IDC_POW10:
                result = M.pow(Fraction(10), rat)
            elif op == IDC_LN:
                result = M.exp(rat) if self.bInv else M.ln(rat)
            elif op == IDC_FAC:
                result = M.fact(rat)
            else:
                raise GuardError("unported unary op %d" % op)
        except CalcErr as e:
            self.display_error(e.code)
            result = rat
        return result

    def resolve_highest(self):  # scicomm.cpp:884-923
        if self.nOpCode:
            if self.bNoPrevEqu:
                self.holdVal = self.currentVal
            else:
                self.currentVal = self.holdVal
            self.currentVal = self.do_operation(self.nOpCode, self.currentVal, self.lastVal)
            self.nPrevOpCode = self.nOpCode
            self.lastVal = self.currentVal
            if not self.bError:
                self.display_num()
            self.bNoPrevEqu = False
        elif not self.bError:
            self.display_num()

    # -- ProcessCommandWorker (scicomm.cpp:111-881) --
    def process(self, w):
        if not is_gui(w):
            self.nLastCom = self.nTempCom
            self.nTempCom = w
        if self.bError:
            if w == IDC_CLEAR:
                pass
            elif w == IDC_CENTR:
                w = IDC_CLEAR
            else:
                self.handle_error_command(w)
                return
        if self.bRecord:
            if (
                is_bin(w) or is_unary(w) or IDC_FE <= w <= IDC_MMINUS or IDC_OPENP <= w <= IDC_CLOSEP
                or IDM_HEX <= w <= IDM_GRAD or w == IDC_INV or (w == IDC_SIGN and self.radix != 10)
            ):
                self.bRecord = False
                self.currentVal = self.input.to_rational(self.radix)
                self.display_num()
        elif is_digit(w) or w == IDC_PNT:
            self.bRecord = True
            self.input.clear()

        if is_digit(w):
            v = w - IDC_0
            if v >= self.radix:
                self.handle_error_command(w)
                return
            if not self.input.try_add_digit(v, self.radix, self.fInt, self.max_dec_str(), self.bits, self.cIntDigits):
                self.handle_error_command(w)
                return
            if self.nLastCom == IDC_CLOSEP:  # implicit multiplication (8)2 (:196-227)
                self.nOpCode = IDC_MUL
                self.lastVal = self.currentVal
                self.holdVal = Fraction(0)
                self.bNoPrevEqu = True
                self.bChangeOp = True
                self.nPrevOpCode = 0
                while self.precCount > 0:
                    self.precCount -= 1
                    self.nPrecOp[self.precCount] = 0
            self.display_num()
            return

        if is_bin(w):  # :234-346
            if is_bin(self.nLastCom):
                self.nOpCode = w
                if self.fPrecedence and self.nPrevOpCode != 0:
                    nprev, nx, ni = prec_of(self.nPrevOpCode), prec_of(self.nLastCom), prec_of(self.nOpCode)
                    if nx <= nprev and ni > nprev:
                        self.nPrevOpCode = 0
                return
            if self.bChangeOp:
                while True:
                    nx, ni = prec_of(w), prec_of(self.nOpCode)
                    if nx > ni and self.fPrecedence:
                        if self.precCount < MAXPRECDEPTH:
                            self.precVals[self.precCount] = self.lastVal
                            self.nPrecOp[self.precCount] = self.nOpCode
                        else:
                            self.precCount = MAXPRECDEPTH - 1
                            self.handle_error_command(w)
                        self.precCount += 1
                        break
                    self.currentVal = self.do_operation(self.nOpCode, self.currentVal, self.lastVal)
                    self.nPrevOpCode = self.nOpCode
                    if not self.bError:
                        self.display_num()
                    if self.precCount != 0 and self.nPrecOp[self.precCount - 1]:
                        self.precCount -= 1
                        self.nOpCode = self.nPrecOp[self.precCount]
                        self.lastVal = self.precVals[self.precCount]
                        continue
                    break
            self.lastVal = self.currentVal
            self.nOpCode = w
            self.bNoPrevEqu = self.bChangeOp = True
            return

        if is_unary(w):  # :349-410
            if is_bin(self.nLastCom):
                self.currentVal = self.lastVal
            if w in TRIG and self.currentVal >= Fraction(10) ** 100:  # IsCurrentTooBigForTrig :1128-1131
                self.currentVal = Fraction(0)
                self.display_error("DOMAIN")
                return
            self.currentVal = self.sci_functions(self.currentVal, w)
            if self.bError:
                return
            self.display_num()
            if self.bInv and w in INV_RESET:
                self.bInv = False
            return

        if w == IDC_CLEAR:  # :434-462
            self.lastVal = Fraction(0)
            self.bChangeOp = False
            self.openParen = 0
            self.precCount = self.nTempCom = self.nLastCom = self.nOpCode = 0
            self.nPrevOpCode = 0
            self.bNoPrevEqu = True
            self.carry = 0
            self.clear_temporary()
        elif w == IDC_CENTR:
            self.clear_temporary()
        elif w == IDC_BACK:
            if self.bRecord:
                self.input.backspace()
                self.display_num()
            else:
                self.handle_error_command(w)
        elif w == IDC_EQU:  # :488-554
            while self.openParen > 0:
                if self.bError:
                    break
                self.nTempCom = self.nLastCom
                self.process(IDC_CLOSEP)
                self.nLastCom = self.nTempCom
                self.nTempCom = w
            if not self.bNoPrevEqu:
                self.lastVal = self.currentVal
            if is_bin(self.nLastCom):
                self.currentVal = self.lastVal
            self.resolve_highest()
            while self.fPrecedence and self.precCount > 0:
                self.precCount -= 1
                self.nOpCode = self.nPrecOp[self.precCount]
                self.lastVal = self.precVals[self.precCount]
                self.bNoPrevEqu = True
                self.resolve_highest()
            if not self.bError:
                self.lastVal = self.currentVal
                self.nPrevOpCode = 0
                self.precCount = 0
            self.bChangeOp = False
        elif w in (IDC_OPENP, IDC_CLOSEP):  # :556-666
            if (
                (self.openParen >= MAXPRECDEPTH and w == IDC_OPENP)
                or (not self.openParen and w != IDC_OPENP)
                or (self.precCount >= MAXPRECDEPTH and self.nPrecOp[self.precCount - 1] != 0)
            ):
                self.handle_error_command(w)
                return
            if w == IDC_OPENP:
                if is_digit(self.nLastCom) or is_unary(self.nLastCom) or self.nLastCom in (IDC_PNT, IDC_CLOSEP):
                    self.process(IDC_MUL)
                self.parenVals[self.openParen] = self.lastVal
                self.nOp[self.openParen] = self.nOpCode if self.bChangeOp else 0
                self.openParen += 1
                if self.precCount < MAXPRECDEPTH:
                    self.nPrecOp[self.precCount] = 0
                    self.precCount += 1
                self.lastVal = Fraction(0)
                if is_bin(self.nLastCom):
                    self.currentVal = Fraction(0)
                self.nTempCom = 0
                self.nOpCode = 0
                self.bChangeOp = False
            else:
                if is_bin(self.nLastCom):
                    self.currentVal = self.lastVal
                self.currentVal = self.do_operation(self.nOpCode, self.currentVal, self.lastVal)
                self.nPrevOpCode = self.nOpCode
                self.precCount -= 1
                self.nOpCode = self.nPrecOp[self.precCount]
                while self.nOpCode:
                    self.lastVal = self.precVals[self.precCount]
                    self.currentVal = self.do_operation(self.nOpCode, self.currentVal, self.lastVal)
                    self.nPrevOpCode = self.nOpCode
                    self.precCount -= 1
                    self.nOpCode = self.nPrecOp[self.precCount]
                self.openParen -= 1
                self.lastVal = self.parenVals[self.openParen]
                self.nOpCode = self.nOp[self.openParen]
                self.bChangeOp = self.nOpCode != 0
            if not self.bError:
                self.display_num()
        elif IDM_HEX <= w <= IDM_BIN:
            self.set_radix_width(radix={IDM_HEX: 16, IDM_DEC: 10, IDM_OCT: 8, IDM_BIN: 2}[w])
        elif IDM_QWORD <= w <= IDM_BYTE:
            if self.bRecord:
                self.currentVal = self.input.to_rational(self.radix)
                self.bRecord = False
            self.set_radix_width(bits={IDM_QWORD: 64, IDM_DWORD: 32, IDM_WORD: 16, IDM_BYTE: 8}[w])
        elif IDM_DEG <= w <= IDM_GRAD:
            self.angle = {IDM_DEG: "deg", IDM_RAD: "rad", IDM_GRAD: "grad"}[w]
        elif w == IDC_SIGN:  # :699-730
            if self.bRecord:
                if self.input.try_toggle_sign(self.fInt, self.max_dec_str()):
                    self.display_num()
                else:
                    self.handle_error_command(w)
                return
            if is_bin(self.nLastCom):
                self.currentVal = self.lastVal
            self.currentVal = -self.currentVal
            self.display_num()
        elif w == IDC_RECALL:
            self.currentVal = self.mem
            self.display_num()
        elif w == IDC_MPLUS:
            self.mem = self.truncate(self.M.add(self.mem, self.currentVal))
        elif w == IDC_MMINUS:
            self.mem = self.truncate(self.M.sub(self.mem, self.currentVal))
        elif w in (IDC_STORE, IDC_MCLEAR):
            self.mem = self.truncate(self.currentVal) if w == IDC_STORE else Fraction(0)
        elif w == IDC_PI:
            if not self.fInt:
                self.currentVal = frac(2 * mpmath.pi if self.bInv else mpmath.pi)
                self.display_num()
                self.bInv = False
            else:
                self.handle_error_command(w)
        elif w == IDC_FE:
            self.nFE = SCI if self.nFE == FLOAT else FLOAT
            self.display_num()
        elif w == IDC_EXP:
            if self.bRecord and not self.fInt and self.input.try_begin_exponent():
                self.display_num()
            else:
                self.handle_error_command(w)
        elif w == IDC_PNT:
            if self.nLastCom == IDC_CLOSEP:
                raise GuardError("'.' after ')' not in the case list")
            if self.bRecord and not self.fInt and self.input.try_add_decimal_pt():
                self.display_num()
            else:
                self.handle_error_command(w)
        elif w == IDC_INV:
            self.bInv = not self.bInv

    # -- CalculatorManager memory (CalculatorManager.cpp:326-445) --
    def m_store(self):
        if self.bError:
            return
        self.process(IDC_STORE)
        self.mem_list.insert(0, self.mem)

    def m_add(self):
        if self.bError:
            return
        if not self.mem_list:
            self.m_store()
        else:
            self.mem = self.mem_list[0]
            self.process(IDC_MPLUS)
            self.mem_list[0] = self.mem

    def m_sub(self):
        if self.bError:
            return
        if not self.mem_list:
            self.m_store()
            self.m_sub()
            self.m_sub()
        else:
            self.mem = self.mem_list[0]
            self.process(IDC_MMINUS)
            self.mem_list[0] = self.mem

    def m_recall(self):
        if self.bError or not self.mem_list:
            return
        self.mem = self.mem_list[0]
        self.process(IDC_RECALL)

    def m_clear(self):
        if not self.mem_list:
            return  # MC is disabled with nothing stored (r11/calculator.md 2.17)
        self.mem_list = []
        self.process(IDC_MCLEAR)

    # -- the phone's keys (calc-keys.md) --
    def key(self, k):
        simple = {
            "decimal": IDC_PNT, "add": IDC_ADD, "subtract": IDC_SUB, "multiply": IDC_MUL, "divide": IDC_DIV,
            "equals": IDC_EQU, "negate": IDC_SIGN, "percent": IDC_PERCENT, "sqrt": IDC_SQRT,
            "reciprocal": IDC_REC, "clear_entry": IDC_CENTR, "clear": IDC_CLEAR, "backspace": IDC_BACK,
            "exp": IDC_EXP, "mod": IDC_MOD, "pi": IDC_PI, "factorial": IDC_FAC, "lparen": IDC_OPENP,
            "rparen": IDC_CLOSEP, "fe": IDC_FE, "or": IDC_OR, "xor": IDC_XOR, "not": IDC_COM, "and": IDC_AND,
            "radix_hex": IDM_HEX, "radix_dec": IDM_DEC, "radix_oct": IDM_OCT, "radix_bin": IDM_BIN,
        }
        # The APP's layer over the engine (StandardCalculatorViewModel.cs:1409-1420, IsRecoverableCommand :1997-2012):
        # a key pressed while an error shows first sends CLEAR, and only a recoverable key — a digit, '.', A-F —
        # then goes on to the engine; any other key is spent on the clear. (The engine alone, scicomm.cpp:131-147,
        # would ignore everything but C / CE; a person presses the app's keys, and the app recovers. Corrected
        # 2026-09-23 after the engine port and this oracle disagreed on standard-067 — settled by this source.)
        if self.bError and k not in ("clear", "clear_entry", "mc", "mr", "ms", "mplus", "mminus", "inv", "hyp", "angle", "word",
                                     "radix_hex", "radix_dec", "radix_oct", "radix_bin", "fe"):
            self.process(IDC_CLEAR)
            if not ((len(k) == 1 and k in HEXDIGITS) or k == "decimal"):
                return
        if len(k) == 1 and k in HEXDIGITS:
            self.process(IDC_0 + HEXDIGITS.index(k))
        elif k in simple:
            self.process(simple[k])
        elif k == "ms":
            self.m_store()
        elif k == "mplus":
            self.m_add()
        elif k == "mminus":
            self.m_sub()
        elif k == "mr":
            self.m_recall()
        elif k == "mc":
            self.m_clear()
        elif k == "inv":
            self.ui_shift = not self.ui_shift
        elif k == "hyp":
            self.ui_hyp = not self.ui_hyp
        elif k == "square":
            self.process(IDC_CUB if self.ui_shift else IDC_SQR)
        elif k == "pow":
            self.process(IDC_ROOT if self.ui_shift else IDC_PWR)
        elif k == "pow10":
            if self.ui_shift:  # e^x = INV + LN (CalculatorManager.cpp CommandPOWE)
                self.process(IDC_INV)
                self.process(IDC_LN)
            else:
                self.process(IDC_POW10)
        elif k == "log":
            self.process(IDC_LN if self.ui_shift else IDC_LOG)
        elif k in ("sin", "cos", "tan"):
            base = {"sin": IDC_SIN, "cos": IDC_COS, "tan": IDC_TAN}[k]
            if self.ui_hyp:
                base += 3
            if self.ui_shift:
                self.process(IDC_INV)
            self.process(base)
            self.ui_shift = self.ui_hyp = False  # the trig pick resets both (CalculatorScientificOperators.xaml.cs:81-87)
        elif k == "angle":
            self.process({"deg": IDM_RAD, "rad": IDM_GRAD, "grad": IDM_DEG}[self.angle])
        elif k == "word":
            self.process({64: IDM_DWORD, 32: IDM_WORD, 16: IDM_BYTE, 8: IDM_QWORD}[self.bits])
        elif k in ("lsh", "rsh"):
            if self.ui_shift:
                self.process(IDC_ROL if k == "lsh" else IDC_ROR)
            else:
                self.process(IDC_LSHF if k == "lsh" else IDC_RSHF)
        else:
            raise ValueError("unknown key %r" % k)


def run_keys(mode, keys, setup=""):
    opts = dict(kv.split("=", 1) for kv in setup.split()) if setup else {}
    c = Calc(mode, angle=opts.get("angle", "deg"), radix=opts.get("radix", "dec"), word=opts.get("word", "qword"))
    for k in keys.split():
        c.key(k)
    return c.display


# ----------------------------------------------------------------------------------------------------------------
# 5. Unit converter (CalcManager/UnitConverter.cpp, UnitConverterDataLoader.cs, UnitConverterViewModel.cs)
# ----------------------------------------------------------------------------------------------------------------

# name (Resources.resw en-US UnitName_*) -> factor (UnitConverterDataLoader.cs:476-667), per category
CONV = {
    "Volume": {"Milliliters": 1, "Cubic centimeters": 1, "Liters": 1000, "Cubic meters": 1000000, "Teaspoons (US)": 4.92892159375,
               "Tablespoons (US)": 14.78676478125, "Fluid ounces (US)": 29.5735295625, "Cups (US)": 236.588237,
               "Pints (US)": 473.176473, "Quarts (US)": 946.352946, "Gallons (US)": 3785.411784,
               "Cubic inches": 16.387064, "Cubic feet": 28316.846592, "Gallons (UK)": 4546.09},
    "Length": {"Inches": 0.0254, "Feet": 0.3048, "Yards": 0.9144, "Miles": 1609.344, "Millimeters": 0.001,
               "Centimeters": 0.01, "Meters": 1, "Kilometers": 1000, "Nautical miles": 1852},
    "Weight and Mass": {"Kilograms": 1, "Grams": 0.001, "Pounds": 0.45359237, "Ounces": 0.028349523125,
                        "Stone": 6.35029318, "Metric tonnes": 1000, "Carats": 0.0002},
    "Energy": {"Thermal calories": 4.184, "Food calories": 4184, "British thermal units": 1055.056, "Kilojoules": 1000,
               "Kilowatt-hours": 3600000, "Joules": 1, "Foot-pounds": 1.3558179483314},
    "Area": {"Acres": 4046.8564224, "Square meters": 1, "Square feet": 0.09290304, "Square yards": 0.83612736,
             "Square miles": 2589988.110336, "Square kilometers": 1000000, "Hectares": 10000},
    "Speed": {"Centimeters per second": 1, "Feet per second": 30.48, "Kilometers per hour": 27.777777777777777777778,
              "Knots": 51.44, "Mach": 34030, "Meters per second": 100, "Miles per hour": 44.7},
    "Time": {"Days": 86400, "Seconds": 1, "Weeks": 604800, "Years": 31557600, "Milliseconds": 0.001,
             "Minutes": 60, "Hours": 3600},
    "Power": {"BTUs/minute": 17.58426666666667, "Foot-pounds/minute": 0.0225969658055233, "Watts": 1, "Kilowatts": 1000,
              "Horsepower (US)": 745.69987158227022},
    "Data": {"Bits": 0.000000125, "Nibble": 0.0000005, "Bytes": 0.000001, "Kilobytes": 0.001, "Megabytes": 1,
             "Gigabytes": 1000, "Terabytes": 1000000, "Kilobits": 0.000125, "Megabits": 0.125, "Gigabits": 125,
             "Kibibytes": 0.001024, "Mebibytes": 1.048576, "Gibibytes": 1073.741824},
    "Pressure": {"Atmospheres": 1, "Bars": 0.9869232667160128, "Kilopascals": 0.0098692326671601,
                 "Millimeters of mercury": 0.0013155687145324, "Pascals": 9.869232667160128e-6,
                 "Pounds per square inch": 0.068045961016531},
    "Angle": {"Degrees": 1, "Radians": 57.29577951308233, "Gradians": 0.9},
}
# Temperature: explicit (ratio, offset, offsetFirst) (UnitConverterDataLoader.cs:669-689)
TEMP = {
    ("Celsius", "Celsius"): (1, 0, False), ("Celsius", "Fahrenheit"): (1.8, 32, False),
    ("Celsius", "Kelvin"): (1, 273.15, False),
    ("Fahrenheit", "Celsius"): (0.55555555555555555555555555555556, -32, True),
    ("Fahrenheit", "Fahrenheit"): (1, 0, False), ("Fahrenheit", "Kelvin"): (0.55555555555555555555555555555556, 459.67, True),
    ("Kelvin", "Celsius"): (1, -273.15, True), ("Kelvin", "Fahrenheit"): (1.8, -459.67, False), ("Kelvin", "Kelvin"): (1, 0, False),
}


def trim_trailing_zeros(s):  # NumberFormattingUtils.cpp:12-28
    if "." not in s:
        return s
    s = s.rstrip("0")
    if s.endswith("."):
        s = s[:-1]
    return s


def converter_display(category, frm, to, keys):
    cur = "0"  # UnitConverter::SendCommand (UnitConverter.cpp:394-537)
    has_dec = False
    for k in keys.split():
        clear_front = cur == "0"
        clear_back = (has_dec and len(cur) - 1 >= 15) or (not has_dec and len(cur) >= 15)
        if k == "decimal":
            clear_front = clear_back = False
            if not has_dec:
                cur += "."
                has_dec = True
        elif k.isdigit():
            cur += k
        else:
            raise ValueError(k)
        if clear_front:
            cur = cur[1:]
        if clear_back:
            cur = cur[:-1]
    if category == "Temperature":
        ratio, offset, offset_first = TEMP[(frm, to)]
    else:
        f = CONV[category]
        ratio, offset, offset_first = float(f[frm]) / float(f[to]), 0.0, False
    if ratio == 1.0 and offset == 0.0:  # UnitConverter.cpp:877-882
        return localize(trim_trailing_zeros(cur))
    v = float(cur)
    ret = (v + offset) * ratio if offset_first else (v * ratio) + offset  # :593-603
    num_pre = 1 if ret == 0 else int(1 + max(0.0, math.log10(abs(ret))))
    if num_pre > 15 or (ret != 0 and abs(ret) < 1e-14):
        raise GuardError("scientific converter output")
    sig = len(trim_trailing_zeros(cur).replace(".", "").replace("-", ""))
    if abs(ret) < 1e-6:
        prec = 15
    else:
        nd = max(7, min(15, sig))
        prec = nd - num_pre if nd > num_pre else 0
    exact = Decimal(ret)
    q = exact.scaleb(prec)
    if q - int(q) == Decimal("0.5") or q - int(q) == Decimal("-0.5"):
        raise GuardError("exact tie at the %.*f rounding digit")
    s = trim_trailing_zeros("%.*f" % (prec, ret))  # RoundSignificantDigits = fixed, precision(n)
    return localize(s)


def localize(s):  # DecimalFormatter.Format with FractionDigits = digits after '.', IsGrouped (UnitConverterViewModel.cs:1202-1330)
    frac_digits = len(s.split(".")[1]) if "." in s else 0
    return "{:,.{}f}".format(float(s), frac_digits)


# ----------------------------------------------------------------------------------------------------------------
# 6. Date calculation (DateCalculator.cs, DateCalculatorViewModel.cs)
# ----------------------------------------------------------------------------------------------------------------

DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October",
          "November", "December"]


def days_in_month(y, m):
    nxt = date(y + (m == 12), m % 12 + 1, 1)
    return (nxt - date(y, m, 1)).days


def add_months(d, n):  # Windows.Globalization.Calendar.AddMonths: clamp to the month's last day
    t = d.year * 12 + (d.month - 1) + n
    y, m = divmod(t, 12)
    m += 1
    return date(y, m, min(d.day, days_in_month(y, m)))


def add_years(d, n):
    return add_months(d, 12 * n)


def adjust(d, unit, n):
    if unit == "year":
        return add_years(d, n)
    if unit == "month":
        return add_months(d, n)
    return d + timedelta(days=7 * n)


def date_difference(d1, d2, units):  # DateCalculator.cs:133-233
    start, end = (d1, d2) if d1 < d2 else (d2, d1)
    pivot = start
    days = (end - start).days
    out = {"year": 0, "month": 0, "week": 0, "day": 0}
    if units != ("day",):
        dim = days_in_month(start.year, start.month)
        diy = sum(days_in_month(end.year, m) for m in range(1, 13))
        per = {"year": diy, "month": dim, "week": 7}
        for unit in ("year", "month", "week"):
            if unit not in units:
                continue
            temp = pivot
            n = days // per[unit]
            if n:
                pivot = adjust(temp, unit, n)
            hit = False
            while True:
                dd = (end - pivot).days
                if dd < 0:
                    n -= 1
                    pivot = adjust(temp, unit, n)
                    hit = True
                elif dd > 0:
                    if hit:
                        break
                    pivot = adjust(temp, unit, n + 1)
                    n += 1
                if dd == 0:
                    break
            out[unit] = n
            days = (end - pivot).days
    out["day"] = days
    return out


def plural(n, one, many):
    return "%d %s" % (n, one if n == 1 else many)


def date_diff_text(d1, d2):  # DateCalculatorViewModel.cs:229-332
    in_days = date_difference(d1, d2, ("day",))
    full = date_difference(d1, d2, ("year", "month", "week", "day"))
    if in_days["day"] == 0:
        return "Same dates"  # Resources.resw:2975
    days_str = plural(in_days["day"], "day", "days")
    if full["year"] == 0 and full["month"] == 0 and full["week"] == 0:
        return days_str
    parts = []
    if full["year"] > 0:
        parts.append(plural(full["year"], "year", "years"))
    if full["month"] > 0:
        parts.append(plural(full["month"], "month", "months"))
    if full["week"] > 0:
        parts.append(plural(full["week"], "week", "weeks"))
    if full["day"] > 0 or not parts:
        parts.append(plural(full["day"], "day", "days"))
    return ", ".join(parts) + " | " + days_str  # en-US list separator ", "


def date_add_text(d, years, months, days, subtract):  # DateCalculator.cs:73-131
    if not subtract:
        if years:
            d = add_years(d, years)
        if months:
            d = add_months(d, months)
        if days:
            d = d + timedelta(days=days)
    else:
        if days:
            d = d - timedelta(days=days)
        if months:
            d = add_months(d, -months)
        if years:
            d = add_years(d, -years)
    return "%s, %s %d, %d" % (DAYS[d.weekday()], MONTHS[d.month - 1], d.day, d.year)


# ----------------------------------------------------------------------------------------------------------------
# 7. The cases
# ----------------------------------------------------------------------------------------------------------------

S = "src/CalcManager/"
C_PREC = S + "CalculatorManager.cpp:164,183,201 (precedence) +:170,189,207 (precision)"
C_STDIMM = S + "CalculatorManager.cpp:164; CEngine/scicomm.cpp:274-345 (Standard executes each operator immediately)"
C_SCIPREC = S + "CalculatorManager.cpp:183; CEngine/scicomm.cpp:274-345 (precedence stack)"
C_ADD = S + "CEngine/scioper.cpp:82-92 + Ratpack/rat.cpp:188-233 (exact rational add/sub)"
C_DIV = S + "CEngine/scioper.cpp:94-131 + Ratpack/rat.cpp:135-164"
C_FMT16 = S + "Ratpack/conv.cpp:1076-1264 (NumberToString, precision 16: CalculatorManager.h:24)"
C_FMT32 = S + "Ratpack/conv.cpp:1076-1264 (NumberToString, precision 32: CalculatorManager.h:25)"
C_ESW = S + "Ratpack/conv.cpp:1083-1087,1242-1261 (integer digits > precision -> d.ddde+N)"
C_SMALL = S + "Ratpack/conv.cpp:28,1119-1135 (more than 2 zeros after the point -> e-notation)"
C_GROUP = S + "CEngine/scidisp.cpp:246-265,286-384 + CEngine/calc.cpp:20-21 (\",\" every 3)"
C_PCT = S + "CEngine/scifunc.cpp:98-111 (X op Y% = X op X*Y/100; x and / use Y/100)"
C_EQU = S + "CEngine/scicomm.cpp:486-554,884-923 (= repeats the last operation with the held operand)"
C_INPUT = S + "CEngine/CalcInput.cpp:59-170 (digit limit = precision; leading zeros ignored)"
C_BACK = S + "CEngine/scicomm.cpp:472-484 + CEngine/CalcInput.cpp:211-252"
C_CLR = S + "CEngine/scicomm.cpp:434-470 (C clears all, CE only the entry)"
C_SIGN = S + "CEngine/scicomm.cpp:699-730 + CEngine/CalcInput.cpp:28-57"
C_MEM = S + "CalculatorManager.cpp:326-445 + CEngine/scicomm.cpp:732-769 (memory slot 0 = newest)"
C_ERRSTATE = S + "CEngine/scicomm.cpp:131-147 (in error only C / CE act)"
C_DIV0 = S + "Ratpack/rat.cpp:144-148 CALC_E_DIVIDEBYZERO -> src/Calculator/Resources/en-US/CEngineStrings.resw:416-418"
C_INDEF = S + "Ratpack/rat.cpp:152-159 CALC_E_INDEFINITE -> src/Calculator/Resources/en-US/CEngineStrings.resw:128-130"
C_DOMAIN_SQRT = S + "Ratpack/exp.cpp:486-525 (negative base, even root) CALC_E_DOMAIN -> src/Calculator/Resources/en-US/CEngineStrings.resw:124-126"
C_OVF = S + "CEngine/scidisp.cpp:23,117-120,148-151 (exponent over 4 digits) -> src/Calculator/Resources/en-US/CEngineStrings.resw:136-138"
C_SNAP = S + "Ratpack/rat.cpp:188-197,346-388 + Ratpack/support.cpp:175-176 (a-b smaller than max(|a|,|b|)*10^-P snaps to 0)"
C_SQRT = S + "CEngine/scifunc.cpp:205-207 + Ratpack/exp.cpp:305-412 (exact integer roots)"
C_TRIG = S + "CEngine/scifunc.cpp:113-146 + Ratpack/trans.cpp:19-35,101-125,196-228,266-290 (DEG/GRAD scaled to pi, RAD as is)"
C_ITRIG = S + "CEngine/scifunc.cpp:113-146 + Ratpack/itrans.cpp (asin/acos/atan, ascalerat)"
C_HYP = S + "CEngine/scifunc.cpp:120-153 + Ratpack/transh.cpp, Ratpack/itransh.cpp"
C_LOG = S + "CEngine/scifunc.cpp:214-228 + CEngine/RationalMath.cpp (Log10 = ln/ln10) + Ratpack/exp.cpp:155-233"
C_POW = S + "CEngine/scioper.cpp:153-159 + Ratpack/exp.cpp:278-529"
C_FACT = S + "CEngine/scifunc.cpp:230-232 + Ratpack/fact.cpp:187-246"
C_PAREN = S + "CEngine/scicomm.cpp:556-666 (+ implicit x: :576-581 before '(', :196-227 after ')')"
C_EXPK = S + "CEngine/scicomm.cpp:825-832 + CEngine/CalcInput.cpp:197-209,273-325"
C_PI = S + "CEngine/scicomm.cpp:770-781 + Ratpack/ratconst.h:178-200 (pi to 2e-47)"
C_MODSCI = S + "CEngine/scioper.cpp:94-151 + Ratpack/logic.cpp:229-253 (sign of the divisor; X mod 0 = X)"
C_FE = S + "CEngine/scicomm.cpp:819-823 + Ratpack/conv.cpp:1162-1190,1242-1255"
C_PROGFMT = S + "CEngine/scicomm.cpp:1161-1193 + CEngine/scidisp.cpp:54-76,246-265 (+ BIN pad StandardCalculatorViewModel.cs:1941-1958)"
C_RADIX = S + "CEngine/scicomm.cpp:669-677 + CEngine/sciset.cpp:12-51"
C_WORD = S + "CEngine/scicomm.cpp:679-691 + CEngine/sciset.cpp:12-51 + CEngine/scidisp.cpp:54-76 (two's complement at the word size)"
C_WRAP = S + "CEngine/scidisp.cpp:54-76,106-110 (results wrap to the word size) + CEngine/scicomm.cpp:1178-1183"
C_BITS = S + "CEngine/scioper.cpp:19-29 + Ratpack/logic.cpp:74-135"
C_NOT = S + "CEngine/scifunc.cpp:38-48 (NOT = x XOR word mask)"
C_SHIFT = S + "CEngine/scioper.cpp:39-80 + Ratpack/logic.cpp:20-62"
C_NORES = S + "CEngine/scioper.cpp:73-77 CALC_E_NORESULT (Ratpack/CalcErr.h:87) -> src/Calculator/Resources/en-US/CEngineStrings.resw:140-142"
C_ROT = S + "CEngine/scifunc.cpp:50-96 (RoL / RoR by one bit within the word)"
C_PDIV = S + "CEngine/scioper.cpp:94-131 + CEngine/scidisp.cpp:106-110 (integer truncation)"
C_PMOD = S + "CEngine/scioper.cpp:132-143 + Ratpack/logic.cpp:192-222 (remainder, sign of the dividend)"
C_PINPUT = S + "CEngine/CalcInput.cpp:103-167 + CEngine/sciset.cpp:144-164 (max digits per word size)"
C_UC = "src/CalcManager/UnitConverter.cpp:593-603,865-925 + src/CalcManager/NumberFormattingUtils.cpp:12-68 + src/Calculator.ViewModels/UnitConverterViewModel.cs:1202-1330"
C_UCT = "src/Calculator.ViewModels/DataLoaders/UnitConverterDataLoader.cs"
C_DD = "src/Calculator.ViewModels/Common/DateCalculator.cs:133-233 + src/Calculator.ViewModels/DateCalculatorViewModel.cs:229-332 + src/Calculator/Resources/en-US/Resources.resw:2963-2987"
C_DA = "src/Calculator.ViewModels/Common/DateCalculator.cs:73-131 + src/Calculator.Tests/DateCalculatorTests.cs:44-104 (month-end clamp) + src/Calculator.ViewModels/DateCalculatorViewModel.cs:292-297 (longdate)"
C_TESS = "docs/plan/phase-15-inbox-clock-calculator-recorder.md:436-452 (T15-2) + docs/plan/review/2026-09-23-phase15-r3-triage.md:62 (T15-52); engine: " + S + "CalculatorManager.cpp:183 (Scientific precedence, precision 32)"


def K(s):
    """Key string shorthand: digits written together are split into single keys ('255' -> '2 5 5')."""
    out = []
    for tok in s.split():
        if re.fullmatch(r"[0-9A-F]+", tok):
            out.extend(tok)
        else:
            out.append(tok)
    return " ".join(out)


STANDARD = [
    # (comment, setup, keys, cite, E11 quote or None)
    ("E11: `2 + 3 × 4 =` -> 20 in Standard (immediate execution)", "", K("2 add 3 multiply 4 equals"), C_STDIMM),
    ("immediate: the display after the second operator already holds 2 + 3", "", K("2 add 3 multiply"), C_STDIMM),
    ("immediate: 10 - 4 / 2 = is (10-4)/2", "", K("10 subtract 4 divide 2 equals"), C_STDIMM),
    ("chain 3 + 4 + 5 = (the second + shows 7)", "", K("3 add 4 add 5 equals"), C_STDIMM),
    ("E11: `0.1 + 0.2` -> 0.3 (exact rationals)", "", K("0 decimal 1 add 0 decimal 2 equals"), C_ADD),
    ("decimal entry without a leading zero", "", K("decimal 5 add decimal 2 5 equals"), C_ADD),
    ("1.5 x 2", "", K("1 decimal 5 multiply 2 equals"), C_ADD),
    ("subtraction below zero", "", K("9 subtract 12 equals"), C_ADD),
    ("1 / 3 at 16 digits", "", K("1 divide 3 equals"), C_DIV + "; " + C_FMT16),
    ("2 / 3 rounds the 16th digit up", "", K("2 divide 3 equals"), C_DIV + "; " + C_FMT16),
    ("1 / 3 x 3 = 1 exactly (rational, not binary floating point)", "", K("1 divide 3 multiply 3 equals"), C_DIV),
    ("1 / 7", "", K("1 divide 7 equals"), C_DIV + "; " + C_FMT16),
    ("1 / 8 terminates", "", K("1 divide 8 equals"), C_DIV),
    ("1 / 300: two zeros after the point stay fixed, 14 significant digits", "", K("1 divide 300 equals"), C_SMALL),
    ("1 / 3000: three zeros after the point -> e-notation", "", K("1 divide 3000 equals"), C_SMALL),
    ("E11: `80 + 15 % =` -> 92", "", K("80 add 15 percent equals"), C_PCT),
    ("percent shows X*Y/100 before =", "", K("80 add 15 percent"), C_PCT),
    ("80 - 15 % = 68", "", K("80 subtract 15 percent equals"), C_PCT),
    ("x uses Y/100: 200 x 10 % = 20", "", K("200 multiply 10 percent equals"), C_PCT),
    ("/ uses Y/100: 50 / 25 % = 200", "", K("50 divide 25 percent equals"), C_PCT),
    ("% with no pending operator is Y * 0/100 = 0", "", K("15 percent"), C_PCT),
    ("E11: `2 × = =` -> 8", "", K("2 multiply equals equals"), C_EQU),
    ("repeated = adds the held operand again", "", K("5 add 3 equals equals equals"), C_EQU),
    ("repeated = subtracts again", "", K("10 subtract 2 equals equals"), C_EQU),
    ("2 x 3 = = multiplies by 3 again", "", K("2 multiply 3 equals equals"), C_EQU),
    ("5 + = uses 5 as the second operand", "", K("5 add equals"), S + "CEngine/scicomm.cpp:351-359,509-514"),
    ("Standard 2 + 3 x 4 = = repeats x4", "", K("2 add 3 multiply 4 equals equals"), C_EQU),
    ("changing the operator: the last one wins", "", K("5 add multiply 3 equals"), S + "CEngine/scicomm.cpp:236-261"),
    ("digit grouping while typing", "", K("1234567"), C_GROUP),
    ("grouping with a fraction (integer part only)", "", K("1234 decimal 5678"), C_GROUP),
    ("grouping of a negative number", "", K("1234567 negate"), C_GROUP + "; " + C_SIGN),
    ("the first operator shrinks 1234.50 to 1,234.5", "", K("1234 decimal 5 0 add"), S + "CEngine/scicomm.cpp:150-160; " + C_FMT16),
    ("16-digit entry limit: the 17th digit is refused", "", K("12345678901234567"), C_INPUT),
    ("16 fractional digits after 0.", "", K("0 decimal 1234567890123456 7"), C_INPUT),
    ("leading zeros are ignored", "", K("0 0 5"), C_INPUT),
    ("a second decimal point is refused", "", K("1 decimal 2 decimal 3"), S + "CEngine/CalcInput.cpp:172-190"),
    ("99999999 x 99999999 fits 16 digits", "", K("99999999 multiply 99999999 equals"), C_FMT16 + "; " + C_GROUP),
    ("E11: integer part past 16 digits -> e-notation (1.e+16)", "", K("9999999999999999 add 1 equals"), C_ESW),
    ("E11: 18 integer digits -> 16 significant digits in e-notation", "", K("999999999 multiply 999999999 equals"), C_ESW),
    ("e-notation keeps 16 significant digits, rounded", "", K("1234567890123456 multiply 1000 equals"), C_ESW),
    ("tiny difference snaps to 0 at 16 digits", "", K("1000000000000000 add 0 decimal 0 0 0 0 1 subtract 1000000000000000 equals"), C_SNAP),
    ("backspace removes the last digit", "", K("123 backspace"), C_BACK),
    ("backspace to empty shows 0", "", K("123 backspace backspace backspace"), C_BACK),
    ("backspace keeps the typed decimal point", "", K("1 decimal 5 backspace"), C_BACK),
    ("backspace over the decimal point", "", K("1 decimal backspace"), C_BACK),
    ("backspace after = does nothing", "", K("2 add 3 equals backspace"), C_BACK),
    ("CE clears only the entry", "", K("5 add 3 clear_entry 2 equals"), C_CLR),
    ("CE on a typed number shows 0", "", K("123 clear_entry"), C_CLR),
    ("C clears the pending operation", "", K("5 add 3 clear 2 equals"), C_CLR),
    ("negate while typing", "", K("5 negate"), C_SIGN),
    ("negate twice", "", K("5 negate negate"), C_SIGN),
    ("negate a result", "", K("2 add 3 equals negate"), C_SIGN),
    ("negate of an empty entry stays 0", "", K("0 negate"), C_SIGN),
    ("negative operand", "", K("5 negate add 3 equals"), C_SIGN),
    ("negate a decimal", "", K("1 decimal 5 negate"), C_SIGN),
    ("sqrt of a perfect square is exact", "", K("81 sqrt"), C_SQRT),
    ("sqrt 2 at 16 digits", "", K("2 sqrt"), C_SQRT + "; " + C_FMT16),
    ("E11: `√−1` (Standard) -> Invalid input", "", K("1 negate sqrt"), C_DOMAIN_SQRT),
    ("x^2", "", K("12 square"), S + "CEngine/scifunc.cpp:201-203"),
    ("x^2 of a decimal", "", K("0 decimal 5 square"), S + "CEngine/scifunc.cpp:201-203"),
    ("1/x", "", K("4 reciprocal"), S + "CEngine/scifunc.cpp:197-199"),
    ("1/x of 3 at 16 digits", "", K("3 reciprocal"), S + "CEngine/scifunc.cpp:197-199; " + C_FMT16),
    ("1/x of 0", "", K("0 reciprocal"), S + "CEngine/scifunc.cpp:197-199 + " + C_DIV0),
    ("a function right after an operator uses the first operand: 9 + sqrt = 12", "", K("9 add sqrt equals"), S + "CEngine/scicomm.cpp:351-359"),
    ("E11: `1 ÷ 0` -> Cannot divide by zero", "", K("1 divide 0 equals"), C_DIV0),
    ("E11: `0 ÷ 0` -> Result is undefined", "", K("0 divide 0 equals"), C_INDEF),
    ("a digit after an error clears it and starts a number (the app recovers)", "", K("1 divide 0 equals 5 add"), C_ERRSTATE + "; Calculator.ViewModels/StandardCalculatorViewModel.cs:1409-1420,1997-2012"),
    ("C leaves the error state", "", K("1 divide 0 equals clear 5"), C_ERRSTATE),
    ("CE acts as C in the error state", "", K("1 divide 0 equals clear_entry"), C_ERRSTATE),
    ("Overflow in Standard: (10^15)^(2^10)", "", K("1000000000000000 " + "square " * 10), C_OVF),
    ("MS / MR", "", K("mc 5 ms clear mr"), C_MEM),
    ("MS ends the entry: the next digit starts a new number", "", K("mc 5 ms 3"), C_MEM),
    ("M+ adds the display to slot 0", "", K("mc 5 ms 3 mplus mr"), C_MEM),
    ("M- subtracts the display from slot 0", "", K("mc 5 ms 3 mminus mr"), C_MEM),
    ("M+ on an empty memory stores first", "", K("mc 5 mplus mplus mr"), S + "CalculatorManager.cpp:371-394"),
    ("M- on an empty memory stores -x", "", K("mc 5 mminus mr"), S + "CalculatorManager.cpp:409-432"),
    ("MS inserts at the front: MR recalls the newest", "", K("mc 5 ms 7 ms mr"), S + "CalculatorManager.cpp:326-345"),
    ("MR replaces the typed entry", "", K("mc 5 ms 3 mr"), C_MEM),
    ("MC ends the entry like MS (record off)", "", K("mc 5 ms 7 mc 3"), S + "CalculatorManager.cpp:440-445 + CEngine/scicomm.cpp:150-160"),
    ("memory recall into an operation", "", K("mc 2 add 3 equals ms clear mr multiply 2 equals"), C_MEM),
]

SCIENTIFIC = [
    ("E11: `2 + 3 × 4 =` -> 14 in Scientific (precedence)", "", K("2 add 3 multiply 4 equals"), C_SCIPREC),
    ("precedence: the x waits, the display still shows 3", "", K("2 add 3 multiply"), C_SCIPREC),
    ("2 x 3 + 4", "", K("2 multiply 3 add 4 equals"), C_SCIPREC),
    ("2 + 3 x 4 - 5", "", K("2 add 3 multiply 4 subtract 5 equals"), C_SCIPREC),
    ("x^y binds tighter than x: 1 + 2 x 3^2", "", K("1 add 2 multiply 3 pow 2 equals"), C_SCIPREC + "; src/CalcManager/CEngine/scicomm.cpp:31-58 (NPrecedenceOfOp)"),
    ("Scientific 2 + 3 x 4 = = repeats the last resolved +12", "", K("2 add 3 multiply 4 equals equals"), C_EQU),
    ("parentheses", "", K("lparen 2 add 3 rparen multiply 4 equals"), C_PAREN),
    ("parentheses on the right", "", K("2 multiply lparen 3 add 4 rparen equals"), C_PAREN),
    ("= closes open parentheses", "", K("lparen 1 add 2 equals"), C_PAREN + "; src/CalcManager/CEngine/scicomm.cpp:488-501 (= closes open parentheses)"),
    ("implicit x before (", "", K("2 lparen 3 add 4 rparen equals"), C_PAREN),
    ("implicit x after )", "", K("lparen 2 add 3 rparen 4 equals"), C_PAREN),
    ("1 / 3 at 32 digits", "", K("1 divide 3 equals"), C_DIV + "; " + C_FMT32),
    ("2 / 3 rounds the 32nd digit up", "", K("2 divide 3 equals"), C_DIV + "; " + C_FMT32),
    ("0.1 + 0.2 = 0.3", "", K("0 decimal 1 add 0 decimal 2 equals"), C_ADD),
    ("1 / 3000 -> e-notation at 32 digits", "", K("1 divide 3000 equals"), C_SMALL),
    ("32-digit entry limit", "", K("123456789012345678901234567890123"), C_INPUT),
    ("20 typed digits are grouped", "", K("12345678901234567890"), C_GROUP),
    ("32 nines + 1 -> 1.e+32", "", K("9" * 32 + " add 1 equals"), C_ESW),
    ("1e31 still fits 32 digits", "", K("1 exp 31 equals"), C_ESW + "; " + C_EXPK),
    ("E11: `200 !` (Scientific) -> 7.8865786736479050355236321393219e+374", "", K("200 factorial"), C_FACT + "; " + C_ESW),
    ("20!", "", K("20 factorial"), C_FACT + "; " + C_GROUP),
    ("50! -> e-notation", "", K("50 factorial"), C_FACT + "; " + C_ESW),
    ("0! = 1", "", K("0 factorial"), C_FACT),
    ("(-1)! -> Invalid input", "", K("1 negate factorial"), S + "Ratpack/fact.cpp:207-212 CALC_E_DOMAIN -> src/Calculator/Resources/en-US/CEngineStrings.resw:124-126"),
    ("3250! -> Overflow (the factorial limit is 3249)", "", K("3250 factorial"), S + "Ratpack/fact.cpp:193-198 + Ratpack/support.cpp:168-170 -> src/Calculator/Resources/en-US/CEngineStrings.resw:136-138"),
    ("E11: sin 30 in DEG -> 0.5", "angle=deg", K("30 sin"), C_TRIG),
    ("E11: sin 30 in RAD -> -0.9880…", "angle=rad", K("30 sin"), C_TRIG),
    ("E11: sin 30 in GRAD -> 0.4540…", "angle=grad", K("30 sin"), C_TRIG),
    ("the angle key cycles DEG -> RAD", "angle=deg", K("30 angle sin"), C_TRIG + " + CEngine/scicomm.cpp:693-697"),
    ("cos 60 DEG", "angle=deg", K("60 cos"), C_TRIG),
    ("tan 45 DEG", "angle=deg", K("45 tan"), C_TRIG),
    ("sin 180 DEG snaps to 0", "angle=deg", K("180 sin"), S + "Ratpack/trans.cpp:86-93 (|x| <= 10^-P -> 0)"),
    ("cos 90 DEG snaps to 0", "angle=deg", K("90 cos"), S + "Ratpack/trans.cpp:181-188"),
    ("tan 90 DEG -> Invalid input", "angle=deg", K("90 tan"), S + "Ratpack/trans.cpp:242-257 CALC_E_DOMAIN -> src/Calculator/Resources/en-US/CEngineStrings.resw:124-126"),
    ("sin of a negative angle", "angle=deg", K("30 negate sin"), C_TRIG),
    ("sin 1 RAD", "angle=rad", K("1 sin"), C_TRIG),
    ("cos 100 GRAD snaps to 0", "angle=grad", K("100 cos"), S + "Ratpack/trans.cpp:181-188"),
    ("sin of 1e100 -> Invalid input (too big for trig)", "angle=deg", K("1 exp 100 sin"), S + "CEngine/scicomm.cpp:373-382,1128-1131 + CEngine/calc.cpp:106"),
    ("asin 0.5 DEG = 30", "angle=deg", K("0 decimal 5 inv sin"), C_ITRIG),
    ("acos 1 DEG = 0", "angle=deg", K("1 inv cos"), C_ITRIG),
    ("atan 1 DEG = 45", "angle=deg", K("1 inv tan"), C_ITRIG),
    ("atan 1 RAD = pi/4", "angle=rad", K("1 inv tan"), C_ITRIG),
    ("atan 1 GRAD = 50", "angle=grad", K("1 inv tan"), C_ITRIG),
    ("acos 0.5 DEG = 60", "angle=deg", K("0 decimal 5 inv cos"), C_ITRIG),
    ("asin 2 -> Invalid input", "angle=deg", K("2 inv sin"), S + "Ratpack/itrans.cpp:108-117 CALC_E_DOMAIN -> src/Calculator/Resources/en-US/CEngineStrings.resw:124-126"),
    ("sinh 1", "angle=deg", K("1 hyp sin"), C_HYP),
    ("cosh 1", "angle=deg", K("1 hyp cos"), C_HYP),
    ("tanh 1", "angle=deg", K("1 hyp tan"), C_HYP),
    ("asinh 1", "angle=deg", K("1 inv hyp sin"), C_HYP),
    ("acosh 0.5 -> Invalid input", "angle=deg", K("0 decimal 5 inv hyp cos"), S + "Ratpack/itransh.cpp:104-110 -> src/Calculator/Resources/en-US/CEngineStrings.resw:124-126"),
    ("log 1000 = 3", "", K("1000 log"), C_LOG),
    ("log 2", "", K("2 log"), C_LOG),
    ("log 0 -> Invalid input", "", K("0 log"), S + "Ratpack/exp.cpp:160-165 CALC_E_DOMAIN -> src/Calculator/Resources/en-US/CEngineStrings.resw:124-126"),
    ("ln 10 (inv log)", "", K("10 inv log"), C_LOG),
    ("ln 1 = 0", "", K("1 inv log"), C_LOG),
    ("10^3 (pow10)", "", K("3 pow10"), S + "CEngine/scifunc.cpp:218-220"),
    ("e^1 (inv pow10)", "", K("1 inv pow10"), S + "CEngine/scifunc.cpp:226-228 + Ratpack/exp.cpp:42-70"),
    ("e^2", "", K("2 inv pow10"), S + "CEngine/scifunc.cpp:226-228 + Ratpack/exp.cpp:42-70"),
    ("2^10", "", K("2 pow 10 equals"), C_POW),
    ("2^0.5", "", K("2 pow 0 decimal 5 equals"), C_POW),
    ("2^-10", "", K("2 pow 10 negate equals"), C_POW),
    ("0^0 = 1", "", K("0 pow 0 equals"), S + "Ratpack/exp.cpp:278-285,421-436 (0^0 special-cased to 1)"),
    ("0^-1 -> Invalid input", "", K("0 pow 1 negate equals"), S + "Ratpack/exp.cpp:424-428 CALC_E_DOMAIN -> src/Calculator/Resources/en-US/CEngineStrings.resw:124-126"),
    ("E11: `10 xʸ 10000` -> Overflow", "", K("10 pow 10000 equals"), C_POW + "; " + C_OVF),
    ("1e9999 x 10 -> Overflow", "", K("1 exp 9999 multiply 10 equals"), C_OVF),
    ("3rd root of 27 (inv pow) is exact", "", K("27 inv pow 3 equals"), S + "CEngine/scioper.cpp:157-159 + Ratpack/exp.cpp:305-412"),
    ("4th root of 16", "", K("16 inv pow 4 equals"), S + "CEngine/scioper.cpp:157-159 + Ratpack/exp.cpp:305-412"),
    ("cube root of -8 is -2 (odd root of a negative)", "", K("8 negate inv pow 3 equals"), S + "Ratpack/exp.cpp:484-512"),
    ("x^3 (inv square)", "", K("3 inv square"), S + "CEngine/scifunc.cpp:209-212"),
    ("1.5^3", "", K("1 decimal 5 inv square"), S + "CEngine/scifunc.cpp:209-212"),
    ("x^2 in Scientific", "", K("12 square"), S + "CEngine/scifunc.cpp:201-203"),
    ("sqrt 2 at 32 digits", "", K("2 sqrt"), C_SQRT + "; " + C_FMT32),
    ("sqrt 2 squared minus 2 snaps to 0", "", K("2 sqrt square subtract 2 equals"), C_SNAP),
    ("Exp entry shows 1.e+3", "", K("1 exp 3"), C_EXPK),
    ("1 Exp 3 = 1,000", "", K("1 exp 3 equals"), C_EXPK),
    ("1.5 Exp 3 + 1", "", K("1 decimal 5 exp 3 add 1 equals"), C_EXPK),
    ("negative exponent: 2 Exp -5", "", K("2 exp 5 negate equals"), C_EXPK + " + CEngine/CalcInput.cpp:36-39"),
    ("pi", "", K("pi"), C_PI),
    ("2 pi", "", K("pi multiply 2 equals"), C_PI),
    ("17 Mod 5", "", K("17 mod 5 equals"), C_MODSCI),
    ("-17 Mod 5 takes the divisor's sign", "", K("17 negate mod 5 equals"), C_MODSCI),
    ("17 Mod -5", "", K("17 mod 5 negate equals"), C_MODSCI),
    ("7.5 Mod 2", "", K("7 decimal 5 mod 2 equals"), C_MODSCI),
    ("5 Mod 0 returns 5", "", K("5 mod 0 equals"), C_MODSCI),
    ("Scientific 1 / 0", "", K("1 divide 0 equals"), C_DIV0),
    ("memory shared with Standard's rules", "", K("mc 5 ms 3 mplus mr"), C_MEM),
    ("F-E forces e-notation (LAST scientific line: the engine keeps F-E across C)", "", K("123 fe"), C_FE),
]

PROGRAMMER = [
    ("E11: 255 DEC -> HEX FF", "radix=dec word=qword", K("255 radix_hex"), C_RADIX + "; " + C_PROGFMT),
    ("E11: 255 DEC -> OCT 377", "radix=dec word=qword", K("255 radix_oct"), C_RADIX + "; " + C_PROGFMT),
    ("E11: 255 DEC -> BIN 1111 1111", "radix=dec word=qword", K("255 radix_bin"), C_RADIX + "; " + C_PROGFMT),
    ("HEX FF -> DEC 255", "radix=hex word=qword", K("FF radix_dec"), C_RADIX),
    ("HEX grouping in fours", "radix=hex word=qword", K("ABCDEF"), C_PROGFMT),
    ("OCT grouping in threes", "radix=oct word=qword", K("1234567"), C_PROGFMT),
    ("OCT 777 -> DEC 511", "radix=oct word=qword", K("777 radix_dec"), C_RADIX),
    ("BIN display is padded to the nibble", "radix=bin word=qword", K("101"), C_PROGFMT),
    ("BIN 101 -> DEC 5", "radix=bin word=qword", K("101 radix_dec"), C_RADIX),
    ("BIN 11111111 + 1 in QWORD", "radix=bin word=qword", K("11111111 add 1 equals"), C_PROGFMT),
    ("DEC grouping", "radix=dec word=qword", K("1234567"), C_GROUP),
    ("E11: BYTE 255 + 1 -> 0 (typed in HEX)", "radix=hex word=byte", K("FF add 1 equals"), C_WRAP),
    ("BYTE 127 + 1 wraps to -128 in DEC", "radix=dec word=byte", K("127 add 1 equals"), C_WRAP),
    ("DEC BYTE: 255 cannot be typed (the third digit is refused)", "radix=dec word=byte", K("255"), C_PINPUT),
    ("E11: QWORD −1 -> FFFF FFFF FFFF FFFF", "radix=dec word=qword", K("1 negate radix_hex"), C_WORD + "; " + C_PROGFMT),
    ("-1 in DEC QWORD", "radix=dec word=qword", K("1 negate"), C_SIGN),
    ("negate in HEX is two's complement", "radix=hex word=qword", K("1 negate"), C_SIGN + "; src/CalcManager/CEngine/scicomm.cpp:154 (+/- outside DEC ends the entry); " + C_WORD),
    ("QWORD 16 F's + 1 wraps to 0", "radix=hex word=qword", K("FFFFFFFFFFFFFFFF add 1 equals"), C_WRAP),
    ("DWORD 7FFFFFFF + 1 = 8000 0000", "radix=hex word=dword", K("7FFFFFFF add 1 equals"), C_WRAP),
    ("DWORD 2147483647 + 1 = -2,147,483,648", "radix=dec word=dword", K("2147483647 add 1 equals"), C_WRAP + "; " + C_PINPUT),
    ("WORD 32767 + 1 = -32,768", "radix=dec word=word", K("32767 add 1 equals"), C_WRAP),
    ("E11: NOT 0 (WORD) -> FFFF", "radix=hex word=word", K("0 not"), C_NOT),
    ("NOT 0 (WORD) in DEC is -1", "radix=dec word=word", K("0 not"), C_NOT + "; " + C_PROGFMT),
    ("NOT F0 (BYTE) = F", "radix=hex word=byte", K("F0 not"), C_NOT),
    ("NOT 0 (QWORD) in HEX", "radix=hex word=qword", K("0 not"), C_NOT),
    ("E11: AND pair C and A = 8", "radix=hex word=qword", K("C and A equals"), C_BITS),
    ("E11: OR pair C or A = E", "radix=hex word=qword", K("C or A equals"), C_BITS),
    ("E11: XOR pair C xor A = 6", "radix=hex word=qword", K("C xor A equals"), C_BITS),
    ("12 AND 10 = 8 (DEC)", "radix=dec word=qword", K("12 and 10 equals"), C_BITS),
    ("12 OR 3 = 15", "radix=dec word=qword", K("12 or 3 equals"), C_BITS),
    ("12 XOR 5 = 9", "radix=dec word=qword", K("12 xor 5 equals"), C_BITS),
    ("AND binds tighter than OR: 1 or 2 and 3 = 3", "radix=dec word=qword", K("1 or 2 and 3 equals"), C_SCIPREC + "; src/CalcManager/CEngine/scicomm.cpp:31-58 (NPrecedenceOfOp)"),
    ("+ binds tighter than AND: 5 + 3 and 6 = 0", "radix=dec word=qword", K("5 add 3 and 6 equals"), C_SCIPREC + "; src/CalcManager/CEngine/scicomm.cpp:31-58 (NPrecedenceOfOp)"),
    ("Programmer respects precedence: 2 + 3 x 4 = 14", "radix=dec word=qword", K("2 add 3 multiply 4 equals"), S + "CalculatorManager.cpp:201 + CEngine/scicomm.cpp:274-345"),
    ("parentheses in Programmer", "radix=dec word=qword", K("lparen 2 add 3 rparen multiply 4 equals"), C_PAREN),
    ("7 / 2 truncates to 3", "radix=dec word=qword", K("7 divide 2 equals"), C_PDIV),
    ("-7 / 2 truncates toward zero to -3", "radix=dec word=qword", K("7 negate divide 2 equals"), C_PDIV),
    ("5 / 2 x 2 = 4 (truncated between operators)", "radix=dec word=qword", K("5 divide 2 multiply 2 equals"), C_PDIV),
    ("Programmer 1 / 0", "radix=dec word=qword", K("1 divide 0 equals"), C_DIV0),
    ("17 Mod 5", "radix=dec word=qword", K("17 mod 5 equals"), C_PMOD),
    ("-17 Mod 5 keeps the dividend's sign", "radix=dec word=qword", K("17 negate mod 5 equals"), C_PMOD),
    ("5 Mod 0 -> Result is undefined", "radix=dec word=qword", K("5 mod 0 equals"), S + "Ratpack/logic.cpp:192-198 CALC_E_INDEFINITE -> src/Calculator/Resources/en-US/CEngineStrings.resw:128-130"),
    ("1 Lsh 4 = 16", "radix=dec word=qword", K("1 lsh 4 equals"), C_SHIFT),
    ("1 Lsh 63 sets the sign bit", "radix=dec word=qword", K("1 lsh 63 equals"), C_SHIFT + "; " + C_PROGFMT),
    ("E11 wrote '1 Lsh 64 (QWORD) -> 0'; the source gives Result not defined (shift >= word size)", "radix=dec word=qword", K("1 lsh 64 equals"), C_NORES),
    ("BYTE 1 Lsh 7 = -128", "radix=dec word=byte", K("1 lsh 7 equals"), C_SHIFT + "; " + C_WRAP),
    ("BYTE 1 Lsh 8 -> Result not defined", "radix=dec word=byte", K("1 lsh 8 equals"), C_NORES),
    ("16 Rsh 2 = 4", "radix=dec word=qword", K("16 rsh 2 equals"), C_SHIFT),
    ("Rsh is arithmetic: -16 Rsh 2 = -4", "radix=dec word=qword", K("16 negate rsh 2 equals"), C_SHIFT),
    ("WORD 8000 Rsh 4 = F800 (sign extended)", "radix=hex word=word", K("8000 rsh 4 equals"), C_SHIFT),
    ("256 Rsh 64 -> Result not defined", "radix=dec word=qword", K("256 rsh 64 equals"), S + "CEngine/scioper.cpp:39-44 CALC_E_NORESULT -> src/Calculator/Resources/en-US/CEngineStrings.resw:140-142"),
    ("RoL (inv Lsh) 5 = 10", "radix=dec word=qword", K("5 inv lsh"), C_ROT),
    ("RoL in BYTE carries the top bit round: 81 -> 3", "radix=hex word=byte", K("81 inv lsh"), C_ROT),
    ("RoR (inv Rsh) in BYTE: 1 -> 80", "radix=hex word=byte", K("1 inv rsh"), C_ROT),
    ("word size QWORD -> DWORD -> WORD -> BYTE truncates 300 to 44", "radix=dec word=qword", K("300 word word word"), C_WORD),
    ("65535 in WORD reads -1", "radix=dec word=qword", K("65535 word word"), C_WORD),
    ("128 in BYTE reads -128", "radix=dec word=qword", K("128 word word word"), C_WORD),
    ("BYTE 80 -> QWORD sign-extends", "radix=hex word=byte", K("80 word"), S + "CEngine/sciset.cpp:14-31 (sign extension) + CEngine/scidisp.cpp:54-76"),
    ("the decimal key does nothing in Programmer", "radix=dec word=qword", K("5 decimal 5"), S + "CEngine/scicomm.cpp:869-875 (integer mode: '.' ignored) + docs/plan/r11/calculator.md 4.14 (disabled key)"),
    ("QWORD DEC max 9223372036854775807 + 1", "radix=dec word=qword", K("9223372036854775807 add 1 equals"), C_WRAP + "; " + C_PINPUT),
    ("backspace in HEX", "radix=hex word=qword", K("FF backspace"), C_BACK),
    ("HEX to OCT", "radix=hex word=qword", K("FF radix_oct"), C_RADIX),
    ("HEX A + 1 = B", "radix=hex word=qword", K("A add 1 equals"), C_WRAP),
    ("64 BIN digits = precision 64: no e-notation switch in Programmer (it needs MORE digits than the precision)",
     "radix=dec word=qword", K("1 negate radix_bin"), S + "Ratpack/conv.cpp:1083-1087 + CalculatorManager.h:26; " + C_PROGFMT),
]

CONVERTER = [
    # (category, from, to, typed keys, note)
    ("Volume", "Liters", "Milliliters", K("1"), ""),
    ("Volume", "Gallons (US)", "Liters", K("1"), ""),
    ("Volume", "Cups (US)", "Milliliters", K("2"), ""),
    ("Length", "Miles", "Kilometers", K("1"), "E12: 1 mile -> 1.609344 kilometers"),
    ("Length", "Miles", "Kilometers", K("5"), "E26/T15-52: 5 miles is 8.04672 kilometers"),
    ("Length", "Inches", "Centimeters", K("12"), ""),
    ("Length", "Feet", "Meters", K("3"), ""),
    ("Length", "Kilometers", "Miles", K("10"), ""),
    ("Weight and Mass", "Kilograms", "Pounds", K("1"), ""),
    ("Weight and Mass", "Ounces", "Pounds", K("16"), ""),
    ("Weight and Mass", "Stone", "Kilograms", K("1"), ""),
    ("Temperature", "Celsius", "Fahrenheit", K("100"), "E12: 100 °C -> 212 °F"),
    ("Temperature", "Fahrenheit", "Celsius", K("32"), ""),
    ("Temperature", "Celsius", "Kelvin", K("0"), ""),
    ("Temperature", "Fahrenheit", "Celsius", K("98 decimal 6"), ""),
    ("Energy", "Food calories", "Kilojoules", K("1"), ""),
    ("Energy", "Kilowatt-hours", "Joules", K("1"), ""),
    ("Energy", "British thermal units", "Joules", K("2"), ""),
    ("Area", "Acres", "Square meters", K("1"), ""),
    ("Area", "Hectares", "Acres", K("1"), ""),
    ("Area", "Square miles", "Square kilometers", K("1"), ""),
    ("Speed", "Kilometers per hour", "Miles per hour", K("100"), "Windows' mph factor is 44.7 cm/s"),
    ("Speed", "Meters per second", "Kilometers per hour", K("10"), ""),
    ("Speed", "Knots", "Kilometers per hour", K("1"), ""),
    ("Time", "Days", "Hours", K("1"), ""),
    ("Time", "Minutes", "Hours", K("90"), ""),
    ("Time", "Years", "Days", K("1"), ""),
    ("Power", "Horsepower (US)", "Watts", K("1"), ""),
    ("Power", "Kilowatts", "Watts", K("1"), ""),
    ("Power", "Watts", "BTUs/minute", K("100"), ""),
    ("Data", "Gigabytes", "Megabytes", K("1"), ""),
    ("Data", "Gibibytes", "Mebibytes", K("1"), ""),
    ("Data", "Bytes", "Bits", K("1"), ""),
    ("Data", "Megabits", "Kilobytes", K("1"), ""),
    ("Pressure", "Atmospheres", "Bars", K("1"), ""),
    ("Pressure", "Atmospheres", "Pounds per square inch", K("1"), ""),
    ("Pressure", "Bars", "Kilopascals", K("1"), ""),
    ("Angle", "Degrees", "Radians", K("180"), ""),
    ("Angle", "Gradians", "Degrees", K("100"), ""),
    ("Angle", "Radians", "Degrees", K("1"), ""),
]

FACTOR_LINE = {  # UnitConverterDataLoader.cs line of each category's factor block (ViewMode.Weight = Weight and Mass)
    "Volume": 591, "Length": 551, "Weight and Mass": 617, "Energy": 537, "Area": 478,
    "Speed": 638, "Time": 580, "Power": 569, "Data": 496, "Pressure": 657, "Angle": 651,
}

DATES = [
    # (op, from, to_or_None, (years, months, days) or None, note)
    ("difference", "2024-02-28", "2024-03-01", None, "across the leap day 2024-02-29"),
    ("difference", "2023-02-28", "2023-03-01", None, "the same span in a common year"),
    ("difference", "2024-01-15", "2024-03-15", None, "two months across a leap February"),
    ("difference", "2024-01-31", "2024-02-29", None, "month end: Jan 31 + 1 month clamps to Feb 29"),
    ("difference", "2008-02-29", "2008-03-31", None, "repo test: 1 month, 2 days (src/Calculator.Tests/DateCalculatorTests.cs:381-397)"),
    ("difference", "2023-12-25", "2024-01-08", None, "across the year end: weeks"),
    ("difference", "2023-12-31", "2024-01-01", None, "one day across the year end"),
    ("difference", "2023-06-15", "2024-06-15", None, "one year containing a leap day"),
    ("difference", "2020-02-29", "2021-02-28", None, "Feb 29 + 1 year clamps to Feb 28"),
    ("difference", "2024-05-05", "2024-05-05", None, "same dates"),
    ("difference", "2008-03-10", "2007-05-10", None, "order does not matter (src/Calculator.Tests/DateCalculatorTests.cs:514-534)"),
    ("difference", "2019-01-01", "2024-09-23", None, "years, months, weeks and days"),
    ("add", "2024-01-31", None, (0, 1, 0), "month-end clamp into a leap February"),
    ("add", "2023-01-31", None, (0, 1, 0), "month-end clamp into a common February"),
    ("add", "2024-02-29", None, (1, 0, 0), "leap day + 1 year"),
    ("add", "2024-12-25", None, (0, 0, 14), "2 weeks, entered as 14 days (the UI has no weeks input)"),
    ("add", "2024-01-31", None, (1, 1, 1), "years, then months, then days"),
    ("add", "2023-12-31", None, (0, 0, 1), "across the year end"),
    ("subtract", "2024-03-31", None, (0, 1, 0), "month-end clamp back into February"),
    ("subtract", "2024-03-10", None, (0, 1, 10), "days first, then months (repo test pattern)"),
    ("subtract", "2025-03-01", None, (1, 1, 1), "days, then months, then years"),
    ("subtract", "2024-01-01", None, (0, 0, 14), "2 weeks back across the year end"),
]

TESS = [
    # (utterance words, kind, payload)
    ("what is fifteen percent of eighty", "percent", (Fraction(15), Fraction(80))),
    ("what is one divided by zero", "binary", ("1", "divided by", "0", K("1 divide 0 equals"))),
    ("what is five miles in kilometers", "convert", ("5", "Length", "Miles", "Kilometers")),
    ("what is five miles in kilometres", "convert", ("5", "Length", "Miles", "Kilometers")),
    ("what is the square root of eighty one", "sqrt", K("81")),
    ("what is 2 plus 2", "binary", ("2", "plus", "2", K("2 add 2 equals"))),
    ("what is 2 plus 3 times 4", "expr", ("2 plus 3 times 4", K("2 add 3 multiply 4 equals"))),
    ("what is point five times four", "binary", (K("decimal 5"), "times", "4", K("decimal 5 multiply 4 equals"))),
    ("what is negative three times four", "binary", (K("3 negate"), "times", "4", K("3 negate multiply 4 equals"))),
    ("what is 10 to the power of 10000", "binary", ("10", "to the power of", K("10000"), K("10 pow 10000 equals"))),
    ("what is 2 to the power of 200", "binary", ("2", "to the power of", K("200"), K("2 pow 200 equals"))),
    ("what is 10 minus 25", "binary", (K("10"), "minus", K("25"), K("10 subtract 25 equals"))),
    ("what is 1 divided by 3", "binary", ("1", "divided by", "3", K("1 divide 3 equals"))),
    ("calculate 7 times 6", "binary", ("7", "times", "6", K("7 multiply 6 equals"))),
    ("how much is 100 divided by 8", "binary", (K("100"), "divided by", "8", K("100 divide 8 equals"))),
    ("what is 25 times 40", "binary", (K("25"), "times", K("40"), K("25 multiply 40 equals"))),
    ("what is the square root of two", "sqrt", K("2")),
    ("what is zero divided by zero", "binary", ("0", "divided by", "0", K("0 divide 0 equals"))),
    ("what is 3 feet in meters", "convert", ("3", "Length", "Feet", "Meters")),
]


def operand_text(keys):
    """A number restated 'as the engine displays it': the Scientific display after typing it."""
    return run_keys("scientific", keys)


def tess_row(utt, kind, payload):
    if kind == "percent":
        a, b = payload
        disp = run_keys("scientific", K("%s multiply %s divide 100 equals" % (b, a)))  # b x a / 100 (T15-2)
        said = "%s %% of %s" % (operand_text(K(str(a))), operand_text(K(str(b))))
    elif kind == "sqrt":
        disp = run_keys("scientific", payload + " sqrt")
        said = "√" + operand_text(payload)
    elif kind == "convert":
        n, cat, frm, to = payload
        disp = converter_display(cat, frm, to, K(n))
        return disp, "%s %s is %s %s." % (n, frm.lower(), disp, to.lower())
    elif kind == "expr":
        said, keys = payload
        disp = run_keys("scientific", keys)
    else:
        a, op, b, keys = payload
        disp = run_keys("scientific", keys)
        said = "%s %s %s" % (operand_text(K(a)), op, operand_text(K(b)))
    if disp in ERR_TEXT.values():
        return disp, disp + "."
    return disp, "%s is %s." % (said, disp)


_BARE = re.compile(r"(?<![\w/.])(CEngine/|Ratpack/|CalculatorManager\.(?:cpp|h)|UnitConverter\.cpp|NumberFormattingUtils\.cpp)")
_BARE_VM = re.compile(r"(?<![\w/.])(StandardCalculatorViewModel\.cs|UnitConverterViewModel\.cs|DateCalculatorViewModel\.cs)")


def full_paths(src):
    """Every cited file as a path from the microsoft/calculator root (or docs/...)."""
    src = _BARE.sub(r"src/CalcManager/\1", src)
    return _BARE_VM.sub(r"src/Calculator.ViewModels/\1", src)


# calc-keys.md key vocabulary per mode (S, Sc, P)
_ALL = {"add", "subtract", "multiply", "divide", "equals", "negate", "clear_entry", "clear", "backspace", "decimal"}
VOCAB = {
    "standard": _ALL | set("0123456789") | {"percent", "sqrt", "square", "reciprocal", "mc", "mr", "mplus", "mminus", "ms"},
    "scientific": _ALL | set("0123456789") | {"sqrt", "square", "mc", "mr", "mplus", "mminus", "ms", "inv", "pow", "sin",
                                              "cos", "tan", "pow10", "log", "exp", "mod", "pi", "factorial", "lparen",
                                              "rparen", "angle", "hyp", "fe"},
    "programmer": _ALL | set("0123456789ABCDEF") | {"ms", "inv", "mod", "lparen", "rparen", "lsh", "rsh", "or", "xor",
                                                    "not", "and", "radix_hex", "radix_dec", "radix_oct", "radix_bin", "word"},
    "converter": set("0123456789") | {"decimal"},
}


def build():
    rows = []  # (id, mode, setup, keys, expected, utt, reply, source, comment)

    def add(mode, setup, keys, expected, source, comment, utt="", reply=""):
        source = full_paths(source)
        n = sum(1 for r in rows if r[1] == mode) + 1
        rows.append(("%s-%03d" % (mode, n), mode, setup, keys, expected, utt, reply, source, comment))

    for mode, cases in (("standard", STANDARD), ("scientific", SCIENTIFIC), ("programmer", PROGRAMMER)):
        for comment, setup, keys, cite in cases:
            bad = [k for k in keys.split() if k not in VOCAB[mode]]
            assert not bad, "%s %r uses keys outside calc-keys.md: %r" % (mode, keys, bad)
            add(mode, setup, keys, run_keys(mode, keys, setup), cite, comment)

    for cat, frm, to, keys, note in CONVERTER:
        if cat == "Temperature":
            cite = C_UC + "; " + C_UCT + ":669-689 (explicit ratio/offset)"
        else:
            cite = C_UC + "; " + C_UCT + ":%d (ratio = from / to factor, :740-757)" % FACTOR_LINE[cat]
        setup = "category=%s from=%s to=%s" % (cat, frm, to)
        assert all(k in VOCAB["converter"] for k in keys.split()), keys
        add("converter", setup, keys, converter_display(cat, frm, to, keys), cite, note or "%s -> %s" % (frm, to))

    for op, frm, to, amt, note in DATES:
        d0 = date.fromisoformat(frm)
        if op == "difference":
            keys = "from=%s to=%s" % (frm, to)
            exp = date_diff_text(d0, date.fromisoformat(to))
            cite = C_DD
        else:
            y, m, dd = amt
            keys = "from=%s years=%d months=%d days=%d" % (frm, y, m, dd)
            exp = date_add_text(d0, y, m, dd, op == "subtract")
            cite = C_DA
        add("date", "op=" + op, keys, exp, cite, note)

    for utt, kind, payload in TESS:
        disp, reply = tess_row(utt, kind, payload)
        cite = C_TESS + ("; " + C_UC if kind == "convert" else "")
        add("tess", "", "", disp, cite, utt, utt, reply)
    return rows


# ----------------------------------------------------------------------------------------------------------------
# 8. Self-checks: every expectation phase 15 names (E11, E12, E26) must come out of the rules as stated there
#    (one deliberate exception, the Lsh 64 line, is asserted to the source's value and reported).
# ----------------------------------------------------------------------------------------------------------------


def self_check(rows):
    by = {(r[1], r[2], r[3]): r[4] for r in rows}
    tess = {r[5]: r[6] for r in rows if r[1] == "tess"}

    def chk(mode, setup, keys, want):
        got = by[(mode, setup, keys)]
        assert got == want, "%s %r %r: got %r want %r" % (mode, setup, keys, got, want)

    chk("standard", "", K("2 add 3 multiply 4 equals"), "20")
    chk("scientific", "", K("2 add 3 multiply 4 equals"), "14")
    chk("standard", "", K("1 divide 0 equals"), "Cannot divide by zero")
    chk("standard", "", K("0 divide 0 equals"), "Result is undefined")
    chk("standard", "", K("1 negate sqrt"), "Invalid input")
    chk("scientific", "", K("10 pow 10000 equals"), "Overflow")
    chk("standard", "", K("0 decimal 1 add 0 decimal 2 equals"), "0.3")
    chk("standard", "", K("80 add 15 percent equals"), "92")
    chk("standard", "", K("2 multiply equals equals"), "8")
    chk("scientific", "", K("200 factorial"), "7.8865786736479050355236321393219e+374")
    chk("standard", "", K("9999999999999999 add 1 equals"), "1.e+16")
    chk("scientific", "angle=deg", K("30 sin"), "0.5")
    assert round(float(by[("scientific", "angle=rad", K("30 sin"))]), 4) == -0.9880  # E11 "−0.9880…"
    assert round(float(by[("scientific", "angle=grad", K("30 sin"))]), 4) == 0.4540  # E11 "0.4540…" (0.453990…)
    chk("programmer", "radix=dec word=qword", K("255 radix_hex"), "FF")
    chk("programmer", "radix=dec word=qword", K("255 radix_oct"), "377")
    chk("programmer", "radix=dec word=qword", K("255 radix_bin"), "1111 1111")
    chk("programmer", "radix=hex word=byte", K("FF add 1 equals"), "0")
    chk("programmer", "radix=dec word=qword", K("1 negate radix_hex"), "FFFF FFFF FFFF FFFF")
    chk("programmer", "radix=hex word=word", K("0 not"), "FFFF")
    chk("programmer", "radix=dec word=qword", K("1 lsh 64 equals"), "Result not defined")  # E11 says 0; source wins
    chk("converter", "category=Length from=Miles to=Kilometers", "1", "1.609344")
    chk("converter", "category=Temperature from=Celsius to=Fahrenheit", K("100"), "212")
    assert tess["what is fifteen percent of eighty"] == "15 % of 80 is 12.", tess["what is fifteen percent of eighty"]
    assert tess["what is one divided by zero"] == "Cannot divide by zero."
    assert tess["what is five miles in kilometers"] == "5 miles is 8.04672 kilometers."
    assert tess["what is five miles in kilometres"] == "5 miles is 8.04672 kilometers."
    assert tess["what is the square root of eighty one"] == "√81 is 9."
    assert tess["what is 2 plus 2"] == "2 plus 2 is 4."
    assert tess["what is 2 plus 3 times 4"] == "2 plus 3 times 4 is 14."
    assert tess["what is point five times four"] == "0.5 times 4 is 2."
    assert tess["what is negative three times four"] == "-3 times 4 is -12."
    # unit-level checks of the formatter against r11/calculator.md 8.4
    assert fmt_guarded(Fraction(math.factorial(200)), 16) == "7.886578673647905e+374"
    assert fmt_guarded(Fraction(10**16), 16) == "1.e+16"
    # date wording pinned by the repo's own view-model test (DateCalculatorTests.cs:381-397)
    assert date_diff_text(date(2008, 3, 31), date(2008, 2, 29)) == "1 month, 2 days | 31 days"
    assert date_diff_text(date(2019, 3, 10), date(2019, 3, 17)) == "1 week | 7 days"
    assert date_diff_text(date(2007, 5, 10), date(2008, 3, 10)) == "10 months | 305 days"
    assert date_add_text(date(2008, 1, 31), 0, 1, 0, False).endswith("February 29, 2008")
    assert date_add_text(date(2008, 3, 31), 0, 1, 10, False).endswith("May 10, 2008")
    assert date_add_text(date(2008, 3, 10), 0, 1, 10, True).endswith("January 29, 2008")


def main():
    rows = build()
    self_check(rows)
    ids = [r[0] for r in rows]
    assert len(ids) == len(set(ids))
    for r in rows:
        for f in r[:8]:
            assert "\t" not in f and "\n" not in f, r
    header = ["id", "mode", "setup", "keys", "expected", "tess_utterance", "tess_reply", "source"]
    lines = ["\t".join(header)]
    lines.append("# Generated by docs/plan/qa/phase-15/scripts/gen_calc_cases.py -- do not edit by hand; re-run the script.")
    lines.append("# Oracle: microsoft/calculator @ %s (MIT); rules ported on the host, never the app's engine." % SRC_COMMIT)
    lines.append("# Source paths below are relative to that repo unless they start with docs/. Driver notes and the")
    lines.append("# omitted cases are in the generator's header comment. setup: split on r\"\\s+(?=(?:angle|radix|word|category|from|to|op)=)\".")
    last_mode = None
    for r in rows:
        if r[1] != last_mode:
            lines.append("# ---- %s ----" % r[1])
            last_mode = r[1]
        lines.append("# " + r[8])
        lines.append("\t".join(r[:8]))
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    counts = {}
    for r in rows:
        counts[r[1]] = counts.get(r[1], 0) + 1
    print("wrote %s" % OUT)
    print("cases: %d total -- %s" % (len(rows), ", ".join("%s %d" % (m, counts.get(m, 0)) for m in
                                                             ("standard", "scientific", "programmer", "converter", "date", "tess"))))
    print("self-checks: E11/E12/E26 named expectations pass (E11 '1 Lsh 64 -> 0' asserted as the source's 'Result not defined')")
    return 0


if __name__ == "__main__":
    sys.exit(main())
