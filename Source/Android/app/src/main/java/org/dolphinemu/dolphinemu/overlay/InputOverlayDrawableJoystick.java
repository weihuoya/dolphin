/*
 * Copyright 2013 Dolphin Emulator Project
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package org.dolphinemu.dolphinemu.overlay;

import java.util.ArrayList;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.view.MotionEvent;

import org.dolphinemu.dolphinemu.NativeLibrary;
import org.dolphinemu.dolphinemu.features.settings.model.BooleanSetting;

/**
 * Custom {@link BitmapDrawable} that is capable
 * of storing it's own ID.
 */
public final class InputOverlayDrawableJoystick
{
  private final int[] axisIDs = {0, 0, 0, 0};
  private final float[] mAxises = {0f, 0f};
  private final float[] mFactors = {1, 1}; // y, x
  private int mTrackId = -1;
  private final int mJoystickType;
  private int mControlPositionX, mControlPositionY;
  private int mPreviousTouchX, mPreviousTouchY;
  private final int mWidth;
  private final int mHeight;
  private Rect mVirtBounds;
  private Rect mOrigBounds;
  private int mOpacity;
  private final BitmapDrawable mOuterBitmap;
  private final BitmapDrawable mDefaultStateInnerBitmap;
  private final BitmapDrawable mPressedStateInnerBitmap;
  private final BitmapDrawable mBoundsBoxBitmap;
  private boolean mPressedState = false;
  private final int mEmulationMode;

  public static final int JOYSTICK_EMULATION_OFF = 0;
  public static final ArrayList<Integer> JOYSTICK_EMULATION_OPTIONS = new ArrayList<>();

  static
  {
    JOYSTICK_EMULATION_OPTIONS.add(JOYSTICK_EMULATION_OFF);
    JOYSTICK_EMULATION_OPTIONS.add(NativeLibrary.ButtonType.WIIMOTE_IR);
    JOYSTICK_EMULATION_OPTIONS.add(NativeLibrary.ButtonType.WIIMOTE_SWING);
    JOYSTICK_EMULATION_OPTIONS.add(NativeLibrary.ButtonType.WIIMOTE_TILT);
    JOYSTICK_EMULATION_OPTIONS.add(NativeLibrary.ButtonType.WIIMOTE_SHAKE_X);
    JOYSTICK_EMULATION_OPTIONS.add(NativeLibrary.ButtonType.NUNCHUK_SWING);
    JOYSTICK_EMULATION_OPTIONS.add(NativeLibrary.ButtonType.NUNCHUK_TILT);
    JOYSTICK_EMULATION_OPTIONS.add(NativeLibrary.ButtonType.NUNCHUK_SHAKE_X);
  }

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
   * @param emulationMode      Joystick motion emulation mode enumerator.
   */
  public InputOverlayDrawableJoystick(Resources res, Bitmap bitmapOuter, Bitmap bitmapInnerDefault,
          Bitmap bitmapInnerPressed, Rect rectOuter, Rect rectInner, int joystick,
          int emulationMode)
  {
    mJoystickType = joystick;
    mEmulationMode = emulationMode;

    if (joystick == NativeLibrary.ButtonType.STICK_EMULATION)
    {
      initFactorsAndAxes();
    }
    else
    {
      axisIDs[0] = joystick + 1;
      axisIDs[1] = joystick + 2;
      axisIDs[2] = joystick + 3;
      axisIDs[3] = joystick + 4;
    }

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

  private void initFactorsAndAxes()
  {
    axisIDs[0] = mEmulationMode + 1;
    axisIDs[1] = mEmulationMode + 2;
    axisIDs[2] = mEmulationMode + 3;
    axisIDs[3] = mEmulationMode + 4;

    switch (mEmulationMode)
    {
      case NativeLibrary.ButtonType.WIIMOTE_IR:
        mFactors[0] = 0.6f;
        mFactors[1] = 0.4f;
        break;
      case NativeLibrary.ButtonType.WIIMOTE_SWING:
      case NativeLibrary.ButtonType.NUNCHUK_SWING:
        mFactors[0] = -0.8f;
        mFactors[1] = -0.8f;
        break;
      case NativeLibrary.ButtonType.WIIMOTE_TILT:
      case NativeLibrary.ButtonType.NUNCHUK_TILT:
        mFactors[0] = 0.8f;
        mFactors[1] = 0.8f;
        break;
      case NativeLibrary.ButtonType.WIIMOTE_SHAKE_X:
        axisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_X;
        axisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_X;
        axisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_Y;
        axisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_SHAKE_Z;
        break;
      case NativeLibrary.ButtonType.NUNCHUK_SHAKE_X:
        axisIDs[0] = NativeLibrary.ButtonType.NUNCHUK_SHAKE_X;
        axisIDs[1] = NativeLibrary.ButtonType.NUNCHUK_SHAKE_X;
        axisIDs[2] = NativeLibrary.ButtonType.NUNCHUK_SHAKE_Y;
        axisIDs[3] = NativeLibrary.ButtonType.NUNCHUK_SHAKE_Z;
        break;
    }
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

  public void draw(Canvas canvas)
  {
    mOuterBitmap.draw(canvas);
    getCurrentStateBitmapDrawable().draw(canvas);
    mBoundsBoxBitmap.draw(canvas);
  }

  public boolean TrackEvent(MotionEvent event)
  {
    boolean reCenter = BooleanSetting.MAIN_JOYSTICK_REL_CENTER.getBooleanGlobal();
    int pointerIndex = event.getActionIndex();
    boolean pressed = false;

    switch (event.getAction() & MotionEvent.ACTION_MASK)
    {
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_POINTER_DOWN:
        if (getBounds().contains((int) event.getX(pointerIndex), (int) event.getY(pointerIndex)))
        {
          mPressedState = pressed = true;
          mOuterBitmap.setAlpha(0);
          mBoundsBoxBitmap.setAlpha(mOpacity);
          if (reCenter)
          {
            getVirtBounds().offset((int) event.getX(pointerIndex) - getVirtBounds().centerX(),
                    (int) event.getY(pointerIndex) - getVirtBounds().centerY());
          }
          mBoundsBoxBitmap.setBounds(getVirtBounds());
          mTrackId = event.getPointerId(pointerIndex);
        }
        break;
      case MotionEvent.ACTION_MOVE:
        if (mJoystickType == NativeLibrary.ButtonType.STICK_EMULATION)
          pressed = true;
        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_POINTER_UP:
        if (mTrackId == event.getPointerId(pointerIndex))
        {
          pressed = true;
          mPressedState = false;
          mAxises[0] = mAxises[1] = 0.0f;
          mOuterBitmap.setAlpha(mOpacity);
          mBoundsBoxBitmap.setAlpha(0);
          setVirtBounds(new Rect(mOrigBounds.left, mOrigBounds.top, mOrigBounds.right,
                  mOrigBounds.bottom));
          setBounds(new Rect(mOrigBounds.left, mOrigBounds.top, mOrigBounds.right,
                  mOrigBounds.bottom));
          SetInnerBounds();
          mTrackId = -1;
        }
        break;
    }

    if (mTrackId == -1)
      return pressed;

    for (int i = 0; i < event.getPointerCount(); i++)
    {
      if (mTrackId == event.getPointerId(i))
      {
        float touchX = event.getX(i);
        float touchY = event.getY(i);
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

        SetInnerBounds();
      }
    }
    return pressed;
  }

  public void onConfigureTouch(MotionEvent event)
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
  }

  public float[] getAxisValues()
  {
    float[] joyaxises = {0f, 0f, 0f, 0f};

    if (mJoystickType == NativeLibrary.ButtonType.STICK_EMULATION)
    {
      switch (mEmulationMode)
      {
        case NativeLibrary.ButtonType.WIIMOTE_SHAKE_X:
        case NativeLibrary.ButtonType.NUNCHUK_SHAKE_X:
          mAxises[0] = Math.abs(mAxises[0]);
          mAxises[1] = Math.abs(mAxises[1]);
          break;
      }
      joyaxises[1] = mAxises[0] * mFactors[0];
      joyaxises[0] = mAxises[0] * mFactors[0];
      joyaxises[3] = mAxises[1] * mFactors[1];
      joyaxises[2] = mAxises[1] * mFactors[1];
    }
    else
    {
      joyaxises[1] = Math.min(mAxises[0], 1.0f);
      joyaxises[0] = Math.min(mAxises[0], 0.0f);
      joyaxises[3] = Math.min(mAxises[1], 1.0f);
      joyaxises[2] = Math.min(mAxises[1], 0.0f);
    }
    return joyaxises;
  }

  public int[] getAxisIDs()
  {
    return axisIDs;
  }

  private void SetInnerBounds()
  {
    double y = mAxises[0];
    double x = mAxises[1];

    double angle = Math.atan2(y, x) + Math.PI + Math.PI;
    double radius = Math.hypot(y, x);
    double maxRadius = (mEmulationMode == NativeLibrary.ButtonType.WIIMOTE_IR) ?
            NativeLibrary.GetInputRadiusAtAngle(0, mJoystickType, angle) * 3f :
            NativeLibrary.GetInputRadiusAtAngle(0, mJoystickType, angle);
    if (radius > maxRadius)
    {
      y = maxRadius * Math.sin(angle);
      x = maxRadius * Math.cos(angle);
      mAxises[0] = (float) y;
      mAxises[1] = (float) x;
    }

    int pixelX = getVirtBounds().centerX() + (int) (x * (getVirtBounds().width() / 2));
    int pixelY = getVirtBounds().centerY() + (int) (y * (getVirtBounds().height() / 2));

    int width = mPressedStateInnerBitmap.getBounds().width() / 2;
    int height = mPressedStateInnerBitmap.getBounds().height() / 2;
    mDefaultStateInnerBitmap.setBounds(pixelX - width, pixelY - height, pixelX + width,
            pixelY + height);
    mPressedStateInnerBitmap.setBounds(mDefaultStateInnerBitmap.getBounds());
  }

  public void setPosition(int x, int y)
  {
    mControlPositionX = x;
    mControlPositionY = y;
  }

  private BitmapDrawable getCurrentStateBitmapDrawable()
  {
    return mPressedState ? mPressedStateInnerBitmap : mDefaultStateInnerBitmap;
  }

  public void setBounds(Rect bounds)
  {
    mOuterBitmap.setBounds(bounds);
  }

  public void setOpacity(int value)
  {
    mOpacity = value;

    mDefaultStateInnerBitmap.setAlpha(value);
    mPressedStateInnerBitmap.setAlpha(value);

    if (mTrackId == -1)
    {
      mOuterBitmap.setAlpha(value);
      mBoundsBoxBitmap.setAlpha(0);
    }
    else
    {
      mOuterBitmap.setAlpha(0);
      mBoundsBoxBitmap.setAlpha(value);
    }
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
