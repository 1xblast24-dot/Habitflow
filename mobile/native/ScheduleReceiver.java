package com.habitflow.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

public class ScheduleReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context c, Intent i) {
    String id = i.getStringExtra("id");
    SharedPreferences p = c.getSharedPreferences("focus", 0);
    try {
      JSONArray arr = new JSONArray(p.getString("schedules", "[]"));
      for (int k = 0; k < arr.length(); k++) {
        JSONObject s = arr.getJSONObject(k);
        if (s.getString("id").equals(id)) { begin(c, p, s); break; }
      }
    } catch (Exception e) { }
    Sched.rearm(c);
  }

  private void say(Context c, String title, String text) {
    NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    Notification.Builder b;
    if (Build.VERSION.SDK_INT >= 26) {
      nm.createNotificationChannel(new NotificationChannel("focus_alerts", "Screen-time alerts", NotificationManager.IMPORTANCE_HIGH));
      b = new Notification.Builder(c, "focus_alerts");
    } else {
      b = new Notification.Builder(c);
    }
    nm.notify(79, b.setContentTitle(title).setContentText(text).setSmallIcon(c.getApplicationInfo().icon).setAutoCancel(true).build());
  }

  private void begin(Context c, SharedPreferences p, JSONObject s) throws Exception {
    long now = System.currentTimeMillis();
    if (p.getLong("endsAt", 0) > now) return;
    String name = s.optString("name", "Focus");
    JSONArray ap = s.getJSONArray("apps");
    Set<String> set = new HashSet<>();
    for (int j = 0; j < ap.length(); j++) set.add(ap.getString(j));
    if (!Settings.canDrawOverlays(c) || !Guard.usageOk(c) || set.isEmpty()) {
      say(c, "Scheduled session skipped", name + " could not start. Check the Focus permissions and the apps to block.");
      return;
    }
    int min = s.getInt("min");
    p.edit().putStringSet("blocked", set).putLong("endsAt", now + min * 60000L).putBoolean("strict", s.optBoolean("strict", false))
      .putLong("startedAt", now).putInt("sessMin", min).putString("sessName", name).putLong("stoppedAt", 0).apply();
    Intent svc = new Intent(c, FocusService.class);
    if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(svc); else c.startService(svc);
    say(c, "Focus session started", name + " \u00b7 " + min + " min");
  }
}
