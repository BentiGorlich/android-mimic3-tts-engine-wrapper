package de.bentigorlich.mimic3ttsenginewrapper;

import android.app.AlertDialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceClickListener {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);
        Preference cacheClear = findPreference("cache_clear");
        if(cacheClear != null)
            cacheClear.setOnPreferenceClickListener(this);
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        if(preference.getKey().equals("cache_clear")) {
            if(Mimic3TTSEngineWeb.s_RunningService != null) {
                Mimic3TTSEngineWeb.s_RunningService.clearCache(false);
                new AlertDialog.Builder(getContext())
                        .setMessage(R.string.cache_reset_successful)
                        .setPositiveButton(R.string.ok, null)
                        .show();
            } else {
                new AlertDialog.Builder(getContext())
                        .setMessage(R.string.cache_reset_failed)
                        .setPositiveButton(R.string.ok, null)
                        .show();
            }
        }
        return false;
    }
}