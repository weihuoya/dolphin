package org.dolphinemu.dolphinemu.utils;

import java.util.HashMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import java.net.URL;
import java.net.HttpURLConnection;

import android.os.Handler;

public class DownloadUtils implements Runnable
{
  private final HashMap<String, File> mCache = new HashMap<>();
  private Handler mHandler;
  private DownloadCallback mListener;
  private File mDownloadPath;
  private String mUrl;
  private HttpURLConnection mUrlConnection;
  private boolean mIsRunning = false;
  private boolean mIsCancelled = false;

  /**
   * Initialize the DownloadUtils object.
   *
   * @param handler  Handler that will handle download status callbacks.
   * @param listener Listener of download status callbacks.
   * @param path     The path to download the file to.
   * @see DownloadUtils#setUrl(String)
   * @see DownloadUtils#start()
   */
  public DownloadUtils(Handler handler, DownloadCallback listener, File path)
  {
    mHandler = handler;
    mListener = listener;
    mDownloadPath = path;
  }

  /**
   * Alternate constructor, when no callbacks are needed (e.g. background task).
   *
   * @param path The path to download the file to.
   * @see DownloadUtils#setUrl(String)
   * @see DownloadUtils#start()
   */
  public DownloadUtils(File path)
  {
    mDownloadPath = path;
  }

  /**
   * Start download on a new thread.
   */
  public void start()
  {
    Thread downloadThread = new Thread(this);
    downloadThread.start();
  }

  @Override
  public void run()
  {
    if (!mCache.containsKey(mUrl))
    {
      downloadFile();
    }
    if (mHandler != null && !mIsCancelled)
      mHandler.post(() -> mListener.onDownloadComplete(mCache.get(mUrl)));
    mIsCancelled = false;
  }

  private void downloadFile()
  {
    try
    {
      mIsRunning = true;
      URL url = new URL(mUrl);
      HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
      mUrlConnection = urlConnection;
      urlConnection.setRequestMethod("GET");
      urlConnection.connect();
      if (mHandler != null) mHandler.post(() -> mListener.onDownloadStart());

      String filename = "download";
      String fieldContentDisp = urlConnection.getHeaderField("Content-Disposition");
      if (fieldContentDisp != null && fieldContentDisp.contains("filename="))
      {
        filename = fieldContentDisp.substring(fieldContentDisp.indexOf("filename=") + 9);
      }
      File file = new File(mDownloadPath, filename);
      mCache.put(mUrl, file);

      FileOutputStream fileOutput = new FileOutputStream(file);
      InputStream inputStream = urlConnection.getInputStream();

      float totalSize = urlConnection.getContentLength();
      int downloadedSize = 0;

      byte[] buffer = new byte[1024];
      int bufferLength;

      while ((bufferLength = inputStream.read(buffer)) > 0)
      {
        fileOutput.write(buffer, 0, bufferLength);
        downloadedSize += bufferLength;

        int progress = (int) (downloadedSize / totalSize * 100);
        if (mHandler != null) mHandler.post(() -> mListener.onDownloadProgress(progress));
      }

      fileOutput.close();
      urlConnection.disconnect();
      mIsRunning = false;
    }
    catch (Exception e)
    {
      mIsRunning = false;
      if (mHandler != null)
      {
        if (mIsCancelled)
        {
          mHandler.post(() -> mListener.onDownloadCancelled());
        }
        else mHandler.post(() -> mListener.onDownloadError());
      }
      deleteFile();
    }
  }

  /**
   * Cancel the current downloads by disconnecting from the url.
   * Report cancelled status back to the listener if any.
   */
  public void cancel()
  {
    mIsCancelled = true;
    if (mUrlConnection != null)
      mUrlConnection.disconnect();
  }

  /**
   * Get download status.
   */
  public boolean isRunning()
  {
    return mIsRunning;
  }

  /**
   * Delete downloaded file.
   */
  private void deleteFile()
  {
    mCache.get(mUrl).delete();
    mCache.remove(mUrl);
  }

  /**
   * Set the url of the file to download.
   *
   * @param url The url of the file to download.
   */
  public void setUrl(String url)
  {
    mUrl = url;
  }

  /**
   * This setter is here for convenience as you should always use the constructor.
   *
   * @param handler Handler that will handle download status callbacks.
   */
  public void setHandler(Handler handler)
  {
    mHandler = handler;
  }

  /**
   * This setter is here for convenience as you should always use the constructor.
   *
   * @param listener The listener of download status callbacks.
   */
  public void setCallbackListener(DownloadCallback listener)
  {
    mListener = listener;
  }

  /**
   * This setter is here for convenience as you should always use the constructor.
   *
   * @param path The path to download the file to.
   */
  public void setDownloadPath(String path)
  {
    mDownloadPath = new File(path);
  }
}