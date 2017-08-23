package au.com.trav3ll3r.playground.tabnav

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import au.com.trav3ll3r.playground.R

class QuickTasksFragment : Fragment() {
    companion object {
        fun newInstance(): QuickTasksFragment {
            return QuickTasksFragment()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_quick_tasks, container, false)

        return view
    }
}