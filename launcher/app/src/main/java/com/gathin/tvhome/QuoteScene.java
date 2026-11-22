package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The quotes screensaver's scene (layout dream_quotes): one quote at a time on black over a slow ambient glow,
 * in Arabic and English, the language it was written in on top, each in quotation marks with the author's
 * name, centred on the screen. Each quote stays a full minute (Quotes.SLOT_MS): it rises in line by line,
 * stays, then fades out and the next one rises in, while the glow behind shifts colour. The quotes are res/raw/quotes.txt, in a shuffled order (Quotes.Deck).
 * Shared by QuoteDream (the system screensaver) and QuoteActivity (Home left idle).
 */
final class QuoteScene {

    private static final long FADE_OUT_MS = 1400L;
    private static final long FADE_IN_MS = 1300L;
    /** Each line starts this long after the one above it. */
    private static final long STAGGER_MS = 260L;
    /** The quote the last run ended on, so the next run does not open with it. */
    private static final String PREFS = "quotes", RECENT_KEY = "recent";
    /** Parts the quotes in RECENT_KEY: a quote's own text can hold a line break. */
    private static final String SEPARATOR = "\u001F";
    /** Names and the rule on a wallpaper or plain black; over the glow they take a pale tint of its colour. */
    private static final int ACCENT = 0xFFE8D9B5;
    /** The glow's colour change: 16 steps over 8 s. Animated per frame it redrew the full-screen gradient ~480 times. */
    private static final int GLOW_STEPS = 16;
    private static final long GLOW_STEP_MS = 500L;
    /** Deep, low-brightness glow colours; the glow is drawn at partial alpha on black. */
    private static final int[] PALETTE = {0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final View content;
    /** The language the quote was written in goes in the first pair, in larger type; its translation in the second. */
    private final TextView first, firstAuthor, second, secondAuthor;
    private final Typeface amiri, outfitLight, outfitMedium;
    private final View rule;
    /** The lines in the order they rise in. */
    private final View[] lines;
    private final DecelerateInterpolator ease = new DecelerateInterpolator(1.6f);
    private final Quotes.Deck deck;
    private Quotes.Quote shown;
    private final ImageView ambient;
    private final GradientDrawable glow;
    private int colorIndex;
    private int glowColor;
    /** clock_wallpaper or clock_glow false: something else (a picture, or plain black) replaced the coloured glow. */
    private boolean wallpaperShown;
    /** clock_wallpaper: the picture start() decodes, its darkening, and the resources to draw it with. */
    private File wallpaperFile;
    private int wallpaperShade;
    private Resources wallpaperRes;
    /** One number per decode request: a result whose number is no longer the current one is dropped. Main thread only. */
    private int wallpaperGeneration;
    /** clock_wallpaper with a collection: the pictures, how long each lasts, and how the next one is picked. */
    private List<File> rotation;
    private long rotationMs;
    private long shownMs;
    private String order;
    private Map<String, Set<String>> moods;
    private String latitude, longitude;

    /** The quote's time is up: fade it out, then bring in the next one. */
    private final Runnable turn = new Runnable() {
        @Override
        public void run() {
            long stayed = Quotes.SLOT_MS + FADE_OUT_MS;
            content.animate().alpha(0f).setDuration(FADE_OUT_MS).withLayer().withEndAction(() -> {
                shiftGlow();
                advanceWallpaper(stayed);
                show(deck.next());
            }).start();
        }
    };

    /** root is the inflated dream_quotes layout. */
    QuoteScene(View root) {
        content = root.findViewById(R.id.dreamContent);
        first = root.findViewById(R.id.quoteFirst);
        firstAuthor = root.findViewById(R.id.quoteFirstAuthor);
        rule = root.findViewById(R.id.quoteRule);
        second = root.findViewById(R.id.quoteSecond);
        secondAuthor = root.findViewById(R.id.quoteSecondAuthor);
        lines = new View[]{first, firstAuthor, rule, second, secondAuthor};
        Context ctx = root.getContext();
        amiri = ctx.getResources().getFont(R.font.amiri);
        outfitLight = ctx.getResources().getFont(R.font.outfit_light);
        outfitMedium = ctx.getResources().getFont(R.font.outfit_medium);
        String recent = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(RECENT_KEY, "");
        deck = new Quotes.Deck(load(ctx.getResources()), random,
                recent.isEmpty() ? new ArrayList<>() : Arrays.asList(recent.split(SEPARATOR)));
        ambient = root.findViewById(R.id.dreamAmbient);
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambient.setBackground(glow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * 0.55f));
        colorIndex = random.nextInt(PALETTE.length);
        setGlow(PALETTE[colorIndex]);
    }

    private static List<Quotes.Quote> load(Resources res) {
        try (Reader in = new InputStreamReader(res.openRawResource(R.raw.quotes), StandardCharsets.UTF_8)) {
            return Quotes.parse(in);
        } catch (IOException | RuntimeException e) {
            return new ArrayList<>();   // a black screen with the glow, rather than no screensaver
        }
    }

    void start() {
        handler.removeCallbacks(turn);
        content.animate().cancel();
        if (!deck.isEmpty()) show(deck.next());
        if (wallpaperFile != null && !wallpaperShown) loadWallpaper();
    }

    void stop() {
        handler.removeCallbacksAndMessages(null);   // the turn and any glow steps still queued
        content.animate().cancel();
        for (View line : lines) line.animate().cancel();
        if (shown != null) {
            content.getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(RECENT_KEY, String.join(SEPARATOR, deck.recent())).apply();
        }
        wallpaperGeneration++;   // a decode still running must not attach its picture to a stopped scene
    }

    /**
     * clock_wallpaper: put the picture Home is showing behind the quote, darkened by clock_wallpaper_dim, in
     * place of the glow, and carry a collection's rotation on while the screensaver is up. Decoded off the main
     * thread at screen size in RGB_565 (4 MB at 1080p), like Home's own; until then, or if it cannot be read,
     * the glow stays.
     * Call before start(), which starts the decode (again after a stop() that dropped an unfinished one).
     */
    void useHomeWallpaper(Context ctx) {
        Config cfg;
        try {
            cfg = Config.load(ctx);
        } catch (RuntimeException e) {
            return;
        }
        if (!cfg.clockWallpaper) {
            // clock_glow false: plain black behind the quote (OLED).
            if (!cfg.clockGlow) {
                wallpaperShown = true;   // also stops the glow's colour cycle
                ambient.setBackground(null);
            }
            return;
        }
        // What Home plays, and Config puts the single wallpaper in it when a theme has no collection.
        // Reading cfg.wallpaper first held the screensaver on wallpaper.jpg while Home rotated.
        if (cfg.wallpapers.isEmpty()) {
            wallpaperFile = null;
        } else {
            // Start on the picture Home is showing. WallpaperPlayer records it under the same
            // preferences and the screensaver runs in the same process, so this is just a read.
            rotation = new ArrayList<>(cfg.wallpapers);
            String shown = ctx.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE)
                    .getString("image", "");
            File current = WallpaperRotation.find(rotation, shown);
            wallpaperFile = current != null ? current : rotation.get(0);
            // Home only rotates while it is visible, so the screensaver carries the rotation on
            // itself; without this it holds one picture for as long as it is up.
            rotationMs = cfg.wallpaperRotationMinutes * 60_000L;
            order = cfg.wallpaperOrder;
            moods = cfg.wallpaperMoods;
            latitude = moods.isEmpty() ? "" : ctx.getString(R.string.latitude);
            longitude = moods.isEmpty() ? "" : ctx.getString(R.string.longitude);
        }
        wallpaperShade = (cfg.clockWallpaperDim * 255 / 100) << 24;
        wallpaperRes = ctx.getResources();
    }

    private void loadWallpaper() {
        File file = wallpaperFile;
        int shade = wallpaperShade;
        Resources res = wallpaperRes;
        DisplayMetrics dm = res.getDisplayMetrics();
        int w = dm.widthPixels, h = dm.heightPixels;
        int generation = ++wallpaperGeneration;
        new Thread(() -> {
            Bitmap bmp = decode(file, w, h);
            if (bmp == null) return;
            handler.post(() -> {
                if (generation != wallpaperGeneration || wallpaperShown) {
                    bmp.recycle();   // stopped meanwhile (or already shown): this picture was never attached
                    return;
                }
                wallpaperShown = true;
                // As the image, not the background: a background is stretched to the view, so a picture of
                // another shape than the screen would be distorted. centerCrop scales and crops it like Home.
                ambient.setBackground(null);
                ambient.setImageDrawable(new LayerDrawable(new Drawable[]{
                        new BitmapDrawable(res, bmp), new ColorDrawable(shade)}));
            });
        }, "quote-wallpaper").start();
    }

    /** WallpaperPlayer's decode: sampled and scaled down to cover the screen, never bigger than 4K. */
    private static Bitmap decode(File file, int screenW, int screenH) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, screenW, screenH);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(width / sample, height / sample, screenW, screenH);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }

    /** Step to the next picture once its turn is up; called as each quote leaves, with the time it stayed. */
    private void advanceWallpaper(long stayedMs) {
        if (rotation == null || rotationMs <= 0 || rotation.size() < 2) return;
        shownMs += stayedMs;
        if (shownMs < rotationMs) return;
        shownMs = 0;
        // The same pick as Home's, so a shuffled collection stays shuffled and wallpaper_moods still
        // decides what may show at this hour.
        File next = WallpaperRotation.pick(playable(), wallpaperFile.getName(), order, random);
        if (next.equals(wallpaperFile)) return;   // a mood group of one
        wallpaperFile = next;
        wallpaperShown = false;
        loadWallpaper();
    }

    /** WallpaperPlayer's filter: the pictures wallpaper_moods allows at this hour, or all of them. */
    private List<File> playable() {
        if (moods.isEmpty()) return rotation;
        LocalDateTime now = LocalDateTime.now();
        int[] sun = WallpaperMood.sun(now.toLocalDate(), latitude, longitude, ZoneId.systemDefault());
        return WallpaperMood.select(rotation, moods,
                WallpaperMood.timeOfDay(now.getHour() * 60 + now.getMinute(), sun[0], sun[1]));
    }

    /** Put the quote on screen: its lines rise in one after the other, and its turn to leave is booked. */
    private void show(Quotes.Quote quote) {
        shown = quote;
        boolean arabicFirst = quote.arabicFirst;
        arabic(arabicFirst ? first : second, arabicFirst ? firstAuthor : secondAuthor, quote, arabicFirst);
        english(arabicFirst ? second : first, arabicFirst ? secondAuthor : firstAuthor, quote, !arabicFirst);
        int accent = wallpaperShown ? ACCENT : tint(PALETTE[colorIndex]);
        firstAuthor.setTextColor(accent);
        secondAuthor.setTextColor(accent);
        rule.setBackgroundColor(accent & 0x99FFFFFF);
        centre();
        float rise = 14f * content.getResources().getDisplayMetrics().density;
        content.setAlpha(1f);
        long delay = 0;
        for (View line : lines) {
            line.animate().cancel();
            line.setAlpha(0f);
            line.setTranslationY(line == rule ? 0f : rise);
            if (line == rule) line.setScaleX(0.2f);
            line.animate().alpha(1f).translationY(0f).scaleX(1f).setStartDelay(delay)
                    .setDuration(FADE_IN_MS).setInterpolator(ease).withLayer().start();
            delay += STAGGER_MS;
        }
        handler.postDelayed(turn, Quotes.SLOT_MS);   // a full minute up before the fade-out starts
    }

    /**
     * The Arabic pair: Amiri, right to left, in guillemets. A long quote is set in smaller type, so every quote
     * sits comfortably inside the same column, and the translation is smaller than the original above it.
     */
    private void arabic(TextView text, TextView author, Quotes.Quote quote, boolean primary) {
        int length = quote.ar.length();
        int size = length <= 60 ? 40 : length <= 120 ? 35 : 29;
        text.setTypeface(amiri);
        text.setTextDirection(View.TEXT_DIRECTION_RTL);
        text.setLineSpacing(0f, 0.9f);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, primary ? size : size * 0.74f);
        text.setText("\u00AB" + quote.ar + "\u00BB");
        author.setTypeface(amiri);
        author.setTextDirection(View.TEXT_DIRECTION_RTL);
        author.setAllCaps(false);
        author.setLetterSpacing(0f);
        author.setTextSize(TypedValue.COMPLEX_UNIT_SP, primary ? 21 : 18);
        author.setText("\u2014 " + quote.arAuthor);
        gap(author, -8);   // Amiri leaves room under a line for its deepest letters; the name sits closer
    }

    /** The English pair: Outfit, left to right, in curly quotation marks, the name in spaced capitals. */
    private void english(TextView text, TextView author, Quotes.Quote quote, boolean primary) {
        int length = quote.en.length();
        int size = length <= 70 ? 32 : length <= 140 ? 27 : 22;
        text.setTypeface(outfitLight);
        text.setTextDirection(View.TEXT_DIRECTION_LTR);
        text.setLineSpacing(0f, 1.2f);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, primary ? size : size * 0.76f);
        text.setText("\u201C" + quote.en + "\u201D");
        author.setTypeface(outfitMedium);
        author.setTextDirection(View.TEXT_DIRECTION_LTR);
        author.setAllCaps(true);
        author.setLetterSpacing(0.16f);
        author.setTextSize(TypedValue.COMPLEX_UNIT_SP, primary ? 14 : 13);
        author.setText("\u2014 " + quote.enAuthor);
        gap(author, 16);
    }

    /** The space between a quote and the name under it, in dp. */
    private static void gap(View author, int dp) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) author.getLayoutParams();
        params.topMargin = Math.round(dp * author.getResources().getDisplayMetrics().density);
        author.setLayoutParams(params);
    }

    /** Every quote in the middle of the screen (the layout centres the column), with the glow behind it. */
    private void centre() {
        content.setTranslationX(0f);
        content.setTranslationY(0f);
        glow.setGradientCenter(0.5f, 0.5f);
    }

    /** The glow's colour, most of the way to white: readable as text, and still of the scene. */
    private static int tint(int color) {
        int r = (color >> 16 & 0xFF), g = (color >> 8 & 0xFF), b = (color & 0xFF);
        return 0xFF000000 | (r + (255 - r) * 7 / 10) << 16 | (g + (255 - g) * 7 / 10) << 8 | (b + (255 - b) * 7 / 10);
    }

    private void shiftGlow() {
        if (wallpaperShown) return;
        int from = glowColor;
        colorIndex = (colorIndex + 1) % PALETTE.length;
        int to = PALETTE[colorIndex];
        ArgbEvaluator mix = new ArgbEvaluator();
        for (int i = 1; i <= GLOW_STEPS; i++) {
            float f = (float) i / GLOW_STEPS;
            handler.postDelayed(() -> setGlow((int) mix.evaluate(f, from, to)), i * GLOW_STEP_MS);
        }
    }

    private void setGlow(int color) {
        glowColor = color;
        int c = color & 0x00FFFFFF;
        glow.setColors(new int[]{0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
    }
}
