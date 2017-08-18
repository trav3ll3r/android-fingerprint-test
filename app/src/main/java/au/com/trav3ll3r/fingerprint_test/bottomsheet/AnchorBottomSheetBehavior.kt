package au.com.trav3ll3r.fingerprint_test.bottomsheet

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.os.Parcel
import android.os.Parcelable
import android.support.annotation.IntDef
import android.support.annotation.RestrictTo
import android.support.annotation.RestrictTo.Scope.LIBRARY_GROUP
import android.support.annotation.VisibleForTesting
import android.support.design.widget.CoordinatorLayout
import android.support.v4.os.ParcelableCompat
import android.support.v4.os.ParcelableCompatCreatorCallbacks
import android.support.v4.view.AbsSavedState
import android.support.v4.view.MotionEventCompat
import android.support.v4.view.NestedScrollingChild
import android.support.v4.view.VelocityTrackerCompat
import android.support.v4.view.ViewCompat
import android.support.v4.widget.ViewDragHelper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import au.com.trav3ll3r.fingerprint_test.R
import java.lang.ref.WeakReference
import java.util.*

/**
 * An interaction behavior plugin for an extended child view of [android.support.design.widget.BottomSheetBehavior]
 * to work similar to the Google Maps bottom sheet with PEEK, ANCHOR and EXPANDED states
 */
class AnchorBottomSheetBehavior<V : View> : CoordinatorLayout.Behavior<V> {

    companion object {

        /**
         * The bottom sheet is dragging.
         */
        const val STATE_DRAGGING = 1

        /**
         * The bottom sheet is settling.
         */
        const val STATE_SETTLING = 2

        /**
         * The bottom sheet is expanded.
         */
        const val STATE_EXPANDED = 3

        /**
         * The bottom sheet is collapsed.
         */
        const val STATE_COLLAPSED = 4

        /**
         * The bottom sheet is hidden.
         */
        const val STATE_HIDDEN = 5

        /**
         * The bottom sheet is anchored.
         */
        const val STATE_ANCHORED = 6

        /**
         * Peek at 64dp.
         *
         *
         *
         * This can be used as a parameter for [.setPeekHeight].
         * [.getPeekHeight] will return this when the value is set.
         */
        val PEEK_HEIGHT_AUTO = -1

        private val HIDE_THRESHOLD = 0.5f

        private val HIDE_FRICTION = 0.1f

        /**
         * Anchor at the 16:9 ratio keyline of its parent.
         *
         *
         *
         * This can be used as a parameter for [.setAnchorHeight].
         * [.getAnchorHeight] ()} will return this when the value is set.
         */
        val ANCHOR_HEIGHT_AUTO = -1

        /**
         * A utility function to get the [AnchorBottomSheetBehavior] associated with the `view`.
         *
         * @param view The [View] with [AnchorBottomSheetBehavior].
         * @return The [AnchorBottomSheetBehavior] associated with the `view`.
         */
        fun <V : View> from(view: V): AnchorBottomSheetBehavior<V> {
            val params = view.layoutParams as? CoordinatorLayout.LayoutParams ?: throw IllegalArgumentException("The view is not a child of CoordinatorLayout")
            val behavior = params
                    .behavior as? AnchorBottomSheetBehavior<*> ?: throw IllegalArgumentException(
                    "The view is not associated with AnchorBottomSheetBehavior")
            return behavior as AnchorBottomSheetBehavior<V>
        }
    }

    /**
     * Callback for monitoring events about bottom sheets.
     */
    abstract class AnchorBottomSheetCallback {

        /**
         * Called when the bottom sheet changes its state.
         *
         * @param bottomSheet The bottom sheet view.
         * @param newState    The new state. This will be one of [.STATE_DRAGGING],
         * [.STATE_SETTLING], [.STATE_EXPANDED],
         * [.STATE_COLLAPSED], or [.STATE_HIDDEN].
         */
        abstract fun onStateChanged(bottomSheet: View, @State newState: Int)

        /**
         * Called when the bottom sheet is being dragged.
         *
         * @param bottomSheet The bottom sheet view.
         * @param slideOffset The new offset of this bottom sheet within [-1,1] range. Offset
         * increases as this bottom sheet is moving upward. From 0 to 1 the sheet
         * is between collapsed and expanded states and from -1 to 0 it is
         * between hidden and collapsed states.
         */
        abstract fun onSlide(bottomSheet: View, slideOffset: Float)
    }

    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    @IntDef(STATE_EXPANDED.toLong(), STATE_COLLAPSED.toLong(), STATE_DRAGGING.toLong(), STATE_SETTLING.toLong(), STATE_HIDDEN.toLong(), STATE_ANCHORED.toLong())
    @Retention(AnnotationRetention.SOURCE)
    annotation class State

    private var mMaximumVelocity: Float = 0.0f

    private var mPeekHeight: Int = 0

    private var mPeekHeightAuto: Boolean = false

    @get:VisibleForTesting
    private var peekHeightMin: Int = 0

    private var mAnchorHeight: Int = 0

    private var mAnchorHeightAuto: Boolean = false

    private var mAnchorHeightMin: Int = 0

    private var mAnchorOffset: Int = 0

    private var mMinOffset: Int = 0

    private var mMaxOffset: Int = 0

    /**
     * Gets whether this bottom sheet can hide when it is swiped down.
     *
     * @return `true` if this bottom sheet can hide.
     * @attr ref R.styleable#AnchorBottomSheetBehavior_Layout_behavior_hideable
     */
    /**
     * Sets whether this bottom sheet can hide when it is swiped down.
     *
     * @param hideable `true` to make this bottom sheet hideable.
     * @attr ref R.styleable#AnchorBottomSheetBehavior_Layout_behavior_hideable
     */
    var isHideable: Boolean = false

    /**
     * Sets whether this bottom sheet should skip the collapsed state when it is being hidden
     * after it is expanded once.
     *
     * @return Whether the bottom sheet should skip the collapsed state.
     * @attr ref R.styleable#AnchorBottomSheetBehavior_Layout_behavior_skipCollapsed
     */
    /**
     * Sets whether this bottom sheet should skip the collapsed state when it is being hidden
     * after it is expanded once. Setting this to true has no effect unless the sheet is hideable.
     *
     * @param skipCollapsed True if the bottom sheet should skip the collapsed state.
     * @attr ref R.styleable#AnchorBottomSheetBehavior_Layout_behavior_skipCollapsed
     */
    var skipCollapsed: Boolean = false

    private val mCurrentColor: Int = 0

    var collapsedColor: Int = 0

    var anchorColor: Int = 0

    var collapsedTextColor: Int = 0

    var anchorTextColor: Int = 0

    @State
    private var mState = STATE_HIDDEN

    private var mViewDragHelper: ViewDragHelper? = null

    private var mIgnoreEvents: Boolean = false

    private var mLastNestedScrollDy: Int = 0

    private var mNestedScrolled: Boolean = false

    private var mParentHeight: Int = 0

    private var mViewRef: WeakReference<V>? = null

    private var mNestedScrollingChildRef: WeakReference<View>? = null

    private var mCallback: AnchorBottomSheetCallback? = null

    private var mVelocityTracker: VelocityTracker? = null

    private var mActivePointerId: Int = 0

    private var mInitialY: Int = 0

    private var mTouchingScrollingChild: Boolean = false

    private lateinit var bottomsheet: LinearLayout

    private var stateFlag = false

    var headerLayout: View? = null
        private set
    private var headerTextViews: List<TextView>? = null

    var contentLayout: View? = null
        private set

    private var parallax: View? = null
        set(view) {
            field = view
            this.parallax!!.visibility = View.INVISIBLE
        }

    private val anchoredViews = ArrayList<View>()
    //    private GoogleMap map;

    /**
     * Default constructor for instantiating GoogleMapsBottomSheetBehaviors.
     */
    constructor() {}

    /**
     * Default constructor for inflating GoogleMapsBottomSheetBehaviors from layout.
     *
     * @param context The [Context].
     * @param attrs   The [AttributeSet].
     */
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.AnchorBottomSheetBehavior_Layout)
        val peekValue = a.peekValue(R.styleable.AnchorBottomSheetBehavior_Layout_behavior_peekHeight)
        if (peekValue != null && peekValue.data == PEEK_HEIGHT_AUTO) {
            peekHeight = peekValue.data
        } else {
            peekHeight = a.getDimensionPixelSize(R.styleable.AnchorBottomSheetBehavior_Layout_behavior_peekHeight, PEEK_HEIGHT_AUTO)
        }
        isHideable = a.getBoolean(R.styleable.AnchorBottomSheetBehavior_Layout_behavior_can_hide, false)
        skipCollapsed = a.getBoolean(R.styleable.AnchorBottomSheetBehavior_Layout_behavior_skipCollapsed, false)

        val anchorValue = a.peekValue(R.styleable.AnchorBottomSheetBehavior_Layout_behavior_anchorHeight)
        if (anchorValue != null && anchorValue.data == ANCHOR_HEIGHT_AUTO) {
            anchorHeight = anchorValue.data
        } else {
            anchorHeight = a.getDimensionPixelSize(R.styleable.AnchorBottomSheetBehavior_Layout_behavior_anchorHeight, ANCHOR_HEIGHT_AUTO)
        }

        collapsedColor = a.getColor(R.styleable.AnchorBottomSheetBehavior_Layout_behavior_collapsedColor, Color.WHITE)
        val anchorColorValue = TypedValue()
        context.theme.resolveAttribute(R.attr.colorAccent, anchorColorValue, true)
        anchorColor = a.getColor(R.styleable.AnchorBottomSheetBehavior_Layout_behavior_anchorColor, anchorColorValue.data)

        anchorTextColor = a.getColor(R.styleable.AnchorBottomSheetBehavior_Layout_behavior_anchorTextColor, Color.WHITE)
        collapsedTextColor = a.getColor(R.styleable.AnchorBottomSheetBehavior_Layout_behavior_collapsedTextColor, Color.BLACK)

        bottomsheet = LinearLayout(context)
        bottomsheet.orientation = LinearLayout.VERTICAL
        bottomsheet.setOnClickListener {
            stateFlag = mState == STATE_ANCHORED
            if (mState == STATE_COLLAPSED) {
                state = STATE_ANCHORED
            } else {
                state = STATE_COLLAPSED
            }
        }

        if (a.hasValue(R.styleable.AnchorBottomSheetBehavior_Layout_behavior_header_layout)) {
            headerLayout = LayoutInflater.from(context).inflate(a.getResourceId(R.styleable.AnchorBottomSheetBehavior_Layout_behavior_header_layout, 0), null)
            headerTextViews = getAllTextViewChildrenRecursively(headerLayout)
            bottomsheet.addView(headerLayout, 0)
        }

        if (a.hasValue(R.styleable.AnchorBottomSheetBehavior_Layout_behavior_content_layout)) {
            contentLayout = LayoutInflater.from(context).inflate(a.getResourceId(R.styleable.AnchorBottomSheetBehavior_Layout_behavior_content_layout, 0), null)
            bottomsheet.addView(contentLayout)
        }

        a.recycle()

        val configuration = ViewConfiguration.get(context)
        mMaximumVelocity = configuration.scaledMaximumFlingVelocity.toFloat()
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
    }

    override fun onLayoutChild(parent: CoordinatorLayout?, child: V?, layoutDirection: Int): Boolean {
        if (ViewCompat.getFitsSystemWindows(parent) && !ViewCompat.getFitsSystemWindows(child)) {
            ViewCompat.setFitsSystemWindows(child!!, true)
        }
        val savedTop = child!!.top
        // First let the parent lay it out
        parent!!.onLayoutChild(child, layoutDirection)
        // Offset the bottom sheet
        mParentHeight = parent.height
        if (mPeekHeightAuto) {
            if (peekHeightMin == 0) {
                peekHeightMin = parent.resources.getDimensionPixelSize(R.dimen.bottom_sheet_peek_height_min)
            }
            mPeekHeight = peekHeightMin
        }
        if (mAnchorHeightAuto) {
            if (mAnchorHeightMin == 0) {
                mAnchorHeightMin = mParentHeight - parent.width * 9 / 16
            }
            mAnchorHeight = mAnchorHeightMin
        }
        mMinOffset = Math.max(0, mParentHeight - child.height)
        mMaxOffset = Math.max(mParentHeight - mPeekHeight, mMinOffset)
        mAnchorOffset = Math.min(mParentHeight - mAnchorHeight, mMaxOffset)
        if (mState == STATE_EXPANDED) {
            ViewCompat.offsetTopAndBottom(child, mMinOffset)
            anchorViews(mMinOffset)
        } else if (isHideable && mState == STATE_HIDDEN) {
            ViewCompat.offsetTopAndBottom(child, mParentHeight)
            anchorViews(mParentHeight)
        } else if (mState == STATE_COLLAPSED) {
            ViewCompat.offsetTopAndBottom(child, mMaxOffset)
            anchorViews(mMaxOffset)
        } else if (mState == STATE_DRAGGING || mState == STATE_SETTLING) {
            ViewCompat.offsetTopAndBottom(child, savedTop - child.top)
        } else if (mState == STATE_ANCHORED) {
            ViewCompat.offsetTopAndBottom(child, mAnchorOffset)
            if (this.parallax != null) {
                val reference = savedTop - this.parallax!!.height
                this.parallax!!.y = reference.toFloat()
                this.parallax!!.visibility = View.VISIBLE
                anchorViews(reference)
            } else {
                anchorViews(mAnchorOffset)
            }
        }
        if (mViewDragHelper == null) {
            mViewDragHelper = ViewDragHelper.create(parent, mDragCallback)
        }
        mViewRef = WeakReference(child)
        mNestedScrollingChildRef = WeakReference<View>(findScrollingChild(child))
        // add missing views to the layout
        val nestedScrolling = mNestedScrollingChildRef!!.get() as ViewGroup
        if (nestedScrolling.childCount == 0) {
            nestedScrolling.addView(bottomsheet)
        }
        return true
    }

    override fun onInterceptTouchEvent(parent: CoordinatorLayout?, child: V?, event: MotionEvent?): Boolean {
        if (!child!!.isShown) {
            mIgnoreEvents = true
            return false
        }

        val action = MotionEventCompat.getActionMasked(event!!)
        // Record the velocity
        if (action == MotionEvent.ACTION_DOWN) {
            reset()
        }
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }
        mVelocityTracker!!.addMovement(event)
        when (action) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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
                val scroll = mNestedScrollingChildRef!!.get()
                if (scroll != null && parent!!.isPointInChildBounds(scroll, initialX, mInitialY)) {
                    mActivePointerId = event.getPointerId(event.actionIndex)
                    mTouchingScrollingChild = true
                }
                mIgnoreEvents = mActivePointerId == MotionEvent.INVALID_POINTER_ID && !parent!!.isPointInChildBounds(child, initialX, mInitialY)
            }
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
                !parent!!.isPointInChildBounds(scroll, event.x.toInt(), event.y.toInt()) &&
                Math.abs(mInitialY - event.y) > mViewDragHelper!!.touchSlop
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
        // Record the velocity
        if (action == MotionEvent.ACTION_DOWN) {
            reset()
        }
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }
        mVelocityTracker!!.addMovement(event)
        // The ViewDragHelper tries to capture only the top-most View. We have to explicitly tell it
        // to capture the bottom sheet in case it is not captured and the touch slop is passed.
        if (action == MotionEvent.ACTION_MOVE && !mIgnoreEvents) {
            if (Math.abs(mInitialY - event.y) > mViewDragHelper!!.touchSlop) {
                mViewDragHelper!!.captureChildView(child, event.getPointerId(event.actionIndex))
            }
        }
        return !mIgnoreEvents
    }

    override fun onStartNestedScroll(coordinatorLayout: CoordinatorLayout, child: V,
                                     directTargetChild: View, target: View, nestedScrollAxes: Int): Boolean {
        mLastNestedScrollDy = 0
        mNestedScrolled = false
        return nestedScrollAxes and ViewCompat.SCROLL_AXIS_VERTICAL != 0
    }

    override fun onNestedPreScroll(coordinatorLayout: CoordinatorLayout, child: V, target: View, dx: Int,
                                   dy: Int, consumed: IntArray) {
        val scrollingChild = mNestedScrollingChildRef!!.get()
        if (target !== scrollingChild) {
            return
        }
        val currentTop = child.top
        val newTop = currentTop - dy
        if (dy > 0) { // Upward
            if (newTop < mMinOffset) {
                consumed[1] = currentTop - mMinOffset
                ViewCompat.offsetTopAndBottom(child, -consumed[1])
                setStateInternal(STATE_EXPANDED)
            } else {
                consumed[1] = dy
                ViewCompat.offsetTopAndBottom(child, -dy)
                setStateInternal(STATE_DRAGGING)
            }
        } else if (dy < 0) { // Downward
            if (!ViewCompat.canScrollVertically(target, -1)) {
                if (newTop <= mMaxOffset || isHideable) {
                    consumed[1] = dy
                    ViewCompat.offsetTopAndBottom(child, -dy)
                    setStateInternal(STATE_DRAGGING)
                } else {
                    consumed[1] = currentTop - mMaxOffset
                    ViewCompat.offsetTopAndBottom(child, -consumed[1])
                    setStateInternal(STATE_COLLAPSED)
                }
            }
        }
        dispatchOnSlide(child.top)
        mLastNestedScrollDy = dy
        mNestedScrolled = true
    }

    override fun onStopNestedScroll(coordinatorLayout: CoordinatorLayout, child: V, target: View) {
        if (child.top == mMinOffset) {
            setStateInternal(STATE_EXPANDED)
            return
        }
        if (target !== mNestedScrollingChildRef!!.get() || !mNestedScrolled) {
            return
        }
        val top: Int
        val targetState: Int
        if (mLastNestedScrollDy > 0) {
            val currentTop = child.top
            if (currentTop > mAnchorOffset) {
                top = mAnchorOffset
                targetState = STATE_ANCHORED
            } else {
                top = mMinOffset
                targetState = STATE_EXPANDED
            }
        } else if (isHideable && shouldHide(child, yVelocity)) {
            top = mParentHeight
            targetState = STATE_HIDDEN
        } else if (mLastNestedScrollDy == 0) {
            val currentTop = child.top
            if (Math.abs(currentTop - mMinOffset) < Math.abs(currentTop - mMaxOffset)) {
                top = mMinOffset
                targetState = STATE_EXPANDED
            } else {
                top = mMaxOffset
                targetState = STATE_COLLAPSED
            }
        } else {
            val currentTop = child.top
            if (currentTop > mAnchorOffset) {
                top = mMaxOffset
                targetState = STATE_COLLAPSED
            } else {
                top = mAnchorOffset
                targetState = STATE_ANCHORED
            }
        }
        if (mViewDragHelper!!.smoothSlideViewTo(child, child.left, top)) {
            setStateInternal(STATE_SETTLING)
            ViewCompat.postOnAnimation(child, SettleRunnable(child, targetState))
        } else {
            setStateInternal(targetState)
        }
        mNestedScrolled = false
    }

    override fun onNestedPreFling(coordinatorLayout: CoordinatorLayout, child: V, target: View,
                                  velocityX: Float, velocityY: Float): Boolean {
        return target === mNestedScrollingChildRef!!.get() && (mState != STATE_EXPANDED || super.onNestedPreFling(coordinatorLayout, child, target,
                velocityX, velocityY))
    }

    /**
     * Gets the height of the bottom sheet when it is collapsed.
     *
     * @return The height of the collapsed bottom sheet in pixels, or [.PEEK_HEIGHT_AUTO]
     * if the sheet is configured to peek automatically at 64dp
     * @attr ref R.styleable#AnchorBottomSheetBehavior_Layout_behavior_peekHeight
     */
    /**
     * Sets the height of the bottom sheet when it is collapsed.
     *
     * @param peekHeight The height of the collapsed bottom sheet in pixels, or
     * [.PEEK_HEIGHT_AUTO] to configure the sheet to peek automatically
     * at 64dp.
     * @attr ref R.styleable#AnchorBottomSheetBehavior_Layout_behavior_peekHeight
     */
    var peekHeight: Int
        get() = if (mPeekHeightAuto) PEEK_HEIGHT_AUTO else mPeekHeight
        set(peekHeight) {
            var layout = false
            if (peekHeight == PEEK_HEIGHT_AUTO) {
                if (!mPeekHeightAuto) {
                    mPeekHeightAuto = true
                    layout = true
                }
            } else if (mPeekHeightAuto || mPeekHeight != peekHeight) {
                mPeekHeightAuto = false
                mPeekHeight = Math.max(0, peekHeight)
                mMaxOffset = mParentHeight - peekHeight
                layout = true
            }
            if (layout && mState == STATE_COLLAPSED && mViewRef != null) {
                val view = mViewRef!!.get()
                view?.requestLayout()
            }
        }

    /**
     * Gets the offset of the bottom sheet when it is anchored.
     * Useful for setting the height of your parallax.
     *
     * @return The offset of the anchored bottom sheet from the top of its parent in pixels.
     */
    /**
     * Sets the offset of the bottom sheet when it is anchored.
     * Useful for when you know the height of your parallax but not the height of the parent.
     *
     * @param anchorOffset The offset of the anchored bottom sheet from the top of its parent in pixels.
     */
    var anchorOffset: Int
        get() = mAnchorOffset
        set(anchorOffset) {
            anchorHeight = mParentHeight - anchorOffset
        }

    /**
     * Gets the height of the bottom sheet when it is anchored.
     *
     * @return The height of the anchored bottom sheet in pixels, or [.ANCHOR_HEIGHT_AUTO]
     * if the sheet is configured to anchor automatically at 16:9 ratio keyline
     * @attr ref R.styleable#AnchorBottomSheetBehavior_Layout_behavior_anchorOffset
     */
    /**
     * Sets the height of the bottom sheet when it is anchored.
     *
     * @param anchorHeight The height of the anchored bottom sheet in pixels, or
     * [.ANCHOR_HEIGHT_AUTO] to configure the sheet to anchor automatically
     * at 16:9 ratio keyline. If the anchor height is smaller than
     * [.mPeekHeight], the anchor height will be default to the peek height.
     * @attr ref R.styleable#AnchorBottomSheetBehavior_Layout_behavior_anchorHeight
     */
    var anchorHeight: Int
        get() = if (mAnchorHeightAuto) ANCHOR_HEIGHT_AUTO else mAnchorHeight
        set(anchorHeight) {
            var layout = false
            if (anchorHeight == ANCHOR_HEIGHT_AUTO) {
                if (!mAnchorHeightAuto) {
                    mAnchorHeightAuto = true
                    layout = true
                }
            } else if (mAnchorHeightAuto || mAnchorHeight != anchorHeight) {
                mAnchorHeightAuto = false
                mAnchorHeight = Math.max(mPeekHeight, anchorHeight)
                mAnchorOffset = mParentHeight - anchorHeight
                layout = true
            }
            if (layout && mState == STATE_ANCHORED && mViewRef != null) {
                val view = mViewRef!!.get()
                view?.requestLayout()
            }
        }

    /**
     * Anchor a view to the top of the bottomsheet/parallax.
     *
     * @param view View to be anchored.
     * @throws IllegalStateException when the view given does not have
     * [android.view.ViewGroup.LayoutParams] of type [CoordinatorLayout.LayoutParams]
     */
    fun anchorView(view: View) {
        if (view.layoutParams !is CoordinatorLayout.LayoutParams) {
            throw IllegalStateException("View must be a child of a CoordinatorLayout")
        }
        anchoredViews.add(view)
    }

    /**
     * Unanchor a view from the bottomsheet, if it is anchored.
     *
     * @param view View to be unanchored.
     */
    fun unanchorView(view: View) {
        if (anchoredViews.contains(view)) {
            anchoredViews.remove(view)
        }
    }

    //    /**
    //     * Anchor the map to the bottomsheet. This will adjust the padding of the map into view
    //     * as the bottomsheet moves towards the top of the screen. This keeps the Google logo in view.
    //     *
    //     * @param map
    //     */
    //    public void anchorMap(GoogleMap map) {
    //        this.map = map;
    //    }
    //
    //    public void unanchorMap() {
    //        this.map = null;
    //    }

    /**
     * Sets a callback to be notified of bottom sheet events.
     *
     * @param callback The callback to notify when bottom sheet events occur.
     */
    fun setBottomSheetCallback(callback: AnchorBottomSheetCallback) {
        mCallback = callback
    }

    /**
     * Gets the current state of the bottom sheet.
     *
     * @return One of [.STATE_EXPANDED], [.STATE_COLLAPSED], [.STATE_DRAGGING],
     * [.STATE_ANCHORED] and [.STATE_SETTLING].
     */
    /**
     * Sets the state of the bottom sheet. The bottom sheet will transition to that state with
     * animation.
     *
     * @param state One of [.STATE_COLLAPSED], [.STATE_EXPANDED],
     * [.STATE_ANCHORED] or [.STATE_HIDDEN].
     */
    // The view is not laid out yet; modify mState and let onLayoutChild handle it later
    var state: Int
        @State
        get() = mState
        set(@State state) {
            if (state == mState) {
                return
            }
            if (mViewRef == null) {
                if (state == STATE_COLLAPSED || state == STATE_EXPANDED ||
                        isHideable && state == STATE_HIDDEN) {
                    mState = state
                }
                return
            }
            val child = mViewRef!!.get() ?: return
            val parent = child.parent
            if (parent != null && parent.isLayoutRequested && ViewCompat.isAttachedToWindow(child)) {
                child.post { startSettlingAnimation(child, state) }
            } else {
                startSettlingAnimation(child, state)
            }
        }

    private fun setStateInternal(@State state: Int) {
        if (mState == state) {
            return
        }
        mState = state
        // determine visibility of parallax
        if (this.parallax != null) {
            if (mState == STATE_COLLAPSED || mState == STATE_HIDDEN) {
                this.parallax!!.visibility = View.GONE
            } else {
                this.parallax!!.visibility = View.VISIBLE
            }
        }
        stateFlag = false
        val bottomSheet = mViewRef!!.get()
        if (bottomSheet != null && mCallback != null) {
            mCallback!!.onStateChanged(bottomSheet, state)
        }
    }

    private fun reset() {
        mActivePointerId = ViewDragHelper.INVALID_POINTER
        if (mVelocityTracker != null) {
            mVelocityTracker!!.recycle()
            mVelocityTracker = null
        }
    }

    private fun shouldHide(child: View?, yvel: Float): Boolean {
        if (skipCollapsed) {
            return true
        }
        if (child!!.top < mMaxOffset) {
            // It should not hide, but collapse.
            return false
        }
        val newTop = child.top + yvel * HIDE_FRICTION
        return Math.abs(newTop - mMaxOffset) / mPeekHeight.toFloat() > HIDE_THRESHOLD
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

    private val yVelocity: Float
        get() {
            mVelocityTracker!!.computeCurrentVelocity(1000, mMaximumVelocity)
            return VelocityTrackerCompat.getYVelocity(mVelocityTracker!!, mActivePointerId)
        }

    private fun startSettlingAnimation(child: View?, state: Int) {
        val top: Int
        if (state == STATE_COLLAPSED) {
            top = mMaxOffset
        } else if (state == STATE_EXPANDED) {
            top = mMinOffset
        } else if (isHideable && state == STATE_HIDDEN) {
            top = mParentHeight
        } else if (state == STATE_ANCHORED) {
            top = mAnchorOffset
        } else {
            throw IllegalArgumentException("Illegal state argument: " + state)
        }
        setStateInternal(STATE_SETTLING)
        if (mViewDragHelper!!.smoothSlideViewTo(child, child!!.left, top)) {
            ViewCompat.postOnAnimation(child, SettleRunnable(child, state))
        }
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
                top = mMinOffset
                targetState = STATE_EXPANDED
            } else if (isHideable && shouldHide(releasedChild, yvel)) {
                top = mParentHeight
                targetState = STATE_HIDDEN
            } else if (yvel == 0f) {
                val currentTop = releasedChild!!.top
                if (Math.abs(currentTop - mMinOffset) < Math.abs(currentTop - mMaxOffset)) {
                    top = mMinOffset
                    targetState = STATE_EXPANDED
                } else {
                    top = mMaxOffset
                    targetState = STATE_COLLAPSED
                }
            } else {
                top = mMaxOffset
                targetState = STATE_COLLAPSED
            }
            if (mViewDragHelper!!.settleCapturedViewAt(releasedChild!!.left, top)) {
                setStateInternal(STATE_SETTLING)
                ViewCompat.postOnAnimation(releasedChild,
                        SettleRunnable(releasedChild, targetState))
            } else {
                setStateInternal(targetState)
            }
        }

        override fun clampViewPositionVertical(child: View?, top: Int, dy: Int): Int {
            return constrain(top, mMinOffset, if (isHideable) mParentHeight else mMaxOffset)
        }

        override fun clampViewPositionHorizontal(child: View?, left: Int, dx: Int): Int {
            return child!!.left
        }

        override fun getViewVerticalDragRange(child: View?): Int {
            return if (isHideable) {
                mParentHeight - mMinOffset
            } else {
                mMaxOffset - mMinOffset
            }
        }
    }

    private fun dispatchOnSlide(top: Int) {
        val bottomSheet = mViewRef!!.get()

        val slideOffset: Float
        if (top > mMaxOffset) {
            slideOffset = (mMaxOffset - top).toFloat() / mPeekHeight
        } else {
            slideOffset = (mMaxOffset - top).toFloat() / (mMaxOffset - mMinOffset)
        }

        // move the parallax relative to the bottomsheet and update colors
        if (this.parallax != null) {
            val height = this.parallax!!.height
            val y = this.parallax!!.y
            if (slideOffset <= 0) {
                this.parallax!!.visibility = View.INVISIBLE
            } else if (mAnchorOffset >= top && y <= mAnchorOffset - height && (y > 0 || top >= height)) {
                this.parallax!!.y = (top - height).toFloat()
            } else if (slideOffset > 0 && (top >= mAnchorOffset || y > mAnchorOffset - height)) {
                // math for translating parallax relative to bottom sheet
                val init = (mAnchorOffset - height).toFloat()
                val travelDistance = mMaxOffset - init
                val percentCovered = (top - mAnchorOffset) / (mMaxOffset - mAnchorOffset).toFloat()
                this.parallax!!.y = init + travelDistance * percentCovered
            }
        }

        if (bottomSheet != null) {
            val reference: Int
            if (this.parallax != null && slideOffset > 0) {
                reference = this.parallax!!.y.toInt()
            } else {
                reference = bottomSheet.top
            }
            anchorViews(reference)

            if (mCallback != null) {
                mCallback!!.onSlide(bottomSheet, slideOffset)
            }
        }
    }

    private fun anchorViews(reference: Int) {
        // move all views that are anchored to the bottomsheet
        var i = 0
        val size = anchoredViews.size
        while (i < size) {
            val view = anchoredViews[i]
            val lp = view.layoutParams as CoordinatorLayout.LayoutParams
            view.y = (reference - lp.bottomMargin - lp.height).toFloat()
            i++
        }
    }

    private inner class SettleRunnable internal constructor(private val mView: View, @param:State /*@State*/private val mTargetState: Int) : Runnable {

        override fun run() {
            if (mViewDragHelper != null && mViewDragHelper!!.continueSettling(true)) {
                ViewCompat.postOnAnimation(mView, this)
            } else {
                setStateInternal(mTargetState)
            }
        }
    }

    private class SavedState : AbsSavedState {
        companion object {

            val CREATOR: Parcelable.Creator<SavedState> = ParcelableCompat.newCreator(
                    object : ParcelableCompatCreatorCallbacks<SavedState> {
                        override fun createFromParcel(`in`: Parcel, loader: ClassLoader): SavedState {
                            return SavedState(`in`, loader)
                        }

                        override fun newArray(size: Int): Array<SavedState?> {
                            return arrayOfNulls(size)
                        }
                    })
        }

        @State
        internal val state: Int

        @JvmOverloads constructor(source: Parcel, loader: ClassLoader? = null) : super(source, loader) {

            state = source.readInt()
        }

        constructor(superState: Parcelable, @State state: Int) : super(superState) {
            this.state = state
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(state)
        }


    }

    private val colorAnimation: ValueAnimator? = null

    private fun animateTextColorChange(colorTo: Int, duration: Int, view: TextView) {
        val colorAnimation = ValueAnimator.ofObject(
                ArgbEvaluator(), view.currentTextColor, colorTo)
                .setDuration(duration.toLong())
        colorAnimation.addUpdateListener { animation -> view.setTextColor(animation.animatedValue as Int) }
        colorAnimation.start()
    }

    private fun getAllTextViewChildrenRecursively(v: View?): List<TextView> {
        if (v is TextView) {
            val viewArrayList = ArrayList<TextView>()
            viewArrayList.add(v)
            return viewArrayList
        }

        val result = ArrayList<TextView>()
        if (v !is ViewGroup) {
            return result
        }

        (0 until v.childCount).forEach {
            val child = v.getChildAt(it)
            result.addAll(getAllTextViewChildrenRecursively(child))
        }
//        for (i in 0 until viewGroup.childCount) {
//            val child = viewGroup.getChildAt(i)
//            result.addAll(getAllTextViewChildrenRecursively(child))
//        }
        return result
    }
}

fun constrain(amount: Int, low: Int, high: Int): Int {
    return if (amount < low) low else if (amount > high) high else amount
}
