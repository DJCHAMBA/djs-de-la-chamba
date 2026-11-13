package com.djchamba.panel;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;
import android.view.Gravity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Base64;

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
        web.addJavascriptInterface(new AndroidDownloads(), "DJSAndroid");
        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                android.app.DownloadManager.Request request =
                    new android.app.DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                request.setTitle(android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType));
                request.setDescription("Descargando archivo de La Chamba");
                request.setNotificationVisibility(
                    android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType));
                android.app.DownloadManager manager =
                    (android.app.DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                manager.enqueue(request);
                toast("Descarga enviada a Descargas.");
            } catch (Exception e) {
                toast("No se pudo iniciar la descarga: " + e.getMessage());
            }
        });
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

    private class AndroidDownloads {
        @JavascriptInterface
        public void saveBase64(String base64, String filename, String mimeType) {
            runOnUiThread(() -> {
                try {
                    String safeName = filename == null ? "audio.mp3" :
                        filename.replaceAll("[\\\\/:*?\\"<>|]", "_").trim();
                    if (safeName.isEmpty()) safeName = "archivo";
                    String type = mimeType == null || mimeType.isEmpty()
                        ? "application/octet-stream" : mimeType;
                    byte[] bytes = Base64.getDecoder().decode(base64);
                    if (bytes.length == 0) throw new IllegalArgumentException("El archivo está vacío.");

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
                        values.put(MediaStore.Downloads.MIME_TYPE, type);
                        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                        values.put(MediaStore.Downloads.IS_PENDING, 1);
                        Uri uri = getContentResolver().insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                        if (uri == null) throw new IllegalStateException("Android no creó el archivo.");
                        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                            if (out == null) throw new IllegalStateException("No se pudo abrir el archivo.");
                            out.write(bytes);
                            out.flush();
                        } catch (Exception error) {
                            getContentResolver().delete(uri, null, null);
                            throw error;
                        }
                        ContentValues ready = new ContentValues();
                        ready.put(MediaStore.Downloads.IS_PENDING, 0);
                        getContentResolver().update(uri, ready, null, null);
                    } else {
                        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("No se pudo crear Descargas.");
                        File target = new File(dir, safeName);
                        try (FileOutputStream out = new FileOutputStream(target)) {
                            out.write(bytes);
                            out.flush();
                        }
                    }
                    toast("Guardado en Descargas: " + safeName);
                } catch (Exception error) {
                    toast("Error al guardar: " + error.getMessage());
                }
            });
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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
