package au.com.trav3ll3r.playground.bottomsheet

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.support.annotation.IntDef
import android.support.design.widget.CoordinatorLayout
import android.support.v4.view.MotionEventCompat
import android.support.v4.view.NestedScrollingChild
import android.support.v4.view.ViewCompat
import android.support.v4.widget.NestedScrollView
import android.support.v4.widget.ViewDragHelper
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import au.com.trav3ll3r.playground.R
import au.com.trav3ll3r.playground.tabnav.TabbedPagerLayout
import java.lang.ref.WeakReference
import java.util.*

class BottomSheetBehaviorPinned<V : View> : CoordinatorLayout.Behavior<V>, TabbedPagerLayout.PageChangeListener {

    private object Holder {
        lateinit var instance: BottomSheetBehaviorPinned<*>
    }

    companion object {
        private val TAG = BottomSheetBehaviorPinned::class.java.simpleName

        val INSTANCE: BottomSheetBehaviorPinned<*> by lazy { Holder.instance }

        /**
         * The bottom sheet is dragging.
         */
        const val STATE_DRAGGING = 1

        /**
         * The bottom sheet is settling.
         */
        const val STATE_SETTLING = 2

        /**
         * The bottom sheet is expanded_half_way.
         */
        const val STATE_ANCHOR_POINT = 3

        /**
         * The bottom sheet is expanded.
         */
        const val STATE_EXPANDED = 4

        /**
         * The bottom sheet is collapsed.
         */
        const val STATE_COLLAPSED = 5

        /**
         * The bottom sheet is hidden.
         */
        const val STATE_HIDDEN = 6

        private val HIDE_THRESHOLD = 0.5f
        private val HIDE_FRICTION = 0.1f

        private val DEFAULT_ANCHOR_POINT = 700

        /**
         * A utility function to get the [BottomSheetBehaviorPinned] associated with the `view`.
         *
         * @param view The [View] with [BottomSheetBehaviorPinned].
         * @return The [BottomSheetBehaviorPinned] associated with the `view`.
         */
        fun <V : View> from(view: V): BottomSheetBehaviorPinned<V> {
            val params = view.layoutParams as? CoordinatorLayout.LayoutParams ?: throw IllegalArgumentException("The view is not a direct child of CoordinatorLayout")
            val behavior = params.behavior as? BottomSheetBehaviorPinned<*> ?: throw IllegalArgumentException("The view is not associated with BottomSheetBehaviorPinned")
            val result = behavior as BottomSheetBehaviorPinned<V>
            Holder.instance = result
            return result
        }
    }

    /**
     * @hide
     */
    @IntDef(STATE_EXPANDED.toLong(), STATE_COLLAPSED.toLong(), STATE_DRAGGING.toLong(), STATE_ANCHOR_POINT.toLong(), STATE_SETTLING.toLong(), STATE_HIDDEN.toLong())
    @Retention(AnnotationRetention.SOURCE)
    annotation class State

    private var mMinimumVelocity: Float = 0f // PROPERLY SET LATER IN CONSTRUCTOR FROM configuration

    /**
     * Gets the height of the bottom sheet when it is collapsed.
     *
     * @return The height of the collapsed bottom sheet.
     * @attr ref android.support.design.R.styleable#BottomSheetBehavior_Params_behavior_peekHeight
     */
    /**
     * Sets the height of the bottom sheet when it is collapsed.
     *
     * @param peekHeight The height of the collapsed bottom sheet in pixels.
     * @attr ref android.support.design.R.styleable#BottomSheetBehavior_Params_behavior_peekHeight
     */
    var peekHeight: Int = 0
        set(peekHeight) {
            field = Math.max(0, peekHeight)
            collapsedOffset = mParentHeight - peekHeight
        }

    private var expandedOffset: Int = 0
    private var collapsedOffset: Int = 0
    var anchorPoint: Int = 0

    /**
     * Gets whether this bottom sheet can hide when it is swiped down.
     *
     * @return `true` if this bottom sheet can hide.
     * @attr ref android.support.design.R.styleable#BottomSheetBehavior_Params_behavior_hideable
     */
    /**
     * Sets whether this bottom sheet can hide when it is swiped down.
     *
     * @param hideable `true` to make this bottom sheet hideable.
     * @attr ref android.support.design.R.styleable#BottomSheetBehavior_Params_behavior_hideable
     */
    var isHideable: Boolean = false

    @State
    private var mState = STATE_COLLAPSED
    @State
    private var mLastStableState = STATE_COLLAPSED
    private var mViewDragHelper: ViewDragHelper? = null
    private var mIgnoreEvents: Boolean = false
    private var mNestedScrolled: Boolean = false
    private var mParentHeight: Int = 0
    private var mViewRef: WeakReference<V>? = null
    private var mNestedScrollingChildRef: WeakReference<View>? = null
    private var mCallback: Vector<BottomSheetCallback>? = null
    private var mActivePointerId: Int = 0
    private var mInitialY: Int = 0
    private var mTouchingScrollingChild: Boolean = false
    private var APP_TOOLBAR_HEIGHT: Int = 0 // SET IN CONSTRUCTOR
    private var SYSTEM_BAR_HEIGHT: Int = 0 // SET IN CONSTRUCTOR

    private val toolbarsHeight by lazy { SYSTEM_BAR_HEIGHT + APP_TOOLBAR_HEIGHT }
    var tabbedPagerLayout: TabbedPagerLayout? = null
        get() {
            return field
        }
        set(value) {
            field = value
            field?.pageChangeListener = this@BottomSheetBehaviorPinned
        }


    /**
     * Default constructor for instantiating BottomSheetBehaviors.
     */
    constructor() {}

    /**
     * Default constructor for inflating BottomSheetBehaviors from layout.
     *
     * @param context The [Context].
     * @param attrs   The [AttributeSet].
     */
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        var a = context.obtainStyledAttributes(attrs, android.support.design.R.styleable.BottomSheetBehavior_Layout)
        peekHeight = a.getDimensionPixelSize(android.support.design.R.styleable.BottomSheetBehavior_Layout_behavior_peekHeight, 0)
        isHideable = a.getBoolean(android.support.design.R.styleable.BottomSheetBehavior_Layout_behavior_hideable, false)
        a.recycle()

        /**
         * Getting the anchorPoint...
         */
        anchorPoint = DEFAULT_ANCHOR_POINT
        a = context.obtainStyledAttributes(attrs, R.styleable.CustomBottomSheetBehavior)
        if (attrs != null)
            anchorPoint = a.getDimension(R.styleable.CustomBottomSheetBehavior_anchorPoint, 0f).toInt()
        a.recycle()

        APP_TOOLBAR_HEIGHT = context.resources.getDimensionPixelSize(R.dimen.app_toolbar_height)
        SYSTEM_BAR_HEIGHT = getStatusBarHeight(context)

        val configuration = ViewConfiguration.get(context)
        mMinimumVelocity = configuration.scaledMinimumFlingVelocity.toFloat()
    }

    override fun onSaveInstanceState(parent: CoordinatorLayout?, child: V?): Parcelable {
        return SavedState(super.onSaveInstanceState(parent, child), mState)
    }

    override fun onRestoreInstanceState(parent: CoordinatorLayout?, child: V?, state: Parcelable?) {
        val ss = state as SavedState?
        super.onRestoreInstanceState(parent, child, ss!!.superState)
        // Intermediate states are restored as collapsed state
        if (ss.state == STATE_DRAGGING || ss.state == STATE_SETTLING) {
            mState = STATE_COLLAPSED
        } else {
            mState = ss.state
        }

        mLastStableState = mState
    }

    override fun onLayoutChild(parent: CoordinatorLayout, child: V, layoutDirection: Int): Boolean {
        // First let the parent lay it out
        if (mState != STATE_DRAGGING && mState != STATE_SETTLING) {
            if (ViewCompat.getFitsSystemWindows(parent) && !ViewCompat.getFitsSystemWindows(child)) {
                child.fitsSystemWindows = true
            }
            parent.onLayoutChild(child, layoutDirection)
        }

        // Offset the bottom sheet
        mParentHeight = parent.height
        expandedOffset = Math.max(toolbarsHeight, mParentHeight + toolbarsHeight - child.height)
        collapsedOffset = Math.max(mParentHeight - peekHeight, expandedOffset)

        /**
         * New behavior
         */
        if (mState == STATE_ANCHOR_POINT) {
            ViewCompat.offsetTopAndBottom(child, anchorPoint)
        } else if (mState == STATE_EXPANDED) {
            ViewCompat.offsetTopAndBottom(child, expandedOffset)
        } else if (isHideable && mState == STATE_HIDDEN) {
            ViewCompat.offsetTopAndBottom(child, mParentHeight)
        } else if (mState == STATE_COLLAPSED) {
            ViewCompat.offsetTopAndBottom(child, collapsedOffset)
        }
        if (mViewDragHelper == null) {
            mViewDragHelper = ViewDragHelper.create(parent, mDragCallback)
        }
        mViewRef = WeakReference(child)
        mNestedScrollingChildRef = WeakReference<View>(findScrollingChild(child))
        return true
    }

    override fun onInterceptTouchEvent(parent: CoordinatorLayout, child: V?, event: MotionEvent): Boolean {
        if (DRAGGING_ENABLED) {

            if (!child!!.isShown) {
                return false
            }

            val action = event.action
            if (action == MotionEvent.ACTION_DOWN) {
                reset()
            }

            when (action) {
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    mTouchingScrollingChild = false
                    mActivePointerId = MotionEvent.INVALID_POINTER_ID
                    // Reset the ignore flag
                    if (mIgnoreEvents) {
                        mIgnoreEvents = false
                        return false
                    }
                }
                MotionEvent.ACTION_DOWN -> {
                    val initialX = event.x.toInt()
                    mInitialY = event.y.toInt()
                    if (mState == STATE_ANCHOR_POINT) {
                        mActivePointerId = event.getPointerId(event.actionIndex)
                        mTouchingScrollingChild = true
                    } else {
                        val scroll = mNestedScrollingChildRef!!.get()
                        if (scroll != null && parent.isPointInChildBounds(scroll, initialX, mInitialY)) {
                            mActivePointerId = event.getPointerId(event.actionIndex)
                            mTouchingScrollingChild = true
                        }
                    }
                    mIgnoreEvents = mActivePointerId == MotionEvent.INVALID_POINTER_ID && !parent.isPointInChildBounds(child, initialX, mInitialY)
                }
                MotionEvent.ACTION_MOVE -> {
                }
            }

            if (action == MotionEvent.ACTION_CANCEL) {
                // We don't want to trigger a BottomSheet fling as a result of a Cancel MotionEvent (e.g., parent horizontal scroll view taking over touch events)
                mScrollVelocityTracker.clear()
            }

            if (!mIgnoreEvents && mViewDragHelper!!.shouldInterceptTouchEvent(event)) {
                return true
            }
            // We have to handle cases that the ViewDragHelper does not capture the bottom sheet because
            // it is not the top most view of its parent. This is not necessary when the touch event is
            // happening over the scrolling content as nested scrolling logic handles that case.
            val scroll = mNestedScrollingChildRef!!.get()
            return action == MotionEvent.ACTION_MOVE && scroll != null &&
                    !mIgnoreEvents && mState != STATE_DRAGGING &&
                    !parent.isPointInChildBounds(scroll, event.x.toInt(), event.y.toInt()) &&
                    Math.abs(mInitialY - event.y) > mViewDragHelper!!.touchSlop
        }
        return false
    }

    override fun onTouchEvent(parent: CoordinatorLayout?, child: V?, event: MotionEvent?): Boolean {
        if (!child!!.isShown) {
            return false
        }

        val action = MotionEventCompat.getActionMasked(event!!)
        if (mState == STATE_DRAGGING && action == MotionEvent.ACTION_DOWN) {
            return true
        }

        mViewDragHelper!!.processTouchEvent(event)

        if (action == MotionEvent.ACTION_DOWN) {
            reset()
        }

        // The ViewDragHelper tries to capture only the top-most View. We have to explicitly tell it
        // to capture the bottom sheet in case it is not captured and the touch slop is passed.
        if (action == MotionEvent.ACTION_MOVE && !mIgnoreEvents) {
            if (Math.abs(mInitialY - event.y) > mViewDragHelper!!.touchSlop) {
                mViewDragHelper!!.captureChildView(child, event.getPointerId(event.actionIndex))
            }
        }
        return !mIgnoreEvents
    }

    override fun onStartNestedScroll(coordinatorLayout: CoordinatorLayout, child: V, directTargetChild: View, target: View, nestedScrollAxes: Int): Boolean {
        if (DRAGGING_ENABLED) {
            mNestedScrolled = false
            return nestedScrollAxes and ViewCompat.SCROLL_AXIS_VERTICAL != 0
        } else {
            return false // DO NOT ACCEPT EVENTS
        }
    }

    private val mScrollVelocityTracker = ScrollVelocityTracker()

    private inner class ScrollVelocityTracker {
        private var mPreviousScrollTime: Long = 0
        var scrollVelocity = 0f
            private set

        fun recordScroll(dy: Int) {
            val now = System.currentTimeMillis()

            if (mPreviousScrollTime > 0) {
                val elapsed = now - mPreviousScrollTime
                scrollVelocity = dy.toFloat() / elapsed * 1000 // pixels per sec
            }

            mPreviousScrollTime = now
        }

        fun clear() {
            mPreviousScrollTime = 0
            scrollVelocity = 0f
        }
    }

    override fun onNestedPreScroll(coordinatorLayout: CoordinatorLayout, child: V, target: View, dx: Int, dy: Int, consumed: IntArray) {
        val scrollingChild = mNestedScrollingChildRef!!.get()
        if (target !== scrollingChild) {
            return
        }

        mScrollVelocityTracker.recordScroll(dy)

        val currentTop = child.top
        val newTop = currentTop - dy

        // Force stop at the anchor - do not go from collapsed to expanded in one scroll
        if (mLastStableState == STATE_COLLAPSED && newTop < anchorPoint || mLastStableState == STATE_EXPANDED && newTop > anchorPoint) {
            consumed[1] = dy
            ViewCompat.offsetTopAndBottom(child, anchorPoint - currentTop)
            dispatchOnSlide(child.top)
            mNestedScrolled = true
            return
        }

        if (dy > 0) { // Upward
            if (newTop < expandedOffset) {
                consumed[1] = currentTop - expandedOffset
                ViewCompat.offsetTopAndBottom(child, -consumed[1])
                setStateInternal(STATE_EXPANDED)
            } else {
                consumed[1] = dy
                ViewCompat.offsetTopAndBottom(child, -dy)
                setStateInternal(STATE_DRAGGING)
            }
        } else if (dy < 0) { // Downward
            if (!ViewCompat.canScrollVertically(target, -1)) {
                if (newTop <= collapsedOffset || isHideable) {
                    consumed[1] = dy
                    ViewCompat.offsetTopAndBottom(child, -dy)
                    setStateInternal(STATE_DRAGGING)
                } else {
                    consumed[1] = currentTop - collapsedOffset
                    ViewCompat.offsetTopAndBottom(child, -consumed[1] + APP_TOOLBAR_HEIGHT)
                    setStateInternal(STATE_COLLAPSED)
                }
            }
        }
        dispatchOnSlide(child.top)
        mNestedScrolled = true
    }

    override fun onStopNestedScroll(coordinatorLayout: CoordinatorLayout, child: V, target: View) {
        if (DRAGGING_ENABLED) {
            if (child.top == expandedOffset) {
                setStateInternal(STATE_EXPANDED)
                mLastStableState = STATE_EXPANDED
                return
            }
            if (target !== mNestedScrollingChildRef!!.get() || !mNestedScrolled) {
                return
            }
            val top: Int
            val targetState: Int

            // Are we flinging up?
            val scrollVelocity = mScrollVelocityTracker.scrollVelocity
            if (scrollVelocity > mMinimumVelocity) {
                if (mLastStableState == STATE_COLLAPSED) {
                    // Fling from collapsed to anchor
                    top = anchorPoint
                    targetState = STATE_ANCHOR_POINT
                } else if (mLastStableState == STATE_ANCHOR_POINT) {
                    // Fling from anchor to expanded
                    top = expandedOffset
                    targetState = STATE_EXPANDED
                } else {
                    // We are already expanded
                    top = expandedOffset
                    targetState = STATE_EXPANDED
                }
            } else
            // Are we flinging down?
                if (scrollVelocity < -mMinimumVelocity) {
                    if (mLastStableState == STATE_EXPANDED) {
                        // Fling from EXPANDED to COLLAPSED (skip ANCHOR)
//                        top = anchorPoint
//                        targetState = STATE_ANCHOR_POINT
                        top = collapsedOffset
                        targetState = STATE_COLLAPSED
                    } else if (mLastStableState == STATE_ANCHOR_POINT) {
                        // Fling from anchor to collapsed
                        top = collapsedOffset
                        targetState = STATE_COLLAPSED
                    } else {
                        // We are already collapsed
                        top = collapsedOffset
                        targetState = STATE_COLLAPSED
                    }
                } else {
                    // Collapse?
                    val currentTop = child.top
                    if (currentTop > anchorPoint * 1.25) { // Multiply by 1.25 to account for parallax. The currentTop needs to be pulled down 50% of the anchor point before collapsing.
                        top = collapsedOffset
                        targetState = STATE_COLLAPSED
                    } else if (currentTop < anchorPoint * 0.5) {
                        top = expandedOffset
                        targetState = STATE_EXPANDED
                    } else {
                        top = anchorPoint
                        targetState = STATE_ANCHOR_POINT
                    }// Snap back to the anchor
                    // Expand?
                }// Not flinging, just settle to the nearest state

            mLastStableState = targetState
            if (mViewDragHelper!!.smoothSlideViewTo(child, child.left, top)) {
                setStateInternal(STATE_SETTLING)
                ViewCompat.postOnAnimation(child, SettleRunnable(child, targetState))
            } else {
                setStateInternal(targetState)
            }
            mNestedScrolled = false
        }
    }

    override fun onNestedPreFling(coordinatorLayout: CoordinatorLayout, child: V, target: View, velocityX: Float, velocityY: Float): Boolean {
        if (DRAGGING_ENABLED) {
            return target === mNestedScrollingChildRef!!.get() && (mState != STATE_EXPANDED || super.onNestedPreFling(coordinatorLayout, child, target, velocityX, velocityY))
        } else {
            return false // DID NOT CONSUME Fling
        }
    }

    /**
     * Adds a callback to be notified of bottom sheet events.
     *
     * @param callback The callback to notify when bottom sheet events occur.
     */
    fun addBottomSheetCallback(callback: BottomSheetCallback) {
        if (mCallback == null)
            mCallback = Vector()

        mCallback!!.add(callback)
    }

    /**
     * Gets the current state of the bottom sheet.
     *
     * @return One of [.STATE_EXPANDED], [.STATE_ANCHOR_POINT], [.STATE_COLLAPSED],
     * [.STATE_DRAGGING], and [.STATE_SETTLING].
     */
    /**
     * Sets the state of the bottom sheet. The bottom sheet will transition to that state with
     * animation.
     *
     * @param state One of [.STATE_COLLAPSED], [.STATE_ANCHOR_POINT],
     * [.STATE_EXPANDED] or [.STATE_HIDDEN].
     */
    // The view is not laid out yet; modify mState and let onLayoutChild handle it later
    /**
     * New behavior (added: state == STATE_ANCHOR_POINT ||)
     */
    var state: Int
        @State
        get() = mState
        set(@State state) {
            if (state == mState) {
                return
            }
            if (mViewRef == null) {
                if (state == STATE_COLLAPSED || state == STATE_EXPANDED || state == STATE_ANCHOR_POINT || isHideable && state == STATE_HIDDEN) {
                    mState = state
                    mLastStableState = state
                }
                return
            }
            val child = mViewRef!!.get() ?: return
            val top: Int
            if (state == STATE_COLLAPSED) {
                top = collapsedOffset
            } else if (state == STATE_ANCHOR_POINT) {
                top = anchorPoint
            } else if (state == STATE_EXPANDED) {
                top = expandedOffset
            } else if (isHideable && state == STATE_HIDDEN) {
                top = mParentHeight
            } else {
                throw IllegalArgumentException("Illegal state argument: " + state)
            }
            setStateInternal(STATE_SETTLING)
            if (mViewDragHelper!!.smoothSlideViewTo(child, child.left, top)) {
                ViewCompat.postOnAnimation(child, SettleRunnable(child, state))
            }
        }

    private fun setStateInternal(@State state: Int) {
        if (mState == state) {
            return
        }
        mState = state
        val bottomSheet = mViewRef!!.get()
        if (bottomSheet != null && mCallback != null) {
            notifyStateChangedToListeners(bottomSheet, state)
        }
    }

    private fun notifyStateChangedToListeners(bottomSheet: View, @State newState: Int) {
        for (bottomSheetCallback in mCallback!!) {
            bottomSheetCallback.onStateChanged(bottomSheet, newState)
        }
    }

    private fun notifyOnSlideToListeners(bottomSheet: View, slideOffset: Float) {
        for (bottomSheetCallback in mCallback!!) {
            bottomSheetCallback.onSlide(bottomSheet, slideOffset)
        }
    }

    private fun reset() {
        mActivePointerId = ViewDragHelper.INVALID_POINTER
    }

    private fun shouldHide(child: View?, yvel: Float): Boolean {
        if (child!!.top < collapsedOffset) {
            // It should not hide, but collapse.
            return false
        }
        val newTop = child.top + yvel * HIDE_FRICTION
        return Math.abs(newTop - collapsedOffset) / peekHeight.toFloat() > HIDE_THRESHOLD
    }

    private fun findScrollingChild(view: View): View? {
        if (view is NestedScrollingChild) {
            return view
        }
        if (view is ViewGroup) {
            var i = 0
            val count = view.childCount
            while (i < count) {
                val scrollingChild = findScrollingChild(view.getChildAt(i))
                if (scrollingChild != null) {
                    return scrollingChild
                }
                i++
            }
        }
        return null
    }

    private val mDragCallback = object : ViewDragHelper.Callback() {

        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            if (mState == STATE_DRAGGING) {
                return false
            }
            if (mTouchingScrollingChild) {
                return false
            }
            if (mState == STATE_EXPANDED && mActivePointerId == pointerId) {
                val scroll = mNestedScrollingChildRef!!.get()
                if (scroll != null && ViewCompat.canScrollVertically(scroll, -1)) {
                    // Let the content scroll up
                    return false
                }
            }
            return mViewRef != null && mViewRef!!.get() === child
        }

        override fun onViewPositionChanged(changedView: View?, left: Int, top: Int, dx: Int, dy: Int) {
            dispatchOnSlide(top)
        }

        override fun onViewDragStateChanged(state: Int) {
            if (state == ViewDragHelper.STATE_DRAGGING) {
                setStateInternal(STATE_DRAGGING)
            }
        }

        override fun onViewReleased(releasedChild: View?, xvel: Float, yvel: Float) {
            val top: Int
            @State val targetState: Int
            if (yvel < 0) { // Moving up
                top = expandedOffset
                targetState = STATE_EXPANDED
            } else if (isHideable && shouldHide(releasedChild, yvel)) {
                top = mParentHeight
                targetState = STATE_HIDDEN
            } else if (yvel == 0f) {
                val currentTop = releasedChild!!.top
                if (Math.abs(currentTop - expandedOffset) < Math.abs(currentTop - collapsedOffset)) {
                    top = expandedOffset
                    targetState = STATE_EXPANDED
                } else {
                    top = collapsedOffset
                    targetState = STATE_COLLAPSED
                }
            } else {
                top = collapsedOffset
                targetState = STATE_COLLAPSED
            }
            if (mViewDragHelper!!.settleCapturedViewAt(releasedChild!!.left, top)) {
                setStateInternal(STATE_SETTLING)
                ViewCompat.postOnAnimation(releasedChild, SettleRunnable(releasedChild, targetState))
            } else {
                setStateInternal(targetState)
            }
        }

        override fun clampViewPositionVertical(child: View?, top: Int, dy: Int): Int {
            return constrain(top, expandedOffset, if (isHideable) mParentHeight else collapsedOffset)
        }

        internal fun constrain(amount: Int, low: Int, high: Int): Int {
            return if (amount < low) low else if (amount > high) high else amount
        }

        override fun clampViewPositionHorizontal(child: View?, left: Int, dx: Int): Int {
            return child!!.left
        }

        override fun getViewVerticalDragRange(child: View?): Int {
            return if (isHideable) {
                mParentHeight - expandedOffset
            } else {
                collapsedOffset - expandedOffset
            }
        }
    }

    private fun dispatchOnSlide(top: Int) {
        val bottomSheet = mViewRef!!.get()
        if (bottomSheet != null && mCallback != null) {
            if (top > collapsedOffset) {
                notifyOnSlideToListeners(bottomSheet, (collapsedOffset - top).toFloat() / peekHeight)
            } else {
                notifyOnSlideToListeners(bottomSheet, (collapsedOffset - top).toFloat() / (collapsedOffset - expandedOffset))
            }
        }
    }

    private inner class SettleRunnable internal constructor(private val mView: View, @State private val mTargetState: Int) : Runnable {

        override fun run() {
            if (mViewDragHelper != null && mViewDragHelper!!.continueSettling(true)) {
                ViewCompat.postOnAnimation(mView, this)
            } else {
                setStateInternal(mTargetState)
            }
        }
    }

    private class SavedState : View.BaseSavedState {

        @State
        internal val state: Int

        constructor(source: Parcel) : super(source) {

            state = source.readInt()
        }

        constructor(superState: Parcelable, @State state: Int) : super(superState) {
            this.state = state
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(state)
        }

        companion object {

            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel): SavedState {
                    return SavedState(source)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }

    private fun getStatusBarHeight(context: Context): Int {
        var result = 0
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = context.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    /**
     * Callback for monitoring events about bottom sheets.
     */
    abstract class BottomSheetCallback {

        /**
         * Called when the bottom sheet changes its state.
         *
         * @param bottomSheet The bottom sheet view.
         * @param newState    The new state. This will be one of [.STATE_DRAGGING],
         * [.STATE_SETTLING], [.STATE_ANCHOR_POINT],
         * [.STATE_EXPANDED],
         * [.STATE_COLLAPSED], or [.STATE_HIDDEN].
         */
        abstract fun onStateChanged(bottomSheet: View, @State newState: Int)

        /**
         * Called when the bottom sheet is being dragged.
         *
         * @param bottomSheet The bottom sheet view.
         * @param slideOffset The new offset of this bottom sheet within its range, from 0 to 1
         * when it is moving upward, and from 0 to -1 when it moving downward.
         */
        abstract fun onSlide(bottomSheet: View, slideOffset: Float)
    }

    private val DRAGGING_ENABLED: Boolean
        get() {
            return when (mState) {
                BottomSheetBehaviorPinned.STATE_EXPANDED -> {
                    return scrollingContent?.scrollY == 0
                }
                else -> true
            }
        }

    /* */
    private var scrollingContent: NestedScrollView? = null

    fun trackScrollingContent(content: NestedScrollView?) {
        scrollingContent = content
        // EACH TIME ScrollingContent IS SET AS TRACKED (ACTIVE), RESET ITS VERTICAL SCROLL TO TOP
        scrollingContent?.scrollTo(0, 0)
        scrollingContent?.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, _, _, _ -> onScrollingContentChange() })
        onScrollingContentChange()
    }

    private fun onScrollingContentChange() {
        tabbedPagerLayout?.markAsSheetDragEnabled(DRAGGING_ENABLED)
    }

    // ---------------------------------------
    // PageChangeListener methods
    override fun onPageSelected(position: Int) {
        Log.d(TAG, "onPageSelected $position")
        when (mState) {
            STATE_COLLAPSED -> state = STATE_ANCHOR_POINT
            STATE_EXPANDED -> { if (position == tabbedPagerLayout?.currentPage) state = STATE_COLLAPSED }
            else -> {}
        }
    }
}