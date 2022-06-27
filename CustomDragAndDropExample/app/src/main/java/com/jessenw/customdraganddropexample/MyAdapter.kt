package com.jessenw.customdraganddropexample

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class MyAdapter(
    private var images: MutableList<Int> = mutableListOf(),
) : RecyclerView.Adapter<MyAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.my_list_item, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.image.setImageResource(images[position])
    }

    override fun getItemCount(): Int = images.size

    class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
        var image: ImageView
        init { image = view.findViewById(R.id.image_view) }
    }
}
