package de.bentigorlich.mimic3ttsenginewrapper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class CheckTTSDataActivity extends Activity {

    Logger _Logger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        _Logger = Logger.getLogger(this.getClass().toString());
        LogManager.getLogManager().addLogger(_Logger);

        String action = getIntent().getAction();
        if(action != null) {
            if(action.equals(TextToSpeech.Engine.ACTION_GET_SAMPLE_TEXT)) {
                getSampleTextForIntent();
            } else if (action.equals(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)) {
                getCheckTTSDataForIntent();
            } else {
                _Logger.warning("called with action that is not implemented: " + action);
            }
        }
    }

    private void getSampleTextForIntent() {
        if(Mimic3TTSEngineWeb.s_RunningService != null) {
            final Intent intent = getIntent();
            final String language = intent.getStringExtra("language");
            final String country = intent.getStringExtra("country");
            final String variant = intent.getStringExtra("variant");
            String voiceName = Mimic3TTSEngineWeb.s_RunningService.onGetDefaultVoiceNameFor(language, country, variant);
            List<MimicVoice> voices = Mimic3TTSEngineWeb.s_RunningService.getMimicVoices();
            Optional<MimicVoice> matchVoice = voices.stream().filter(voice -> voice.key.equals(voiceName)).findAny();
            int result;
            String text = null;
            if(matchVoice.isPresent()) {
                MimicVoice v = matchVoice.get();
                text = v.sample_text;
                result = TextToSpeech.LANG_AVAILABLE;
            } else {
                result = TextToSpeech.LANG_NOT_SUPPORTED;
            }


            final Intent returnData = new Intent();
            if(text != null)
                returnData.putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, text);
            setResult(result, returnData);
            finish();
        } else {
            int result = TextToSpeech.LANG_NOT_SUPPORTED;
            final Intent returnData = new Intent();
            setResult(result, returnData);
            finish();
        }
    }

    private void getCheckTTSDataForIntent() {
        if(Mimic3TTSEngineWeb.s_RunningService != null) {
            List<MimicVoice> voices = Mimic3TTSEngineWeb.s_RunningService.getMimicVoices();
            ArrayList<String> availableVoices = new ArrayList<>();
            Locale[] availableLocales = Locale.getAvailableLocales();
            for(MimicVoice voice : voices) {
                String[] languageParts = voice.language.replace("_", "-").split("-");
                String language = languageParts[0];
                String country = languageParts.length == 2 ? languageParts[1] : null;
                Locale locale;
                Locale.Builder builder = new Locale.Builder();
                builder.setLanguage(language);
                if (country != null)
                    builder.setRegion(country);
                locale = builder.build();
                if (Arrays.stream(availableLocales).anyMatch(l -> l.toString().equals(locale.toString())))
                    availableVoices.add(locale.toString());
            }
            final Intent returnData = new Intent();
            returnData.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, availableVoices);
            returnData.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, new ArrayList<>());
            setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, returnData);
            finish();
        } else {
            final Intent returnData = new Intent();
            returnData.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, new ArrayList<>());
            returnData.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, new ArrayList<>());
            setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_FAIL, returnData);
            finish();
        }
    }
}