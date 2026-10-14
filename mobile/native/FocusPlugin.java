package com.habitflow.app;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@CapacitorPlugin(name = "Focus")
public class FocusPlugin extends Plugin {

  private boolean hasUsage() {
    Context c = getContext();
    AppOpsManager a = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
    int m = a.checkOpNoThrow("android:get_usage_stats", Process.myUid(), c.getPackageName());
    return m == AppOpsManager.MODE_ALLOWED;
  }

  private void launch(Intent i, PluginCall call) {
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    getContext().startActivity(i);
    call.resolve();
  }

  @PluginMethod
  public void status(PluginCall call) {
    long end = getContext().getSharedPreferences("focus", 0).getLong("endsAt", 0);
    JSObject o = new JSObject();
    o.put("usage", hasUsage());
    o.put("overlay", Settings.canDrawOverlays(getContext()));
    o.put("active", end > System.currentTimeMillis());
    o.put("endsAt", end);
    call.resolve(o);
  }

  @PluginMethod
  public void openUsageSettings(PluginCall call) {
    launch(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), call);
  }

  @PluginMethod
  public void openOverlaySettings(PluginCall call) {
    launch(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getContext().getPackageName())), call);
  }

  @PluginMethod
  public void listApps(PluginCall call) {
    PackageManager pm = getContext().getPackageManager();
    Intent main = new Intent(Intent.ACTION_MAIN);
    main.addCategory(Intent.CATEGORY_LAUNCHER);
    List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
    List<String[]> rows = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (ResolveInfo r : list) {
      String p = r.activityInfo.packageName;
      if (p.equals(getContext().getPackageName()) || !seen.add(p)) continue;
      rows.add(new String[] { p, String.valueOf(r.loadLabel(pm)) });
    }
    Collections.sort(rows, (a, b) -> a[1].compareToIgnoreCase(b[1]));
    JSArray arr = new JSArray();
    for (String[] s : rows) {
      JSObject o = new JSObject();
      o.put("package", s[0]);
      o.put("label", s[1]);
      o.put("icon", Guard.icon(getContext(), s[0]));
      o.put("cat", Guard.cat(getContext(), s[0]));
      arr.put(o);
    }
    JSObject res = new JSObject();
    res.put("apps", arr);
    call.resolve(res);
  }

  @PluginMethod
  public void usage(PluginCall call) {
    if (!hasUsage()) { call.reject("no_usage_access"); return; }
    int days = call.getInt("days", 1);
    Calendar cal = Calendar.getInstance();
    cal.set(Calendar.HOUR_OF_DAY, 0);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    cal.add(Calendar.DAY_OF_YEAR, -(Math.max(days, 1) - 1));
    long start = cal.getTimeInMillis();
    long end = System.currentTimeMillis();
    UsageStatsManager m = (UsageStatsManager) getContext().getSystemService(Context.USAGE_STATS_SERVICE);
    Map<String, UsageStats> map = m.queryAndAggregateUsageStats(start, end);
    PackageManager pm = getContext().getPackageManager();
    List<Object[]> rows = new ArrayList<>();
    long total = 0;
    for (Map.Entry<String, UsageStats> e : map.entrySet()) {
      long t = e.getValue().getTotalTimeInForeground();
      String p = e.getKey();
      if (t < 60000 || p.equals(getContext().getPackageName())) continue;
      if (pm.getLaunchIntentForPackage(p) == null) continue;
      String label;
      try { label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(p, 0))); } catch (Exception ex) { label = p; }
      rows.add(new Object[] { p, label, Long.valueOf(t) });
      total += t;
    }
    Collections.sort(rows, (a, b) -> Long.compare((Long) b[2], (Long) a[2]));
    JSArray arr = new JSArray();
    int lim = call.getInt("limit", 25);
    for (int i = 0; i < rows.size() && i < lim; i++) {
      Object[] r = rows.get(i);
      JSObject o = new JSObject();
      o.put("package", (String) r[0]);
      o.put("label", (String) r[1]);
      o.put("icon", Guard.icon(getContext(), (String) r[0]));
      o.put("cat", Guard.cat(getContext(), (String) r[0]));
      o.put("ms", ((Long) r[2]).longValue());
      arr.put(o);
    }
    JSObject res = new JSObject();
    res.put("apps", arr);
    res.put("totalMs", total);
    call.resolve(res);
  }

  @PluginMethod
  public void startFocus(PluginCall call) {
    JSArray pk = call.getArray("packages");
    int minutes = call.getInt("minutes", 25);
    if (pk == null || pk.length() == 0) { call.reject("no_apps"); return; }
    if (!Settings.canDrawOverlays(getContext()) || !hasUsage()) { call.reject("permissions"); return; }
    Set<String> set = new HashSet<>();
    try {
      for (int i = 0; i < pk.length(); i++) set.add(pk.getString(i));
    } catch (Exception e) { call.reject("bad_packages"); return; }
    long end = System.currentTimeMillis() + minutes * 60000L;
    getContext().getSharedPreferences("focus", 0).edit().putStringSet("blocked", set).putLong("endsAt", end).putBoolean("strict", Boolean.TRUE.equals(call.getBoolean("strict", false))).apply();
    Intent i = new Intent(getContext(), FocusService.class);
    if (Build.VERSION.SDK_INT >= 26) getContext().startForegroundService(i); else getContext().startService(i);
    JSObject o = new JSObject();
    o.put("endsAt", end);
    call.resolve(o);
  }

  @PluginMethod
  public void stopFocus(PluginCall call) {
    SharedPreferences sp = getContext().getSharedPreferences("focus", 0);
    if (sp.getBoolean("strict", false) && sp.getLong("endsAt", 0) > System.currentTimeMillis()) { call.reject("strict"); return; }
    sp.edit().putLong("endsAt", 0).apply();
    call.resolve();
  }

  @PluginMethod
  public void guardInfo(PluginCall call) {
    if (!hasUsage()) { call.reject("no_usage_access"); return; }
    SharedPreferences p = getContext().getSharedPreferences("focus", 0);
    Set<String> allow = p.getStringSet("allow", new HashSet<String>());
    int limitMin = p.getInt("limitMin", 180);
    long today = Guard.todayMs(getContext());
    JSObject o = new JSObject();
    o.put("enabled", p.getBoolean("guard", false));
    o.put("limitMin", limitMin);
    o.put("todayMs", today);
    o.put("active", p.getBoolean("guard", false) && today >= limitMin * 60000L);
    JSArray apps = new JSArray();
    for (Object[] r : Guard.candidates(getContext())) {
      JSObject a = new JSObject();
      a.put("package", (String) r[0]);
      a.put("label", (String) r[1]);
      a.put("icon", Guard.icon(getContext(), (String) r[0]));
      a.put("ms", ((Long) r[2]).longValue());
      apps.put(a);
    }
    JSArray al = new JSArray();
    for (String s : allow) al.put(s);
    o.put("apps", apps);
    o.put("allow", al);
    call.resolve(o);
  }

  @PluginMethod
  public void setGuard(PluginCall call) {
    boolean en = Boolean.TRUE.equals(call.getBoolean("enabled", false));
    int limitMin = call.getInt("limitMin", 180);
    JSArray al = call.getArray("allow");
    Set<String> allow = new HashSet<>();
    try {
      if (al != null) for (int i = 0; i < al.length(); i++) allow.add(al.getString(i));
    } catch (Exception e) { }
    if (en && (!Settings.canDrawOverlays(getContext()) || !hasUsage())) { call.reject("permissions"); return; }
    JSArray pk = call.getArray("packages");
    Set<String> gp = new HashSet<>();
    try {
      if (pk != null) for (int i = 0; i < pk.length(); i++) gp.add(pk.getString(i));
    } catch (Exception e) { }
    getContext().getSharedPreferences("focus", 0).edit().putBoolean("guard", en).putInt("limitMin", limitMin).putStringSet("allow", allow).putStringSet("guardApps", gp).apply();
    if (en) {
      Intent i = new Intent(getContext(), FocusService.class);
      if (Build.VERSION.SDK_INT >= 26) getContext().startForegroundService(i); else getContext().startService(i);
    }
    call.resolve();
  }

  @PluginMethod
  public void week(PluginCall call) {
    if (!hasUsage()) { call.reject("no_usage_access"); return; }
    long mid = Guard.midnight();
    long now = System.currentTimeMillis();
    JSArray arr = new JSArray();
    for (int i = 6; i >= 0; i--) {
      Calendar c = Calendar.getInstance();
      c.setTimeInMillis(mid);
      c.add(Calendar.DAY_OF_YEAR, -i);
      long s = c.getTimeInMillis();
      c.add(Calendar.DAY_OF_YEAR, 1);
      long e = Math.min(c.getTimeInMillis(), now);
      JSObject o = new JSObject();
      o.put("t", s);
      o.put("ms", e > s ? Guard.rangeMs(getContext(), s, e) : 0L);
      arr.put(o);
    }
    JSObject res = new JSObject();
    res.put("days", arr);
    call.resolve(res);
  }

  @PluginMethod
  public void sleep(PluginCall call) {
    if (!hasUsage()) { call.reject("no_usage_access"); return; }
    int n = Math.min(Math.max(call.getInt("days", 7), 1), 14);
    UsageStatsManager m = (UsageStatsManager) getContext().getSystemService(Context.USAGE_STATS_SERVICE);
    long mid = Guard.midnight();
    long now = System.currentTimeMillis();
    JSArray arr = new JSArray();
    for (int i = n - 1; i >= 0; i--) {
      Calendar c = Calendar.getInstance();
      c.setTimeInMillis(mid);
      c.add(Calendar.DAY_OF_YEAR, -i);
      long day = c.getTimeInMillis();
      long ws = day - 10 * 3600000L;
      long we = Math.min(day + 14 * 3600000L, now);
      JSObject o = new JSObject();
      o.put("t", day);
      if (we - ws < 8 * 3600000L) { o.put("none", true); arr.put(o); continue; }
      List<long[]> use = new ArrayList<>();
      List<Long> shorts = new ArrayList<>();
      UsageEvents ev = m.queryEvents(ws, we);
      UsageEvents.Event e = new UsageEvents.Event();
      long on = 0;
      while (ev != null && ev.hasNextEvent()) {
        ev.getNextEvent(e);
        int ty = e.getEventType();
        if (ty == 15) {
          if (on == 0) on = e.getTimeStamp();
        } else if (ty == 16 && on > 0) {
          long d = e.getTimeStamp() - on;
          if (d >= 180000L) use.add(new long[] { on, e.getTimeStamp() }); else shorts.add(Long.valueOf(on));
          on = 0;
        }
      }
      if (on > 0) use.add(new long[] { on, we });
      long bs = 0, be = 0;
      for (int k = 0; k + 1 < use.size(); k++) {
        long gs = use.get(k)[1], ge = use.get(k + 1)[0];
        if (gs < day - 6 * 3600000L || gs > day + 6 * 3600000L) continue;
        if (ge - gs > be - bs) { bs = gs; be = ge; }
      }
      if (be - bs < 3 * 3600000L) { o.put("none", true); arr.put(o); continue; }
      int wakes = 0;
      for (Long s : shorts) if (s.longValue() > bs && s.longValue() < be) wakes++;
      o.put("bed", bs);
      o.put("wake", be);
      o.put("ms", be - bs);
      o.put("wakes", wakes);
      arr.put(o);
    }
    JSObject res = new JSObject();
    res.put("nights", arr);
    call.resolve(res);
  }
}
