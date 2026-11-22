package com.gathin.tvhome;

import android.service.dreams.DreamService;

/**
 * Screensaver: quotes in Arabic and English with their authors, one at a time on black over a slow ambient
 * glow (QuoteScene). No network: the quotes and the Arabic typeface are in the APK.
 */
public class QuoteDream extends DreamService {

    private QuoteScene scene;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // the theme's locale, as QuoteActivity
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(true);   // it is there to be read
        setContentView(R.layout.dream_quotes);
        scene = new QuoteScene(getWindow().getDecorView());
        scene.useHomeWallpaper(this);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        scene.start();
    }

    @Override
    public void onDreamingStopped() {
        scene.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (scene != null) scene.stop();
        super.onDetachedFromWindow();
    }
}
