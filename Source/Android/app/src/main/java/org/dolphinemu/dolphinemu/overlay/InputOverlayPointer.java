package org.dolphinemu.dolphinemu.overlay;
import org.dolphinemu.dolphinemu.NativeLibrary;
import org.dolphinemu.dolphinemu.utils.Log;

public class InputOverlayPointer
{
  private int mType;
  private int mTrackId;

  private float mScaledDensity;
  private int mMaxWidth;
  private int mMaxHeight;
  private float mGameWidthHalf;
  private float mGameHeightHalf;
  private float mAdjustX;
  private float mAdjustY;

  private int[] mAxisIDs = new int[4];
  private float[] mAxises = new float[4];

  private float mCenterX;
  private float mCenterY;

  public InputOverlayPointer(int width, int height, float scaledDensity)
  {
    mType = 0;
    mAxisIDs[0] = 0;
    mAxisIDs[1] = 0;
    mAxisIDs[2] = 0;
    mAxisIDs[3] = 0;

    mScaledDensity = scaledDensity;
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

  public void setType(int type)
  {
    reset();
    mType = type;

    if(type == 1)
    {
      // click
      mAxisIDs[0] = 0;
      mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_IR + 2;
      mAxisIDs[2] = 0;
      mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_IR + 4;
    }
    else
    {
      // stick
      mAxisIDs[0] = NativeLibrary.ButtonType.WIIMOTE_IR + 1;
      mAxisIDs[1] = NativeLibrary.ButtonType.WIIMOTE_IR + 2;
      mAxisIDs[2] = NativeLibrary.ButtonType.WIIMOTE_IR + 3;
      mAxisIDs[3] = NativeLibrary.ButtonType.WIIMOTE_IR + 4;
    }
  }

  public void reset()
  {
    mTrackId = -1;
    for (int i = 0; i < 4; i++)
    {
      mAxises[i] = 0.0f;
      NativeLibrary.onGamePadMoveEvent(NativeLibrary.TouchScreenDevice,
        NativeLibrary.ButtonType.WIIMOTE_IR + i + 1, 0.0f);
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

    if(mType == 1)
    {
      // click
      axises[0] = axises[1] = ((y * mAdjustY) - mGameHeightHalf) / mGameHeightHalf;
      axises[2] = axises[3] = ((x * mAdjustX) - mGameWidthHalf) / mGameWidthHalf;
    }
    else
    {
      // stick
      axises[0] = axises[1] = (y - mCenterY) / mGameHeightHalf * mScaledDensity;
      axises[2] = axises[3] = (x - mCenterX) / mGameWidthHalf * mScaledDensity;
    }

    for (int i = 0; i < mAxisIDs.length; ++i)
    {
      float value = mAxises[i] + axises[i];
      if (mTrackId == -1)
      {
        if(InputOverlay.sIRRecenter)
        {
          // recenter
          value = 0;
        }
        if(mType != 1)
        {
          // stick
          mAxises[i] = value;
        }
      }
      NativeLibrary.onGamePadMoveEvent(NativeLibrary.TouchScreenDevice, mAxisIDs[i], value);
    }
  }
}
