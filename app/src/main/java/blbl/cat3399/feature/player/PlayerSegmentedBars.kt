package blbl.cat3399.feature.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.content.ContextCompat
import blbl.cat3399.R
import kotlin.math.max

class SegmentedSeekBar : AppCompatSeekBar {
    private val skipSegmentPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = ContextCompat.getColor(context, R.color.blbl_blue)
            alpha = 170
        }

    private val poiSegmentPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = ContextCompat.getColor(context, R.color.blbl_red)
            alpha = 220
        }

    private val poiMarkerHalfWidthPx = (context.resources.displayMetrics.density * 1.5f).coerceAtLeast(1f)

    private var segments: List<SegmentMark> = emptyList()

    private val tmpRect = RectF()

    private var trackHeightPx: Int = 0

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    fun setTrackHeightPx(heightPx: Int) {
        val v = heightPx.coerceAtLeast(0)
        if (trackHeightPx == v) return
        trackHeightPx = v
        updateTrackBounds()
        invalidate()
    }

    fun setSegments(segments: List<SegmentMark>) {
        this.segments = segments
        invalidate()
    }

    fun clearSegments() {
        setSegments(emptyList())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateTrackBounds()
    }

    override fun setProgressDrawable(d: android.graphics.drawable.Drawable?) {
        super.setProgressDrawable(d)
        updateTrackBounds()
    }

    private fun updateTrackBounds() {
        val d = progressDrawable ?: return
        if (width <= 0 || height <= 0) return

        val b = d.bounds
        val hasValidBounds = b.width() > 1 && b.height() > 1

        val left =
            if (hasValidBounds) b.left
            else max(paddingLeft, thumbOffset).coerceAtLeast(0)
        val right =
            if (hasValidBounds) b.right
            else (width - max(paddingRight, thumbOffset)).coerceAtLeast(left + 1)

        val contentTop = paddingTop
        val contentBottom = (height - paddingBottom).coerceAtLeast(contentTop + 1)
        val contentHeight = (contentBottom - contentTop).coerceAtLeast(1)

        val desired = trackHeightPx.takeIf { it > 0 } ?: b.height().takeIf { it > 0 } ?: contentHeight
        val h = desired.coerceIn(1, contentHeight)

        val centerY =
            if (hasValidBounds) b.centerY()
            else (contentTop + contentHeight / 2)
        val top = (centerY - h / 2).coerceIn(contentTop, contentBottom - 1)
        val bottom = (top + h).coerceAtMost(contentBottom)

        if (b.left != left || b.top != top || b.right != right || b.bottom != bottom) {
            d.setBounds(left, top, right, bottom)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (segments.isEmpty()) return

        val bounds = progressDrawable?.bounds ?: return
        val top = bounds.top.toFloat()
        val bottom = bounds.bottom.toFloat()

        val leftInset = max(paddingLeft, thumbOffset).toFloat()
        val rightInset = max(paddingRight, thumbOffset).toFloat()
        val leftBase = leftInset
        val rightBase = (width.toFloat() - rightInset)
        val range = rightBase - leftBase
        if (range <= 1f) return

        drawSegments(canvas = canvas, top = top, bottom = bottom, leftBase = leftBase, rightBase = rightBase, range = range, poiOnly = false)
        drawSegments(canvas = canvas, top = top, bottom = bottom, leftBase = leftBase, rightBase = rightBase, range = range, poiOnly = true)
    }

    private fun drawSegments(
        canvas: Canvas,
        top: Float,
        bottom: Float,
        leftBase: Float,
        rightBase: Float,
        range: Float,
        poiOnly: Boolean,
    ) {
        for (seg in segments) {
            val isPoi = seg.style == SegmentMarkStyle.POI
            if (isPoi != poiOnly) continue

            if (isPoi) {
                val centerFraction = seg.startFraction.coerceIn(0f, 1f)
                val center =
                    if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                        rightBase - range * centerFraction
                    } else {
                        leftBase + range * centerFraction
                    }
                val l = (center - poiMarkerHalfWidthPx).coerceIn(leftBase, rightBase)
                val r = (center + poiMarkerHalfWidthPx).coerceIn(l + 1f, rightBase)
                tmpRect.set(l, top, r, bottom)
                canvas.drawRect(tmpRect, poiSegmentPaint)
                continue
            }

            val start = seg.startFraction.coerceIn(0f, 1f)
            val end = seg.endFraction.coerceIn(0f, 1f)
            if (end <= start) continue
            val (l, r) =
                if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                    (rightBase - range * end) to (rightBase - range * start)
                } else {
                    (leftBase + range * start) to (leftBase + range * end)
                }
            tmpRect.set(l, top, r, bottom)
            canvas.drawRect(tmpRect, skipSegmentPaint)
        }
    }
}

class SegmentedProgressBar : ProgressBar {
    private val skipSegmentPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = ContextCompat.getColor(context, R.color.blbl_blue)
            alpha = 170
        }

    private val poiSegmentPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = ContextCompat.getColor(context, R.color.blbl_red)
            alpha = 220
        }

    private val poiMarkerHalfWidthPx = (context.resources.displayMetrics.density * 1.5f).coerceAtLeast(1f)

    private var segments: List<SegmentMark> = emptyList()

    private val tmpRect = RectF()

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    fun setSegments(segments: List<SegmentMark>) {
        this.segments = segments
        invalidate()
    }

    fun clearSegments() {
        setSegments(emptyList())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (segments.isEmpty()) return

        val bounds = progressDrawable?.bounds ?: return
        val top = bounds.top.toFloat()
        val bottom = bounds.bottom.toFloat()

        val leftBase = paddingLeft.toFloat()
        val rightBase = (width - paddingRight).toFloat()
        val range = rightBase - leftBase
        if (range <= 1f) return

        drawSegments(canvas = canvas, top = top, bottom = bottom, leftBase = leftBase, rightBase = rightBase, range = range, poiOnly = false)
        drawSegments(canvas = canvas, top = top, bottom = bottom, leftBase = leftBase, rightBase = rightBase, range = range, poiOnly = true)
    }

    private fun drawSegments(
        canvas: Canvas,
        top: Float,
        bottom: Float,
        leftBase: Float,
        rightBase: Float,
        range: Float,
        poiOnly: Boolean,
    ) {
        for (seg in segments) {
            val isPoi = seg.style == SegmentMarkStyle.POI
            if (isPoi != poiOnly) continue

            if (isPoi) {
                val centerFraction = seg.startFraction.coerceIn(0f, 1f)
                val center =
                    if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                        rightBase - range * centerFraction
                    } else {
                        leftBase + range * centerFraction
                    }
                val l = (center - poiMarkerHalfWidthPx).coerceIn(leftBase, rightBase)
                val r = (center + poiMarkerHalfWidthPx).coerceIn(l + 1f, rightBase)
                tmpRect.set(l, top, r, bottom)
                canvas.drawRect(tmpRect, poiSegmentPaint)
                continue
            }

            val start = seg.startFraction.coerceIn(0f, 1f)
            val end = seg.endFraction.coerceIn(0f, 1f)
            if (end <= start) continue
            val (l, r) =
                if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                    (rightBase - range * end) to (rightBase - range * start)
                } else {
                    (leftBase + range * start) to (leftBase + range * end)
                }
            tmpRect.set(l, top, r, bottom)
            canvas.drawRect(tmpRect, skipSegmentPaint)
        }
    }
}
