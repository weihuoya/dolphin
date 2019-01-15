package org.dolphinemu.dolphinemu.overlay;

import org.dolphinemu.dolphinemu.NativeLibrary;

public class InputOverlayPointer
{
  private int mTrackId;
  private float mWidth;
  private float mHeight;
  private float mCenterX;
  private float mCenterY;
  private int[] mAxisIDs = new int[4];
  private float[] mAxises = new float[4];

  public InputOverlayPointer(float width, float height)
  {
    mAxisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_IR + 1;
    mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_IR + 2;
    mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_IR + 3;
    mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_IR + 4;

    mAxises[0] = mAxises[1] = mAxises[2] = mAxises[3] = 0;

    mWidth = width;
    mHeight = height;

    mTrackId = -1;
  }

  public void reset()
  {
    mTrackId = -1;
    mAxises[0] = mAxises[1] = mAxises[2] = mAxises[3] = 0;
    for (int i = 0; i < 4; i++)
    {
      NativeLibrary.onGamePadMoveEvent(NativeLibrary.TouchScreenDevice, mAxisIDs[i], mAxises[i]);
    }
  }

  public int getTrackId()
  {
    return mTrackId;
  }

  public void onPointerDown(int id, float x, float y)
  {
    mTrackId = id;
    mCenterX = x;
    mCenterY = y;
    setPointerState(x, y);
  }

  public void onPointerMove(int id, float x, float y)
  {
    setPointerState(x, y);
  }

  public void onPointerUp(int id, float x, float y)
  {
    mTrackId = -1;
    setPointerState(x, y);
  }

  private void setPointerState(float x, float y)
  {
    float[] axises = new float[4];
    axises[0] = axises[1] = (y - mCenterY) / mHeight / 50 * InputOverlay.sIREmulateSensitive;
    axises[2] = axises[3] = (x - mCenterX) / mWidth / 100 * InputOverlay.sIREmulateSensitive;

    for (int i = 0; i < 4; i++)
    {
      float value = mAxises[i] + axises[i];
      NativeLibrary.onGamePadMoveEvent(NativeLibrary.TouchScreenDevice, mAxisIDs[i], value);
      if (mTrackId == -1)
      {
        mAxises[i] = value;
      }
    }
  }
}
