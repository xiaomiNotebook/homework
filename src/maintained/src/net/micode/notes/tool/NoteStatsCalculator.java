/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.tool;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 便签使用统计的纯计算逻辑（M09）。
 *
 * <p>职责边界：本类只做“已知便签列表 → 统计结果”的换算，不访问数据库、不持有 Context、
 * 不依赖 Android 类，因此可以脱离设备和模拟器做单元测试。数据读取由
 * {@link NoteStatsRepository} 负责，展示由统计页面负责。
 *
 * <p>约定：所有入参的便签列表都应当已经排除回收站；为保证健壮性，本类仍不会抛异常，
 * 传入空列表时返回全 0 统计，传入 null 时同样按空列表处理。
 */
public final class NoteStatsCalculator {

    /** 近 7 天范围（趋势图与“近期新增”都使用该常量）。 */
    public static final int RANGE_DAYS_7 = 7;

    /** 近 30 天范围。 */
    public static final int RANGE_DAYS_30 = 30;

    /** 文件夹分布图最多展示的条目数，超出部分合并为“其他”。 */
    public static final int MAX_FOLDER_SLICES = 6;

    private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;

    private NoteStatsCalculator() {
        // 工具类不允许实例化
    }

    /**
     * 参与统计的一条便签的只读快照。
     *
     * <p>字段刻意保持最小：只保留统计需要的 id、所属文件夹、创建时间和正文字符数，
     * 避免把整条便签内容加载进内存。
     */
    public static final class NoteRecord {
        public final long id;
        public final long folderId;
        public final long createdDate;
        public final int contentLength;

        public NoteRecord(long id, long folderId, long createdDate, int contentLength) {
            this.id = id;
            this.folderId = folderId;
            this.createdDate = createdDate;
            this.contentLength = Math.max(0, contentLength);
        }
    }

    /**
     * 统计汇总结果：便签总数、累计字符数、近期新增数量、文件夹数量。
     */
    public static final class Summary {
        public final int totalNotes;
        public final long totalCharacters;
        public final int recentNotes;
        public final int folderCount;

        public Summary(int totalNotes, long totalCharacters, int recentNotes, int folderCount) {
            this.totalNotes = totalNotes;
            this.totalCharacters = totalCharacters;
            this.recentNotes = recentNotes;
            this.folderCount = folderCount;
        }
    }

    /**
     * 文件夹分布的一项：文件夹名称与该文件夹下的便签数。
     */
    public static final class FolderSlice {
        public final String name;
        public final int count;

        public FolderSlice(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }

    /**
     * 汇总统计。
     *
     * <p>近期新增的判定口径：创建时间落在“今天往前数 recentDays 天”的日界内，
     * 采用自然日而不是“最近 24 小时 × N”，与界面上“近 7 天/近 30 天”的表述一致。
     *
     * @param notes      已排除回收站的便签列表，可为 null
     * @param folderCount 有效文件夹数量（由数据层统计，含根目录外的用户文件夹）
     * @param now        当前时间，便于测试注入固定时钟
     * @param recentDays 近期天数，小于等于 0 时近期新增恒为 0
     */
    public static Summary summarize(List<NoteRecord> notes, int folderCount, long now, int recentDays) {
        List<NoteRecord> safeNotes = notes == null ? Collections.<NoteRecord>emptyList() : notes;
        long totalCharacters = 0;
        long recentStart = recentDays > 0 ? dayStart(now) - (recentDays - 1) * MILLIS_PER_DAY : now + 1;
        int recentNotes = 0;
        for (NoteRecord note : safeNotes) {
            totalCharacters += note.contentLength;
            if (note.createdDate >= recentStart && note.createdDate <= now) {
                recentNotes++;
            }
        }
        return new Summary(safeNotes.size(), totalCharacters, recentNotes, Math.max(0, folderCount));
    }

    /**
     * 构建趋势数据，数组下标 0 表示最早的日期，最后一个元素表示今天。
     *
     * <p>实现要点：以“今天 0 点”为锚点，用 Calendar 逐日回退来定位每天的起点，
     * 而不是简单减去 86400000，避免夏令时或时区规则导致日界偏移。
     *
     * @param notes 已排除回收站的便签列表，可为 null
     * @param days  统计天数，小于等于 0 时返回空数组
     * @param now   当前时间
     * @return 每天的便签新增数量
     */
    public static int[] buildTrend(List<NoteRecord> notes, int days, long now) {
        if (days <= 0) {
            return new int[0];
        }
        List<NoteRecord> safeNotes = notes == null ? Collections.<NoteRecord>emptyList() : notes;
        int[] counts = new int[days];
        long[] starts = new long[days];
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(dayStart(now));
        starts[days - 1] = calendar.getTimeInMillis();
        for (int i = days - 2; i >= 0; i--) {
            calendar.add(Calendar.DAY_OF_MONTH, -1);
            starts[i] = calendar.getTimeInMillis();
        }
        for (NoteRecord note : safeNotes) {
            if (note.createdDate < starts[0] || note.createdDate > now) {
                continue;
            }
            for (int i = days - 1; i >= 0; i--) {
                if (note.createdDate >= starts[i]) {
                    counts[i]++;
                    break;
                }
            }
        }
        return counts;
    }

    /**
     * 构建文件夹分布：按便签数降序排列，数量相同时按名称升序，最多保留 maxSlices 项，
     * 其余条目合并为 otherLabel，保证图表可读且总量守恒。
     *
     * <p>未知的文件夹 id 统一归入 unknownLabel，避免因为文件夹已被删除而丢失统计。
     * 两个标签由调用方传入而不是写死，便于界面按语言环境显示。
     *
     * @param notes       已排除回收站的便签列表，可为 null
     * @param folderNames 文件夹 id 到显示名称的映射，可为 null
     * @param maxSlices   最多展示的条目数，小于等于 0 时使用默认值
     * @param unknownLabel 未知文件夹的归并名称
     * @param otherLabel  超出展示数量后的归并名称
     */
    public static List<FolderSlice> buildFolderDistribution(List<NoteRecord> notes,
            Map<Long, String> folderNames, int maxSlices, String unknownLabel,
            String otherLabel) {
        List<NoteRecord> safeNotes = notes == null ? Collections.<NoteRecord>emptyList() : notes;
        Map<Long, String> names = folderNames == null ? new HashMap<Long, String>() : folderNames;
        int limit = maxSlices <= 0 ? MAX_FOLDER_SLICES : maxSlices;

        Map<String, Integer> countByName = new HashMap<String, Integer>();
        for (NoteRecord note : safeNotes) {
            String name = names.get(note.folderId);
            if (name == null || name.length() == 0) {
                name = unknownLabel;
            }
            Integer old = countByName.get(name);
            countByName.put(name, old == null ? 1 : old + 1);
        }

        List<FolderSlice> slices = new ArrayList<FolderSlice>();
        for (Map.Entry<String, Integer> entry : countByName.entrySet()) {
            slices.add(new FolderSlice(entry.getKey(), entry.getValue()));
        }
        Collections.sort(slices, new Comparator<FolderSlice>() {
            @Override
            public int compare(FolderSlice left, FolderSlice right) {
                if (left.count != right.count) {
                    return right.count - left.count;
                }
                return left.name.compareTo(right.name);
            }
        });

        if (slices.size() <= limit) {
            return slices;
        }
        List<FolderSlice> result = new ArrayList<FolderSlice>(limit);
        int others = 0;
        for (int i = 0; i < slices.size(); i++) {
            if (i < limit - 1) {
                result.add(slices.get(i));
            } else {
                others += slices.get(i).count;
            }
        }
        result.add(new FolderSlice(otherLabel, others));
        return result;
    }

    /**
     * 取给定时刻所在自然日的 0 点毫秒值。
     */
    static long dayStart(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}
