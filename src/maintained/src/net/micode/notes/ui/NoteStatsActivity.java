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

package net.micode.notes.ui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.NoteStatsCalculator;
import net.micode.notes.tool.NoteStatsRepository;

/**
 * 便签使用数据可视化页面（M09）。
 *
 * <p>职责边界：本类只做界面编排——读取快照、调用纯计算类、把结果填入控件与图表；
 * 统计规则在 {@link NoteStatsCalculator}，数据读取在 {@link NoteStatsRepository}。
 *
 * <p>线程模型：数据库查询放在后台线程，结果通过 runOnUiThread 回主线程渲染；
 * 页面销毁后才返回的结果会被丢弃，避免向已销毁的界面写数据。
 *
 * <p>数据口径：只统计非回收站的普通便签，字符数只计文本正文，文件夹数量只计用户文件夹，
 * 与验收要求一致。
 */
public class NoteStatsActivity extends Activity implements OnClickListener {

    private static final String DATE_LABEL_PATTERN = "M/d";

    private int mRangeDays = NoteStatsCalculator.RANGE_DAYS_7;

    private Button mRange7Button;

    private Button mRange30Button;

    private TextView mStatusView;

    private TextView mTotalNotesView;

    private TextView mTotalCharactersView;

    private TextView mRecentNotesView;

    private TextView mRecentCaptionView;

    private TextView mFolderCountView;

    private StatsBarChartView mTrendChart;

    private StatsBarChartView mFolderChart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_stats);
        setTitle(R.string.stats_title);

        mRange7Button = (Button) findViewById(R.id.btn_range_7);
        mRange30Button = (Button) findViewById(R.id.btn_range_30);
        mStatusView = (TextView) findViewById(R.id.tv_stats_status);
        mTotalNotesView = (TextView) findViewById(R.id.tv_total_notes);
        mTotalCharactersView = (TextView) findViewById(R.id.tv_total_characters);
        mRecentNotesView = (TextView) findViewById(R.id.tv_recent_notes);
        mRecentCaptionView = (TextView) findViewById(R.id.tv_recent_caption);
        mFolderCountView = (TextView) findViewById(R.id.tv_folder_count);
        mTrendChart = (StatsBarChartView) findViewById(R.id.chart_trend);
        mFolderChart = (StatsBarChartView) findViewById(R.id.chart_folder);

        mRange7Button.setOnClickListener(this);
        mRange30Button.setOnClickListener(this);

        loadStats();
    }

    @Override
    public void onClick(View view) {
        int days = view.getId() == R.id.btn_range_30
                ? NoteStatsCalculator.RANGE_DAYS_30 : NoteStatsCalculator.RANGE_DAYS_7;
        if (days == mRangeDays) {
            return;
        }
        mRangeDays = days;
        loadStats();
    }

    /**
     * 读取数据并渲染。重复点击时间范围时会发起新的查询，
     * 由于结果只用于覆盖界面，重复查询不会造成数据错乱。
     */
    private void loadStats() {
        mStatusView.setText(R.string.stats_loading);
        updateRangeButtons();
        final int rangeDays = mRangeDays;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final NoteStatsRepository.Snapshot snapshot =
                        new NoteStatsRepository().load(getApplicationContext());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) {
                            return;
                        }
                        render(snapshot, rangeDays);
                    }
                });
            }
        }).start();
    }

    /**
     * 把快照与当前时间范围渲染到界面。
     */
    private void render(NoteStatsRepository.Snapshot snapshot, int rangeDays) {
        long now = System.currentTimeMillis();
        Map<Long, String> folderNames = new HashMap<Long, String>(snapshot.folderNames);
        folderNames.put((long) Notes.ID_ROOT_FOLDER, getString(R.string.stats_root_folder));
        folderNames.put((long) Notes.ID_CALL_RECORD_FOLDER,
                getString(R.string.stats_call_record_folder));

        NoteStatsCalculator.Summary summary = NoteStatsCalculator.summarize(
                snapshot.notes, snapshot.folderCount, now, rangeDays);
        mTotalNotesView.setText(String.valueOf(summary.totalNotes));
        mTotalCharactersView.setText(String.valueOf(summary.totalCharacters));
        mRecentNotesView.setText(String.valueOf(summary.recentNotes));
        mRecentCaptionView.setText(getString(R.string.stats_recent_notes, rangeDays));
        mFolderCountView.setText(String.valueOf(summary.folderCount));

        int[] trend = NoteStatsCalculator.buildTrend(snapshot.notes, rangeDays, now);
        String[] trendLabels = buildTrendLabels(rangeDays, now);
        mTrendChart.setBars(trendLabels, trend);

        List<NoteStatsCalculator.FolderSlice> slices =
                NoteStatsCalculator.buildFolderDistribution(snapshot.notes, folderNames,
                        NoteStatsCalculator.MAX_FOLDER_SLICES,
                        getString(R.string.stats_unclassified),
                        getString(R.string.stats_others));
        String[] folderLabels = new String[slices.size()];
        int[] folderValues = new int[slices.size()];
        for (int i = 0; i < slices.size(); i++) {
            folderLabels[i] = slices.get(i).name;
            folderValues[i] = slices.get(i).count;
        }
        mFolderChart.setBars(folderLabels, folderValues);

        mStatusView.setText(snapshot.notes.isEmpty() ? getString(R.string.stats_empty) : "");
    }

    /**
     * 生成趋势图的横轴标签，顺序与 buildTrend 一致：最后一个元素是今天。
     */
    private String[] buildTrendLabels(int days, long now) {
        List<String> labels = new ArrayList<String>(days);
        SimpleDateFormat format = new SimpleDateFormat(DATE_LABEL_PATTERN, Locale.getDefault());
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        calendar.add(Calendar.DAY_OF_MONTH, -(days - 1));
        for (int i = 0; i < days; i++) {
            labels.add(format.format(calendar.getTime()));
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        return labels.toArray(new String[labels.size()]);
    }

    /**
     * 当前选中的时间范围按钮置灰，作为选择状态的可视反馈。
     */
    private void updateRangeButtons() {
        boolean isSevenDays = mRangeDays == NoteStatsCalculator.RANGE_DAYS_7;
        mRange7Button.setEnabled(!isSevenDays);
        mRange30Button.setEnabled(isSevenDays);
    }
}
