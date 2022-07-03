package com.jessenw.customdraganddropexample

import android.animation.Animator
import android.animation.ValueAnimator
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class MyItemTouchHelper(private val callback: Callback) : ItemTouchHelper(callback) {

    companion object {
        const val ACTIVE_POINTER_ID_NONE = -1
    }

    private var recyclerView: RecyclerView? = null

    private var selected: RecyclerView.ViewHolder? = null

    private var initialTouchX: Float? = null
    private var initialTouchY: Float? = null

    /* The coordinates of the selected view at the time it is selected */
    private var selectedStartX = 0f
    private var selectedStartY = 0f

    /* The difference between the last and initial touch events */
    private var dX = 0f
    private var dY = 0f

    /* The pointer being tracked */
    var activePointerId: Int = ACTIVE_POINTER_ID_NONE

    private var dragScrollStartTime = Long.MIN_VALUE

    private val recoverAnimations = mutableListOf<RecoverAnimation>()

    private var gestureDetector: GestureDetectorCompat? = null

    private var velocityTracker: VelocityTracker? = null

    private val onItemTouchListener = object: RecyclerView.OnItemTouchListener {
        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
            gestureDetector?.onTouchEvent(e) // Detect gesture from touch event

            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    activePointerId = e.getPointerId(0)
                    initialTouchX = e.x
                    initialTouchY = e.y
                    obtainVelocityTracker()
                    findAnimation(e)?.let { anim ->
                        initialTouchX =- anim.x
                        initialTouchY =- anim.y
                    }
                }
            }

            return false
        }

        override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
            TODO("Not yet implemented")
        }

        override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            TODO("Not yet implemented")
        }
    }

    // Used to detect long press gestures
    private var itemTouchHelperGestureListener: ItemTouchHelperGestureListener? = null

    override fun attachToRecyclerView(recyclerView: RecyclerView?) {
        super.attachToRecyclerView(recyclerView)
        destroyCallbacks()
        this.recyclerView = recyclerView
        setupCallbacks()
    }

    private fun setupCallbacks() {
        recyclerView?.let { recyclerView ->
            recyclerView.addOnItemTouchListener(onItemTouchListener)
            recyclerView.addOnChildAttachStateChangeListener(this)

            // Start gesture detection
            itemTouchHelperGestureListener = ItemTouchHelperGestureListener()
            gestureDetector = GestureDetectorCompat(recyclerView.context, itemTouchHelperGestureListener)
        }
    }

    private fun destroyCallbacks() {
        recyclerView?.let { recyclerView ->
            recyclerView.removeOnItemTouchListener(onItemTouchListener)
            recyclerView.removeOnChildAttachStateChangeListener(this)
            // Clean up attached cells in recovery state
            recoverAnimations.forEach { anim ->
                callback.clearView(recyclerView, anim.viewHolder)
            }
            recoverAnimations.clear()

            // Stop gesture detection
            itemTouchHelperGestureListener?.doNotReactToLongPress()
            itemTouchHelperGestureListener = null
            gestureDetector = null
        }
    }

    private fun beginDrag(selected: RecyclerView.ViewHolder) {
        dragScrollStartTime = Long.MIN_VALUE
        endRecoverAnimation(selected, true)


    }

    private fun endRecoverAnimation(viewHolder: RecyclerView.ViewHolder, override: Boolean) {
        recoverAnimations.forEach { anim ->
            if (anim.viewHolder == viewHolder) {
                anim.overriden = anim.overriden || override
                if (!anim.ended) {
                    anim.cancel()
                }
                recoverAnimations.remove(anim)
                return
            }
        }
    }

    /* Helper functions */

    private fun findAnimation(e: MotionEvent): RecoverAnimation? {
        if (recoverAnimations.isEmpty()) return null

        val target = findChildView(e)
        recoverAnimations.forEach {
            if (it.viewHolder.itemView == target) return it
        }
        return null
    }

    private fun findChildView(e: MotionEvent): View? {
        val x = e.x
        val y = e.y

        // If there is currently a selected view and it
        // passes a hit test, this is the current child view
        selected?.let { viewHolder ->
            val selectedView = viewHolder.itemView
            val left = selectedStartX + dX
            val top = selectedStartY + dY
            if (hitTest(selectedView, x, y, left, top)) return selectedView
        }

        // TODO: Handle the case were a view is in the middle of a recovery animation

        return recyclerView?.findChildViewUnder(x, y)
    }

    private fun hitTest(child: View, x: Float, y: Float, left: Float, top: Float): Boolean {
        return x >= left
                && x <= left + child.width
                && y >= top
                && y <= top + child.height
    }

    private fun obtainVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker?.recycle()
        }
        velocityTracker = VelocityTracker.obtain()
    }

    private fun releaseVelocityTracker() {
        velocityTracker?.let {
            it.recycle()
            velocityTracker = null
        }
    }

    private inner class ItemTouchHelperGestureListener: GestureDetector.SimpleOnGestureListener() {

        private var shouldReactToLongPress = true

        override fun onDown(e: MotionEvent?): Boolean = true

        override fun onLongPress(e: MotionEvent?) {
            if (!shouldReactToLongPress) return

            e ?: return
            findChildView(e)?.let { child ->
                recyclerView?.getChildViewHolder(child)?.let { viewHolder ->
                    val pointerId = e.getPointerId(0)
                    if (pointerId == activePointerId) {
                        val index = e.findPointerIndex(activePointerId)
                        val pX = e.getX(index)
                        val pY = e.getY(index)
                        initialTouchX = pX
                        initialTouchY = pY
                        dX = 0f
                        dY = 0f
                        beginDrag(viewHolder)
                    }
                }
            }
        }

        fun doNotReactToLongPress() {
            shouldReactToLongPress = false
        }
    }

    private inner class RecoverAnimation(
        val viewHolder: RecyclerView.ViewHolder,
        val startDx: Float,
        val startDy: Float,
        val targetX: Float,
        val targetY: Float
    ) : Animator.AnimatorListener {

        private val valueAnimator: ValueAnimator = ValueAnimator.ofFloat(0f, 1f)
        var isPendingCleanup = false
        var x = 0f
        var y = 0f

        // If user starts touching a recovering view, we put it into interaction
        // mode again, instantly
        var overriden = false
        var ended = false
        private var fraction = 0f

        init {
            valueAnimator.addUpdateListener { animation -> setFraction(animation.animatedFraction) }
            valueAnimator.setTarget(viewHolder.itemView)
            valueAnimator.addListener(this)
            setFraction(0f)
        }

        fun setDuration(duration: Long) {
            valueAnimator.duration = duration
        }

        fun start() {
            viewHolder.setIsRecyclable(false)
            valueAnimator.start()
        }

        fun cancel() {
            valueAnimator.cancel()
        }

        fun setFraction(f: Float) {
            fraction = f
        }

        /**
         * We run updates on onDraw method but use the fraction from animator callback.
         * This way, we can sync translate x/y values w/ the animators to avoid one-off frames.
         */
        fun update() {
            x = if (startDx == targetX) viewHolder.itemView.translationX
            else startDx + fraction * (targetX - startDx)
            y = if (startDy == targetY) viewHolder.itemView.translationY
            else startDy + fraction * (targetY - startDy)
        }

        override fun onAnimationStart(animation: Animator) {}

        override fun onAnimationEnd(animation: Animator) {
            if (!ended) {
                viewHolder.setIsRecyclable(true)
            }
            ended = true
        }

        override fun onAnimationCancel(animation: Animator) {
            setFraction(1f) // Make sure we recover the view's state.
        }

        override fun onAnimationRepeat(animation: Animator) {}
    }
}
