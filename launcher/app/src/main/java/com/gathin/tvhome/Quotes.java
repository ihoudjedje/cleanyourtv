package com.gathin.tvhome;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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

    /** Each quote's turn on screen, from the moment it starts to rise in to the moment the next one does. */
    static final long SLOT_MS = 30_000L;

    /**
     * At least this many other quotes come between two showings of the same one: 15 minutes and more of
     * quotes at 30 s each without a repeat. With fewer quotes than that, as many as the list allows.
     */
    static final int MIN_GAP = 30;

    /**
     * Every quote once in a random order, then all of them again in a new one. Between two showings of a
     * quote come at least MIN_GAP others, across a new round and across a restart (recent: the quotes the
     * last run showed, oldest first), and where the list allows it no author follows himself.
     */
    static final class Deck {
        private final List<Quote> all;
        /** What this round has not shown yet. */
        private final List<Quote> left = new ArrayList<>();
        /** The number of the showing each quote was last on, counted from the first quote in recent. */
        private final Map<Quote, Long> lastShown = new IdentityHashMap<>();
        private final ArrayDeque<Quote> history = new ArrayDeque<>();
        private final Random random;
        private final int gap;
        private long count;
        private Quote last;

        Deck(List<Quote> quotes, Random random, List<String> recent) {
            this.all = new ArrayList<>(quotes);
            this.random = random;
            this.gap = Math.min(MIN_GAP, Math.max(0, all.size() - 1));
            for (String en : recent) {
                for (Quote q : all) {
                    if (q.en.equals(en)) {
                        lastShown.put(q, count);
                        remember(q);
                        last = q;
                    }
                }
                count++;
            }
        }

        boolean isEmpty() {
            return all.isEmpty();
        }

        Quote next() {
            if (left.isEmpty()) left.addAll(all);
            List<Quote> pick = candidates(true);
            // An author with half of what is left must come up now, or the round would end on his quotes
            // back to back (al-Mutanabbi has many).
            String crowded = crowdedAuthor();
            if (crowded != null) {
                List<Quote> his = new ArrayList<>();
                for (Quote q : pick) if (q.enAuthor.equals(crowded)) his.add(q);
                if (!his.isEmpty()) pick = his;
            }
            if (pick.isEmpty()) pick = candidates(false);   // only one author left that may come up
            Quote q;
            if (!pick.isEmpty()) {
                q = pick.get(random.nextInt(pick.size()));
            } else {
                // Every quote left was shown too recently, which only a short list allows: the longest ago.
                q = left.get(0);
                for (Quote c : left) if (seen(c) < seen(q)) q = c;
            }
            left.remove(q);
            lastShown.put(q, count++);
            remember(q);
            last = q;
            return q;
        }

        /** The quotes shown most recently, oldest first, for the next run's Deck. */
        List<String> recent() {
            List<String> out = new ArrayList<>();
            for (Quote q : history) out.add(q.en);
            return out;
        }

        private List<Quote> candidates(boolean byAuthor) {
            List<Quote> out = new ArrayList<>();
            for (Quote q : left) {
                if (count - seen(q) <= gap) continue;
                if (byAuthor && last != null && q.enAuthor.equals(last.enAuthor)) continue;
                out.add(q);
            }
            return out;
        }

        /** The author, other than the last one shown, with at least half of the quotes this round has left. */
        private String crowdedAuthor() {
            Map<String, Integer> counts = new java.util.HashMap<>();
            for (Quote q : left) counts.merge(q.enAuthor, 1, Integer::sum);
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                if (last != null && e.getKey().equals(last.enAuthor)) continue;
                if (2 * e.getValue() >= left.size()) return e.getKey();
            }
            return null;
        }

        private long seen(Quote q) {
            Long at = lastShown.get(q);
            return at == null ? Long.MIN_VALUE / 2 : at;
        }

        private void remember(Quote q) {
            history.remove(q);
            history.addLast(q);
            while (history.size() > MIN_GAP) history.removeFirst();
        }
    }
}
