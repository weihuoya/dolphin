package org.dolphinemu.dolphinemu.dialogs;

import java.io.File;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.DialogFragment;

import org.dolphinemu.dolphinemu.R;
import org.dolphinemu.dolphinemu.model.UpdaterData;
import org.dolphinemu.dolphinemu.utils.DownloadCallback;
import org.dolphinemu.dolphinemu.utils.DownloadUtils;
import org.dolphinemu.dolphinemu.utils.LoadCallback;
import org.dolphinemu.dolphinemu.utils.UpdaterUtils;
import org.dolphinemu.dolphinemu.utils.VersionCode;

public final class UpdaterDialog extends DialogFragment implements LoadCallback<UpdaterData>,
        DownloadCallback
{
  private static final String DATA = "updaterData";

  private View updaterBody, changelogBody;
  private Button downloadButton, changelogButton;
  private ProgressBar loadingBar, downloadProgressBar, changelogProgressBar;
  private TextView updaterMessage, errorText, versionText, downloadSize, changelogText,
          changelogErrorText;
  private ImageView changelogArrow;

  private UpdaterData mData;
  private DownloadUtils mDownload;

  private Animation rotateDown;
  private Animation rotateUp;

  private final VersionCode mBuildVersion = UpdaterUtils.getBuildVersion();
  private boolean isChangelogOpen = false;

  public static UpdaterDialog newInstance(UpdaterData data)
  {
    UpdaterDialog fragment = new UpdaterDialog();

    if (data != null)
    {
      Bundle arguments = new Bundle();
      arguments.putParcelable(DATA, data);
      fragment.setArguments(arguments);
    }

    return fragment;
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState)
  {
    AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
    ViewGroup viewGroup = (ViewGroup) getActivity().getLayoutInflater()
            .inflate(R.layout.dialog_updater, null);

    TextView textCurrent = viewGroup.findViewById(R.id.text_current_version);
    textCurrent.setText(getString(R.string.current_version, mBuildVersion));

    loadingBar = viewGroup.findViewById(R.id.updater_loading);
    updaterBody = viewGroup.findViewById(R.id.updater_body);
    updaterMessage = viewGroup.findViewById(R.id.text_updater_message);
    errorText = viewGroup.findViewById(R.id.updater_error);
    versionText = viewGroup.findViewById(R.id.text_version);
    downloadButton = viewGroup.findViewById(R.id.button_download);
    downloadSize = viewGroup.findViewById(R.id.text_download_size);
    downloadProgressBar = viewGroup.findViewById(R.id.progressbar_download);
    changelogButton = viewGroup.findViewById(R.id.button_view_changelog);
    changelogProgressBar = viewGroup.findViewById(R.id.changelog_loading);
    changelogText = viewGroup.findViewById(R.id.changelog_text);
    changelogBody = viewGroup.findViewById(R.id.changelog_body);
    changelogErrorText = viewGroup.findViewById(R.id.changelog_error);
    changelogArrow = viewGroup.findViewById(R.id.changelog_arrow);

    if (getArguments() != null) // Assuming valid data is passed!
    {
      onLoad(getArguments().getParcelable(DATA));
    }
    else
    {
      UpdaterUtils.makeDataRequest(this);
    }

    mDownload = new DownloadUtils(new Handler(Looper.getMainLooper()),
            this, UpdaterUtils.getDownloadFolder(getContext()));
    initAnimations();

    builder.setView(viewGroup);
    return builder.create();
  }

  @Override
  public void onDestroy()
  {
    super.onDestroy();
    mDownload.cancel();
    UpdaterUtils.cleanDownloadFolder(getContext());
  }

  @Override
  public void onLoad(UpdaterData data)
  {
    mData = data;

    versionText.setText(getString(R.string.version_description, mData.version.toString()));
    downloadSize.setText(getString(R.string.download_size, mData.size));
    changelogButton.setOnClickListener(this::onChangelogClick);

    int result = mBuildVersion.compareTo(mData.version);
    if (result >= 0)
    {
      updaterMessage.setText(R.string.updater_uptodate);
      updaterMessage.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
      downloadButton.setText(null);
      downloadButton.setEnabled(false);
    }
    else
    {
      updaterMessage.setText(R.string.updater_newavailable);
      updaterMessage.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
      downloadButton.setOnClickListener(this::onDownloadClick);
    }

    loadingBar.setVisibility(View.GONE);
    updaterBody.setVisibility(View.VISIBLE);
  }

  @Override
  public void onLoadError()
  {
    loadingBar.setVisibility(View.GONE);
    errorText.setVisibility(View.VISIBLE);
  }

  public void onDownloadClick(View view)
  {
    if (mDownload.isRunning())
    {
      mDownload.cancel();
    }
    else
    {
      mDownload.setUrl(mData.downloadUrl);
      mDownload.start();
    }
  }

  @Override
  public void onDownloadStart()
  {
    downloadProgressBar.setProgress(0);
    downloadButton.setActivated(true);
    downloadButton.setText(R.string.cancel);
  }

  @Override
  public void onDownloadProgress(int progress)
  {
    downloadProgressBar.setProgress(progress);
  }

  @Override
  public void onDownloadComplete(File downloadFile)
  {
    downloadButton.setText(R.string.button_install);
    onDownloadStop();

    try
    {
      Uri fileUri = FileProvider.getUriForFile(getContext(),
              getContext().getApplicationContext().getPackageName() + ".filesprovider",
              downloadFile);

      Intent promptInstall = new Intent(Intent.ACTION_VIEW);
      promptInstall.setData(fileUri);
      promptInstall.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      startActivity(promptInstall);
    }
    catch (Exception e)
    {
      Log.e(getClass().getSimpleName(), e.toString());
      Toast.makeText(getContext(), "Installation failed", Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  public void onDownloadCancelled()
  {
    downloadButton.setText(R.string.button_download);
    onDownloadStop();
  }

  @Override
  public void onDownloadError()
  {
    downloadButton.setText(R.string.error);
    onDownloadStop();
  }

  private void onDownloadStop()
  {
    downloadButton.setActivated(false);
  }

  private void onChangelogClick(View view)
  {
    if (!isChangelogOpen)
    {
      changelogProgressBar.setVisibility(View.VISIBLE);

      UpdaterUtils.makeChangelogRequest(getString(R.string.changelog_section),
              new LoadCallback<String>()
              {
                @Override
                public void onLoad(String data)
                {
                  changelogProgressBar.setVisibility(View.GONE);
                  changelogText.setText(data);
                  changelogArrow.startAnimation(rotateDown);
                  changelogBody.setVisibility(View.VISIBLE);
                  isChangelogOpen = true;
                }

                @Override
                public void onLoadError()
                {
                  changelogProgressBar.setVisibility(View.GONE);
                  changelogErrorText.setVisibility(View.VISIBLE);
                  new Handler()
                          .postDelayed(() -> opacityOut(changelogErrorText, View.INVISIBLE), 1750);
                }
              });
    }
    else
    {
      isChangelogOpen = false;
      changelogArrow.startAnimation(rotateUp);
      changelogBody.setVisibility(View.GONE);
    }
  }

  // UI animations stuff
  private void initAnimations()
  {
    rotateDown = new RotateAnimation(0.0f, -180.0f,
            Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
            0.5f);
    rotateDown.setRepeatCount(0);
    rotateDown.setDuration(200);
    rotateDown.setFillAfter(true);

    rotateUp = new RotateAnimation(-180.0f, 0.0f,
            Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
            0.5f);
    rotateUp.setRepeatCount(0);
    rotateUp.setDuration(200);
    rotateUp.setFillAfter(true);
  }

  private void opacityOut(View view, int endVisibility)
  {
    view.animate()
            .alpha(0.0f)
            .setListener(new AnimatorListenerAdapter()
            {
              @Override
              public void onAnimationEnd(Animator animation)
              {
                view.setVisibility(endVisibility);
                view.setAlpha(1.0f);
                view.animate().setListener(null);
              }
            });
  }
}