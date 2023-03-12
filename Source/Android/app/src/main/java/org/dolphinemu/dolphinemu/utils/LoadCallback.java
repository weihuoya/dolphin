package org.dolphinemu.dolphinemu.utils;

import androidx.annotation.Keep;

public interface LoadCallback<T>
{
  @Keep
  void onLoad(T data);

  @Keep
  void onLoadError();
}