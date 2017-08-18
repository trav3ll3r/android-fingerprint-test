package au.com.trav3ll3r.fingerprint_test.bottomsheet

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.View
import android.view.ViewTreeObserver
import au.com.trav3ll3r.fingerprint_test.R
import org.jetbrains.anko.contentView
import org.jetbrains.anko.find

class BottomSheetActivity : AppCompatActivity() {

    private lateinit var mBottomSheetBehavior: AnchorBottomSheetBehavior<*>
    private lateinit var bottomSheet: View
    private lateinit var toolbar: Toolbar

    private val bottomSheetTreeObserver: ViewTreeObserver.OnGlobalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        setBottomSheetHeight()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bottom_sheet)

        toolbar = find(R.id.toolbar)
        setSupportActionBar(toolbar)

        bottomSheet = find(R.id.bottom_sheet)

        mBottomSheetBehavior = AnchorBottomSheetBehavior.from(bottomSheet)

        mBottomSheetBehavior.setBottomSheetCallback(object : AnchorBottomSheetBehavior.AnchorBottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, @AnchorBottomSheetBehavior.State newState: Int) {}
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        bottomSheet.viewTreeObserver.addOnGlobalLayoutListener(bottomSheetTreeObserver)
    }

    private fun setBottomSheetHeight() {
        bottomSheet.viewTreeObserver.removeOnGlobalLayoutListener(bottomSheetTreeObserver)
        val initialHeight = contentView?.measuredHeight ?: 0 // IF THIS IS NULL WE'RE FUBAR!!!
        val fullHeight = initialHeight - toolbar.height
        bottomSheet.layoutParams.height = fullHeight

        mBottomSheetBehavior.peekHeight = resources.getDimensionPixelSize(R.dimen.bottom_sheet_peek_height)
        mBottomSheetBehavior.isHideable = false
        mBottomSheetBehavior.state = AnchorBottomSheetBehavior.STATE_COLLAPSED

        bottomSheet.requestLayout()
    }
}