package com.emailatomizer;

/** Minimal RFC 3492 Punycode encoder without Nameprep/NFKC mapping. */
public final class RawPunycode {
    private RawPunycode() {}

    private static final int BASE = 36, TMIN = 1, TMAX = 26, SKEW = 38, DAMP = 700;
    private static final int INITIAL_BIAS = 72, INITIAL_N = 128;

    public static String encodeLabel(String input) {
        if (input == null || input.isEmpty()) return "";
        int[] cps = input.codePoints().toArray();
        StringBuilder out = new StringBuilder();
        int basic = 0;
        for (int cp : cps) {
            if (cp < 0x80) { out.append((char) cp); basic++; }
        }
        int h = basic;
        if (basic > 0 && h < cps.length) out.append('-');
        int n = INITIAL_N, delta = 0, bias = INITIAL_BIAS;
        while (h < cps.length) {
            int m = Integer.MAX_VALUE;
            for (int cp : cps) if (cp >= n && cp < m) m = cp;
            long inc = (long) (m - n) * (h + 1L);
            if (inc > Integer.MAX_VALUE - delta) throw new IllegalArgumentException("punycode overflow");
            delta += (int) inc;
            n = m;
            for (int cp : cps) {
                if (cp < n) {
                    if (++delta == 0) throw new IllegalArgumentException("punycode overflow");
                }
                if (cp == n) {
                    int q = delta;
                    for (int k = BASE;; k += BASE) {
                        int t = k <= bias ? TMIN : (k >= bias + TMAX ? TMAX : k - bias);
                        if (q < t) break;
                        out.append(encodeDigit(t + (q - t) % (BASE - t)));
                        q = (q - t) / (BASE - t);
                    }
                    out.append(encodeDigit(q));
                    bias = adapt(delta, h + 1, h == basic);
                    delta = 0;
                    h++;
                }
            }
            delta++;
            n++;
        }
        return out.toString();
    }

    public static String toRawALabel(String unicodeLabel) {
        if (unicodeLabel.codePoints().allMatch(cp -> cp < 0x80)) return unicodeLabel;
        return "xn--" + encodeLabel(unicodeLabel);
    }

    private static int adapt(int delta, int numPoints, boolean firstTime) {
        delta = firstTime ? delta / DAMP : delta / 2;
        delta += delta / numPoints;
        int k = 0;
        while (delta > ((BASE - TMIN) * TMAX) / 2) {
            delta /= BASE - TMIN;
            k += BASE;
        }
        return k + (BASE - TMIN + 1) * delta / (delta + SKEW);
    }

    private static char encodeDigit(int d) {
        return (char) (d < 26 ? 'a' + d : '0' + (d - 26));
    }
}
