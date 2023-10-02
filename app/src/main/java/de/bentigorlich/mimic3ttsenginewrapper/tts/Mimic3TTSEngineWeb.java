package de.bentigorlich.mimic3ttsenginewrapper.tts;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import android.speech.tts.Voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.FileHandler;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import de.bentigorlich.mimic3ttsenginewrapper.entities.CacheEntry;
import de.bentigorlich.mimic3ttsenginewrapper.entities.MimicVoice;
import de.bentigorlich.mimic3ttsenginewrapper.util.JsonFormatter;

public class Mimic3TTSEngineWeb extends TextToSpeechService {

    public interface OnVoicesLoadedListener {
        void onVoicesLoaded(List<MimicVoice> voices);
    }

    public interface OnLoadedListener {
        void onLoaded();
    }

    public interface OnErrorListener {
        void onError(String error);
    }

    public static class CacheFile {
        ArrayList<CacheEntry> Cache;
        ArrayList<KVP<String, CacheEntry>> SpecialCache;
        ArrayList<MimicVoice> Voices;
    }

    public static class KVP<K, V> {
        public K Key;
        public V Value;
        public KVP(K key, V value) {
            Key = key;
            Value = value;
        }
    }

    public static Mimic3TTSEngineWeb s_RunningService;
    public static String s_ServerAddress;
    private static final List<OnLoadedListener> s_OnLoadedListeners = new ArrayList<>();
    private List<MimicVoice> Voices = new ArrayList<>();

    private final Logger _Logger;
    private Thread T;
    private boolean Running;
    private boolean FetchVoices = true;
    private boolean SaveCache = false;

    private long MaxCacheSizeInB = 2L * 1024 * 1024 * 1024;
    private float MaxCacheSizeInGB = 2;
    private long CurrentCacheSize = 0;
    private final Lock CacheLock = new ReentrantLock();

    private final List<OnVoicesLoadedListener> OnVoicesLoadedListeners = new ArrayList<>();
    private final List<OnErrorListener> OnErrorListeners = new ArrayList<>();

    private boolean SynthesisRequest = false;
    private String SynthesisText;
    private int SynthesisSpeechRate;
    private String SynthesisVoice;
    private String SynthesisSpecialKey = null;
    private SynthesisCallback Callback;

    private final Map<String, Locale> LocaleMap;
    private final Map<String, Locale> CountryMap;

    private Map<String, CacheEntry> Cache = new HashMap<>();
    private final Map<String, CacheEntry> SpecialCache = new HashMap<>();

    private final Timer cacheFlushInterval;

    public Mimic3TTSEngineWeb() {
        _Logger = Logger.getLogger("de.bentigorlich.mimic3ttsenginewrapper.tts.Mimic3TTSEngineWeb");
        LogManager.getLogManager().addLogger(_Logger);

        String cacheDir = Mimic3TTSEngineWrapperApp.getStorageContext().getDataDir().getAbsolutePath();
        try {
            FileHandler fileHandler = new FileHandler(cacheDir + "/tts_%g.log", 1024 * 1024, 1, true);
            fileHandler.setFormatter(new JsonFormatter());
            _Logger.addHandler(fileHandler);
        } catch (IOException e) {
            _Logger.severe("IOException while adding filehandler to logger: " + e.getMessage());
            for(StackTraceElement el : e.getStackTrace()) {
                _Logger.warning("at: " + el.toString());
            }
        }

        _Logger.info("Instantiated Mimic3TTSEngineWeb");

        String[] languages = Locale.getISOLanguages();
        LocaleMap = new HashMap<>(languages.length);
        CountryMap = new HashMap<>(languages.length);
        for (String language : languages) {
            Locale locale = new Locale(language);
            LocaleMap.put(locale.getISO3Language(), locale);
            if(!locale.getISO3Country().equals(""))
                CountryMap.put(locale.getISO3Country(), locale);
        }

        try {
            buildCache();
            if(Voices != null && Voices.size() > 0) {
                FetchVoices = false;
            }
        } catch (Exception ex) {
            _Logger.severe("An unhandled exception occurred: " + ex.getClass().getName() + ": " + ex.getMessage());
            for(StackTraceElement el : ex.getStackTrace()) {
                _Logger.warning("at: " + el.toString());
            }
        }

        cacheFlushInterval = new Timer();
        cacheFlushInterval.schedule(new TimerTask() {
            @Override
            public void run() {
                saveCache();
            }
        }, 30000, 600000);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        _Logger.info("Created Mimic3TTSEngineWeb");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        _Logger.info("Started Mimic3TTSEngineWeb");
        if(intent != null && intent.getAction() != null && !intent.getAction().equals(""))
            _Logger.info("got action: " + intent.getAction());
        s_RunningService = this;
        Running = true;
        if(intent != null) {
            String address = intent.getStringExtra("server_address");
            if (address != null && !address.equals(""))
                s_ServerAddress = address;
        }
        T = new Thread(this::main);
        T.start();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public boolean stopService(Intent name) {
        _Logger.info("stopping TTS service");
        s_RunningService = null;
        Running = false;
        cacheFlushInterval.cancel();
        saveCache();
        return super.stopService(name);
    }

    @Override
    public void onDestroy() {
        _Logger.info("destroying TTS service");
        cacheFlushInterval.cancel();
        saveCache();
        super.onDestroy();
    }

    private void main() {
        for (OnLoadedListener listener : s_OnLoadedListeners) {
            listener.onLoaded();
        }

        if(Voices != null && Voices.size() > 0) {
            for(OnVoicesLoadedListener listener : OnVoicesLoadedListeners) {
                listener.onVoicesLoaded(Voices);
            }
        }

        while(Running) {
            try {
                if (FetchVoices) {
                    loadVoices();
                    FetchVoices = false;
                } else if (SynthesisRequest) {
                    synthesizeText(SynthesisText, SynthesisVoice, SynthesisSpeechRate, Callback, SynthesisSpecialKey);
                    SynthesisRequest = false;
                } else if (SaveCache) {
                    saveCache();
                    SaveCache = false;
                } else {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                    }
                }
            } catch (Exception ex) {
                _Logger.severe("An unhandled exception occurred: " + ex.getClass().getName() + ": " + ex.getMessage());
                for(StackTraceElement el : ex.getStackTrace()) {
                    _Logger.warning("at: " + el.toString());
                }
            }
        }
    }

    private void loadVoices() {
        List<MimicVoice> voices = new ArrayList<>();
        if(s_ServerAddress != null && !s_ServerAddress.equals("") && !s_ServerAddress.equals("https://")) {
            String slash = "";
            if (!s_ServerAddress.endsWith("/"))
                slash = "/";
            _Logger.info("Fetching Voices from " + s_ServerAddress + slash + "api/voices");
            try {
                URL url = new URL(s_ServerAddress + slash + "api/voices");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                try {
                    BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
                    _Logger.info("Got raw data");
                    Type listType = new TypeToken<ArrayList<MimicVoice>>() {}.getType();
                    Gson gson = new Gson();
                    String rawData = new BufferedReader(new InputStreamReader(in)).lines().collect(Collectors.joining());
                    voices = gson.fromJson(rawData, listType);
                    _Logger.info("Got Voices");
                } finally {
                    conn.disconnect();
                }
            } catch (MalformedURLException ex) {
                _Logger.severe("Malformed server url: " + ex.getMessage());
                ex.printStackTrace();
                for (OnErrorListener listener: OnErrorListeners) {
                    listener.onError(ex.getMessage());
                }
            } catch (IOException ex) {
                _Logger.severe("Connection error: " + ex.getMessage());
                ex.printStackTrace();
                for (StackTraceElement el : ex.getStackTrace()) {
                    _Logger.warning("at " + el.toString());
                }
                for (OnErrorListener listener: OnErrorListeners) {
                    listener.onError(ex.getMessage());
                }
            } catch (JsonSyntaxException | IllegalStateException ex) {
                _Logger.severe("Json error: " + ex.getMessage());
                ex.printStackTrace();
                for (StackTraceElement el : ex.getStackTrace()) {
                    _Logger.warning("at " + el.toString());
                }
                for (OnErrorListener listener: OnErrorListeners) {
                    listener.onError(ex.getMessage());
                }
            } catch (Exception ex) {
                _Logger.severe("Error: " + ex.getMessage());
                ex.printStackTrace();
                for (StackTraceElement el : ex.getStackTrace()) {
                    _Logger.warning("at " + el.toString());
                }
                for (OnErrorListener listener: OnErrorListeners) {
                    listener.onError(ex.getMessage());
                }
            }
        }
        Voices = voices;
        voices = getMimicVoices();
        for (OnVoicesLoadedListener listener: OnVoicesLoadedListeners) {
            listener.onVoicesLoaded(voices);
        }
    }

    @Override
    protected int onIsLanguageAvailable(String lang, String country, String variant) {
        if(LocaleMap.containsKey(lang))
            lang = Objects.requireNonNull(LocaleMap.get(lang)).getLanguage();
        if(CountryMap.containsKey(country))
            country = Objects.requireNonNull(CountryMap.get(country)).getCountry();

        for(MimicVoice voice: Voices) {
            String[] voiceParts = voice.language.split("[-_/]");
            if (voiceParts.length >= 1 && voiceParts[0].equalsIgnoreCase(lang)) {
                if(voiceParts.length >= 2 && voiceParts[1].equalsIgnoreCase(country)) {
                    return TextToSpeech.LANG_COUNTRY_AVAILABLE;
                }
                return TextToSpeech.LANG_AVAILABLE;
            }
        }
        _Logger.warning("we don't support: " + lang + "-" + country + "-" + variant);
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
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(Mimic3TTSEngineWrapperApp.getStorageContext());
        String language = preferences.getString(Mimic3TTSEngineWrapperApp.PREF_LANGUAGE, "en_US");
        _Logger.info("Someone requested the current language: " + language);
        return language.split("[-_]");
    }

    @Override
    protected int onLoadLanguage(String lang, String country, String variant) {
        _Logger.info("We shall load: " + lang + "-" + country + "-" + variant);
        return onIsLanguageAvailable(lang, country, variant);
    }

    @Override
    protected void onStop() { }

    @Override
    protected void onSynthesizeText(SynthesisRequest synthesisRequest, SynthesisCallback synthesisCallback) {
        synthesizeText(synthesisRequest.getCharSequenceText().toString(), synthesisRequest.getVoiceName(), synthesisRequest.getSpeechRate(), synthesisCallback, null);
    }

    protected void synthesizeText(String text, @Nullable String voice, int speechRate, SynthesisCallback synthesisCallback, String specialKey) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(Mimic3TTSEngineWrapperApp.getStorageContext());
        String prefVoiceKey = preferences.getString(Mimic3TTSEngineWrapperApp.PREF_VOICE, "");
        String speaker = preferences.getString(Mimic3TTSEngineWrapperApp.PREF_SPEAKER, null);
        MimicVoice prefVoice = null;
        MimicVoice givenVoice = null;
        boolean canUseCache = false;
        for(MimicVoice currVoice : Voices)  {
            if(currVoice.key.equals(prefVoiceKey)) {
                prefVoice = currVoice;
                canUseCache = true;
            } else if (voice != null && currVoice.key.equals(voice)) {
                givenVoice = currVoice;
            }
        }
        if(givenVoice == null || (prefVoice != null && givenVoice.language.equals(prefVoice.language))) {
            voice = prefVoiceKey;
            if(speaker != null)
                voice += "#" + speaker;
        }

        CacheEntry entry = getCacheEntry(text);
        boolean specialKeySet = specialKey != null && !specialKey.equals("");
        boolean useCache = canUseCache && ((specialKeySet && SpecialCache.containsKey(specialKey)) || entry != null);

        synthesisCallback.start(22050, AudioFormat.ENCODING_PCM_16BIT, 1);

        if(s_ServerAddress != null) {
            if(!useCache) {
                synthesizeTextFromUrl(preferences, speechRate, voice, text, synthesisCallback, specialKey);
            } else {
                try {
                    synthesizeTextFromCache(specialKeySet, specialKey, text, synthesisCallback);
                } catch (FileNotFoundException e) {
                    synthesizeTextFromUrl(preferences, speechRate, voice, text, synthesisCallback, specialKey);
                }
            }
        }
    }

    private void synthesizeTextFromUrl(SharedPreferences preferences, int speechRate, String voice, String text, SynthesisCallback synthesisCallback, String specialKey) {
        try {
            String slash = "";
            if (!s_ServerAddress.endsWith("/"))
                slash = "/";
            float lengthScale = (float)1 / ((float)speechRate/100);
            float noiseScale = (float)preferences.getInt(Mimic3TTSEngineWrapperApp.PREF_AUDIO_VOLATILITY, 667) / 1000;
            float noiseW = (float)preferences.getInt(Mimic3TTSEngineWrapperApp.PREF_PHONEME_VOLATILITY, 800) / 1000;
            String urlString = s_ServerAddress + slash + "api/tts?ssml=0&audioTarget=client&noiseScale=" + noiseScale + "&noiseW=" + noiseW + "&lengthScale=" + lengthScale + "&voice=" + URLEncoder.encode(voice, StandardCharsets.UTF_8.toString());
            _Logger.info("Synthesizing text with " + urlString + " : " + text);
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                byte[] outputBuffer = text.trim().getBytes(StandardCharsets.UTF_8);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(outputBuffer.length);
                BufferedOutputStream out = new BufferedOutputStream(conn.getOutputStream());
                out.write(outputBuffer, 0, outputBuffer.length);
                out.close();

                int status = conn.getResponseCode();
                String message = conn.getResponseMessage();
                InputStream in = new BufferedInputStream(conn.getInputStream());
                int nRead;
                int ttsMaxLength = TextToSpeech.getMaxSpeechInputLength();
                byte[] data = new byte[ttsMaxLength];
                ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

                _Logger.info("Got raw data");
                while ((nRead = in.read(data, 0, data.length)) != -1) {
                    byteBuffer.write(data, 0, nRead);
                }
                in.close();

                byte[] completeData = byteBuffer.toByteArray();
                _Logger.info("Got audio");
                for (int i = 0; i<completeData.length/ttsMaxLength; i++) {
                    int start = i * ttsMaxLength;
                    int end = start + ttsMaxLength;
                    if(completeData.length < end)
                        end = completeData.length;
                    synthesisCallback.audioAvailable(completeData, start, end - start);
                }

                synthesisCallback.done();
                CacheEntry cacheEntry = new CacheEntry();
                cacheEntry.Text = text;
                cacheEntry.ByteSize = completeData.length;
                addToCache(completeData, cacheEntry, specialKey);
            } finally {
                conn.disconnect();
            }
        } catch (MalformedURLException ex) {
            _Logger.severe("Malformed server url: " + ex.getMessage());
            synthesisCallback.error();
            synthesisCallback.done();
        } catch (IOException ex) {
            _Logger.severe("Connection error: " + ex.getMessage());
            if(SpecialCache.containsKey("default_no_connection")) {
                CacheEntry noConn = SpecialCache.get("default_no_connection");
                File noConnFile = new File(Mimic3TTSEngineWrapperApp.getStorageContext().getCacheDir(), "default_no_connection");
                if(noConnFile.exists()) {
                    try {
                        InputStream in = Files.newInputStream(noConnFile.toPath());
                        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
                        int nRead;
                        byte[] data = new byte[TextToSpeech.getMaxSpeechInputLength()];
                        _Logger.info("Got raw data");
                        while ((nRead = in.read(data, 0, data.length)) != -1) {
                            synthesisCallback.audioAvailable(data, 0, nRead);
                            byteBuffer.write(data, 0, nRead);
                        }
                        _Logger.info("Got audio");
                        synthesisCallback.done();
                        in.close();
                    } catch (IOException ex2) {
                        synthesisCallback.error();
                        synthesisCallback.done();
                        _Logger.severe("Cache error loading default_no_connection: " + ex2.getMessage());
                        ex.printStackTrace();
                        for (StackTraceElement el : ex.getStackTrace()) {
                            _Logger.warning("at: " + el.toString());
                        }
                    }
                } else {
                    synthesisCallback.error();
                    synthesisCallback.done();
                    _Logger.severe("default_no_connection was in cache, but file doesn't exist");
                }
            } else {
                synthesisCallback.error();
                synthesisCallback.done();
                _Logger.severe("default_no_connection was not in cache");
            }
        }
    }

    private void synthesizeTextFromCache(boolean specialKeySet, String specialKey, String text, SynthesisCallback synthesisCallback) throws FileNotFoundException {
        String key;
        if (!specialKeySet) {
            try {
                key = Mimic3TTSEngineWrapperApp.getSha256Hex(text);
            } catch (NoSuchAlgorithmException e) {
                _Logger.severe("sha256 is not supported, should never happen...");
                synthesisCallback.error();
                synthesisCallback.done();
                return;
            }
        } else {
            key = specialKey;
        }
        File cacheFile = new File(Mimic3TTSEngineWrapperApp.getStorageContext().getCacheDir(), key);
        if(!cacheFile.exists()) {
            _Logger.severe("Should synthesize from cache, but there is no cache file...");
            throw new FileNotFoundException(key);
        }

        _Logger.info("Synthesizing text with cache: " + text);
        try {
            InputStream in = new BufferedInputStream(Files.newInputStream(cacheFile.toPath()));
            int nRead;
            byte[] data = new byte[TextToSpeech.getMaxSpeechInputLength()];

            _Logger.info("Got raw data");
            while ((nRead = in.read(data, 0, data.length)) != -1) {
                synthesisCallback.audioAvailable(data, 0, nRead);
            }
            synthesisCallback.done();
        } catch (IOException ex) {
            _Logger.severe("IO error: " + ex.getMessage());
            ex.printStackTrace();
            for (StackTraceElement el : ex.getStackTrace()) {
                _Logger.warning("at " + el.toString());
            }
            synthesisCallback.error();
            synthesisCallback.done();
        }
    }

    private CacheEntry getCacheEntry(String text) {
        try {
            String key = Mimic3TTSEngineWrapperApp.getSha256Hex(text);
            if(Cache.containsKey(key))
                return Cache.get(key);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        return null;
    }

    public void dispatchSynthesisRequest(@NonNull String text, @NonNull String voice, int speechRate, @NonNull SynthesisCallback synthesisCallback, @Nullable String specialKey) {
        SynthesisText = text;
        SynthesisSpeechRate = speechRate;
        SynthesisVoice = voice;
        Callback = synthesisCallback;
        SynthesisRequest = true;
        SynthesisSpecialKey = specialKey;
    }

    @Override
    public String onGetDefaultVoiceNameFor(String lang, String country, String variant) {
        if(LocaleMap.containsKey(lang))
            lang = Objects.requireNonNull(LocaleMap.get(lang)).getLanguage();
        if(CountryMap.containsKey(country))
            country = Objects.requireNonNull(CountryMap.get(country)).getCountry();

        MimicVoice fallback = null;
        for(MimicVoice voice : Voices) {
            String[] voiceParts = voice.language.split("[-_/]");
            if (voiceParts.length >= 1 && voiceParts[0].equalsIgnoreCase(lang)) {
                if(voiceParts.length >= 2 && voiceParts[1].equalsIgnoreCase(country)) {
                    return voice.key;
                }
                if(fallback == null)
                    fallback = voice;
            }
        }
        if(fallback != null)
            return fallback.key;
        _Logger.warning("couldn't find a voice name for " + lang + "-" + country + "-" + variant);
        return null;
    }

    public List<MimicVoice> getMimicVoices() {
        List<MimicVoice> copy = new ArrayList<>();
        for(int i = 0; i<Voices.size(); i++) {
            copy.add(Voices.get(i).clone());
        }
        return copy;
    }

    private void saveCache() {
        _Logger.info("saving cache");
        Gson gson = new Gson();
        CacheFile cf = new CacheFile();
        cf.SpecialCache = new ArrayList<>();
        ArrayList<KVP<String, CacheEntry>> specialCache = new ArrayList<>();
        for(Map.Entry<String, CacheEntry> entry : SpecialCache.entrySet()) {
            specialCache.add(new KVP<>(entry.getKey(), entry.getValue()));
        }
        cf.SpecialCache.addAll(specialCache);
        cf.Cache = new ArrayList<>();
        cf.Cache.addAll(Cache.values());
        cf.Voices = (ArrayList<MimicVoice>) Voices;
        String json = gson.toJson(cf);
        File f = new File(Mimic3TTSEngineWrapperApp.getStorageContext().getCacheDir(), "cache.json");
        try {
            if (!f.exists()) {
                if(!f.createNewFile())
                    _Logger.severe("couldn't create cache.json... cache will be lost");
            }

            if(f.exists()) {
                FileWriter writer = new FileWriter(f, false);
                writer.write(json);
                writer.close();
            }
        } catch (IOException ignored) {}
    }

    private void buildCache() {
        _Logger.info("building cache");
        File cacheDir = Mimic3TTSEngineWrapperApp.getStorageContext().getCacheDir();
        File cacheJson = null;
        Map<String, File> files =  new HashMap<>();
        if(cacheDir != null && !cacheDir.isFile()) {
            for(File f : cacheDir.listFiles()) {
                if(f.getName().equals("cache.json"))
                    cacheJson = f;
                else
                    files.put(f.getName(), f);
            }
        }
        if(cacheJson != null) {
            Gson gson = new Gson();
            try {
                BufferedReader reader = new BufferedReader(new FileReader(cacheJson));
                String json = reader.lines().collect(Collectors.joining());
                CacheFile cacheFile = gson.fromJson(json, CacheFile.class);
                try {
                    CacheLock.lock();
                    Cache.clear();
                    CurrentCacheSize = 0;
                    for (CacheEntry entry : cacheFile.Cache) {
                        String entryId = Mimic3TTSEngineWrapperApp.getSha256Hex(entry.Text);
                        if (files.containsKey(entryId)) {
                            File f = files.get(entryId);
                            if (f != null) {
                                Cache.put(entryId, entry);
                                CurrentCacheSize += f.length();
                            }
                        }
                    }
                    for (KVP<String, CacheEntry> entry : cacheFile.SpecialCache) {
                        if(files.containsKey(entry.Key)) {
                            File f = files.get(entry.Key);
                            if(f != null) {
                                SpecialCache.put(entry.Key, entry.Value);
                            }
                        }
                    }
                    Voices = cacheFile.Voices;
                    _Logger.info("built cache, " + Cache.size() + " entries in cache, " + SpecialCache.size() + " entries in special cache and " + Voices.size() + " voices");
                } catch (NoSuchAlgorithmException ignored) {
                } finally {
                    CacheLock.unlock();
                }
            } catch (FileNotFoundException ignored) { }
        }
    }

    private void addToCache(byte[] data, CacheEntry cacheEntry, @Nullable String specialKey) throws IllegalArgumentException {
        if (cacheEntry.ByteSize != data.length){
            throw new IllegalArgumentException("cacheEntry.ByteSize != actual byte size");
        }

        File cacheDir = Mimic3TTSEngineWrapperApp.getStorageContext().getCacheDir();
        try {
            String key;
            if (specialKey != null && !specialKey.equals(""))
                key = specialKey;
            else
                key = Mimic3TTSEngineWrapperApp.getSha256Hex(cacheEntry.Text);
            File f = new File(cacheDir, key);
            if(f.exists()) {
                boolean ignored = f.delete();
            }
            if(f.createNewFile()) {
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(data);
                    out.close();
                    CacheLock.lock();
                    CurrentCacheSize += cacheEntry.ByteSize;
                    if (specialKey != null && !specialKey.equals("")) {
                        _Logger.info("adding " + specialKey + " to special cache");
                        SpecialCache.put(specialKey, cacheEntry);
                    } else {
                        _Logger.info("adding '" + cacheEntry.Text + "' to cache");
                        Cache.put(key, cacheEntry);
                    }
                }
                finally {
                    CacheLock.unlock();
                }
            } else {
                _Logger.severe("because of some reason we cannot create the file: " + f.getAbsolutePath());
            }
        } catch (IOException | NoSuchAlgorithmException ex) {
            _Logger.severe(ex.getClass().getName() + " occurred: " + ex.getMessage());
            for (StackTraceElement el : ex.getStackTrace()) {
                _Logger.warning("at: " + el.toString());
            }
        }

    }

    public void setCacheSize(float newCacheSizeInGB) {
        MaxCacheSizeInGB = newCacheSizeInGB;
        MaxCacheSizeInB = (long)newCacheSizeInGB * 1024 * 1024 * 1024;
        if(MaxCacheSizeInB > CurrentCacheSize)
            shrinkCacheToSize(MaxCacheSizeInB);
    }

    private void shrinkCacheToSize(long shrinkToSizeInB) {
        _Logger.info("shrinking cache to " + shrinkToSizeInB + " bytes, currently: " + CurrentCacheSize);
        try {
            CacheLock.lock();
            List<CacheEntry> entries = new ArrayList<>();
            for (CacheEntry entry : Cache.values()) {
                entries.add(entry.clone());
            }
            entries.sort(Comparator.comparing(cacheEntry -> cacheEntry.LastUsed));
            long newCacheSize = 0;
            Map<String, CacheEntry> newCache = new HashMap<>();
            File cacheDir = Mimic3TTSEngineWrapperApp.getStorageContext().getCacheDir();
            for (CacheEntry entry : entries) {
                String entryId = Mimic3TTSEngineWrapperApp.getSha256Hex(entry.Text);
                if (newCacheSize < shrinkToSizeInB) {
                    newCache.put(entryId, entry);
                    newCacheSize += entry.ByteSize;
                } else {
                    File f = new File(cacheDir, entryId);
                    if (f.exists()) {
                        boolean ignored = f.delete();
                    }
                }
            }
            Cache = newCache;
        }
        catch (NoSuchAlgorithmException ignored) { }
        finally {
            CacheLock.unlock();
        }
    }

    private void clearSpecialCache() {
        _Logger.info("clearing special cache");
        try {
            CacheLock.lock();
            File cacheDir = Mimic3TTSEngineWrapperApp.getStorageContext().getCacheDir();
            for (Map.Entry<String, CacheEntry> set: SpecialCache.entrySet()) {
                File f = new File(cacheDir, set.getKey());
                if (f.exists()) {
                    boolean ignored = f.delete();
                }
            }
            SpecialCache.clear();
        }
        finally {
            CacheLock.unlock();
        }
    }

    private void clearCache() {
        _Logger.info("clearing cache");
        try {
            CacheLock.lock();
            File cacheDir = Mimic3TTSEngineWrapperApp.getStorageContext().getCacheDir();
            for (Map.Entry<String, CacheEntry> set: Cache.entrySet()) {
                File f = new File(cacheDir, set.getKey());
                if (f.exists()) {
                    boolean ignored = f.delete();
                }
            }
            Cache.clear();
        }
        finally {
            CacheLock.unlock();
        }
    }

    public void triggerLoadVoices() {
        FetchVoices = true;
    }

    public void triggerSaveCache() { SaveCache = true; }

    public void clearCache(boolean clearSpecialCacheToo) {
        if(clearSpecialCacheToo)
            clearSpecialCache();
        clearCache();
    }

    public void addVoicesLoadedListener(OnVoicesLoadedListener listener) {
        OnVoicesLoadedListeners.add(listener);
    }

    public void removeVoicesLoadedListener(OnVoicesLoadedListener listener) {
        OnVoicesLoadedListeners.remove(listener);
    }

    public static void addLoadedListener(OnLoadedListener listener) {
        s_OnLoadedListeners.add(listener);
    }

    public static void removeLoadedListener(OnLoadedListener listener) {
        s_OnLoadedListeners.remove(listener);
    }

    public void addErrorListener(OnErrorListener listener) {
        OnErrorListeners.add(listener);
    }

    public void removeErrorListener(OnErrorListener listener) {
        OnErrorListeners.remove(listener);
    }
}
