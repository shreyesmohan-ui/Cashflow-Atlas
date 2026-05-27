package com.shrey.cashflowatlas;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import com.shrey.cashflowatlas.analytics.FinanceAnalyzer;
import com.shrey.cashflowatlas.model.CategoryRule;
import com.shrey.cashflowatlas.model.FinanceState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AtlasView extends View {
    public enum Mode { FLOW, RISK, MERCHANTS }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private FinanceState state;
    private FinanceAnalyzer.Summary summary;
    private Mode mode = Mode.FLOW;
    private boolean privacyMask = false;

    public AtlasView(Context context) {
        super(context);
        setMinimumHeight(dp(340));
    }

    public void setData(FinanceState state, FinanceAnalyzer.Summary summary, Mode mode, boolean privacyMask) {
        this.state = state;
        this.summary = summary;
        this.mode = mode;
        this.privacyMask = privacyMask;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (state == null || summary == null) {
            return;
        }
        drawBackground(canvas);
        if (mode == Mode.RISK) {
            drawRisk(canvas);
        } else if (mode == Mode.MERCHANTS) {
            drawMerchants(canvas);
        } else {
            drawFlow(canvas);
        }
    }

    private void drawBackground(Canvas canvas) {
        canvas.drawColor(Color.rgb(255, 253, 244));
        paint.setColor(Color.argb(22, 31, 41, 51));
        paint.setStrokeWidth(1);
        for (int x = 0; x < getWidth(); x += dp(42)) {
            canvas.drawLine(x, 0, x, getHeight(), paint);
        }
        for (int y = 0; y < getHeight(); y += dp(42)) {
            canvas.drawLine(0, y, getWidth(), y, paint);
        }
    }

    private void drawFlow(Canvas canvas) {
        float height = getHeight();
        float width = getWidth();
        float incomeX = dp(80);
        float centerY = height * 0.47f;
        float splitX = width * 0.42f;
        float saveY = centerY - dp(76);
        float spendY = centerY + dp(76);

        drawNode(canvas, incomeX, centerY, dp(66), Color.rgb(5, 150, 105), "Income", rupees(summary.effectiveIncome));
        drawNode(canvas, splitX, saveY, dp(52), Color.rgb(14, 165, 233), "Save", rupees(Math.max(summary.freeCash, 0)));
        drawNode(canvas, splitX, spendY, dp(52), Color.rgb(249, 115, 22), "Actual", rupees(summary.actualSpend));

        drawRibbon(canvas, incomeX + dp(65), centerY, splitX - dp(52), saveY, Color.rgb(14, 165, 233), dp(20));
        float spendWidth = summary.plannedSpend > 0 ? Math.max(dp(12), dp(26) * (float) Math.min(1.35, summary.actualSpend / summary.plannedSpend)) : dp(12);
        drawRibbon(canvas, incomeX + dp(65), centerY, splitX - dp(52), spendY, Color.rgb(249, 115, 22), spendWidth);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(dp(12));
        paint.setColor(Color.rgb(99, 112, 131));
        canvas.drawText("Plan " + rupees(summary.plannedSpend) + " | SMS " + rupees(summary.importedSmsSpend), dp(18), height - dp(22), paint);

        float max = 1;
        for (CategoryRule category : state.categories) {
            max = Math.max(max, (float) Math.max(category.budget, getActual(category.id)));
        }

        float startX = width * 0.64f;
        for (int i = 0; i < state.categories.size(); i += 1) {
            CategoryRule category = state.categories.get(i);
            float actual = (float) getActual(category.id);
            float value = Math.max(actual, (float) category.budget);
            float radius = dp(22) + (value / max) * dp(18);
            float x = startX + (i % 2) * dp(128);
            float y = dp(54) + (i / 2) * dp(76);
            drawRibbon(canvas, splitX + dp(52), spendY, x - radius, y, category.color, dp(9));
            drawNode(canvas, x, y, radius, category.color, category.label, rupees(actual));
        }
    }

    private void drawRisk(Canvas canvas) {
        float left = dp(18);
        float top = dp(44);
        float row = dp(42);
        float barLeft = dp(122);
        float barWidth = getWidth() - barLeft - dp(22);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(dp(18));
        paint.setColor(Color.rgb(31, 41, 51));
        canvas.drawText("Budget risk & forecast", left, dp(28), paint);

        for (int i = 0; i < state.categories.size(); i += 1) {
            CategoryRule category = state.categories.get(i);
            float y = top + i * row;
            float actual = (float) getActual(category.id);
            Double forecastVal = summary.forecastedByCategory.get(category.id);
            float forecast = forecastVal != null ? forecastVal.floatValue() : actual;
            
            float ratioActual = category.budget > 0 ? Math.min(1.25f, actual / (float) category.budget) : 0;
            float ratioForecast = category.budget > 0 ? Math.min(1.25f, forecast / (float) category.budget) : 0;

            paint.setTextSize(dp(12));
            paint.setColor(Color.rgb(31, 41, 51));
            canvas.drawText(category.label, left, y + dp(14), paint);
            
            // Background
            paint.setColor(Color.argb(25, 31, 41, 51));
            canvas.drawRoundRect(new RectF(barLeft, y, barLeft + barWidth, y + dp(14)), dp(8), dp(8), paint);
            
            // Forecast (Ghost bar)
            paint.setColor(withAlpha(category.color, 60));
            canvas.drawRoundRect(new RectF(barLeft, y, barLeft + barWidth * Math.min(1, ratioForecast), y + dp(14)), dp(8), dp(8), paint);

            // Actual
            paint.setColor(ratioActual > 1 ? Color.rgb(220, 38, 38) : category.color);
            canvas.drawRoundRect(new RectF(barLeft, y, barLeft + barWidth * Math.min(1, ratioActual), y + dp(14)), dp(8), dp(8), paint);
            
            // Budget Marker
            paint.setColor(Color.rgb(31, 41, 51));
            paint.setStrokeWidth(dp(1.5f));
            canvas.drawLine(barLeft + barWidth * 0.8f, y - dp(2), barLeft + barWidth * 0.8f, y + dp(16), paint); // Normalized budget at 80% width

            paint.setColor(Color.rgb(99, 112, 131));
            canvas.drawText(rupees(actual) + " (fc: " + rupees(forecast) + ")", barLeft, y + dp(32), paint);
        }
    }

    private void drawMerchants(Canvas canvas) {
        float left = dp(18);
        float top = dp(44);
        float width = getWidth();
        float height = getHeight();

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(dp(18));
        paint.setColor(Color.rgb(31, 41, 51));
        boolean usingSms = !summary.smsSpendByMerchant.isEmpty();
        canvas.drawText(usingSms ? "Imported SMS merchant map" : "Merchant intelligence map", left, dp(28), paint);

        Map<String, Double> source = usingSms ? summary.smsSpendByMerchant : summary.spendByMerchant;
        List<Map.Entry<String, Double>> merchants = new ArrayList<>(source.entrySet());
        java.util.Collections.sort(merchants, (a, b) -> b.getValue().compareTo(a.getValue()));

        int count = Math.min(8, merchants.size());
        float maxSpend = count > 0 ? merchants.get(0).getValue().floatValue() : 1;

        for (int i = 0; i < count; i++) {
            Map.Entry<String, Double> entry = merchants.get(i);
            float ratio = entry.getValue().floatValue() / maxSpend;
            float radius = dp(24) + ratio * dp(32);

            // Simple spiral layout
            float angle = i * 0.9f;
            float dist = i * dp(36);
            float x = width / 2 + (float) Math.cos(angle) * dist;
            float y = height / 2 + (float) Math.sin(angle) * dist;

            int color = Color.rgb(15, 118, 110); // Teal-700
            drawNode(canvas, x, y, radius, color, entry.getKey(), rupees(entry.getValue()));
        }
    }

    private void drawNode(Canvas canvas, float x, float y, float radius, int color, String label, String value) {
        paint.setColor(withAlpha(color, 44));
        canvas.drawCircle(x, y, radius + dp(9), paint);
        paint.setColor(color);
        canvas.drawCircle(x, y, radius, paint);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(Math.max(dp(9), radius / 4));
        canvas.drawText(label, x, y - dp(2), paint);
        paint.setTextSize(Math.max(dp(8), radius / 5));
        canvas.drawText(value, x, y + dp(14), paint);
    }

    private void drawRibbon(Canvas canvas, float x1, float y1, float x2, float y2, int color, float strokeWidth) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(withAlpha(color, 56));
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(x1, y1);
        path.cubicTo((x1 + x2) / 2, y1, (x1 + x2) / 2, y2, x2, y2);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private double getActual(String categoryId) {
        Double value = summary.actualByCategory.get(categoryId);
        return value == null ? 0 : value;
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private String rupees(double value) {
        if (privacyMask) return "Rs XXX";
        return "Rs " + Math.round(value);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
