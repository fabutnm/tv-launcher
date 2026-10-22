package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private TextView themeChoice;
    private TextView wallpaperChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private static final int[] CLOCK_MINUTES = {0, 5, 10, 30};
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            dockChoices[i].setOnClickListener(v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(dockChoices);
        }
        themeChoice = findViewById(R.id.themeChoice);
        themeChoice.setVisibility(ThemeLibrary.names(this).isEmpty() ? View.GONE : View.VISIBLE);
        themeChoice.setOnClickListener(v -> showThemePicker());
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            setResult(RESULT_OK);
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        buildFeatureRows(config);
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).commit();
            SharedPreferences.Editor clear = choices.edit();
            for (String key : Config.FEATURE_KEYS) clear.remove(key);
            clear.commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            dockOpacity = current.dockOpacity;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            setResult(RESULT_OK);
            buildFeatureRows(current);   // the switch rows show the theme's values again
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        refresh();
        if (config.launcherSettingsButton) reorderChoice.requestFocus();
        else columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    /** The look-and-feel switch rows, showing the saved choice or else what the theme (config) decides. */
    private void buildFeatureRows(Config config) {
        LinearLayout features = findViewById(R.id.featureRows);
        features.removeAllViews();
        addSwitch(features, R.string.feature_cinematic, Config.CINEMATIC_KEY, config.cinematicHome);
        addSwitch(features, R.string.feature_sky, Config.SKY_KEY, config.skyIntensity > 0);
        addSwitch(features, R.string.feature_true_color, Config.TRUE_COLOR_KEY, config.wallpaperTrueColor);
        addSwitch(features, R.string.feature_all_names, Config.ALL_NAMES_KEY, config.alwaysShowNames);
        addSwitch(features, R.string.feature_library, Config.LIBRARY_KEY, config.appLibrary);
        addClockRow(features, config.homeIdleClockMinutes);
    }

    /** One On/Off row for a look-and-feel switch; saved at once, Home rebuilds when settings close. */
    private void addSwitch(LinearLayout parent, int label, String key, boolean initial) {
        TextView row = settingsRow(parent);
        boolean[] value = {choices.getBoolean(key, initial)};
        Runnable show = () -> row.setText(getString(R.string.feature_state, getString(label),
                getString(value[0] ? R.string.on : R.string.off)));
        row.setOnClickListener(v -> {
            value[0] = !value[0];
            choices.edit().putBoolean(key, value[0]).apply();
            setResult(RESULT_OK);
            show.run();
            refresh();
        });
        show.run();
    }

    /** The idle clock on Home: Off, 5, 10 or 30 minutes; each press steps to the next. */
    private void addClockRow(LinearLayout parent, int initial) {
        TextView row = settingsRow(parent);
        int[] minutes = {choices.getInt(Config.IDLE_CLOCK_KEY, initial)};
        Runnable show = () -> row.setText(getString(R.string.feature_state, getString(R.string.feature_clock),
                minutes[0] == 0 ? getString(R.string.off) : getString(R.string.feature_minutes, minutes[0])));
        row.setOnClickListener(v -> {
            int next = CLOCK_MINUTES[1];
            for (int i = 0; i < CLOCK_MINUTES.length; i++) {
                if (CLOCK_MINUTES[i] == minutes[0]) next = CLOCK_MINUTES[(i + 1) % CLOCK_MINUTES.length];
            }
            minutes[0] = next;
            choices.edit().putInt(Config.IDLE_CLOCK_KEY, next).apply();
            setResult(RESULT_OK);
            show.run();
            refresh();
        });
        show.run();
    }

    private TextView settingsRow(LinearLayout parent) {
        TextView row = (TextView) getLayoutInflater().inflate(R.layout.settings_row, parent, false);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) row.getLayoutParams();
        lp.topMargin = Math.round(10 * getResources().getDisplayMetrics().density);
        parent.addView(row);
        return row;
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    /** Library themes (ThemeLibrary); picking one replaces the theme in place, like tools/theme.sh does. */
    private void showThemePicker() {
        List<String> names = ThemeLibrary.names(this);
        if (names.isEmpty()) return;
        String[] labels = new String[names.size()];
        for (int i = 0; i < names.size(); i++) labels[i] = ThemeLibrary.label(this, names.get(i));
        int checked = names.indexOf(ThemeLibrary.current(this));
        new AlertDialog.Builder(this)
                .setTitle(R.string.theme_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    String name = names.get(which);
                    themeChoice.setEnabled(false);
                    new Thread(() -> {
                        boolean applied = ThemeLibrary.apply(this, name);
                        runOnUiThread(() -> themeApplied(applied));
                    }, "theme-apply").start();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void themeApplied(boolean applied) {
        if (isFinishing() || isDestroyed()) return;
        themeChoice.setEnabled(true);
        if (!applied) {
            Toast.makeText(this, R.string.theme_failed, Toast.LENGTH_LONG).show();
            return;
        }
        // Loading the new theme clears the on-TV choices, as a push from the PC does; show what it decides.
        Config current = Config.load(this);
        columns = current.columns;
        rowsBelowDock = current.rowsBelowDock;
        dockOpacity = current.dockOpacity;
        animations = current.animations;
        advancedEffects = current.animations && current.advancedEffects;
        setResult(RESULT_OK);
        Toast.makeText(this, R.string.theme_applied, Toast.LENGTH_SHORT).show();
        buildFeatureRows(current);
        refresh();
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size()
                : indexOf(images, current);
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which).getName()).apply();
                    }
                    setResult(RESULT_OK);
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    setResult(RESULT_OK);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        setResult(RESULT_OK);
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String theme = ThemeLibrary.current(this);
        themeChoice.setText(getString(R.string.theme_state,
                theme != null ? ThemeLibrary.label(this, theme) : getString(R.string.theme_custom)));
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        for (String key : Config.FEATURE_KEYS) if (choices.contains(key)) return true;
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
