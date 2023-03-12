package org.dolphinemu.dolphinemu.utils;

import java.io.File;

import androidx.annotation.Keep;

public interface DownloadCallback
{
  @Keep
  default void onDownloadStart()
  {
  }

  @Keep
  default void onDownloadProgress(int progress)
  {
  }

  @Keep
  void onDownloadComplete(File file);

  @Keep
  default void onDownloadCancelled()
  {
  }

  @Keep
  void onDownloadError();
}