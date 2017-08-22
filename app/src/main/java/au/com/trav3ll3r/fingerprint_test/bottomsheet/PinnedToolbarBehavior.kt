package au.com.trav3ll3r.fingerprint_test.bottomsheet

import android.content.Context
import android.support.design.widget.CoordinatorLayout
import android.support.v4.widget.NestedScrollView
import android.util.AttributeSet
import android.view.View

import java.lang.ref.WeakReference

/**
 * This class will link the PinnedToolbar view (that can be anything extending View) with a
 * NestedScrollView (the dependency). Whenever dependency is moved, the PinnedToolbar will be moved alongside
 * top edge of dependency and act as fully attached to it, until it touches the AppBarLayout.Toolbar.
 * Then it will stay pinned and the content of dependency will scroll behind it.
 *
 * The PinnedToolbar needs to be <bold>a direct child of</bold> CoordinatorLayout and <bold>after</bold>
 * [BottomSheetBehaviorPinned] in the XML file to get desired behavior of content scrolling behind it.
 * It doesn't matter where the PinnedToolbar element start in XML, it will be moved with Dependency
 *
 * @param <V>
</V> */
class PinnedToolbarBehavior<V : View>(context: Context, attrs: AttributeSet) : CoordinatorLayout.Behavior<V>(context, attrs) {

    companion object {
        /**
         * A utility function to get the [PinnedToolbarBehavior] associated with the `view`.
         *
         * @param view The [View] with [PinnedToolbarBehavior].
         * @return The [PinnedToolbarBehavior] associated with the `view`.
         */
        fun <V : View> from(view: V): PinnedToolbarBehavior<V> {
            val params = view.layoutParams as? CoordinatorLayout.LayoutParams ?: throw IllegalArgumentException("The view is not a child of CoordinatorLayout")
            val behavior = params.behavior as? PinnedToolbarBehavior<*> ?: throw IllegalArgumentException(
                    "The view is not associated with PinnedToolbarBehavior")
            return behavior as PinnedToolbarBehavior<V>
        }
    }

    /**
     * To avoid using multiple "peekheight=" in XML and looking flexibility allowing [BottomSheetBehaviorPinned.mPeekHeight]
     * get changed dynamically we get the [NestedScrollView] that has
     * "app:layout_behavior=" [BottomSheetBehaviorPinned] inside the [CoordinatorLayout]
     */
    private var mBottomSheetBehaviorRef: WeakReference<BottomSheetBehaviorPinned<*>>? = null
    /**
     * Following [.onDependentViewChanged]'s docs mCurrentChildY just save the child Y
     * position.
     */
    private var mCurrentChildY: Int = 0

    override fun layoutDependsOn(parent: CoordinatorLayout, child: V, dependency: View): Boolean {
        if (dependency is NestedScrollView) {
            try {
                BottomSheetBehaviorPinned.from(dependency)
                return true
            } catch (ex: IllegalArgumentException) {
            }
        }
        return false
    }

    override fun onDependentViewChanged(parent: CoordinatorLayout, child: V, dependency: View): Boolean {
        /**
         * collapsedY and anchorPointY are calculated every time looking for
         * flexibility, in case that dependency's height, child's height or [BottomSheetBehaviorPinned.getPeekHeight]'s
         * value changes over time, I mean, you can have a [android.widget.ImageView]
         * using images with different sizes and you don't want to resize them or so
         */
        if (mBottomSheetBehaviorRef == null || mBottomSheetBehaviorRef!!.get() == null)
            setupBottomSheetBehavior(parent)

        /**
         * mCollapsedY: Y position in where backdrop gets hidden behind dependency.
         * [BottomSheetBehaviorPinned.getPeekHeight] and collapsedY are the same point on screen.
         */
        //        val collapsedY = dependency!!.height - mBottomSheetBehaviorRef?.get()?.getPeekHeight()?: 0
        /**
         * anchorPointY: with top being Y=0, anchorPointY defines the point in Y where 2 of two things could happen:
         * 1) the backdrop should be moved behind dependency view (when [.mCurrentChildY] has positive value) or
         * 2) the dependency view overlaps the backdrop (when [.mCurrentChildY] has negative value)
         */
        //        val anchorPointY = child!!.height
        /**
         * lastCurrentChildY: Just to know if we need to return true or false at the end of this
         * method.
         */
        val lastCurrentChildY = mCurrentChildY

//        mCurrentChildY = (int) ((dependency.getY() - anchorPointY) * collapsedY / (collapsedY - anchorPointY));
        mCurrentChildY = dependency.y.toInt() - child.measuredHeight
        if (mCurrentChildY <= 0) {
            mCurrentChildY = 0
        }
        child.y = mCurrentChildY.toFloat()
        return lastCurrentChildY == mCurrentChildY
    }

    /**
     * Find a direct child of CoordinatorLayout with [BottomSheetBehaviorPinned]
     * @param coordinatorLayout with app:layout_behavior= [BottomSheetBehaviorPinned]
     */
    private fun setupBottomSheetBehavior(coordinatorLayout: CoordinatorLayout) {
        (0 until coordinatorLayout.childCount)
                .map { coordinatorLayout.getChildAt(it) }
                .filterIsInstance<NestedScrollView>()
                .forEach {
                    try {
                        val temp = BottomSheetBehaviorPinned.from(it)
                        mBottomSheetBehaviorRef = WeakReference(temp)
                    } catch (e: IllegalArgumentException) {
                    }
                }
    }
}