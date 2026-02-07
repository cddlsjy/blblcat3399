package blbl.cat3399.feature.player

import blbl.cat3399.feature.player.danmaku.DanmakuSessionSettings

data class SegmentMark(
    val startFraction: Float,
    val endFraction: Float,
)

internal data class SubtitleItem(
    val lan: String,
    val lanDoc: String,
    val url: String,
)

data class PlayerPlaylistItem(
    val bvid: String,
    val cid: Long? = null,
    val epId: Long? = null,
    val aid: Long? = null,
    val title: String? = null,
)

internal sealed interface Playable {
    data class Dash(
        val videoUrl: String,
        val audioUrl: String,
        val videoUrlCandidates: List<String>,
        val audioUrlCandidates: List<String>,
        val qn: Int,
        val codecid: Int,
        val audioId: Int,
        val audioKind: DashAudioKind,
        val isDolbyVision: Boolean,
    ) : Playable

    data class VideoOnly(
        val videoUrl: String,
        val videoUrlCandidates: List<String>,
        val qn: Int,
        val codecid: Int,
        val isDolbyVision: Boolean,
    ) : Playable

    data class Progressive(
        val url: String,
        val urlCandidates: List<String>,
    ) : Playable
}

internal enum class DashAudioKind { NORMAL, DOLBY, FLAC }

internal data class PlaybackConstraints(
    val allowDolbyVision: Boolean = true,
    val allowDolbyAudio: Boolean = true,
    val allowFlacAudio: Boolean = true,
)

internal data class ResumeCandidate(
    val rawTime: Long,
    val rawTimeUnitHint: RawTimeUnitHint,
    val source: String,
)

internal enum class RawTimeUnitHint {
    UNKNOWN,
    SECONDS_LIKELY,
    MILLIS_LIKELY,
}

internal data class SkipSegment(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val category: String?,
    val source: String,
)

internal data class PendingAutoSkip(
    val token: Int,
    val segment: SkipSegment,
    val dueAtElapsedMs: Long,
)

internal data class PlayerSessionSettings(
    val playbackSpeed: Float,
    val preferCodec: String,
    val preferAudioId: Int,
    val targetAudioId: Int = 0,
    val actualAudioId: Int = 0,
    val preferredQn: Int,
    val targetQn: Int,
    val actualQn: Int = 0,
    val playbackModeOverride: String?,
    val subtitleEnabled: Boolean,
    val subtitleLangOverride: String?,
    val subtitleTextSizeSp: Float,
    val danmaku: DanmakuSessionSettings,
    val debugEnabled: Boolean,
)
