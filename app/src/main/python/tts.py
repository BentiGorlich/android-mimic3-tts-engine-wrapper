import argparse
import io
import logging
import threading
import typing
import wave
from queue import Queue
from mimic3_tts.__main__ import CommandLineInterfaceState, process_line, shutdown_tts, OutputNaming
from opentts_abc import Voice

_LOGGER = logging.getLogger()


def init(args: list[str]):
    parser = argparse.ArgumentParser(prog="mimic3wrapper", description="Wrapper for Mimic 3 command-line interface")
    parser.add_argument("text", nargs="*", help="Text to convert to speech")
    parser.add_argument("--voice", "-v", help="Name of voice (expected in <voices-dir>/<language>)")
    parser.add_argument("--speaker", "-s", help="Name or number of speaker (default: first speaker)")
    parser.add_argument("--voices-dir", action="append", help="Directory with voices (format is <language>/<voice_name>)")
    parser.add_argument("--ssml", action="store_true", help="Input text is SSML")
    parser.add_argument("--deterministic", action="store_true", help="Ensure that the same audio is always synthesized from the same text")
    parser.add_argument("--noise-scale", type=float, help="Noise scale [0-1], default is 0.667")
    parser.add_argument("--length-scale", type=float, help="Length scale (1.0 is default speed, 0.5 is 2x faster)")
    parser.add_argument("--noise-w", type=float, help="Variation in cadence [0-1], default is 0.8")
    parser.add_argument("--result-queue-size", default=5, help="Maximum number of sentences to maintain in output queue (default: 5)")
    parser.add_argument("--voices", action="store_true", help="List available voices")
    parsed_args = CommandLineInterfaceState(args=parser.parse_args(args))
    return main(parsed_args)


def main(state: CommandLineInterfaceState):
    initialize_args(state)
    initialize_tts(state)
    logging.basicConfig(level=logging.DEBUG)
    _LOGGER.setLevel(logging.DEBUG)

    try:
        if state.args.voices or not state.texts:
            result_voices: list[Voice] = []
            for voice in state.tts.get_voices():
                result_voices.append(voice)
            return result_voices
        else:
            return process_lines(state)
    finally:
        shutdown_tts(state)


def process_lines(state: CommandLineInterfaceState):
    assert state.texts is not None
    try:
        result_idx = 0

        for line in state.texts:
            line_voice: typing.Optional[str] = None
            line_id = ""
            line = line.strip()
            if not line:
                continue

            process_line(line, state, line_id=line_id, line_voice=line_voice)
            result_idx += 1

    except KeyboardInterrupt:
        if state.result_queue is not None:
            # Draw audio playback queue
            while not state.result_queue.empty():
                state.result_queue.get()
    finally:
        # Wait for raw stream to finish
        if state.result_queue is not None:
            state.result_queue.put(None)

        if state.result_thread is not None:
            state.result_thread.join()

    # -------------------------------------------------------------------------

    # Write combined audio to stdout
    if state.all_audio:
        _LOGGER.debug("writing byte array with " + len(state.all_audio).__str__() + " bytes")
        with io.BytesIO() as wav_io:
            wav_file_play: wave.Wave_write = wave.open(wav_io, "wb")
            with wav_file_play:
                wav_file_play.setframerate(state.sample_rate_hz)
                wav_file_play.setsampwidth(state.sample_width_bytes)
                wav_file_play.setnchannels(state.num_channels)
                wav_file_play.writeframes(state.all_audio)

            return wav_io.getvalue()


def initialize_tts(state: CommandLineInterfaceState):
    """Create Mimic 3 TTS from command-line arguments"""
    from mimic3_tts import Mimic3Settings, Mimic3TextToSpeechSystem  # noqa: F811

    args = state.args

    # Local TTS
    state.tts = Mimic3TextToSpeechSystem(
        Mimic3Settings(
            length_scale=args.length_scale,
            noise_scale=args.noise_scale,
            noise_w=args.noise_w,
            voices_directories=args.voices_dir,
            use_cuda=False,
            use_deterministic_compute=args.deterministic,
        )
    )

    if state.args.voices:
        return

    state.tts.voice = args.voice
    state.tts.speaker = args.speaker

    if state.tts:
        if state.args.voice:
            # Set default voice
            state.tts.voice = state.args.voice

    state.result_queue = Queue(maxsize=args.result_queue_size)

    state.result_thread = threading.Thread(
        target=process_result, daemon=True, args=(state,)
    )
    state.result_thread.start()


def initialize_args(state: CommandLineInterfaceState):
    """Initialize CLI state from command-line arguments"""
    args = state.args

    if args.ssml:
        # Avoid text mangling when using SSML
        args.output_naming = OutputNaming.TIME

    # Read text from stdin or arguments
    if args.text:
        # Use arguments
        state.texts = args.text

    if (not args.speaker) and args.voice and ("#" in args.voice):
        # Split apart voice
        args.voice, args.speaker = args.voice.split("#", maxsplit=1)

    if args.deterministic:
        # Disable noise
        args.noise_scale = 0.0
        args.noise_w = 0.0


def process_result(state: CommandLineInterfaceState):
    try:
        from mimic3_tts import AudioResult, MarkResult

        assert state.result_queue is not None
        while True:
            result_todo = state.result_queue.get()
            if result_todo is None:
                break

            try:
                result = result_todo.result

                if isinstance(result, AudioResult):
                    # Combine all audio and output to stdout at the end
                    state.all_audio += result.audio_bytes
                    state.sample_rate_hz = result.sample_rate_hz
                    state.sample_width_bytes = result.sample_width_bytes
                    state.num_channels = result.num_channels

                elif isinstance(result, MarkResult):
                    if state.mark_writer:
                        print(result.name, file=state.mark_writer)

            except Exception:
                _LOGGER.exception("Error processing result")
    except Exception:
        _LOGGER.exception("process_result")
