package com.gathin.tvhome;

import android.content.SharedPreferences;

/**
 * Ultra-black paper vs the theme's colourful Home. Used only while {@code paper_toggle} is on;
 * nothing is read or written while that switch is off. Ultra black until the owner taps.
 */
final class Paper {
    static final String KEY = "paper_colorful";
    static final int BLACK = 0xFF000000;

    private Paper() { }

    static boolean colorful(SharedPreferences prefs) {
        return prefs != null && prefs.getBoolean(KEY, false);
    }
}
