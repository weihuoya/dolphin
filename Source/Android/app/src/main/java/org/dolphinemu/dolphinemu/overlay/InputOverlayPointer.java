package org.dolphinemu.dolphinemu.overlay;
import org.dolphinemu.dolphinemu.NativeLibrary;

public class InputOverlayPointer
{
  private int mTrackId;

  private int mMaxWidth;
  private int mMaxHeight;
  private float mGameWidthHalf;
  private float mGameHeightHalf;
  private float mAdjustX;
  private float mAdjustY;

  private int[] mAxisIDs = new int[4];

  public InputOverlayPointer(int width, int height)
  {
    mAxisIDs[0] = 0;
    mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_IR + 2;
    mAxisIDs[2] = 0;
    mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_IR + 4;

    mMaxWidth = width;
    mMaxHeight = height;
    mGameWidthHalf = width / 2.0f;
    mGameHeightHalf = height / 2.0f;
    mAdjustX = 1.0f;
    mAdjustY = 1.0f;

    mTrackId = -1;

    if(NativeLibrary.IsRunning())
    {
      updateTouchPointer();
    }
  }

  public void updateTouchPointer()
  {
    float deviceAR = (float)mMaxWidth / (float)mMaxHeight;
    float gameAR = NativeLibrary.GetGameAspectRatio();

    if(gameAR <= deviceAR)
    {
      mAdjustX = gameAR / deviceAR;
      mAdjustY = 1.0f;
      mGameWidthHalf = Math.round(mMaxHeight * gameAR) / 2.0f;
      mGameHeightHalf = mMaxHeight / 2.0f;
    }
    else
    {
      mAdjustX = 1.0f;
      mAdjustY = gameAR / deviceAR;
      mGameWidthHalf = mMaxWidth / 2.0f;
      mGameHeightHalf = Math.round(mMaxWidth / gameAR) / 2.0f;
    }
  }

  public void reset()
  {
    mTrackId = -1;
  }

  public int getTrackId()
  {
    return mTrackId;
  }

  public void onPointerDown(int id, float x, float y)
  {
    mTrackId = id;
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
    axises[0] = axises[1] = ((y * mAdjustY) - mGameHeightHalf) / mGameHeightHalf;
    axises[2] = axises[3] = ((x * mAdjustX) - mGameWidthHalf) / mGameWidthHalf;

    if(mTrackId == -1 && InputOverlay.sIRRecenter)
    {
      axises[0] = axises[1] = axises[2] = axises[3] = 0.0f;
    }

    for (int i = 0; i < 4; i++)
    {
      NativeLibrary.onGamePadMoveEvent(NativeLibrary.TouchScreenDevice, mAxisIDs[i], axises[i]);
    }
  }
}
