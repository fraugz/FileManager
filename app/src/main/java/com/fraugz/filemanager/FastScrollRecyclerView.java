package com.fraugz.filemanager;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.util.Date;

public class FastScrollRecyclerView extends RecyclerView {

    /** Implemented by adapters to name the row the thumb is passing while it is dragged. */
    public interface LabelProvider {
        String getFastScrollLabel(Context context, int position);
    }

    private static final int IDLE_COLOR = 0xFF8E8E93;

    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF thumb = new RectF();
    private final RectF bubble = new RectF();
    private final int activeColor;
    private final float density;
    private final float scale;
    private boolean dragging;
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;
    private float dragOffset;
    private float dragFraction;
    private String label;

    public FastScrollRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        scale = Math.max(1f, ThemeManager.getUiScale(context));
        boolean dark = ThemeManager.getTheme(context) == ThemeManager.THEME_DARK;
        activeColor = dark ? 0xFF1E88E5 : 0xFF1565C0;
        bubblePaint.setColor(activeColor);
        labelPaint.setColor(0xFFFFFFFF);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(dp(18));
        labelPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        setVerticalScrollBarEnabled(false);
    }

    /** "Today", "Yesterday" or a medium date in the app language, e.g. "13 nov 2026". */
    static String dateLabel(Context context, long timestamp) {
        if (DateUtils.isToday(timestamp)) return context.getString(R.string.today);
        if (DateUtils.isToday(timestamp + DateUtils.DAY_IN_MILLIS)) {
            return context.getString(R.string.yesterday);
        }
        return DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(timestamp));
    }

    private float dp(float value) {
        return value * density * scale;
    }

    private float travel() {
        return getHeight() - dp(56);
    }

    private boolean updateThumb() {
        LayoutManager manager = getLayoutManager();
        if (!(manager instanceof LinearLayoutManager)
                || !manager.canScrollVertically()
                || getAdapter() == null || getAdapter().getItemCount() == 0
                || getHeight() <= dp(56)
                || (!canScrollVertically(-1) && !canScrollVertically(1))) {
            thumb.setEmpty();
            return false;
        }

        int range = computeVerticalScrollRange() - computeVerticalScrollExtent();
        if (range <= 0) {
            thumb.setEmpty();
            return false;
        }
        float fraction = dragging ? dragFraction
                : Math.max(0f, Math.min(1f, (float) computeVerticalScrollOffset() / range));
        float top = dp(4) + fraction * travel();
        float right = getWidth() - dp(4);
        // Slim pill at rest; slightly wider while held. The touch target stays 48dp wide.
        thumb.set(right - dp(dragging ? 8 : 6), top, right, top + dp(48));
        return true;
    }

    boolean hitsFastScrollThumb(float x, float y) {
        return updateThumb() && x >= getWidth() - dp(48) && x <= getWidth()
                && y >= thumb.top && y <= thumb.bottom;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (!updateThumb()) return;
        thumbPaint.setColor(dragging ? activeColor : IDLE_COLOR);
        float radius = thumb.width() / 2f;
        canvas.drawRoundRect(thumb, radius, radius, thumbPaint);
        if (!dragging) return;
        updateLabel();
        if (label != null) drawLabel(canvas);
    }

    private void updateLabel() {
        if (!(getAdapter() instanceof LabelProvider)) return;
        View row = findChildViewUnder(getWidth() / 2f, thumb.centerY());
        int position = row == null ? NO_POSITION : getChildAdapterPosition(row);
        if (position == NO_POSITION) return;
        String text = ((LabelProvider) getAdapter()).getFastScrollLabel(getContext(), position);
        // Rows without a label (section headers) keep the previous one instead of flickering.
        if (text != null && !text.isEmpty()) label = text;
    }

    private void drawLabel(Canvas canvas) {
        float height = dp(44);
        float width = Math.max(height, labelPaint.measureText(label) + dp(32));
        float right = thumb.left - dp(16);
        float top = Math.max(dp(4),
                Math.min(getHeight() - dp(4) - height, thumb.centerY() - height / 2f));
        bubble.set(right - width, top, right, top + height);
        canvas.drawRoundRect(bubble, height / 2f, height / 2f, bubblePaint);
        Paint.FontMetrics metrics = labelPaint.getFontMetrics();
        canvas.drawText(label, bubble.centerX(),
                bubble.centerY() - (metrics.ascent + metrics.descent) / 2f, labelPaint);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                && hitsFastScrollThumb(event.getX(), event.getY())) {
            dragOffset = event.getY() - thumb.top;
            dragFraction = (thumb.top - dp(4)) / travel();
            activePointerId = event.getPointerId(0);
            dragging = true;
            stopScroll();
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            invalidate();
            return true;
        }
        return dragging || super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!dragging) return super.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                dragTo(event);
                break;
            case MotionEvent.ACTION_UP:
                dragTo(event);
                finishDrag();
                performClick();
                break;
            case MotionEvent.ACTION_CANCEL:
                finishDrag();
                break;
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerId(event.getActionIndex()) == activePointerId) {
                    finishDrag();
                }
                break;
        }
        return true;
    }

    private void dragTo(MotionEvent event) {
        int index = event.findPointerIndex(activePointerId);
        if (index < 0 || !updateThumb()) {
            finishDrag();
            return;
        }
        dragFraction = Math.max(0f, Math.min(1f,
                (event.getY(index) - dragOffset - dp(4)) / travel()));
        scrollToFraction();
        invalidate();
    }

    private void scrollToFraction() {
        LinearLayoutManager manager = (LinearLayoutManager) getLayoutManager();
        int count = getAdapter().getItemCount();
        if (dragFraction == 1f) {
            manager.scrollToPositionWithOffset(count - 1, 0);
            return;
        }
        // Estimate row height so a drag jumps directly instead of laying out every skipped row.
        float rowHeight = (float) computeVerticalScrollRange() / count;
        int offset = Math.round(dragFraction
                * (computeVerticalScrollRange() - computeVerticalScrollExtent()));
        int position = Math.min(count - 1, (int) (offset / rowHeight));
        manager.scrollToPositionWithOffset(position, -Math.round(offset - position * rowHeight));
    }

    private void finishDrag() {
        dragging = false;
        label = null;
        activePointerId = MotionEvent.INVALID_POINTER_ID;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        invalidate();
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onDetachedFromWindow() {
        finishDrag();
        super.onDetachedFromWindow();
    }
}
