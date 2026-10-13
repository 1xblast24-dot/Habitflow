package com.habitflow.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context c, Intent i) {
    SharedPreferences p = c.getSharedPreferences("focus", 0);
    if (p.getBoolean("guard", false) || p.getLong("endsAt", 0) > System.currentTimeMillis()) {
      Intent s = new Intent(c, FocusService.class);
      if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s); else c.startService(s);
    }
  }
}
