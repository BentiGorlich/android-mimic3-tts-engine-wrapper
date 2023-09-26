package de.bentigorlich.mimic3ttsenginewrapper.activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import de.bentigorlich.mimic3ttsenginewrapper.tts.Mimic3TTSEngineWeb;
import de.bentigorlich.mimic3ttsenginewrapper.tts.Mimic3TTSEngineWrapperApp;
import de.bentigorlich.mimic3ttsenginewrapper.R;

public class SettingsActivity extends AppCompatActivity implements Mimic3TTSEngineWeb.OnErrorListener, Preference.OnPreferenceChangeListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.settingsView, SettingsFragment.class, null)
                .commit();
        if(Mimic3TTSEngineWeb.s_RunningService != null) {
            Mimic3TTSEngineWeb.s_RunningService.addErrorListener(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(Mimic3TTSEngineWeb.s_RunningService != null) {
            Mimic3TTSEngineWeb.s_RunningService.removeErrorListener(this);
        }
    }

    @Override
    public void onError(String error) {
        Context settings = this;
        runOnUiThread(() -> new AlertDialog.Builder(settings)
                .setTitle(R.string.tts_server_error)
                .setMessage(error)
                .setPositiveButton(R.string.ok, null)
                .show());

    }

    @Override
    public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
        if(preference.getKey().equals(Mimic3TTSEngineWrapperApp.PREF_CACHE_SIZE)) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(Mimic3TTSEngineWrapperApp.getStorageContext());
            String sizeString = preferences.getString(Mimic3TTSEngineWrapperApp.PREF_CACHE_SIZE, "2");
            float size = Float.parseFloat(sizeString);
            if(Mimic3TTSEngineWeb.s_RunningService != null) {
                Mimic3TTSEngineWeb.s_RunningService.setCacheSize(size);
            }
        }
        return true;
    }
}