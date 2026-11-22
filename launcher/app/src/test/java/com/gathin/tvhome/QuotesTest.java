package com.gathin.tvhome;

import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class QuotesTest {
    private static List<Quotes.Quote> parse(String text) throws IOException {
        return Quotes.parse(new StringReader(text));
    }

    /** The quotes the launcher ships, read as the screensaver reads them. */
    private static List<Quotes.Quote> shipped() throws IOException {
        try (Reader in = Files.newBufferedReader(Paths.get("src/main/res/raw/quotes.txt"), StandardCharsets.UTF_8)) {
            return Quotes.parse(in);
        }
    }

    private static boolean hasArabic(String text) {
        return text.codePoints().anyMatch(c -> c >= 0x0600 && c <= 0x06FF);
    }

    @Test public void aBlockIsQuoteAuthorArabicAuthor() throws IOException {
        List<Quotes.Quote> quotes = parse("# source\nWell done.\nSomeone\nأحسنت\nفلان\n\n# note\nA / B\nPoet\nأ / ب\nشاعر");
        assertEquals(2, quotes.size());
        assertEquals("Well done.", quotes.get(0).en);
        assertEquals("Someone", quotes.get(0).enAuthor);
        assertEquals("فلان", quotes.get(0).arAuthor);
        assertEquals("A\nB", quotes.get(1).en);
        assertEquals("أ\nب", quotes.get(1).ar);
        assertFalse(quotes.get(0).arabicFirst);
    }

    @Test public void aBlockThatStartsInArabicWasWrittenInArabic() throws IOException {
        List<Quotes.Quote> quotes = parse("أ / ب\nشاعر\nA / B\nPoet\n");
        assertTrue(quotes.get(0).arabicFirst);
        assertEquals("A\nB", quotes.get(0).en);
        assertEquals("Poet", quotes.get(0).enAuthor);
        assertEquals("أ\nب", quotes.get(0).ar);
        assertEquals("شاعر", quotes.get(0).arAuthor);
    }

    @Test public void aBlockOfAnotherSizeIsLeftOut() throws IOException {
        assertTrue(parse("A quote with no author\n\nOne\nTwo\nThree\nFour\nFive\n").isEmpty());
        assertEquals(1, parse("\n\n# only notes\n\nQ\nA\nق\nم\n\n\n").size());
    }

    @Test public void everyShippedQuoteHasBothLanguagesAndAnAuthor() throws IOException {
        List<Quotes.Quote> quotes = shipped();
        assertFalse(quotes.isEmpty());
        // Every block in the file made a quote: none was dropped for its size.
        long blocks = 0;
        boolean inBlock = false;
        for (String line : Files.readAllLines(Paths.get("src/main/res/raw/quotes.txt"), StandardCharsets.UTF_8)) {
            boolean text = !line.trim().isEmpty() && !line.trim().startsWith("#");
            if (text && !inBlock) blocks++;
            if (text) inBlock = true;
            else if (line.trim().isEmpty()) inBlock = false;
        }
        assertEquals(blocks, quotes.size());
        Set<String> seen = new HashSet<>();
        for (Quotes.Quote q : quotes) {
            assertFalse(q.en, hasArabic(q.en) || hasArabic(q.enAuthor));
            assertTrue(q.en, hasArabic(q.ar) && hasArabic(q.arAuthor));
            assertFalse(q.ar, q.ar.matches(".*[A-Za-z].*") || q.arAuthor.matches(".*[A-Za-z].*"));
            assertTrue(q.en, q.enAuthor.length() >= 3 && q.arAuthor.length() >= 3);
            // The screensaver adds the quotation marks.
            assertFalse(q.en, q.en.matches("^[\"“«].*") || q.ar.matches("^[\"“«].*"));
            assertTrue("twice: " + q.en, seen.add(q.en));
            assertTrue("twice: " + q.ar, seen.add(q.ar));
        }
    }

    @Test public void everyQuoteStaysAMinuteAndFifteenMinutesPassWithoutARepeat() throws IOException {
        assertEquals(60_000L, Quotes.SLOT_MS);
        assertTrue(Quotes.MIN_GAP * Quotes.SLOT_MS >= 15 * 60_000L);
        assertTrue("quotes: " + shipped().size(), shipped().size() > 2 * Quotes.MIN_GAP);
    }

    @Test public void everyShippedQuoteWasWrittenInArabic() throws IOException {
        for (Quotes.Quote q : shipped()) assertTrue(q.en, q.arabicFirst);
    }

    @Test public void theDeckShowsEveryQuoteOnceARoundAndNeverOneAgainWithinThirty() throws IOException {
        List<Quotes.Quote> quotes = shipped();
        int n = quotes.size();
        for (int seed = 0; seed < 40; seed++) {
            Quotes.Deck deck = new Quotes.Deck(quotes, new Random(seed), new ArrayList<>());
            List<Quotes.Quote> shown = new ArrayList<>();
            for (int i = 0; i < n * 5; i++) shown.add(deck.next());
            for (int round = 0; round < 5; round++) {
                assertEquals("seed " + seed, n, new HashSet<>(shown.subList(round * n, round * n + n)).size());
            }
            Map<Quotes.Quote, Integer> at = new IdentityHashMap<>();
            for (int i = 0; i < shown.size(); i++) {
                Integer before = at.put(shown.get(i), i);
                if (before != null) assertTrue("seed " + seed + ": again after " + (i - before), i - before > Quotes.MIN_GAP);
                if (i > 0) assertNotEquals("seed " + seed, shown.get(i - 1).enAuthor, shown.get(i).enAuthor);
            }
        }
    }

    @Test public void aRunDoesNotRepeatWhatTheLastRunShowedRecently() throws IOException {
        List<Quotes.Quote> quotes = shipped();
        List<String> recent = new ArrayList<>();
        for (int i = 0; i < Quotes.MIN_GAP; i++) recent.add(quotes.get(i).en);
        for (int seed = 0; seed < 40; seed++) {
            Quotes.Deck deck = new Quotes.Deck(quotes, new Random(seed), recent);
            for (int p = 0; p < Quotes.MIN_GAP; p++) {
                Quotes.Quote q = deck.next();
                int k = recent.indexOf(q.en);
                if (k >= 0) assertTrue("seed " + seed, recent.size() + p - k > Quotes.MIN_GAP);
            }
            List<String> carried = deck.recent();
            assertEquals(Quotes.MIN_GAP, carried.size());
        }
    }

    @Test public void aShortListStillNeverRepeatsAQuoteRunning() throws IOException {
        List<Quotes.Quote> two = parse("One\nA\n\u0661\n\u0623\u0644\u0641\n\nTwo\nB\n\u0662\n\u0628\u0627\u0621\n");
        for (int seed = 0; seed < 20; seed++) {
            assertEquals("Two", new Quotes.Deck(two, new Random(seed), Arrays.asList("One")).next().en);
        }
        List<Quotes.Quote> same = parse("One\nA\n\u0661\n\u0623\u0644\u0641\n\nTwo\nA\n\u0662\n\u0623\u0644\u0641\n\nThree\nA\n\u0663\n\u0623\u0644\u0641\n");
        Quotes.Deck deck = new Quotes.Deck(same, new Random(3), new ArrayList<>());
        String before = deck.next().en;
        for (int i = 0; i < 30; i++) {
            String en = deck.next().en;
            assertNotEquals(before, en);
            before = en;
        }
        assertTrue(new Quotes.Deck(parse(""), new Random(), new ArrayList<>()).isEmpty());
    }
}
