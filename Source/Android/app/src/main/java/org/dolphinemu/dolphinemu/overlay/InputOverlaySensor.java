package org.dolphinemu.dolphinemu.overlay;

import org.dolphinemu.dolphinemu.NativeLibrary;
import org.dolphinemu.dolphinemu.activities.EmulationActivity;

public class InputOverlaySensor
{
  private boolean mAccuracyChanged;
  private float mBaseYaw;
  private float mBasePitch;
  private float mBaseRoll;

  public InputOverlaySensor()
  {
    mAccuracyChanged = false;
  }

  public void onSensorChanged(float[] rotation)
  {
    // portrait:  yaw(0) - pitch(1) - roll(2)
    // landscape: yaw(0) - pitch(2) - roll(1)
    if(mAccuracyChanged)
    {
      if(Math.abs(mBaseYaw - rotation[0]) > 0.1f ||
        Math.abs(mBasePitch - rotation[2]) > 0.1f ||
        Math.abs(mBaseRoll - rotation[1]) > 0.1f)
      {
        mBaseYaw = rotation[0];
        mBasePitch = rotation[2];
        mBaseRoll = rotation[1];
        return;
      }
      mAccuracyChanged = false;
    }

    float z = mBaseYaw - rotation[0];
    float y = mBasePitch - rotation[2];
    float x = mBaseRoll - rotation[1];
    int[] axisIDs = new int[4];
    float[] axises = new float[4];

    z = z * (1 + Math.abs(z));
    y = y * (1 + Math.abs(y));
    x = x * (1 + Math.abs(x));

    if(EmulationActivity.isGameCubeGame())
    {
      switch (InputOverlay.sSensorGCSetting)
      {
        case InputOverlay.SENSOR_GC_JOYSTICK:
          axisIDs[0] = NativeLibrary.ButtonType.STICK_MAIN + 1;
          axisIDs[1] = NativeLibrary.ButtonType.STICK_MAIN + 2;
          axisIDs[2] = NativeLibrary.ButtonType.STICK_MAIN + 3;
          axisIDs[3] = NativeLibrary.ButtonType.STICK_MAIN + 4;
          axises[0] = y; // up
          axises[1] = y; // down
          axises[2] = x; // left
          axises[3] = x; // right
          break;
        case InputOverlay.SENSOR_GC_CSTICK:
          axisIDs[0] = NativeLibrary.ButtonType.STICK_C + 1;
          axisIDs[1] = NativeLibrary.ButtonType.STICK_C + 2;
          axisIDs[2] = NativeLibrary.ButtonType.STICK_C + 3;
          axisIDs[3] = NativeLibrary.ButtonType.STICK_C + 4;
          axises[0] = y; // up
          axises[1] = y; // down
          axises[2] = x; // left
          axises[3] = x; // right
          break;
        case InputOverlay.SENSOR_GC_DPAD:
          axisIDs[0] = NativeLibrary.ButtonType.BUTTON_UP;
          axisIDs[1] = NativeLibrary.ButtonType.BUTTON_DOWN;
          axisIDs[2] = NativeLibrary.ButtonType.BUTTON_LEFT;
          axisIDs[3] = NativeLibrary.ButtonType.BUTTON_RIGHT;
          axises[0] = y; // up
          axises[1] = y; // down
          axises[2] = x; // left
          axises[3] = x; // right
          break;
      }
    }
    else
    {
      switch (InputOverlay.sSensorWiiSetting)
      {
        case InputOverlay.SENSOR_WII_DPAD:
          axisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_UP;
          axisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_DOWN;
          axisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_LEFT;
          axisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_RIGHT;
          axises[0] = y; // up
          axises[1] = y; // down
          axises[2] = x; // left
          axises[3] = x; // right
          break;
        case InputOverlay.SENSOR_WII_STICK:
          if(InputOverlay.sControllerType == InputOverlay.COCONTROLLER_CLASSIC)
          {
            axisIDs[0] = NativeLibrary.ButtonType.CLASSIC_STICK_LEFT_UP;
            axisIDs[1] = NativeLibrary.ButtonType.CLASSIC_STICK_LEFT_DOWN;
            axisIDs[2] = NativeLibrary.ButtonType.CLASSIC_STICK_LEFT_LEFT;
            axisIDs[3] = NativeLibrary.ButtonType.CLASSIC_STICK_LEFT_RIGHT;
          }
          else
          {
            axisIDs[0] = NativeLibrary.ButtonType.NUNCHUK_STICK + 1;
            axisIDs[1] = NativeLibrary.ButtonType.NUNCHUK_STICK + 2;
            axisIDs[2] = NativeLibrary.ButtonType.NUNCHUK_STICK + 3;
            axisIDs[3] = NativeLibrary.ButtonType.NUNCHUK_STICK + 4;
          }
          axises[0] = y; // up
          axises[1] = y; // down
          axises[2] = x; // left
          axises[3] = x; // right
          break;
        case InputOverlay.SENSOR_WII_IR:
          axisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_IR + 1;
          axisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_IR + 2;
          axisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_IR + 3;
          axisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_IR + 4;
          axises[0] = y; // up
          axises[1] = y; // down
          axises[2] = x; // left
          axises[3] = x; // right
          break;
        case InputOverlay.SENSOR_WII_SWING:
          axisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_SWING + 1;
          axisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_SWING + 2;
          axisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_SWING + 3;
          axisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_SWING + 4;
          axises[0] = y; // up
          axises[1] = y; // down
          axises[2] = x; // left
          axises[3] = x; // right
          break;
        case InputOverlay.SENSOR_WII_TILT:
          if(InputOverlay.sControllerType == InputOverlay.CONTROLLER_WIINUNCHUK)
          {
            axisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_TILT + 1; // up
            axisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_TILT + 2; // down
            axisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_TILT + 3; // left
            axisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_TILT + 4; // right
          }
          else
          {
            axisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_TILT + 4; // right
            axisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_TILT + 3; // left
            axisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_TILT + 1; // up
            axisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_TILT + 2; // down
          }
          axises[0] = y / 2.0f;
          axises[1] = y / 2.0f;
          axises[2] = x / 2.0f;
          axises[3] = x / 2.0f;
          break;
        case InputOverlay.SENSOR_WII_SHAKE:
          axises[0] = -x;
          axises[1] = x;
          axises[2] = -y;
          axises[3] = y;
          handleShakeEvent(axises);
          return;
      }
    }

    for (int i = 0; i < 4; i++)
    {
      NativeLibrary.onGamePadMoveEvent(NativeLibrary.TouchScreenDevice, axisIDs[i], axises[i]);
    }
  }

  // axis to button
  private void handleShakeEvent(float[] axises)
  {
    int[] axisIDs = new int[4];
    axisIDs[0] = 0;
    axisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_X;
    axisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_Y;
    axisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_Z;

    for(int i = 0; i < axises.length; ++i)
    {
      if(axises[i] > 0.15f)
      {
        if(InputOverlay.sShakeStates[i] != NativeLibrary.ButtonState.PRESSED)
        {
          InputOverlay.sShakeStates[i] = NativeLibrary.ButtonState.PRESSED;
          NativeLibrary.onGamePadEvent(NativeLibrary.TouchScreenDevice, axisIDs[i],
            NativeLibrary.ButtonState.PRESSED);
        }
      }
      else if(InputOverlay.sShakeStates[i] != NativeLibrary.ButtonState.RELEASED)
      {
        InputOverlay.sShakeStates[i] = NativeLibrary.ButtonState.RELEASED;
        NativeLibrary.onGamePadEvent(NativeLibrary.TouchScreenDevice, axisIDs[i],
          NativeLibrary.ButtonState.RELEASED);
      }
    }
  }

  public void onAccuracyChanged(int accuracy)
  {
    mAccuracyChanged = true;
    mBaseYaw = (float)Math.PI;
    mBasePitch = (float)Math.PI;
    mBaseRoll = (float)Math.PI;
  }
}
