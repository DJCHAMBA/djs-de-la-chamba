package com.djchamba.djsdelachamba;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebStorage;
import android.graphics.Color;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.io.OutputStream;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 4107;
    private static final String HOME_URL =
        "https://djchamba.github.io/djs-de-la-chamba/?source=android-app";

    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> pendingFileChooser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(9, 10, 13));
        getWindow().setNavigationBarColor(Color.rgb(9, 10, 13));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(9, 10, 13));
        root.setFitsSystemWindows(true);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setIndeterminate(false);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(10, 132, 255)));
        progressBar.setBackgroundColor(Color.rgb(25, 29, 38));
        root.addView(progressBar, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(3)
        ));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(9, 10, 13));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setUserAgentString(settings.getUserAgentString() + " DJSDeLaChambaAndroid/1.0");

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        webView.addJavascriptInterface(new AndroidBridge(), "DJSAndroid");
        webView.setWebViewClient(new AppWebViewClient());
        webView.setWebChromeClient(new AppChromeClient());
        webView.setDownloadListener(new AppDownloadListener());

        root.addView(webView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));
        setContentView(root);

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(HOME_URL);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean isAppHost(Uri uri) {
        return uri != null
            && "https".equalsIgnoreCase(uri.getScheme())
            && "djchamba.github.io".equalsIgnoreCase(uri.getHost());
    }

    private void openExternal(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(intent);
        } catch (Exception ignored) {
            Toast.makeText(this, "No se pudo abrir ese enlace.", Toast.LENGTH_SHORT).show();
        }
    }

    private class AppWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (isAppHost(uri) || "about".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }
            openExternal(uri.toString());
            return true;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Uri uri = Uri.parse(url);
            if (isAppHost(uri) || "about".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }
            openExternal(url);
            return true;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request.isForMainFrame()) {
                runOnUiThread(() -> Toast.makeText(
                    MainActivity.this,
                    "No pude conectar. Revisa tu internet y vuelve a cargar.",
                    Toast.LENGTH_LONG
                ).show());
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler,
                                       android.net.http.SslError error) {
            handler.cancel();
        }
    }

    private class AppChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progressBar.setProgress(newProgress);
            progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePathCallback,
                                         FileChooserParams fileChooserParams) {
            if (pendingFileChooser != null) {
                pendingFileChooser.onReceiveValue(null);
            }
            pendingFileChooser = filePathCallback;

            try {
                Intent picker = fileChooserParams.createIntent();
                picker.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(
                    Intent.createChooser(picker, "Selecciona un audio"),
                    FILE_CHOOSER_REQUEST
                );
                return true;
            } catch (Exception error) {
                pendingFileChooser.onReceiveValue(null);
                pendingFileChooser = null;
                Toast.makeText(MainActivity.this, "No pude abrir el selector de archivos.", Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                      android.os.Message resultMsg) {
            WebView popup = new WebView(MainActivity.this);
            popup.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                    if (url != null && !url.equals("about:blank")) {
                        openExternal(url);
                    }
                    view.stopLoading();
                    view.destroy();
                }
            });
            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(popup);
            resultMsg.sendToTarget();
            return true;
        }
    }

    private class AppDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                    String mimetype, long contentLength) {
            openExternal(url);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (pendingFileChooser == null) return;
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            pendingFileChooser.onReceiveValue(results);
            pendingFileChooser = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (pendingFileChooser != null) {
            pendingFileChooser.onReceiveValue(null);
            pendingFileChooser = null;
        }
        if (webView != null) {
            webView.stopLoading();
            webView.removeJavascriptInterface("DJSAndroid");
            webView.destroy();
            webView = null;
        }
        WebStorage.getInstance().flush();
        super.onDestroy();
    }

    private class AndroidBridge {
        @JavascriptInterface
        public void saveBase64(String encodedData, String fileName, String mimeType) {
            if (encodedData == null || encodedData.length() > 240 * 1024 * 1024) {
                showToast("El archivo es demasiado grande para exportarlo desde la app.");
                return;
            }

            new Thread(() -> {
                try {
                    byte[] bytes = Base64.decode(encodedData, Base64.DEFAULT);
                    String safeName = sanitizeFileName(fileName);
                    String safeMime = (mimeType == null || mimeType.trim().isEmpty())
                        ? "application/octet-stream" : mimeType;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
                        values.put(MediaStore.Downloads.MIME_TYPE, safeMime);
                        values.put(
                            MediaStore.Downloads.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS + "/DJS DE LA CHAMBA"
                        );
                        values.put(MediaStore.Downloads.IS_PENDING, 1);

                        Uri uri = getContentResolver().insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                        );
                        if (uri == null) throw new IllegalStateException("No pude crear el archivo.");

                        try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
                            if (stream == null) throw new IllegalStateException("No pude guardar el archivo.");
                            stream.write(bytes);
                            stream.flush();
                        }

                        ContentValues complete = new ContentValues();
                        complete.put(MediaStore.Downloads.IS_PENDING, 0);
                        getContentResolver().update(uri, complete, null, null);
                    } else {
                        File directory = new File(
                            getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                            "DJS DE LA CHAMBA"
                        );
                        if (!directory.exists() && !directory.mkdirs()) {
                            throw new IllegalStateException("No pude crear la carpeta de descargas.");
                        }
                        File target = new File(directory, safeName);
                        try (OutputStream stream = new java.io.FileOutputStream(target)) {
                            stream.write(bytes);
                            stream.flush();
                        }
                    }

                    showToast("Guardado en Descargas / DJS DE LA CHAMBA");
                } catch (Exception error) {
                    showToast("No se pudo guardar el archivo: " + error.getMessage());
                }
            }).start();
        }

        private String sanitizeFileName(String name) {
            String value = (name == null || name.trim().isEmpty()) ? "audio-exportado.mp3" : name.trim();
            value = value.replaceAll("[\\\\/:*?\"<>|]+", "_");
            return value.length() > 120 ? value.substring(value.length() - 120) : value;
        }

        private void showToast(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
        }
    }
}
