/**
 * Copyright 2013 Dolphin Emulator Project
 * Licensed under GPLv2+
 * Refer to the license.txt file included.
 */

package org.dolphinemu.dolphinemu.overlay;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.view.MotionEvent;

import org.dolphinemu.dolphinemu.NativeLibrary;

/**
 * Custom {@link BitmapDrawable} that is capable
 * of storing it's own ID.
 */
public final class InputOverlayDrawableJoystick
{
  private final int[] mAxisIDs = {0, 0, 0, 0};
  private final float[] mAxises = {0f, 0f};
  private final float[] mFactors = {1, 1, 1, 1};

  private int mTrackId = -1;
  private int mJoystickType;
  private int mControlPositionX, mControlPositionY;
  private int mPreviousTouchX, mPreviousTouchY;
  private int mWidth;
  private int mHeight;
  private int mAlpha;
  private Rect mVirtBounds;
  private Rect mOrigBounds;
  private BitmapDrawable mOuterBitmap;
  private BitmapDrawable mDefaultStateInnerBitmap;
  private BitmapDrawable mPressedStateInnerBitmap;
  private BitmapDrawable mBoundsBoxBitmap;

  /**
   * Constructor
   *
   * @param res                {@link Resources} instance.
   * @param bitmapOuter        {@link Bitmap} which represents the outer non-movable part of the joystick.
   * @param bitmapInnerDefault {@link Bitmap} which represents the default inner movable part of the joystick.
   * @param bitmapInnerPressed {@link Bitmap} which represents the pressed inner movable part of the joystick.
   * @param rectOuter          {@link Rect} which represents the outer joystick bounds.
   * @param rectInner          {@link Rect} which represents the inner joystick bounds.
   * @param joystick           Identifier for which joystick this is.
   */
  public InputOverlayDrawableJoystick(Resources res, Bitmap bitmapOuter,
    Bitmap bitmapInnerDefault, Bitmap bitmapInnerPressed,
    Rect rectOuter, Rect rectInner, int joystick)
  {
    setAxisIDs(joystick);

    mOuterBitmap = new BitmapDrawable(res, bitmapOuter);
    mDefaultStateInnerBitmap = new BitmapDrawable(res, bitmapInnerDefault);
    mPressedStateInnerBitmap = new BitmapDrawable(res, bitmapInnerPressed);
    mBoundsBoxBitmap = new BitmapDrawable(res, bitmapOuter);
    mWidth = bitmapOuter.getWidth();
    mHeight = bitmapOuter.getHeight();

    setBounds(rectOuter);
    mDefaultStateInnerBitmap.setBounds(rectInner);
    mPressedStateInnerBitmap.setBounds(rectInner);
    mVirtBounds = getBounds();
    mOrigBounds = mOuterBitmap.copyBounds();
    mBoundsBoxBitmap.setAlpha(0);
    mBoundsBoxBitmap.setBounds(getVirtBounds());
    SetInnerBounds();
  }

  /**
   * Gets this InputOverlayDrawableJoystick's button ID.
   *
   * @return this InputOverlayDrawableJoystick's button ID.
   */
  public int getId()
  {
    return mJoystickType;
  }

  public void onDraw(Canvas canvas)
  {
    mOuterBitmap.draw(canvas);
    getCurrentStateBitmapDrawable().draw(canvas);
    mBoundsBoxBitmap.draw(canvas);
  }

  public void onPointerDown(int id, float x, float y)
  {
    boolean reCenter = InputOverlay.sJoystickRelative;
    mOuterBitmap.setAlpha(0);
    mBoundsBoxBitmap.setAlpha(mAlpha);
    if (reCenter)
    {
      getVirtBounds().offset((int)x - getVirtBounds().centerX(), (int)y - getVirtBounds().centerY());
    }
    mBoundsBoxBitmap.setBounds(getVirtBounds());
    mTrackId = id;

    setJoystickState(x, y);
  }

  public void onPointerMove(int id, float x, float y)
  {
    setJoystickState(x, y);
  }

  public void onPointerUp(int id, float x, float y)
  {
    mOuterBitmap.setAlpha(mAlpha);
    mBoundsBoxBitmap.setAlpha(0);
    setVirtBounds(new Rect(mOrigBounds.left, mOrigBounds.top, mOrigBounds.right, mOrigBounds.bottom));
    setBounds(new Rect(mOrigBounds.left, mOrigBounds.top, mOrigBounds.right, mOrigBounds.bottom));
    mTrackId = -1;

    setJoystickState(x, y);
  }

  public void setAxisIDs(int joystick)
  {
    if(joystick != 0)
    {
      mJoystickType = joystick;

      mFactors[0] = 1;
      mFactors[1] = 1;
      mFactors[2] = 1;
      mFactors[3] = 1;

      mAxisIDs[0] = joystick + 1;
      mAxisIDs[1] = joystick + 2;
      mAxisIDs[2] = joystick + 3;
      mAxisIDs[3] = joystick + 4;
      return;
    }

    switch(InputOverlay.sJoyStickSetting)
    {
      case InputOverlay.JOYSTICK_EMULATE_IR:
        mJoystickType = 0;

        mFactors[0] = 0.8f;
        mFactors[1] = 0.8f;
        mFactors[2] = 0.4f;
        mFactors[3] = 0.4f;

        mAxisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_IR + 1;
        mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_IR + 2;
        mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_IR + 3;
        mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_IR + 4;
        break;
      case InputOverlay.JOYSTICK_EMULATE_WII_SWING:
        mJoystickType = 0;

        mFactors[0] = -0.8f;
        mFactors[1] = -0.8f;
        mFactors[2] = -0.8f;
        mFactors[3] = -0.8f;

        mAxisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_SWING + 1;
        mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_SWING + 2;
        mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_SWING + 3;
        mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_SWING + 4;
        break;
      case InputOverlay.JOYSTICK_EMULATE_WII_TILT:
        mJoystickType = 0;
        if(InputOverlay.sControllerType == InputOverlay.CONTROLLER_WIINUNCHUK)
        {
          mFactors[0] = 0.8f;
          mFactors[1] = 0.8f;
          mFactors[2] = 0.8f;
          mFactors[3] = 0.8f;

          mAxisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_TILT + 1;
          mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_TILT + 2;
          mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_TILT + 3;
          mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_TILT + 4;
        }
        else
        {
          mFactors[0] = -0.8f;
          mFactors[1] = -0.8f;
          mFactors[2] = 0.8f;
          mFactors[3] = 0.8f;

          mAxisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_TILT + 4; // right
          mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_TILT + 3; // left
          mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_TILT + 1; // up
          mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_TILT + 2; // down
        }
        break;
      case InputOverlay.JOYSTICK_EMULATE_WII_SHAKE:
        mJoystickType = 0;
        mAxisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_X;
        mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_X;
        mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_Y;
        mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_Z;
        break;
      case InputOverlay.JOYSTICK_EMULATE_NUNCHUK_SWING:
        mJoystickType = 0;

        mFactors[0] = -0.8f;
        mFactors[1] = -0.8f;
        mFactors[2] = -0.8f;
        mFactors[3] = -0.8f;

        mAxisIDs[0] = NativeLibrary.ButtonType.NUNCHUK_SWING + 1;
        mAxisIDs[1] = NativeLibrary.ButtonType.NUNCHUK_SWING + 2;
        mAxisIDs[2] = NativeLibrary.ButtonType.NUNCHUK_SWING + 3;
        mAxisIDs[3] = NativeLibrary.ButtonType.NUNCHUK_SWING + 4;
        break;
      case InputOverlay.JOYSTICK_EMULATE_NUNCHUK_TILT:
        mJoystickType = 0;

        mFactors[0] = 0.8f;
        mFactors[1] = 0.8f;
        mFactors[2] = 0.8f;
        mFactors[3] = 0.8f;

        mAxisIDs[0] = NativeLibrary.ButtonType.NUNCHUK_TILT + 1;
        mAxisIDs[1] = NativeLibrary.ButtonType.NUNCHUK_TILT + 2;
        mAxisIDs[2] = NativeLibrary.ButtonType.NUNCHUK_TILT + 3;
        mAxisIDs[3] = NativeLibrary.ButtonType.NUNCHUK_TILT + 4;
        break;
      case InputOverlay.JOYSTICK_EMULATE_NUNCHUK_SHAKE:
        mJoystickType = 0;
        mAxisIDs[0] = NativeLibrary.ButtonType.NUNCHUK_SHAKE_X;
        mAxisIDs[1] = NativeLibrary.ButtonType.NUNCHUK_SHAKE_X;
        mAxisIDs[2] = NativeLibrary.ButtonType.NUNCHUK_SHAKE_Y;
        mAxisIDs[3] = NativeLibrary.ButtonType.NUNCHUK_SHAKE_Z;
        break;
    }
  }

  private void setJoystickState(float touchX, float touchY)
  {
    if(mTrackId != -1)
    {
      float maxY = getVirtBounds().bottom;
      float maxX = getVirtBounds().right;
      touchX -= getVirtBounds().centerX();
      maxX -= getVirtBounds().centerX();
      touchY -= getVirtBounds().centerY();
      maxY -= getVirtBounds().centerY();
      final float AxisX = touchX / maxX;
      final float AxisY = touchY / maxY;
      mAxises[0] = AxisY;
      mAxises[1] = AxisX;
    }
    else
    {
      mAxises[0] = mAxises[1] = 0.0f;
    }

    SetInnerBounds();

    float[] axises = getAxisValues();

    if(mJoystickType != 0)
    {
      // fx wii classic or classic bind
      axises[1] = Math.min(axises[1], 1.0f);
      axises[0] = Math.min(axises[0], 0.0f);
      axises[3] = Math.min(axises[3], 1.0f);
      axises[2] = Math.min(axises[2], 0.0f);
    }
    else if (InputOverlay.sJoyStickSetting == InputOverlay.JOYSTICK_EMULATE_WII_SHAKE ||
      InputOverlay.sJoyStickSetting == InputOverlay.JOYSTICK_EMULATE_NUNCHUK_SHAKE)
    {
      // shake
      axises[0] = -axises[1];
      axises[1] = -axises[1];
      axises[3] = -axises[3];
      handleShakeEvent(axises);
      return;
    }

    for (int i = 0; i < 4; i++)
    {
      NativeLibrary.onGamePadMoveEvent(
        NativeLibrary.TouchScreenDevice, mAxisIDs[i], mFactors[i] * axises[i]);
    }
  }

  // axis to button
  private void handleShakeEvent(float[] axises)
  {
    for(int i = 0; i < axises.length; ++i)
    {
      if(axises[i] > 0.15f)
      {
        if(InputOverlay.sShakeStates[i] != NativeLibrary.ButtonState.PRESSED)
        {
          InputOverlay.sShakeStates[i] = NativeLibrary.ButtonState.PRESSED;
          NativeLibrary.onGamePadEvent(NativeLibrary.TouchScreenDevice, mAxisIDs[i],
            NativeLibrary.ButtonState.PRESSED);
        }
      }
      else if(InputOverlay.sShakeStates[i] != NativeLibrary.ButtonState.RELEASED)
      {
        InputOverlay.sShakeStates[i] = NativeLibrary.ButtonState.RELEASED;
        NativeLibrary.onGamePadEvent(NativeLibrary.TouchScreenDevice, mAxisIDs[i],
          NativeLibrary.ButtonState.RELEASED);
      }
    }
  }

  public boolean onConfigureTouch(MotionEvent event)
  {
    int pointerIndex = event.getActionIndex();
    int fingerPositionX = (int) event.getX(pointerIndex);
    int fingerPositionY = (int) event.getY(pointerIndex);
    switch (event.getAction())
    {
      case MotionEvent.ACTION_DOWN:
        mPreviousTouchX = fingerPositionX;
        mPreviousTouchY = fingerPositionY;
        break;
      case MotionEvent.ACTION_MOVE:
        int deltaX = fingerPositionX - mPreviousTouchX;
        int deltaY = fingerPositionY - mPreviousTouchY;
        mControlPositionX += deltaX;
        mControlPositionY += deltaY;
        setBounds(new Rect(mControlPositionX, mControlPositionY,
          mOuterBitmap.getIntrinsicWidth() + mControlPositionX,
          mOuterBitmap.getIntrinsicHeight() + mControlPositionY));
        setVirtBounds(new Rect(mControlPositionX, mControlPositionY,
          mOuterBitmap.getIntrinsicWidth() + mControlPositionX,
          mOuterBitmap.getIntrinsicHeight() + mControlPositionY));
        SetInnerBounds();
        setOrigBounds(new Rect(new Rect(mControlPositionX, mControlPositionY,
          mOuterBitmap.getIntrinsicWidth() + mControlPositionX,
          mOuterBitmap.getIntrinsicHeight() + mControlPositionY)));
        mPreviousTouchX = fingerPositionX;
        mPreviousTouchY = fingerPositionY;
        break;
    }
    return true;
  }

  public float[] getAxisValues()
  {
    return new float[]{mAxises[0], mAxises[0], mAxises[1], mAxises[1]};
  }

  private void SetInnerBounds()
  {
    int X = getVirtBounds().centerX() + (int) ((mAxises[1]) * (getVirtBounds().width() / 2));
    int Y = getVirtBounds().centerY() + (int) ((mAxises[0]) * (getVirtBounds().height() / 2));

    if (X > getVirtBounds().centerX() + (getVirtBounds().width() / 2))
      X = getVirtBounds().centerX() + (getVirtBounds().width() / 2);
    if (X < getVirtBounds().centerX() - (getVirtBounds().width() / 2))
      X = getVirtBounds().centerX() - (getVirtBounds().width() / 2);
    if (Y > getVirtBounds().centerY() + (getVirtBounds().height() / 2))
      Y = getVirtBounds().centerY() + (getVirtBounds().height() / 2);
    if (Y < getVirtBounds().centerY() - (getVirtBounds().height() / 2))
      Y = getVirtBounds().centerY() - (getVirtBounds().height() / 2);

    int width = mPressedStateInnerBitmap.getBounds().width() / 2;
    int height = mPressedStateInnerBitmap.getBounds().height() / 2;
    mDefaultStateInnerBitmap.setBounds(X - width, Y - height, X + width, Y + height);
    mPressedStateInnerBitmap.setBounds(mDefaultStateInnerBitmap.getBounds());
  }

  public void setPosition(int x, int y)
  {
    mControlPositionX = x;
    mControlPositionY = y;
  }

  private BitmapDrawable getCurrentStateBitmapDrawable()
  {
    return mTrackId != -1 ? mPressedStateInnerBitmap : mDefaultStateInnerBitmap;
  }

  public void setBounds(Rect bounds)
  {
    mOuterBitmap.setBounds(bounds);
  }

  public void setAlpha(int value)
  {
    mAlpha = value;
    mDefaultStateInnerBitmap.setAlpha(value);
    mOuterBitmap.setAlpha(value);
  }

  public Rect getBounds()
  {
    return mOuterBitmap.getBounds();
  }

  private void setVirtBounds(Rect bounds)
  {
    mVirtBounds = bounds;
  }

  private void setOrigBounds(Rect bounds)
  {
    mOrigBounds = bounds;
  }

  private Rect getVirtBounds()
  {
    return mVirtBounds;
  }

  public int getWidth()
  {
    return mWidth;
  }

  public int getHeight()
  {
    return mHeight;
  }

  public int getTrackId()
  {
    return mTrackId;
  }
}
