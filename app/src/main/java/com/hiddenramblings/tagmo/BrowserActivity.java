package com.hiddenramblings.tagmo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.eightbit.content.ActionIntent;
import com.eightbit.io.Debug;
import com.eightbit.os.Storage;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.snackbar.Snackbar;
import com.hiddenramblings.tagmo.adapter.BrowserAmiibosAdapter;
import com.hiddenramblings.tagmo.adapter.BrowserFoldersAdapter;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboFile;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.AmiiboSeries;
import com.hiddenramblings.tagmo.amiibo.AmiiboType;
import com.hiddenramblings.tagmo.amiibo.Character;
import com.hiddenramblings.tagmo.amiibo.GameSeries;
import com.hiddenramblings.tagmo.github.InstallReceiver;
import com.hiddenramblings.tagmo.github.RequestCommit;
import com.hiddenramblings.tagmo.nfctech.KeyManager;
import com.hiddenramblings.tagmo.nfctech.PowerTagManager;
import com.hiddenramblings.tagmo.nfctech.TagReader;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.settings.BrowserSettings;
import com.hiddenramblings.tagmo.settings.BrowserSettings.BrowserSettingsListener;
import com.robertlevonyan.views.chip.Chip;
import com.robertlevonyan.views.chip.OnCloseClickListener;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.InstanceState;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.OptionsMenu;
import org.androidannotations.annotations.OptionsMenuItem;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.api.BackgroundExecutor;
import org.apmem.tools.layouts.FlowLayout;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.activity_browser)
@OptionsMenu({R.menu.browser_menu})
public class BrowserActivity extends AppCompatActivity implements
        SearchView.OnQueryTextListener,
        SwipeRefreshLayout.OnRefreshListener,
        BrowserSettingsListener,
        BrowserAmiibosAdapter.OnAmiiboClickListener {

    public static final int SORT_ID = 0x0;
    public static final int SORT_NAME = 0x1;
    public static final int SORT_AMIIBO_SERIES = 0x2;
    public static final int SORT_AMIIBO_TYPE = 0x3;
    public static final int SORT_GAME_SERIES = 0x4;
    public static final int SORT_CHARACTER = 0x5;
    public static final int SORT_FILE_PATH = 0x6;

    public static final int VIEW_TYPE_SIMPLE = 0;
    public static final int VIEW_TYPE_COMPACT = 1;
    public static final int VIEW_TYPE_LARGE = 2;
    public static final int VIEW_TYPE_IMAGE = 3;

    @ViewById(R.id.chip_list)
    FlowLayout chipList;
    @ViewById(R.id.amiibos_list)
    RecyclerView amiibosView;
    @ViewById(R.id.swipe_refresh)
    SwipeRefreshLayout swipeRefreshLayout;
    @ViewById(R.id.empty_text)
    TextView emptyText;
    @ViewById(R.id.folders_list)
    RecyclerView foldersView;
    @ViewById(R.id.bottom_sheet)
    ViewGroup bottomSheet;
    @ViewById(R.id.current_folder)
    TextView currentFolderView;
    @ViewById(R.id.toggle)
    ImageView toggle;

    @OptionsMenuItem(R.id.search)
    MenuItem menuSearch;
    @OptionsMenuItem(R.id.sort_id)
    MenuItem menuSortId;
    @OptionsMenuItem(R.id.sort_name)
    MenuItem menuSortName;
    @OptionsMenuItem(R.id.sort_game_series)
    MenuItem menuSortGameSeries;
    @OptionsMenuItem(R.id.sort_character)
    MenuItem menuSortCharacter;
    @OptionsMenuItem(R.id.sort_amiibo_series)
    MenuItem menuSortAmiiboSeries;
    @OptionsMenuItem(R.id.sort_amiibo_type)
    MenuItem menuSortAmiiboType;
    @OptionsMenuItem(R.id.sort_file_path)
    MenuItem menuSortFilePath;
    @OptionsMenuItem(R.id.filter_game_series)
    MenuItem menuFilterGameSeries;
    @OptionsMenuItem(R.id.filter_character)
    MenuItem menuFilterCharacter;
    @OptionsMenuItem(R.id.filter_amiibo_series)
    MenuItem menuFilterAmiiboSeries;
    @OptionsMenuItem(R.id.filter_amiibo_type)
    MenuItem menuFilterAmiiboType;
    @OptionsMenuItem(R.id.view_simple)
    MenuItem menuViewSimple;
    @OptionsMenuItem(R.id.view_compact)
    MenuItem menuViewCompact;
    @OptionsMenuItem(R.id.view_large)
    MenuItem menuViewLarge;
    @OptionsMenuItem(R.id.view_image)
    MenuItem menuViewImage;
    @OptionsMenuItem(R.id.recursive)
    MenuItem menuRecursiveFiles;
    @OptionsMenuItem(R.id.show_downloads)
    MenuItem menuIncludeDownloads;
    @OptionsMenuItem(R.id.show_missing)
    MenuItem menuShowMissing;
    @OptionsMenuItem(R.id.enable_scale)
    MenuItem menuEnableScale;
    @OptionsMenuItem(R.id.refresh)
    MenuItem menuRefresh;
    @OptionsMenuItem(R.id.amiibo_backup)
    MenuItem menuBackup;
    @OptionsMenuItem(R.id.export_logcat)
    MenuItem menuLogcat;
    @OptionsMenuItem(R.id.unlock_elite)
    MenuItem menuUnlockElite;

    BottomSheetBehavior<View> bottomSheetBehavior;
    KeyManager keyManager;
    SearchView searchView;
    private AmiiboFile clickedAmiibo = null;
    private Handler handler = new Handler();

    @InstanceState
    BrowserSettings settings;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TagMo.setScaledTheme(this, R.style.AppTheme);
        this.supportInvalidateOptionsMenu();
        File[] files = getFilesDir().listFiles((dir, name) ->
                name.toLowerCase(Locale.ROOT).endsWith(".apk"));
        if (files != null) {
            for (File file : files) {
                if (!file.isDirectory())
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
            }
        }
        keyManager = new KeyManager(this);
    }

    @AfterViews
    void afterViews() {
        this.bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        this.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        this.bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    toggle.setImageResource(R.drawable.ic_expand_less_white_24dp);
                } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    toggle.setImageResource(R.drawable.ic_expand_more_white_24dp);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
            }
        });

        if (this.settings == null) {
            this.settings = new BrowserSettings().initialize();
        } else {
            this.onFilterGameSeriesChanged();
            this.onFilterCharacterChanged();
            this.onFilterAmiiboSeriesChanged();
            this.onFilterAmiiboTypeChanged();
            this.onAmiiboFilesChanged();
        }
        this.settings.addChangeListener(this);

        this.swipeRefreshLayout.setOnRefreshListener(this);

        if (this.settings.getAmiiboView() == VIEW_TYPE_IMAGE)
            this.amiibosView.setLayoutManager(new GridLayoutManager(this, getColumnCount()));
        else
            this.amiibosView.setLayoutManager(new LinearLayoutManager(this));
        this.amiibosView.setAdapter(new BrowserAmiibosAdapter(settings, this));
        this.settings.addChangeListener((BrowserSettingsListener) this.amiibosView.getAdapter());

        this.foldersView.setLayoutManager(new LinearLayoutManager(this));
        this.foldersView.setAdapter(new BrowserFoldersAdapter(settings));
        this.settings.addChangeListener((BrowserSettingsListener) this.foldersView.getAdapter());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                this.onRefresh();
            } else {
                requestScopedStorage();
            }
        } else {
            int permission = ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE);
            if (permission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
            } else {
                this.onRefresh();
            }
        }

        this.loadPTagKeyManager();

        new RequestCommit().setListener(result -> {
            try {
                JSONObject jsonObject = (JSONObject) new JSONTokener(result).nextValue();
                String lastCommit = ((String) jsonObject.get("name")).substring(6);
                if (!BuildConfig.COMMIT.equals(lastCommit)) {
                    JSONObject assets = (JSONObject) ((JSONArray) jsonObject.get("assets")).get(0);
                    showInstallSnackbar((String) assets.get("browser_download_url"));
                }
            } catch (Exception e) {
                Debug.Error(e);
            }
        }).execute(getString(R.string.repo_url,
                TagMo.getPrefs().stableChannel().get() ? "master" : "experimental"));
    }

    ActivityResultLauncher<Intent> onNFCActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) return;

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction())) return;

        if (result.getData().hasExtra(TagMo.EXTRA_SIGNATURE)) {
            String signature = result.getData().getStringExtra(TagMo.EXTRA_SIGNATURE);
            int active_bank = result.getData().getIntExtra(
                    TagMo.EXTRA_ACTIVE_BANK, TagMo.getPrefs().eliteActiveBank().get());
            int bank_count = result.getData().getIntExtra(
                    TagMo.EXTRA_BANK_COUNT, TagMo.getPrefs().eliteBankCount().get());

            TagMo.getPrefs().eliteSignature().put(signature);
            TagMo.getPrefs().eliteActiveBank().put(active_bank);
            TagMo.getPrefs().eliteBankCount().put(bank_count);

            Intent eliteIntent = new Intent(this, BankListActivity_.class);
            eliteIntent.putExtras(result.getData());
            eliteIntent.putExtra(TagMo.EXTRA_AMIIBO_FILES, settings.getAmiiboFiles());
            startActivity(eliteIntent);
        } else {
            Bundle args = new Bundle();
            args.putByteArray(TagMo.EXTRA_TAG_DATA,
                    result.getData().getByteArrayExtra(TagMo.EXTRA_TAG_DATA));

            startActivity(new Intent(this, AmiiboActivity_.class).putExtras(args));
        }
    });

    @Click(R.id.fab)
    public void onFabClicked() {
        onNFCActivity.launch(new Intent(this,
                NfcActivity_.class).setAction(TagMo.ACTION_SCAN_TAG));
    }

    @Click(R.id.toggle)
    void onBrowserFolderExpandClick() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        } else {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    @OptionsItem(R.id.sort_id)
    void onSortIdClick() {
        settings.setSort(SORT_ID);
        settings.notifyChanges();
    }

    @OptionsItem(R.id.sort_name)
    void onSortNameClick() {
        settings.setSort(SORT_NAME);
        settings.notifyChanges();
    }

    @OptionsItem(R.id.sort_game_series)
    void onSortGameSeriesClick() {
        settings.setSort(SORT_GAME_SERIES);
        settings.notifyChanges();
    }

    @OptionsItem(R.id.sort_character)
    void onSortCharacterClick() {
        settings.setSort(SORT_CHARACTER);
        settings.notifyChanges();
    }

    @OptionsItem(R.id.sort_amiibo_series)
    void onSortAmiiboSeriesClick() {
        settings.setSort(SORT_AMIIBO_SERIES);
        settings.notifyChanges();
    }

    @OptionsItem(R.id.sort_amiibo_type)
    void onSortAmiiboTypeClick() {
        settings.setSort(SORT_AMIIBO_TYPE);
        settings.notifyChanges();
    }

    @OptionsItem(R.id.sort_file_path)
    void onSortFilePathClick() {
        settings.setSort(SORT_FILE_PATH);
        settings.notifyChanges();
    }

    @OptionsItem(R.id.view_simple)
    void onViewSimpleClick() {
        if (this.settings.getAmiiboView() == VIEW_TYPE_IMAGE)
            this.amiibosView.setLayoutManager(new LinearLayoutManager(this));
        settings.setAmiiboView(VIEW_TYPE_SIMPLE);
        settings.notifyChanges();
    }

    @OptionsItem(R.id.view_compact)
    void onViewCompactClick() {
        if (this.settings.getAmiiboView() == VIEW_TYPE_IMAGE)
            this.amiibosView.setLayoutManager(new LinearLayoutManager(this));
        settings.setAmiiboView(VIEW_TYPE_COMPACT);
        settings.notifyChanges();
    }

    @OptionsItem(R.id.view_large)
    void onViewLargeClick() {
        if (this.settings.getAmiiboView() == VIEW_TYPE_IMAGE)
            this.amiibosView.setLayoutManager(new LinearLayoutManager(this));
        settings.setAmiiboView(VIEW_TYPE_LARGE);
        settings.notifyChanges();
    }

    @OptionsItem(R.id.view_image)
    void onViewImageClick() {
        this.amiibosView.setLayoutManager(new GridLayoutManager(this, getColumnCount()));
        settings.setAmiiboView(VIEW_TYPE_IMAGE);
        settings.notifyChanges();
    }

    @OptionsItem(R.id.recursive)
    void onRecursiveFilesClicked() {
        this.settings.setRecursiveEnabled(!this.settings.isRecursiveEnabled());
        this.settings.notifyChanges();
    }

    @OptionsItem(R.id.show_downloads)
    void OnIncludeDownloadsCicked() {
        this.settings.setIncludeDownloads(!this.settings.isShowingDownloads());
        this.settings.notifyChanges();
    }

    @OptionsItem(R.id.show_missing)
    void OnShowMissingCicked() {
        this.settings.setShowMissingFiles(!this.settings.isShowingMissingFiles());
        this.settings.notifyChanges();
    }

    @OptionsItem(R.id.enable_scale)
    void onEnableScaleClicked() {
        TagMo.getPrefs().enableScaling().put(!menuEnableScale.isChecked());
        this.recreate();
        this.supportInvalidateOptionsMenu();
    }

    ActivityResultLauncher<Intent> onBackupActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) return;

        if (!TagMo.ACTION_NFC_SCANNED.equals(result.getData().getAction())) return;

        byte[] tagData = result.getData().getByteArrayExtra(TagMo.EXTRA_TAG_DATA);

        View view = getLayoutInflater().inflate(R.layout.dialog_backup, amiibosView, false);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        final EditText input = view.findViewById(R.id.backup_entry);
        input.setText(TagReader.generateFileName(settings.getAmiiboManager(), tagData));
        Dialog backupDialog = dialog.setView(view).show();
        view.findViewById(R.id.save_backup).setOnClickListener(v -> {
            try {
                File directory = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                String fileName = TagReader.writeBytesToFile(directory,
                        input.getText().toString() + ".bin", tagData);
                showToast(getString(R.string.wrote_file, fileName));
                this.onRootFolderChanged(false);
            } catch (IOException e) {
                showToast(e.getMessage());
            }
            backupDialog.dismiss();
        });
        view.findViewById(R.id.cancel_backup).setOnClickListener(v -> backupDialog.dismiss());
    });

    @OptionsItem(R.id.amiibo_backup)
    void onAmiiboBackupClicked() {
        Intent backup = new Intent(this, NfcActivity_.class);
        backup.setAction(TagMo.ACTION_BACKUP_AMIIBO);
        onBackupActivity.launch(backup);
    }

    @UiThread
    public void showLogcatSnackbar(String message, Uri fileUri) {
        Snackbar snackbar = new IconifiedSnackbar(this).buildSnackbar(
                message, Snackbar.LENGTH_LONG);
        if (fileUri != null) {
            snackbar.setAction(R.string.view, v -> {
                startActivity(new ActionIntent(Intent.ACTION_VIEW, fileUri));
                snackbar.dismiss();
            });
        }
        snackbar.show();
    }

    @OptionsItem(R.id.export_logcat)
    @Background
    void onExportLogcatClicked() {
        try {
            File file = Debug.generateLogcat(new File(
                    TagMo.getExternalFiles(), "tagmo_logcat.txt"));
            TagMo.scanFile(file);
            showLogcatSnackbar(getString(R.string.wrote_file,
                    Storage.getRelativePath(file)), Storage.getFileUri(file));
        } catch (IOException e) {
            showLogcatSnackbar(e.getMessage(), null);
        }
    }

    @OptionsItem(R.id.unlock_elite)
    void onUnlockEliteClicked() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.prepare_unlock)
                .setPositiveButton(R.string.start, (dialog, which) -> {
                    Intent unlock = new Intent(this, NfcActivity_.class);
                    unlock.setAction(TagMo.ACTION_UNLOCK_UNIT);
                    startActivity(unlock);
                    dialog.dismiss();
                }).show();
    }

    @OptionsItem(R.id.filter_game_series)
    boolean onFilterGameSeriesClick() {
        SubMenu subMenu = menuFilterGameSeries.getSubMenu();
        subMenu.clear();

        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (amiiboManager == null)
            return false;

        Set<String> items = new HashSet<>();
        for (AmiiboFile amiiboFile : settings.getAmiiboFiles()) {
            Amiibo amiibo = amiiboManager.amiibos.get(amiiboFile.getId());
            if (amiibo == null)
                continue;

            GameSeries gameSeries = amiibo.getGameSeries();
            if (gameSeries != null &&
                    Amiibo.matchesCharacterFilter(amiibo.getCharacter(), settings.getCharacterFilter()) &&
                    Amiibo.matchesAmiiboSeriesFilter(amiibo.getAmiiboSeries(), settings.getAmiiboSeriesFilter()) &&
                    Amiibo.matchesAmiiboTypeFilter(amiibo.getAmiiboType(), settings.getAmiiboTypeFilter())
            ) {
                items.add(gameSeries.name);
            }
        }

        ArrayList<String> list = new ArrayList<>(items);
        Collections.sort(list);
        for (String item : list) {
            subMenu.add(R.id.filter_game_series_group, Menu.NONE, 0, item)
                    .setChecked(item.equals(settings.getGameSeriesFilter()))
                    .setOnMenuItemClickListener(onFilterGameSeriesItemClick);
        }
        subMenu.setGroupCheckable(R.id.filter_game_series_group, true, true);

        return true;
    }

    MenuItem.OnMenuItemClickListener onFilterGameSeriesItemClick =
            new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            settings.setGameSeriesFilter(menuItem.getTitle().toString());
            settings.notifyChanges();
            return false;
        }
    };

    @OptionsItem(R.id.filter_character)
    boolean onFilterCharacterClick() {
        SubMenu subMenu = menuFilterCharacter.getSubMenu();
        subMenu.clear();

        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (amiiboManager == null)
            return true;

        Set<String> items = new HashSet<>();
        for (AmiiboFile amiiboFile : settings.getAmiiboFiles()) {
            Amiibo amiibo = amiiboManager.amiibos.get(amiiboFile.getId());
            if (amiibo == null)
                continue;

            Character character = amiibo.getCharacter();
            if (character != null &&
                    Amiibo.matchesGameSeriesFilter(amiibo.getGameSeries(), settings.getGameSeriesFilter()) &&
                    Amiibo.matchesAmiiboSeriesFilter(amiibo.getAmiiboSeries(), settings.getAmiiboSeriesFilter()) &&
                    Amiibo.matchesAmiiboTypeFilter(amiibo.getAmiiboType(), settings.getAmiiboTypeFilter())
            ) {
                items.add(character.name);
            }
        }

        ArrayList<String> list = new ArrayList<>(items);
        Collections.sort(list);
        for (String item : list) {
            subMenu.add(R.id.filter_character_group, Menu.NONE, 0, item)
                    .setChecked(item.equals(settings.getCharacterFilter()))
                    .setOnMenuItemClickListener(onFilterCharacterItemClick);
        }
        subMenu.setGroupCheckable(R.id.filter_character_group, true, true);

        return true;
    }

    MenuItem.OnMenuItemClickListener onFilterCharacterItemClick =
            new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            settings.setCharacterFilter(menuItem.getTitle().toString());
            settings.notifyChanges();
            return false;
        }
    };

    @OptionsItem(R.id.filter_amiibo_series)
    boolean onFilterAmiiboSeriesClick() {
        SubMenu subMenu = menuFilterAmiiboSeries.getSubMenu();
        subMenu.clear();

        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (amiiboManager == null)
            return true;

        Set<String> items = new HashSet<>();
        for (AmiiboFile amiiboFile : settings.getAmiiboFiles()) {
            Amiibo amiibo = amiiboManager.amiibos.get(amiiboFile.getId());
            if (amiibo == null)
                continue;

            AmiiboSeries amiiboSeries = amiibo.getAmiiboSeries();
            if (amiiboSeries != null &&
                    Amiibo.matchesGameSeriesFilter(amiibo.getGameSeries(), settings.getGameSeriesFilter()) &&
                    Amiibo.matchesCharacterFilter(amiibo.getCharacter(), settings.getCharacterFilter()) &&
                    Amiibo.matchesAmiiboTypeFilter(amiibo.getAmiiboType(), settings.getAmiiboTypeFilter())
            ) {
                items.add(amiiboSeries.name);
            }
        }

        ArrayList<String> list = new ArrayList<>(items);
        Collections.sort(list);
        for (String item : list) {
            subMenu.add(R.id.filter_amiibo_series_group, Menu.NONE, 0, item)
                    .setChecked(item.equals(settings.getAmiiboSeriesFilter()))
                    .setOnMenuItemClickListener(onFilterAmiiboSeriesItemClick);
        }
        subMenu.setGroupCheckable(R.id.filter_amiibo_series_group, true, true);

        return true;
    }

    MenuItem.OnMenuItemClickListener onFilterAmiiboSeriesItemClick = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            settings.setAmiiboSeriesFilter(menuItem.getTitle().toString());
            settings.notifyChanges();
            return false;
        }
    };

    @OptionsItem(R.id.filter_amiibo_type)
    boolean onFilterAmiiboTypeClick() {
        SubMenu subMenu = menuFilterAmiiboType.getSubMenu();
        subMenu.clear();

        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (amiiboManager == null)
            return true;

        Set<AmiiboType> items = new HashSet<>();
        for (AmiiboFile amiiboFile : settings.getAmiiboFiles()) {
            Amiibo amiibo = amiiboManager.amiibos.get(amiiboFile.getId());
            if (amiibo == null)
                continue;

            AmiiboType amiiboType = amiibo.getAmiiboType();
            if (amiiboType != null &&
                    Amiibo.matchesGameSeriesFilter(amiibo.getGameSeries(), settings.getGameSeriesFilter()) &&
                    Amiibo.matchesCharacterFilter(amiibo.getCharacter(), settings.getCharacterFilter()) &&
                    Amiibo.matchesAmiiboSeriesFilter(amiibo.getAmiiboSeries(), settings.getAmiiboSeriesFilter())
            ) {
                items.add(amiiboType);
            }
        }

        ArrayList<AmiiboType> list = new ArrayList<>(items);
        Collections.sort(list);
        for (AmiiboType item : list) {
            subMenu.add(R.id.filter_amiibo_type_group, Menu.NONE, 0, item.name)
                    .setChecked(item.name.equals(settings.getAmiiboTypeFilter()))
                    .setOnMenuItemClickListener(onFilterAmiiboTypeItemClick);
        }
        subMenu.setGroupCheckable(R.id.filter_amiibo_type_group, true, true);

        return true;
    }

    MenuItem.OnMenuItemClickListener onFilterAmiiboTypeItemClick = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            settings.setAmiiboTypeFilter(menuItem.getTitle().toString());
            settings.notifyChanges();
            return false;
        }
    };

    @SuppressLint("RestrictedApi")
    ActivityResultLauncher<Intent> onSettingsActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
        if (result.getData().hasExtra(SettingsActivity.STORAGE)) {
            this.settings.setBrowserRootFolder(new File(Storage.getFile(),
                    TagMo.getPrefs().browserRootFolder().get()));
            this.settings.notifyChanges();
        }
        if (result.getData().hasExtra(SettingsActivity.POWERTAG)) {
            this.loadPTagKeyManager();
        }
        if (result.getData().hasExtra(SettingsActivity.REFRESH)) {
            this.onRefresh();
        }
    });

    @OptionsItem(R.id.settings)
    void openSettings() {
        onSettingsActivity.launch(new Intent(this, SettingsActivity_.class));
    }

    @OptionsItem(R.id.refresh)
    @Override
    public void onRefresh() {
        this.loadAmiiboManager();
        this.onRootFolderChanged(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);

        this.onSortChanged();
        this.onViewChanged();
        this.onRecursiveFilesChanged();
        this.onIncludeDownloadsChanged();
        this.onShowMissingChanged();
        this.onEnableScaleChanged();

        menuUnlockElite.setVisible(TagMo.getPrefs().enableEliteSupport().get());

        // setOnQueryTextListener will clear this, so make a copy
        String query = settings.getQuery();

        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchView = (SearchView) menuSearch.getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setSubmitButtonEnabled(true);
        searchView.setOnQueryTextListener(this);
        menuSearch.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                    onBackPressed();
                    return false;
                } else if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    onBackPressed();
                    return false;
                } else {
                    return true;
                }
            }
        });

        //focus the SearchView
        if (!query.isEmpty()) {
            menuSearch.expandActionView();
            searchView.setQuery(query, true);
            searchView.clearFocus();
        }

        return result;
    }

    ActivityResultLauncher<Intent> onViewerActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        this.onRootFolderChanged(false);

        if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
        if (!TagMo.ACTION_DELETE_AMIIBO.equals(result.getData().getAction())) return;

        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.warn_delete_file,
                        Storage.getRelativePath(clickedAmiibo.getFilePath())))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    //noinspection ResultOfMethodCallIgnored
                    clickedAmiibo.getFilePath().delete();
                    this.onRootFolderChanged(false);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    openAmiiboViewer(clickedAmiibo);
                    dialog.dismiss();
                }).show();
    });

    private void openAmiiboViewer(AmiiboFile amiiboFile) {
        Bundle args = new Bundle();
        try {
            args.putByteArray(TagMo.EXTRA_TAG_DATA,
                    TagReader.readTagStream(amiiboFile.getFilePath()));
        } catch (Exception e) {
            Debug.Error(e);
            return;
        }

        Intent intent = new Intent(this, AmiiboActivity_.class);
        intent.putExtras(args);
        onViewerActivity.launch(intent);
    }

    @Override
    public void onAmiiboClicked(AmiiboFile amiiboFile) {
        if (amiiboFile.getFilePath() == null)
            return;

        clickedAmiibo = amiiboFile;
        openAmiiboViewer(amiiboFile);
    }

    @Override
    public void onAmiiboImageClicked(AmiiboFile amiiboFile) {
        Bundle bundle = new Bundle();
        bundle.putLong(TagMo.EXTRA_AMIIBO_ID, amiiboFile.getId());

        Intent intent = new Intent(this, ImageActivity_.class);
        intent.putExtras(bundle);

        this.startActivity(intent);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        settings.setQuery(query);
        settings.notifyChanges();
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        settings.setQuery(newText);
        settings.notifyChanges();
        return true;
    }

    @UiThread
    void setAmiiboFilesLoadingBarVisibility(boolean visible) {
        this.swipeRefreshLayout.setRefreshing(visible);
    }

    public static final String BACKGROUND_POWERTAG = "powertag";

    void loadPTagKeyManager() {
        if (TagMo.getPrefs().enablePowerTagSupport().get()) {
            BackgroundExecutor.cancelAll(BACKGROUND_POWERTAG, true);
            loadPTagKeyManagerTask();
        }
    }

    @Background(id = BACKGROUND_POWERTAG)
    void loadPTagKeyManagerTask() {
        try {
            PowerTagManager.getPowerTagManager();
        } catch (Exception e) {
            Debug.Error(e);
            showToast(R.string.fail_powertag_keys);
        }
    }

    public static final String BACKGROUND_AMIIBO_MANAGER = "amiibo_manager";

    void loadAmiiboManager() {
        BackgroundExecutor.cancelAll(BACKGROUND_AMIIBO_MANAGER, true);
        loadAmiiboManagerTask();
    }

    @Background(id = BACKGROUND_AMIIBO_MANAGER)
    void loadAmiiboManagerTask() {
        AmiiboManager amiiboManager;
        try {
            amiiboManager = AmiiboManager.getAmiiboManager();
        } catch (IOException | JSONException | ParseException e) {
            Debug.Error(e);
            amiiboManager = null;
            showToast(R.string.amiibo_info_parse_error);
        }

        if (Thread.currentThread().isInterrupted())
            return;

        final AmiiboManager uiAmiiboManager = amiiboManager;
        this.runOnUiThread(() -> {
            settings.setAmiiboManager(uiAmiiboManager);
            settings.notifyChanges();
        });
    }

    public static final String BACKGROUND_FOLDERS = "folders";

    void loadFolders(File rootFolder) {
        BackgroundExecutor.cancelAll(BACKGROUND_FOLDERS, true);
        loadFoldersTask(rootFolder);
    }

    ArrayList<File> listFolders(File rootFolder) {
        ArrayList<File> folders = new ArrayList<>();

        File[] files = rootFolder.listFiles();
        if (files == null)
            return folders;

        for (File file : files) {
            if (file.isDirectory()) {
                folders.add(file);
            }
        }
        return folders;
    }

    @Background(id = BACKGROUND_FOLDERS)
    void loadFoldersTask(File rootFolder) {
        final ArrayList<File> folders = listFolders(rootFolder);
        Collections.sort(folders, (file1, file2) -> file1.getPath().compareToIgnoreCase(file2.getPath()));

        if (Thread.currentThread().isInterrupted())
            return;

        this.runOnUiThread(() -> {
            settings.setFolders(folders);
            settings.notifyChanges();
        });
    }

    public static final String BACKGROUND_AMIIBO_FILES = "amiibo_files";

    void loadAmiiboFiles(File rootFolder, boolean recursiveFiles) {
        BackgroundExecutor.cancelAll(BACKGROUND_AMIIBO_FILES, true);
        loadAmiiboFilesTask(rootFolder, recursiveFiles);
    }
    
    ArrayList<AmiiboFile> listAmiibos(File rootFolder, boolean recursiveFiles) {
        ArrayList<AmiiboFile> amiiboFiles = new ArrayList<>();
        File[] files = rootFolder.listFiles();
        if (files == null)
            return amiiboFiles;

        for (File file : files) {
            if (file.isDirectory() && recursiveFiles) {
                amiiboFiles.addAll(listAmiibos(file, true));
            } else {
                if (file.getName().toLowerCase(Locale.ROOT).endsWith(".bin")) {
                    try {
                        byte[] data = TagReader.readTagFile(file);
                        TagReader.validateTag(data);
                        amiiboFiles.add(new AmiiboFile(file, TagUtils.amiiboIdFromTag(data)));
                    } catch (Exception e) {
                        //
                    }
                }

            }
        }
        return amiiboFiles;
    }

    @Background(id = BACKGROUND_AMIIBO_FILES)
    void loadAmiiboFilesTask(File rootFolder, boolean recursiveFiles) {
        final ArrayList<AmiiboFile> amiiboFiles = listAmiibos(rootFolder, recursiveFiles);
        if (TagMo.getPrefs().includeDownloads().get()) {
            amiiboFiles.addAll(listAmiibos(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), recursiveFiles));
        }

        if (Thread.currentThread().isInterrupted())
            return;
        this.setAmiiboFilesLoadingBarVisibility(false);

        if (amiiboFiles.isEmpty() && !TagMo.getPrefs().ignoreSdcard().get()) {
            emptyText.setText(R.string.amiibo_not_found);
            if (recursiveFiles) showStorageSnackbar();
        }
        this.runOnUiThread(() -> {
            settings.setAmiiboFiles(amiiboFiles);
            settings.notifyChanges();
        });
    }

    @Override
    public void onBrowserSettingsChanged(BrowserSettings newBrowserSettings, BrowserSettings oldBrowserSettings) {
        if (newBrowserSettings == null || oldBrowserSettings == null) return;
        boolean folderChanged = false;
        if (!BrowserSettings.equals(newBrowserSettings.getBrowserRootFolder(), oldBrowserSettings.getBrowserRootFolder())) {
            folderChanged = true;
        }
        if (newBrowserSettings.isRecursiveEnabled() != oldBrowserSettings.isRecursiveEnabled()) {
            folderChanged = true;
            onRecursiveFilesChanged();
        }
        if (newBrowserSettings.isShowingDownloads() != oldBrowserSettings.isShowingDownloads()) {
            folderChanged = true;
            onIncludeDownloadsChanged();
        }
        if (newBrowserSettings.isShowingMissingFiles() != oldBrowserSettings.isShowingMissingFiles()) {
            folderChanged = true;
            onShowMissingChanged();
        }
        if (folderChanged) {
            onRootFolderChanged(true);
        }

        if (newBrowserSettings.getSort() != oldBrowserSettings.getSort()) {
            onSortChanged();
        }
        if (!BrowserSettings.equals(newBrowserSettings.getGameSeriesFilter(), oldBrowserSettings.getGameSeriesFilter())) {
            onFilterGameSeriesChanged();
        }
        if (!BrowserSettings.equals(newBrowserSettings.getCharacterFilter(), oldBrowserSettings.getCharacterFilter())) {
            onFilterCharacterChanged();
        }
        if (!BrowserSettings.equals(newBrowserSettings.getAmiiboSeriesFilter(), oldBrowserSettings.getAmiiboSeriesFilter())) {
            onFilterAmiiboSeriesChanged();
        }
        if (!BrowserSettings.equals(newBrowserSettings.getAmiiboTypeFilter(), oldBrowserSettings.getAmiiboTypeFilter())) {
            onFilterAmiiboTypeChanged();
        }
        if (newBrowserSettings.getAmiiboView() != oldBrowserSettings.getAmiiboView()) {
            onViewChanged();
        }
        if (!BrowserSettings.equals(newBrowserSettings.getAmiiboFiles(), oldBrowserSettings.getAmiiboFiles())) {
            onAmiiboFilesChanged();
        }

        TagMo.getPrefs().edit()
                .browserRootFolder().put(Storage.getRelativePath(newBrowserSettings.getBrowserRootFolder()))
                .query().put(newBrowserSettings.getQuery())
                .sort().put(newBrowserSettings.getSort())
                .filterGameSeries().put(newBrowserSettings.getGameSeriesFilter())
                .filterCharacter().put(newBrowserSettings.getCharacterFilter())
                .filterAmiiboSeries().put(newBrowserSettings.getAmiiboSeriesFilter())
                .filterAmiiboType().put(newBrowserSettings.getAmiiboTypeFilter())
                .browserAmiiboView().put(newBrowserSettings.getAmiiboView())
                .imageNetworkSetting().put(newBrowserSettings.getImageNetworkSettings())
                .recursiveFolders().put(newBrowserSettings.isRecursiveEnabled())
                .includeDownloads().put(newBrowserSettings.isShowingDownloads())
                .showMissingFiles().put(newBrowserSettings.isShowingMissingFiles())
                .apply();
    }

    private void onAmiiboFilesChanged() {
        if (settings.getAmiiboFiles() == null || settings.getAmiiboFiles().size() == 0) {
            emptyText.setVisibility(View.VISIBLE);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        } else {
            emptyText.setVisibility(View.GONE);
        }
    }

    void onSortChanged() {
        if (menuSortId == null)
            return;

        int sort = settings.getSort();
        if (sort == SORT_ID) {
            menuSortId.setChecked(true);
        } else if (sort == SORT_NAME) {
            menuSortName.setChecked(true);
        } else if (sort == SORT_GAME_SERIES) {
            menuSortGameSeries.setChecked(true);
        } else if (sort == SORT_CHARACTER) {
            menuSortCharacter.setChecked(true);
        } else if (sort == SORT_AMIIBO_SERIES) {
            menuSortAmiiboSeries.setChecked(true);
        } else if (sort == SORT_AMIIBO_TYPE) {
            menuSortAmiiboType.setChecked(true);
        } else if (sort == SORT_FILE_PATH) {
            menuSortFilePath.setChecked(true);
        }
    }

    private int getColumnCount() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            mWindowManager.getDefaultDisplay().getRealMetrics(metrics);
        else
            mWindowManager.getDefaultDisplay().getMetrics(metrics);
        return (int) ((metrics.widthPixels / metrics.density) / 96 + 0.5);
    }

    void onViewChanged() {
        if (menuViewSimple == null)
            return;

        int view = settings.getAmiiboView();
        if (view == VIEW_TYPE_SIMPLE) {
            menuViewSimple.setChecked(true);
        } else if (view == VIEW_TYPE_COMPACT) {
            menuViewCompact.setChecked(true);
        } else if (view == VIEW_TYPE_LARGE) {
            menuViewLarge.setChecked(true);
        } else if (view == VIEW_TYPE_IMAGE) {
            menuViewImage.setChecked(true);
        }
    }

    void onRootFolderChanged(boolean indicator) {
        if (this.settings != null) {
            File rootFolder = this.settings.getBrowserRootFolder();
            if (keyManager.isKeyMissing()) {
                emptyText.setText(R.string.keys_not_loaded);
                showSetupSnackbar();
            } else {
                emptyText.setText(R.string.refreshing_list);
                this.setAmiiboFilesLoadingBarVisibility(indicator);
                this.loadAmiiboFiles(rootFolder, this.settings.isRecursiveEnabled());
            }
            this.loadFolders(rootFolder);
            String relativeRoot = Storage.getRelativePath(rootFolder);
            setFolderText(relativeRoot.length() > 1 ? relativeRoot : rootFolder.getAbsolutePath());
        }
    }

    void onFilterGameSeriesChanged() {
        addFilterItemView(settings.getGameSeriesFilter(),
                "filter_game_series", onFilterGameSeriesChipCloseClick);
    }

    OnCloseClickListener onFilterGameSeriesChipCloseClick = new OnCloseClickListener() {
        @Override
        public void onCloseClick(@NonNull View v) {
            settings.setGameSeriesFilter("");
            settings.notifyChanges();
        }
    };

    void onFilterCharacterChanged() {
        addFilterItemView(settings.getCharacterFilter(), "filter_character", onFilterCharacterChipCloseClick);
    }

    OnCloseClickListener onFilterCharacterChipCloseClick = new OnCloseClickListener() {
        @Override
        public void onCloseClick(@NonNull View v) {
            settings.setCharacterFilter("");
            settings.notifyChanges();
        }
    };

    void onFilterAmiiboSeriesChanged() {
        addFilterItemView(settings.getAmiiboSeriesFilter(), "filter_amiibo_series", onFilterAmiiboSeriesChipCloseClick);
    }

    OnCloseClickListener onFilterAmiiboSeriesChipCloseClick = new OnCloseClickListener() {
        @Override
        public void onCloseClick(@NonNull View v) {
            settings.setAmiiboSeriesFilter("");
            settings.notifyChanges();
        }
    };

    void onFilterAmiiboTypeChanged() {
        addFilterItemView(settings.getAmiiboTypeFilter(), "filter_amiibo_type", onAmiiboTypeChipCloseClick);
    }

    void onRecursiveFilesChanged() {
        if (menuRecursiveFiles == null)
            return;

        menuRecursiveFiles.setChecked(settings.isRecursiveEnabled());
    }

    void onIncludeDownloadsChanged() {
        if (menuIncludeDownloads == null)
            return;

        menuIncludeDownloads.setChecked(settings.isShowingDownloads());
    }

    void onShowMissingChanged() {
        if (menuShowMissing == null)
            return;

        menuShowMissing.setChecked(settings.isShowingMissingFiles());
    }

    void onEnableScaleChanged() {
        if (menuEnableScale == null)
            return;

        menuEnableScale.setChecked(TagMo.getPrefs().enableScaling().get());
    }

    OnCloseClickListener onAmiiboTypeChipCloseClick = new OnCloseClickListener() {
        @Override
        public void onCloseClick(@NonNull View v) {
            settings.setAmiiboTypeFilter("");
            settings.notifyChanges();
        }
    };

    @SuppressLint("InflateParams")
    public void addFilterItemView(String text, String tag, OnCloseClickListener listener) {
        FrameLayout chipContainer = chipList.findViewWithTag(tag);
        chipList.removeView(chipContainer);
        if (!text.isEmpty()) {
            chipContainer = (FrameLayout) getLayoutInflater().inflate(R.layout.chip_view, null);
            chipContainer.setTag(tag);
            Chip chip = chipContainer.findViewById(R.id.chip);
            chip.setText(text);
            chip.setClosable(true);
            chip.setOnCloseClickListener(listener);
            chipList.addView(chipContainer);
            chipList.setVisibility(View.VISIBLE);
        } else if (chipList.getChildCount() == 0) {
            chipList.setVisibility(View.GONE);
        }
    }

    private void setFolderText(String text) {
        this.currentFolderView.setGravity(Gravity.NO_GRAVITY);
        this.currentFolderView.setText(text);
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> {
            int size = settings.getAmiiboFiles().size();
            if (size <= 0) return;
            this.currentFolderView.setGravity(Gravity.CENTER);
            if (settings.getAmiiboManager() != null) {
                int count = 0;
                for (Amiibo amiibo : settings.getAmiiboManager().amiibos.values()) {
                    for (AmiiboFile amiiboFile : settings.getAmiiboFiles()) {
                        if (amiibo.id == amiiboFile.getId()) {
                            count += 1;
                            break;
                        }
                    }
                }
                currentFolderView.setText(getString(R.string.amiibo_collected, size, count,
                        settings.getAmiiboManager().amiibos.values().size()));
            } else {
                currentFolderView.setText(getString(R.string.files_displayed, size));
            }
        }, 5000);

    }

    @UiThread
    public void showToast(int msgRes) {
        Toast.makeText(this, msgRes, Toast.LENGTH_LONG).show();
    }

    @UiThread
    public void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    @UiThread
    public void showStorageSnackbar() {
        Snackbar snackbar = new IconifiedSnackbar(this).buildSnackbar(
                getString(R.string.emulated_storage), Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.enable, v -> {
            TagMo.getPrefs().ignoreSdcard().put(true);
            this.settings.setBrowserRootFolder(new File(Storage.getFile(),
                    TagMo.getPrefs().browserRootFolder().get()));
            this.settings.notifyChanges();
            this.onRootFolderChanged(true);
        });
        snackbar.show();
    }

    @UiThread
    public void showSetupSnackbar() {
        Snackbar snackbar = new IconifiedSnackbar(this).buildSnackbar(
                getString(
                R.string.config_required), Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(R.string.setup, v -> {
            openSettings();
            snackbar.dismiss();
        });
        snackbar.show();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    void installUpdate(Uri apkUri) throws IOException {
        PackageInstaller installer = getPackageManager().getPackageInstaller();
        ContentResolver resolver = getContentResolver();
        InputStream apkStream = resolver.openInputStream(apkUri);
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int sessionId = installer.createSession(params);
        PackageInstaller.Session session = installer.openSession(sessionId);
        DocumentFile document = DocumentFile.fromSingleUri(this, apkUri);
        if (document == null)
            throw new IOException(getString(R.string.fail_invalid_size));
        OutputStream sessionStream = session.openWrite("NAME", 0, document.length());
        byte[] buf = new byte[8192];
        int size;
        while ((size = apkStream.read(buf)) > 0) {
            sessionStream.write(buf, 0, size);
        }
        session.fsync(sessionStream);
        apkStream.close();
        sessionStream.close();
        PendingIntent pi = PendingIntent.getBroadcast(this, 8675309,
                new Intent(this, InstallReceiver.class),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT);
        session.commit(pi.getIntentSender());
    }

    @Background
    void downloadUpdate(String apkUrl) {
        File apk = new File(getFilesDir(), apkUrl.substring(apkUrl.lastIndexOf('/') + 1));
        try {
            URL u = new URL(apkUrl);
            InputStream is = u.openStream();

            DataInputStream dis = new DataInputStream(is);

            byte[] buffer = new byte[1024];
            int length;

            FileOutputStream fos = new FileOutputStream(apk);
            while ((length = dis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
            fos.close();

            if (!apk.getName().toLowerCase(Locale.ROOT).endsWith(".apk")) {
                //noinspection ResultOfMethodCallIgnored
                apk.delete();
                showToast(R.string.download_corrupt);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                installUpdate(Storage.getFileUri(apk));
            } else {
                Intent intent = new ActionIntent(Intent.ACTION_INSTALL_PACKAGE);
                intent.setDataAndType(Storage.getFileUri(apk),
                        getString(R.string.mimetype_apk));
                intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
                intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
                intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME,
                        getApplicationInfo().packageName);
                startActivity(intent);
            }
        } catch (MalformedURLException mue) {
            Debug.Error(mue);
        } catch (IOException ioe) {
            Debug.Error(ioe);
        } catch (SecurityException se) {
            Debug.Error(se);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    ActivityResultLauncher<Intent> onRequestInstall = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (getPackageManager().canRequestPackageInstalls())
            downloadUpdate(TagMo.getPrefs().downloadUrl().get());
        TagMo.getPrefs().downloadUrl().remove();
    });

    @UiThread
    public void showInstallSnackbar(String apkUrl) {
        Snackbar snackbar = new IconifiedSnackbar(this).buildSnackbar(
                getString(
                R.string.update_tagmo_apk), Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.install, v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (getPackageManager().canRequestPackageInstalls()) {
                    downloadUpdate(apkUrl);
                } else {
                    TagMo.getPrefs().downloadUrl().put(apkUrl);
                    Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    intent.setData(Uri.parse(String.format("package:%s", getPackageName())));
                    onRequestInstall.launch(intent);
                }
            } else {
                downloadUpdate(apkUrl);
            }
        });
        snackbar.show();
    }

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static final String READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE";
    private static final String WRITE_EXTERNAL_STORAGE = "android.permission.WRITE_EXTERNAL_STORAGE";
    private static final String[] PERMISSIONS_STORAGE = {
            READ_EXTERNAL_STORAGE,
            WRITE_EXTERNAL_STORAGE
    };

    @RequiresApi(api = Build.VERSION_CODES.R)
    ActivityResultLauncher<Intent> onRequestScopedStorage = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (Environment.isExternalStorageManager()) {
            this.onRefresh();
        } else {
            Snackbar storageBar = Snackbar.make(findViewById(R.id.coordinator),
                    R.string.permission_required, Snackbar.LENGTH_LONG);
            storageBar.setAction(R.string.allow, v -> requestScopedStorage());
            storageBar.show();
        }
    });

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        int permission = ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            Snackbar storageBar = Snackbar.make(findViewById(R.id.coordinator),
                    R.string.permission_required, Snackbar.LENGTH_LONG);
            storageBar.setAction(R.string.allow, v -> ActivityCompat.requestPermissions(
                    BrowserActivity.this, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE));
            storageBar.show();
        } else {
            this.onRefresh();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    void requestScopedStorage() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        try {
            intent.setData(Uri.parse(String.format("package:%s", getPackageName())));
        } catch (Exception e) {
            intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
        }
        onRequestScopedStorage.launch(intent);
    }

//    @Override
//    protected void onNewIntent(Intent intent) {
//        super.onNewIntent(intent);
//
//        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())
//                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())
//                || NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
//            Intent reader = new Intent(this, NfcActivity_.class);
//            reader.setAction(intent.getAction());
//            reader.putExtras(intent.getExtras());
//            startActivity(reader);
//        }
//    }

    @Override
    public void onBackPressed() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        } else {
            super.onBackPressed();
        }
    }
}
