package com.kyunghoon.eversolohistory;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        boolean enabled = context.getSharedPreferences(HistoryService.PREF, Context.MODE_PRIVATE)
                .getBoolean(HistoryService.KEY_ENABLED, true);
        if (!enabled) return;
        Intent s = new Intent(context, HistoryService.class);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(s);
        else context.startService(s);
    }
}
