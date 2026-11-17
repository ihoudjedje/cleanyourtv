package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PaperTest {
    @Test
    public void defaultIsUltraBlack() {
        assertFalse(Paper.colorful(null));
        assertEquals(0xFF000000, Paper.BLACK);
    }
}
