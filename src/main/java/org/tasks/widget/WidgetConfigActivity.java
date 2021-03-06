package org.tasks.widget;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;

import com.todoroo.astrid.api.Filter;

import org.tasks.Broadcaster;
import org.tasks.R;
import org.tasks.activities.ColorPickerActivity;
import org.tasks.activities.FilterSelectionActivity;
import org.tasks.analytics.Tracker;
import org.tasks.dialogs.ColorPickerDialog;
import org.tasks.dialogs.DialogBuilder;
import org.tasks.dialogs.SeekBarDialog;
import org.tasks.injection.ActivityComponent;
import org.tasks.injection.InjectingPreferenceActivity;
import org.tasks.preferences.DefaultFilterProvider;
import org.tasks.preferences.Preferences;
import org.tasks.themes.ThemeCache;
import org.tasks.themes.ThemeColor;
import org.tasks.themes.WidgetTheme;

import java.text.NumberFormat;

import javax.inject.Inject;

import static org.tasks.dialogs.SeekBarDialog.newSeekBarDialog;

public class WidgetConfigActivity extends InjectingPreferenceActivity implements SeekBarDialog.SeekBarCallback {

    private static final String FRAG_TAG_OPACITY_SEEKBAR = "frag_tag_opacity_seekbar";
    private static final String FRAG_TAG_FONT_SIZE_SEEKBAR = "frag_tag_font_size_seekbar";

    private static final int REQUEST_FILTER = 1005;
    private static final int REQUEST_THEME_SELECTION = 1006;
    private static final int REQUEST_COLOR_SELECTION = 1007;
    private static final int REQUEST_OPACITY = 1008;
    private static final int REQUEST_FONT_SIZE = 1009;

    @Inject Tracker tracker;
    @Inject DialogBuilder dialogBuilder;
    @Inject Broadcaster broadcaster;
    @Inject Preferences preferences;
    @Inject DefaultFilterProvider defaultFilterProvider;
    @Inject ThemeCache themeCache;

    private WidgetPreferences widgetPreferences;
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences_widget);

        appWidgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        // If they gave us an intent without the widget id, just bail.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }
        widgetPreferences = new WidgetPreferences(this, preferences, appWidgetId);
        setResult(RESULT_OK, new Intent() {{
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        }});

        setupCheckbox(R.string.p_widget_show_due_date);
        setupCheckbox(R.string.p_widget_show_checkboxes);
        CheckBoxPreference showHeader = setupCheckbox(R.string.p_widget_show_header);
        CheckBoxPreference showSettings = setupCheckbox(R.string.p_widget_show_settings);
        showSettings.setDependency(showHeader.getKey());

        getPref(R.string.p_widget_filter).setOnPreferenceClickListener(preference -> {
            startActivityForResult(new Intent(WidgetConfigActivity.this, FilterSelectionActivity.class) {{
                putExtra(FilterSelectionActivity.EXTRA_RETURN_FILTER, true);
            }}, REQUEST_FILTER);
            return false;
        });

        getPref(R.string.p_widget_theme).setOnPreferenceClickListener(preference -> {
            startActivityForResult(new Intent(WidgetConfigActivity.this, ColorPickerActivity.class) {{
                putExtra(ColorPickerActivity.EXTRA_PALETTE, ColorPickerDialog.ColorPalette.WIDGET_BACKGROUND);
            }}, REQUEST_THEME_SELECTION);
            return false;
        });

        Preference colorPreference = getPref(R.string.p_widget_color);
        colorPreference.setDependency(showHeader.getKey());
        colorPreference.setOnPreferenceClickListener(preference -> {
            startActivityForResult(new Intent(WidgetConfigActivity.this, ColorPickerActivity.class) {{
                putExtra(ColorPickerActivity.EXTRA_PALETTE, ColorPickerDialog.ColorPalette.COLORS);
            }}, REQUEST_COLOR_SELECTION);
            return false;
        });

        getPref(R.string.p_widget_opacity).setOnPreferenceClickListener(preference -> {
            newSeekBarDialog(R.layout.dialog_opacity_seekbar, widgetPreferences.getOpacity(), REQUEST_OPACITY)
                    .show(getFragmentManager(), FRAG_TAG_OPACITY_SEEKBAR);
            return false;
        });

        getPref(R.string.p_widget_font_size).setOnPreferenceClickListener(preference -> {
            newSeekBarDialog(R.layout.dialog_font_size_seekbar, widgetPreferences.getFontSize(), REQUEST_FONT_SIZE)
                    .show(getFragmentManager(), FRAG_TAG_FONT_SIZE_SEEKBAR);
            return false;
        });

        updateFilter();
        updateOpacity();
        updateFontSize();
        updateTheme();
        updateColor();
    }

    private CheckBoxPreference setupCheckbox(int resId) {
        CheckBoxPreference preference = (CheckBoxPreference) getPref(resId);
        String key = getString(resId) + appWidgetId;
        preference.setKey(key);
        preference.setChecked(preferences.getBoolean(key, true));
        return preference;
    }

    private void updateOpacity() {
        int opacity = widgetPreferences.getOpacity();
        getPref(R.string.p_widget_opacity).setSummary(NumberFormat.getPercentInstance().format(opacity / 100.0));
    }

    private void updateFontSize() {
        int fontSize = widgetPreferences.getFontSize();
        getPref(R.string.p_widget_font_size).setSummary(NumberFormat.getIntegerInstance().format(fontSize));
    }

    private void updateFilter() {
        Filter filter = defaultFilterProvider.getFilterFromPreference(widgetPreferences.getFilterId());
        getPref(R.string.p_widget_filter).setSummary(filter.listingTitle);
    }

    private void updateTheme() {
        WidgetTheme widgetTheme = themeCache.getWidgetTheme(widgetPreferences.getThemeIndex());
        getPref(R.string.p_widget_theme).setSummary(widgetTheme.getName());
    }

    private void updateColor() {
        ThemeColor themeColor = themeCache.getThemeColor(widgetPreferences.getColorIndex());
        getPref(R.string.p_widget_color).setSummary(themeColor.getName());
    }

    @Override
    protected void onPause() {
        super.onPause();

        broadcaster.refresh();
        // force update after setting preferences
        sendBroadcast(new Intent(this, TasksWidget.class) {{
            setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{appWidgetId});
        }});
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_FILTER) {
            if (resultCode == RESULT_OK) {
                Filter filter = data.getParcelableExtra(FilterSelectionActivity.EXTRA_FILTER);
                widgetPreferences.setFilter(defaultFilterProvider.getFilterPreferenceValue(filter));
                updateFilter();
            }
        } else if (requestCode == REQUEST_THEME_SELECTION) {
            if (resultCode == RESULT_OK) {
                widgetPreferences.setTheme(data.getIntExtra(ColorPickerActivity.EXTRA_THEME_INDEX, 0));
                updateTheme();
            }
        } else if (requestCode == REQUEST_COLOR_SELECTION) {
            if (resultCode == RESULT_OK) {
                widgetPreferences.setColor(data.getIntExtra(ColorPickerActivity.EXTRA_THEME_INDEX, 0));
                updateColor();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void inject(ActivityComponent component) {
        component.inject(this);
    }

    @Override
    public void valueSelected(int value, int requestCode) {
        if (requestCode == REQUEST_OPACITY) {
            widgetPreferences.setOpacity(value);
            updateOpacity();
        } else if (requestCode == REQUEST_FONT_SIZE) {
            widgetPreferences.setFontSize(value);
            updateFontSize();
        }
    }
}
