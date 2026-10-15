package com.habitflow.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;

/** Keeps one exact alarm armed for the next scheduled focus session. */
public class Sched {
  static final String ACTION = "com.habitflow.app.SESSION_ALARM";

  static long nextAt(JSONObject s, long now) {
    try {
      JSONArray days = s.getJSONArray("days");
      int h = s.getInt("h"), m = s.getInt("m");
      for (int d = 0; d < 8; d++) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.add(Calendar.DAY_OF_YEAR, d);
        c.set(Calendar.HOUR_OF_DAY, h);
        c.set(Calendar.MINUTE, m);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        int dow = c.get(Calendar.DAY_OF_WEEK) - 1;
        boolean on = false;
        for (int i = 0; i < days.length(); i++) if (days.getInt(i) == dow) on = true;
        if (on && c.getTimeInMillis() > now) return c.getTimeInMillis();
      }
    } catch (Exception e) { }
    return Long.MAX_VALUE;
  }

  static void rearm(Context c) {
    AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    Intent i = new Intent(c, ScheduleReceiver.class);
    i.setAction(ACTION);
    int fl = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    am.cancel(PendingIntent.getBroadcast(c, 4242, i, fl));
    try {
      JSONArray arr = new JSONArray(c.getSharedPreferences("focus", 0).getString("schedules", "[]"));
      long now = System.currentTimeMillis();
      long best = Long.MAX_VALUE;
      String bestId = "";
      for (int k = 0; k < arr.length(); k++) {
        JSONObject s = arr.getJSONObject(k);
        long t = nextAt(s, now);
        if (t < best) { best = t; bestId = s.getString("id"); }
      }
      if (best == Long.MAX_VALUE) return;
      i.putExtra("id", bestId);
      PendingIntent pi = PendingIntent.getBroadcast(c, 4242, i, fl);
      try {
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, best, pi);
        else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, best, pi);
      } catch (SecurityException e) {
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, best, pi);
      }
    } catch (Exception e) { }
  }
}
