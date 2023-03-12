package org.dolphinemu.dolphinemu.utils;

import java.io.File;

import android.util.Log;
import android.content.Context;
import android.os.Environment;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;

import org.dolphinemu.dolphinemu.R;
import org.dolphinemu.dolphinemu.model.UpdaterData;
import org.dolphinemu.dolphinemu.dialogs.UpdaterDialog;
import org.dolphinemu.dolphinemu.features.settings.model.BooleanSetting;
import org.dolphinemu.dolphinemu.features.settings.model.StringSetting;
import org.dolphinemu.dolphinemu.features.settings.model.Settings;

public class UpdaterUtils
{
  public static final String URL =
          "https://api.github.com/repos/Gamer64ytb/dolphin/releases"; // for old release check.
  public static final String LATEST = "/latest"; // for latest release check.

  public static void openUpdaterWindow(Context context, UpdaterData data)
  {
    FragmentManager fm = ((FragmentActivity) context).getSupportFragmentManager();
    UpdaterDialog updaterDialog = UpdaterDialog.newInstance(data);
    updaterDialog.show(fm, "fragment_updater");
  }

  // search updates if the user have it enabled.
  public static void checkUpdatesInit(Context context)
  {
    new AfterDirectoryInitializationRunner().run(context, false, () ->
    {
      cleanDownloadFolder(context);

      if (!BooleanSetting.UPDATER_PERMISSION_ASKED.getBooleanGlobal())
      {
        showPermissionDialog(context);
      }

      if (BooleanSetting.UPDATER_CHECK_AT_STARTUP.getBooleanGlobal())
      {
        checkUpdates(context);
      }
    });
  }

  private static void checkUpdates(Context context)
  {
    makeDataRequest(new LoadCallback<UpdaterData>()
    {
      @Override
      public void onLoad(UpdaterData data)
      {
        VersionCode version = getBuildVersion();
        if (!StringSetting.UPDATER_SKIPPED_VERSION.getStringGlobal()
                .equals(data.version.toString()) &&
                version.compareTo(data.version) < 0)
        {
          showUpdateMessage(context, data);
        }
      }

      @Override
      public void onLoadError()
      {
        // ignore
      }
    });
  }

  // new update dialog
  private static void showUpdateMessage(Context context, UpdaterData data)
  {
    new AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.updates_alert))
            .setMessage(context.getString(R.string.updater_alert_body))
            .setPositiveButton(R.string.yes, (dialogInterface, i) ->
                    openUpdaterWindow(context, data))
            .setNegativeButton(R.string.skip_version, (dialogInterface, i) ->
                    setSkipVersion(data.version.toString()))
            .setNeutralButton(R.string.not_now,
                    ((dialogInterface, i) -> dialogInterface.dismiss()))
            .show();
  }

  // ask to the user on first boot if he wants check updates on startup.
  private static void showPermissionDialog(Context context)
  {
    new AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.updater_check_startup))
            .setMessage(context.getString(R.string.updater_check_startup_description))
            .setPositiveButton(R.string.yes, (dialogInterface, i) ->
                    setPrefs(true))
            .setNegativeButton(R.string.no, (dialogInterface, i) ->
                    setPrefs(false))
            .setOnDismissListener(dialog -> checkUpdatesInit(context))
            .show();
  }

  private static void setPrefs(boolean enabled)
  {
    try (Settings settings = new Settings())
    {
      settings.loadSettings();

      BooleanSetting.UPDATER_CHECK_AT_STARTUP.setBoolean(settings, enabled);
      BooleanSetting.UPDATER_PERMISSION_ASKED.setBoolean(settings, true);

      // Context is set to null to avoid toasts
      settings.saveSettings(null, null);
    }
  }

  private static void setSkipVersion(String version)
  {
    try (Settings settings = new Settings())
    {
      settings.loadSettings();

      StringSetting.UPDATER_SKIPPED_VERSION.setString(settings, version);

      // Context is set to null to avoid toasts
      settings.saveSettings(null, null);
    }
  }

  // get the update info
  public static void makeDataRequest(LoadCallback<UpdaterData> listener)
  {
    JsonObjectRequest jsonRequest = new JsonObjectRequest(Request.Method.GET, URL + LATEST, null,
            response ->
            {
              try // first, try access to github
                  // and get the updates, network is needed.
              {
                UpdaterData data = new UpdaterData(response);
                listener.onLoad(data);
              }
              catch (Exception e) // if fails (because build not detected
                                  // or no network connection) show a error.
              {
                Log.e(UpdaterUtils.class.getSimpleName(), e.toString());
                listener.onLoadError();
              }
            },
            error -> listener.onLoadError());
    VolleyUtil.getQueue().add(jsonRequest);
  }

  // get the changelog info
  public static void makeChangelogRequest(String format, LoadCallback<String> listener)
  {
    JsonArrayRequest jsonRequest = new JsonArrayRequest(Request.Method.GET, URL, null,
            response ->
            {
              try // first, try access to github
                  // and get the text, network is needed.
              {
                StringBuilder changelog = new StringBuilder();

                for (int i = 0; i < response.length(); i++)
                {
                  changelog.append(String.format(format,
                          response.getJSONObject(i).getString("tag_name"),
                          response.getJSONObject(i).getString("published_at").substring(0, 10),
                          response.getJSONObject(i).getString("body")));
                }
                changelog.setLength(Math.max(changelog.length() - 1, 0));
                listener.onLoad(changelog.toString());
              }
              catch (Exception e) // if fails (because page not founded
                                  // or no network connection) show a error.
              {
                Log.e(UpdaterUtils.class.getSimpleName(), e.toString());
                listener.onLoadError();
              }
            },
            error -> listener.onLoadError());
    VolleyUtil.getQueue().add(jsonRequest); // request finished successfully
  }

  // clean downloaded apk of updater
  public static void cleanDownloadFolder(Context context)
  {
    File[] files = getDownloadFolder(context).listFiles();
    if (files != null)
    {
      for (File file : files)
        file.delete();
    }
  }

  public static File getDownloadFolder(Context context)
  {
    return context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
  }

  public static VersionCode getBuildVersion()
  {
    return VersionCode.create("5.0-15960"); // get version
  }
}
