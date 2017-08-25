package au.com.trav3ll3r.playground.tabnav

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import au.com.trav3ll3r.playground.R
import org.jetbrains.anko.find

class QuickActionsFragment : BaseTabbedPageFragment() {
    companion object {
        fun newInstance(): QuickActionsFragment {
            return QuickActionsFragment()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_quick_actions, container, false) as TabScrollableContent
        return view
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindClicks()
    }

    override fun onResume() {
        super.onResume()

    }

    private fun bindClicks() {
        // ACTION CLICKED

        val buttonsGroup = (view as? ViewGroup)?.getChildAt(0) as? ViewGroup
        val buttonsCount = buttonsGroup?.childCount ?: 0
        (0 until buttonsCount).forEach {
            val v = buttonsGroup?.getChildAt(it) as? Button
            v?.setOnClickListener {
                Log.d("QuickActionsFragment", "Action Selected")
                Toast.makeText(context, "Quick Action ${v.text}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}