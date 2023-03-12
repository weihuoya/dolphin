package org.dolphinemu.dolphinemu.utils;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class VersionCode implements Parcelable
{
  private static final int LESSER = -1;
  private static final int EQUAL = 0;
  private static final int GREATER = 1;

  public final int major;         // 1.x.x
  public final int minor;         // x.1.x
  public final int patch;         // x.x.1
  public final int qualifier;     // x.x.x-12345

  private VersionCode(int major, int minor, int patch, int qualifier)
  {
    this.major = major;
    this.minor = minor;
    this.patch = patch;
    this.qualifier = qualifier;
  }

  public static VersionCode create(String versionString) throws IllegalArgumentException
  {
    int maj = 0, min = 0, pat = 0, qual = 0;
    try
    {
      String[] versionSplit = versionString.split("-");
      if (versionSplit.length > 1)
      {
        qual = Integer.parseInt(versionSplit[1]);
      }
      versionSplit = versionSplit[0].split("\\.");

      int i = 0;
      maj = Integer.parseInt(versionSplit[i++]);
      min = Integer.parseInt(versionSplit[i++]);
      pat = Integer.parseInt(versionSplit[i]);
    }
    catch (IndexOutOfBoundsException ignored)
    {
      // ignore
    }
    catch (NumberFormatException e)
    {
      throw new IllegalArgumentException("Invalid version string");
    }

    return new VersionCode(maj, min, pat, qual);
  }

  /**
   * @param operand The VersionCode to compare this VersionCode against
   * @return the value 0 if the VersionCode argument is equal to this VersionCode; a value less
   * than 0 if this VersionCode is less than the VersionCode argument; and a value greater than 0
   * if this VersionCode is greater than the VersionCode argument.
   */
  public int compareTo(VersionCode operand)
  {
    if (major != operand.major)
    {
      if (major > operand.major)
        return GREATER;
      else return LESSER;
    }
    else
    {
      if (minor != operand.minor)
      {
        if (minor > operand.minor)
          return GREATER;
        else return LESSER;
      }
      else
      {
        if (patch != operand.patch)
        {
          if (patch > operand.patch)
            return GREATER;
          else return LESSER;
        }
        else
        {
          if (qualifier != operand.qualifier)
          {
            if (qualifier > operand.qualifier)
              return GREATER;
            else return LESSER;
          }
          else return EQUAL;
        }
      }
    }
  }

  public boolean equals(VersionCode operand)
  {
    return major == operand.major && minor == operand.minor && patch == operand.patch &&
            qualifier == operand.qualifier;
  }

  @NonNull
  public String toString()
  {
    StringBuilder versionString = new StringBuilder();
    versionString.append(major).append(".").append(minor);
    if (patch != 0)
      versionString.append(".").append(patch);
    if (qualifier != 0)
      versionString.append("-").append(qualifier);

    return versionString.toString();
  }

  protected VersionCode(Parcel in)
  {
    this.major = in.readInt();
    this.minor = in.readInt();
    this.patch = in.readInt();
    this.qualifier = in.readInt();
  }

  @Override
  public int describeContents()
  {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags)
  {
    dest.writeInt(this.major);
    dest.writeInt(this.minor);
    dest.writeInt(this.patch);
    dest.writeInt(this.qualifier);
  }

  public static final Creator<VersionCode> CREATOR = new Creator<VersionCode>()
  {
    @Override
    public VersionCode createFromParcel(Parcel source)
    {
      return new VersionCode(source);
    }

    @Override
    public VersionCode[] newArray(int size)
    {
      return new VersionCode[size];
    }
  };
}