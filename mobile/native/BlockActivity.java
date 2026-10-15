package com.habitflow.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class BlockActivity extends Activity {

  private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

  private TextView text(String s, int sp, float alpha, boolean bold) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextColor(Color.WHITE);
    t.setTextSize(sp);
    t.setAlpha(alpha);
    t.setGravity(Gravity.CENTER);
    if (bold) t.setTypeface(null, Typeface.BOLD);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.bottomMargin = dp(10);
    t.setLayoutParams(lp);
    return t;
  }

  private Button button(String s, String bg, String fg) {
    Button b = new Button(this);
    b.setText(s);
    b.setAllCaps(false);
    b.setTextColor(Color.parseColor(fg));
    b.setTextSize(16);
    GradientDrawable d = new GradientDrawable();
    d.setColor(Color.parseColor(bg));
    d.setCornerRadius(dp(14));
    b.setBackground(d);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
    lp.topMargin = dp(14);
    b.setLayoutParams(lp);
    return b;
  }

  @Override
  protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    String pkg = getIntent().getStringExtra("pkg");
    long end = getIntent().getLongExtra("end", 0);
    String label = pkg;
    try {
      PackageManager pm = getPackageManager();
      label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
    } catch (Exception e) { }
    long mins = Math.max(1, (end - System.currentTimeMillis()) / 60000);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setGravity(Gravity.CENTER);
    root.setBackgroundColor(Color.parseColor("#0B0D12"));
    root.setPadding(dp(28), dp(28), dp(28), dp(28));
    boolean limit = getIntent().getBooleanExtra("limit", false);
    root.addView(text(limit ? "Daily limit reached" : "Stay focused", 34, 1f, true));
    root.addView(text(label + " is blocked", 18, 0.85f, false));
    root.addView(text(limit ? "Distracting apps unlock tomorrow" : mins + " min left in your focus session", 15, 0.6f, false));
    Button home = button("Back to home", "#FFFFFF", "#0B0D12");
    home.setOnClickListener(v -> goHome());
    Button stop = button("End focus session", "#1F2430", "#FFFFFF");
    stop.setOnClickListener(v -> {
      getSharedPreferences("focus", 0).edit().putLong("endsAt", 0).putLong("stoppedAt", System.currentTimeMillis()).apply();
      finish();
    });
    root.addView(home);
    boolean strict = getSharedPreferences("focus", 0).getBoolean("strict", false) && end > System.currentTimeMillis();
    if (!limit && !strict) root.addView(stop);
    setContentView(root);
  }

  private void goHome() {
    Intent i = new Intent(Intent.ACTION_MAIN);
    i.addCategory(Intent.CATEGORY_HOME);
    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(i);
    finish();
  }

  @Override
  public void onBackPressed() { goHome(); }
}
