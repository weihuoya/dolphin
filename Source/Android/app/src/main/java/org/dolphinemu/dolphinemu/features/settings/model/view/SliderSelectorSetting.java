package org.dolphinemu.dolphinemu.features.settings.model.view;

import android.content.Context;

import org.dolphinemu.dolphinemu.DolphinApplication;
import org.dolphinemu.dolphinemu.features.settings.model.AbstractIntSetting;
import org.dolphinemu.dolphinemu.features.settings.model.AbstractSetting;
import org.dolphinemu.dolphinemu.features.settings.model.Settings;

public final class SliderSelectorSetting extends SliderSetting
{

  private AbstractIntSetting mSetting;
  private int mChoicesId;
  private int mValuesId;
  private float mModifier;

  public SliderSelectorSetting(Context context, AbstractIntSetting setting, int titleId,
          int descriptionId,
          int choicesId, int valuesId, String units, float modifier)
  {
    super(context, titleId, descriptionId, 0, getResArrayLength(valuesId), units);
    mSetting = setting;
    mValuesId = valuesId;
    mChoicesId = choicesId;
    mModifier = modifier;
  }

  public SliderSelectorSetting(Context context, AbstractIntSetting setting, int titleId,
          int descriptionId,
          int choicesId, int valuesId, String units)
  {
    this(context, setting, titleId, descriptionId, choicesId, valuesId, units, 1);
  }

  private static int getResArrayLength(int arrayId)
  {
    return DolphinApplication.getAppContext().getResources().getIntArray(arrayId).length - 1;
  }

  public int getChoicesId()
  {
    return mChoicesId;
  }

  public int getValuesId()
  {
    return mValuesId;
  }

  public float getModifier()
  {
    return mModifier;
  }

  public int getSelectedValue(Settings settings)
  {
    return mSetting.getInt(settings);
  }

  public void setSelectedValue(Settings settings, int selection)
  {
    mSetting.setInt(settings, selection);
  }

  @Override
  public int getType()
  {
    return TYPE_SLIDER_SELECTOR;
  }

  @Override
  public AbstractSetting getSetting()
  {
    return mSetting;
  }
}

