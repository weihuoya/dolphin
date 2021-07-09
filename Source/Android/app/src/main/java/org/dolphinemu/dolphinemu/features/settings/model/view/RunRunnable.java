// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.settings.model.view;

import android.content.Context;

import org.dolphinemu.dolphinemu.features.settings.model.AbstractSetting;

public final class RunRunnable extends SettingsItem
{
  private final int mAlertText;
  private final int mToastTextAfterRun;
  private final Runnable mRunnable;

  public RunRunnable(Context context, int titleId, int descriptionId, int alertText,
          int toastTextAfterRun, Runnable runnable)
  {
    super(context, titleId, descriptionId);
    mAlertText = alertText;
    mToastTextAfterRun = toastTextAfterRun;
    mRunnable = runnable;
  }

  public int getAlertText()
  {
    return mAlertText;
  }

  public int getToastTextAfterRun()
  {
    return mToastTextAfterRun;
  }

  public Runnable getRunnable()
  {
    return mRunnable;
  }

  @Override
  public int getType()
  {
    return TYPE_RUN_RUNNABLE;
  }

  @Override
  public AbstractSetting getSetting()
  {
    return null;
  }
}
