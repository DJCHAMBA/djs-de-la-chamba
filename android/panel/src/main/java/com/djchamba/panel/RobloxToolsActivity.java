package com.djchamba.panel;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.view.Gravity;

public class RobloxToolsActivity extends Activity {
    private static final int PICK_FILE = 771;
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private String section = "editor";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(9,8,23));
        getWindow().setNavigationBarColor(Color.rgb(9,8,23));
        section = getIntent().getStringExtra("section");
        if (section == null) section = "editor";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(9,8,23));
        TextView back = new TextView(this);
        back.setText("←  VOLVER A PANEL LA CHAMBA");
        back.setTextColor(Color.WHITE);
        back.setTextSize(14);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setPadding(22, 16, 16, 16);
        back.setBackgroundColor(Color.rgb(30,20,52));
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(54)));

        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                String target = section.equals("upload") ? "tabUpload"
                    : section.equals("permissions") ? "openPermissions" : "tabEditor";
                view.evaluateJavascript("(function(){var e=document.getElementById('" + target + "');if(e)e.click();})()", null);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                try { startActivityForResult(Intent.createChooser(pick, "Selecciona un archivo"), PICK_FILE); }
                catch (Exception e) { fileCallback = null; callback.onReceiveValue(null); return false; }
                return true;
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        web.loadUrl("https://djchamba.github.io/djs-de-la-chamba/");
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE && fileCallback != null) {
            Uri[] results = resultCode == RESULT_OK && data != null && data.getData() != null
                ? new Uri[]{data.getData()} : null;
            fileCallback.onReceiveValue(results);
            fileCallback = null;
        }
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        if (web != null) { web.stopLoading(); web.destroy(); web = null; }
        super.onDestroy();
    }
}