package com.habitflow.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.util.HashSet;
import java.util.Set;

public class FocusService extends Service {
  private final Handler h = new Handler(Looper.getMainLooper());
  private Set<String> blocked = new HashSet<>();
  private String cur = "";
  private long lastBlock = 0;
  private final Runnable tick = new Runnable() {
    @Override
    public void run() {
      check();
      h.postDelayed(this, 700);
    }
  };

  @Override
  public IBinder onBind(Intent i) { return null; }

  @Override
  public int onStartCommand(Intent i, int flags, int startId) {
    SharedPreferences p = getSharedPreferences("focus", 0);
    blocked = new HashSet<>(p.getStringSet("blocked", new HashSet<String>()));
    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    Notification.Builder b;
    if (Build.VERSION.SDK_INT >= 26) {
      nm.createNotificationChannel(new NotificationChannel("focus", "Focus mode", NotificationManager.IMPORTANCE_LOW));
      b = new Notification.Builder(this, "focus");
    } else {
      b = new Notification.Builder(this);
    }
    b.setContentTitle("Focus mode is on").setContentText("Distracting apps are blocked").setSmallIcon(getApplicationInfo().icon).setOngoing(true);
    if (Build.VERSION.SDK_INT >= 34) {
      startForeground(77, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
    } else {
      startForeground(77, b.build());
    }
    h.removeCallbacks(tick);
    h.post(tick);
    return START_STICKY;
  }

  private void check() {
    long now = System.currentTimeMillis();
    long end = getSharedPreferences("focus", 0).getLong("endsAt", 0);
    if (now >= end) { stopSelf(); return; }
    UsageStatsManager m = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
    UsageEvents ev = m.queryEvents(now - 5000, now);
    UsageEvents.Event e = new UsageEvents.Event();
    while (ev != null && ev.hasNextEvent()) {
      ev.getNextEvent(e);
      if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) cur = e.getPackageName();
    }
    if (blocked.contains(cur) && now - lastBlock > 1500) {
      lastBlock = now;
      Intent b = new Intent(this, BlockActivity.class);
      b.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
      b.putExtra("pkg", cur);
      b.putExtra("end", end);
      startActivity(b);
    }
  }

  @Override
  public void onDestroy() {
    h.removeCallbacks(tick);
    super.onDestroy();
  }
}
