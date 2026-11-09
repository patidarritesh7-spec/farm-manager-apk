package com.radhe.farm;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_REQ = 1001;
    private static final int PERM_REQ = 1002;
    private Uri camUri = null;

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
                if (filePathCallback != null) { filePathCallback.onReceiveValue(null); }
                filePathCallback = cb;
                try {
                    camUri = null;
                    if (params.isCaptureEnabled() && Build.VERSION.SDK_INT >= 29) {
                        // seedha camera: photo Pictures/KisanFarm me save hoti hai
                        try {
                            ContentValues cv = new ContentValues();
                            cv.put(MediaStore.Images.Media.DISPLAY_NAME, "kisan_" + System.currentTimeMillis() + ".jpg");
                            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                            cv.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/KisanFarm");
                            camUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                        } catch (Exception ex) { camUri = null; }
                    }
                    if (camUri != null) {
                        Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        cam.putExtra(MediaStore.EXTRA_OUTPUT, camUri);
                        cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivityForResult(cam, FILE_REQ);
                    } else {
                        Intent intent = params.createIntent();
                        startActivityForResult(intent, FILE_REQ);
                    }
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        // Notification permission (Android 13+) - reminders ke liye
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{ Manifest.permission.POST_NOTIFICATIONS }, PERM_REQ);
        }

        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_REQ) {
            if (filePathCallback == null) return;
            Uri[] results;
            if (camUri != null) {
                if (resultCode == Activity.RESULT_OK) { results = new Uri[]{ camUri }; }
                else { results = null; try { getContentResolver().delete(camUri, null, null); } catch (Exception ex) {} }
                camUri = null;
            } else {
                results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            }
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

    // ===== data: Documents/KisanFarm/farmdata.json (visible)  +  private copy in app files =====
    private static final String DATA_DIR = Environment.DIRECTORY_DOCUMENTS + "/KisanFarm";
    private static final String DATA_FILE = "farmdata.json";
    private static final String PDF_DIR = Environment.DIRECTORY_DOWNLOADS + "/KisanFarm_Reports";

    private Uri findUri(Uri coll, String name, String relDir) {
        String sel = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?";
        String[] args = new String[]{ name, relDir + "/" };
        Cursor c = null;
        try {
            c = getContentResolver().query(coll, new String[]{ MediaStore.MediaColumns._ID }, sel, args, null);
            if (c != null && c.moveToFirst()) {
                return ContentUris.withAppendedId(coll, c.getLong(0));
            }
        } catch (Exception e) {
            // ignore
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    /** Naam se file dhundo, na mile to nayi banao; content overwrite karo. */
    private Uri writeMedia(Uri coll, String name, String mime, String relDir, byte[] bytes) throws Exception {
        Uri uri = findUri(coll, name, relDir);
        if (uri == null) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, relDir);
            uri = getContentResolver().insert(coll, cv);
        }
        if (uri == null) throw new Exception("insert failed");
        OutputStream os = getContentResolver().openOutputStream(uri, "rwt");
        os.write(bytes);
        os.flush();
        os.close();
        return uri;
    }

    private static byte[] readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        is.close();
        return bo.toByteArray();
    }

    private File privFile() { return new File(getFilesDir(), DATA_FILE); }

    private void toastUi(final String m) {
        runOnUiThread(new Runnable() { public void run() { Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show(); } });
    }

    public class Bridge {
        @JavascriptInterface
        public String getDataPath() {
            return DATA_DIR + "/" + DATA_FILE + "  (+ app private copy)";
        }

        @JavascriptInterface
        public String loadData() {
            try {
                Uri uri = findUri(MediaStore.Files.getContentUri("external"), DATA_FILE, DATA_DIR);
                if (uri != null) {
                    String s = new String(readAll(getContentResolver().openInputStream(uri)), "UTF-8");
                    if (s.length() > 2) return s;
                }
            } catch (Exception e) { /* fall through */ }
            try {
                File pf = privFile();
                if (pf.exists()) return new String(readAll(new FileInputStream(pf)), "UTF-8");
            } catch (Exception e) { /* ignore */ }
            return "";
        }

        @JavascriptInterface
        public void saveData(final String content) {
            byte[] b;
            try { b = content.getBytes("UTF-8"); } catch (Exception e) { return; }
            try { // private copy: pehle temp me likho phir rename (adhoora file na bane)
                File tmp = new File(getFilesDir(), DATA_FILE + ".tmp");
                FileOutputStream fo = new FileOutputStream(tmp);
                fo.write(b); fo.flush(); fo.close();
                File pf = privFile();
                if (!tmp.renameTo(pf)) { pf.delete(); tmp.renameTo(pf); }
            } catch (Exception e) { /* ignore */ }
            try {
                writeMedia(MediaStore.Files.getContentUri("external"), DATA_FILE, "application/json", DATA_DIR, b);
            } catch (Exception e) { /* ignore */ }
        }

        private boolean doBackup(String name, String content) {
            try {
                writeMedia(MediaStore.Downloads.EXTERNAL_CONTENT_URI, name, "application/json",
                        Environment.DIRECTORY_DOWNLOADS, content.getBytes("UTF-8"));
                return true;
            } catch (Exception e) { return false; }
        }

        @JavascriptInterface
        public void saveBackup(final String name, final String content) {
            toastUi(doBackup(name, content) ? "Backup saved: Downloads/" + name : "Backup failed");
        }

        @JavascriptInterface
        public void saveBackupSilent(final String name, final String content) {
            doBackup(name, content);
        }

        /** PDF / JSON file save + share. pkg: "com.whatsapp" | "" (chooser) | "save" (sirf save) */
        @JavascriptInterface
        public void shareFile(final String name, final String b64, final String mime, final String pkg) {
            try {
                byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                boolean isPdf = "application/pdf".equals(mime);
                Uri uri = writeMedia(MediaStore.Downloads.EXTERNAL_CONTENT_URI, name, mime,
                        isPdf ? PDF_DIR : Environment.DIRECTORY_DOWNLOADS, bytes);
                if ("save".equals(pkg)) { toastUi("Saved: " + (isPdf ? "Downloads/KisanFarm_Reports/" : "Downloads/") + name); return; }
                final Intent send = new Intent(Intent.ACTION_SEND);
                send.setType(mime);
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.putExtra(Intent.EXTRA_SUBJECT, name);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                final boolean wa = pkg != null && pkg.length() > 0;
                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            if (wa) {
                                Intent w = new Intent(send);
                                w.setPackage(pkg);
                                try { startActivity(w); return; }
                                catch (ActivityNotFoundException e) {
                                    Toast.makeText(MainActivity.this, "WhatsApp not found - choose app", Toast.LENGTH_SHORT).show();
                                }
                            }
                            startActivity(Intent.createChooser(send, name));
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "Share error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
            } catch (Exception e) {
                toastUi("Share error: " + e.getMessage());
            }
        }

        /** JSON: [{id,title,body,at(ms)}] - sab purane alarm hata kar naye lagata hai */
        @JavascriptInterface
        public void syncReminders(final String json) {
            try {
                getSharedPreferences("rem", Context.MODE_PRIVATE).edit().putString("list", json).apply();
                ReminderReceiver.scheduleAll(MainActivity.this);
            } catch (Exception e) { /* ignore */ }
        }
    }
}
