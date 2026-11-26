package net.micode.notes.tool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import net.micode.notes.tool.NoteStatsCalculator.FolderSlice;
import net.micode.notes.tool.NoteStatsCalculator.NoteRecord;
import net.micode.notes.tool.NoteStatsCalculator.Summary;

/**
 * M09 统计逻辑的单元测试：不依赖 Android，可在 JVM 上重复运行。
 */
public class NoteStatsCalculatorTest {

    private static final long DAY = 24L * 60 * 60 * 1000;

    private static long dayStart(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    @Test
    public void summarizeCountsNotesCharactersAndRecent() {
        long today = dayStart(System.currentTimeMillis()) + 10 * 60 * 60 * 1000L;
        List<NoteRecord> notes = new ArrayList<NoteRecord>();
        notes.add(new NoteRecord(1, 0, today - 1 * DAY, 10));
        notes.add(new NoteRecord(2, 0, today - 3 * DAY, 20));
        notes.add(new NoteRecord(3, -5, today - 10 * DAY, 30));

        Summary summary = NoteStatsCalculator.summarize(notes, 2, today, 7);

        assertEquals(3, summary.totalNotes);
        assertEquals(60, summary.totalCharacters);
        assertEquals(2, summary.recentNotes);
        assertEquals(2, summary.folderCount);
    }

    @Test
    public void summarizeHandlesNullAndEmptyInput() {
        Summary summary = NoteStatsCalculator.summarize(null, -1, System.currentTimeMillis(), 7);
        assertEquals(0, summary.totalNotes);
        assertEquals(0, summary.totalCharacters);
        assertEquals(0, summary.recentNotes);
        assertEquals(0, summary.folderCount);
    }

    @Test
    public void summarizeRecentWindowCoversTodayAndSixDaysBefore() {
        long today = dayStart(System.currentTimeMillis()) + 60 * 1000L;
        List<NoteRecord> notes = new ArrayList<NoteRecord>();
        notes.add(new NoteRecord(1, 0, today, 1));
        notes.add(new NoteRecord(2, 0, today - 6 * DAY, 1));
        notes.add(new NoteRecord(3, 0, today - 7 * DAY, 1));

        Summary summary = NoteStatsCalculator.summarize(notes, 0, today, 7);
        assertEquals(2, summary.recentNotes);
    }

    @Test
    public void buildTrendPlacesCountsOnCorrectDays() {
        long today = dayStart(System.currentTimeMillis()) + 12 * 60 * 60 * 1000L;
        List<NoteRecord> notes = new ArrayList<NoteRecord>();
        notes.add(new NoteRecord(1, 0, today, 1));
        notes.add(new NoteRecord(2, 0, today, 1));
        notes.add(new NoteRecord(3, 0, today - 2 * DAY, 1));

        int[] trend = NoteStatsCalculator.buildTrend(notes, 7, today);

        assertEquals(7, trend.length);
        assertEquals(2, trend[6]);
        assertEquals(1, trend[4]);
        assertEquals(0, trend[0]);
    }

    @Test
    public void buildTrendIgnoresFutureAndOutOfRangeDates() {
        long today = dayStart(System.currentTimeMillis()) + 12 * 60 * 60 * 1000L;
        List<NoteRecord> notes = new ArrayList<NoteRecord>();
        notes.add(new NoteRecord(1, 0, today + DAY, 1));
        notes.add(new NoteRecord(2, 0, today - 9 * DAY, 1));

        int[] trend = NoteStatsCalculator.buildTrend(notes, 7, today);

        int total = 0;
        for (int value : trend) {
            total += value;
        }
        assertEquals(0, total);
        assertEquals(0, NoteStatsCalculator.buildTrend(notes, 0, today).length);
    }

    @Test
    public void buildFolderDistributionSortsAndMergesOthers() {
        long now = System.currentTimeMillis();
        List<NoteRecord> notes = new ArrayList<NoteRecord>();
        for (int i = 0; i < 3; i++) {
            notes.add(new NoteRecord(i, 1, now, 1));
        }
        notes.add(new NoteRecord(10, 2, now, 1));
        notes.add(new NoteRecord(11, 2, now, 1));
        notes.add(new NoteRecord(12, 3, now, 1));
        notes.add(new NoteRecord(13, 0, now, 1));
        notes.add(new NoteRecord(14, 99, now, 1));
        Map<Long, String> names = new HashMap<Long, String>();
        names.put(1L, "工作");
        names.put(2L, "生活");
        names.put(3L, "学习");
        names.put(0L, "根目录");

        List<FolderSlice> slices = NoteStatsCalculator.buildFolderDistribution(
                notes, names, 3, "未分类", "其他");

        assertEquals(3, slices.size());
        assertEquals("工作", slices.get(0).name);
        assertEquals(3, slices.get(0).count);
        assertEquals("生活", slices.get(1).name);
        assertEquals(2, slices.get(1).count);
        assertEquals("其他", slices.get(2).name);
        assertEquals(3, slices.get(2).count);
    }

    @Test
    public void buildFolderDistributionKeepsAllWhenBelowLimit() {
        long now = System.currentTimeMillis();
        List<NoteRecord> notes = new ArrayList<NoteRecord>();
        notes.add(new NoteRecord(1, 1, now, 1));
        Map<Long, String> names = new HashMap<Long, String>();
        names.put(1L, "工作");

        List<FolderSlice> slices = NoteStatsCalculator.buildFolderDistribution(
                notes, names, 6, "未分类", "其他");

        assertEquals(1, slices.size());
        assertEquals("工作", slices.get(0).name);
        assertTrue(slices.get(0).count == 1);
    }
}
