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
}