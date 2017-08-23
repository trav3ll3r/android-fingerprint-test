package au.com.trav3ll3r.playground.tabnav

import android.content.Context
import android.support.annotation.ColorRes
import android.support.design.widget.TabLayout
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewPager
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import au.com.trav3ll3r.playground.R
import au.com.trav3ll3r.playground.bottomsheet.BottomSheetBehaviorPinned
import org.jetbrains.anko.backgroundResource
import org.jetbrains.anko.find

class TabbedPagerLayout
@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAB_INDICATOR_WIDTH_COEFFICIENT = 0.3
    }

    private var attrs: AttributeSet? = null

    val currentPage: Int get() = viewPager.currentItem
    private val tabCount: Int
        get() {
            return viewPager.adapter.count
        }

    private val tabsBackground: ViewGroup by lazy { find<ViewGroup>(R.id.tabbed_background) }
    private val tabLayout: TabLayout by lazy { find<TabLayout>(R.id.tab_layout) }
    private val tabIndicatorBg: View by lazy { find<View>(R.id.tab_indicator_bg) }
    private val tabIndicator: View by lazy { find<View>(R.id.tab_indicator) }
    private val viewPager: ViewPager by lazy { find<ViewPager>(R.id.tabbed_menu_view_pager) }
    private val viewPagerWidth by lazy { viewPager.width }

    init {
        inflate(context, R.layout.tabbed_pager_layout, this)
        orientation = VERTICAL
        this.attrs = attrs

        // Change margins for buttons and tabs
        val tabPadding = getPadding(attrs, R.styleable.TabbedPagerLayout_tabs_padding, R.dimen.activity_horizontal_margin)
        var params = tabLayout.layoutParams as LayoutParams
        params.leftMargin = tabPadding
        params.rightMargin = tabPadding
        tabLayout.layoutParams = params

        params = tabIndicatorBg.layoutParams as LayoutParams
        params.leftMargin = tabPadding
        params.rightMargin = tabPadding
        tabIndicatorBg.layoutParams = params

        @ColorRes val tabUnderlineColor: Int
        @ColorRes val tabUnderlineSelectedColor: Int
        @ColorRes val tabsBackgroundColor: Int

        val a = context.theme.obtainStyledAttributes(
                attrs,
                R.styleable.TabbedPagerLayout,
                0, 0)

        try {
            tabUnderlineColor = a.getResourceId(R.styleable.TabbedPagerLayout_underlineColor, android.R.color.holo_red_dark)
            tabUnderlineSelectedColor = a.getResourceId(R.styleable.TabbedPagerLayout_underlineSelectedColor, android.R.color.holo_red_dark)
            tabsBackgroundColor = a.getResourceId(R.styleable.TabbedPagerLayout_tabs_background, android.R.color.holo_red_dark)
        } finally {
            a.recycle()
        }

        setTabUnderlineColor(tabUnderlineColor)
        setTabUnderlineSelectedColor(tabUnderlineSelectedColor)
        setTabsBackground(tabsBackgroundColor)

        initViewPager()
    }

    private fun initViewPager() {
        tabLayout.setupWithViewPager(viewPager)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab?) {}

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                val view = tab?.customView
                if (view is TabCustomView) {
                    view.setTabSelected(false)
                }
            }

            override fun onTabSelected(tab: TabLayout.Tab?) {
                val view = tab?.customView
                if (view is TabCustomView) {
                    view.setTabSelected(true)
                }
            }
        })

        // Change margins for content
        val contentPadding = getPadding(attrs, R.styleable.TabbedPagerLayout_content_padding, R.dimen.activity_horizontal_margin)
        val params = viewPager.layoutParams as LayoutParams
        params.marginStart = contentPadding
        params.marginEnd = contentPadding
        viewPager.layoutParams = params
    }

    private val pageListener = object : ViewPager.OnPageChangeListener {
        override fun onPageScrollStateChanged(state: Int) {}

        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            updateTabIndicator(position, positionOffset)
        }

        override fun onPageSelected(position: Int) {}
    }

    fun updateTabIndicator(position: Int, positionOffset: Float = 0f) {
        if (viewPager.adapter == null) {
            return
        }

        val tabWidth = viewPagerWidth / viewPager.adapter.count

        val radius = 1.0
        val exp = radius - Math.sqrt(Math.pow(radius, 2.0) - Math.pow(positionOffset.toDouble(), 2.0))
        val log = Math.sqrt(Math.pow(radius, 2.0) - Math.pow(positionOffset - radius, 2.0))

        val width = ((1 + TAB_INDICATOR_WIDTH_COEFFICIENT * (log - exp)) * tabWidth).toInt()
        val startOffset = ((position + exp) * tabWidth).toFloat()
        tabIndicator.layoutParams.width = width
        tabIndicator.x = startOffset
        tabIndicator.requestLayout()

        val frag = viewPager.adapter?.instantiateItem(viewPager, currentPage) as BaseTabbedPageFragment
        BottomSheetBehaviorPinned.INSTANCE.trackScrollingContent(frag.getScrollableContent())
    }

    private fun getPadding(attrs: AttributeSet?, index: Int, defaultValueId: Int): Int {
        var padding = context.resources.getDimensionPixelSize(defaultValueId)
        if (attrs != null) {
            val a = context.theme.obtainStyledAttributes(
                    attrs,
                    R.styleable.TabbedPagerLayout,
                    0, 0)

            try {
                padding = a.getDimensionPixelSize(index, padding)
            } finally {
                a.recycle()
            }
        }
        return padding
    }

    fun setTabUnderlineColor(@ColorRes color: Int) = tabIndicatorBg.setBackgroundColor(ContextCompat.getColor(context, color))

    fun setTabUnderlineSelectedColor(@ColorRes color: Int) = tabIndicator.setBackgroundColor(ContextCompat.getColor(context, color))

    fun setTabsBackground(@ColorRes color: Int) { tabsBackground.backgroundResource = color }

    fun setAdapter(adapter: TabbedPagerAdapter) {
        viewPager.adapter = adapter
        (0 until tabCount).forEach { i ->
            val tab = tabLayout.getTabAt(i)
            tab?.customView = adapter.dataSource.getTabContent(i)
        }
        viewPager.addOnPageChangeListener(pageListener)
    }

    fun markAsSheetDragEnabled(enabled: Boolean) {
        tabsBackground.elevation = if (enabled) 0f else 20f
    }

    fun setTabsClickable(canClick: Boolean) {
        val strip = tabLayout.getChildAt(0) as LinearLayout
        (0 until tabCount).forEach { i ->
            val tab = strip.getChildAt(i)
            tab.isClickable = canClick
        }
    }

    fun collapseImageArea(context: Context) {
        // TODO remove this magic number
        val height = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32.0f, context.resources.displayMetrics).toInt()
        val params = tabLayout.layoutParams as LayoutParams
        params.height = height
        params.topMargin = 0
        tabLayout.layoutParams = params
    }

    fun destroy() = viewPager.removeOnPageChangeListener(pageListener)

    fun selectPage(index: Int) {
        viewPager.currentItem = index
    }

    /**
     * Applies selected style to tab at [index]
     */
    fun applySelectedStyle(index: Int) {
        val tab = tabLayout.getTabAt(index)?.customView as TabCustomView?
        tab?.setTabSelected(true)
    }

    /**
     * Applies deselected style to tab at [index]
     */
    fun applyDeselectStyle(index: Int) {
        val tab = tabLayout.getTabAt(index)?.customView as TabCustomView?
        tab?.setTabSelected(false)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    fun onParentLayoutChange(parent: View) {
        viewPager.layoutParams.height = parent.measuredHeight - tabLayout.height - parent.resources.getDimensionPixelSize(R.dimen.app_toolbar_height)
        requestLayout()
    }
}

class TabbedPagerAdapter(val dataSource: DataSource, fragmentManager: FragmentManager)
    : FragmentStatePagerAdapter(fragmentManager) {

    interface DataSource {
        fun getCount(): Int
        fun newPage(index: Int): Fragment
        fun getTabContent(index: Int): TabCustomView
    }

    override fun getItem(position: Int): Fragment? = dataSource.newPage(position)

    override fun getCount(): Int = dataSource.getCount()
}