package org.dolphinemu.dolphinemu.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.nononsenseapps.filepicker.DividerItemDecoration;

import org.dolphinemu.dolphinemu.NativeLibrary;
import org.dolphinemu.dolphinemu.R;
import org.dolphinemu.dolphinemu.activities.EmulationActivity;
import org.dolphinemu.dolphinemu.features.settings.ui.MenuTag;
import org.dolphinemu.dolphinemu.features.settings.ui.SettingsActivity;

import java.util.ArrayList;

public class RunningSettingDialog extends DialogFragment
{
  public class SettingsItem
  {
    // gfx
    public static final int SETTING_SHOW_FPS = 0;
    public static final int SETTING_SKIP_EFB = 1;
    public static final int SETTING_EFB_TEXTURE = 2;
    public static final int SETTING_IGNORE_FORMAT = 3;
    public static final int SETTING_ARBITRARY_MIPMAP_DETECTION = 4;
    public static final int SETTING_IMMEDIATE_XFB = 5;
    // core
    public static final int SETTING_SYNC_ON_SKIP_IDLE = 6;
    public static final int SETTING_OVERCLOCK_ENABLE = 7;
    public static final int SETTING_OVERCLOCK_PERCENT = 8;
    public static final int SETTING_JIT_FOLLOW_BRANCH = 9;
    // view type
    public static final int TYPE_CHECKBOX = 0;
    public static final int TYPE_RADIO_GROUP = 1;
    public static final int TYPE_SEEK_BAR = 2;
    public static final int TYPE_BUTTON = 3;
    // func: gamecube and wii
    public static final int SETTING_LOAD_SUBMENU = 200;
    public static final int SETTING_EDIT_BUTTONS = 201;
    public static final int SETTING_TOGGLE_CONTROLS = 202;
    public static final int SETTING_ADJUST_CONTROLS = 203;
    public static final int SETTING_ON_SCREEN_HOTKEY = 204;
    public static final int SETTING_RESET_OVERLAY = 205;
    public static final int SETTING_TAKE_SCREENSHOT = 206;
    public static final int SETTING_QUICK_SAVE = 207;
    public static final int SETTING_QUICK_LOAD = 208;
    public static final int SETTING_CHANGE_DISC = 209;
    public static final int SETTING_EXIT_GAME = 210;
    // func: wii only
    public static final int SETTING_CHOOSE_CONTROLLER = 211;
    public static final int SETTING_MOTION_CONTROLS = 212;
    public static final int SETTING_IR_SENSITIVITY = 213;
    public static final int SETTING_CHOOSE_DOUBLE_TAP_BUTTON = 214;

    private int mSetting;
    private String mName;
    private int mType;
    private int mValue;

    public SettingsItem(int setting, int nameId, int type, int value)
    {
      mSetting = setting;
      mName = getString(nameId);
      mType = type;
      mValue = value;
    }

    public SettingsItem(int setting, String name, int type, int value)
    {
      mSetting = setting;
      mName = name;
      mType = type;
      mValue = value;
    }

    public int getType()
    {
      return mType;
    }

    public int getSetting()
    {
      return mSetting;
    }

    public String getName()
    {
      return mName;
    }

    public int getValue()
    {
      return mValue;
    }

    public void setValue(int value)
    {
      mValue = value;
    }
  }

  public abstract class SettingViewHolder extends RecyclerView.ViewHolder
          implements View.OnClickListener
  {
    public SettingViewHolder(View itemView)
    {
      super(itemView);
      itemView.setOnClickListener(this);
      findViews(itemView);
    }

    protected abstract void findViews(View root);

    public abstract void bind(SettingsItem item);

    public abstract void onClick(View clicked);
  }

  public final class ButtonSettingViewHolder extends SettingViewHolder
  {
    SettingsItem mItem;
    private TextView mName;

    public ButtonSettingViewHolder(View itemView)
    {
      super(itemView);
    }

    @Override
    protected void findViews(View root)
    {
      mName = root.findViewById(R.id.text_setting_name);
    }

    @Override
    public void bind(SettingsItem item)
    {
      mItem = item;
      mName.setText(item.getName());
    }

    @Override
    public void onClick(View clicked)
    {
      EmulationActivity activity = (EmulationActivity) NativeLibrary.getEmulationActivity();
      switch (mItem.getSetting())
      {
        case SettingsItem.SETTING_LOAD_SUBMENU:
          loadSubMenu(mItem.getValue());
          break;
        case SettingsItem.SETTING_EDIT_BUTTONS:
          activity.editControlsPlacement();
          dismiss();
          break;
        case SettingsItem.SETTING_TOGGLE_CONTROLS:
          activity.toggleControls();
          dismiss();
          break;
        case SettingsItem.SETTING_ADJUST_CONTROLS:
          activity.adjustScale();
          dismiss();
          break;
        case SettingsItem.SETTING_ON_SCREEN_HOTKEY:
          activity.setHotkeyMode();
          dismiss();
          break;
        case SettingsItem.SETTING_RESET_OVERLAY:
          activity.resetOverlay();
          dismiss();
          break;
        case SettingsItem.SETTING_TAKE_SCREENSHOT:
          NativeLibrary.SaveScreenShot();
          dismiss();
          break;
        case SettingsItem.SETTING_QUICK_SAVE:
          NativeLibrary.SaveState(9, false);
          dismiss();
          break;
        case SettingsItem.SETTING_QUICK_LOAD:
          NativeLibrary.LoadState(9);
          dismiss();
          break;
        case SettingsItem.SETTING_CHANGE_DISC:
          Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
          intent.addCategory(Intent.CATEGORY_OPENABLE);
          intent.setType("*/*");
          startActivityForResult(intent, EmulationActivity.REQUEST_CHANGE_DISC);
          dismiss();
          break;
        case SettingsItem.SETTING_EXIT_GAME:
          NativeLibrary.StopEmulation();
          activity.finish();
          break;
        // wii
        case SettingsItem.SETTING_CHOOSE_CONTROLLER:
          activity.chooseController();
          dismiss();
          break;
        case SettingsItem.SETTING_MOTION_CONTROLS:
          activity.showMotionControlsOptions();
          dismiss();
          break;
        case SettingsItem.SETTING_IR_SENSITIVITY:
          activity.setIRSensitivity();
          dismiss();
          break;
        case SettingsItem.SETTING_CHOOSE_DOUBLE_TAP_BUTTON:
          activity.chooseDoubleTapButton();
          dismiss();
          break;
      }
    }
  }

  public final class CheckBoxSettingViewHolder extends SettingViewHolder
          implements CompoundButton.OnCheckedChangeListener
  {
    SettingsItem mItem;
    private TextView mTextSettingName;
    private CheckBox mCheckbox;

    public CheckBoxSettingViewHolder(View itemView)
    {
      super(itemView);
    }

    @Override
    protected void findViews(View root)
    {
      mTextSettingName = root.findViewById(R.id.text_setting_name);
      mCheckbox = root.findViewById(R.id.checkbox);
      mCheckbox.setOnCheckedChangeListener(this);
    }

    @Override
    public void bind(SettingsItem item)
    {
      mItem = item;
      mTextSettingName.setText(item.getName());
      mCheckbox.setChecked(mItem.getValue() > 0);
    }

    @Override
    public void onClick(View clicked)
    {
      mCheckbox.toggle();
      mItem.setValue(mCheckbox.isChecked() ? 1 : 0);
    }

    @Override
    public void onCheckedChanged(CompoundButton view, boolean isChecked)
    {
      mItem.setValue(isChecked ? 1 : 0);
    }
  }

  public final class RadioButtonSettingViewHolder extends SettingViewHolder
          implements RadioGroup.OnCheckedChangeListener
  {
    SettingsItem mItem;
    private TextView mTextSettingName;
    private RadioGroup mRadioGroup;

    public RadioButtonSettingViewHolder(View itemView)
    {
      super(itemView);
    }

    @Override
    protected void findViews(View root)
    {
      mTextSettingName = root.findViewById(R.id.text_setting_name);
      mRadioGroup = root.findViewById(R.id.radio_group);
      mRadioGroup.setOnCheckedChangeListener(this);
    }

    @Override
    public void bind(SettingsItem item)
    {
      int checkIds[] = {R.id.radio0, R.id.radio1, R.id.radio2};
      int index = item.getValue();
      if (index < 0 || index >= checkIds.length)
        index = 0;

      mItem = item;
      mTextSettingName.setText(item.getName());
      mRadioGroup.check(checkIds[index]);
    }

    @Override
    public void onClick(View clicked)
    {

    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId)
    {
      switch (checkedId)
      {
        case R.id.radio0:
          mItem.setValue(0);
          break;
        case R.id.radio1:
          mItem.setValue(1);
          break;
        case R.id.radio2:
          mItem.setValue(2);
          break;
        default:
          mItem.setValue(0);
          break;
      }
    }
  }

  public final class SeekBarSettingViewHolder extends SettingViewHolder
  {
    SettingsItem mItem;
    private TextView mTextSettingName;
    private TextView mTextSettingValue;
    private SeekBar mSeekBar;

    public SeekBarSettingViewHolder(View itemView)
    {
      super(itemView);
    }

    @Override
    protected void findViews(View root)
    {
      mTextSettingName = root.findViewById(R.id.text_setting_name);
      mTextSettingValue = root.findViewById(R.id.text_setting_value);
      mSeekBar = root.findViewById(R.id.seekbar);
      mSeekBar.setProgress(99);
    }

    @Override
    public void bind(SettingsItem item)
    {
      mItem = item;
      mTextSettingName.setText(item.getName());
      switch (item.getSetting())
      {
        case SettingsItem.SETTING_OVERCLOCK_PERCENT:
          mSeekBar.setMax(400);
          break;
        default:
          mSeekBar.setMax(10);
          break;
      }
      mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
      {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean b)
        {
          if (seekBar.getMax() > 99)
          {
            progress = (progress / 5) * 5;
            mTextSettingValue.setText(progress + "%");
          }
          else
          {
            mTextSettingValue.setText(String.valueOf(progress));
          }
          mItem.setValue(progress);
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
      mSeekBar.setProgress(item.getValue());
    }

    @Override
    public void onClick(View clicked)
    {

    }
  }

  public class SettingsAdapter extends RecyclerView.Adapter<SettingViewHolder>
  {
    private int[] mRunningSettings;
    private ArrayList<SettingsItem> mSettings;

    public void loadMainMenu()
    {
      mSettings = new ArrayList<>();
      mSettings.add(new SettingsItem(SettingsItem.SETTING_LOAD_SUBMENU,
              R.string.emulation_quick_settings, SettingsItem.TYPE_BUTTON, MENU_SETTINGS));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_LOAD_SUBMENU,
              R.string.emulation_overlay_controls, SettingsItem.TYPE_BUTTON, MENU_OVERLAY));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_TAKE_SCREENSHOT,
              R.string.emulation_screenshot,
              SettingsItem.TYPE_BUTTON, 0));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_QUICK_SAVE, R.string.emulation_quicksave,
              SettingsItem.TYPE_BUTTON, 0));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_QUICK_LOAD, R.string.emulation_quickload,
              SettingsItem.TYPE_BUTTON, 0));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_CHANGE_DISC,
              R.string.emulation_change_disc,
              SettingsItem.TYPE_BUTTON, 0));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_EXIT_GAME, R.string.emulation_exit,
              SettingsItem.TYPE_BUTTON, 0));
      notifyDataSetChanged();
    }

    public void loadOverlayMenu()
    {
      mSettings = new ArrayList<>();
      mSettings.add(new SettingsItem(SettingsItem.SETTING_EDIT_BUTTONS,
              R.string.emulation_edit_layout, SettingsItem.TYPE_BUTTON, 0));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_TOGGLE_CONTROLS,
              R.string.emulation_toggle_controls,
              SettingsItem.TYPE_BUTTON, 0));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_ADJUST_CONTROLS,
              R.string.emulation_control_adjustments, SettingsItem.TYPE_BUTTON, 0));
      // wii
      if (NativeLibrary.IsEmulatingWii())
      {
        mSettings.add(new SettingsItem(SettingsItem.SETTING_CHOOSE_CONTROLLER,
                R.string.emulation_choose_controller, SettingsItem.TYPE_BUTTON, 0));
        mSettings.add(new SettingsItem(SettingsItem.SETTING_MOTION_CONTROLS,
                R.string.emulation_motion_controls, SettingsItem.TYPE_BUTTON, 0));
        mSettings.add(new SettingsItem(SettingsItem.SETTING_IR_SENSITIVITY,
                R.string.emulation_ir_sensitivity, SettingsItem.TYPE_BUTTON, 0));
        mSettings.add(new SettingsItem(SettingsItem.SETTING_CHOOSE_DOUBLE_TAP_BUTTON,
                R.string.emulation_choose_doubletap, SettingsItem.TYPE_BUTTON, 0));
      }
      mSettings.add(new SettingsItem(SettingsItem.SETTING_ON_SCREEN_HOTKEY,
              R.string.emulation_hotkey, SettingsItem.TYPE_BUTTON, 0));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_RESET_OVERLAY,
              R.string.emulation_touch_overlay_reset, SettingsItem.TYPE_BUTTON, 0));
      notifyDataSetChanged();
    }

    public void loadQuickSettingsMenu()
    {
      int i = 0;
      mRunningSettings = NativeLibrary.getRunningSettings();
      mSettings = new ArrayList<>();

      // gfx
      mSettings.add(new SettingsItem(SettingsItem.SETTING_SHOW_FPS, R.string.show_fps,
              SettingsItem.TYPE_CHECKBOX, mRunningSettings[i++]));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_SKIP_EFB,
              R.string.skip_efb_access, SettingsItem.TYPE_CHECKBOX, mRunningSettings[i++]));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_EFB_TEXTURE, R.string.efb_copy_method,
              SettingsItem.TYPE_CHECKBOX, mRunningSettings[i++]));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_IGNORE_FORMAT,
              R.string.ignore_format_changes, SettingsItem.TYPE_CHECKBOX, mRunningSettings[i++]));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_ARBITRARY_MIPMAP_DETECTION,
              R.string.arbitrary_mipmap_detection, SettingsItem.TYPE_CHECKBOX,
              mRunningSettings[i++]));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_IMMEDIATE_XFB,
              R.string.immediate_xfb, SettingsItem.TYPE_CHECKBOX, mRunningSettings[i++]));

      // core
      mSettings.add(new SettingsItem(SettingsItem.SETTING_SYNC_ON_SKIP_IDLE,
              R.string.synchronize_gpu_thread, SettingsItem.TYPE_CHECKBOX, mRunningSettings[i++]));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_OVERCLOCK_ENABLE,
              R.string.overclock_enable, SettingsItem.TYPE_CHECKBOX, mRunningSettings[i++]));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_OVERCLOCK_PERCENT,
              R.string.overclock_title, SettingsItem.TYPE_SEEK_BAR, mRunningSettings[i++]));
      mSettings.add(new SettingsItem(SettingsItem.SETTING_JIT_FOLLOW_BRANCH,
              R.string.jit_follow_branch, SettingsItem.TYPE_CHECKBOX, mRunningSettings[i++]));
    }

    @NonNull
    @Override
    public SettingViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
    {
      View itemView;
      LayoutInflater inflater = LayoutInflater.from(parent.getContext());
      switch (viewType)
      {
        case SettingsItem.TYPE_CHECKBOX:
          itemView = inflater.inflate(R.layout.list_item_running_checkbox, parent, false);
          return new CheckBoxSettingViewHolder(itemView);
        case SettingsItem.TYPE_RADIO_GROUP:
          itemView = inflater.inflate(R.layout.list_item_running_radio3, parent, false);
          return new RadioButtonSettingViewHolder(itemView);
        case SettingsItem.TYPE_SEEK_BAR:
          itemView = inflater.inflate(R.layout.list_item_running_seekbar, parent, false);
          return new SeekBarSettingViewHolder(itemView);
        case SettingsItem.TYPE_BUTTON:
          itemView = inflater.inflate(R.layout.list_item_running_button, parent, false);
          return new ButtonSettingViewHolder(itemView);
      }
      return null;
    }

    @Override
    public int getItemCount()
    {
      return mSettings.size();
    }

    @Override
    public int getItemViewType(int position)
    {
      return mSettings.get(position).getType();
    }

    @Override
    public void onBindViewHolder(@NonNull SettingViewHolder holder, int position)
    {
      holder.bind(mSettings.get(position));
    }

    public void saveSettings()
    {
      if (mRunningSettings == null)
      {
        return;
      }

      // settings
      boolean isChanged = false;
      int[] newSettings = new int[mRunningSettings.length];
      for (int i = 0; i < mRunningSettings.length; ++i)
      {
        newSettings[i] = mSettings.get(i).getValue();
        if (newSettings[i] != mRunningSettings[i])
        {
          isChanged = true;
        }
      }
      // apply settings changed
      if (isChanged)
      {
        NativeLibrary.setRunningSettings(newSettings);
      }
    }
  }

  public static RunningSettingDialog newInstance()
  {
    return new RunningSettingDialog();
  }

  public static final int MENU_MAIN = 0;
  public static final int MENU_OVERLAY = 1;
  public static final int MENU_SETTINGS = 2;

  private int mMenu;
  private TextView mTitle;
  private TextView mInfo;
  private Handler mHandler;
  private SettingsAdapter mAdapter;
  private DialogInterface.OnDismissListener mDismissListener;

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState)
  {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    ViewGroup contents = (ViewGroup) getActivity().getLayoutInflater()
            .inflate(R.layout.dialog_running_settings, null);

    mTitle = contents.findViewById(R.id.text_title);
    mInfo = contents.findViewById(R.id.text_info);
    mHandler = new Handler(getActivity().getMainLooper());
    setHeapInfo();

    contents.findViewById(R.id.open_settings).setOnClickListener(v ->
    {
      SettingsActivity.launch(requireActivity(), MenuTag.SETTINGS);
      dismiss();
    });

    int columns = 1;
    Drawable lineDivider = getContext().getDrawable(R.drawable.line_divider);
    RecyclerView recyclerView = contents.findViewById(R.id.list_settings);
    RecyclerView.LayoutManager layoutManager = new GridLayoutManager(getContext(), columns);
    recyclerView.setLayoutManager(layoutManager);
    mAdapter = new SettingsAdapter();
    recyclerView.setAdapter(mAdapter);
    recyclerView.addItemDecoration(new DividerItemDecoration(lineDivider));
    builder.setView(contents);
    loadSubMenu(MENU_MAIN);
    return builder.create();
  }

  // show ram usage
  public void setHeapInfo()
  {
    long heapsize = Debug.getNativeHeapAllocatedSize() >> 20;
    mInfo.setText(String.format("%dMB", heapsize));
    mHandler.postDelayed(this::setHeapInfo, 1000);
  }

  public void setOnDismissListener(DialogInterface.OnDismissListener listener)
  {
    mDismissListener = listener;
  }

  @Override
  public void onDismiss(DialogInterface dialog)
  {
    super.onDismiss(dialog);
    if (mMenu == MENU_SETTINGS)
    {
      mAdapter.saveSettings();
    }
    if (mDismissListener != null)
    {
      mDismissListener.onDismiss(dialog);
    }
    mHandler.removeCallbacksAndMessages(null);
  }

  private void loadSubMenu(int menu)
  {
    if (menu == MENU_MAIN)
    {
      mTitle.setText(NativeLibrary.GetCurrentGameID());
      mAdapter.loadMainMenu();
    }
    else if (menu == MENU_OVERLAY)
    {
      mTitle.setText(R.string.emulation_overlay_controls);
      mAdapter.loadOverlayMenu();
    }
    else if (menu == MENU_SETTINGS)
    {
      mTitle.setText(R.string.settings);
      mAdapter.loadQuickSettingsMenu();
    }
    mMenu = menu;
  }
}