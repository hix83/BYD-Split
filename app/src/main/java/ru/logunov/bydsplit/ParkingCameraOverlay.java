package ru.logunov.bydsplit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

final class ParkingCameraOverlay {
    private static View currentView;
    private static WebView currentWebView;

    private ParkingCameraOverlay() {
    }

    static boolean canShow(Context context) {
        return Settings.canDrawOverlays(context);
    }

    static boolean isShowing() {
        return currentView != null;
    }

    static boolean show(Context context) {
        if (!canShow(context)) {
            return false;
        }
        String url = AppPreferences.getParkingCameraUrl(context);
        String name = AppPreferences.getParkingCameraName(context);
        render(context.getApplicationContext(), name, url);
        return true;
    }

    static void close(Context context) {
        dismiss(context.getApplicationContext());
    }

    private static void render(Context context, String name, String url) {
        dismiss(context);
        WindowManager manager = (WindowManager) context.getSystemService(
                Context.WINDOW_SERVICE);
        int width = Math.round(context.getResources().getDisplayMetrics().widthPixels
                * (2f / 3f) * 1.4f);
        int height = Math.round(context.getResources().getDisplayMetrics().heightPixels
                * (2f / 3f) * 1.4f * 0.9f);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(rounded(context, Color.rgb(22, 32, 50), 18,
                Color.rgb(76, 141, 255)));
        root.setElevation(dp(context, 28));
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        root.setAlpha(0f);
        root.setScaleX(0.96f);
        root.setScaleY(0.96f);
        root.setClickable(true);

        FrameLayout touchLayer = new FrameLayout(context);
        touchLayer.setBackgroundColor(Color.TRANSPARENT);
        touchLayer.setClickable(true);
        touchLayer.setOnClickListener(view -> dismiss(context));

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(context, 8), dp(context, 8), dp(context, 10),
                dp(context, 8));
        TextView refresh = action(context, "Обновить", false);
        refresh.setOnClickListener(view -> currentWebView.reload());
        header.addView(refresh, new LinearLayout.LayoutParams(dp(context, 116),
                dp(context, 40)));
        TextView close = action(context, "Закрыть", true);
        close.setOnClickListener(view -> dismiss(context));
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                dp(context, 104), dp(context, 40));
        closeParams.leftMargin = dp(context, 8);
        header.addView(close, closeParams);
        TextView title = new TextView(context);
        title.setText(name);
        title.setTextColor(Color.rgb(238, 243, 250));
        title.setTextSize(15);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        titleParams.leftMargin = dp(context, 14);
        header.addView(title, titleParams);

        WebView webView = new WebView(context);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }
        webView.loadUrl(normalizeUrl(url));
        currentWebView = webView;

        FrameLayout webFrame = new FrameLayout(context);
        webFrame.setPadding(dp(context, 8), 0, dp(context, 8), dp(context, 8));
        webFrame.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(webFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                width, height, Gravity.CENTER);
        touchLayer.addView(root, cardParams);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;
        params.y = 0;
        manager.addView(touchLayer, params);
        currentView = touchLayer;
        MainActivity.onParkingCameraVisibilityChanged(true);
        root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private static void dismiss(Context context) {
        boolean wasShowing = currentView != null;
        if (currentView != null) {
            try {
                ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
                        .removeView(currentView);
            } catch (RuntimeException ignored) {
                // The system may already have removed the overlay.
            }
        }
        if (currentWebView != null) {
            currentWebView.stopLoading();
            currentWebView.destroy();
        }
        currentView = null;
        currentWebView = null;
        if (wasShowing) {
            MainActivity.onParkingCameraVisibilityChanged(false);
        }
    }

    private static TextView action(Context context, String label, boolean destructive) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(Color.WHITE);
        view.setTextSize(14);
        view.setBackground(rounded(context,
                destructive ? Color.rgb(98, 45, 55) : Color.rgb(34, 49, 73),
                12, Color.TRANSPARENT));
        return view;
    }

    private static String normalizeUrl(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.contains("://") ? trimmed : "https://" + trimmed;
    }

    private static GradientDrawable rounded(Context context, int color,
                                            int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radius));
        if (stroke != Color.TRANSPARENT) {
            drawable.setStroke(dp(context, 1), stroke);
        }
        return drawable;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
