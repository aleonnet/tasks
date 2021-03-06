/**
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.actfm;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import com.todoroo.astrid.api.AstridApiConstants;
import com.todoroo.astrid.api.CustomFilter;
import com.todoroo.astrid.dao.StoreObjectDao;

import org.tasks.R;
import org.tasks.dialogs.DialogBuilder;
import org.tasks.injection.ActivityComponent;
import org.tasks.injection.ThemedInjectingAppCompatActivity;
import org.tasks.preferences.Preferences;
import org.tasks.ui.MenuColorizer;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;

import static android.text.TextUtils.isEmpty;

public class FilterSettingsActivity extends ThemedInjectingAppCompatActivity implements Toolbar.OnMenuItemClickListener {

    public static final String TOKEN_FILTER = "token_filter";

    private CustomFilter filter;

    @Inject StoreObjectDao storeObjectDao;
    @Inject DialogBuilder dialogBuilder;
    @Inject Preferences preferences;

    @BindView(R.id.tag_name) EditText filterName;
    @BindView(R.id.toolbar) Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.filter_settings_activity);
        ButterKnife.bind(this);

        filter = getIntent().getParcelableExtra(TOKEN_FILTER);

        final boolean backButtonSavesTask = preferences.backButtonSavesTask();
        toolbar.setNavigationIcon(getResources().getDrawable(
                backButtonSavesTask ? R.drawable.ic_close_24dp : R.drawable.ic_save_24dp));
        toolbar.setTitle(filter.listingTitle);
        toolbar.setNavigationOnClickListener(v -> {
            if (backButtonSavesTask) {
                discard();
            } else {
                save();
            }
        });
        toolbar.inflateMenu(R.menu.tag_settings_activity);
        toolbar.setOnMenuItemClickListener(this);
        MenuColorizer.colorToolbar(this, toolbar);

        filterName.setText(filter.listingTitle);
    }

    @Override
    public void inject(ActivityComponent component) {
        component.inject(this);
    }

    private void save() {
        String oldName = filter.listingTitle;
        String newName = filterName.getText().toString().trim();

        if (isEmpty(newName)) {
            Toast.makeText(this, R.string.name_cannot_be_empty, Toast.LENGTH_LONG).show();
            return;
        }

        boolean nameChanged = !oldName.equals(newName);
        if (nameChanged) {
            filter.listingTitle = newName;
            storeObjectDao.update(filter.toStoreObject());
            setResult(RESULT_OK, new Intent(AstridApiConstants.BROADCAST_EVENT_FILTER_RENAMED).putExtra(TOKEN_FILTER, filter));
        }

        finish();
    }

    @Override
    public void finish() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(filterName.getWindowToken(), 0);
        super.finish();
    }

    @Override
    public void onBackPressed() {
        if (preferences.backButtonSavesTask()) {
            save();
        } else {
            discard();
        }
    }

    private void deleteTag() {
        dialogBuilder.newMessageDialog(R.string.delete_tag_confirmation, filter.listingTitle)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    storeObjectDao.delete(filter.getId());
                    setResult(RESULT_OK, new Intent(AstridApiConstants.BROADCAST_EVENT_FILTER_DELETED).putExtra(TOKEN_FILTER, filter));
                    finish();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void discard() {
        String tagName = this.filterName.getText().toString().trim();
        if (filter.listingTitle.equals(tagName)) {
            finish();
        } else {
            dialogBuilder.newMessageDialog(R.string.discard_changes)
                    .setPositiveButton(R.string.discard, (dialog, which) -> finish())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.delete:
                deleteTag();
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}
