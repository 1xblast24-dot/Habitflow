package com.habitflow.app;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Screen-time helpers: today's total and the apps that look distracting. */
public class Guard {

  static long midnight() {
    Calendar c = Calendar.getInstance();
    c.set(Calendar.HOUR_OF_DAY, 0);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    return c.getTimeInMillis();
  }

  static String home(Context c) {
    Intent i = new Intent(Intent.ACTION_MAIN);
    i.addCategory(Intent.CATEGORY_HOME);
    ResolveInfo r = c.getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
    return (r != null && r.activityInfo != null) ? r.activityInfo.packageName : "";
  }

  static long todayMs(Context c) {
    UsageStatsManager m = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
    Map<String, UsageStats> map = m.queryAndAggregateUsageStats(midnight(), System.currentTimeMillis());
    PackageManager pm = c.getPackageManager();
    String home = home(c);
    long total = 0;
    for (Map.Entry<String, UsageStats> e : map.entrySet()) {
      String p = e.getKey();
      if (p.equals(c.getPackageName()) || p.equals(home)) continue;
      if (pm.getLaunchIntentForPackage(p) == null) continue;
      total += e.getValue().getTotalTimeInForeground();
    }
    return total;
  }

  private static boolean distracting(PackageManager pm, String p) {
    if (Build.VERSION.SDK_INT < 26) return false;
    try {
      int cat = pm.getApplicationInfo(p, 0).category;
      return cat == ApplicationInfo.CATEGORY_SOCIAL || cat == ApplicationInfo.CATEGORY_GAME
          || cat == ApplicationInfo.CATEGORY_VIDEO || cat == ApplicationInfo.CATEGORY_NEWS;
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean essential(String p) {
    return p.contains(".dialer") || p.contains("contacts") || p.contains("messaging") || p.contains(".mms")
        || p.contains("clock") || p.contains("calendar") || p.contains("settings");
  }

  /** Social, game, video and news apps you really use (last 7 days). Rows: {package, label, ms}. */
  static List<Object[]> candidates(Context c) {
    UsageStatsManager m = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
    long end = System.currentTimeMillis();
    Map<String, UsageStats> map = m.queryAndAggregateUsageStats(end - 7L * 86400000L, end);
    PackageManager pm = c.getPackageManager();
    String home = home(c);
    List<Object[]> rows = new ArrayList<>();
    for (Map.Entry<String, UsageStats> e : map.entrySet()) {
      String p = e.getKey();
      long t = e.getValue().getTotalTimeInForeground();
      if (t < 300000 || p.equals(c.getPackageName()) || p.equals(home) || essential(p)) continue;
      if (pm.getLaunchIntentForPackage(p) == null || !distracting(pm, p)) continue;
      String label;
      try { label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(p, 0))); } catch (Exception ex) { label = p; }
      rows.add(new Object[] { p, label, Long.valueOf(t) });
    }
    Collections.sort(rows, (a, b) -> Long.compare((Long) b[2], (Long) a[2]));
    return rows;
  }
}
