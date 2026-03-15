package audio

/**
 * ElevenLabs TTS integration for motivation and meditation voices.
 * Fetches audio from the ElevenLabs API and plays it via an HTML Audio element.
 */
class VoiceEngine {

    private val apiKey = "sk_804443a0d69a3857a1e1867cb86a17171fb363e0c9e295b3"
    // Rachel — calm, warm female voice
    private val voiceId = "21m00Tcm4TlvDq8ikWAM"
    private val modelId = "eleven_multilingual_v2"

    // State stored on window so JS can access it
    init {
        js("window.__voiceState = { audio: null, playing: false }")
    }

    private var lastMotivationIndex = -1
    private var lastMeditationIndex = -1

    private val motivationTexts = arrayOf(
        "You're doing amazing. Take a moment to appreciate how far you've come. Every small step forward is progress, and you should be proud of yourself.",
        "Remember, it's okay to take things one step at a time. You don't have to have everything figured out right now. Just breathe, and trust the process.",
        "You are stronger than you think. The challenges you face today are building the resilience you'll need tomorrow. Keep going, you've got this.",
        "Be gentle with yourself. You're doing the best you can, and that is more than enough. Give yourself permission to rest when you need it.",
        "Every expert was once a beginner. Don't compare your chapter one to someone else's chapter twenty. Your journey is uniquely yours, and it's beautiful."
    )

    private val meditationTexts = arrayOf(
        "Close your eyes and take a slow, deep breath in. Hold it gently, and now release. Feel the tension melting away from your shoulders, your jaw, your hands. You are safe. You are present. Let each breath carry you deeper into calm.",
        "Imagine a warm, golden light slowly filling your body from the top of your head to the tips of your toes. With each breath, this light grows brighter, dissolving any stress or worry. You are at peace. There is nothing you need to do right now.",
        "Focus on the rhythm of your breathing. In, and out. In, and out. Like gentle waves on a quiet shore. Let your thoughts drift by like clouds in a vast sky. You don't need to hold onto any of them. Just breathe, and be.",
        "Picture yourself in a quiet meadow. The sun is warm on your face, a gentle breeze touches your skin. The grass is soft beneath you. There is no rush, no deadline, no pressure. Just this perfect, peaceful moment.",
        "Let go of everything that happened today. Let go of everything that might happen tomorrow. Right now, in this very moment, you are whole and complete. Breathe in stillness. Breathe out gratitude. You deserve this peace."
    )

    fun playMotivation() {
        lastMotivationIndex = (lastMotivationIndex + 1) % motivationTexts.size
        speak(motivationTexts[lastMotivationIndex])
    }

    fun playMeditation() {
        lastMeditationIndex = (lastMeditationIndex + 1) % meditationTexts.size
        speak(meditationTexts[lastMeditationIndex])
    }

    fun stop() {
        js("""
            if (window.__voiceState && window.__voiceState.audio) {
                window.__voiceState.audio.pause();
                window.__voiceState.audio.currentTime = 0;
                window.__voiceState.playing = false;
            }
        """)
    }

    fun isCurrentlyPlaying(): Boolean {
        return js("window.__voiceState && window.__voiceState.playing") as Boolean
    }

    private fun speak(text: String) {
        stop()

        val apiKeyVal = apiKey
        val voiceIdVal = voiceId
        val modelIdVal = modelId

        js("""
            (function() {
                var url = 'https://api.elevenlabs.io/v1/text-to-speech/' + voiceIdVal;
                var body = JSON.stringify({
                    text: text,
                    model_id: modelIdVal,
                    voice_settings: {
                        stability: 0.75,
                        similarity_boost: 0.6,
                        style: 0.3,
                        use_speaker_boost: true
                    }
                });
                fetch(url, {
                    method: 'POST',
                    headers: {
                        'xi-api-key': apiKeyVal,
                        'Content-Type': 'application/json',
                        'Accept': 'audio/mpeg'
                    },
                    body: body
                })
                .then(function(response) {
                    if (!response.ok) throw new Error('ElevenLabs API error: ' + response.status);
                    return response.blob();
                })
                .then(function(blob) {
                    var audioUrl = URL.createObjectURL(blob);
                    var audio = new Audio(audioUrl);
                    audio.volume = 0.8;
                    window.__voiceState.audio = audio;
                    window.__voiceState.playing = true;
                    audio.onended = function() {
                        window.__voiceState.playing = false;
                        URL.revokeObjectURL(audioUrl);
                    };
                    audio.play();
                })
                .catch(function(err) {
                    console.warn('ElevenLabs TTS failed:', err);
                    window.__voiceState.playing = false;
                });
            })()
        """)
    }
}
