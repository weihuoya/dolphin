/**
 * Copyright 2013 Dolphin Emulator Project
 * Licensed under GPLv2+
 * Refer to the license.txt file included.
 */

package org.dolphinemu.dolphinemu.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;

import org.dolphinemu.dolphinemu.NativeLibrary;
import org.dolphinemu.dolphinemu.NativeLibrary.ButtonType;
import org.dolphinemu.dolphinemu.R;
import org.dolphinemu.dolphinemu.activities.EmulationActivity;

import java.util.ArrayList;

/**
 * Draws the interactive input overlay on top of the
 * {@link SurfaceView} that is rendering emulation.
 */
public final class InputOverlay extends SurfaceView implements OnTouchListener
{
  public static final String CONTROL_INIT_PREF_KEY = "InitOverlay";
  public static final String CONTROL_SCALE_PREF_KEY = "ControlScale";
  public static int sControllerScale;
  public static final String CONTROL_ALPHA_PREF_KEY = "ControlAlpha";
  public static int sControllerAlpha;

  public static final String POINTER_PREF_KEY = "TouchPointer1";
  public static final String RECENTER_PREF_KEY = "IRRecenter";
  public static boolean sIRRecenter;
  public static final String RELATIVE_PREF_KEY = "JoystickRelative";
  public static boolean sJoystickRelative;

  public static final String CONTROL_TYPE_PREF_KEY = "WiiController";
  public static final int CONTROLLER_GAMECUBE = 0;
  public static final int CONTROLLER_CLASSIC = 1;
  public static final int CONTROLLER_WIINUNCHUK = 2;
  public static final int CONTROLLER_WIIREMOTE = 3;
  public static int sControllerType;

  public static final String JOYSTICK_PREF_KEY = "JoystickEmulate";
  public static final int JOYSTICK_EMULATE_NONE = 0;
  public static final int JOYSTICK_EMULATE_IR = 1;
  public static final int JOYSTICK_EMULATE_WII_SWING = 2;
  public static final int JOYSTICK_EMULATE_WII_TILT = 3;
  public static final int JOYSTICK_EMULATE_WII_SHAKE = 4;
  public static final int JOYSTICK_EMULATE_NUNCHUK_SWING = 5;
  public static final int JOYSTICK_EMULATE_NUNCHUK_TILT = 6;
  public static final int JOYSTICK_EMULATE_NUNCHUK_SHAKE = 7;
  public static int sJoyStickSetting;

  public static final int SENSOR_GC_NONE = 0;
  public static final int SENSOR_GC_JOYSTICK = 1;
  public static final int SENSOR_GC_CSTICK = 2;
  public static final int SENSOR_GC_DPAD = 3;
  public static int sSensorGCSetting;

  public static final int SENSOR_WII_NONE = 0;
  public static final int SENSOR_WII_DPAD = 1;
  public static final int SENSOR_WII_STICK = 2;
  public static final int SENSOR_WII_IR = 3;
  public static final int SENSOR_WII_SWING = 4;
  public static final int SENSOR_WII_TILT = 5;
  public static final int SENSOR_WII_SHAKE = 6;
  public static final int SENSOR_NUNCHUK_SWING = 7;
  public static final int SENSOR_NUNCHUK_TILT = 8;
  public static final int SENSOR_NUNCHUK_SHAKE = 9;
  public static int sSensorWiiSetting;

  public static int[] sShakeStates = new int[4];

  private final ArrayList<InputOverlayDrawableButton> overlayButtons = new ArrayList<>();
  private final ArrayList<InputOverlayDrawableDpad> overlayDpads = new ArrayList<>();
  private final ArrayList<InputOverlayDrawableJoystick> overlayJoysticks = new ArrayList<>();
  private InputOverlayPointer mOverlayPointer = null;
  private InputOverlaySensor mOverlaySensor = null;

  private boolean mIsInEditMode = false;
  private InputOverlayDrawableButton mButtonBeingConfigured;
  private InputOverlayDrawableDpad mDpadBeingConfigured;
  private InputOverlayDrawableJoystick mJoystickBeingConfigured;

  private SharedPreferences mPreferences;

  /**
   * Resizes a {@link Bitmap} by a given scale factor
   *
   * @param context The current {@link Context}
   * @param bitmap  The {@link Bitmap} to scale.
   * @param scale   The scale factor for the bitmap.
   * @return The scaled {@link Bitmap}
   */
  public static Bitmap resizeBitmap(Context context, Bitmap bitmap, float scale)
  {
    // Determine the button size based on the smaller screen dimension.
    // This makes sure the buttons are the same size in both portrait and landscape.
    DisplayMetrics dm = context.getResources().getDisplayMetrics();
    int minDimension = Math.min(dm.widthPixels, dm.heightPixels);

    return Bitmap.createScaledBitmap(bitmap,
      (int) (minDimension * scale),
      (int) (minDimension * scale),
      true);
  }

  /**
   * Constructor
   *
   * @param context The current {@link Context}.
   * @param attrs   {@link AttributeSet} for parsing XML attributes.
   */
  public InputOverlay(Context context, AttributeSet attrs)
  {
    super(context, attrs);

    mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    if (!mPreferences.getBoolean(CONTROL_INIT_PREF_KEY, false))
      defaultOverlay();

    // initialize shake states
    for(int i = 0; i < sShakeStates.length; ++i)
    {
      sShakeStates[i] = NativeLibrary.ButtonState.RELEASED;
    }

    // init touch pointer
    int touchPointer = 0;
    if(!EmulationActivity.get().isGameCubeGame())
      touchPointer = mPreferences.getInt(InputOverlay.POINTER_PREF_KEY, 0);
    setTouchPointer(touchPointer);

    // Load the controls.
    refreshControls();

    // Set the on touch listener.
    setOnTouchListener(this);

    // Force draw
    setWillNotDraw(false);

    // Request focus for the overlay so it has priority on presses.
    requestFocus();
  }

  @Override
  public void draw(Canvas canvas)
  {
    super.draw(canvas);

    for (InputOverlayDrawableButton button : overlayButtons)
    {
      button.onDraw(canvas);
    }

    for (InputOverlayDrawableDpad dpad : overlayDpads)
    {
      dpad.onDraw(canvas);
    }

    for (InputOverlayDrawableJoystick joystick : overlayJoysticks)
    {
      joystick.onDraw(canvas);
    }
  }

  @Override
  public boolean onTouch(View v, MotionEvent event)
  {
    if (isInEditMode())
    {
      return onTouchWhileEditing(event);
    }

    boolean isProcessed = false;
    switch (event.getAction() & MotionEvent.ACTION_MASK)
    {
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_POINTER_DOWN:
      {
        int pointerIndex = event.getActionIndex();
        int pointerId = event.getPointerId(pointerIndex);
        float pointerX = event.getX(pointerIndex);
        float pointerY = event.getY(pointerIndex);

        for (InputOverlayDrawableJoystick joystick : overlayJoysticks)
        {
          if(joystick.getBounds().contains((int)pointerX, (int)pointerY))
          {
            joystick.onPointerDown(pointerId, pointerX, pointerY);
            isProcessed = true;
            break;
          }
        }

        for (InputOverlayDrawableButton button : overlayButtons)
        {
          if (button.getBounds().contains((int)pointerX, (int)pointerY))
          {
            button.onPointerDown(pointerId, pointerX, pointerY);
            isProcessed = true;
          }
        }

        for (InputOverlayDrawableDpad dpad : overlayDpads)
        {
          if (dpad.getBounds().contains((int)pointerX, (int)pointerY))
          {
            dpad.onPointerDown(pointerId, pointerX, pointerY);
            isProcessed = true;
          }
        }

        if(!isProcessed && mOverlayPointer != null && mOverlayPointer.getTrackId() == -1)
          mOverlayPointer.onPointerDown(pointerId, pointerX, pointerY);
        break;
      }
      case MotionEvent.ACTION_MOVE:
      {
        int pointerCount = event.getPointerCount();
        for (int i = 0; i < pointerCount; ++i)
        {
          boolean isCaptured = false;
          int pointerId = event.getPointerId(i);
          float pointerX = event.getX(i);
          float pointerY = event.getY(i);

          for (InputOverlayDrawableJoystick joystick : overlayJoysticks)
          {
            if(joystick.getTrackId() == pointerId)
            {
              joystick.onPointerMove(pointerId, pointerX, pointerY);
              isCaptured = true;
              isProcessed = true;
              break;
            }
          }
          if(isCaptured)
            continue;

          for (InputOverlayDrawableButton button : overlayButtons)
          {
            if (button.getBounds().contains((int)pointerX, (int)pointerY))
            {
              if(button.getTrackId() == -1)
              {
                button.onPointerDown(pointerId, pointerX, pointerY);
                isProcessed = true;
              }
            }
            else if(button.getTrackId() == pointerId)
            {
              button.onPointerUp(pointerId, pointerX, pointerY);
              isProcessed = true;
            }
          }

          for (InputOverlayDrawableDpad dpad : overlayDpads)
          {
            if(dpad.getTrackId() == pointerId)
            {
              dpad.onPointerMove(pointerId, pointerX, pointerY);
              isProcessed = true;
            }
          }

          if(mOverlayPointer != null && mOverlayPointer.getTrackId() == pointerId)
          {
            mOverlayPointer.onPointerMove(pointerId, pointerX, pointerY);
          }
        }
        break;
      }

      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_POINTER_UP:
      {
        int pointerIndex = event.getActionIndex();
        int pointerId = event.getPointerId(pointerIndex);
        float pointerX = event.getX(pointerIndex);
        float pointerY = event.getY(pointerIndex);

        if(mOverlayPointer != null && mOverlayPointer.getTrackId() == pointerId)
        {
          mOverlayPointer.onPointerUp(pointerId, pointerX, pointerY);
        }

        for (InputOverlayDrawableJoystick joystick : overlayJoysticks)
        {
          if(joystick.getTrackId() == pointerId)
          {
            joystick.onPointerUp(pointerId, pointerX, pointerY);
            isProcessed = true;
            break;
          }
        }

        for (InputOverlayDrawableButton button : overlayButtons)
        {
          if(button.getTrackId() == pointerId)
          {
            button.onPointerUp(pointerId, pointerX, pointerY);
            if (mOverlayPointer != null && button.getId() == ButtonType.HOTKEYS_UPRIGHT_TOGGLE)
            {
              mOverlayPointer.reset();
            }
            isProcessed = true;
          }
        }

        for (InputOverlayDrawableDpad dpad : overlayDpads)
        {
          if (dpad.getTrackId() == pointerId)
          {
            dpad.onPointerUp(pointerId, pointerX, pointerY);
            isProcessed = true;
          }
        }
        break;
      }
    }

    if(isProcessed)
      invalidate();

    return true;
  }

  public boolean onTouchWhileEditing(MotionEvent event)
  {
    int pointerIndex = event.getActionIndex();
    int fingerPositionX = (int) event.getX(pointerIndex);
    int fingerPositionY = (int) event.getY(pointerIndex);

    // Maybe combine Button and Joystick as subclasses of the same parent?
    // Or maybe create an interface like IMoveableHUDControl?

    for (InputOverlayDrawableButton button : overlayButtons)
    {
      // Determine the button state to apply based on the MotionEvent action flag.
      switch (event.getAction() & MotionEvent.ACTION_MASK)
      {
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_POINTER_DOWN:
          // If no button is being moved now, remember the currently touched button to move.
          if (mButtonBeingConfigured == null &&
            button.getBounds().contains(fingerPositionX, fingerPositionY))
          {
            mButtonBeingConfigured = button;
            mButtonBeingConfigured.onConfigureTouch(event);
          }
          break;
        case MotionEvent.ACTION_MOVE:
          if (mButtonBeingConfigured != null)
          {
            mButtonBeingConfigured.onConfigureTouch(event);
            invalidate();
            return true;
          }
          break;

        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_POINTER_UP:
          if (mButtonBeingConfigured == button)
          {
            // Persist button position by saving new place.
            saveControlPosition(mButtonBeingConfigured.getId(), mButtonBeingConfigured.getBounds());
            mButtonBeingConfigured = null;
          }
          break;
      }
    }

    for (InputOverlayDrawableDpad dpad : overlayDpads)
    {
      // Determine the button state to apply based on the MotionEvent action flag.
      switch (event.getAction() & MotionEvent.ACTION_MASK)
      {
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_POINTER_DOWN:
          // If no button is being moved now, remember the currently touched button to move.
          if (mButtonBeingConfigured == null &&
            dpad.getBounds().contains(fingerPositionX, fingerPositionY))
          {
            mDpadBeingConfigured = dpad;
            mDpadBeingConfigured.onConfigureTouch(event);
          }
          break;
        case MotionEvent.ACTION_MOVE:
          if (mDpadBeingConfigured != null)
          {
            mDpadBeingConfigured.onConfigureTouch(event);
            invalidate();
            return true;
          }
          break;

        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_POINTER_UP:
          if (mDpadBeingConfigured == dpad)
          {
            // Persist button position by saving new place.
            saveControlPosition(mDpadBeingConfigured.getId(0), mDpadBeingConfigured.getBounds());
            mDpadBeingConfigured = null;
          }
          break;
      }
    }

    for (InputOverlayDrawableJoystick joystick : overlayJoysticks)
    {
      switch (event.getAction())
      {
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_POINTER_DOWN:
          if (mJoystickBeingConfigured == null &&
            joystick.getBounds().contains(fingerPositionX, fingerPositionY))
          {
            mJoystickBeingConfigured = joystick;
            mJoystickBeingConfigured.onConfigureTouch(event);
          }
          break;
        case MotionEvent.ACTION_MOVE:
          if (mJoystickBeingConfigured != null)
          {
            mJoystickBeingConfigured.onConfigureTouch(event);
            invalidate();
          }
          break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_POINTER_UP:
          if (mJoystickBeingConfigured != null)
          {
            saveControlPosition(mJoystickBeingConfigured.getId(),
              mJoystickBeingConfigured.getBounds());
            mJoystickBeingConfigured = null;
          }
          break;
      }
    }

    return true;
  }

  public void onSensorChanged(float[] rotation)
  {
    if(mOverlaySensor != null)
    {
      mOverlaySensor.onSensorChanged(rotation);
    }
  }

  public void onAccuracyChanged(int accuracy)
  {
    if(mOverlaySensor == null)
    {
      mOverlaySensor = new InputOverlaySensor();
    }
    mOverlaySensor.onAccuracyChanged(accuracy);
  }

  private void addGameCubeOverlayControls()
  {
    if (mPreferences.getBoolean("buttonToggleGc0", true))
    {
      overlayButtons.add(initializeOverlayButton(R.drawable.gcpad_a, R.drawable.gcpad_a_pressed,
        ButtonType.BUTTON_A));
    }
    if (mPreferences.getBoolean("buttonToggleGc1", true))
    {
      overlayButtons.add(initializeOverlayButton(R.drawable.gcpad_b, R.drawable.gcpad_b_pressed,
        ButtonType.BUTTON_B));
    }
    if (mPreferences.getBoolean("buttonToggleGc2", true))
    {
      overlayButtons.add(initializeOverlayButton(R.drawable.gcpad_x, R.drawable.gcpad_x_pressed,
        ButtonType.BUTTON_X));
    }
    if (mPreferences.getBoolean("buttonToggleGc3", true))
    {
      overlayButtons.add(initializeOverlayButton(R.drawable.gcpad_y, R.drawable.gcpad_y_pressed,
        ButtonType.BUTTON_Y));
    }
    if (mPreferences.getBoolean("buttonToggleGc4", true))
    {
      overlayButtons.add(initializeOverlayButton(R.drawable.gcpad_z, R.drawable.gcpad_z_pressed,
        ButtonType.BUTTON_Z));
    }
    if (mPreferences.getBoolean("buttonToggleGc5", true))
    {
      overlayButtons
        .add(initializeOverlayButton(R.drawable.gcpad_start, R.drawable.gcpad_start_pressed,
          ButtonType.BUTTON_START));
    }
    if (mPreferences.getBoolean("buttonToggleGc6", true))
    {
      overlayButtons.add(initializeOverlayButton(R.drawable.gcpad_l, R.drawable.gcpad_l_pressed,
        ButtonType.TRIGGER_L));
    }
    if (mPreferences.getBoolean("buttonToggleGc7", true))
    {
      overlayButtons.add(initializeOverlayButton(R.drawable.gcpad_r, R.drawable.gcpad_r_pressed,
        ButtonType.TRIGGER_R));
    }
    if (mPreferences.getBoolean("buttonToggleGc8", true))
    {
      overlayDpads.add(initializeOverlayDpad(R.drawable.gcwii_dpad,
        R.drawable.gcwii_dpad_pressed_one_direction,
        R.drawable.gcwii_dpad_pressed_two_directions,
        ButtonType.BUTTON_UP, ButtonType.BUTTON_DOWN,
        ButtonType.BUTTON_LEFT, ButtonType.BUTTON_RIGHT));
    }
    if (mPreferences.getBoolean("buttonToggleGc9", true))
    {
      overlayJoysticks.add(initializeOverlayJoystick(R.drawable.gcwii_joystick_range,
        R.drawable.gcwii_joystick, R.drawable.gcwii_joystick_pressed, ButtonType.STICK_MAIN));
    }
    if (mPreferences.getBoolean("buttonToggleGc10", true))
    {
      overlayJoysticks.add(initializeOverlayJoystick(R.drawable.gcwii_joystick_range,
        R.drawable.gcpad_c, R.drawable.gcpad_c_pressed, ButtonType.STICK_C));
    }
  }

  private void addWiimoteOverlayControls()
  {
    if (mPreferences.getBoolean("buttonToggleWii0", true))
    {
      overlayButtons.add(initializeOverlayButton(R.drawable.wiimote_a, R.drawable.wiimote_a_pressed,
        ButtonType.WIIMOTE_BUTTON_A));
    }
    if (mPreferences.getBoolean("buttonToggleWii1", true))
    {
      overlayButtons.add(initializeOverlayButton(R.drawable.wiimote_b, R.drawable.wiimote_b_pressed,
        ButtonType.WIIMOTE_BUTTON_B));
    }
    if (mPreferences.getBoolean("buttonToggleWii2", true))
    {
      overlayButtons
        .add(initializeOverlayButton(R.drawable.wiimote_one, R.drawable.wiimote_one_pressed,
          ButtonType.WIIMOTE_BUTTON_1));
    }
    if (mPreferences.getBoolean("buttonToggleWii3", true))
    {
      overlayButtons
        .add(initializeOverlayButton(R.drawable.wiimote_two, R.drawable.wiimote_two_pressed,
          ButtonType.WIIMOTE_BUTTON_2));
    }
    if (mPreferences.getBoolean("buttonToggleWii4", true))
    {
      overlayButtons
        .add(initializeOverlayButton(R.drawable.wiimote_plus, R.drawable.wiimote_plus_pressed,
          ButtonType.WIIMOTE_BUTTON_PLUS));
    }
    if (mPreferences.getBoolean("buttonToggleWii5", true))
    {
      overlayButtons.add(initializeOverlayButton(R.drawable.wiimote_minus,
        R.drawable.wiimote_minus_pressed, ButtonType.WIIMOTE_BUTTON_MINUS));
    }
    if (mPreferences.getBoolean("buttonToggleWii6", true))
    {
      overlayButtons
        .add(initializeOverlayButton(R.drawable.wiimote_home, R.drawable.wiimote_home_pressed,
          ButtonType.WIIMOTE_BUTTON_HOME));
    }
    if (mPreferences.getBoolean("buttonToggleWii7", true))
    {
      overlayButtons
        .add(initializeOverlayButton(R.drawable.classic_x, R.drawable.classic_x_pressed,
          ButtonType.HOTKEYS_UPRIGHT_TOGGLE));
    }
    if (mPreferences.getBoolean("buttonToggleWii8", true))
    {
      if (sControllerType == CONTROLLER_WIINUNCHUK)
      {
        overlayDpads.add(initializeOverlayDpad(R.drawable.gcwii_dpad,
          R.drawable.gcwii_dpad_pressed_one_direction,
          R.drawable.gcwii_dpad_pressed_two_directions,
          ButtonType.WIIMOTE_UP, ButtonType.WIIMOTE_DOWN,
          ButtonType.WIIMOTE_LEFT, ButtonType.WIIMOTE_RIGHT));
      }
      else
      {
        // Horizontal Wii Remote
        overlayDpads.add(initializeOverlayDpad(R.drawable.gcwii_dpad,
          R.drawable.gcwii_dpad_pressed_one_direction,
          R.drawable.gcwii_dpad_pressed_two_directions,
          ButtonType.WIIMOTE_RIGHT, ButtonType.WIIMOTE_LEFT,
          ButtonType.WIIMOTE_UP, ButtonType.WIIMOTE_DOWN));
      }
    }

    // joystick emulate
    if(sJoyStickSetting != JOYSTICK_EMULATE_NONE)
    {
      overlayJoysticks.add(initializeOverlayJoystick(R.drawable.gcwii_joystick_range,
        R.drawable.gcwii_joystick, R.drawable.gcwii_joystick_pressed, 0));
    }
  }

  private void addNunchukOverlayControls()
  {
    if (mPreferences.getBoolean("buttonToggleWii9", true))
    {
      overlayButtons.add(initializeOverlayButton(R.drawable.nunchuk_c, R.drawable.nunchuk_c_pressed,
        ButtonType.NUNCHUK_BUTTON_C));
    }
    if (mPreferences.getBoolean("buttonToggleWii10", true))
    {
      overlayButtons.add(initializeOverlayButton(R.drawable.nunchuk_z, R.drawable.nunchuk_z_pressed,
        ButtonType.NUNCHUK_BUTTON_Z));
    }
    if (mPreferences.getBoolean("buttonToggleWii11", true))
    {
      overlayJoysticks.add(initializeOverlayJoystick(R.drawable.gcwii_joystick_range,
        R.drawable.gcwii_joystick, R.drawable.gcwii_joystick_pressed,
        ButtonType.NUNCHUK_STICK));
    }
  }

  private void addClassicOverlayControls()
  {
    if (mPreferences.getBoolean("buttonToggleClassic0", true))
    {
      overlayButtons.add(initializeOverlayButton(R.drawable.classic_a, R.drawable.classic_a_pressed,
        ButtonType.CLASSIC_BUTTON_A));
    }
    if (mPreferences.getBoolean("buttonToggleClassic1", true))
    {
      overlayButtons.add(initializeOverlayButton(R.drawable.classic_b, R.drawable.classic_b_pressed,
        ButtonType.CLASSIC_BUTTON_B));
    }
    if (mPreferences.getBoolean("buttonToggleClassic2", true))
    {
      overlayButtons.add(initializeOverlayButton(R.drawable.classic_x, R.drawable.classic_x_pressed,
        ButtonType.CLASSIC_BUTTON_X));
    }
    if (mPreferences.getBoolean("buttonToggleClassic3", true))
    {
      overlayButtons.add(initializeOverlayButton(R.drawable.classic_y, R.drawable.classic_y_pressed,
        ButtonType.CLASSIC_BUTTON_Y));
    }
    if (mPreferences.getBoolean("buttonToggleClassic4", true))
    {
      overlayButtons
        .add(initializeOverlayButton(R.drawable.wiimote_one, R.drawable.wiimote_one_pressed,
          ButtonType.WIIMOTE_BUTTON_1));
    }
    if (mPreferences.getBoolean("buttonToggleClassic5", true))
    {
      overlayButtons
        .add(initializeOverlayButton(R.drawable.wiimote_two, R.drawable.wiimote_two_pressed,
          ButtonType.WIIMOTE_BUTTON_2));
    }
    if (mPreferences.getBoolean("buttonToggleClassic6", true))
    {
      overlayButtons
        .add(initializeOverlayButton(R.drawable.wiimote_plus, R.drawable.wiimote_plus_pressed,
          ButtonType.CLASSIC_BUTTON_PLUS));
    }
    if (mPreferences.getBoolean("buttonToggleClassic7", true))
    {
      overlayButtons.add(initializeOverlayButton(R.drawable.wiimote_minus,
        R.drawable.wiimote_minus_pressed, ButtonType.CLASSIC_BUTTON_MINUS));
    }
    if (mPreferences.getBoolean("buttonToggleClassic8", true))
    {
      overlayButtons
        .add(initializeOverlayButton(R.drawable.wiimote_home, R.drawable.wiimote_home_pressed,
          ButtonType.CLASSIC_BUTTON_HOME));
    }
    if (mPreferences.getBoolean("buttonToggleClassic9", true))
    {
      overlayButtons.add(initializeOverlayButton(R.drawable.classic_l, R.drawable.classic_l_pressed,
        ButtonType.CLASSIC_TRIGGER_L));
    }
    if (mPreferences.getBoolean("buttonToggleClassic10", true))
    {
      overlayButtons.add(initializeOverlayButton(R.drawable.classic_r, R.drawable.classic_r_pressed,
        ButtonType.CLASSIC_TRIGGER_R));
    }
    if (mPreferences.getBoolean("buttonToggleClassic11", true))
    {
      overlayButtons
        .add(initializeOverlayButton(R.drawable.classic_zl, R.drawable.classic_zl_pressed,
          ButtonType.CLASSIC_BUTTON_ZL));
    }
    if (mPreferences.getBoolean("buttonToggleClassic12", true))
    {
      overlayButtons
        .add(initializeOverlayButton(R.drawable.classic_zr, R.drawable.classic_zr_pressed,
          ButtonType.CLASSIC_BUTTON_ZR));
    }
    if (mPreferences.getBoolean("buttonToggleClassic13", true))
    {
      overlayDpads.add(initializeOverlayDpad(R.drawable.gcwii_dpad,
        R.drawable.gcwii_dpad_pressed_one_direction,
        R.drawable.gcwii_dpad_pressed_two_directions,
        ButtonType.CLASSIC_DPAD_UP, ButtonType.CLASSIC_DPAD_DOWN,
        ButtonType.CLASSIC_DPAD_LEFT, ButtonType.CLASSIC_DPAD_RIGHT));
    }
    if (mPreferences.getBoolean("buttonToggleClassic14", true))
    {
      overlayJoysticks.add(initializeOverlayJoystick(R.drawable.gcwii_joystick_range,
        R.drawable.gcwii_joystick, R.drawable.gcwii_joystick_pressed,
        ButtonType.CLASSIC_STICK_LEFT));
    }
    if (mPreferences.getBoolean("buttonToggleClassic15", true))
    {
      overlayJoysticks.add(initializeOverlayJoystick(R.drawable.gcwii_joystick_range,
        R.drawable.gcwii_joystick, R.drawable.gcwii_joystick_pressed,
        ButtonType.CLASSIC_STICK_RIGHT));
    }

    // joystick emulate
    if(sJoyStickSetting != JOYSTICK_EMULATE_NONE)
    {
      overlayJoysticks.add(initializeOverlayJoystick(R.drawable.gcwii_joystick_range,
        R.drawable.gcwii_joystick, R.drawable.gcwii_joystick_pressed, 0));
    }
  }

  public void refreshControls()
  {
    // Remove all the overlay buttons
    overlayButtons.clear();
    overlayDpads.clear();
    overlayJoysticks.clear();

    if(mPreferences.getBoolean("showInputOverlay", true))
    {
      if (EmulationActivity.get().isGameCubeGame() || sControllerType == CONTROLLER_GAMECUBE)
      {
        addGameCubeOverlayControls();
      }
      else if (sControllerType == CONTROLLER_CLASSIC)
      {
        addClassicOverlayControls();
      }
      else
      {
        addWiimoteOverlayControls();
        if (sControllerType == CONTROLLER_WIINUNCHUK)
        {
          addNunchukOverlayControls();
        }
      }
    }

    invalidate();
  }

  public void resetCurrentLayout()
  {
    SharedPreferences.Editor sPrefsEditor = mPreferences.edit();
    Resources res = getResources();

    switch (getControllerType())
    {
      case CONTROLLER_GAMECUBE:
        gcDefaultOverlay(sPrefsEditor, res);
        break;
      case CONTROLLER_CLASSIC:
        wiiClassicDefaultOverlay(sPrefsEditor, res);
        break;
      case CONTROLLER_WIINUNCHUK:
        wiiNunchukDefaultOverlay(sPrefsEditor, res);
        break;
      case CONTROLLER_WIIREMOTE:
        wiiRemoteDefaultOverlay(sPrefsEditor, res);
        break;
    }

    sPrefsEditor.apply();
    refreshControls();
  }

  public void setTouchPointer(int type)
  {
    if(type > 0)
    {
      if(mOverlayPointer == null)
      {
        final DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
        mOverlayPointer = new InputOverlayPointer(dm.widthPixels, dm.heightPixels,  dm.scaledDensity);
      }
      mOverlayPointer.setType(type);
    }
    else
    {
      mOverlayPointer = null;
    }
  }

  public void updateTouchPointer()
  {
    if(mOverlayPointer != null)
    {
      mOverlayPointer.updateTouchPointer();
    }
  }

  private void saveControlPosition(int buttonId, Rect bounds)
  {
    final Context context = getContext();
    final DisplayMetrics dm = context.getResources().getDisplayMetrics();
    final int controller = getControllerType();
    SharedPreferences.Editor sPrefsEditor = mPreferences.edit();
    float x = (bounds.left + (bounds.right - bounds.left) / 2.0f) / dm.widthPixels * 2.0f - 1.0f;
    float y = (bounds.top + (bounds.bottom - bounds.top) / 2.0f) / dm.heightPixels * 2.0f - 1.0f;
    sPrefsEditor.putFloat(controller + "_" + buttonId + "_X", x);
    sPrefsEditor.putFloat(controller + "_" + buttonId + "_Y", y);
    sPrefsEditor.apply();
  }

  private int getControllerType()
  {
    return EmulationActivity.get().isGameCubeGame() ? CONTROLLER_GAMECUBE : sControllerType;
  }

  /**
   * Initializes an InputOverlayDrawableButton, given by resId, with all of the
   * parameters set for it to be properly shown on the InputOverlay.
   * <p>
   * This works due to the way the X and Y coordinates are stored within
   * the {@link SharedPreferences}.
   * <p>
   * In the input overlay configuration menu,
   * once a touch event begins and then ends (ie. Organizing the buttons to one's own liking for the overlay).
   * the X and Y coordinates of the button at the END of its touch event
   * (when you remove your finger/stylus from the touchscreen) are then stored
   * within a SharedPreferences instance so that those values can be retrieved here.
   * <p>
   * This has a few benefits over the conventional way of storing the values
   * (ie. within the Dolphin ini file).
   * <ul>
   * <li>No native calls</li>
   * <li>Keeps Android-only values inside the Android environment</li>
   * </ul>
   * <p>
   * Technically no modifications should need to be performed on the returned
   * InputOverlayDrawableButton. Simply add it to the HashSet of overlay items and wait
   * for Android to call the onDraw method.
   *
   * @param defaultResId The resource ID of the {@link Drawable} to get the {@link Bitmap} of (Default State).
   * @param pressedResId The resource ID of the {@link Drawable} to get the {@link Bitmap} of (Pressed State).
   * @param buttonId     Identifier for determining what type of button the initialized InputOverlayDrawableButton represents.
   * @return An {@link InputOverlayDrawableButton} with the correct drawing bounds set.
   */
  private InputOverlayDrawableButton initializeOverlayButton(int defaultResId, int pressedResId,
    int buttonId)
  {
    final Context context = getContext();
    // Resources handle for fetching the initial Drawable resource.
    final Resources res = context.getResources();
    // SharedPreference to retrieve the X and Y coordinates for the InputOverlayDrawableButton.
    final int controller = getControllerType();

    // Decide scale based on button ID and user preference
    float scale;

    switch (buttonId)
    {
      case ButtonType.BUTTON_A:
      case ButtonType.WIIMOTE_BUTTON_B:
      case ButtonType.NUNCHUK_BUTTON_Z:
      case ButtonType.BUTTON_X:
      case ButtonType.BUTTON_Y:
        scale = 0.17f;
        break;
      case ButtonType.BUTTON_Z:
      case ButtonType.TRIGGER_L:
      case ButtonType.TRIGGER_R:
        scale = 0.18f;
        break;
      case ButtonType.BUTTON_START:
        scale = 0.075f;
        break;
      case ButtonType.WIIMOTE_BUTTON_1:
      case ButtonType.WIIMOTE_BUTTON_2:
      case ButtonType.WIIMOTE_BUTTON_PLUS:
      case ButtonType.WIIMOTE_BUTTON_MINUS:
        scale = 0.0725f;
        if(controller == CONTROLLER_WIIREMOTE)
          scale = 0.123f;
        break;
      case ButtonType.WIIMOTE_BUTTON_HOME:
      case ButtonType.CLASSIC_BUTTON_PLUS:
      case ButtonType.CLASSIC_BUTTON_MINUS:
      case ButtonType.CLASSIC_BUTTON_HOME:
        scale = 0.0725f;
        break;
      case ButtonType.HOTKEYS_UPRIGHT_TOGGLE:
        scale = 0.0675f;
        break;
      case ButtonType.CLASSIC_TRIGGER_L:
      case ButtonType.CLASSIC_TRIGGER_R:
        scale = 0.22f;
        break;
      case ButtonType.CLASSIC_BUTTON_ZL:
      case ButtonType.CLASSIC_BUTTON_ZR:
        scale = 0.18f;
        break;
      default:
        scale = 0.125f;
        break;
    }

    scale *= sControllerScale + 50;
    scale /= 100;

    // Initialize the InputOverlayDrawableButton.
    final Bitmap defaultStateBitmap =
      resizeBitmap(context, BitmapFactory.decodeResource(res, defaultResId), scale);
    final Bitmap pressedStateBitmap =
      resizeBitmap(context, BitmapFactory.decodeResource(res, pressedResId), scale);
    final InputOverlayDrawableButton overlayDrawable =
      new InputOverlayDrawableButton(res, defaultStateBitmap, pressedStateBitmap, buttonId);

    // The X and Y coordinates of the InputOverlayDrawableButton on the InputOverlay.
    // These were set in the input overlay configuration menu.
    float x = mPreferences.getFloat(controller + "_" + buttonId + "_X", 0f);
    float y = mPreferences.getFloat(controller + "_" + buttonId + "_Y", 0.5f);

    int width = overlayDrawable.getWidth();
    int height = overlayDrawable.getHeight();
    final DisplayMetrics dm = res.getDisplayMetrics();
    int drawableX = (int) ((dm.widthPixels / 2.0f) * (1.0f + x) - width / 2.0f);
    int drawableY = (int) ((dm.heightPixels / 2.0f) * (1.0f + y) - height / 2.0f);

    // Now set the bounds for the InputOverlayDrawableButton.
    // This will dictate where on the screen (and the what the size) the InputOverlayDrawableButton will be.
    overlayDrawable.setBounds(drawableX, drawableY, drawableX + width, drawableY + height);

    // Need to set the image's position
    overlayDrawable.setPosition(drawableX, drawableY);
    overlayDrawable.setAlpha((sControllerAlpha * 255) / 100);

    return overlayDrawable;
  }

  /**
   * Initializes an {@link InputOverlayDrawableDpad}
   *
   * @param defaultResId              The {@link Bitmap} resource ID of the default sate.
   * @param pressedOneDirectionResId  The {@link Bitmap} resource ID of the pressed sate in one direction.
   * @param pressedTwoDirectionsResId The {@link Bitmap} resource ID of the pressed sate in two directions.
   * @param buttonUp                  Identifier for the up button.
   * @param buttonDown                Identifier for the down button.
   * @param buttonLeft                Identifier for the left button.
   * @param buttonRight               Identifier for the right button.
   * @return the initialized {@link InputOverlayDrawableDpad}
   */
  private InputOverlayDrawableDpad initializeOverlayDpad(int defaultResId,
    int pressedOneDirectionResId,
    int pressedTwoDirectionsResId,
    int buttonUp,
    int buttonDown,
    int buttonLeft,
    int buttonRight)
  {
    final Context context = getContext();
    // Resources handle for fetching the initial Drawable resource.
    final Resources res = context.getResources();
    // SharedPreference to retrieve the X and Y coordinates for the InputOverlayDrawableDpad.
    final int controller = getControllerType();

    // Decide scale based on button ID and user preference
    float scale;

    switch (buttonUp)
    {
      case ButtonType.BUTTON_UP:
        scale = 0.2375f;
        break;
      case ButtonType.CLASSIC_DPAD_UP:
        scale = 0.275f;
        break;
      default:
        scale = 0.2125f;
        break;
    }

    scale *= sControllerScale + 50;
    scale /= 100;

    if(controller == CONTROLLER_WIIREMOTE)
      scale *= 1.4f;

    // Initialize the InputOverlayDrawableDpad.
    final Bitmap defaultStateBitmap =
      resizeBitmap(context, BitmapFactory.decodeResource(res, defaultResId), scale);
    final Bitmap pressedOneDirectionStateBitmap =
      resizeBitmap(context, BitmapFactory.decodeResource(res, pressedOneDirectionResId),
        scale);
    final Bitmap pressedTwoDirectionsStateBitmap =
      resizeBitmap(context, BitmapFactory.decodeResource(res, pressedTwoDirectionsResId),
        scale);
    final InputOverlayDrawableDpad overlayDrawable =
      new InputOverlayDrawableDpad(res, defaultStateBitmap,
        pressedOneDirectionStateBitmap, pressedTwoDirectionsStateBitmap,
        buttonUp, buttonDown, buttonLeft, buttonRight);

    // The X and Y coordinates of the InputOverlayDrawableDpad on the InputOverlay.
    // These were set in the input overlay configuration menu.
    float x = mPreferences.getFloat(controller + "_" + buttonUp + "_X", 0f);
    float y = mPreferences.getFloat(controller + "_" + buttonUp + "_Y", 0.5f);

    int width = overlayDrawable.getWidth();
    int height = overlayDrawable.getHeight();
    final DisplayMetrics dm = res.getDisplayMetrics();
    int drawableX = (int) ((dm.widthPixels / 2.0f) * (1.0f + x) - width / 2.0f);
    int drawableY = (int) ((dm.heightPixels / 2.0f) * (1.0f + y) - height / 2.0f);

    // Now set the bounds for the InputOverlayDrawableDpad.
    // This will dictate where on the screen (and the what the size) the InputOverlayDrawableDpad will be.
    overlayDrawable.setBounds(drawableX, drawableY, drawableX + width, drawableY + height);

    // Need to set the image's position
    overlayDrawable.setPosition(drawableX, drawableY);
    overlayDrawable.setAlpha((sControllerAlpha * 255) / 100);

    return overlayDrawable;
  }

  /**
   * Initializes an {@link InputOverlayDrawableJoystick}
   *
   * @param resOuter        Resource ID for the outer image of the joystick (the static image that shows the circular bounds).
   * @param defaultResInner Resource ID for the default inner image of the joystick (the one you actually move around).
   * @param pressedResInner Resource ID for the pressed inner image of the joystick.
   * @param joystick        Identifier for which joystick this is.
   * @return the initialized {@link InputOverlayDrawableJoystick}.
   */
  private InputOverlayDrawableJoystick initializeOverlayJoystick(int resOuter, int defaultResInner,
    int pressedResInner, int joystick)
  {
    final Context context = getContext();
    // Resources handle for fetching the initial Drawable resource.
    final Resources res = context.getResources();

    // SharedPreference to retrieve the X and Y coordinates for the InputOverlayDrawableJoystick.
    final int controller = getControllerType();

    // Decide scale based on user preference
    float scale = 0.275f;
    scale *= sControllerScale + 50;
    scale /= 100;

    // Initialize the InputOverlayDrawableJoystick.
    final Bitmap bitmapOuter =
      resizeBitmap(context, BitmapFactory.decodeResource(res, resOuter), scale);
    final Bitmap bitmapInnerDefault = BitmapFactory.decodeResource(res, defaultResInner);
    final Bitmap bitmapInnerPressed = BitmapFactory.decodeResource(res, pressedResInner);

    // The X and Y coordinates of the InputOverlayDrawableButton on the InputOverlay.
    // These were set in the input overlay configuration menu.
    float x = mPreferences.getFloat(controller + "_" + joystick + "_X", -0.3f);
    float y = mPreferences.getFloat(controller + "_" + joystick + "_Y", 0.3f);

    // Decide inner scale based on joystick ID
    float innerScale;

    switch (joystick)
    {
      case ButtonType.STICK_C:
        innerScale = 1.833f;
        break;
      default:
        innerScale = 1.375f;
        break;
    }

    // Now set the bounds for the InputOverlayDrawableJoystick.
    // This will dictate where on the screen (and the what the size) the InputOverlayDrawableJoystick will be.
    int outerSize = bitmapOuter.getWidth();
    final DisplayMetrics dm = res.getDisplayMetrics();
    int drawableX = (int) ((dm.widthPixels / 2.0f) * (1.0f + x) - outerSize / 2.0f);
    int drawableY = (int) ((dm.heightPixels / 2.0f) * (1.0f + y) - outerSize / 2.0f);

    Rect outerRect = new Rect(drawableX, drawableY, drawableX + outerSize, drawableY + outerSize);
    Rect innerRect = new Rect(0, 0, (int) (outerSize / innerScale), (int) (outerSize / innerScale));

    // Send the drawableId to the joystick so it can be referenced when saving control position.
    final InputOverlayDrawableJoystick overlayDrawable = new InputOverlayDrawableJoystick(
      res, bitmapOuter, bitmapInnerDefault, bitmapInnerPressed, outerRect, innerRect,
      joystick);

    // Need to set the image's position
    overlayDrawable.setPosition(drawableX, drawableY);
    overlayDrawable.setAlpha((sControllerAlpha * 255) / 100);

    return overlayDrawable;
  }

  public void setIsInEditMode(boolean isInEditMode)
  {
    mIsInEditMode = isInEditMode;
  }

  public boolean isInEditMode()
  {
    return mIsInEditMode;
  }

  private void defaultOverlay()
  {
    SharedPreferences.Editor sPrefsEditor = mPreferences.edit();
    Resources res = getResources();

    // GameCube
    gcDefaultOverlay(sPrefsEditor, res);

    // Wii Nunchuk
    wiiNunchukDefaultOverlay(sPrefsEditor, res);

    // Wii Remote
    wiiRemoteDefaultOverlay(sPrefsEditor, res);

    // Wii Classic
    wiiClassicDefaultOverlay(sPrefsEditor, res);

    sPrefsEditor.putBoolean(CONTROL_INIT_PREF_KEY, true);
    sPrefsEditor.apply();
  }

  private void gcDefaultOverlay(SharedPreferences.Editor sPrefsEditor, Resources res)
  {
    final int controller = CONTROLLER_GAMECUBE;
    // Each value is a percent from max X/Y stored as an int. Have to bring that value down
    // to a decimal before multiplying by MAX X/Y.
    sPrefsEditor.putFloat(controller + "_" + ButtonType.BUTTON_A + "_X",
      res.getInteger(R.integer.BUTTON_A_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.BUTTON_A + "_Y",
      res.getInteger(R.integer.BUTTON_A_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.BUTTON_B + "_X",
      res.getInteger(R.integer.BUTTON_B_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.BUTTON_B + "_Y",
      res.getInteger(R.integer.BUTTON_B_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.BUTTON_X + "_X",
      res.getInteger(R.integer.BUTTON_X_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.BUTTON_X + "_Y",
      res.getInteger(R.integer.BUTTON_X_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.BUTTON_Y + "_X",
      res.getInteger(R.integer.BUTTON_Y_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.BUTTON_Y + "_Y",
      res.getInteger(R.integer.BUTTON_Y_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.BUTTON_Z + "_X",
      res.getInteger(R.integer.BUTTON_Z_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.BUTTON_Z + "_Y",
      res.getInteger(R.integer.BUTTON_Z_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.BUTTON_UP + "_X",
      res.getInteger(R.integer.BUTTON_UP_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.BUTTON_UP + "_Y",
      res.getInteger(R.integer.BUTTON_UP_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.TRIGGER_L + "_X",
      res.getInteger(R.integer.TRIGGER_L_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.TRIGGER_L + "_Y",
      res.getInteger(R.integer.TRIGGER_L_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.TRIGGER_R + "_X",
      res.getInteger(R.integer.TRIGGER_R_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.TRIGGER_R + "_Y",
      res.getInteger(R.integer.TRIGGER_R_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.BUTTON_START + "_X",
      res.getInteger(R.integer.BUTTON_START_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.BUTTON_START + "_Y",
      res.getInteger(R.integer.BUTTON_START_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.STICK_C + "_X",
      res.getInteger(R.integer.STICK_C_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.STICK_C + "_Y",
      res.getInteger(R.integer.STICK_C_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.STICK_MAIN + "_X",
      res.getInteger(R.integer.STICK_MAIN_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.STICK_MAIN + "_Y",
      res.getInteger(R.integer.STICK_MAIN_Y) / 100.0f);
  }

  private void wiiNunchukDefaultOverlay(SharedPreferences.Editor sPrefsEditor, Resources res)
  {
    final int controller = CONTROLLER_WIINUNCHUK;
    // Each value is a percent from max X/Y stored as an int. Have to bring that value down
    // to a decimal before multiplying by MAX X/Y.
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_A + "_X",
      res.getInteger(R.integer.WIIMOTE_BUTTON_A_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_A + "_Y",
      res.getInteger(R.integer.WIIMOTE_BUTTON_A_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_B + "_X",
      res.getInteger(R.integer.WIIMOTE_BUTTON_B_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_B + "_Y",
      res.getInteger(R.integer.WIIMOTE_BUTTON_B_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_1 + "_X",
      res.getInteger(R.integer.WIIMOTE_BUTTON_1_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_1 + "_Y",
      res.getInteger(R.integer.WIIMOTE_BUTTON_1_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_2 + "_X",
      res.getInteger(R.integer.WIIMOTE_BUTTON_2_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_2 + "_Y",
      res.getInteger(R.integer.WIIMOTE_BUTTON_2_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.NUNCHUK_BUTTON_Z + "_X",
      res.getInteger(R.integer.NUNCHUK_BUTTON_Z_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.NUNCHUK_BUTTON_Z + "_Y",
      res.getInteger(R.integer.NUNCHUK_BUTTON_Z_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.NUNCHUK_BUTTON_C + "_X",
      res.getInteger(R.integer.NUNCHUK_BUTTON_C_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.NUNCHUK_BUTTON_C + "_Y",
      res.getInteger(R.integer.NUNCHUK_BUTTON_C_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_MINUS + "_X",
      res.getInteger(R.integer.WIIMOTE_BUTTON_MINUS_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_MINUS + "_Y",
      res.getInteger(R.integer.WIIMOTE_BUTTON_MINUS_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_PLUS + "_X",
      res.getInteger(R.integer.WIIMOTE_BUTTON_PLUS_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_PLUS + "_Y",
      res.getInteger(R.integer.WIIMOTE_BUTTON_PLUS_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_UP + "_X",
      res.getInteger(R.integer.WIIMOTE_UP_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_UP + "_Y",
      res.getInteger(R.integer.WIIMOTE_UP_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_HOME + "_X",
      res.getInteger(R.integer.WIIMOTE_BUTTON_HOME_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_HOME + "_Y",
      res.getInteger(R.integer.WIIMOTE_BUTTON_HOME_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.NUNCHUK_STICK + "_X",
      res.getInteger(R.integer.NUNCHUK_STICK_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.NUNCHUK_STICK + "_Y",
      res.getInteger(R.integer.NUNCHUK_STICK_Y) / 100.0f);
    // Horizontal dpad
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_RIGHT + "_X",
      res.getInteger(R.integer.WIIMOTE_RIGHT_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_RIGHT + "_Y",
      res.getInteger(R.integer.WIIMOTE_RIGHT_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.HOTKEYS_UPRIGHT_TOGGLE + "_X",
      res.getInteger(R.integer.WIIMOTE_BUTTON_UPRIGHT_TOGGLE_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.HOTKEYS_UPRIGHT_TOGGLE + "_Y",
      res.getInteger(R.integer.WIIMOTE_BUTTON_UPRIGHT_TOGGLE_Y) / 100.0f);
  }

  private void wiiRemoteDefaultOverlay(SharedPreferences.Editor sPrefsEditor, Resources res)
  {
    final int controller = CONTROLLER_WIIREMOTE;
    // Each value is a percent from max X/Y stored as an int. Have to bring that value down
    // to a decimal before multiplying by MAX X/Y.
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_A + "_X",
      res.getInteger(R.integer.WIIMOTE_BUTTON_A_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_A + "_Y",
      res.getInteger(R.integer.WIIMOTE_BUTTON_A_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_B + "_X",
      res.getInteger(R.integer.WIIMOTE_BUTTON_B_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_B + "_Y",
      res.getInteger(R.integer.WIIMOTE_BUTTON_B_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_1 + "_X",
      res.getInteger(R.integer.WIIMOTE_BUTTON_1_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_1 + "_Y",
      res.getInteger(R.integer.WIIMOTE_BUTTON_1_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_2 + "_X",
      res.getInteger(R.integer.WIIMOTE_BUTTON_2_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_2 + "_Y",
      res.getInteger(R.integer.WIIMOTE_BUTTON_2_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_MINUS + "_X",
      res.getInteger(R.integer.WIIMOTE_BUTTON_MINUS_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_MINUS + "_Y",
      res.getInteger(R.integer.WIIMOTE_BUTTON_MINUS_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_PLUS + "_X",
      res.getInteger(R.integer.WIIMOTE_BUTTON_PLUS_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_PLUS + "_Y",
      res.getInteger(R.integer.WIIMOTE_BUTTON_PLUS_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_HOME + "_X",
      res.getInteger(R.integer.WIIMOTE_BUTTON_HOME_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_HOME + "_Y",
      res.getInteger(R.integer.WIIMOTE_BUTTON_HOME_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_RIGHT + "_X",
      res.getInteger(R.integer.WIIMOTE_RIGHT_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_RIGHT + "_Y",
      res.getInteger(R.integer.WIIMOTE_RIGHT_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.HOTKEYS_UPRIGHT_TOGGLE + "_X",
      res.getInteger(R.integer.WIIMOTE_BUTTON_UPRIGHT_TOGGLE_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.HOTKEYS_UPRIGHT_TOGGLE + "_Y",
      res.getInteger(R.integer.WIIMOTE_BUTTON_UPRIGHT_TOGGLE_Y) / 100.0f);
  }

  private void wiiClassicDefaultOverlay(SharedPreferences.Editor sPrefsEditor, Resources res)
  {
    final int controller = CONTROLLER_CLASSIC;
    // Each value is a percent from max X/Y stored as an int. Have to bring that value down
    // to a decimal before multiplying by MAX X/Y.
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_BUTTON_A + "_X",
      res.getInteger(R.integer.CLASSIC_BUTTON_A_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_BUTTON_A + "_Y",
      res.getInteger(R.integer.CLASSIC_BUTTON_A_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_BUTTON_B + "_X",
      res.getInteger(R.integer.CLASSIC_BUTTON_B_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_BUTTON_B + "_Y",
      res.getInteger(R.integer.CLASSIC_BUTTON_B_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_BUTTON_X + "_X",
      res.getInteger(R.integer.CLASSIC_BUTTON_X_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_BUTTON_X + "_Y",
      res.getInteger(R.integer.CLASSIC_BUTTON_X_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_BUTTON_Y + "_X",
      res.getInteger(R.integer.CLASSIC_BUTTON_Y_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_BUTTON_Y + "_Y",
      res.getInteger(R.integer.CLASSIC_BUTTON_Y_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_1 + "_X",
      res.getInteger(R.integer.CLASSIC_BUTTON_1_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_1 + "_Y",
      res.getInteger(R.integer.CLASSIC_BUTTON_1_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_2 + "_X",
      res.getInteger(R.integer.CLASSIC_BUTTON_2_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.WIIMOTE_BUTTON_2 + "_Y",
      res.getInteger(R.integer.CLASSIC_BUTTON_2_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_BUTTON_MINUS + "_X",
      res.getInteger(R.integer.CLASSIC_BUTTON_MINUS_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_BUTTON_MINUS + "_Y",
      res.getInteger(R.integer.CLASSIC_BUTTON_MINUS_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_BUTTON_PLUS + "_X",
      res.getInteger(R.integer.CLASSIC_BUTTON_PLUS_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_BUTTON_PLUS + "_Y",
      res.getInteger(R.integer.CLASSIC_BUTTON_PLUS_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_BUTTON_HOME + "_X",
      res.getInteger(R.integer.CLASSIC_BUTTON_HOME_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_BUTTON_HOME + "_Y",
      res.getInteger(R.integer.CLASSIC_BUTTON_HOME_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_BUTTON_ZL + "_X",
      res.getInteger(R.integer.CLASSIC_BUTTON_ZL_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_BUTTON_ZL + "_Y",
      res.getInteger(R.integer.CLASSIC_BUTTON_ZL_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_BUTTON_ZR + "_X",
      res.getInteger(R.integer.CLASSIC_BUTTON_ZR_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_BUTTON_ZR + "_Y",
      res.getInteger(R.integer.CLASSIC_BUTTON_ZR_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_DPAD_UP + "_X",
      res.getInteger(R.integer.CLASSIC_DPAD_UP_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_DPAD_UP + "_Y",
      res.getInteger(R.integer.CLASSIC_DPAD_UP_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_STICK_LEFT + "_X",
      res.getInteger(R.integer.CLASSIC_STICK_LEFT_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_STICK_LEFT + "_Y",
      res.getInteger(R.integer.CLASSIC_STICK_LEFT_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_STICK_RIGHT + "_X",
      res.getInteger(R.integer.CLASSIC_STICK_RIGHT_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_STICK_RIGHT + "_Y",
      res.getInteger(R.integer.CLASSIC_STICK_RIGHT_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_TRIGGER_L + "_X",
      res.getInteger(R.integer.CLASSIC_TRIGGER_L_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_TRIGGER_L + "_Y",
      res.getInteger(R.integer.CLASSIC_TRIGGER_L_Y) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_TRIGGER_R + "_X",
      res.getInteger(R.integer.CLASSIC_TRIGGER_R_X) / 100.0f);
    sPrefsEditor.putFloat(controller + "_" + ButtonType.CLASSIC_TRIGGER_R + "_Y",
      res.getInteger(R.integer.CLASSIC_TRIGGER_R_Y) / 100.0f);
  }
}
