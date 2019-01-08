package org.dolphinemu.dolphinemu.overlay;

import org.dolphinemu.dolphinemu.NativeLibrary;

public class InputOverlayPointer
{
  private int mTrackId;
  private float mWidth;
  private float mHeight;
  private float mCenterX;
  private float mCenterY;
  private final int[] mAxisIDs = new int[4];

  public InputOverlayPointer(float width, float height)
  {
    mAxisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_IR + 1;
    mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_IR + 2;
    mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_IR + 3;
    mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_IR + 4;

    mWidth = width;
    mHeight = height;

    mTrackId = -1;
  }

  public int getTrackId()
  {
    return mTrackId;
  }

  public void onPointerDown(int id, float x, float y)
  {
    NativeLibrary.onGamePadEvent(NativeLibrary.TouchScreenDevice,
      NativeLibrary.ButtonType.WIIMOTE_IR_HIDE, NativeLibrary.ButtonState.RELEASED);

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
    setPointerState(0, 0);

    if(InputOverlay.sAutoHidePointer)
      NativeLibrary.onGamePadEvent(NativeLibrary.TouchScreenDevice,
        NativeLibrary.ButtonType.WIIMOTE_IR_HIDE, NativeLibrary.ButtonState.PRESSED);
  }

  private void setPointerState(float x, float y)
  {
    float axises[] = new float[4];
    if(mTrackId == -1)
    {
      axises[0] = axises[1] = axises[2] = axises[3] = 0;
    }
    else
    {
      axises[0] = axises[1] = (y - mCenterY) / mHeight / 50 * InputOverlay.sIREmulateSensitive;
      axises[2] = axises[3] = (x - mCenterX) / mWidth / 100 * InputOverlay.sIREmulateSensitive;
    }

    for (int i = 0; i < 4; i++)
    {
      NativeLibrary.onGamePadMoveEvent(NativeLibrary.TouchScreenDevice, mAxisIDs[i], axises[i]);
    }
  }
}
