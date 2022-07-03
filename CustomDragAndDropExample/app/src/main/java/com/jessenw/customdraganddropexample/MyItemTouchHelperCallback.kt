package com.jessenw.customdraganddropexample

import android.graphics.Canvas
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class MyItemTouchHelperCallback(val recyclerView: RecyclerView): ItemTouchHelper.Callback() {

    private val cellHoverOffset = 16f

    private var mCurrentViewHolder: RecyclerView.ViewHolder? = null

    private var mTargetCurrent: RecyclerView.ViewHolder? = null
    private var mTargetNew: RecyclerView.ViewHolder? = null

    private var mGestureDetector: GestureDetectorCompat? = null
    private var mItemTouchHelperGestureListener: MyItemTouchHelperGestureListener? = null
    private val mOnItemTouchListener = object: RecyclerView.OnItemTouchListener {
        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
            mGestureDetector?.onTouchEvent(e)
            println("Biscuit: ${e.x}, ${e.y}")
            return true
        }

        override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
            return
        }

        override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            return
        }

    }

    init {
        mItemTouchHelperGestureListener = MyItemTouchHelperGestureListener()
        mGestureDetector = GestureDetectorCompat(recyclerView.context, mItemTouchHelperGestureListener)

        // Configure callbacks
        recyclerView.addOnItemTouchListener(mOnItemTouchListener)
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        return makeMovementFlags(dragFlags, 0) // Not supporting swipe
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        mCurrentViewHolder = viewHolder
        viewHolder.itemView.alpha = 0.5f

        // If we are hovering a new target, stage it
        if (mTargetCurrent != target) {
            mTargetNew = target
            if (mTargetCurrent != null) {
                // Reset the position of the current target, update and offset the new target
                mTargetCurrent!!.itemView.translationX -= cellHoverOffset
                mTargetCurrent = mTargetNew
                mTargetCurrent!!.itemView.translationX += cellHoverOffset
            } else {
                // If this is the first target we are hovering over
                mTargetCurrent = mTargetNew
                mTargetCurrent!!.itemView.translationX += cellHoverOffset
            }
        }
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
        MyItemTouchUIUtil.instance.onDraw(c, recyclerView, viewHolder.itemView, dX, dY,
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
        MyItemTouchUIUtil.instance.onDrawOver(c, recyclerView, viewHolder?.itemView, dX, dY,
            actionState, isCurrentlyActive)
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { /* Nop */ }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)

        // Perform clean-up
        mCurrentViewHolder?.itemView?.alpha = 1f
        mTargetCurrent?.let {
            it.itemView.translationX -= cellHoverOffset
        }
        mTargetCurrent = null
        mTargetNew = null
    }

    override fun chooseDropTarget(
        selected: RecyclerView.ViewHolder,
        dropTargets: MutableList<RecyclerView.ViewHolder>,
        curX: Int,
        curY: Int
    ): RecyclerView.ViewHolder {
        return dropTargets.first()
    }

    override fun getMoveThreshold(viewHolder: RecyclerView.ViewHolder): Float {
        return .1f
    }

    inner class MyItemTouchHelperGestureListener: GestureDetector.SimpleOnGestureListener() {

        // By default, SimpleOnGestureListener sets this to false, so no events are detected.
        // All gestures begin with an onDown so we need this to be true
        override fun onDown(e: MotionEvent?): Boolean = true

        override fun onLongPress(e: MotionEvent?) {
            e ?: return

            recyclerView.findChildViewUnder(e.x, e.y)?.let { child ->
                recyclerView.getChildViewHolder(child)?.let { viewHolder ->
                    val pointerId = e.getPointerId(0)
                }
            }
        }
    }
}
