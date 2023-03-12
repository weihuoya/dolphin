package org.dolphinemu.dolphinemu.model;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Parcel;
import android.os.Parcelable;

import org.dolphinemu.dolphinemu.utils.VersionCode;

public class UpdaterData implements Parcelable
{
  public final VersionCode version;
  public final String downloadUrl;
  public final float size;

  public UpdaterData(JSONObject data) throws JSONException, IllegalArgumentException
  {
    this.version = VersionCode.create(data.getString("tag_name"));
    this.downloadUrl =
            data.getJSONArray("assets").getJSONObject(0).getString("browser_download_url");
    this.size = data.getJSONArray("assets").getJSONObject(0).getInt("size") /
            1048576f; // byte count to MegaBytes
  }

  protected UpdaterData(Parcel in)
  {
    this.version = in.readParcelable(VersionCode.class.getClassLoader());
    this.downloadUrl = in.readString();
    this.size = in.readFloat();
  }

  @Override
  public int describeContents()
  {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags)
  {
    dest.writeParcelable(this.version, flags);
    dest.writeString(this.downloadUrl);
    dest.writeFloat(this.size);
  }

  public static final Creator<UpdaterData> CREATOR = new Creator<UpdaterData>()
  {
    @Override
    public UpdaterData createFromParcel(Parcel source)
    {
      return new UpdaterData(source);
    }

    @Override
    public UpdaterData[] newArray(int size)
    {
      return new UpdaterData[size];
    }
  };
}