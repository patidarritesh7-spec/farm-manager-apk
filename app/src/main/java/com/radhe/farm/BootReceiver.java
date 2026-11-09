package com.radhe.farm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Phone restart ke baad reminders dobara lagata hai. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent i) {
        ReminderReceiver.scheduleAll(ctx);
    }
}
