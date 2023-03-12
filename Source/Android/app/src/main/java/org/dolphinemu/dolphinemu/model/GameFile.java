// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.widget.ImageView;

import org.dolphinemu.dolphinemu.NativeLibrary;
import org.dolphinemu.dolphinemu.R;
import org.dolphinemu.dolphinemu.utils.CoverHelper;
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import androidx.annotation.Keep;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class GameFile
{
  @Keep
  private long mPointer;

  @Keep
  private GameFile(long pointer)
  {
    mPointer = pointer;
  }

  public native static GameFile parse(String path);

  @Override
  public native void finalize();

  public native int getPlatform();

  public native String getTitle();

  public native String getDescription();

  public native String getCompany();

  public native int getCountry();

  public native int getRegion();

  public native String getPath();

  public native String getGameId();

  public native String getGameTdbId();

  public native int getDiscNumber();

  public native int getRevision();

  public native int getBlobType();

  public native String getFileFormatName();

  public native long getBlockSize();

  public native String getCompressionMethod();

  public native boolean shouldShowFileFormatDetails();

  public native boolean shouldAllowConversion();

  public native long getFileSize();

  public native boolean isDatelDisc();

  public native boolean isNKit();

  public native int[] getBanner();

  public native int getBannerWidth();

  public native int getBannerHeight();

  public String getCoverPath(Context context)
  {
    return context.getExternalCacheDir().getPath() + "/GameCovers/" + getGameTdbId() + ".png";
  }

  public String getCustomCoverPath()
  {
    return getPath().substring(0, getPath().lastIndexOf(".")) + ".cover.png";
  }

  private static final int COVER_UNKNOWN = 0;
  private static final int COVER_CACHE = 1;
  private static final int COVER_NONE = 2;
  private int mCoverType = COVER_UNKNOWN;

  public void loadGameBanner(ImageView imageView)
  {
    if (mCoverType == COVER_UNKNOWN)
    {
      if (loadFromCache(imageView))
      {
        mCoverType = COVER_CACHE;
        return;
      }

      mCoverType = COVER_NONE;
      loadFromNetwork(imageView, new Callback()
      {
        @Override public void onSuccess()
        {
          mCoverType = COVER_CACHE;
          CoverHelper.saveCover(((BitmapDrawable) imageView.getDrawable()).getBitmap(),
                  getCoverPath(imageView.getContext()));
        }

        @Override public void onError(Exception e)
        {
          if (loadFromISO(imageView))
          {
            mCoverType = COVER_CACHE;
          }
          else if (NativeLibrary.isNetworkConnected(imageView.getContext()))
          {
            // save placeholder to file
            CoverHelper.saveCover(((BitmapDrawable) imageView.getDrawable()).getBitmap(),
                    getCoverPath(imageView.getContext()));
          }
        }
      });
    }
    else if (mCoverType == COVER_CACHE)
    {
      loadFromCache(imageView);
    }
    else
    {
      imageView.setImageResource(R.drawable.no_banner);
    }
  }

  private boolean loadFromCache(ImageView imageView)
  {
    File file = new File(getCoverPath(imageView.getContext()));
    if (file.exists())
    {
      imageView.setImageURI(Uri.parse("file://" + getCoverPath(imageView.getContext())));
      return true;
    }
    return false;
  }

  private void loadFromNetwork(ImageView imageView, Callback callback)
  {
    Picasso.get()
            .load(CoverHelper.buildGameTDBUrl(this, null))
            .placeholder(R.drawable.no_banner)
            .error(R.drawable.no_banner)
            .into(imageView, new Callback()
            {
              @Override
              public void onSuccess()
              {
                callback.onSuccess();
              }

              @Override
              public void onError(Exception e)
              {
                String id = getGameTdbId();
                String region = null;
                if (id.length() < 3)
                {
                  callback.onError(e);
                  return;
                }
                else if (id.charAt(3) != 'E')
                {
                  region = "US";
                }
                else if (id.charAt(3) != 'J')
                {
                  region = "JA";
                }
                else
                {
                  callback.onError(e);
                  return;
                }
                Picasso.get()
                        .load(CoverHelper.buildGameTDBUrl(GameFile.this, region))
                        .placeholder(R.drawable.no_banner)
                        .error(R.drawable.no_banner)
                        .into(imageView, callback);
              }
            });
  }

  private boolean loadFromISO(ImageView imageView)
  {
    int[] vector = getBanner();
    int width = getBannerWidth();
    int height = getBannerHeight();
    if (vector.length > 0 && width > 0 && height > 0)
    {
      File file = new File(getCoverPath(imageView.getContext()));
      Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      bitmap.setPixels(vector, 0, width, 0, 0, width, height);
      try
      {
        FileOutputStream out = new FileOutputStream(file);
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        out.close();
      }
      catch (Exception e)
      {
        return false;
      }
      return loadFromCache(imageView);
    }
    return false;
  }
}
