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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * 统计页面使用的极简柱状图控件（M09）。
 *
 * <p>职责边界：只负责把“标签 + 数值”画成柱状图，不做统计、不做数据读取。
 * 之所以不用第三方图表库，是为了不引入新的依赖和网络下载，保持与原项目的轻量集成。
 *
 * <p>绘制规则：
 * <ul>
 *   <li>柱高按最大值为基准等比缩放，最大值为 0 时所有柱高为 0，但仍绘制坐标轴与标签；</li>
 *   <li>柱子数量超过 10 个时，每隔 5 个绘制一次横轴标签，避免文字重叠；</li>
 *   <li>空数据时只显示一条基线，不绘制任何文字，由页面另行提示“暂无便签数据”。</li>
 * </ul>
 */
public class StatsBarChartView extends View {

    private static final int BAR_COLOR = 0xFF4C8BF5;

    private static final int VALUE_TEXT_COLOR = 0xFF666666;

    private static final int LABEL_TEXT_COLOR = 0xFF999999;

    private static final int AXIS_COLOR = 0xFFE0E0E0;

    private String[] mLabels = new String[0];

    private int[] mValues = new int[0];

    private final Paint mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF mBarRect = new RectF();

    public StatsBarChartView(Context context) {
        super(context);
        init();
    }

    public StatsBarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mBarPaint.setColor(BAR_COLOR);
        mTextPaint.setColor(VALUE_TEXT_COLOR);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * 注入图表数据。
     *
     * <p>边界处理：标签与数值长度不一致时，以较短的为准截断，避免绘制时数组越界。
     */
    public void setBars(String[] labels, int[] values) {
        int size = Math.min(labels == null ? 0 : labels.length, values == null ? 0 : values.length);
        mLabels = new String[size];
        mValues = new int[size];
        System.arraycopy(labels, 0, mLabels, 0, size);
        System.arraycopy(values, 0, mValues, 0, size);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        float paddingLeft = 12 * density;
        float paddingRight = 12 * density;
        float paddingTop = 20 * density;
        float paddingBottom = 24 * density;
        float chartWidth = width - paddingLeft - paddingRight;
        float chartHeight = height - paddingTop - paddingBottom;
        float baseline = paddingTop + chartHeight;

        Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        axisPaint.setColor(AXIS_COLOR);
        axisPaint.setStrokeWidth(Math.max(1f, density));
        canvas.drawLine(paddingLeft, baseline, width - paddingRight, baseline, axisPaint);

        int size = mValues.length;
        if (size == 0 || chartWidth <= 0 || chartHeight <= 0) {
            return;
        }

        int maxValue = 0;
        for (int value : mValues) {
            maxValue = Math.max(maxValue, value);
        }
        float slotWidth = chartWidth / size;
        float barWidth = Math.min(slotWidth * 0.6f, 44 * density);
        mTextPaint.setTextSize(10 * density);

        boolean denseLabels = size > 10;
        for (int i = 0; i < size; i++) {
            float centerX = paddingLeft + slotWidth * i + slotWidth / 2f;
            float barHeight = maxValue == 0 ? 0
                    : chartHeight * mValues[i] / (float) maxValue;
            if (barHeight > 0) {
                mBarRect.set(centerX - barWidth / 2f, baseline - barHeight,
                        centerX + barWidth / 2f, baseline);
                canvas.drawRoundRect(mBarRect, 3 * density, 3 * density, mBarPaint);
                mTextPaint.setColor(VALUE_TEXT_COLOR);
                canvas.drawText(String.valueOf(mValues[i]), centerX,
                        baseline - barHeight - 4 * density, mTextPaint);
            } else if (maxValue == 0) {
                mTextPaint.setColor(VALUE_TEXT_COLOR);
                canvas.drawText("0", centerX, baseline - 4 * density, mTextPaint);
            }

            boolean drawLabel = !denseLabels || (size - 1 - i) % 5 == 0;
            if (drawLabel) {
                mTextPaint.setColor(LABEL_TEXT_COLOR);
                canvas.drawText(ellipsize(mLabels[i]), centerX, baseline + 16 * density,
                        mTextPaint);
            }
        }
    }

    /**
     * 横轴标签最长保留 5 个字符，超出部分用省略号，避免长文件夹名互相覆盖。
     */
    private String ellipsize(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= 5) {
            return text;
        }
        return text.substring(0, 4) + "…";
    }

    /**
     * 供测试与排查使用：返回当前柱子数量。
     */
    int getBarCount() {
        return mValues.length;
    }

    /**
     * 供测试与排查使用：返回指定柱子的数值，越界返回 0。
     */
    int getBarValue(int index) {
        if (index < 0 || index >= mValues.length) {
            return 0;
        }
        return mValues[index];
    }

    /**
     * 仅供预览时设置文字颜色，实际页面使用默认配色。
     */
    public void setTextColorForPreview(int color) {
        if (color != Color.TRANSPARENT) {
            mTextPaint.setColor(color);
            invalidate();
        }
    }
}
