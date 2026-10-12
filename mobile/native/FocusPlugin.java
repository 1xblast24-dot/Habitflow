package com.habitflow.app;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
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
    for (int i = 0; i < rows.size() && i < 25; i++) {
      Object[] r = rows.get(i);
      JSObject o = new JSObject();
      o.put("package", (String) r[0]);
      o.put("label", (String) r[1]);
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
    getContext().getSharedPreferences("focus", 0).edit().putStringSet("blocked", set).putLong("endsAt", end).apply();
    Intent i = new Intent(getContext(), FocusService.class);
    if (Build.VERSION.SDK_INT >= 26) getContext().startForegroundService(i); else getContext().startService(i);
    JSObject o = new JSObject();
    o.put("endsAt", end);
    call.resolve(o);
  }

  @PluginMethod
  public void stopFocus(PluginCall call) {
    getContext().getSharedPreferences("focus", 0).edit().putLong("endsAt", 0).apply();
    getContext().stopService(new Intent(getContext(), FocusService.class));
    call.resolve();
  }
}
