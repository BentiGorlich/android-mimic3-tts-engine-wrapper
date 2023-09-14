package de.bentigorlich.mimic3ttsenginewrapper;

import androidx.annotation.NonNull;

import java.util.Date;

public class CacheEntry implements Cloneable {
    public String Text;
    public Date LastUsed;
    public long ByteSize;

    @NonNull
    @Override
    public CacheEntry clone() {
        CacheEntry copy = new CacheEntry();
        copy.Text = Text;
        copy.LastUsed = LastUsed;
        copy.ByteSize = ByteSize;
        return copy;
    }
}
