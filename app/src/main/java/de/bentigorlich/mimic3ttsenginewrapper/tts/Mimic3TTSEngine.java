package de.bentigorlich.mimic3ttsenginewrapper.tts;

import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import android.speech.tts.Voice;
//import com.chaquo.python.PyObject;
//import com.chaquo.python.Python;
//import com.chaquo.python.android.AndroidPlatform;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import de.bentigorlich.mimic3ttsenginewrapper.entities.MimicVoice;

public class Mimic3TTSEngine extends TextToSpeechService {

    //private final PyObject TtsModule;
    private List<MimicVoice> Voices;
    private MimicVoice CurrentVoice;
    private String CurrentSpeaker;

    public Mimic3TTSEngine() {
        /*
        if(!Python.isStarted())
            Python.start(new AndroidPlatform(this));

        Python py = Python.getInstance();
        TtsModule = py.getModule("tts");
        List<PyObject> pyVoices = TtsModule.callAttr("init", "--voices")
                .asList();
        List<MimicVoice> voices = new ArrayList<>();
        for (PyObject pyVoice: pyVoices) {
            voices.add(pyVoice.toJava(MimicVoice.class));
        }
        Voices = voices;
         */
    }

    @Override
    protected int onIsLanguageAvailable(String lang, String country, String variant) {
        for(MimicVoice voice: Voices) {
            if(voice.language.equalsIgnoreCase(lang)) {
                for (String speaker : voice.speakers) {
                    if(speaker.equalsIgnoreCase(variant)) {
                        return TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE;
                    }
                }
                return TextToSpeech.LANG_COUNTRY_AVAILABLE;
            }
        }
        return TextToSpeech.LANG_NOT_SUPPORTED;
    }

    @Override
    public int onIsValidVoiceName (String voiceName) {
        for(MimicVoice voice : Voices) {
            String currVoiceName = voice.key;
            if(voice.speakers != null && voice.speakers.length > 0) {
                for (String speaker : voice.speakers) {
                    currVoiceName = voice.key + "#" + speaker;
                    if(currVoiceName.equalsIgnoreCase(voiceName))
                        return TextToSpeech.SUCCESS;
                }
            } else {
                if(currVoiceName.equalsIgnoreCase(voiceName))
                    return TextToSpeech.SUCCESS;
            }
        }
        return TextToSpeech.ERROR;
    }

    @Override
    public List<Voice> onGetVoices () {
        List<Voice> androidVoices = new ArrayList<>();
        for(MimicVoice voice : Voices) {
            Voice androidVoice = new Voice(voice.key, new Locale(voice.language), Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL, false, new HashSet<>());
            if(voice.speakers != null && voice.speakers.length > 0) {
                for (String speaker : voice.speakers) {
                    androidVoice = new Voice(voice.key + "#" + speaker, new Locale(voice.language), Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL, false, new HashSet<>());
                    androidVoices.add(androidVoice);
                }
            } else {
                androidVoices.add(androidVoice);
            }
        }
        return androidVoices;
    }

    @Override
    protected String[] onGetLanguage() {
        if(CurrentVoice == null)
            return new String[0];
        return new String[] {CurrentVoice.language, "", CurrentSpeaker};
    }

    @Override
    protected int onLoadLanguage(String lang, String country, String variant) {
        return onIsLanguageAvailable(lang, country, variant);
    }

    @Override
    protected void onStop() { }

    @Override
    protected void onSynthesizeText(SynthesisRequest synthesisRequest, SynthesisCallback synthesisCallback) {
        /*
        synthesisCallback.start(22050, AudioFormat.ENCODING_PCM_16BIT, 2);
        String ssml = String.format("<speak><prosody rate=\"%s\">%s</prosody></speak>", synthesisRequest.getSpeechRate(), synthesisRequest.getCharSequenceText().toString());
        String[] args = new String[] { "--voice", synthesisRequest.getVoiceName(), "--ssml", ssml };
        PyObject result = TtsModule.callAttr("init", (Object) args);
        Set<PyObject> resultSet = result.asSet();
        byte[] bytes = new byte[resultSet.size()];
        int i = 0;
        for(PyObject b : resultSet) {
            bytes[i] = b.toByte();
            i++;
        }
        int bufferSize = synthesisCallback.getMaxBufferSize();
        for(i = 0; i<bytes.length / bufferSize; i++) {
            int offset = i * bufferSize;
            int end = offset + bufferSize;
            if(end > bytes.length)
                end = bytes.length;
            byte[] bytesSlice = Arrays.copyOfRange(bytes, offset, end);
            synthesisCallback.audioAvailable(bytesSlice, 0, bytesSlice.length);
        }
        synthesisCallback.done();
         */
    }

    @Override
    public String onGetDefaultVoiceNameFor(String lang, String country, String variant) {
        for(MimicVoice voice : Voices) {
            if(!voice.language.equalsIgnoreCase(lang))
                continue;
            String currVoiceName = voice.key + " | default";
            if(voice.speakers != null && voice.speakers.length > 0) {
                for (String speaker : voice.speakers) {
                    currVoiceName = voice.key + " | " + speaker;
                    if(speaker.equalsIgnoreCase(variant))
                        return currVoiceName;
                }
            } else {
                return currVoiceName;
            }
        }
        return null;
    }

    public List<MimicVoice> GetMimicVoices() {
        List<MimicVoice> copy = new ArrayList<>(Voices.size());
        for(int i = 0; i<Voices.size(); i++) {
            copy.set(i, Voices.get(i).clone());
        }
        return copy;
    }
}
