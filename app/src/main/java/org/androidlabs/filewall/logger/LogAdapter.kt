package org.androidlabs.filewall.logger

import android.graphics.Color
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LogAdapter(
    private var logs: MutableList<String>
) : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    var textColor: Int = Color.WHITE
    var bgColor: Int = Color.BLACK

    class LogViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val tv = TextView(parent.context).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(16, 8, 16, 8)
        }
        return LogViewHolder(tv)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.textView.text = logs[position]
        holder.textView.setTextColor(textColor)
        holder.textView.setBackgroundColor(Color.TRANSPARENT)
    }

    override fun getItemCount(): Int = logs.size

    fun setLogs(newLogs: List<String>) {
        logs.clear()
        logs.addAll(newLogs)
        notifyDataSetChanged()
    }
}