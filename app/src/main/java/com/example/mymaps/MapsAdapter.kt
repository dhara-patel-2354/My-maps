package com.example.mymaps

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.mymaps.models.UserMap

private const val TAG = "MapsAdapter"

class MapsAdapter(
    val context: Context,
    val userMaps: List<UserMap>,
    val onClickListener: OnClickListener
) : RecyclerView.Adapter<MapsAdapter.ViewHolder>() {

    interface OnClickListener {
        fun onItemClick(position: Int)
        fun onItemLongClick(position: Int)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_usr_map, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val userMap = userMaps[position]
        holder.itemView.setOnClickListener {
            Log.i(TAG, "Tapped on position $position")
            onClickListener.onItemClick(position)
        }
        holder.itemView.setOnLongClickListener {
            Log.i(TAG, "Long tapped on position $position")
            onClickListener.onItemLongClick(position)
            true
        }
        val textViewTitle = holder.itemView.findViewById<TextView>(R.id.tvMapTitle)
        textViewTitle.text = userMap.title
    }

    override fun getItemCount() = userMaps.size
}
