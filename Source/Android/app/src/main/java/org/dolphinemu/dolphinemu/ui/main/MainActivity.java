// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.ui.main;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.nononsenseapps.filepicker.DividerItemDecoration;

import org.dolphinemu.dolphinemu.R;
import org.dolphinemu.dolphinemu.activities.EmulationActivity;
import org.dolphinemu.dolphinemu.adapters.GameAdapter;
import org.dolphinemu.dolphinemu.features.settings.model.BooleanSetting;
import org.dolphinemu.dolphinemu.features.settings.model.NativeConfig;
import org.dolphinemu.dolphinemu.features.settings.ui.MenuTag;
import org.dolphinemu.dolphinemu.features.settings.ui.SettingsActivity;
import org.dolphinemu.dolphinemu.model.GameFileCache;
import org.dolphinemu.dolphinemu.services.GameFileCacheManager;
import org.dolphinemu.dolphinemu.utils.AfterDirectoryInitializationRunner;
import org.dolphinemu.dolphinemu.utils.BooleanSupplier;
import org.dolphinemu.dolphinemu.utils.CompletableFuture;
import org.dolphinemu.dolphinemu.utils.ContentHandler;
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization;
import org.dolphinemu.dolphinemu.utils.FileBrowserHelper;
import org.dolphinemu.dolphinemu.utils.PermissionsHandler;
import org.dolphinemu.dolphinemu.utils.StartupHandler;
import org.dolphinemu.dolphinemu.BuildConfig;
import org.dolphinemu.dolphinemu.utils.WiiUtils;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

public final class MainActivity extends AppCompatActivity
{
  public static final int REQUEST_DIRECTORY = 1;
  public static final int REQUEST_GAME_FILE = 2;
  public static final int REQUEST_SD_FILE = 3;
  public static final int REQUEST_WAD_FILE = 4;
  public static final int REQUEST_WII_SAVE_FILE = 5;
  public static final int REQUEST_NAND_BIN_FILE = 6;

  private static final String PREF_GAMELIST = "GAME_LIST_TYPE";

  private DividerItemDecoration mDivider;
  private GameAdapter mAdapter;
  private RecyclerView mGameList;
  private Toolbar mToolbar;
  private SwipeRefreshLayout mSwipeRefreshLayout;

  // Library
  private String mDirToAdd;
  private static boolean sShouldRescanLibrary = true;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    findViews();

    setTitle(getString(R.string.app_name_version));

    setSupportActionBar(mToolbar);

    //String versionName = BuildConfig.VERSION_NAME;
    //setVersionString(versionName);

    GameFileCacheManager.getGameFiles().observe(this, (gameFiles) -> showGames());

    Observer<Boolean> refreshObserver = (isLoading) ->
    {
      mSwipeRefreshLayout.setRefreshing(GameFileCacheManager.isLoadingOrRescanning());
    };
    GameFileCacheManager.isLoading().observe(this, refreshObserver);
    GameFileCacheManager.isRescanning().observe(this, refreshObserver);

    // Toolbar options
    mToolbar.setOnMenuItemClickListener(menuItem ->
    {
      switch (menuItem.getItemId())
      {
        case R.id.menu_add_directory:
          launchFileListActivity();
          return true;

        case R.id.menu_toggle_gamelist:
          toggleGameList();
          return true;

        case R.id.menu_settings:
          launchSettingsActivity(MenuTag.SETTINGS);
          return true;

        case R.id.menu_refresh:
          mSwipeRefreshLayout.setRefreshing(true);
          GameFileCacheManager.startRescan(this);
          return true;

        case R.id.menu_open_file:
          launchOpenFileActivity(REQUEST_GAME_FILE);
          return true;

        case R.id.menu_install_wad:
          new AfterDirectoryInitializationRunner().run(this, true,
                  () -> launchOpenFileActivity(REQUEST_WAD_FILE));
          return true;

        case R.id.menu_import_wii_save:
          new AfterDirectoryInitializationRunner().run(this, true,
                  () -> launchOpenFileActivity(REQUEST_WII_SAVE_FILE));
          return true;

        case R.id.menu_import_nand_backup:
          new AfterDirectoryInitializationRunner().run(this, true,
                  () -> launchOpenFileActivity(REQUEST_NAND_BIN_FILE));
          return true;
      }
      return false;
    });

    // Stuff in this block only happens when this activity is newly created (i.e. not a rotation)
    if (savedInstanceState == null)
      StartupHandler.HandleInit(this);

    if (PermissionsHandler.hasWriteAccess(this))
    {
      new AfterDirectoryInitializationRunner()
              .run(this, false, this::startGameFileCacheService);
    }
  }

  @Override
  protected void onResume()
  {
    super.onResume();

    if (DirectoryInitialization.shouldStart(this))
    {
      DirectoryInitialization.start(this);
      new AfterDirectoryInitializationRunner()
              .run(this, false, this::startGameFileCacheService);
    }

    if (mDirToAdd != null)
    {
      GameFileCache.addGameFolder(mDirToAdd);
      mDirToAdd = null;
    }

    if (sShouldRescanLibrary && !GameFileCacheManager.isRescanning().getValue())
    {
      new AfterDirectoryInitializationRunner().run(this, false, () ->
      {
        GameFileCacheManager.startRescan(this);
      });
    }

    sShouldRescanLibrary = true;

    // In case the user changed a setting that affects how games are displayed,
    // such as system language, cover downloading...
    mAdapter.refetchMetadata();
  }

  @Override
  protected void onDestroy()
  {
    super.onDestroy();
  }

  @Override
  protected void onStart()
  {
    super.onStart();
    StartupHandler.checkSessionReset(this);
  }

  @Override
  protected void onStop()
  {
    super.onStop();

    if (isChangingConfigurations())
    {
      skipRescanningLibrary();
    }
    else if (DirectoryInitialization.areDolphinDirectoriesReady())
    {
      // If the currently selected platform tab changed, save it to disk
      NativeConfig.save(NativeConfig.LAYER_BASE);
    }

    StartupHandler.setSessionTime(this);
  }

  // TODO: Replace with a ButterKnife injection.
  private void findViews()
  {
    mToolbar = findViewById(R.id.toolbar_main);
    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
    Drawable lineDivider = getDrawable(R.drawable.line_divider);
    mDivider = new DividerItemDecoration(lineDivider);
    mGameList = findViewById(R.id.grid_games);
    mAdapter = new GameAdapter();
    mGameList.setAdapter(mAdapter);
    refreshGameList(pref.getBoolean(PREF_GAMELIST, true));

    mSwipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
    mSwipeRefreshLayout.setColorSchemeResources(R.color.dolphin_purple);
    mSwipeRefreshLayout.setOnRefreshListener(this::setOnSwipeRefreshListener);
  }

  private void setOnSwipeRefreshListener()
  {
    mSwipeRefreshLayout.setRefreshing(true);
    GameFileCacheManager.startRescan(this);
  }

  private void refreshGameList(boolean flag)
  {
    int resourceId;
    int columns = getResources().getInteger(R.integer.game_grid_columns);
    RecyclerView.LayoutManager layoutManager;
    if (flag)
    {
      resourceId = R.layout.card_game;
      layoutManager = new GridLayoutManager(this, columns);
      mGameList.addItemDecoration(mDivider);
    }
    else
    {
      columns = columns * 2 + 1;
      resourceId = R.layout.card_game2;
      layoutManager = new GridLayoutManager(this, columns);
      mGameList.removeItemDecoration(mDivider);
    }
    mAdapter.setResourceId(resourceId);
    mGameList.setLayoutManager(layoutManager);
  }

  public void toggleGameList()
  {
    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
    boolean flag = !pref.getBoolean(PREF_GAMELIST, true);
    pref.edit().putBoolean(PREF_GAMELIST, flag).apply();
    refreshGameList(flag);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu)
  {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.menu_game_grid, menu);
    return true;
  }

  public void setVersionString(String version)
  {
    mToolbar.setSubtitle(version);
  }

  public void launchSettingsActivity(MenuTag menuTag)
  {
    SettingsActivity.launch(this, menuTag);
  }

  public void launchFileListActivity()
  {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
    startActivityForResult(intent, REQUEST_DIRECTORY);
  }

  public void launchOpenFileActivity(int requestCode)
  {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("*/*");
    startActivityForResult(intent, requestCode);
  }

  public void installWAD(String path)
  {
    runOnThreadAndShowResult(R.string.import_in_progress, 0, () ->
    {
      boolean success = WiiUtils.installWAD(path);
      int message = success ? R.string.wad_install_success : R.string.wad_install_failure;
      return getResources().getString(message);
    });
  }

  public void importWiiSave(String path)
  {
    CompletableFuture<Boolean> canOverwriteFuture = new CompletableFuture<>();

    runOnThreadAndShowResult(R.string.import_in_progress, 0, () ->
    {
      BooleanSupplier canOverwrite = () ->
      {
        runOnUiThread(() ->
        {
          AlertDialog.Builder builder =
                  new AlertDialog.Builder(this, R.style.DolphinDialogBase);
          builder.setMessage(R.string.wii_save_exists);
          builder.setCancelable(false);
          builder.setPositiveButton(R.string.yes, (dialog, i) -> canOverwriteFuture.complete(true));
          builder.setNegativeButton(R.string.no, (dialog, i) -> canOverwriteFuture.complete(false));
          builder.show();
        });

        try
        {
          return canOverwriteFuture.get();
        }
        catch (ExecutionException | InterruptedException e)
        {
          // Shouldn't happen
          throw new RuntimeException(e);
        }
      };

      int result = WiiUtils.importWiiSave(path, canOverwrite);

      int message;
      switch (result)
      {
        case WiiUtils.RESULT_SUCCESS:
          message = R.string.wii_save_import_success;
          break;
        case WiiUtils.RESULT_CORRUPTED_SOURCE:
          message = R.string.wii_save_import_corruped_source;
          break;
        case WiiUtils.RESULT_TITLE_MISSING:
          message = R.string.wii_save_import_title_missing;
          break;
        case WiiUtils.RESULT_CANCELLED:
          return null;
        default:
          message = R.string.wii_save_import_error;
          break;
      }
      return getResources().getString(message);
    });
  }

  public void importNANDBin(String path)
  {
    AlertDialog.Builder builder =
            new AlertDialog.Builder(this, R.style.DolphinDialogBase);

    builder.setMessage(R.string.nand_import_warning);
    builder.setNegativeButton(R.string.no, (dialog, i) -> dialog.dismiss());
    builder.setPositiveButton(R.string.yes, (dialog, i) ->
    {
      dialog.dismiss();

      runOnThreadAndShowResult(R.string.import_in_progress, R.string.do_not_close_app, () ->
      {
        // ImportNANDBin doesn't provide any result value, unfortunately...
        // It does however show a panic alert if something goes wrong.
        WiiUtils.importNANDBin(path);
        return null;
      });
    });

    builder.show();
  }

  private void runOnThreadAndShowResult(int progressTitle, int progressMessage, Supplier<String> f)
  {
    AlertDialog progressDialog = new AlertDialog.Builder(this, R.style.DolphinDialogBase)
            .create();
    progressDialog.setTitle(progressTitle);
    if (progressMessage != 0)
      progressDialog.setMessage(getResources().getString(progressMessage));
    progressDialog.setCancelable(false);
    progressDialog.show();

    new Thread(() ->
    {
      String result = f.get();
      runOnUiThread(() ->
      {
        progressDialog.dismiss();

        if (result != null)
        {
          AlertDialog.Builder builder =
                  new AlertDialog.Builder(this, R.style.DolphinDialogBase);
          builder.setMessage(result);
          builder.setPositiveButton(R.string.ok, (dialog, i) -> dialog.dismiss());
          builder.show();
        }
      });
    }, getResources().getString(progressTitle)).start();
  }

  public static void skipRescanningLibrary()
  {
    sShouldRescanLibrary = false;
  }

  /**
   * Called when a selection is made using the Storage Access Framework folder picker.
   */
  public void onDirectorySelected(Intent result)
  {
    Uri uri = result.getData();

    boolean recursive = BooleanSetting.MAIN_RECURSIVE_ISO_PATHS.getBooleanGlobal();
    String[] childNames = ContentHandler.getChildNames(uri, recursive);
    if (Arrays.stream(childNames).noneMatch((name) -> FileBrowserHelper.GAME_EXTENSIONS.contains(
            FileBrowserHelper.getExtension(name, false))))
    {
      AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.DolphinDialogBase);
      builder.setMessage(getString(R.string.wrong_file_extension_in_directory,
              FileBrowserHelper.setToSortedDelimitedString(FileBrowserHelper.GAME_EXTENSIONS)));
      builder.setPositiveButton(R.string.ok, null);
      builder.show();
    }

    ContentResolver contentResolver = getContentResolver();
    Uri canonicalizedUri = contentResolver.canonicalize(uri);
    if (canonicalizedUri != null)
      uri = canonicalizedUri;

    int takeFlags = result.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
    getContentResolver().takePersistableUriPermission(uri, takeFlags);

    mDirToAdd = uri.toString();
  }

  /**
   * @param requestCode An int describing whether the Activity that is returning did so successfully.
   * @param resultCode  An int describing what Activity is giving us this callback.
   * @param result      The information the returning Activity is providing us.
   */
  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent result)
  {
    super.onActivityResult(requestCode, resultCode, result);

    // If the user picked a file, as opposed to just backing out.
    if (resultCode == RESULT_OK)
    {
      Uri uri = result.getData();
      switch (requestCode)
      {
        case REQUEST_DIRECTORY:
          onDirectorySelected(result);
          break;

        case REQUEST_GAME_FILE:
          FileBrowserHelper.runAfterExtensionCheck(this, uri,
                  FileBrowserHelper.GAME_LIKE_EXTENSIONS,
                  () -> EmulationActivity.launch(this, result.getData().toString(), false));
          break;

        case REQUEST_WAD_FILE:
          FileBrowserHelper.runAfterExtensionCheck(this, uri, FileBrowserHelper.WAD_EXTENSION,
                  () -> installWAD(result.getData().toString()));
          break;

        case REQUEST_WII_SAVE_FILE:
          FileBrowserHelper.runAfterExtensionCheck(this, uri, FileBrowserHelper.BIN_EXTENSION,
                  () -> importWiiSave(result.getData().toString()));
          break;

        case REQUEST_NAND_BIN_FILE:
          FileBrowserHelper.runAfterExtensionCheck(this, uri, FileBrowserHelper.BIN_EXTENSION,
                  () -> importNANDBin(result.getData().toString()));
          break;
      }
    }
    else
    {
      skipRescanningLibrary();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
          @NonNull int[] grantResults)
  {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    if (requestCode == PermissionsHandler.REQUEST_CODE_WRITE_PERMISSION)
    {
      if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
      {
        DirectoryInitialization.start(this);
        new AfterDirectoryInitializationRunner()
                .run(this, false, this::startGameFileCacheService);
      }
      else
      {
        Toast.makeText(this, R.string.write_permission_needed, Toast.LENGTH_LONG).show();
      }
    }
  }

  /**
   * To be called when the game file cache is updated.
   */
  public void showGames()
  {
    if (mAdapter != null)
    {
      mAdapter.swapDataSet(GameFileCacheManager.getAllGameFiles());
    }
  }

  // Don't call this before DirectoryInitialization completes.
  private void startGameFileCacheService()
  {
    showGames();
    GameFileCacheManager.startLoad(this);
  }
}
