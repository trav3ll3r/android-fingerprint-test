package au.com.trav3ll3r.fingerprint_test.tabnav

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import au.com.trav3ll3r.fingerprint_test.R

class QuickActionsFragment : Fragment() {
    companion object {
        fun newInstance(): QuickActionsFragment {
            return QuickActionsFragment()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_quick_actions, container, false)

        return view
    }
}