package com.jessenw.customdraganddropexample

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.view.View
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.ItemTouchUIUtil
import androidx.recyclerview.widget.RecyclerView

class MyItemTouchUIUtil: ItemTouchUIUtil {

    val cursorHorizontalPadding = 8

    companion object {
        var instance = MyItemTouchUIUtil()
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
        if (c != null && view != null && recyclerView != null) {
            // Draw cursor, but only while dragging
            if (isCurrentlyActive) {
                val width = recyclerView.measuredWidth
                val paint = Paint()
                paint.color = Color.RED
                val rect = Rect(0, view.top, width, (view.top + 10))
                c.drawRect(rect, paint)
            }
        }

        // Debug
        if (c != null && view != null) {
            val originX = (dX + view.top).toInt()
            val originY = (dY + view.left).toInt()
            val paint = Paint()
            paint.color = Color.RED
            val rect = Rect(originX, originY, originX + 10, originY + 10)
            c.drawRect(rect, paint)
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
