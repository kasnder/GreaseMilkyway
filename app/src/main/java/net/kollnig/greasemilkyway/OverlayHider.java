// OverlayHider.java
package net.kollnig.greasemilkyway;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

public class OverlayHider {

    private static View overlayView;

    public static void show(Context context) {
        if (overlayView != null) return; // Already showing

        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        overlayView = new FrameLayout(context);
        overlayView.setBackgroundColor(0x00000000); // Transparent

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // use TYPE_PHONE for pre-Oreo
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP;
        wm.addView(overlayView, params);

        // Auto remove after 1000ms
        new Handler().postDelayed(() -> {
            try {
                wm.removeView(overlayView);
                overlayView = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 1000);
    }
}
