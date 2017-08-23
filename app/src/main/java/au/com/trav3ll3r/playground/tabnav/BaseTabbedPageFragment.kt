package au.com.trav3ll3r.playground.tabnav

import android.support.v4.app.Fragment
import android.support.v4.widget.NestedScrollView

abstract class BaseTabbedPageFragment : Fragment() {
    fun getScrollableContent(): NestedScrollView {
        return view as? NestedScrollView ?: throw RuntimeException("Expected NestedScrollView as fragment's layout root element!")
    }
}