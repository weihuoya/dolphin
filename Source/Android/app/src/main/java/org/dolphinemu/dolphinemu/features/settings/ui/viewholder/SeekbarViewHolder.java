package org.dolphinemu.dolphinemu.features.settings.ui.viewholder;

import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import org.dolphinemu.dolphinemu.R;
import org.dolphinemu.dolphinemu.features.settings.model.view.SettingsItem;
import org.dolphinemu.dolphinemu.features.settings.model.view.SliderSetting;
import org.dolphinemu.dolphinemu.features.settings.ui.SettingsAdapter;

public final class SeekbarViewHolder extends SettingViewHolder
{
  private SliderSetting mItem;

  private TextView mName;
  private TextView mValue;
  private SeekBar mSeekBar;

  public SeekbarViewHolder(View itemView, SettingsAdapter adapter)
  {
    super(itemView, adapter);
  }

  @Override
  protected void findViews(View root)
  {
    mName = root.findViewById(R.id.text_setting_name);
    mValue = root.findViewById(R.id.text_setting_value);
    mSeekBar = root.findViewById(R.id.seekbar);
  }

  @Override
  public void bind(SettingsItem item)
  {
    mItem = (SliderSetting) item;
    mName.setText(item.getNameId());
    mValue.setText(mItem.getSelectedValue() + mItem.getUnits());
    mSeekBar.setMax(mItem.getMax());
    mSeekBar.setProgress(mItem.getSelectedValue());
    mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
    {
      @Override
      public void onProgressChanged(SeekBar seekBar, int progress, boolean b)
      {
        mValue.setText(progress + mItem.getUnits());
        mItem.setSelectedValue(progress);
        getAdapter().onSeekbarClick(mItem, getAdapterPosition(), progress);
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekBar)
      {

      }

      @Override
      public void onStopTrackingTouch(SeekBar seekBar)
      {

      }
    });
  }

  @Override
  public void onClick(View clicked)
  {

  }
}
