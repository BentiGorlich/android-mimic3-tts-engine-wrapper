package de.bentigorlich.mimic3ttsenginewrapper;

import androidx.annotation.NonNull;

public class MimicVoice implements Cloneable {
    public String key;
    public String name;
    public String language;
    public String description;
    public String location;
    public String[] speakers;
    public String[] aliases;
    public String[] version;
    public String sample_text;

    @NonNull
    @Override
    public MimicVoice clone() {
        MimicVoice copy = new MimicVoice();
        copy.key = key;
        copy.name = name;
        copy.language = language;
        copy.description = description;
        copy.location = location;
        copy.speakers = speakers;
        copy.aliases = aliases;
        copy.version = version;
        copy.sample_text = sample_text;

        return copy;
    }
}
