package au.com.trav3ll3r.playground.bottomsheet

import android.os.Bundle
import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
import android.support.design.widget.AppBarLayout
import android.support.design.widget.CoordinatorLayout
import android.support.v4.app.FragmentManager
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import au.com.trav3ll3r.playground.R
import au.com.trav3ll3r.playground.tabnav.BaseTabbedPageFragment
import au.com.trav3ll3r.playground.tabnav.QuickActionsFragment
import au.com.trav3ll3r.playground.tabnav.QuickTasksFragment
import au.com.trav3ll3r.playground.tabnav.TabCustomView
import au.com.trav3ll3r.playground.tabnav.TabbedPagerAdapter
import au.com.trav3ll3r.playground.tabnav.TabbedPagerLayout
import org.jetbrains.anko.find
import org.jetbrains.anko.padding

class BottomSheetActivity : AppCompatActivity() {

    private lateinit var bottomSheetBehavior: BottomSheetBehaviorPinned<*>

    /**
     * Must be of type [android.support.v4.widget.NestedScrollView]
     * and decorated with [app:layout_behavior]="@string/BottomSheetBehavior"]
     */
    private lateinit var bottomSheet: View
    private lateinit var tabbedPagerLayout: TabbedPagerLayout
    private lateinit var toolbar: Toolbar
    private lateinit var appBarLayout: AppBarLayout
    private val DEFAULT_APP_TOOLBAR_ELEVATION: Float by lazy { appBarLayout.elevation }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bottom_sheet)

        appBarLayout = find(R.id.app_bar_layout)
        toolbar = find(R.id.toolbar)
        setSupportActionBar(toolbar)

        configureTabbedPagerLayout(supportFragmentManager)
        setUpBottomSheet()
    }

    private fun setUpBottomSheet() {
        /**
         * If we want to listen for states callback
         */
        val coordinatorLayout = find<CoordinatorLayout>(R.id.coordinator_layout)
        bottomSheet = coordinatorLayout.find(R.id.bottom_sheet)
        bottomSheetBehavior = BottomSheetBehaviorPinned.from(bottomSheet)
        bottomSheetBehavior.tabbedPagerLayout = tabbedPagerLayout
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehaviorPinned.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, @BottomSheetBehaviorPinned.State newState: Int) {
                tabbedPagerLayout.setOnClickListener(null)
                appBarLayout.elevation = DEFAULT_APP_TOOLBAR_ELEVATION

                when (newState) {
                    BottomSheetBehaviorPinned.STATE_COLLAPSED -> Log.d("bottomsheet-", "STATE_COLLAPSED")
                    BottomSheetBehaviorPinned.STATE_EXPANDED -> {
                        appBarLayout.elevation = 0f
                        Log.d("bottomsheet-", "STATE_EXPANDED")
                    }
                    BottomSheetBehaviorPinned.STATE_DRAGGING -> Log.d("bottomsheet-", "STATE_DRAGGING")
                    BottomSheetBehaviorPinned.STATE_ANCHOR_POINT -> Log.d("bottomsheet-", "STATE_ANCHOR_POINT")
                    BottomSheetBehaviorPinned.STATE_HIDDEN -> Log.d("bottomsheet-", "STATE_HIDDEN")
                    BottomSheetBehaviorPinned.STATE_SETTLING -> Log.d("bottomsheet-", "STATE_SETTLING")
                    else -> throw RuntimeException("bottomsheet-UNKNOWN_STATE")
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
        bottomSheet.viewTreeObserver.addOnGlobalLayoutListener(bottomSheetTreeObserver)
    }

    private fun updateTabbedPagerLayout() {
        tabbedPagerLayout.onParentLayoutChange(bottomSheet)
    }

    private val items: List<QuickItem> = listOf(
            QuickItem(QuickActionsFragment::class.java.simpleName, R.string.ok, android.R.drawable.ic_lock_lock),
            QuickItem(QuickTasksFragment::class.java.simpleName, R.string.cancel, android.R.drawable.arrow_up_float)
//            ,QuickItem(QuickActionsFragment::class.java.simpleName, android.R.string.yes, android.R.drawable.arrow_down_float)
    )

    /**
     * Configure Tabs using custom [TabbedPagerLayout]
     */
    private fun configureTabbedPagerLayout(childFragmentManager: FragmentManager) {
        val MAX_ITEMS = items.size
        tabbedPagerLayout = find(R.id.tabbed_pager)

        tabbedPagerLayout.setAdapter(TabbedPagerAdapter(object : TabbedPagerAdapter.DataSource {
            override fun getCount() = items.size

            override fun newPage(index: Int): BaseTabbedPageFragment = when (index) {
                0 -> QuickActionsFragment.newInstance()
                1 -> QuickTasksFragment.newInstance()
                else -> throw RuntimeException("${javaClass.simpleName} should not have more than $MAX_ITEMS tabs")
            }

            override fun getTabContent(index: Int): TabCustomView {
                val customView = TabCustomView(this@BottomSheetActivity)
                customView.setIcon(items[index].icon)
                customView.setText(items[index].label)
                customView.setTextColor(android.R.color.black)
                customView.setShowIcon(true)
                //customView.setTextColorStateList(R.color.quick_links_tab_text_colors)
                customView.padding = 0
                return customView
            }
        }, childFragmentManager))
        tabbedPagerLayout.selectPage(0)
    }

    private val bottomSheetTreeObserver: ViewTreeObserver.OnGlobalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        setBottomSheetHeight()
    }

    private fun setBottomSheetHeight() {
        bottomSheet.viewTreeObserver.removeOnGlobalLayoutListener(bottomSheetTreeObserver)
        updateTabbedPagerLayout()
    }
}

// MODEL
data class QuickItem(val clazz: String, @StringRes val label: Int, @DrawableRes val icon: Int)
