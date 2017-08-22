package au.com.trav3ll3r.fingerprint_test.tabnav

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
import android.widget.LinearLayout
import au.com.trav3ll3r.fingerprint_test.R
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

    private val tabLayout: TabLayout by lazy { find<TabLayout>(R.id.tab_layout) }
    private val tabIndicatorBg: View by lazy { find<View>(R.id.tab_indicator_bg) }
    private val tabIndicator: View by lazy { find<View>(R.id.tab_indicator) }
    private lateinit var viewPager: ViewPager

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

        val tabUnderlineColor: Int
        val tabUnderlineSelectedColor: Int

        val a = context.theme.obtainStyledAttributes(
                attrs,
                R.styleable.TabbedPagerLayout,
                0, 0)

        try {
            tabUnderlineColor = a.getResourceId(R.styleable.TabbedPagerLayout_underlineColor, android.R.color.white)
            tabUnderlineSelectedColor = a.getResourceId(R.styleable.TabbedPagerLayout_underlineSelectedColor, R.color.colorPrimaryDark)
        } finally {
            a.recycle()
        }

        setTabUnderlineColor(tabUnderlineColor)
        setTabUnderlineSelectedColor(tabUnderlineSelectedColor)
    }

    fun setViewPager(viewPager: ViewPager) {
        this.viewPager = viewPager
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
        params.leftMargin = contentPadding
        params.rightMargin = contentPadding
        viewPager.layoutParams = params
    }

    private val pageListener = object : ViewPager.OnPageChangeListener {
        override fun onPageScrollStateChanged(state: Int) {}

        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            if (viewPager.adapter == null) {
                return
            }

            val tabWidth = viewPager.width / viewPager.adapter.count

            val radius = 1.0
            val exp = radius - Math.sqrt(Math.pow(radius, 2.0) - Math.pow(positionOffset.toDouble(), 2.0))
            val log = Math.sqrt(Math.pow(radius, 2.0) - Math.pow(positionOffset - radius, 2.0))

            val width = ((1 + TAB_INDICATOR_WIDTH_COEFFICIENT * (log - exp)) * tabWidth).toInt()
            tabIndicator.layoutParams.width = width
            tabIndicator.requestLayout()

            val padding = ((position + exp) * tabWidth).toInt()
            tabIndicatorBg.setPadding(padding, 0, 0, 0)
            tabIndicatorBg.requestLayout()
        }

        override fun onPageSelected(position: Int) {}
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

    fun setTabUnderlineColor(@ColorRes color: Int) =
            tabIndicatorBg.setBackgroundColor(ContextCompat.getColor(context, color))

    fun setTabUnderlineSelectedColor(@ColorRes color: Int) =
            tabIndicator.setBackgroundColor(ContextCompat.getColor(context, color))

    fun setAdapter(adapter: TabbedPagerAdapter) {
        viewPager.adapter = adapter
        (0 until tabCount).forEach { i ->
            val tab = tabLayout.getTabAt(i)
            tab?.customView = adapter.dataSource.getTabContent(i)
        }
        viewPager.addOnPageChangeListener(pageListener)
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