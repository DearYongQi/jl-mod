package ru.playsoftware.j2meloader.util;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * A semi-transparent overlay window that displays runtime log output.
 * Provides a static d() method that writes to both android.util.Log and the on-screen overlay.
 */
public class OverlayLog {
	private static final StringBuilder logBuffer = new StringBuilder();
	private static TextView logTextView;
	private static WindowManager windowManager;
	private static boolean initialized = false;
	private static final Handler mainHandler = new Handler(Looper.getMainLooper());

	private static WindowManager.LayoutParams layoutParams;
	private static float dragStartX, dragStartY;
	private static float initialParamX, initialParamY;

	private OverlayLog() {}

	/**
	 * Initialize and show the log overlay window.
	 * Must be called from an Activity context (e.g. in onCreate).
	 */
	public static void init(Context context) {
		if (initialized) return;

		windowManager = (WindowManager) context.getApplicationContext()
				.getSystemService(Context.WINDOW_SERVICE);

		logTextView = new TextView(context);
		logTextView.setText("J2ME-Loader Overlay Log\n");
		logTextView.setTextSize(10);
		logTextView.setTextColor(0xFFFFFFFF);
		logTextView.setBackgroundColor(0x80000000);
		logTextView.setPadding(12, 8, 12, 8);
		logTextView.setMaxLines(15);
		logTextView.setTextIsSelectable(false);

		int type;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
		} else {
			type = WindowManager.LayoutParams.TYPE_PHONE;
		}

		layoutParams = new WindowManager.LayoutParams(
				WindowManager.LayoutParams.WRAP_CONTENT,
				WindowManager.LayoutParams.WRAP_CONTENT,
				type,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
						| WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
				PixelFormat.TRANSLUCENT
		);
		layoutParams.gravity = Gravity.TOP | Gravity.START;
		layoutParams.x = 0;
		layoutParams.y = 0;

		logTextView.setOnTouchListener(new DragTouchListener());

		windowManager.addView(logTextView, layoutParams);
		initialized = true;
		Log.d("OverlayLog", "Overlay initialized");
	}

	/**
	 * Log a message to both android.util.Log.d() and the on-screen overlay.
	 */
	public static void d(String tag, String msg) {
		Log.d(tag, msg);
		String line = tag + ": " + msg;
		synchronized (logBuffer) {
			logBuffer.append(line).append("\n");
			if (logBuffer.length() > 8000) {
				logBuffer.delete(0, logBuffer.length() - 6000);
			}
		}
		mainHandler.post(() -> {
			if (logTextView != null) {
				logTextView.setText(logBuffer.toString());
			}
		});
	}

	/**
	 * Remove the overlay window from the screen.
	 */
	public static void destroy() {
		if (windowManager != null && logTextView != null) {
			try {
				windowManager.removeView(logTextView);
			} catch (Exception ignored) {
			}
			logTextView = null;
		}
		initialized = false;
	}

	private static class DragTouchListener implements View.OnTouchListener {
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					dragStartX = event.getRawX();
					dragStartY = event.getRawY();
					initialParamX = layoutParams.x;
					initialParamY = layoutParams.y;
					return true;
				case MotionEvent.ACTION_MOVE:
					float dx = event.getRawX() - dragStartX;
					float dy = event.getRawY() - dragStartY;
					layoutParams.x = (int) (initialParamX + dx);
					layoutParams.y = (int) (initialParamY + dy);
					windowManager.updateViewLayout(logTextView, layoutParams);
					return true;
				case MotionEvent.ACTION_UP:
					v.performClick();
					return true;
			}
			return false;
		}
	}
}
