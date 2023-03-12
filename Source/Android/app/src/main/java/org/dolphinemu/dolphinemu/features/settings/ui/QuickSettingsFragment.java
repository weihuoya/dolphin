package org.dolphinemu.dolphinemu.features.settings.ui;

import java.util.ArrayList;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.Toast;

import com.nononsenseapps.filepicker.DividerItemDecoration;

import org.dolphinemu.dolphinemu.R;
import org.dolphinemu.dolphinemu.activities.EmulationActivity;
import org.dolphinemu.dolphinemu.features.settings.model.Settings;
import org.dolphinemu.dolphinemu.features.settings.model.view.SliderSelectorSetting;

import org.dolphinemu.dolphinemu.features.settings.model.BooleanSetting;
import org.dolphinemu.dolphinemu.features.settings.model.IntSetting;
import org.dolphinemu.dolphinemu.features.settings.model.view.CheckBoxSetting;
import org.dolphinemu.dolphinemu.features.settings.model.view.InvertedCheckBoxSetting;
import org.dolphinemu.dolphinemu.features.settings.model.view.SettingsItem;

public class QuickSettingsFragment extends Fragment implements SettingsFragmentView
{
  private EmulationActivity mActivity;
  private SettingsAdapter mAdapter;
  private ArrayList<SettingsItem> mSettingsList;

  public static QuickSettingsFragment newInstance()
  {
    return new QuickSettingsFragment();
  }

  @Override
  public void onAttach(@NonNull Context context)
  {
    super.onAttach(context);
    mActivity = (EmulationActivity) context;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

    mAdapter = new SettingsAdapter(this, getContext());
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
          @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.fragment_quick_settings, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    super.onViewCreated(view, savedInstanceState);

    view.findViewById(R.id.open_settings).setOnClickListener(v ->
    {
      SettingsActivity.launch(requireActivity(), MenuTag.SETTINGS);
      mActivity.closeSubmenu();
    });

    RecyclerView recyclerView = view.findViewById(R.id.list_quick_settings);
    recyclerView.setAdapter(mAdapter);

    Drawable lineDivider = getContext().getDrawable(R.drawable.line_divider);
    recyclerView.addItemDecoration(new DividerItemDecoration(lineDivider));

    loadSettingsList();
    showSettingsList(mSettingsList);
  }

  @Override
  public void onDetach()
  {
    super.onDetach();
    mActivity = null;
  }

  @Override
  public void onSettingsFileLoaded(Settings settings)
  {
  }

  @Override
  public void showSettingsList(ArrayList<SettingsItem> settingsList)
  {
    mAdapter.setSettings(settingsList);
  }

  @Override
  public void loadDefaultSettings()
  {
  }

  @Override
  public SettingsAdapter getAdapter()
  {
    return mAdapter;
  }

  @Override
  public void loadSubMenu(MenuTag menuKey)
  {
  }

  @Override
  public void showToastMessage(String message)
  {
    Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
  }

  @Override
  public Settings getSettings()
  {
    return mActivity.getSettings();
  }

  @Override
  public void onSettingChanged()
  {
    if (mActivity != null)
      mActivity.refreshHotkeyOverlay();
  }

  @Override
  public void onGcPadSettingChanged(MenuTag menuTag, int value)
  {
  }

  @Override
  public void onWiimoteSettingChanged(MenuTag menuTag, int value)
  {
  }

  @Override
  public void onExtensionSettingChanged(MenuTag menuTag, int value)
  {
  }

  private void loadSettingsList()
  {
    ArrayList<SettingsItem> sl = new ArrayList<>();
    Context context = getContext();

    // Advanced ; Uncomment when they are migrated to the new system
    /*sl.add(new CheckBoxSetting(context, BooleanSetting.MAIN_SYNC_ON_SKIP_IDLE,
            R.string.skip_on_skip_idle, 0));
    sl.add(new CheckBoxSetting(context, BooleanSetting.MAIN_JIT_FOLLOW_BRANCH,
            R.string.jit_follow_branch, 0));
    sl.add(new CheckBoxSetting(context, BooleanSetting.MAIN_OVERCLOCK_ENABLE,
            R.string.overclock_enable, 0));
    sl.add(new PercentSliderSetting(context, FloatSetting.MAIN_OVERCLOCK, R.string.overclock_title,
            0, 0, 400, "%"));
    sl.add(new PercentSliderSetting(context, FloatSetting.MAIN_EMULATION_SPEED, R.string.speed_limit,
            0, 0, 200, "%"));*/

    // GFX Enhancements

    sl.add(new SliderSelectorSetting(context, IntSetting.GFX_EFB_SCALE,
            R.string.internal_resolution,
            0, 0, R.array.internalResolutionValues, "x", 0.01f));

    // GFX Hacks
    sl.add(new InvertedCheckBoxSetting(context, BooleanSetting.GFX_HACK_EFB_ACCESS_ENABLE,
            R.string.skip_efb_access, 0));
    sl.add(new InvertedCheckBoxSetting(context, BooleanSetting.GFX_HACK_EFB_EMULATE_FORMAT_CHANGES,
            R.string.ignore_format_changes, 0));
    sl.add(new CheckBoxSetting(context, BooleanSetting.GFX_HACK_SKIP_EFB_COPY_TO_RAM,
            R.string.efb_copy_method, 0));
    sl.add(new CheckBoxSetting(context, BooleanSetting.GFX_HACK_DEFER_EFB_COPIES,
            R.string.defer_efb_copies, 0));
    //sl.add(new InvertedCheckBoxSetting(context, BooleanSetting.GFX_HACK_BBOX_ENABLE, R.string.disable_bbox, 0));

    mSettingsList = sl;
  }
}