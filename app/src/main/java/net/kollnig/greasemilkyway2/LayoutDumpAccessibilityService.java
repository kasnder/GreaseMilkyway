package net.kollnig.greasemilkyway2;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.graphics.Rect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LayoutDumpAccessibilityService extends AccessibilityService {
    private static final String TAG = "LayoutDumpService";
    private final List<View> overlayViews = new ArrayList<>();
    private final Map<String, View> overlayCache = new HashMap<>();
    private WindowManager windowManager;
    private static final boolean ENABLE_LAYOUT_DUMP = true;
    private final List<String> relevantAppIds = List.of("com.google.android.youtube");
    private HandlerThread handlerThread = new HandlerThread("LayoutDumpThread");
    private Handler handler;

    private String dumpNodeInfoToJson(AccessibilityNodeInfo node) {
        if (node == null) {
            return "{}";
        }
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{");
        jsonBuilder.append("\"className\": \"" + node.getClassName() + "\",");
        if (node.getViewIdResourceName() != null) {
            jsonBuilder.append("\"viewIdResourceName\": \"" + node.getViewIdResourceName() + "\",");
        }
        if (node.getText() != null) {
            jsonBuilder.append("\"text\": \"" + node.getText() + "\",");
        }
        if (node.getContentDescription() != null) {
            jsonBuilder.append("\"contentDescription\": \"" + node.getContentDescription() + "\",");
        }
        jsonBuilder.append("\"clickable\": " + node.isClickable() + ",");
        jsonBuilder.append("\"enabled\": " + node.isEnabled() + ",");
        jsonBuilder.append("\"boundsInScreen\": {");
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        jsonBuilder.append("\"left\": " + bounds.left + ",");
        jsonBuilder.append("\"top\": " + bounds.top + ",");
        jsonBuilder.append("\"right\": " + bounds.right + ",");
        jsonBuilder.append("\"bottom\": " + bounds.bottom);
        jsonBuilder.append("},");
        jsonBuilder.append("\"children\": [");
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo childNode = node.getChild(i);
            if (childNode != null) {
                jsonBuilder.append(dumpNodeInfoToJson(childNode));
                if (i < node.getChildCount() - 1) {
                    jsonBuilder.append(",");
                }
                childNode.recycle();
            }
        }
        jsonBuilder.append("]");
        jsonBuilder.append("}");
        return jsonBuilder.toString();
    }

    private final Runnable layoutDumpRunnable = new Runnable() {
        @Override
        public void run() {
            if (ENABLE_LAYOUT_DUMP) {
                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                if (rootNode == null) {
                    Log.e(TAG, "Root node is null, unable to dump layout");
                } else {
                    String jsonLayout = dumpNodeInfoToJson(rootNode);
                    Log.d(TAG, "Layout Dump: " + jsonLayout);
                }
            }
            Log.d(TAG, "Running layout dump...");
            handler.postDelayed(this, 10000); // Run every 10 seconds
        }
    };

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        if (ENABLE_LAYOUT_DUMP) {
            handler.post(layoutDumpRunnable);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        Log.d(TAG, "onAccessibilityEvent triggered: " + event.toString());

        if ((event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED)
                && relevantAppIds.contains(event.getPackageName())) {
            // Remove existing callbacks and add a new one to allow content to settle before drawing
            handler.removeCallbacksAndMessages(null);
            handler.postDelayed(() -> {
                removeAllOverlays();
                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                if (rootNode == null) {
                    Log.e(TAG, "Root node is null, unable to draw overlays");
                } else { //  if (isRelevantElementInView(rootNode))  if (isRelevantElementInView(rootNode))
                    drawTextViewOverlays();
                }
            }, 200); // Adjust this delay as needed
        }
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacks(layoutDumpRunnable);
        if (handlerThread != null) {
            handlerThread.quitSafely();
        }
        // Handle any interruptions if necessary
    }

    private void drawTextViewOverlays() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            Log.e(TAG, "Root node is null, unable to draw text view overlays");
        } else {
            traverseNodeAndDrawOverlays(rootNode);
        }
    }

    private void traverseNodeAndDrawOverlays(AccessibilityNodeInfo node) {
        if (node == null || !node.isVisibleToUser()) {
            return;
        }

        // Check if the current node is the RecyclerView containing "Next Up" videos
        if ("android.support.v7.widget.RecyclerView".contentEquals(node.getClassName())
                && "com.google.android.youtube:id/watch_list".equals(node.getViewIdResourceName())) {

            // Iterate through child ViewGroups and draw overlays, only if they contain sub-items with specific content descriptions
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo childNode = node.getChild(i);
                if (childNode != null && "android.view.ViewGroup".contentEquals(childNode.getClassName())) {
                    if (containsRelevantContentDescription(childNode)) {
                        Rect bounds = new Rect();
                        childNode.getBoundsInScreen(bounds);
                        if (!bounds.isEmpty()) {
                            drawGreyOverlay(bounds);
                        }
                    }
                }
                if (childNode != null) {
                    childNode.recycle(); // Recycle to free resources
                }
            }
        }

        // Recursively visit all child nodes
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo childNode = node.getChild(i);
            if (childNode != null) {
                traverseNodeAndDrawOverlays(childNode);
                childNode.recycle(); // Recycle to free resources
            }
        }
    }

    private boolean containsRelevantContentDescription(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }

        // Check if the current node contains a content description "Shorts" or "Go to channel"
        if (node.getContentDescription() != null && ("Shorts".contentEquals(node.getContentDescription()) || "Go to channel".contentEquals(node.getContentDescription()))) {
            return true;
        }

        // Recursively check all child nodes
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo childNode = node.getChild(i);
            if (childNode != null) {
                boolean result = containsRelevantContentDescription(childNode);
                childNode.recycle(); // Recycle to free resources
                if (result) {
                    return true;
                }
            }
        }
        return false;
    }

    private void drawGreyOverlay(Rect bounds) {
        String key = bounds.toShortString();
        if (overlayCache.containsKey(key)) {
            return; // Overlay already exists, skip creating a new one
        }

        // Create a simple view to act as an overlay with a black background
        View overlayView = new View(this);
        overlayView.setBackgroundColor(Color.BLACK); // Black color with full opacity

        // Set layout parameters for the overlay
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                bounds.width(),
                bounds.height(),
                bounds.left,
                bounds.top,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;

        // Add the overlay to the window manager on the UI thread
        handler.post(() -> {
            windowManager.addView(overlayView, params);
            overlayViews.add(overlayView);
            overlayCache.put(key, overlayView); // Add to cache
        });
    }

    private void removeAllOverlays() {
        // Remove all overlays from the window manager
        for (View overlayView : overlayViews) {
            handler.post(() -> windowManager.removeView(overlayView));
        }
        overlayViews.clear();
        overlayCache.clear();
    }

    private boolean isRelevantElementInView(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) {
            return false;
        }

        // Check if the current node matches specific criteria (e.g., player control button)
        if ("android.widget.ImageView".contentEquals(rootNode.getClassName())
                && "com.google.android.youtube:id/player_control_play_pause_replay_button".equals(rootNode.getViewIdResourceName())) {
            return true;
        }

        // Recursively check all child nodes
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            AccessibilityNodeInfo childNode = rootNode.getChild(i);
            if (childNode != null) {
                boolean result = isRelevantElementInView(childNode);
                childNode.recycle(); // Recycle to free resources
                if (result) {
                    return true;
                }
            }
        }
        return false;
    }
}