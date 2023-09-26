package de.bentigorlich.mimic3ttsenginewrapper.activities;

import static de.bentigorlich.mimic3ttsenginewrapper.tts.Mimic3TTSEngineWrapperApp.PREF_AUDIO_VOLATILITY;
import static de.bentigorlich.mimic3ttsenginewrapper.tts.Mimic3TTSEngineWrapperApp.PREF_CACHE_ACTIVATE;
import static de.bentigorlich.mimic3ttsenginewrapper.tts.Mimic3TTSEngineWrapperApp.PREF_LANGUAGE;
import static de.bentigorlich.mimic3ttsenginewrapper.tts.Mimic3TTSEngineWrapperApp.PREF_PHONEME_VOLATILITY;
import static de.bentigorlich.mimic3ttsenginewrapper.tts.Mimic3TTSEngineWrapperApp.PREF_SERVER_ADDRESS;
import static de.bentigorlich.mimic3ttsenginewrapper.tts.Mimic3TTSEngineWrapperApp.PREF_SPEAKER;
import static de.bentigorlich.mimic3ttsenginewrapper.tts.Mimic3TTSEngineWrapperApp.PREF_SPEED;
import static de.bentigorlich.mimic3ttsenginewrapper.tts.Mimic3TTSEngineWrapperApp.PREF_VOICE;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import de.bentigorlich.mimic3ttsenginewrapper.tts.Mimic3TTSEngineWeb;
import de.bentigorlich.mimic3ttsenginewrapper.tts.Mimic3TTSEngineWrapperApp;
import de.bentigorlich.mimic3ttsenginewrapper.entities.MimicVoice;
import de.bentigorlich.mimic3ttsenginewrapper.R;
import de.bentigorlich.mimic3ttsenginewrapper.tts.SynthesisListener;
import de.bentigorlich.mimic3ttsenginewrapper.util.JsonFormatter;

public class MainActivity extends AppCompatActivity implements SeekBar.OnSeekBarChangeListener,
        AdapterView.OnItemSelectedListener, View.OnClickListener, Mimic3TTSEngineWeb.OnVoicesLoadedListener,
        SharedPreferences.OnSharedPreferenceChangeListener, Mimic3TTSEngineWeb.OnLoadedListener, Mimic3TTSEngineWeb.OnErrorListener {
    List<MimicVoice> Voices;
    HashMap<String, HashMap<String, List<String>>> Languages;
    HashMap<String, MimicVoice> VoiceMap;

    private String SelectedLanguage;
    private String SelectedVoice;
    private String SelectedSpeaker;
    private int SpeechSpeed;
    private int AudioVolatility;
    private int PhonemeVolatility;
    SharedPreferences SharedPreferences;

    private final Logger _Logger;

    public MainActivity()  {
        _Logger = Logger.getLogger(this.getClass().toString());
        LogManager.getLogManager().addLogger(_Logger);

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setSupportActionBar(findViewById(R.id.toolbar));
        setContentView(R.layout.activity_main);

        String cacheDir = getDataDir().getAbsolutePath();
        try {
            FileHandler fileHandler = new FileHandler(cacheDir + "/main_%g.log", 1024 * 1024, 1, true);
            fileHandler.setFormatter(new JsonFormatter());
            _Logger.addHandler(fileHandler);
        } catch (IOException e) {
            _Logger.severe("IOException while adding filehandler to logger: " + e.getMessage());
            for(StackTraceElement el : e.getStackTrace()) {
                _Logger.warning("at: " + el.toString());
            }
        }

        SharedPreferences = PreferenceManager.getDefaultSharedPreferences(Mimic3TTSEngineWrapperApp.getStorageContext());
        SharedPreferences.registerOnSharedPreferenceChangeListener(this);
        SelectedLanguage = SharedPreferences.getString(PREF_LANGUAGE, "");
        SelectedVoice = SharedPreferences.getString(PREF_VOICE, "");
        SelectedSpeaker = SharedPreferences.getString(PREF_SPEAKER, "");
        SpeechSpeed = SharedPreferences.getInt(PREF_SPEED, 100);
        AudioVolatility = SharedPreferences.getInt(PREF_AUDIO_VOLATILITY, 677);
        PhonemeVolatility = SharedPreferences.getInt(PREF_PHONEME_VOLATILITY, 800);

        Mimic3TTSEngineWeb.s_ServerAddress = SharedPreferences.getString(PREF_SERVER_ADDRESS, "");
        Mimic3TTSEngineWeb.addLoadedListener(this);
        if (Mimic3TTSEngineWeb.s_RunningService == null) {
            Intent startIntent = new Intent(MainActivity.this, Mimic3TTSEngineWeb.class);
            startIntent.putExtra(PREF_SERVER_ADDRESS, Mimic3TTSEngineWeb.s_ServerAddress);
            startService(startIntent);
        } else {
            onVoicesLoaded(Mimic3TTSEngineWeb.s_RunningService.getMimicVoices());
        }

        adjustMissingServerAddressError();

        findViewById(R.id.btn_speak).setOnClickListener(this);
        findViewById(R.id.lbl_audio_volatility).setOnClickListener(this);
        findViewById(R.id.lbl_phonemeVolatility).setOnClickListener(this);
        SeekBar s = findViewById(R.id.speedBar);
        s.setOnSeekBarChangeListener(this);
        s.setProgress(SpeechSpeed);

        s = findViewById(R.id.audioVolatilityBar);
        s.setOnSeekBarChangeListener(this);
        s.setProgress(AudioVolatility);

        s = findViewById(R.id.phonemeVolatilityBar);
        s.setOnSeekBarChangeListener(this);
        s.setProgress(PhonemeVolatility);
    }

    private void adjustMissingServerAddressError() {
        if (Mimic3TTSEngineWeb.s_ServerAddress == null || Mimic3TTSEngineWeb.s_ServerAddress.equals("") || Mimic3TTSEngineWeb.s_ServerAddress.equals("https://") || Mimic3TTSEngineWeb.s_ServerAddress.equals("http://"))
            findViewById(R.id.server_missing).setVisibility(View.VISIBLE);
        else
            findViewById(R.id.server_missing).setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Mimic3TTSEngineWeb.removeLoadedListener(this);
        SharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        if (Mimic3TTSEngineWeb.s_RunningService != null) {
            Mimic3TTSEngineWeb.s_RunningService.removeVoicesLoadedListener(this);
            Mimic3TTSEngineWeb.s_RunningService.removeErrorListener(this);
        }
    }

    private void setLanguageItems(String[] languages) {
        Arrays.sort(languages);
        Spinner languageDD = findViewById(R.id.language);
        languageDD.setAdapter(new ArrayAdapter<>(this, androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, languages));
        for (int i = 0; i < languages.length; i++) {
            if (languages[i].equals(SelectedLanguage))
                languageDD.setSelection(i);
        }
    }

    public void setSelectedLanguage(String language) {
        if(!language.equals(SelectedLanguage)) {
            SelectedLanguage = language;
            SharedPreferences.edit().putString(PREF_LANGUAGE, SelectedLanguage).apply();

            if (Mimic3TTSEngineWeb.s_RunningService != null) {
                Mimic3TTSEngineWeb.s_RunningService.clearCache(true);
            }
        }
    }

    private void setVoiceItems(String[] voices) {
        Spinner voicesDD = findViewById(R.id.voices);
        voicesDD.setAdapter(new ArrayAdapter<>(this, androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, voices));
        for (int i = 0; i < voices.length; i++) {
            if (voices[i].equals(SelectedVoice))
                voicesDD.setSelection(i);
        }
    }

    public void setSelectedVoice(String voice) {
        if(!voice.equals(SelectedVoice)) {
            SelectedVoice = voice;
            SharedPreferences.edit().putString(PREF_VOICE, SelectedVoice).apply();

            if (Mimic3TTSEngineWeb.s_RunningService != null) {
                Mimic3TTSEngineWeb.s_RunningService.clearCache(true);
                MimicVoice mimicVoice = VoiceMap.get(voice);
                if(mimicVoice != null && (mimicVoice.speakers == null || mimicVoice.speakers.length == 0))
                    synthesizeDefaultStrings();
            }
        }
    }

    private void setSpeakerItems(String[] speakers) {
        Spinner speakerDD = findViewById(R.id.speakers);
        speakerDD.setAdapter(new ArrayAdapter<>(this, androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, speakers));
        for (int i = 0; i < speakers.length; i++) {
            if (speakers[i].equals(SelectedSpeaker))
                speakerDD.setSelection(i);
        }
    }

    public void setSelectedSpeaker(String speaker) {
        if(!speaker.equals(SelectedSpeaker)) {
            SelectedSpeaker = speaker;
            SharedPreferences.edit().putString(PREF_SPEAKER, SelectedSpeaker).apply();

            if (Mimic3TTSEngineWeb.s_RunningService != null) {
                Mimic3TTSEngineWeb.s_RunningService.clearCache(true);
                synthesizeDefaultStrings();
            }
        }
    }

    private void synthesizeDefaultStrings() {
        if (Mimic3TTSEngineWeb.s_RunningService != null) {
            _Logger.info("synthesizing default strings");
            HashMap<String, String> defaultStrings = new HashMap<String, String>() {{
                put("default_no_connection", getString(R.string.default_no_connection));
            }};

            String voice = SelectedVoice;
            if(SelectedSpeaker != null && !SelectedSpeaker.equals(""))
                voice += "#" + SelectedSpeaker;
            for(Map.Entry<String, String> s : defaultStrings.entrySet()) {
                Mimic3TTSEngineWeb.s_RunningService.dispatchSynthesisRequest(s.getValue(), voice, SpeechSpeed, new SynthesisListener(false), s.getKey());
            }
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (parent.getId() == R.id.language) {
            setSelectedLanguage((String) parent.getItemAtPosition(position));
            HashMap<String, List<String>> voices = Languages.get(SelectedLanguage);
            if (voices != null) {
                setVoiceItems(voices.keySet().toArray(new String[0]));
            }
        } else if (parent.getId() == R.id.voices) {
            setSelectedVoice((String) parent.getItemAtPosition(position));
            HashMap<String, List<String>> voices = Languages.get(SelectedLanguage);
            if (voices != null) {
                List<String> speakers = voices.get(SelectedVoice);
                if (speakers != null) {
                    setSpeakerItems(speakers.toArray(new String[0]));
                }
            }
        } else if (parent.getId() == R.id.speakers) {
            setSelectedSpeaker((String) parent.getItemAtPosition(position));
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        if (parent.getId() == R.id.language) {
            SelectedLanguage = null;
            SelectedVoice = null;
            SelectedSpeaker = null;
            setVoiceItems(new String[0]);
            setSpeakerItems(new String[0]);

            if (Mimic3TTSEngineWeb.s_RunningService != null) {
                Mimic3TTSEngineWeb.s_RunningService.clearCache(true);
            }
        } else if (parent.getId() == R.id.voices) {
            SelectedVoice = null;
            SelectedSpeaker = null;
            setSpeakerItems(new String[0]);

            if (Mimic3TTSEngineWeb.s_RunningService != null) {
                Mimic3TTSEngineWeb.s_RunningService.clearCache(true);
            }
        } else if (parent.getId() == R.id.speakers) {
            SelectedSpeaker = null;

            if (Mimic3TTSEngineWeb.s_RunningService != null) {
                Mimic3TTSEngineWeb.s_RunningService.clearCache(true);
            }
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.btn_speak) {
            EditText input = findViewById(R.id.testText);
            String inputText = input.getText().toString();
            if (Mimic3TTSEngineWeb.s_RunningService != null && !inputText.equals("") && SelectedVoice != null && !SelectedVoice.equals("")) {
                String voice = SelectedVoice;
                if (SelectedSpeaker != null && !SelectedSpeaker.equals(""))
                    voice += "#" + SelectedSpeaker;
                Mimic3TTSEngineWeb.s_RunningService.dispatchSynthesisRequest(inputText, voice, SpeechSpeed, new SynthesisListener(true), null);
            }
        } else if(view.getId() == R.id.lbl_audio_volatility) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.lbl_audio_volatility)
                    .setMessage(R.string.lbl_audio_volatility_tooltip)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        } else if(view.getId() == R.id.lbl_phonemeVolatility) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.lbl_phoneme_volatility)
                    .setMessage(R.string.lbl_phoneme_volatility_tooltip)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        }
    }

    public void onMenuItemClick(@NonNull MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.menu_settings) {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        } else if (menuItem.getItemId() == R.id.menu_logs) {
            startActivity(new Intent(MainActivity.this, LogActivity.class));
        }
    }

    @Override
    public void onVoicesLoaded(List<MimicVoice> voices) {
        MainActivity main = this;
        runOnUiThread(() -> {
            Languages = new HashMap<>();
            VoiceMap = new HashMap<>();
            Voices = voices;
            for (MimicVoice voice : Voices) {
                if (!Languages.containsKey(voice.language)) {
                    Languages.put(voice.language, new HashMap<>());
                }
                HashMap<String, List<String>> voiceMap = Languages.get(voice.language);
                if(voiceMap == null)
                    continue;

                List<String> speakers = new ArrayList<>();
                if (voice.speakers != null)
                    speakers = Arrays.asList(voice.speakers);
                voiceMap.put(voice.key, speakers);
                VoiceMap.put(voice.key, voice);
            }

            Spinner languageDD = findViewById(R.id.language);
            languageDD.setOnItemSelectedListener(main);
            Spinner voicesDD = findViewById(R.id.voices);
            voicesDD.setOnItemSelectedListener(main);
            Spinner speakerDD = findViewById(R.id.speakers);
            speakerDD.setOnItemSelectedListener(main);

            String[] locales = Resources.getSystem().getAssets().getLocales();
            setLanguageItems(Languages.keySet().toArray(new String[0]));
        });
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(PREF_SERVER_ADDRESS)) {
            Mimic3TTSEngineWeb.s_ServerAddress = sharedPreferences.getString(PREF_SERVER_ADDRESS, "");
            if (Mimic3TTSEngineWeb.s_RunningService != null)
                Mimic3TTSEngineWeb.s_RunningService.triggerLoadVoices();
            adjustMissingServerAddressError();
        } else if (key.equals(PREF_CACHE_ACTIVATE)) {
            boolean cacheActive = sharedPreferences.getBoolean(PREF_CACHE_ACTIVATE, true);
            if(!cacheActive && Mimic3TTSEngineWeb.s_RunningService != null) {
                Mimic3TTSEngineWeb.s_RunningService.setCacheSize(0);
            }
        }
    }

    @Override
    public void onLoaded() {
        Mimic3TTSEngineWeb.s_RunningService.addVoicesLoadedListener(this);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean b) { }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) { }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        int value = seekBar.getProgress();
        boolean changed = false;
        if (seekBar.getId() == R.id.speedBar) {
            SpeechSpeed = value;
            SharedPreferences.edit().putInt(PREF_SPEED, SpeechSpeed).apply();
            changed = true;
        } else if (seekBar.getId() == R.id.audioVolatilityBar) {
            AudioVolatility = value;
            SharedPreferences.edit().putInt(PREF_AUDIO_VOLATILITY, AudioVolatility).apply();
            changed = true;
        } else if (seekBar.getId() == R.id.phonemeVolatilityBar) {
            PhonemeVolatility = value;
            SharedPreferences.edit().putInt(PREF_PHONEME_VOLATILITY, PhonemeVolatility).apply();
            changed = true;
        }

        if(changed && Mimic3TTSEngineWeb.s_RunningService != null) {
            Mimic3TTSEngineWeb.s_RunningService.clearCache(true);
            if(SelectedVoice != null && !SelectedVoice.equals("") && VoiceMap.containsKey(SelectedVoice)) {
                MimicVoice voice = VoiceMap.get(SelectedVoice);
                if(voice != null && (voice.speakers == null || voice.speakers.length == 0 || (SelectedSpeaker != null && !SelectedSpeaker.equals("")))) {
                    synthesizeDefaultStrings();
                }
            }
        }
    }

    @Override
    public void onError(String error) {
        Context main = this;
        runOnUiThread(() -> new AlertDialog.Builder(main)
                .setTitle(R.string.tts_server_error)
                .setMessage(error)
                .setPositiveButton(R.string.ok, null)
                .show());
    }
}