package com.radhe.farm;

import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_REQ = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                filePathCallback = cb;
                try {
                    Intent intent = params.createIntent();
                    startActivityForResult(intent, FILE_REQ);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_REQ) {
            if (filePathCallback == null) return;
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    // ----- live data store in a VISIBLE phone folder: Documents/KisanFarm/farmdata.json -----
    private static final String DATA_DIR = Environment.DIRECTORY_DOCUMENTS + "/KisanFarm";
    private static final String DATA_FILE = "farmdata.json";

    private Uri findDataUri() {
        Uri coll = MediaStore.Files.getContentUri("external");
        String sel = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?";
        String[] args = new String[]{ DATA_FILE, DATA_DIR + "/" };
        Cursor c = null;
        try {
            c = getContentResolver().query(coll, new String[]{ MediaStore.MediaColumns._ID }, sel, args, null);
            if (c != null && c.moveToFirst()) {
                long id = c.getLong(0);
                return ContentUris.withAppendedId(coll, id);
            }
        } catch (Exception e) {
            // ignore
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    public class Bridge {
        @JavascriptInterface
        public String loadData() {
            try {
                Uri uri = findDataUri();
                if (uri == null) return "";
                InputStream is = getContentResolver().openInputStream(uri);
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
                is.close();
                return new String(bo.toByteArray(), "UTF-8");
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void saveData(final String content) {
            try {
                Uri uri = findDataUri();
                if (uri == null) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.MediaColumns.DISPLAY_NAME, DATA_FILE);
                    cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                    cv.put(MediaStore.MediaColumns.RELATIVE_PATH, DATA_DIR);
                    uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), cv);
                }
                if (uri != null) {
                    OutputStream os = getContentResolver().openOutputStream(uri, "rwt");
                    os.write(content.getBytes("UTF-8"));
                    os.flush();
                    os.close();
                }
            } catch (Exception e) {
                // ignore
            }
        }

        @JavascriptInterface
        public void saveBackup(final String name, final String content) {
            String msg;
            try {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri != null) {
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    os.write(content.getBytes("UTF-8"));
                    os.flush();
                    os.close();
                    msg = "Backup saved to Downloads: " + name;
                } else {
                    msg = "Backup failed";
                }
            } catch (Exception e) {
                msg = "Backup error: " + e.getMessage();
            }
            final String fmsg = msg;
            runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(MainActivity.this, fmsg, Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}
