package org.dolphinemu.dolphinemu.features.settings.ui.viewholder;

import java.util.Locale;

import android.content.res.Resources;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.dolphinemu.dolphinemu.R;
import org.dolphinemu.dolphinemu.features.settings.model.view.SettingsItem;
import org.dolphinemu.dolphinemu.features.settings.model.view.SliderSelectorSetting;
import org.dolphinemu.dolphinemu.features.settings.ui.SettingsAdapter;

public class SliderSelectorViewHolder extends SettingViewHolder
        implements SeekBar.OnSeekBarChangeListener
{
  private SliderSelectorSetting mItem;

  private TextView mTextSettingName;
  private TextView mTextSettingDescription;
  private SeekBar mSeekbar;
  private TextView mTextSliderValue;
  private TextView mUnits;
  private int mSeekbarMinValue;
  private int mSeekbarProgress;

  private String[] mChoices;
  private int[] mValues;

  private boolean compactMode;
  private float modifier;

  public SliderSelectorViewHolder(View itemView, SettingsAdapter adapter)
  {
    super(itemView, adapter);
  }

  @Override
  protected void findViews(View root)
  {
    mTextSettingName = root.findViewById(R.id.text_setting_name);
    mTextSettingDescription = root.findViewById(R.id.text_setting_description);
    mSeekbar = root.findViewById(R.id.seekbar);
    mTextSliderValue = root.findViewById(R.id.text_value);
    mUnits = root.findViewById(R.id.text_units);
  }

  @Override
  public void bind(SettingsItem item)
  {
    mItem = (SliderSelectorSetting) item;
    compactMode = mItem.getChoicesId() == 0;
    modifier = mItem.getModifier();

    mTextSettingName.setText(mItem.getName());

    int currentValue = mItem.getSelectedValue(getAdapter().getSettings());
    Resources resMgr = mTextSettingName.getContext().getResources();
    if (!compactMode)
      mChoices = resMgr.getStringArray(mItem.getChoicesId());
    mValues = resMgr.getIntArray(mItem.getValuesId());
    for (int i = 0; i < mValues.length; ++i)
    {
      if (mValues[i] == currentValue)
      {
        mSeekbarProgress = i;
        break;
      }
    }

    if (compactMode)
    {
      mTextSliderValue.setText(format(currentValue * modifier));
      mUnits.setText(mItem.getUnits());
      mTextSettingDescription.setVisibility(View.GONE);
      mTextSliderValue.setVisibility(View.VISIBLE);
      mUnits.setVisibility(View.VISIBLE);
    }
    else
    {
      mTextSettingDescription.setText(mChoices[mSeekbarProgress]);
      mTextSettingDescription.setVisibility(View.VISIBLE);
      mTextSliderValue.setVisibility(View.GONE);
      mUnits.setVisibility(View.GONE);
    }

    // TODO: Once we require API 26, uncomment this line and remove the mSeekbarMinValue variable
    //mSeekbar.setMin(mItem.getMin());
    mSeekbarMinValue = mItem.getMin();

    mSeekbar.setMax(mItem.getMax() - mSeekbarMinValue);
    mSeekbar.setProgress(mSeekbarProgress - mSeekbarMinValue);

    if (mItem.isEditable())
      mSeekbar.setOnSeekBarChangeListener(this);

    setStyle(mTextSettingName, mItem);
  }

  @Override
  public void onClick(View clicked)
  {
    if (!mItem.isEditable())
    {
      showNotRuntimeEditableError();
      return;
    }

    //getAdapter().onSliderClick(mItem, getAdapterPosition());

    setStyle(mTextSettingName, mItem);
  }

  @Nullable
  @Override
  protected SettingsItem getItem()
  {
    return mItem;
  }

  @Override
  public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
  {
    mSeekbarProgress = progress + mSeekbarMinValue;
    if (compactMode)
      mTextSliderValue.setText(format(mValues[progress] * modifier));
    else
      mTextSettingDescription.setText(mChoices[progress]);
  }

  @Override
  public void onStartTrackingTouch(SeekBar seekBar)
  {
  }

  @Override
  public void onStopTrackingTouch(SeekBar seekBar)
  {
    if (mItem.getSelectedValue(getAdapter().getSettings()) != mValues[mSeekbarProgress])
      getAdapter().getView().onSettingChanged();

    mItem.setSelectedValue(getAdapter().getSettings(), mValues[mSeekbarProgress]);
  }

  private String format(float value)
  {
    if (modifier % 1 == 0)
      return String.valueOf((int) value);
    else
      return String.format(Locale.ENGLISH, "%.1f", value);
  }
}