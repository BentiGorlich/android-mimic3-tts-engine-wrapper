package de.bentigorlich.mimic3ttsenginewrapper;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.TextToSpeech;

import java.io.ByteArrayOutputStream;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class SynthesisListener implements SynthesisCallback {

    boolean Started = false;
    boolean Finished = false;
    final boolean PlayOnFinish;
    int SampleRate;
    AudioFormat Format;
    int ChannelCount;
    ByteArrayOutputStream AudioBuffer = new ByteArrayOutputStream();
    Logger _Logger;

    public SynthesisListener(boolean playOnFinish) {
        _Logger = Logger.getLogger(this.getClass().toString());
        LogManager.getLogManager().addLogger(_Logger);
        PlayOnFinish = playOnFinish;
    }

    @Override
    public int getMaxBufferSize() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int start(int sampleRate, int audioFormat, int channelCount) {
        _Logger.info("starting synthesis with sampleRate: " + sampleRate + " with channel: " + channelCount + " in format: " + audioFormat);
        SampleRate = sampleRate;
        Format = new AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(audioFormat)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();
        ChannelCount = channelCount;
        Started = true;
        return TextToSpeech.SUCCESS;
    }

    @Override
    public int audioAvailable(byte[] buffer, int offset, int length) {
        _Logger.info("got some bytes (" + length + ")");
        AudioBuffer.write(buffer, offset, length);
        return TextToSpeech.SUCCESS;
    }

    @Override
    public int done() {
        if(!PlayOnFinish) {
            _Logger.info("Synthesis done");
            return 0;
        }

        _Logger.info("Synthesis done, building track");
        Finished = true;
        AudioAttributes attributes = new AudioAttributes.Builder()
                .build();
        AudioTrack t = new AudioTrack(attributes, Format, AudioBuffer.size(), AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE);
        int trackError = t.write(AudioBuffer.toByteArray(), 0, AudioBuffer.size());
        if(trackError >= 0) {
            _Logger.info("playing track");
            t.play();
        } else {
            switch (trackError) {
                case AudioTrack.ERROR_BAD_VALUE:
                    _Logger.severe("AudioTrack.ERROR_BAD_VALUE");
                    break;
                case AudioTrack.ERROR_DEAD_OBJECT:
                    _Logger.severe("AudioTrack.ERROR_DEAD_OBJECT");
                    break;
                case AudioTrack.ERROR_INVALID_OPERATION:
                    _Logger.severe("AudioTrack.ERROR_INVALID_OPERATION");
                    break;
                case AudioTrack.ERROR:
                    _Logger.severe("AudioTrack.ERROR");
                    break;
            }
        }
        return 0;
    }

    @Override
    public void error() {
        Finished = true;
    }

    @Override
    public void error(int i) {
        Finished = true;
    }

    @Override
    public boolean hasStarted() {
        return Started;
    }

    @Override
    public boolean hasFinished() {
        return Finished;
    }
}
