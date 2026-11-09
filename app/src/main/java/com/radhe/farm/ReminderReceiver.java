package com.radhe.farm;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** Alarm aane par notification dikhata hai. scheduleAll() saare reminders dobara lagata hai. */
public class ReminderReceiver extends BroadcastReceiver {
    static final String CH = "farm_reminders";

    @Override
    public void onReceive(Context ctx, Intent i) {
        String title = i.getStringExtra("title");
        String body = i.getStringExtra("body");
        int nid = i.getIntExtra("nid", 1);
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH, "Reminders", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, nid, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, CH) : new Notification.Builder(ctx);
        b.setSmallIcon(android.R.drawable.ic_popup_reminder)
         .setContentTitle(title == null ? "Kisan Bahi-Book" : title)
         .setContentText(body == null ? "" : body)
         .setContentIntent(pi)
         .setAutoCancel(true);
        try { nm.notify(nid, b.build()); } catch (Exception e) { /* permission nahi mili */ }
    }

    static PendingIntent pending(Context ctx, int code, String title, String body) {
        Intent it = new Intent(ctx, ReminderReceiver.class);
        it.setAction("com.radhe.farm.REMINDER." + code);
        it.putExtra("title", title);
        it.putExtra("body", body);
        it.putExtra("nid", code);
        return PendingIntent.getBroadcast(ctx, code, it, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void scheduleAll(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            SharedPreferences sp = ctx.getSharedPreferences("rem", Context.MODE_PRIVATE);
            // purane alarm cancel
            Set<String> old = sp.getStringSet("codes", new HashSet<String>());
            for (String c : old) {
                try { am.cancel(pending(ctx, Integer.parseInt(c), "", "")); } catch (Exception e) { /* skip */ }
            }
            Set<String> codes = new HashSet<String>();
            JSONArray arr = new JSONArray(sp.getString("list", "[]"));
            long now = System.currentTimeMillis();
            for (int k = 0; k < arr.length(); k++) {
                JSONObject o = arr.getJSONObject(k);
                long at = o.getLong("at");
                if (at <= now) continue;
                int code = (o.getString("id").hashCode() & 0x7fffffff) % 1000000 + 10;
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, code, o.optString("title"), o.optString("body")));
                codes.add(String.valueOf(code));
            }
            sp.edit().putStringSet("codes", codes).apply();
        } catch (Exception e) { /* ignore */ }
    }
}
