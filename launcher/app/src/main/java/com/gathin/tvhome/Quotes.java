package com.gathin.tvhome;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The quotes screensaver's quotes: res/raw/quotes.txt read into a list, how long each one stays up, and the
 * order they come in. No Android classes, so it is tested on the JVM (QuotesTest).
 */
final class Quotes {
    private Quotes() {
    }

    /**
     * One quote in English and Arabic, each with its author's name. A line break in a quote parts a verse's
     * halves. arabicFirst: it was written in Arabic, so the Arabic is shown above the English.
     */
    static final class Quote {
        final String en, enAuthor, ar, arAuthor;
        final boolean arabicFirst;

        Quote(String en, String enAuthor, String ar, String arAuthor, boolean arabicFirst) {
            this.en = en;
            this.enAuthor = enAuthor;
            this.ar = ar;
            this.arAuthor = arAuthor;
            this.arabicFirst = arabicFirst;
        }
    }

    /**
     * Blocks of four lines (quote, author, the quote in the other language, its author) parted by blank
     * lines; a line that starts with # is a note. The language a block starts with is the one the quote was
     * written in. " / " in a quote becomes a line break. A block of any other size is left out rather than
     * shown with a quote under the wrong name.
     */
    static List<Quote> parse(Reader in) throws IOException {
        List<Quote> quotes = new ArrayList<>();
        List<String> block = new ArrayList<>();
        BufferedReader reader = new BufferedReader(in);
        for (String line = reader.readLine(); ; line = reader.readLine()) {
            String text = line == null ? "" : line.trim();
            if (text.startsWith("#")) continue;
            if (!text.isEmpty()) {
                block.add(text);
            } else {
                if (block.size() == 4) {
                    boolean arabicFirst = arabic(block.get(0));
                    int en = arabicFirst ? 2 : 0, ar = arabicFirst ? 0 : 2;
                    quotes.add(new Quote(block.get(en).replace(" / ", "\n"), block.get(en + 1),
                            block.get(ar).replace(" / ", "\n"), block.get(ar + 1), arabicFirst));
                }
                block.clear();
            }
            if (line == null) return quotes;
        }
    }

    private static boolean arabic(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x0600 && c <= 0x06FF) return true;
        }
        return false;
    }

    /** Time on screen: 22 s for a short quote, up to 30 s for a long one: time to read it twice and think. */
    static long holdMs(Quote quote) {
        return Math.min(30_000L, 22_000L + 40L * Math.max(quote.en.length(), quote.ar.length()));
    }

    /**
     * Every quote once in a random order, then again in a new one. A quote never follows itself, across a
     * reshuffle or a restart (avoidFirst is the quote the last run ended on), and where the list allows it
     * neither does a second quote by the same author.
     */
    static final class Deck {
        private final List<Quote> order;
        private final Random random;
        private int next;
        private Quote last;

        Deck(List<Quote> quotes, Random random, String avoidFirst) {
            this.order = new ArrayList<>(quotes);
            this.random = random;
            for (Quote q : quotes) if (q.en.equals(avoidFirst)) last = q;
            shuffle();
        }

        boolean isEmpty() {
            return order.isEmpty();
        }

        Quote next() {
            if (next >= order.size()) shuffle();
            last = order.get(next++);
            return last;
        }

        private void shuffle() {
            Collections.shuffle(order, random);
            next = 0;
            Quote carried = last;
            for (int i = 0; i < order.size(); i++) {
                Quote before = i == 0 ? carried : order.get(i - 1);
                if (before == null || !clashes(order.get(i), before)) continue;
                int swap = -1;
                for (int j = i + 1; j < order.size() && swap < 0; j++) {
                    if (!clashes(order.get(j), before)) swap = j;
                }
                // Near the end nothing later may fit: trade places with an earlier quote where both then fit.
                for (int j = 0; j < i && swap < 0; j++) {
                    Collections.swap(order, i, j);
                    if (fits(i, carried) && fits(j, carried)) swap = j;
                    Collections.swap(order, i, j);
                }
                // Nothing fits by author: at least never the same quote twice running.
                if (swap < 0 && order.get(i) == before && i + 1 < order.size()) swap = i + 1;
                if (swap >= 0) Collections.swap(order, i, swap);
            }
        }

        /** The quote at this place clashes with neither neighbour (the one before the first is the last one shown). */
        private boolean fits(int at, Quote carried) {
            Quote before = at == 0 ? carried : order.get(at - 1);
            return (before == null || !clashes(order.get(at), before))
                    && (at + 1 >= order.size() || !clashes(order.get(at), order.get(at + 1)));
        }

        private static boolean clashes(Quote a, Quote b) {
            return a == b || a.enAuthor.equals(b.enAuthor);
        }
    }
}
