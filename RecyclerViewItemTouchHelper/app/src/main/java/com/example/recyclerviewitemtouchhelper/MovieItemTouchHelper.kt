package com.example.recyclerviewitemtouchhelper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.view.View
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchUIUtil
import androidx.recyclerview.widget.RecyclerView

class MovieItemTouchHelperCallback(private val movies: ArrayList<Movie>) : ItemTouchHelper.Callback() {

    /**
     * We don't need this be configurable... yet, so lets just hardcode flags for now
     */
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
//        val from = viewHolder.adapterPosition
//        val to = target.adapterPosition
//
//        // Move position
//        val item = movies.removeAt(from)
//        movies.add(to, item)
//        recyclerView.adapter?.notifyItemMoved(from, to)

//        return true
        return false
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        MovieItemTouchUIUtil.instance.onDraw(c, recyclerView, viewHolder.itemView, dX, dY,
            actionState, isCurrentlyActive)
    }

    override fun onChildDrawOver(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder?,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        MovieItemTouchUIUtil.instance.onDrawOver(c, recyclerView, viewHolder?.itemView, dX, dY,
            actionState, isCurrentlyActive)
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // Swipe actions not supported
    }
}

class MovieItemTouchUIUtil: ItemTouchUIUtil {

    companion object {
        var instance = MovieItemTouchUIUtil()
    }

    init {
        instance = this
    }

    override fun onDraw(
        c: Canvas?,
        recyclerView: RecyclerView?,
        view: View?,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (view != null && recyclerView != null) {
            if (Build.VERSION.SDK_INT >= 21 && isCurrentlyActive) {
                val newElevation = 1f + findMaxElevation(recyclerView, view)
                ViewCompat.setElevation(view, newElevation)
            }
            view.translationX = dX
            view.translationY = dY
        }
    }

    override fun onDrawOver(
        c: Canvas?,
        recyclerView: RecyclerView?,
        view: View?,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (c != null && view != null) {
            if (isCurrentlyActive) {
                recyclerView.measuredWidth
                val paint = Paint()
                paint.color = Color.RED
                val rect = Rect(0, view.top, 200, (view.top + 10))
                c.drawRect(rect, paint)
            }
        }
    }

    private fun findMaxElevation(recyclerView: RecyclerView, view: View): Float {
        val childCount = recyclerView.childCount
        var max = 0f
        for (i in 0 until childCount) {
            val child = recyclerView.getChildAt(i)
            val elevation = ViewCompat.getElevation(child)
            if (elevation > max) {
                max = elevation
            }
        }
        return max
    }

    override fun clearView(view: View?) {
        view?.let { view ->
            view.translationX = 0f
            view.translationY = 0f
        }
    }

    override fun onSelected(view: View?) {
    }
}
