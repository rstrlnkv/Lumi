package ru.digitalpunk.lumi.engine

import android.content.Context
import android.view.MotionEvent
import org.mozilla.geckoview.GeckoView

/**
 * GeckoView that reports the finger to a [PageTouchListener]. It never keeps a touch from Gecko; on
 * ACTION_DOWN it asks Gecko what the page under the finger can do (which also forwards the event).
 */
internal class LumiGeckoView(context: Context) : GeckoView(context) {
    var listener: PageTouchListener? = null

    /** While a screen covers the page, TalkBack and keyboard focus skip it. */
    fun hideFromAccessibility(hide: Boolean) {
        importantForAccessibility = if (hide) IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else IMPORTANT_FOR_ACCESSIBILITY_AUTO
        descendantFocusability = if (hide) FOCUS_BLOCK_DESCENDANTS else FOCUS_BEFORE_DESCENDANTS
    }

    private var gestureId = 0
    private var multiTouch = false
    private var lastY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val id = ++gestureId
                multiTouch = false
                lastY = event.y
                listener?.onTouchDown()
                onTouchEventForDetailResult(event).accept { detail ->
                    if (id == gestureId && detail != null) {
                        listener?.onTouchDownResolved(
                            PageTouchInfo.fromFlags(detail.handledResult(), detail.scrollableDirections(), detail.overscrollDirections()),
                        )
                    }
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val first = !multiTouch
                multiTouch = true
                if (first) listener?.onTouchEnd(cancelled = true)
                return super.onTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                val handled = super.onTouchEvent(event)
                if (!multiTouch) {
                    val y = event.getY(0)
                    listener?.onTouchMove(y - lastY)
                    lastY = y
                }
                return handled
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val handled = super.onTouchEvent(event)
                if (!multiTouch) listener?.onTouchEnd(cancelled = event.actionMasked == MotionEvent.ACTION_CANCEL)
                return handled
            }
        }
        return super.onTouchEvent(event)
    }
}
