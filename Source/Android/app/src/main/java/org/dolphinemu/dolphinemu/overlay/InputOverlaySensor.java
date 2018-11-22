package org.dolphinemu.dolphinemu.overlay;

import org.dolphinemu.dolphinemu.NativeLibrary;
import org.dolphinemu.dolphinemu.activities.EmulationActivity;

public class InputOverlaySensor
{
  private final int[] mAxisIDs = {0, 0, 0, 0};
  private boolean mAccuracyChanged;
  private float mBaseYaw;
  private float mBasePitch;
  private float mBaseRoll;

  public InputOverlaySensor()
  {
    mAccuracyChanged = false;
  }

  public void setAxisIDs()
  {
    if(EmulationActivity.isGameCubeGame())
    {
      switch (InputOverlay.sSensorGCSetting)
      {
        case InputOverlay.SENSOR_GC_JOYSTICK:
          mAxisIDs[0] = NativeLibrary.ButtonType.STICK_MAIN + 1;
          mAxisIDs[1] = NativeLibrary.ButtonType.STICK_MAIN + 2;
          mAxisIDs[2] = NativeLibrary.ButtonType.STICK_MAIN + 3;
          mAxisIDs[3] = NativeLibrary.ButtonType.STICK_MAIN + 4;
          break;
        case InputOverlay.SENSOR_GC_CSTICK:
          mAxisIDs[0] = NativeLibrary.ButtonType.STICK_C + 1;
          mAxisIDs[1] = NativeLibrary.ButtonType.STICK_C + 2;
          mAxisIDs[2] = NativeLibrary.ButtonType.STICK_C + 3;
          mAxisIDs[3] = NativeLibrary.ButtonType.STICK_C + 4;
          break;
        case InputOverlay.SENSOR_GC_DPAD:
          mAxisIDs[0] = NativeLibrary.ButtonType.BUTTON_UP;
          mAxisIDs[1] = NativeLibrary.ButtonType.BUTTON_DOWN;
          mAxisIDs[2] = NativeLibrary.ButtonType.BUTTON_LEFT;
          mAxisIDs[3] = NativeLibrary.ButtonType.BUTTON_RIGHT;
          break;
      }
    }
    else
    {
      switch (InputOverlay.sSensorWiiSetting)
      {
        case InputOverlay.SENSOR_WII_DPAD:
          mAxisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_UP;
          mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_DOWN;
          mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_LEFT;
          mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_RIGHT;
          break;
        case InputOverlay.SENSOR_WII_STICK:
          if(InputOverlay.sControllerType == InputOverlay.COCONTROLLER_CLASSIC)
          {
            mAxisIDs[0] = NativeLibrary.ButtonType.CLASSIC_STICK_LEFT_UP;
            mAxisIDs[1] = NativeLibrary.ButtonType.CLASSIC_STICK_LEFT_DOWN;
            mAxisIDs[2] = NativeLibrary.ButtonType.CLASSIC_STICK_LEFT_LEFT;
            mAxisIDs[3] = NativeLibrary.ButtonType.CLASSIC_STICK_LEFT_RIGHT;
          }
          else
          {
            mAxisIDs[0] = NativeLibrary.ButtonType.NUNCHUK_STICK + 1;
            mAxisIDs[1] = NativeLibrary.ButtonType.NUNCHUK_STICK + 2;
            mAxisIDs[2] = NativeLibrary.ButtonType.NUNCHUK_STICK + 3;
            mAxisIDs[3] = NativeLibrary.ButtonType.NUNCHUK_STICK + 4;
          }

          break;
        case InputOverlay.SENSOR_WII_IR:
          mAxisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_IR + 1;
          mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_IR + 2;
          mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_IR + 3;
          mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_IR + 4;
          break;
        case InputOverlay.SENSOR_WII_SWING:
          mAxisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_SWING + 1;
          mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_SWING + 2;
          mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_SWING + 3;
          mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_SWING + 4;
          break;
        case InputOverlay.SENSOR_WII_TILT:
          if(InputOverlay.sControllerType == InputOverlay.CONTROLLER_WIINUNCHUK)
          {
            mAxisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_TILT + 1; // up
            mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_TILT + 2; // down
            mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_TILT + 3; // left
            mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_TILT + 4; // right
          }
          else
          {
            mAxisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_TILT + 4; // right
            mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_TILT + 3; // left
            mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_TILT + 1; // up
            mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_TILT + 2; // down
          }
          break;
        case InputOverlay.SENSOR_WII_SHAKE:
          mAxisIDs[0] = 0;
          mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_X;
          mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_Y;
          mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_Z;
      }
    }
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
    float[] axises = new float[4];

    z = z * (1 + Math.abs(z));
    y = y * (1 + Math.abs(y));
    x = x * (1 + Math.abs(x));

    axises[0] = y; // up
    axises[1] = y; // down
    axises[2] = x; // left
    axises[3] = x; // right

    if(!EmulationActivity.isGameCubeGame())
    {
      if(InputOverlay.SENSOR_WII_TILT == InputOverlay.sSensorWiiSetting)
      {
        axises[0] = y / 2.0f;
        axises[1] = y / 2.0f;
        axises[2] = x / 2.0f;
        axises[3] = x / 2.0f;
      }
      else if(InputOverlay.SENSOR_WII_SHAKE == InputOverlay.sSensorWiiSetting)
      {
        axises[0] = -x;
        axises[1] = x;
        axises[2] = -y;
        axises[3] = y;
        handleShakeEvent(axises);
        return;
      }
    }

    for (int i = 0; i < mAxisIDs.length; i++)
    {
      NativeLibrary.onGamePadMoveEvent(NativeLibrary.TouchScreenDevice, mAxisIDs[i], axises[i]);
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
    setAxisIDs();
    mAccuracyChanged = true;
    mBaseYaw = (float)Math.PI;
    mBasePitch = (float)Math.PI;
    mBaseRoll = (float)Math.PI;
  }
}
