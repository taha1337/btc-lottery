package com.taha1337.btclottery;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(16,20,17), CARD = Color.rgb(25,31,26), LINE = Color.rgb(49,59,49);
    private static final int INK = Color.rgb(240,243,233), MUTED = Color.rgb(160,174,158), ACCENT = Color.rgb(255,183,107);
    private LotteryApp app;
    private LinearLayout shell, page, nav;
    private EditText address, guesses;
    private Button checkButton;
    private LinearLayout feedback;
    private String tab = "Check";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        app = (LotteryApp) getApplication();
        if (state != null) tab = state.getString("tab", "Check");
        shell = column();
        shell.setBackgroundColor(BG);
        if (android.os.Build.VERSION.SDK_INT >= 30) shell.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        if (android.os.Build.VERSION.SDK_INT < 30) {
            shell.setOnApplyWindowInsetsListener((view, insets) -> {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                return insets;
            });
        }
        setContentView(shell);
        showPage();
    }

    @Override protected void onResume() {
        super.onResume();
        app.listener = () -> { if (tab.equals("Check")) renderFeedback(); else showPage(); };
        if (tab.equals("Check")) renderFeedback();
    }

    @Override protected void onPause() {
        saveDraft();
        app.listener = null;
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        saveDraft();
        out.putString("tab", tab);
        super.onSaveInstanceState(out);
    }

    private void saveDraft() {
        if (address != null && guesses != null) app.prefs.edit()
                .putString("address", address.getText().toString()).putString("guesses", guesses.getText().toString()).apply();
    }

    private void showPage() {
        shell.removeAllViews();
        LinearLayout heading = row();
        heading.setPadding(dp(24), dp(14), dp(24), dp(14));
        TextView brand = text("₿  BTC LOTTERY", 15, INK, true);
        brand.setLetterSpacing(.10f);
        heading.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView badge = text("REMOTE CHECKER", 9, ACCENT, true);
        badge.setLetterSpacing(.08f);
        heading.addView(badge);
        shell.addView(heading);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page = column();
        page.setPadding(dp(24), dp(18), dp(24), dp(28));
        scroll.addView(page);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        nav = row();
        nav.setPadding(dp(12), dp(8), dp(12), dp(10));
        for (String name : new String[]{"Check", "History", "About"}) {
            Button button = button(name, name.equals(tab));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(50), 1);
            params.setMargins(dp(4), 0, dp(4), 0);
            nav.addView(button, params);
            button.setOnClickListener(v -> { saveDraft(); tab = name; showPage(); });
        }
        shell.addView(nav);
        if (tab.equals("Check")) checkPage();
        else if (tab.equals("History")) historyPage();
        else aboutPage();
    }

    private void checkPage() {
        label(page, "YOUR NUMBERS. ONE LIVE BLOCK.");
        add(page, text("Make your\nnext guess.", 38, INK, true), 8);
        add(page, text("Enter your numbers. The service checks them against a fresh Bitcoin block.", 15, MUTED, false), 10);
        LinearLayout split = row();
        split.setPadding(dp(18), dp(16), dp(18), dp(16));
        split.setBackground(shape(CARD, 18, LINE));
        LinearLayout user = column();
        user.addView(text("95%", 27, ACCENT, true));
        user.addView(text("Your share", 12, MUTED, false));
        split.addView(user, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout fee = column();
        fee.addView(text("5%", 27, INK, true));
        fee.addView(text("Fixed operator fee", 12, MUTED, false));
        split.addView(fee, new LinearLayout.LayoutParams(0, -2, 1));
        add(page, split, 24);

        label(page, "BITCOIN RECEIVING ADDRESS", 26);
        address = input("Your BTC address", false);
        address.setText(app.prefs.getString("address", ""));
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        address.setSingleLine(true);
        add(page, address, 8);
        add(page, text("This address receives your share if a valid block is accepted.", 12, MUTED, false), 7);

        label(page, "NONCE GUESSES", 22);
        guesses = input("42, 12345, 999999", true);
        guesses.setText(app.prefs.getString("guesses", ""));
        add(page, guesses, 8);
        add(page, text("0–4,294,967,295 · up to 128 guesses\nSeparate numbers with commas, spaces, or new lines.", 12, MUTED, false), 7);
        checkButton = button("Check my guesses  →", true);
        add(page, checkButton, 22, 56);
        checkButton.setOnClickListener(v -> beginCheck());
        add(page, text("Valid results submit automatically. Duplicate guesses are checked once; your numbers are never changed.", 12, MUTED, false), 10);
        feedback = column();
        add(page, feedback, 22);
        renderFeedback();
    }

    private void beginCheck() {
        address.setError(null);
        guesses.setError(null);
        String receivingAddress = address.getText().toString().trim();
        if (!receivingAddress.matches("[a-zA-Z0-9]{14,90}")) {
            address.setError("Enter a Bitcoin receiving address."); address.requestFocus(); return;
        }
        List<Long> values;
        try { values = NonceInput.parse(guesses.getText().toString()); }
        catch (IllegalArgumentException error) { guesses.setError(error.getMessage()); guesses.requestFocus(); return; }
        saveDraft();
        ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(guesses.getWindowToken(), 0);
        app.check(receivingAddress, values);
        renderFeedback();
    }

    private void renderFeedback() {
        if (feedback == null || !tab.equals("Check")) return;
        checkButton.setEnabled(!app.busy);
        checkButton.setAlpha(app.busy ? .6f : 1f);
        checkButton.setText(app.busy ? "Checking your guesses…" : "Check my guesses  →");
        address.setEnabled(!app.busy);
        guesses.setEnabled(!app.busy);
        feedback.removeAllViews();
        if (app.busy) {
            ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setIndeterminate(true);
            progress.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
            feedback.addView(progress);
            add(feedback, text("Waiting for the live service. You can leave this screen; this check will continue while the app is running.", 13, MUTED, false), 10);
        } else if (!app.message.isEmpty()) {
            TextView error = text(app.message, 14, ACCENT, false);
            error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            feedback.addView(error);
        } else if (app.latest != null) {
            showResult(feedback, app.latest);
        }
    }

    private String outcome(JSONObject response) {
        String status = response.optJSONObject("submission").optString("status");
        return switch (status) {
            case "accepted" -> "Block accepted";
            case "stale" -> "Block changed before submission";
            case "rejected" -> "Block was not accepted";
            case "unknown" -> "Submission outcome unknown";
            default -> "No match this time";
        };
    }

    private void showResult(LinearLayout parent, JSONObject result) {
        LinearLayout card = column();
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(shape(CARD, 18, LINE));
        TextView title = text(outcome(result), 21, INK, true);
        title.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        card.addView(title);
        add(card, text("BLOCK " + result.optLong("height") + "  ·  " + result.optInt("checked") + " CHECKED", 11, ACCENT, true), 9);
        int duplicates = result.optInt("duplicates_skipped");
        if (duplicates > 0) add(card, text(duplicates + " duplicate guesses skipped.", 12, MUTED, false), 8);
        String status = result.optJSONObject("submission").optString("status");
        if (status.equals("accepted")) add(card, text("The node accepted the block. Acceptance is not final confirmation; coinbase rewards require 100 blocks of maturity.", 13, MUTED, false), 12);
        else if (status.equals("unknown")) add(card, text("The node did not confirm submission. Check the block hash before trying again.", 13, MUTED, false), 12);
        else if (status.equals("stale")) add(card, text("A newer block arrived. This result was not submitted. Run a new check for a fresh candidate.", 13, MUTED, false), 12);
        else if (status.equals("rejected")) add(card, text("The node rejected the submitted block. No reward is confirmed.", 13, MUTED, false), 12);
        else add(card, text("None of these guesses met the block’s target. There is no guaranteed reward.", 13, MUTED, false), 12);
        String blockHash = result.optJSONObject("submission").optString("hash");
        if (!blockHash.isEmpty()) {
            TextView hash = text(blockHash, 11, INK, false); hash.setTextIsSelectable(true); add(card, hash, 12);
        }
        JSONArray results = result.optJSONArray("results");
        Button details = button("View " + (results == null ? 0 : results.length()) + " hash results", false);
        add(card, details, 14, 48);
        details.setOnClickListener(v -> showHashes(results));
        parent.addView(card);
    }

    private void showHashes(JSONArray results) {
        if (results == null) return;
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < results.length(); i++) {
            JSONObject item = results.optJSONObject(i);
            if (item != null) content.append(item.optLong("nonce")).append(item.optBoolean("solved") ? " · SOLVED\n" : "\n")
                    .append(item.optString("hash")).append("\n\n");
        }
        ScrollView scroll = new ScrollView(this);
        TextView body = text(content.toString().trim(), 12, INK, false);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        body.setPadding(dp(22), dp(12), dp(22), dp(12));
        scroll.addView(body);
        new AlertDialog.Builder(this).setTitle("Hash results").setView(scroll).setPositiveButton("Done", null)
                .setNeutralButton("Copy", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("BTC Lottery results", content));
                }).show();
    }

    private void historyPage() {
        label(page, "ON THIS DEVICE");
        add(page, text("Your checks.", 34, INK, true), 8);
        add(page, text("Your last 20 checks are saved here. Tap a check to see its results.", 15, MUTED, false), 10);
        JSONArray records = app.history();
        if (records.length() == 0) {
            add(page, text("Nothing here yet.", 23, INK, true), 50);
            add(page, text("Your first check starts with a few numbers.", 14, MUTED, false), 10);
        }
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record == null) continue;
            JSONObject response = record.optJSONObject("response");
            LinearLayout card = column();
            card.setPadding(dp(18), dp(16), dp(18), dp(16));
            card.setBackground(shape(CARD, 16, LINE));
            card.addView(text(response == null ? "Check incomplete" : outcome(response), 17, INK, true));
            add(card, text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(record.optLong("time"))), 12, MUTED, false), 7);
            add(card, text(record.optInt("count") + " guesses  ·  " + shortAddress(record.optString("address")), 12, ACCENT, false), 7);
            card.setFocusable(true);
            card.setOnClickListener(v -> {
                if (response == null) new AlertDialog.Builder(this).setTitle("Check incomplete").setMessage(record.optString("error")).setPositiveButton("Done", null).show();
                else {
                    ScrollView scroll = new ScrollView(this);
                    LinearLayout detail = column(); detail.setPadding(dp(16), dp(12), dp(16), dp(12));
                    TextView receiving = text("Receiving address\n" + record.optString("address"), 12, MUTED, false);
                    receiving.setTextIsSelectable(true); detail.addView(receiving);
                    showResult(detail, response); scroll.addView(detail);
                    new AlertDialog.Builder(this).setTitle("Saved check").setView(scroll).setPositiveButton("Done", null).show();
                }
            });
            add(page, card, 16);
        }
    }

    private void aboutPage() {
        label(page, "BTC LOTTERY / 0.1.0");
        add(page, text("Small guesses.\nClear rules.", 34, INK, true), 8);
        section("How it works", "Your receiving address and nonce guesses go to the hosted checking service. It builds a live Bitcoin candidate, checks your exact numbers, and automatically submits a valid result. All hash checking happens on the service.");
        section("Your share", "The candidate pays 95% of its reward to your address and a fixed 5% to the operator. The operator address and fee cannot be changed in the app. A successful check is extremely unlikely; no payment is required to enter guesses.");
        section("Your data", "The app saves your address, drafts, and last 20 checks on this phone. It has no ads or analytics SDKs. Requests send your address and guesses to the Cloudflare service and Bitcoin node; infrastructure providers receive connection information such as your IP address. Accepted blocks and their payout addresses are public on Bitcoin.");
        section("Keep your keys private", "Only enter a receiving address. This app never needs your private key or recovery phrase.");
        Button clear = button("Clear saved address and history", false);
        clear.setEnabled(!app.busy);
        add(page, clear, 24, 54);
        clear.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Clear local data?")
                .setMessage("Remove the saved address, draft guesses, and check history from this phone?")
                .setNegativeButton("Cancel", null).setPositiveButton("Clear", (dialog, which) -> {
                    app.clear(); address = null; guesses = null; showPage();
                    Toast.makeText(this, "Local data cleared", Toast.LENGTH_SHORT).show();
                }).show());
    }

    private void section(String title, String body) {
        add(page, text(title, 19, INK, true), 25);
        add(page, text(body, 14, MUTED, false), 8);
    }
    private String shortAddress(String value) { return value.length() < 18 ? value : value.substring(0, 8) + "…" + value.substring(value.length() - 6); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private GradientDrawable shape(int color, int radius, int stroke) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius)); shape.setStroke(dp(1), stroke); return shape;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL)); v.setLineSpacing(dp(3), 1); return v;
    }
    private void label(LinearLayout parent, String value) { label(parent, value, 0); }
    private void label(LinearLayout parent, String value, int top) { TextView v = text(value, 11, ACCENT, true); v.setLetterSpacing(.09f); add(parent, v, top); }
    private EditText input(String hint, boolean multi) {
        EditText v = new EditText(this); v.setTextSize(15); v.setTextColor(INK); v.setHintTextColor(MUTED); v.setHint(hint);
        v.setContentDescription(multi ? "Nonce guesses" : "Bitcoin receiving address");
        v.setPadding(dp(16), dp(14), dp(16), dp(14)); v.setBackground(shape(CARD, 14, LINE));
        v.setInputType(InputType.TYPE_CLASS_TEXT | (multi ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
        v.setMinLines(multi ? 3 : 1); v.setMaxLines(multi ? 6 : 1); v.setGravity(Gravity.TOP);
        v.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(multi ? 2048 : 90)});
        v.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO); return v;
    }
    private Button button(String value, boolean primary) {
        Button v = new Button(this); v.setText(value); v.setAllCaps(false); v.setTextSize(14); v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(primary ? BG : INK); v.setBackgroundTintList(null); v.setBackground(shape(primary ? ACCENT : CARD, 14, primary ? ACCENT : LINE));
        v.setPadding(dp(12), dp(8), dp(12), dp(8)); return v;
    }
    private void add(LinearLayout parent, View view, int top) { add(parent, view, top, -2); }
    private void add(LinearLayout parent, View view, int top, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, height < 0 ? height : dp(height)); params.topMargin = dp(top); parent.addView(view, params);
    }
}
