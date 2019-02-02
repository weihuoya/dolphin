package org.dolphinemu.dolphinemu.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import org.dolphinemu.dolphinemu.NativeLibrary;
import org.dolphinemu.dolphinemu.R;
import org.dolphinemu.dolphinemu.dialogs.RunningSettingDialog;
import org.dolphinemu.dolphinemu.dialogs.StateSavesDialog;
import org.dolphinemu.dolphinemu.fragments.EmulationFragment;
import org.dolphinemu.dolphinemu.model.GameFile;
import org.dolphinemu.dolphinemu.overlay.InputOverlay;
import org.dolphinemu.dolphinemu.services.GameFileCacheService;
import org.dolphinemu.dolphinemu.ui.main.MainActivity;
import org.dolphinemu.dolphinemu.ui.main.MainPresenter;
import org.dolphinemu.dolphinemu.ui.platform.Platform;
import org.dolphinemu.dolphinemu.utils.ControllerMappingHelper;
import org.dolphinemu.dolphinemu.utils.FileBrowserHelper;
import org.dolphinemu.dolphinemu.utils.Java_GCAdapter;
import org.dolphinemu.dolphinemu.utils.Java_WiimoteAdapter;
import org.dolphinemu.dolphinemu.utils.Rumble;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;

public final class EmulationActivity extends AppCompatActivity
{
  private static WeakReference<EmulationActivity> sActivity = new WeakReference<>(null);

  public static final int REQUEST_CHANGE_DISC = 1;

  private SensorManager mSensorManager;
  private View mDecorView;
  private EmulationFragment mEmulationFragment;

  private SharedPreferences mPreferences;
  private ControllerMappingHelper mControllerMappingHelper;

  private boolean mStopEmulation;
  private boolean mMenuVisible;

  private String mSelectedTitle;
  private String mSelectedGameId;
  private int mPlatform;
  private String[] mPaths;
  private String mSavedState;

  public static final String RUMBLE_PREF_KEY = "PhoneRumble";

  public static final String EXTRA_SELECTED_GAMES = "SelectedGames";
  public static final String EXTRA_SELECTED_TITLE = "SelectedTitle";
  public static final String EXTRA_SELECTED_GAMEID = "SelectedGameId";
  public static final String EXTRA_PLATFORM = "Platform";
  public static final String EXTRA_SAVED_STATE = "SavedState";

  public static void launch(FragmentActivity activity, GameFile gameFile, String savedState)
  {
    Intent launcher = new Intent(activity, EmulationActivity.class);
    launcher.putExtra(EXTRA_SELECTED_GAMES, GameFileCacheService.getAllDiscPaths(gameFile));
    launcher.putExtra(EXTRA_SELECTED_TITLE, gameFile.getTitle());
    launcher.putExtra(EXTRA_SELECTED_GAMEID, gameFile.getGameId());
    launcher.putExtra(EXTRA_PLATFORM, gameFile.getPlatform());
    launcher.putExtra(EXTRA_SAVED_STATE, savedState);

    // I believe this warning is a bug. Activities are FragmentActivity from the support lib
    //noinspection RestrictedApi
    activity.startActivityForResult(launcher, MainPresenter.REQUEST_EMULATE_GAME);
  }

  public static EmulationActivity get()
  {
    return sActivity.get();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    sActivity = new WeakReference<>(this);

    if (savedInstanceState == null)
    {
      // Get params we were passed
      Intent gameToEmulate = getIntent();
      mPaths = gameToEmulate.getStringArrayExtra(EXTRA_SELECTED_GAMES);
      mSelectedTitle = gameToEmulate.getStringExtra(EXTRA_SELECTED_TITLE);
      mSelectedGameId = gameToEmulate.getStringExtra(EXTRA_SELECTED_GAMEID);
      mPlatform = gameToEmulate.getIntExtra(EXTRA_PLATFORM, 0);
      mSavedState = gameToEmulate.getStringExtra(EXTRA_SAVED_STATE);
    }
    else
    {
      restoreState(savedInstanceState);
    }

    mControllerMappingHelper = new ControllerMappingHelper();

    // Get a handle to the Window containing the UI.
    mDecorView = getWindow().getDecorView();
    mDecorView.setOnSystemUiVisibilityChangeListener(visibility ->
    {
      if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0)
      {
        // Go back to immersive fullscreen mode in 3s
        Handler handler = new Handler(getMainLooper());
        handler.postDelayed(this::enableFullscreenImmersive, 3000 /* 3s */);
      }
    });
    // Set these options now so that the SurfaceView the game renders into is the right size.
    mStopEmulation = false;
    enableFullscreenImmersive();

    setTheme(R.style.DolphinEmulationBase);

    Java_GCAdapter.manager = (UsbManager) getSystemService(Context.USB_SERVICE);
    Java_WiimoteAdapter.manager = (UsbManager) getSystemService(Context.USB_SERVICE);
    Rumble.initDeviceRumble();

    setContentView(R.layout.activity_emulation);

    // Find or create the EmulationFragment
    mEmulationFragment = (EmulationFragment) getSupportFragmentManager()
      .findFragmentById(R.id.frame_emulation_fragment);
    if (mEmulationFragment == null)
    {
      mEmulationFragment = EmulationFragment.newInstance(mPaths);
      getSupportFragmentManager().beginTransaction()
        .add(R.id.frame_emulation_fragment, mEmulationFragment)
        .commit();
    }

    setTitle(mSelectedTitle);

    mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    loadPreferences();
  }

  @Override
  protected void onDestroy()
  {
    super.onDestroy();
    sActivity.clear();
    savePreferences();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState)
  {
    if (!isChangingConfigurations())
    {
      mSavedState = getFilesDir() + File.separator + "temp.sav";
      NativeLibrary.SaveStateAs(mSavedState, true);
    }
    outState.putStringArray(EXTRA_SELECTED_GAMES, mPaths);
    outState.putString(EXTRA_SELECTED_TITLE, mSelectedTitle);
    outState.putString(EXTRA_SELECTED_GAMEID, mSelectedGameId);
    outState.putInt(EXTRA_PLATFORM, mPlatform);
    outState.putString(EXTRA_SAVED_STATE, mSavedState);
    super.onSaveInstanceState(outState);
  }

  protected void restoreState(Bundle savedInstanceState)
  {
    mPaths = savedInstanceState.getStringArray(EXTRA_SELECTED_GAMES);
    mSelectedTitle = savedInstanceState.getString(EXTRA_SELECTED_TITLE);
    mSelectedGameId = savedInstanceState.getString(EXTRA_SELECTED_GAMEID);
    mPlatform = savedInstanceState.getInt(EXTRA_PLATFORM);
    mSavedState = savedInstanceState.getString(EXTRA_SAVED_STATE);
  }

  @Override
  public void onBackPressed()
  {
    if (mMenuVisible)
    {
      mStopEmulation = true;
      mEmulationFragment.stopEmulation();
      finish();
    }
    else
    {
      disableFullscreenImmersive();
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent result)
  {
    switch (requestCode)
    {
      case REQUEST_CHANGE_DISC:
        // If the user picked a file, as opposed to just backing out.
        if (resultCode == MainActivity.RESULT_OK)
        {
          String newDiscPath = FileBrowserHelper.getSelectedDirectory(result);
          if (!TextUtils.isEmpty(newDiscPath))
          {
            NativeLibrary.ChangeDisc(newDiscPath);
          }
        }
        break;
    }
  }

  private void enableFullscreenImmersive()
  {
    if (mStopEmulation)
    {
      return;
    }
    mMenuVisible = false;
    // It would be nice to use IMMERSIVE_STICKY, but that doesn't show the toolbar.
    mDecorView.setSystemUiVisibility(
      View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
        View.SYSTEM_UI_FLAG_FULLSCREEN |
        View.SYSTEM_UI_FLAG_IMMERSIVE);
  }

  private void disableFullscreenImmersive()
  {
    mMenuVisible = true;
    mDecorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
      | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
      | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu)
  {
    // Inflate the menu; this adds items to the action bar if it is present.
    if (isGameCubeGame())
    {
      getMenuInflater().inflate(R.menu.menu_emulation, menu);
    }
    else
    {
      getMenuInflater().inflate(R.menu.menu_emulation_wii, menu);
    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item)
  {
    switch (item.getItemId())
    {
      // Edit the placement of the controls
      case R.id.menu_emulation_edit_layout:
        editControlsPlacement();
        break;

      case R.id.menu_emulation_joystick_settings:
        showJoystickSettings();
        break;

      case R.id.menu_emulation_sensor_settings:
        showSensorSettings();
        break;

      // Enable/Disable specific buttons or the entire input overlay.
      case R.id.menu_emulation_toggle_controls:
        toggleControls();
        break;

      // Adjust the scale of the overlay controls.
      case R.id.menu_emulation_adjust_scale:
        adjustScale();
        break;

      // (Wii games only) Change the controller for the input overlay.
      case R.id.menu_emulation_choose_controller:
        chooseController();
        break;

      /*case R.id.menu_refresh_wiimotes:
        NativeLibrary.RefreshWiimotes();
        break;*/

      // Screenshot capturing
      case R.id.menu_emulation_screenshot:
        NativeLibrary.SaveScreenShot();
        break;

      // Quick save / load
      case R.id.menu_quicksave:
        showStateSaves();
        break;

      case R.id.menu_change_disc:
        FileBrowserHelper.openFilePicker(this, REQUEST_CHANGE_DISC);
        break;

      case R.id.menu_running_setting:
        RunningSettingDialog.newInstance()
          .show(getSupportFragmentManager(), "RunningSettingDialog");
        break;

      default:
        return false;
    }

    return true;
  }

  private void showStateSaves()
  {
    StateSavesDialog.newInstance(mSelectedGameId).show(getSupportFragmentManager(), "StateSavesDialog");
  }

  private void showJoystickSettings()
  {
    final int joystick = InputOverlay.sJoyStickSetting;
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.emulation_joystick_settings);

    builder.setSingleChoiceItems(R.array.wiiJoystickSettings, joystick,
      (dialog, indexSelected) ->
      {
        InputOverlay.sJoyStickSetting = indexSelected;
      });
    builder.setOnDismissListener((dialogInterface) ->
    {
      if(InputOverlay.sJoyStickSetting != joystick)
      {
        mEmulationFragment.refreshInputOverlay();
      }
    });

    AlertDialog alertDialog = builder.create();
    alertDialog.show();
  }

  private void showSensorSettings()
  {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.emulation_sensor_settings);

    if(isGameCubeGame())
    {
      int sensor = InputOverlay.sSensorGCSetting;
      builder.setSingleChoiceItems(R.array.gcSensorSettings, sensor,
        (dialog, indexSelected) ->
        {
          InputOverlay.sSensorGCSetting = indexSelected;
        });
      builder.setOnDismissListener((dialogInterface) ->
      {
        setSensorState(InputOverlay.sSensorGCSetting > 0);
      });
    }
    else
    {
      int sensor = InputOverlay.sSensorWiiSetting;
      builder.setSingleChoiceItems(R.array.wiiSensorSettings, sensor,
        (dialog, indexSelected) ->
        {
          InputOverlay.sSensorWiiSetting = indexSelected;
        });
      builder.setOnDismissListener((dialogInterface) ->
      {
        setSensorState(InputOverlay.sSensorWiiSetting > 0);
      });
    }

    AlertDialog alertDialog = builder.create();
    alertDialog.show();
  }

  private void setSensorState(boolean enabled)
  {
    if(enabled)
    {
      if(mSensorManager == null)
      {
        mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        Sensor rotationVector = mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if(rotationVector != null)
        {
          mSensorManager.registerListener(mEmulationFragment, rotationVector, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
      }
    }
    else
    {
      if(mSensorManager != null)
      {
        mSensorManager.unregisterListener(mEmulationFragment);
        mSensorManager = null;
      }
    }

    //
    mEmulationFragment.onAccuracyChanged(null, 0);
  }

  private void editControlsPlacement()
  {
    if (mEmulationFragment.isConfiguringControls())
    {
      mEmulationFragment.stopConfiguringControls();
    }
    else
    {
      mEmulationFragment.startConfiguringControls();
    }
  }

  @Override
  protected void onResume()
  {
    super.onResume();

    if(mSensorManager != null)
    {
      Sensor rotationVector = mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
      if(rotationVector != null)
      {
        mSensorManager.registerListener(mEmulationFragment, rotationVector, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
      }
    }
  }

  @Override
  protected void onPause()
  {
    super.onPause();

    if(mSensorManager != null)
    {
      mSensorManager.unregisterListener(mEmulationFragment);
    }
  }

  private void loadPreferences()
  {
    String id = mSelectedGameId.length() > 3 ? mSelectedGameId.substring(0, 3) : mSelectedGameId;
    String scaleKey = InputOverlay.CONTROL_SCALE_PREF_KEY + "_" + id;
    String typeKey = InputOverlay.CONTROL_TYPE_PREF_KEY + "_" + id;
    String joystickKey = InputOverlay.JOYSTICK_PREF_KEY + "_" + id;
    String recenterKey = InputOverlay.RECENTER_PREF_KEY + "_" + id;

    InputOverlay.sControllerScale = mPreferences.getInt(scaleKey, 50);
    InputOverlay.sControllerType = mPreferences.getInt(typeKey, InputOverlay.CONTROLLER_WIINUNCHUK);
    InputOverlay.sJoyStickSetting = mPreferences.getInt(joystickKey, InputOverlay.JOYSTICK_EMULATE_NONE);
    InputOverlay.sJoystickRelative = mPreferences.getBoolean(InputOverlay.RELATIVE_PREF_KEY, true);
    InputOverlay.sIRRecenter = mPreferences.getBoolean(recenterKey, false);

    if(isGameCubeGame())
      InputOverlay.sJoyStickSetting = InputOverlay.JOYSTICK_EMULATE_NONE;

    InputOverlay.sSensorGCSetting = InputOverlay.SENSOR_GC_NONE;
    InputOverlay.sSensorWiiSetting = InputOverlay.SENSOR_WII_NONE;

    Rumble.setPhoneRumble(this, mPreferences.getBoolean(RUMBLE_PREF_KEY, true));
  }

  private void savePreferences()
  {
    String id = mSelectedGameId.length() > 3 ? mSelectedGameId.substring(0, 3) : mSelectedGameId;
    String scaleKey = InputOverlay.CONTROL_SCALE_PREF_KEY + "_" + id;
    String typeKey = InputOverlay.CONTROL_TYPE_PREF_KEY + "_" + id;
    String joystickKey = InputOverlay.JOYSTICK_PREF_KEY + "_" + id;
    String recenterKey = InputOverlay.RECENTER_PREF_KEY + "_" + id;

    SharedPreferences.Editor editor = mPreferences.edit();
    editor.putInt(typeKey, InputOverlay.sControllerType);
    editor.putInt(scaleKey, InputOverlay.sControllerScale);
    editor.putInt(joystickKey, InputOverlay.sJoyStickSetting);
    editor.putBoolean(InputOverlay.RELATIVE_PREF_KEY, InputOverlay.sJoystickRelative);
    editor.putBoolean(recenterKey, InputOverlay.sIRRecenter);
    editor.apply();
  }

  // Gets button presses
  @Override
  public boolean dispatchKeyEvent(KeyEvent event)
  {
    if (mMenuVisible)
    {
      return super.dispatchKeyEvent(event);
    }

    int action;
    switch (event.getAction())
    {
      case KeyEvent.ACTION_DOWN:
        // Handling the case where the back button is pressed.
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK)
        {
          onBackPressed();
          return true;
        }
        // Normal key events.
        action = NativeLibrary.ButtonState.PRESSED;
        break;
      case KeyEvent.ACTION_UP:
        action = NativeLibrary.ButtonState.RELEASED;
        break;
      default:
        return false;
    }

    InputDevice input = event.getDevice();
    if (input != null)
      return NativeLibrary.onGamePadEvent(input.getDescriptor(), event.getKeyCode(), action);
    else
      return false;
  }

  private void toggleControls()
  {
    final SharedPreferences.Editor editor = mPreferences.edit();
    final int controller = InputOverlay.sControllerType;
    boolean[] enabledButtons = new boolean[16];
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.emulation_toggle_controls);

    int resId;
    String keyPrefix;
    if (isGameCubeGame() || controller == InputOverlay.CONTROLLER_GAMECUBE)
    {
      resId = R.array.gcpadButtons;
      keyPrefix = "buttonToggleGc";
    }
    else if (controller == InputOverlay.COCONTROLLER_CLASSIC)
    {
      resId = R.array.classicButtons;
      keyPrefix = "buttonToggleClassic";
    }
    else
    {
      resId = controller == InputOverlay.CONTROLLER_WIINUNCHUK ?
        R.array.nunchukButtons : R.array.wiimoteButtons;
      keyPrefix = "buttonToggleWii";
    }

    for (int i = 0; i < enabledButtons.length; i++)
    {
      enabledButtons[i] = mPreferences.getBoolean(keyPrefix + i, true);
    }
    builder.setMultiChoiceItems(resId, enabledButtons,
      (dialog, indexSelected, isChecked) -> editor
        .putBoolean(keyPrefix + indexSelected, isChecked));

    builder.setNeutralButton(getString(R.string.emulation_toggle_all), (dialogInterface, i) ->
    {
      editor.putBoolean("showInputOverlay",
        !mPreferences.getBoolean("showInputOverlay", false));
      editor.apply();
      mEmulationFragment.refreshInputOverlay();
    });
    builder.setPositiveButton(getString(R.string.ok), (dialogInterface, i) ->
    {
      editor.apply();
      mEmulationFragment.refreshInputOverlay();
    });

    AlertDialog alertDialog = builder.create();
    alertDialog.show();
  }

  private void adjustScale()
  {
    LayoutInflater inflater = LayoutInflater.from(this);
    View view = inflater.inflate(R.layout.dialog_seekbar, null);

    final SeekBar seekbar = view.findViewById(R.id.seekbar);
    final TextView value = view.findViewById(R.id.text_value);
    final TextView units = view.findViewById(R.id.text_units);

    seekbar.setMax(150);
    seekbar.setProgress(InputOverlay.sControllerScale);
    seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
    {
      public void onStartTrackingTouch(SeekBar seekBar)
      {
        // Do nothing
      }

      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
      {
        value.setText(String.valueOf(progress + 50));
      }

      public void onStopTrackingTouch(SeekBar seekBar)
      {
        // Do nothing
      }
    });

    value.setText(String.valueOf(seekbar.getProgress() + 50));
    units.setText("%");

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.emulation_control_scale);
    builder.setView(view);
    builder.setPositiveButton(getString(R.string.ok), (dialogInterface, i) ->
    {
      InputOverlay.sControllerScale = seekbar.getProgress();
      mEmulationFragment.refreshInputOverlay();
    });

    AlertDialog alertDialog = builder.create();
    alertDialog.show();
  }

  private void chooseController()
  {
    int controller = InputOverlay.sControllerType;
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.emulation_choose_controller);
    builder.setSingleChoiceItems(R.array.controllersEntries, controller,
      (dialog, indexSelected) ->
      {
        InputOverlay.sControllerType = indexSelected;
      });
    builder.setOnDismissListener((dialogInterface) ->
    {
      NativeLibrary.SetConfig("WiimoteNew.ini", "Wiimote1", "Extension",
        getResources().getStringArray(R.array.controllersValues)[InputOverlay.sControllerType]);
      mEmulationFragment.refreshInputOverlay();
    });

    AlertDialog alertDialog = builder.create();
    alertDialog.show();
  }

  @Override
  public boolean dispatchGenericMotionEvent(MotionEvent event)
  {
    if (mMenuVisible)
    {
      return false;
    }

    if (((event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) == 0))
    {
      return super.dispatchGenericMotionEvent(event);
    }

    // Don't attempt to do anything if we are disconnecting a device.
    if (event.getActionMasked() == MotionEvent.ACTION_CANCEL)
      return true;

    InputDevice input = event.getDevice();
    List<InputDevice.MotionRange> motions = input.getMotionRanges();

    for (InputDevice.MotionRange range : motions)
    {
      int axis = range.getAxis();
      float origValue = event.getAxisValue(axis);
      float value = mControllerMappingHelper.scaleAxis(input, axis, origValue);
      // If the input is still in the "flat" area, that means it's really zero.
      // This is used to compensate for imprecision in joysticks.
      if (Math.abs(value) > range.getFlat())
      {
        NativeLibrary.onGamePadMoveEvent(input.getDescriptor(), axis, value);
      }
      else
      {
        NativeLibrary.onGamePadMoveEvent(input.getDescriptor(), axis, 0.0f);
      }
    }

    return true;
  }

  public boolean isGameCubeGame()
  {
    return Platform.fromNativeInt(mPlatform) == Platform.GAMECUBE;
  }

  public String getSavedState()
  {
    return mSavedState;
  }

  public void setTouchPointerEnabled(boolean enabled)
  {
    mEmulationFragment.setTouchPointerEnabled(enabled);
  }

  public void updateTouchPointer()
  {
    mEmulationFragment.updateTouchPointer();
  }
}
