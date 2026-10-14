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
  private Set<String> guardSet = new HashSet<>();
  private boolean guardOn = false;
  private long guardDay = 0;
  private long lastGuard = 0;
  private String cur = "";
  private long lastBlock = 0;
  private final Runnable tick = new Runnable() {
    @Override
    public void run() {
      try { check(); } catch (Exception e) { }
      h.postDelayed(this, 700);
    }
  };

  @Override
  public IBinder onBind(Intent i) { return null; }

  private Notification.Builder builder(String channel, String title, String text) {
    Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, channel) : new Notification.Builder(this);
    return b.setContentTitle(title).setContentText(text).setSmallIcon(getApplicationInfo().icon);
  }

  @Override
  public int onStartCommand(Intent i, int flags, int startId) {
    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT >= 26) {
      nm.createNotificationChannel(new NotificationChannel("focus", "Focus mode", NotificationManager.IMPORTANCE_LOW));
      nm.createNotificationChannel(new NotificationChannel("focus_alerts", "Screen-time alerts", NotificationManager.IMPORTANCE_HIGH));
    }
    Notification n = builder("focus", "HabitFlow is protecting your focus", "Focus mode and screen-time limit are active").setOngoing(true).build();
    if (Build.VERSION.SDK_INT >= 34) startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
    else startForeground(77, n);
    h.removeCallbacks(tick);
    h.post(tick);
    return START_STICKY;
  }

  private void refreshGuard(SharedPreferences p) {
    long mid = Guard.midnight();
    if (mid != guardDay) { guardDay = mid; guardOn = false; guardSet = new HashSet<>(); }
    if (guardOn) return;
    long limit = p.getInt("limitMin", 180) * 60000L;
    if (Guard.todayMs(this) < limit) return;
    guardOn = true;
    Set<String> allow = new HashSet<>(p.getStringSet("allow", new HashSet<String>()));
    guardSet = new HashSet<>(p.getStringSet("guardApps", new HashSet<String>()));
    if (guardSet.isEmpty()) {
      for (Object[] r : Guard.candidates(this)) {
        String pk = (String) r[0];
        if (!allow.contains(pk)) guardSet.add(pk);
      }
    }
    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    nm.notify(78, builder("focus_alerts", "Screen-time limit reached", "Distracting apps are blocked until tomorrow.").setAutoCancel(true).build());
  }

  private void check() {
    SharedPreferences p = getSharedPreferences("focus", 0);
    long now = System.currentTimeMillis();
    long end = p.getLong("endsAt", 0);
    boolean session = now < end;
    boolean guard = p.getBoolean("guard", false);
    if (!session && !guard) { stopSelf(); return; }
    if (guard && now - lastGuard > 15000) { lastGuard = now; refreshGuard(p); }
    Set<String> active = new HashSet<>();
    if (session) active.addAll(p.getStringSet("blocked", new HashSet<String>()));
    if (guard && guardOn) active.addAll(guardSet);
    UsageStatsManager m = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
    UsageEvents ev = m.queryEvents(now - 5000, now);
    UsageEvents.Event e = new UsageEvents.Event();
    while (ev != null && ev.hasNextEvent()) {
      ev.getNextEvent(e);
      if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) cur = e.getPackageName();
    }
    if (active.contains(cur) && now - lastBlock > 1500) {
      lastBlock = now;
      Intent b = new Intent(this, BlockActivity.class);
      b.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
      b.putExtra("pkg", cur);
      b.putExtra("end", end);
      b.putExtra("limit", !session);
      startActivity(b);
    }
  }

  @Override
  public void onDestroy() {
    h.removeCallbacks(tick);
    super.onDestroy();
  }
}
