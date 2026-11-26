package net.micode.notes.tool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * M10 阅读偏好与阅读时长估算的单元测试：不依赖 Android，可在 JVM 上重复运行。
 */
public class ReadingPrefsTest {

    @Test
    public void normalizeThemeFallsBackToDefaultOnIllegalValue() {
        assertEquals(ReadingPrefs.THEME_LIGHT, ReadingPrefs.normalizeTheme(-1));
        assertEquals(ReadingPrefs.THEME_LIGHT, ReadingPrefs.normalizeTheme(3));
        assertEquals(ReadingPrefs.THEME_DARK, ReadingPrefs.normalizeTheme(ReadingPrefs.THEME_DARK));
    }

    @Test
    public void normalizeFontAndSpacingLevelStayInRange() {
        assertEquals(ReadingPrefs.DEFAULT_FONT_LEVEL, ReadingPrefs.normalizeFontLevel(-2));
        // 越界值统一回落到默认档，而不是截断到边界档
        assertEquals(ReadingPrefs.DEFAULT_FONT_LEVEL, ReadingPrefs.normalizeFontLevel(99));
        assertEquals(ReadingPrefs.DEFAULT_LINE_SPACING_LEVEL,
                ReadingPrefs.normalizeLineSpacingLevel(-5));
        assertEquals(ReadingPrefs.DEFAULT_LINE_SPACING_LEVEL,
                ReadingPrefs.normalizeLineSpacingLevel(5));
        assertEquals(ReadingPrefs.FONT_SIZES_SP.length - 1, ReadingPrefs.normalizeFontLevel(2));
    }

    @Test
    public void estimateReadingMinutesHandlesBoundaries() {
        assertEquals(0, ReadingPrefs.estimateReadingMinutes(0));
        assertEquals(0, ReadingPrefs.estimateReadingMinutes(-10));
        assertEquals(1, ReadingPrefs.estimateReadingMinutes(1));
        assertEquals(1, ReadingPrefs.estimateReadingMinutes(ReadingPrefs.CHARACTERS_PER_MINUTE));
        assertEquals(2,
                ReadingPrefs.estimateReadingMinutes(ReadingPrefs.CHARACTERS_PER_MINUTE + 1));
        assertEquals(10, ReadingPrefs.estimateReadingMinutes(3000));
    }

    @Test
    public void fontSizeAndLineSpacingFollowLevelMapping() {
        assertEquals(ReadingPrefs.FONT_SIZES_SP[0], ReadingPrefs.fontSizeSp(0));
        assertEquals(ReadingPrefs.FONT_SIZES_SP[2], ReadingPrefs.fontSizeSp(2));
        assertTrue(ReadingPrefs.fontSizeSp(0) < ReadingPrefs.fontSizeSp(2));
        assertTrue(ReadingPrefs.lineSpacingMultiplier(0) < ReadingPrefs.lineSpacingMultiplier(2));
    }

    @Test
    public void colorsAreOpaqueForAllThemes() {
        for (int theme = 0; theme < ReadingPrefs.THEME_BACKGROUND_COLORS.length; theme++) {
            int background = ReadingPrefs.backgroundColor(theme);
            int text = ReadingPrefs.textColor(theme);
            assertEquals(0xFF, (background >>> 24) & 0xFF);
            assertEquals(0xFF, (text >>> 24) & 0xFF);
            assertTrue(background != text);
        }
    }
}
