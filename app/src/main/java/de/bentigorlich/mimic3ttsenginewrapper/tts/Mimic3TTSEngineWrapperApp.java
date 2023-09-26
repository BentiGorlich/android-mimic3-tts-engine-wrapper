package de.bentigorlich.mimic3ttsenginewrapper.tts;

import android.app.Application;
import android.content.Context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Mimic3TTSEngineWrapperApp extends Application {
    private static Context storageContext;
    public static final String PREF_LANGUAGE = "selected_language";
    public static final String PREF_VOICE = "selected_voice";
    public static final String PREF_SPEAKER = "selected_speaker";
    public static final String PREF_SPEED = "speech_speed";
    public static final String PREF_SERVER_ADDRESS = "server_address";
    public static final String PREF_AUDIO_VOLATILITY = "audio_volatility";
    public static final String PREF_PHONEME_VOLATILITY = "phoneme_volatility";
    public static final String PREF_CACHE_ACTIVATE = "cache_activate";
    public static final String PREF_CACHE_SIZE = "cache_size";
    public static final String PREF_CACHE_CLEAR = "cache_clear";

    public static String getSha256Hex(String text) throws NoSuchAlgorithmException {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        final byte[] hashBytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hashBytes);
    }

    public static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if(hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public void onCreate() {
        super.onCreate();
        Mimic3TTSEngineWrapperApp.storageContext = getApplicationContext();
    }

    public static Context getStorageContext() {
        return Mimic3TTSEngineWrapperApp.storageContext;
    }
}
