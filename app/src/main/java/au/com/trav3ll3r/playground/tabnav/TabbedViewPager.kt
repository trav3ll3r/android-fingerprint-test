package au.com.trav3ll3r.playground.tabnav

import android.content.Context
import android.support.v4.view.ViewPager
import android.util.AttributeSet
import android.view.MotionEvent

class TabbedViewPager(context: Context, attrs: AttributeSet) : ViewPager(context, attrs) {

    /**
     * Controls whether or not [TabbedViewPager] will intercept and process touch events
     * If set to TRUE, [TabbedViewPager] will process touch events which are mainly used to swipe between the pages (horizontal swipe)
     * If set to FALSE, none of touch events will be handled
     *
     * <p>
     * Default value: TRUE
     * </p>
     */
    var allowTouchEvents: Boolean = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (allowTouchEvents) {
            super.onTouchEvent(event)
        } else false
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return if (allowTouchEvents) {
            super.onInterceptTouchEvent(event)
        } else false
    }

//    override fun onTouchEvent(event: MotionEvent): Boolean {
//        return when {
//            allowTouchEvents -> super.onTouchEvent(event)
//            event.action in listOf(MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE) -> return true
//            else -> false
//        }
//    }

//    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
//        return when {
//            allowTouchEvents -> super.onInterceptTouchEvent(event)
//            event.action in listOf(MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE) -> return true
//            else -> false
//        }
//    }

//    override fun performClick(): Boolean {
//        return super.performClick()
//    }
//
//    override fun onTouchEvent(event: MotionEvent): Boolean {
//        if (event.actionMasked in listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_DOWN, MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE)) { performClick(); return true }
//        return false
//    }
//
//    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
//        if (event.actionMasked in listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_DOWN, MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE)) { return true}
//        return false
//    }
}