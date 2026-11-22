package com.gathin.tvhome;

import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
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
        int arabicFirst = 0;
        for (Quotes.Quote q : shipped()) if (q.arabicFirst) arabicFirst++;
        assertTrue("written in Arabic: " + arabicFirst, arabicFirst >= 25 && arabicFirst < shipped().size());
    }

    @Test public void aBlockOfAnotherSizeIsLeftOut() throws IOException {
        assertTrue(parse("A quote with no author\n\nOne\nTwo\nThree\nFour\nFive\n").isEmpty());
        assertEquals(1, parse("\n\n# only notes\n\nQ\nA\nق\nم\n\n\n").size());
    }

    @Test public void everyShippedQuoteHasBothLanguagesAndAnAuthor() throws IOException {
        List<Quotes.Quote> quotes = shipped();
        assertTrue("quotes: " + quotes.size(), quotes.size() >= 60);
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

    @Test public void aQuoteStaysTwentyTwoToThirtySeconds() throws IOException {
        for (Quotes.Quote q : shipped()) {
            long ms = Quotes.holdMs(q);
            assertTrue(q.en + ": " + ms, ms >= 22_000L && ms <= 30_000L);
        }
        assertTrue(Quotes.holdMs(new Quotes.Quote("Short.", "A", "ق", "م", false))
                < Quotes.holdMs(new Quotes.Quote(new String(new char[150]).replace('\0', 'x'), "A", "ق", "م", false)));
    }

    @Test public void theDeckShowsEveryQuoteOnceBeforeAnyAgain() throws IOException {
        List<Quotes.Quote> quotes = shipped();
        Quotes.Deck deck = new Quotes.Deck(quotes, new Random(7), "");
        Set<Quotes.Quote> round = new HashSet<>();
        for (int i = 0; i < quotes.size(); i++) round.add(deck.next());
        assertEquals(quotes.size(), round.size());
    }

    @Test public void noQuoteOrAuthorFollowsItself() throws IOException {
        List<Quotes.Quote> quotes = shipped();
        for (int seed = 0; seed < 50; seed++) {
            Quotes.Deck deck = new Quotes.Deck(quotes, new Random(seed), "");
            Quotes.Quote before = deck.next();
            for (int i = 0; i < quotes.size() * 3; i++) {   // across two reshuffles
                Quotes.Quote q = deck.next();
                assertNotEquals(before.en, q.en);
                assertNotEquals("seed " + seed, before.enAuthor, q.enAuthor);
                before = q;
            }
        }
    }

    @Test public void aRunDoesNotOpenWithTheQuoteTheLastOneEndedOn() throws IOException {
        List<Quotes.Quote> two = parse("One\nA\n١\nألف\n\nTwo\nB\n٢\nباء\n");
        for (int seed = 0; seed < 20; seed++) {
            assertEquals("Two", new Quotes.Deck(two, new Random(seed), "One").next().en);
        }
    }

    @Test public void aListOfOneAuthorStillNeverRepeatsAQuote() throws IOException {
        List<Quotes.Quote> same = parse("One\nA\n١\nألف\n\nTwo\nA\n٢\nألف\n\nThree\nA\n٣\nألف\n");
        Quotes.Deck deck = new Quotes.Deck(same, new Random(3), "");
        String before = deck.next().en;
        for (int i = 0; i < 30; i++) {
            String en = deck.next().en;
            assertNotEquals(before, en);
            before = en;
        }
        assertTrue(new Quotes.Deck(parse(""), new Random(), "").isEmpty());
    }
}
