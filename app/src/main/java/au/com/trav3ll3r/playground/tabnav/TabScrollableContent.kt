package au.com.trav3ll3r.playground.tabnav

import android.content.Context
import android.support.v4.widget.NestedScrollView
import android.util.AttributeSet
import android.view.MotionEvent

class TabScrollableContent(context: Context, attributeSet: AttributeSet) : NestedScrollView(context, attributeSet) {

    companion object {
        private const val SCROLL_DIRECTION_UP = -1
        private const val SCROLL_DIRECTION_DOWN = -1
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
//        BottomSheetBehaviorPinned.LOCKED_FOR_SCROLLING = true
//        when (ev.action) {
//            MotionEvent.ACTION_MOVE -> {
//                if (computeVerticalScrollOffset() > 0) {
//                    BottomSheetBehaviorPinned.LOCKED_FOR_SCROLLING = true
//                    return false
//                } else {
//                    BottomSheetBehaviorPinned.LOCKED_FOR_SCROLLING = false
//                    return super.onInterceptTouchEvent(ev)
//                }
//            }
//            //canScrollVertically()
//        }
        return true
    }

//    override fun setVerticalScrollbarPosition(position: Int) {
//        super.setVerticalScrollbarPosition(position)
//    }
//
//    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray) {
//        super.onNestedPreScroll(target, dx, dy, consumed)
//    }

//    override fun onStopNestedScroll(target: View) {
//        if (computeVerticalScrollOffset() > 0) {
//            BottomSheetBehaviorPinned.LOCKED_FOR_SCROLLING = true
//        } else {
//            BottomSheetBehaviorPinned.LOCKED_FOR_SCROLLING = false
//        }
//        super.onStopNestedScroll(target)
//    }
}