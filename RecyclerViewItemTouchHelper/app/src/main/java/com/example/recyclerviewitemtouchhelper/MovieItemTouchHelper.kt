package com.example.recyclerviewitemtouchhelper

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class MovieItemTouchHelper(private val movies: ArrayList<Movie>) : ItemTouchHelper.Callback() {
    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        return makeMovementFlags(dragFlags, 0) // Not supporting swipe
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val from = viewHolder.adapterPosition
        val to = target.adapterPosition

        // Move position
        val item = movies.removeAt(from)
        movies.add(to, item)
        recyclerView.adapter?.notifyItemMoved(from, to)

        return true
    }

    // Not supporting swipe
    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { }
}
