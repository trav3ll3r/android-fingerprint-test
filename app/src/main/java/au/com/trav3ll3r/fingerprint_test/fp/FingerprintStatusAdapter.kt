package au.com.trav3ll3r.fingerprint_test.fp

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import au.com.trav3ll3r.fingerprint_test.R
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.find

// MODEL
data class FingerprintStatus(val label: String, val isSatisfied: Boolean = false, val reason: String = "")

// VIEW HOLDER
private class StatusViewHolder(val v: View) : RecyclerView.ViewHolder(v) {
    private val label: TextView by lazy { v.find<TextView>(R.id.status_label) }
    private val reason: TextView by lazy { v.find<TextView>(R.id.status_reason) }
    private val satisfied: View by lazy { v.find<View>(R.id.status_satisfied) }

    fun bind(spStatus: FingerprintStatus) {
        label.text = spStatus.label
        satisfied.backgroundColor = if (spStatus.isSatisfied) v.context.resources.getColor(R.color.success_color) else v.context.resources.getColor(R.color.error_color)
        reason.text = spStatus.reason
    }
}

// ADAPTER
class FingerprintStatusAdapter(private val items: MutableList<FingerprintStatus>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder? {
        return StatusViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_fingerprint_status, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val vh = holder as StatusViewHolder
        vh.bind(getItem(position))
    }

    override fun getItemCount(): Int {
        return items.size
    }

    private fun getItem(position: Int): FingerprintStatus {
        return items[position]
    }

    fun update(newItems: List<FingerprintStatus>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}