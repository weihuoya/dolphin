// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.overlay;

import android.graphics.Rect;
import android.os.Handler;
import android.view.MotionEvent;

import org.dolphinemu.dolphinemu.NativeLibrary;

import java.util.ArrayList;

public class InputOverlayPointer
{
  public static final int DOUBLE_TAP_A = 0;
  public static final int DOUBLE_TAP_B = 1;
  public static final int DOUBLE_TAP_2 = 2;
  public static final int DOUBLE_TAP_CLASSIC_A = 3;

  public static final int MODE_DISABLED = 0;
  public static final int MODE_FOLLOW = 1;
  public static final int MODE_DRAG = 2;

  private final float[] mAxes = {0f, 0f};
  private final float[] mOldAxes = {0f, 0f};

  private float mGameCenterX;
  private float mGameCenterY;
  private float mGameWidthHalfInv;
  private float mGameHeightHalfInv;

  private float mTouchStartX;
  private float mTouchStartY;

  private int mMode;
  private boolean mRecenter;

  private boolean doubleTap = false;
  private int doubleTapButton;
  private int mTrackId = -1;

  public static ArrayList<Integer> DOUBLE_TAP_OPTIONS = new ArrayList<>();

  static
  {
    DOUBLE_TAP_OPTIONS.add(NativeLibrary.ButtonType.WIIMOTE_BUTTON_A);
    DOUBLE_TAP_OPTIONS.add(NativeLibrary.ButtonType.WIIMOTE_BUTTON_B);
    DOUBLE_TAP_OPTIONS.add(NativeLibrary.ButtonType.WIIMOTE_BUTTON_2);
    DOUBLE_TAP_OPTIONS.add(NativeLibrary.ButtonType.CLASSIC_BUTTON_A);
  }

  public InputOverlayPointer(Rect surfacePosition, int button, int mode, boolean recenter)
  {
    doubleTapButton = button;
    mMode = mode;
    mRecenter = recenter;

    mGameCenterX = (surfacePosition.left + surfacePosition.right) / 2.0f;
    mGameCenterY = (surfacePosition.top + surfacePosition.bottom) / 2.0f;

    float gameWidth = surfacePosition.right - surfacePosition.left;
    float gameHeight = surfacePosition.bottom - surfacePosition.top;

    // Adjusting for device's black bars.
    float surfaceAR = gameWidth / gameHeight;
    float gameAR = NativeLibrary.GetGameAspectRatio();

    if (gameAR <= surfaceAR)
    {
      // Black bars on left/right
      gameWidth = gameHeight * gameAR;
    }
    else
    {
      // Black bars on top/bottom
      gameHeight = gameWidth / gameAR;
    }

    mGameWidthHalfInv = 1.0f / (gameWidth * 0.5f);
    mGameHeightHalfInv = 1.0f / (gameHeight * 0.5f);
  }

  public void onTouch(MotionEvent event)
  {
    int pointerIndex = event.getActionIndex();

    switch (event.getAction() & MotionEvent.ACTION_MASK)
    {
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_POINTER_DOWN:
        mTrackId = event.getPointerId(pointerIndex);
        mTouchStartX = event.getX(pointerIndex);
        mTouchStartY = event.getY(pointerIndex);
        touchPress();
        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_POINTER_UP:
        if (mTrackId == event.getPointerId(pointerIndex))
          mTrackId = -1;
        if (mMode == MODE_DRAG)
          updateOldAxes();
        if (mRecenter)
          reset();
        break;
    }

    if (mTrackId == -1)
      return;

    if (mMode == MODE_FOLLOW)
    {
      mAxes[0] = (event.getY(event.findPointerIndex(mTrackId)) - mGameCenterY) * mGameHeightHalfInv;
      mAxes[1] = (event.getX(event.findPointerIndex(mTrackId)) - mGameCenterX) * mGameWidthHalfInv;
    }
    else if (mMode == MODE_DRAG)
    {
      mAxes[0] = mOldAxes[0] +
              (event.getY(event.findPointerIndex(mTrackId)) - mTouchStartY) * mGameHeightHalfInv;
      mAxes[1] = mOldAxes[1] +
              (event.getX(event.findPointerIndex(mTrackId)) - mTouchStartX) * mGameWidthHalfInv;
    }
  }

  private void touchPress()
  {
    if (mMode != MODE_DISABLED)
    {
      if (doubleTap)
      {
        NativeLibrary.onGamePadEvent(NativeLibrary.TouchScreenDevice,
                doubleTapButton, NativeLibrary.ButtonState.PRESSED);
        new Handler()
                .postDelayed(() -> NativeLibrary.onGamePadEvent(NativeLibrary.TouchScreenDevice,
                        doubleTapButton, NativeLibrary.ButtonState.RELEASED), 50);
      }
      else
      {
        doubleTap = true;
        new Handler().postDelayed(() -> doubleTap = false, 300);
      }
    }
  }

  private void updateOldAxes()
  {
    mOldAxes[0] = mAxes[0];
    mOldAxes[1] = mAxes[1];
  }

  private void reset()
  {
    mAxes[0] = mAxes[1] = mOldAxes[0] = mOldAxes[1] = 0f;
  }

  public float[] getAxisValues()
  {
    float[] iraxes = {0f, 0f, 0f, 0f};
    iraxes[1] = mAxes[0];
    iraxes[0] = mAxes[0];
    iraxes[3] = mAxes[1];
    iraxes[2] = mAxes[1];
    return iraxes;
  }

  public void setMode(int mode)
  {
    mMode = mode;
    if (mode == MODE_DRAG)
      updateOldAxes();
  }

  public void setRecenter(boolean recenter)
  {
    mRecenter = recenter;
  }
}
