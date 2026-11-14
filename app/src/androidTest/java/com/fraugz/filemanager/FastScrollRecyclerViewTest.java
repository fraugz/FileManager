package com.fraugz.filemanager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.FutureTask;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class FastScrollRecyclerViewTest {

    @Test
    public void thumbIsSlimButKeepsLargeTouchTarget() throws Exception {
        onMainThread(() -> {
            Fixture fixture = new Fixture(200);
            float right = fixture.list.getWidth();
            assertTrue(fixture.list.hitsFastScrollThumb(right - fixture.dp(47), fixture.dp(5)));
            assertTrue(fixture.list.hitsFastScrollThumb(right - fixture.dp(8), fixture.dp(51)));
            assertFalse(fixture.list.hitsFastScrollThumb(right - fixture.dp(49), fixture.dp(32)));
            assertFalse(fixture.list.hitsFastScrollThumb(right - fixture.dp(8), fixture.dp(53)));

            assertTrue(fixture.alphaAt(right - fixture.dp(7), fixture.dp(20)) > 0);
            assertEquals(0, fixture.alphaAt(right - fixture.dp(16), fixture.dp(20)));
        });
    }

    @Test
    public void draggingShowsLabelOfRowUnderThumb() throws Exception {
        onMainThread(() -> {
            Fixture fixture = new Fixture(200);
            float bubbleX = fixture.list.getWidth() - fixture.dp(50);
            float bottomY = fixture.list.getHeight() - fixture.dp(28);
            assertEquals(0, fixture.alphaAt(bubbleX, fixture.dp(28)));

            fixture.touch(MotionEvent.ACTION_DOWN, fixture.dp(32));
            assertTrue(fixture.alphaAt(bubbleX, fixture.dp(28)) > 0);
            assertEquals(0, fixture.rows.lastLabelled);

            fixture.touch(MotionEvent.ACTION_MOVE, fixture.list.getHeight() + fixture.dp(100));
            assertTrue(fixture.alphaAt(bubbleX, bottomY) > 0);
            assertEquals(199, fixture.rows.lastLabelled);

            fixture.touch(MotionEvent.ACTION_UP, fixture.list.getHeight() + fixture.dp(100));
            assertEquals(0, fixture.alphaAt(bubbleX, bottomY));
        });
    }

    @Test
    public void fileLabelFollowsTheSortOrder() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File file = new File(context.getCacheDir(), "epsilon.txt");
        assertTrue(file.createNewFile() || file.exists());
        try {
            FileAdapter adapter = new FileAdapter(
                    Collections.singletonList(new FileItem(file)), null);
            assertEquals("E", adapter.getFastScrollLabel(context, 0));

            adapter.setFastScrollLabelMode(FileAdapter.LABEL_DATE);
            assertTrue(file.setLastModified(System.currentTimeMillis()));
            assertEquals(context.getString(R.string.today), adapter.getFastScrollLabel(context, 0));

            long older = 1_600_000_000_000L;
            assertTrue(file.setLastModified(older));
            assertEquals(DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(older)),
                    adapter.getFastScrollLabel(context, 0));
        } finally {
            file.delete();
        }
    }

    @Test
    public void nameLabelsAreRecomputedWhenTheListChanges() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        List<FileItem> items = new ArrayList<>();
        for (int d = 1; d <= 20; d++) {
            items.add(new FileItem(new File(String.format(Locale.US, "/x/IMG_202110%02d_1.jpg", d))));
        }
        for (int d = 1; d <= 20; d++) {
            items.add(new FileItem(new File(String.format(Locale.US, "/x/IMG_202111%02d_1.jpg", d))));
        }
        FileAdapter adapter = new FileAdapter(items, null);
        assertEquals(FastScrollRecyclerView.dateLabel(context,
                        new GregorianCalendar(2021, 9, 1).getTimeInMillis()),
                adapter.getFastScrollLabel(context, 0));

        // Same number of rows, different names: only the change notification can tell.
        for (int i = 0; i < items.size(); i++) {
            items.set(i, new FileItem(new File(String.format(Locale.US, "/x/a%02d.txt", i))));
        }
        adapter.notifyDataSetChanged();
        assertEquals("A0", adapter.getFastScrollLabel(context, 0));
    }

    @Test
    public void draggingReachesBothEndsWithoutPullToRefresh() throws Exception {
        onMainThread(() -> {
            Fixture fixture = new Fixture(200);
            fixture.touch(MotionEvent.ACTION_DOWN, fixture.dp(32));
            fixture.touch(MotionEvent.ACTION_MOVE, fixture.list.getHeight() / 2f);
            fixture.touch(MotionEvent.ACTION_UP, fixture.list.getHeight() + fixture.dp(100));
            assertFalse(fixture.list.canScrollVertically(1));
            assertTrue(fixture.manager.findLastVisibleItemPosition() == 199);

            fixture.touch(MotionEvent.ACTION_DOWN, fixture.list.getHeight() - fixture.dp(32));
            fixture.touch(MotionEvent.ACTION_MOVE, fixture.list.getHeight() / 2f);
            fixture.touch(MotionEvent.ACTION_UP, -fixture.dp(100));
            assertEquals(0, fixture.manager.findFirstVisibleItemPosition());
            assertFalse(fixture.list.canScrollVertically(-1));
            assertFalse(fixture.root.isRefreshing());
        });
    }

    @Test
    public void cancellationAndShortListsDoNotCaptureOrdinaryTouches() throws Exception {
        onMainThread(() -> {
            Fixture fixture = new Fixture(200);
            fixture.touch(MotionEvent.ACTION_DOWN, fixture.dp(32));
            fixture.touch(MotionEvent.ACTION_CANCEL, fixture.dp(32));
            MotionEvent ordinaryDown = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN,
                    fixture.dp(40), fixture.dp(32), 0);
            assertFalse(fixture.list.onInterceptTouchEvent(ordinaryDown));
            ordinaryDown.recycle();

            fixture.list.setAdapter(new Rows(3));
            fixture.layout();
            assertFalse(fixture.list.hitsFastScrollThumb(fixture.list.getWidth() - fixture.dp(8),
                    fixture.dp(32)));
            fixture.list.setAdapter(new Rows(0));
            fixture.layout();
            assertFalse(fixture.list.hitsFastScrollThumb(fixture.list.getWidth() - fixture.dp(8),
                    fixture.dp(32)));
        });
    }

    private static void onMainThread(Runnable test) throws Exception {
        FutureTask<Void> task = new FutureTask<>(test, null);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(task);
        task.get();
    }

    private static class Fixture {
        final SwipeRefreshLayout root;
        final FastScrollRecyclerView list;
        final LinearLayoutManager manager;
        final Rows rows;
        final float density;
        final float scale;
        long downTime;

        Fixture(int count) {
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            density = context.getResources().getDisplayMetrics().density;
            scale = Math.max(1f, ThemeManager.getUiScale(context));
            root = new SwipeRefreshLayout(context);
            list = new FastScrollRecyclerView(context, null);
            manager = new LinearLayoutManager(context);
            rows = new Rows(count);
            list.setLayoutManager(manager);
            list.setAdapter(rows);
            root.addView(list, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            layout();
        }

        float dp(float value) {
            return value * density * scale;
        }

        void layout() {
            int width = Math.round(dp(320));
            int height = Math.round(dp(480));
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, width, height);
        }

        void touch(int action, float y) {
            long now = SystemClock.uptimeMillis();
            if (action == MotionEvent.ACTION_DOWN) downTime = now;
            MotionEvent event = MotionEvent.obtain(downTime, now, action,
                    list.getWidth() - dp(8), y, 0);
            root.dispatchTouchEvent(event);
            event.recycle();
            layout();
        }

        int alphaAt(float x, float y) {
            Bitmap bitmap = Bitmap.createBitmap(list.getWidth(), list.getHeight(),
                    Bitmap.Config.ARGB_8888);
            list.draw(new Canvas(bitmap));
            int alpha = bitmap.getPixel(Math.round(x), Math.round(y)) >>> 24;
            bitmap.recycle();
            return alpha;
        }
    }

    private static class Rows extends RecyclerView.Adapter<RecyclerView.ViewHolder>
            implements FastScrollRecyclerView.LabelProvider {
        private final int count;
        int lastLabelled = -1;

        Rows(int count) {
            this.count = count;
        }

        @Override
        public String getFastScrollLabel(Context context, int position) {
            lastLabelled = position;
            return "L";
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View row = new View(parent.getContext());
            row.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.round(64 * parent.getResources().getDisplayMetrics().density)));
            return new RecyclerView.ViewHolder(row) {};
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {}

        @Override
        public int getItemCount() {
            return count;
        }
    }
}
