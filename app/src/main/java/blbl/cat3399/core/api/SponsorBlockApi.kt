package blbl.cat3399.core.api

import blbl.cat3399.BuildConfig
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.net.BiliClient
import org.json.JSONArray

object SponsorBlockApi {
    private const val TAG = "SponsorBlockApi"
    private const val BASE_URL = "https://bsbsb.top"
    private const val PROJECT_URL = "https://github.com/cat3399/blbl"
    private const val ACTION_POI = "poi"

    enum class FetchState {
        SUCCESS,
        NOT_FOUND,
        ERROR,
    }

    data class Segment(
        val cid: Long?,
        val startMs: Long,
        val endMs: Long,
        val category: String?,
        val uuid: String?,
        val actionType: String?,
    )

    internal data class RawSegment(
        val cid: String?,
        val category: String?,
        val actionType: String?,
        val uuid: String?,
        val startSec: Double,
        val endSec: Double,
    )

    data class FetchResult(
        val state: FetchState,
        val segments: List<Segment> = emptyList(),
        val detail: String? = null,
    )

    suspend fun skipSegments(bvid: String, cid: Long): FetchResult {
        val safeBvid = bvid.trim()
        if (safeBvid.isBlank() || cid <= 0L) {
            return FetchResult(state = FetchState.NOT_FOUND, detail = "invalid_args")
        }

        val exact = querySkipSegments(bvid = safeBvid, cid = cid)
        if (exact.state == FetchState.SUCCESS && exact.segments.isNotEmpty()) {
            return exact.withDetail("exact_cid").also { logResult(safeBvid, cid, it) }
        }

        val fallback = querySkipSegments(bvid = safeBvid, cid = null)
        val result =
            when (fallback.state) {
                FetchState.SUCCESS -> {
                    val picked = pickSegmentsForCid(fallback.segments, cid)
                    if (picked.isNotEmpty()) {
                        FetchResult(state = FetchState.SUCCESS, segments = picked, detail = "fallback_no_cid")
                    } else {
                        FetchResult(
                            state = FetchState.NOT_FOUND,
                            detail = if (fallback.segments.isEmpty()) "fallback_empty" else "fallback_cid_mismatch",
                        )
                    }
                }

                FetchState.NOT_FOUND -> {
                    when (exact.state) {
                        FetchState.SUCCESS -> FetchResult(state = FetchState.NOT_FOUND, detail = "exact_empty")
                        FetchState.NOT_FOUND -> FetchResult(state = FetchState.NOT_FOUND, detail = "not_found")
                        FetchState.ERROR -> FetchResult(state = FetchState.NOT_FOUND, detail = "fallback_not_found")
                    }
                }

                FetchState.ERROR -> {
                    when (exact.state) {
                        FetchState.SUCCESS -> fallback.withDetail("${fallback.detail ?: "fallback_error"}; exact_empty")
                        FetchState.NOT_FOUND -> fallback.withDetail("${fallback.detail ?: "fallback_error"}; exact_not_found")
                        FetchState.ERROR -> {
                            val exactDetail = exact.detail ?: "exact_error"
                            val fallbackDetail = fallback.detail ?: "fallback_error"
                            FetchResult(state = FetchState.ERROR, detail = "exact=$exactDetail; fallback=$fallbackDetail")
                        }
                    }
                }
            }
        logResult(safeBvid, cid, result)
        return result
    }

    private suspend fun querySkipSegments(bvid: String, cid: Long?): FetchResult {
        val url =
            buildString {
                append(BASE_URL)
                append("/api/skipSegments?videoID=")
                append(bvid)
                if (cid != null && cid > 0L) {
                    append("&cid=")
                    append(cid)
                }
            }
        return runCatching {
            BiliClient.requestStringResponse(
                url = url,
                method = "GET",
                headers = sponsorBlockRequestHeaders(),
                noCookies = true,
            )
        }.fold(
            onSuccess = { response ->
                when {
                    response.code == 404 -> FetchResult(state = FetchState.NOT_FOUND, detail = requestScopeLabel(cid))
                    !response.isSuccessful -> FetchResult(
                        state = FetchState.ERROR,
                        detail = "${requestScopeLabel(cid)} http_${response.code}",
                    )
                    else -> {
                        runCatching { parseSkipSegments(response.body) }
                            .fold(
                                onSuccess = { parsed ->
                                    FetchResult(
                                        state = FetchState.SUCCESS,
                                        segments = parsed,
                                        detail = requestScopeLabel(cid),
                                    )
                                },
                                onFailure = { t ->
                                    FetchResult(
                                        state = FetchState.ERROR,
                                        detail = "${requestScopeLabel(cid)} parse:${t.message.orEmpty()}",
                                    )
                                },
                            )
                    }
                }
            },
            onFailure = { t ->
                FetchResult(
                    state = FetchState.ERROR,
                    detail = "${requestScopeLabel(cid)} ${t.javaClass.simpleName}:${t.message.orEmpty()}",
                )
            },
        )
    }

    private fun logResult(bvid: String, cid: Long, result: FetchResult) {
        val detail = result.detail?.takeIf { it.isNotBlank() } ?: "-"
        AppLog.i(TAG, "skipSegments bvid=$bvid cid=$cid state=${result.state.name.lowercase()} count=${result.segments.size} detail=$detail")
    }

    private fun requestScopeLabel(cid: Long?): String = if (cid != null && cid > 0L) "cid=$cid" else "cid=all"

    private fun FetchResult.withDetail(detail: String): FetchResult = copy(detail = detail)

    internal fun sponsorBlockRequestHeaders(): Map<String, String> =
        mapOf(
            "Origin" to PROJECT_URL,
            "Referer" to PROJECT_URL,
            "X-Ext-Version" to "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}",
        )

    internal fun parseSkipSegments(raw: String): List<Segment> {
        val arr = JSONArray(raw)
        val items = ArrayList<RawSegment>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val category = obj.optString("category", "").trim().takeIf { it.isNotBlank() }
            val actionType = obj.optString("actionType", "").trim().takeIf { it.isNotBlank() }
            val uuid = obj.optString("UUID", "").trim().takeIf { it.isNotBlank() }
            val cid = obj.optString("cid", "").trim().takeIf { it.isNotBlank() }
            val segmentArr = obj.optJSONArray("segment") ?: continue
            if (segmentArr.length() < 2) continue
            val startSec = segmentArr.optDouble(0, Double.NaN)
            val endSec = segmentArr.optDouble(1, Double.NaN)
            items.add(
                RawSegment(
                    cid = cid,
                    category = category,
                    uuid = uuid,
                    actionType = actionType,
                    startSec = startSec,
                    endSec = endSec,
                ),
            )
        }
        return normalizeSegments(items)
    }

    internal fun normalizeSegments(items: List<RawSegment>): List<Segment> {
        val out = ArrayList<Segment>(items.size)
        for (item in items) {
            if (!item.startSec.isFinite() || !item.endSec.isFinite()) continue
            val startMs = (item.startSec * 1000.0).toLong().coerceAtLeast(0L)
            val endMs = (item.endSec * 1000.0).toLong().coerceAtLeast(0L)
            val isPoi = isPoiSegment(category = item.category, actionType = item.actionType)
            if (isPoi) {
                if (endMs < startMs) continue
            } else if (endMs <= startMs) {
                continue
            }
            out.add(
                Segment(
                    cid = item.cid?.trim()?.toLongOrNull(),
                    startMs = startMs,
                    endMs = endMs,
                    category = item.category,
                    uuid = item.uuid,
                    actionType = item.actionType,
                ),
            )
        }
        return out
    }

    internal fun pickSegmentsForCid(segments: List<Segment>, cid: Long): List<Segment> {
        val exact = segments.filter { it.cid == cid }
        if (exact.isNotEmpty()) return exact

        val uniqueKnownCids = segments.mapNotNull { it.cid }.toSet()
        return when {
            uniqueKnownCids.isEmpty() -> segments
            uniqueKnownCids.size == 1 -> segments
            else -> emptyList()
        }
    }

    internal fun isPoiSegment(category: String?, actionType: String?): Boolean {
        val normalizedAction = actionType?.trim().orEmpty()
        if (normalizedAction.equals(ACTION_POI, ignoreCase = true)) return true
        return category?.trim().equals("poi_highlight", ignoreCase = true)
    }
}
