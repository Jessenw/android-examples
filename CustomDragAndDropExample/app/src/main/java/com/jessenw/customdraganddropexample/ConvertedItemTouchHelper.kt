package com.jessenw.customdraganddropexample

import android.animation.Animator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.*
import android.view.GestureDetector.SimpleOnGestureListener
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.R
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.*

open class ConvertedItemTouchHelper
/**
 * Creates an ItemTouchHelper that will work with the given Callback.
 *
 *
 * You can attach ItemTouchHelper to a RecyclerView via
 * [.attachToRecyclerView]. Upon attaching, it will add an item decoration,
 * an onItemTouchListener and a Child attach / detach listener to the RecyclerView.
 *
 * @param callback The Callback which controls the behavior of this touch helper.
 */(
    /**
     * Developer callback which controls the behavior of
     */
    var mCallback: ItemTouchHelper.Callback
) :
    ItemDecoration(), OnChildAttachStateChangeListener {
    /**
     * Views, whose state should be cleared after they are detached from RecyclerView.
     * This is necessary after swipe dismissing an item. We wait until animator finishes its job
     * to clean these views.
     */
    val mPendingCleanup: MutableList<View> = ArrayList()

    /**
     * Re-use array to calculate dx dy for a ViewHolder
     */
    private val mTmpPosition = FloatArray(2)

    /**
     * Currently selected view holder
     */
    var mSelected: ViewHolder? = null

    /**
     * The reference coordinates for the action start. For drag & drop, this is the time long
     * press is completed vs for swipe, this is the initial touch point.
     */
    var mInitialTouchX = 0f
    var mInitialTouchY = 0f

    /**
     * Set when ItemTouchHelper is assigned to a RecyclerView.
     */
    private var mSwipeEscapeVelocity = 0f

    /**
     * Set when ItemTouchHelper is assigned to a RecyclerView.
     */
    private var mMaxSwipeVelocity = 0f

    /**
     * The diff between the last event and initial touch.
     */
    var mDx = 0f
    var mDy = 0f

    /**
     * The coordinates of the selected view at the time it is selected. We record these values
     * when action starts so that we can consistently position it even if LayoutManager moves the
     * View.
     */
    private var mSelectedStartX = 0f
    private var mSelectedStartY = 0f

    /**
     * The pointer we are tracking.
     */
    var mActivePointerId: /* synthetic access */Int =
        ACTIVE_POINTER_ID_NONE

    /**
     * Current mode.
     */
    private var mActionState: Int = ACTION_STATE_IDLE

    /**
     * The direction flags obtained from unmasking
     * [Callback.getAbsoluteMovementFlags] for the current
     * action state.
     */
    var mSelectedFlags/* synthetic access */ = 0

    /**
     * When a View is dragged or swiped and needs to go back to where it was, we create a Recover
     * Animation and animate it to its location using this custom Animator, instead of using
     * framework Animators.
     * Using framework animators has the side effect of clashing with ItemAnimator, creating
     * jumpy UIs.
     */
    var mRecoverAnimations: MutableList<RecoverAnimation> = ArrayList()
    private var mSlop = 0
    var mRecyclerView: RecyclerView? = null

    /**
     * When user drags a view to the edge, we start scrolling the LayoutManager as long as View
     * is partially out of bounds.
     */
    /* synthetic access */ val mScrollRunnable: Runnable = object : Runnable {
        override fun run() {
            if (mSelected != null && scrollIfNecessary()) {
                if (mSelected != null) { //it might be lost during scrolling
                    moveIfNecessary(mSelected!!)
                }
                mRecyclerView!!.removeCallbacks(this)
                ViewCompat.postOnAnimation(mRecyclerView!!, this)
            }
        }
    }

    /**
     * Used for detecting fling swipe
     */
    var mVelocityTracker: VelocityTracker? = null

    //re-used list for selecting a swap target
    private var mSwapTargets: MutableList<ViewHolder>? = null

    //re used for for sorting swap targets
    private var mDistances: MutableList<Int>? = null

    /**
     * If drag & drop is supported, we use child drawing order to bring them to front.
     */
    private var mChildDrawingOrderCallback: ChildDrawingOrderCallback? = null

    /**
     * This keeps a reference to the child dragged by the user. Even after user stops dragging,
     * until view reaches its final position (end of recover animation), we keep a reference so
     * that it can be drawn above other children.
     */
    var mOverdrawChild: /* synthetic access */View? = null

    /**
     * We cache the position of the overdraw child to avoid recalculating it each time child
     * position callback is called. This value is invalidated whenever a child is attached or
     * detached.
     */
    var mOverdrawChildPosition/* synthetic access */ = -1

    /**
     * Used to detect long press.
     */
    var mGestureDetector: /* synthetic access */GestureDetectorCompat? = null

    /**
     * Callback for when long press occurs.
     */
    private var mItemTouchHelperGestureListener: ItemTouchHelperGestureListener? =
        null
    private val mOnItemTouchListener: OnItemTouchListener = object : OnItemTouchListener {
        override fun onInterceptTouchEvent(
            recyclerView: RecyclerView,
            event: MotionEvent
        ): Boolean {
            mGestureDetector!!.onTouchEvent(event)
            if (DEBUG) {
                Log.d(
                    TAG,
                    "intercept: x:" + event.x + ",y:" + event.y + ", " + event
                )
            }
            val action = event.actionMasked
            if (action == MotionEvent.ACTION_DOWN) {
                mActivePointerId = event.getPointerId(0)
                mInitialTouchX = event.x
                mInitialTouchY = event.y
                obtainVelocityTracker()
                if (mSelected == null) {
                    val animation = findAnimation(event)
                    if (animation != null) {
                        mInitialTouchX -= animation.mX
                        mInitialTouchY -= animation.mY
                        endRecoverAnimation(animation.mViewHolder, true)
                        if (mPendingCleanup.remove(animation.mViewHolder.itemView)) {
                            mCallback.clearView(mRecyclerView!!, animation.mViewHolder)
                        }
                        select(animation.mViewHolder, animation.mActionState)
                        updateDxDy(event, mSelectedFlags, 0)
                    }
                }
            } else if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
                mActivePointerId = ACTIVE_POINTER_ID_NONE
                select(null, ACTION_STATE_IDLE)
            } else if (mActivePointerId != ACTIVE_POINTER_ID_NONE) {
                // in a non scroll orientation, if distance change is above threshold, we
                // can select the item
                val index = event.findPointerIndex(mActivePointerId)
                if (DEBUG) {
                    Log.d(
                        TAG,
                        "pointer index $index"
                    )
                }
                if (index >= 0) {
                    checkSelectForSwipe(action, event, index)
                }
            }
            if (mVelocityTracker != null) {
                mVelocityTracker!!.addMovement(event)
            }
            return mSelected != null
        }

        override fun onTouchEvent(recyclerView: RecyclerView, event: MotionEvent) {
            mGestureDetector!!.onTouchEvent(event)
            if (DEBUG) {
                Log.d(
                    TAG,
                    "on touch: x:$mInitialTouchX,y:$mInitialTouchY, :$event"
                )
            }
            if (mVelocityTracker != null) {
                mVelocityTracker!!.addMovement(event)
            }
            if (mActivePointerId == ACTIVE_POINTER_ID_NONE) {
                return
            }
            val action = event.actionMasked
            val activePointerIndex = event.findPointerIndex(mActivePointerId)
            if (activePointerIndex >= 0) {
                checkSelectForSwipe(action, event, activePointerIndex)
            }
            val viewHolder = mSelected ?: return
            when (action) {
                MotionEvent.ACTION_MOVE -> {

                    // Find the index of the active pointer and fetch its position
                    if (activePointerIndex >= 0) {
                        updateDxDy(event, mSelectedFlags, activePointerIndex)
                        moveIfNecessary(viewHolder)
                        mRecyclerView!!.removeCallbacks(mScrollRunnable)
                        mScrollRunnable.run()
                        mRecyclerView!!.invalidate()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (mVelocityTracker != null) {
                        mVelocityTracker!!.clear()
                    }
                    select(null, ACTION_STATE_IDLE)
                    mActivePointerId = ACTIVE_POINTER_ID_NONE
                }
                MotionEvent.ACTION_UP -> {
                    select(null, ACTION_STATE_IDLE)
                    mActivePointerId = ACTIVE_POINTER_ID_NONE
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    val pointerIndex = event.actionIndex
                    val pointerId = event.getPointerId(pointerIndex)
                    if (pointerId == mActivePointerId) {
                        // This was our active pointer going up. Choose a new
                        // active pointer and adjust accordingly.
                        val newPointerIndex = if (pointerIndex == 0) 1 else 0
                        mActivePointerId = event.getPointerId(newPointerIndex)
                        updateDxDy(event, mSelectedFlags, pointerIndex)
                    }
                }
            }
        }

        override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            if (!disallowIntercept) {
                return
            }
            select(null, ACTION_STATE_IDLE)
        }
    }

    /**
     * Temporary rect instance that is used when we need to lookup Item decorations.
     */
    private var mTmpRect: Rect? = null

    /**
     * When user started to drag scroll. Reset when we don't scroll
     */
    private var mDragScrollStartTimeInMs: Long = 0

    /**
     * Attaches the ItemTouchHelper to the provided RecyclerView. If TouchHelper is already
     * attached to a RecyclerView, it will first detach from the previous one. You can call this
     * method with `null` to detach it from the current RecyclerView.
     *
     * @param recyclerView The RecyclerView instance to which you want to add this helper or
     * `null` if you want to remove ItemTouchHelper from the current
     * RecyclerView.
     */
    open fun attachToRecyclerView(recyclerView: RecyclerView?) {
        if (mRecyclerView === recyclerView) {
            return  // nothing to do
        }
        if (mRecyclerView != null) {
            destroyCallbacks()
        }
        mRecyclerView = recyclerView
        if (recyclerView != null) {
            val resources = recyclerView.resources
            mSwipeEscapeVelocity = resources
                .getDimension(R.dimen.item_touch_helper_swipe_escape_velocity)
            mMaxSwipeVelocity = resources
                .getDimension(R.dimen.item_touch_helper_swipe_escape_max_velocity)
            setupCallbacks()
        }
    }

    private fun setupCallbacks() {
        val vc = ViewConfiguration.get(mRecyclerView!!.context)
        mSlop = vc.scaledTouchSlop
        mRecyclerView!!.addItemDecoration(this)
        mRecyclerView!!.addOnItemTouchListener(mOnItemTouchListener)
        mRecyclerView!!.addOnChildAttachStateChangeListener(this)
        startGestureDetection()
    }

    private fun destroyCallbacks() {
        mRecyclerView!!.removeItemDecoration(this)
        mRecyclerView!!.removeOnItemTouchListener(mOnItemTouchListener)
        mRecyclerView!!.removeOnChildAttachStateChangeListener(this)
        // clean all attached
        val recoverAnimSize = mRecoverAnimations.size
        for (i in recoverAnimSize - 1 downTo 0) {
            val recoverAnimation = mRecoverAnimations[0]
            mCallback.clearView(mRecyclerView!!, recoverAnimation.mViewHolder)
        }
        mRecoverAnimations.clear()
        mOverdrawChild = null
        mOverdrawChildPosition = -1
        releaseVelocityTracker()
        stopGestureDetection()
    }

    private fun startGestureDetection() {
        mItemTouchHelperGestureListener = ItemTouchHelperGestureListener()
        mGestureDetector = GestureDetectorCompat(
            mRecyclerView!!.context,
            mItemTouchHelperGestureListener
        )
    }

    private fun stopGestureDetection() {
        if (mItemTouchHelperGestureListener != null) {
            mItemTouchHelperGestureListener!!.doNotReactToLongPress()
            mItemTouchHelperGestureListener = null
        }
        if (mGestureDetector != null) {
            mGestureDetector = null
        }
    }

    private fun getSelectedDxDy(outPosition: FloatArray) {
        if (mSelectedFlags and (LEFT or RIGHT) != 0) {
            outPosition[0] = mSelectedStartX + mDx - mSelected!!.itemView.left
        } else {
            outPosition[0] = mSelected!!.itemView.translationX
        }
        if (mSelectedFlags and (UP or DOWN) != 0) {
            outPosition[1] = mSelectedStartY + mDy - mSelected!!.itemView.top
        } else {
            outPosition[1] = mSelected!!.itemView.translationY
        }
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: State) {
        var dx = 0f
        var dy = 0f
        if (mSelected != null) {
            getSelectedDxDy(mTmpPosition)
            dx = mTmpPosition[0]
            dy = mTmpPosition[1]
        }
        mCallback.onDrawOver(c, parent, mSelected, mRecoverAnimations, mActionState, dx, dy)
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: State) {
        // we don't know if RV changed something so we should invalidate this index.
        mOverdrawChildPosition = -1
        var dx = 0f
        var dy = 0f
        if (mSelected != null) {
            getSelectedDxDy(mTmpPosition)
            dx = mTmpPosition[0]
            dy = mTmpPosition[1]
        }
        mCallback.onDraw(c, parent, mSelected, mRecoverAnimations, mActionState, dx, dy
        )
    }

    /**
     * Starts dragging or swiping the given View. Call with null if you want to clear it.
     *
     * @param selected    The ViewHolder to drag or swipe. Can be null if you want to cancel the
     * current action, but may not be null if actionState is ACTION_STATE_DRAG.
     * @param actionState The type of action
     */
    fun  /* synthetic access */select(selected: ViewHolder?, actionState: Int) {
        if (selected === mSelected && actionState == mActionState) {
            return
        }
        mDragScrollStartTimeInMs = Long.MIN_VALUE
        val prevActionState = mActionState
        // prevent duplicate animations
        endRecoverAnimation(selected, true)
        mActionState = actionState
        if (actionState == ACTION_STATE_DRAG) {
            requireNotNull(selected) { "Must pass a ViewHolder when dragging" }

            // we remove after animation is complete. this means we only elevate the last drag
            // child but that should perform good enough as it is very hard to start dragging a
            // new child before the previous one settles.
            mOverdrawChild = selected.itemView
            addChildDrawingOrderCallback()
        }
        val actionStateMask =
            ((1 shl DIRECTION_FLAG_COUNT + DIRECTION_FLAG_COUNT * actionState)
                    - 1)
        var preventLayout = false
        if (mSelected != null) {
            val prevSelected: ViewHolder = mSelected!!
            if (prevSelected.itemView.parent != null) {
                val swipeDir = 0
//                    if (prevActionState == ACTION_STATE_DRAG) 0
//                    else swipeIfNecessary(prevSelected)
                releaseVelocityTracker()
                // find where we should animate to
                val targetTranslateX: Float
                val targetTranslateY: Float
                val animationType: Int
                when (swipeDir) {
                    LEFT, RIGHT, START, END -> {
                        targetTranslateY = 0f
                        targetTranslateX = Math.signum(mDx) * mRecyclerView!!.width
                    }
                    UP, DOWN -> {
                        targetTranslateX = 0f
                        targetTranslateY = Math.signum(mDy) * mRecyclerView!!.height
                    }
                    else -> {
                        targetTranslateX = 0f
                        targetTranslateY = 0f
                    }
                }
                animationType =
                    if (prevActionState == ACTION_STATE_DRAG) {
                        ANIMATION_TYPE_DRAG
                    } else if (swipeDir > 0) {
                        ANIMATION_TYPE_SWIPE_SUCCESS
                    } else {
                        ANIMATION_TYPE_SWIPE_CANCEL
                    }
                getSelectedDxDy(mTmpPosition)
                val currentTranslateX = mTmpPosition[0]
                val currentTranslateY = mTmpPosition[1]
                val rv: RecoverAnimation =
                    object : RecoverAnimation(
                        prevSelected, animationType,
                        prevActionState, currentTranslateX, currentTranslateY,
                        targetTranslateX, targetTranslateY
                    ) {
                        override fun onAnimationEnd(animation: Animator?) {
                            super.onAnimationEnd(animation)
                            if (mOverridden) {
                                return
                            }
                            if (swipeDir <= 0) {
                                // this is a drag or failed swipe. recover immediately
                                mCallback.clearView(mRecyclerView!!, prevSelected)
                                // full cleanup will happen on onDrawOver
                            } else {
                                // wait until remove animation is complete.
                                mPendingCleanup.add(prevSelected.itemView)
                                mIsPendingCleanup = true
                                if (swipeDir > 0) {
                                    // Animation might be ended by other animators during a layout.
                                    // We defer callback to avoid editing adapter during a layout.
                                    postDispatchSwipe(this, swipeDir)
                                }
                            }
                            // removed from the list after it is drawn for the last time
                            if (mOverdrawChild === prevSelected.itemView) {
                                removeChildDrawingOrderCallbackIfNecessary(prevSelected.itemView)
                            }
                        }
                    }
                val duration = mCallback.getAnimationDuration(
                    mRecyclerView!!, animationType,
                    targetTranslateX - currentTranslateX, targetTranslateY - currentTranslateY
                )
                rv.setDuration(duration)
                mRecoverAnimations.add(rv)
                rv.start()
                preventLayout = true
            } else {
                removeChildDrawingOrderCallbackIfNecessary(prevSelected.itemView)
                mCallback.clearView(mRecyclerView!!, prevSelected)
            }
            mSelected = null
        }
        if (selected != null) {
            mSelectedFlags =
                (mCallback.getAbsoluteMovementFlags(mRecyclerView!!, selected) and actionStateMask
                        shr mActionState * DIRECTION_FLAG_COUNT)
            mSelectedStartX = selected.itemView.left.toFloat()
            mSelectedStartY = selected.itemView.top.toFloat()
            mSelected = selected
            if (actionState == ACTION_STATE_DRAG) {
                mSelected!!.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
        val rvParent = mRecyclerView!!.parent
        rvParent?.requestDisallowInterceptTouchEvent(mSelected != null)
        if (!preventLayout) {
            mRecyclerView!!.layoutManager!!.requestSimpleAnimationsInNextLayout()
        }
        mCallback.onSelectedChanged(mSelected, mActionState)
        mRecyclerView!!.invalidate()
    }

    fun postDispatchSwipe(
        anim: RecoverAnimation,
        swipeDir: Int
    ) {
        // wait until animations are complete.
        mRecyclerView!!.post(object : Runnable {
            override fun run() {
                if (mRecyclerView != null && mRecyclerView!!.isAttachedToWindow
                    && !anim.mOverridden
                    && anim.mViewHolder.adapterPosition != NO_POSITION
                ) {
                    val animator = mRecyclerView!!.itemAnimator
                    // if animator is running or we have other active recover animations, we try
                    // not to call onSwiped because DefaultItemAnimator is not good at merging
                    // animations. Instead, we wait and batch.
                    if ((animator == null || !animator.isRunning(null))
                        && !hasRunningRecoverAnim()
                    ) {
                        mCallback.onSwiped(anim.mViewHolder, swipeDir)
                    } else {
                        mRecyclerView!!.post(this)
                    }
                }
            }
        })
    }

    fun  /* synthetic access */hasRunningRecoverAnim(): Boolean {
        val size = mRecoverAnimations.size
        for (i in 0 until size) {
            if (!mRecoverAnimations[i].mEnded) {
                return true
            }
        }
        return false
    }

    /**
     * If user drags the view to the edge, trigger a scroll if necessary.
     */
    fun  /* synthetic access */scrollIfNecessary(): Boolean {
        if (mSelected == null) {
            mDragScrollStartTimeInMs = Long.MIN_VALUE
            return false
        }
        val now = System.currentTimeMillis()
        val scrollDuration = if (mDragScrollStartTimeInMs
            == Long.MIN_VALUE
        ) 0 else now - mDragScrollStartTimeInMs
        val lm = mRecyclerView!!.layoutManager
        if (mTmpRect == null) {
            mTmpRect = Rect()
        }
        var scrollX = 0
        var scrollY = 0
        lm!!.calculateItemDecorationsForChild(mSelected!!.itemView, mTmpRect!!)
        if (lm.canScrollHorizontally()) {
            val curX = (mSelectedStartX + mDx).toInt()
            val leftDiff = curX - mTmpRect!!.left - mRecyclerView!!.paddingLeft
            if (mDx < 0 && leftDiff < 0) {
                scrollX = leftDiff
            } else if (mDx > 0) {
                val rightDiff = (curX + mSelected!!.itemView.width + mTmpRect!!.right
                        - (mRecyclerView!!.width - mRecyclerView!!.paddingRight))
                if (rightDiff > 0) {
                    scrollX = rightDiff
                }
            }
        }
        if (lm.canScrollVertically()) {
            val curY = (mSelectedStartY + mDy).toInt()
            val topDiff = curY - mTmpRect!!.top - mRecyclerView!!.paddingTop
            if (mDy < 0 && topDiff < 0) {
                scrollY = topDiff
            } else if (mDy > 0) {
                val bottomDiff = (curY + mSelected!!.itemView.height + mTmpRect!!.bottom
                        - (mRecyclerView!!.height - mRecyclerView!!.paddingBottom))
                if (bottomDiff > 0) {
                    scrollY = bottomDiff
                }
            }
        }
        if (scrollX != 0) {
            scrollX = mCallback.interpolateOutOfBoundsScroll(
                mRecyclerView!!,
                mSelected!!.itemView.width, scrollX,
                mRecyclerView!!.width, scrollDuration
            )
        }
        if (scrollY != 0) {
            scrollY = mCallback.interpolateOutOfBoundsScroll(
                mRecyclerView!!,
                mSelected!!.itemView.height, scrollY,
                mRecyclerView!!.height, scrollDuration
            )
        }
        if (scrollX != 0 || scrollY != 0) {
            if (mDragScrollStartTimeInMs == Long.MIN_VALUE) {
                mDragScrollStartTimeInMs = now
            }
            mRecyclerView!!.scrollBy(scrollX, scrollY)
            return true
        }
        mDragScrollStartTimeInMs = Long.MIN_VALUE
        return false
    }

    private fun findSwapTargets(viewHolder: ViewHolder): List<ViewHolder> {
        if (mSwapTargets == null) {
            mSwapTargets = ArrayList()
            mDistances = ArrayList()
        } else {
            mSwapTargets!!.clear()
            mDistances!!.clear()
        }
        val margin = mCallback.boundingBoxMargin
        val left = Math.round(mSelectedStartX + mDx) - margin
        val top = Math.round(mSelectedStartY + mDy) - margin
        val right = left + viewHolder.itemView.width + 2 * margin
        val bottom = top + viewHolder.itemView.height + 2 * margin
        val centerX = (left + right) / 2
        val centerY = (top + bottom) / 2
        val lm = mRecyclerView!!.layoutManager
        val childCount = lm!!.childCount
        for (i in 0 until childCount) {
            val other = lm.getChildAt(i)
            if (other === viewHolder.itemView) {
                continue  //myself!
            }
            if (other!!.bottom < top || other.top > bottom || other.right < left || other.left > right) {
                continue
            }
            val otherVh = mRecyclerView!!.getChildViewHolder(other)
            if (mCallback.canDropOver(mRecyclerView!!, mSelected!!, otherVh)) {
                // find the index to add
                val dx = Math.abs(centerX - (other.left + other.right) / 2)
                val dy = Math.abs(centerY - (other.top + other.bottom) / 2)
                val dist = dx * dx + dy * dy
                var pos = 0
                val cnt = mSwapTargets!!.size
                for (j in 0 until cnt) {
                    if (dist > mDistances!![j]) {
                        pos++
                    } else {
                        break
                    }
                }
                mSwapTargets!!.add(pos, otherVh)
                mDistances!!.add(pos, dist)
            }
        }
        return mSwapTargets!!
    }

    /**
     * Checks if we should swap w/ another view holder.
     */
    fun moveIfNecessary(viewHolder: ViewHolder) {
        if (mRecyclerView!!.isLayoutRequested) {
            return
        }
        if (mActionState != ACTION_STATE_DRAG) {
            return
        }
        val threshold = mCallback.getMoveThreshold(viewHolder)
        val x = (mSelectedStartX + mDx).toInt()
        val y = (mSelectedStartY + mDy).toInt()
        if (Math.abs(y - viewHolder.itemView.top) < viewHolder.itemView.height * threshold
            && Math.abs(x - viewHolder.itemView.left)
            < viewHolder.itemView.width * threshold
        ) {
            return
        }
        val swapTargets = findSwapTargets(viewHolder)
        if (swapTargets.size == 0) {
            return
        }
        // may swap.
        val target = mCallback.chooseDropTarget(viewHolder, swapTargets, x, y)
        if (target == null) {
            mSwapTargets!!.clear()
            mDistances!!.clear()
            return
        }
        val toPosition = target.adapterPosition
        val fromPosition = viewHolder.adapterPosition
        if (mCallback.onMove(mRecyclerView!!, viewHolder, target)) {
            // keep target visible
            mCallback.onMoved(
                mRecyclerView!!, viewHolder, fromPosition,
                target, toPosition, x, y
            )
        }
    }

    override fun onChildViewAttachedToWindow(view: View) {}
    override fun onChildViewDetachedFromWindow(view: View) {
        removeChildDrawingOrderCallbackIfNecessary(view)
        val holder = mRecyclerView!!.getChildViewHolder(view) ?: return
        if (mSelected != null && holder === mSelected) {
            select(null, ACTION_STATE_IDLE)
        } else {
            endRecoverAnimation(holder, false) // this may push it into pending cleanup list.
            if (mPendingCleanup.remove(holder.itemView)) {
                mCallback.clearView(mRecyclerView!!, holder)
            }
        }
    }

    /**
     * Returns the animation type or 0 if cannot be found.
     */
    fun  /* synthetic access */endRecoverAnimation(viewHolder: ViewHolder?, override: Boolean) {
        val recoverAnimSize = mRecoverAnimations.size
        for (i in recoverAnimSize - 1 downTo 0) {
            val anim = mRecoverAnimations[i]
            if (anim.mViewHolder === viewHolder) {
                anim.mOverridden = anim.mOverridden or override
                if (!anim.mEnded) {
                    anim.cancel()
                }
                mRecoverAnimations.removeAt(i)
                return
            }
        }
    }

    override fun getItemOffsets(
        outRect: Rect, view: View, parent: RecyclerView,
        state: State
    ) {
        outRect.setEmpty()
    }

    fun  /* synthetic access */obtainVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker!!.recycle()
        }
        mVelocityTracker = VelocityTracker.obtain()
    }

    private fun releaseVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker!!.recycle()
            mVelocityTracker = null
        }
    }

    private fun findSwipedView(motionEvent: MotionEvent): ViewHolder? {
        val lm = mRecyclerView!!.layoutManager
        if (mActivePointerId == ACTIVE_POINTER_ID_NONE) {
            return null
        }
        val pointerIndex = motionEvent.findPointerIndex(mActivePointerId)
        val dx = motionEvent.getX(pointerIndex) - mInitialTouchX
        val dy = motionEvent.getY(pointerIndex) - mInitialTouchY
        val absDx = Math.abs(dx)
        val absDy = Math.abs(dy)
        if (absDx < mSlop && absDy < mSlop) {
            return null
        }
        if (absDx > absDy && lm!!.canScrollHorizontally()) {
            return null
        } else if (absDy > absDx && lm!!.canScrollVertically()) {
            return null
        }
        val child = findChildView(motionEvent) ?: return null
        return mRecyclerView!!.getChildViewHolder(child)
    }

    /**
     * Checks whether we should select a View for swiping.
     */
    fun  /* synthetic access */checkSelectForSwipe(
        action: Int,
        motionEvent: MotionEvent,
        pointerIndex: Int
    ) {
        if (mSelected != null || action != MotionEvent.ACTION_MOVE || mActionState == ACTION_STATE_DRAG || !mCallback.isItemViewSwipeEnabled
        ) {
            return
        }
        if (mRecyclerView!!.scrollState == SCROLL_STATE_DRAGGING) {
            return
        }
        val vh = findSwipedView(motionEvent) ?: return
        val movementFlags = mCallback.getAbsoluteMovementFlags(mRecyclerView, vh)
        val swipeFlags = (movementFlags and ACTION_MODE_SWIPE_MASK
                shr DIRECTION_FLAG_COUNT * ACTION_STATE_SWIPE)
        if (swipeFlags == 0) {
            return
        }

        // mDx and mDy are only set in allowed directions. We use custom x/y here instead of
        // updateDxDy to avoid swiping if user moves more in the other direction
        val x = motionEvent.getX(pointerIndex)
        val y = motionEvent.getY(pointerIndex)

        // Calculate the distance moved
        val dx = x - mInitialTouchX
        val dy = y - mInitialTouchY
        // swipe target is chose w/o applying flags so it does not really check if swiping in that
        // direction is allowed. This why here, we use mDx mDy to check slope value again.
        val absDx = Math.abs(dx)
        val absDy = Math.abs(dy)
        if (absDx < mSlop && absDy < mSlop) {
            return
        }
        if (absDx > absDy) {
            if (dx < 0 && swipeFlags and LEFT == 0) {
                return
            }
            if (dx > 0 && swipeFlags and RIGHT == 0) {
                return
            }
        } else {
            if (dy < 0 && swipeFlags and UP == 0) {
                return
            }
            if (dy > 0 && swipeFlags and DOWN == 0) {
                return
            }
        }
        mDy = 0f
        mDx = mDy
        mActivePointerId = motionEvent.getPointerId(0)
        select(vh, ACTION_STATE_SWIPE)
    }

    fun  /* synthetic access */findChildView(event: MotionEvent): View? {
        // first check elevated views, if none, then call RV
        val x = event.x
        val y = event.y
        if (mSelected != null) {
            val selectedView = mSelected!!.itemView
            if (hitTest(
                    selectedView,
                    x,
                    y,
                    mSelectedStartX + mDx,
                    mSelectedStartY + mDy
                )
            ) {
                return selectedView
            }
        }
        for (i in mRecoverAnimations.indices.reversed()) {
            val anim = mRecoverAnimations[i]
            val view = anim.mViewHolder.itemView
            if (hitTest(view, x, y, anim.mX, anim.mY)) {
                return view
            }
        }
        return mRecyclerView!!.findChildViewUnder(x, y)
    }

    /**
     * Starts dragging the provided ViewHolder. By default, ItemTouchHelper starts a drag when a
     * View is long pressed. You can disable that behavior by overriding
     * [Callback.isLongPressDragEnabled].
     *
     *
     * For this method to work:
     *
     *  * The provided ViewHolder must be a child of the RecyclerView to which this
     * ItemTouchHelper
     * is attached.
     *  * [Callback] must have dragging enabled.
     *  * There must be a previous touch event that was reported to the ItemTouchHelper
     * through RecyclerView's ItemTouchListener mechanism. As long as no other ItemTouchListener
     * grabs previous events, this should work as expected.
     *
     *
     * For example, if you would like to let your user to be able to drag an Item by touching one
     * of its descendants, you may implement it as follows:
     * <pre>
     * viewHolder.dragButton.setOnTouchListener(new View.OnTouchListener() {
     * public boolean onTouch(View v, MotionEvent event) {
     * if (MotionEvent.getActionMasked(event) == MotionEvent.ACTION_DOWN) {
     * mstartDrag(viewHolder);
     * }
     * return false;
     * }
     * });
    </pre> *
     *
     *
     *
     * @param viewHolder The ViewHolder to start dragging. It must be a direct child of
     * RecyclerView.
     * @see Callback.isItemViewSwipeEnabled
     */
    fun startDrag(viewHolder: ViewHolder) {
        if (!mCallback.hasDragFlag(mRecyclerView, viewHolder)) {
            Log.e(
                TAG,
                "Start drag has been called but dragging is not enabled"
            )
            return
        }
        if (viewHolder.itemView.parent !== mRecyclerView) {
            Log.e(
                TAG,
                "Start drag has been called with a view holder which is not a child of "
                        + "the RecyclerView which is controlled by this "
            )
            return
        }
        obtainVelocityTracker()
        mDy = 0f
        mDx = mDy
        select(viewHolder, ACTION_STATE_DRAG)
    }

    /**
     * Starts swiping the provided ViewHolder. By default, ItemTouchHelper starts swiping a View
     * when user swipes their finger (or mouse pointer) over the View. You can disable this
     * behavior
     * by overriding [Callback]
     *
     *
     * For this method to work:
     *
     *  * The provided ViewHolder must be a child of the RecyclerView to which this
     * ItemTouchHelper is attached.
     *  * [Callback] must have swiping enabled.
     *  * There must be a previous touch event that was reported to the ItemTouchHelper
     * through RecyclerView's ItemTouchListener mechanism. As long as no other ItemTouchListener
     * grabs previous events, this should work as expected.
     *
     *
     * For example, if you would like to let your user to be able to swipe an Item by touching one
     * of its descendants, you may implement it as follows:
     * <pre>
     * viewHolder.dragButton.setOnTouchListener(new View.OnTouchListener() {
     * public boolean onTouch(View v, MotionEvent event) {
     * if (MotionEvent.getActionMasked(event) == MotionEvent.ACTION_DOWN) {
     * mstartSwipe(viewHolder);
     * }
     * return false;
     * }
     * });
    </pre> *
     *
     * @param viewHolder The ViewHolder to start swiping. It must be a direct child of
     * RecyclerView.
     */
//    fun startSwipe(viewHolder: ViewHolder) {
//        if (!mCallback.hasSwipeFlag(mRecyclerView, viewHolder)) {
//            Log.e(
//                TAG,
//                "Start swipe has been called but swiping is not enabled"
//            )
//            return
//        }
//        if (viewHolder.itemView.parent !== mRecyclerView) {
//            Log.e(
//                TAG,
//                "Start swipe has been called with a view holder which is not a child of "
//                        + "the RecyclerView controlled by this "
//            )
//            return
//        }
//        obtainVelocityTracker()
//        mDy = 0f
//        mDx = mDy
//        select(viewHolder, ACTION_STATE_SWIPE)
//    }

    fun findAnimation(event: MotionEvent): RecoverAnimation? {
        if (mRecoverAnimations.isEmpty()) {
            return null
        }
        val target = findChildView(event)
        for (i in mRecoverAnimations.indices.reversed()) {
            val anim = mRecoverAnimations[i]
            if (anim.mViewHolder.itemView === target) {
                return anim
            }
        }
        return null
    }

    fun updateDxDy(ev: MotionEvent, directionFlags: Int, pointerIndex: Int) {
        val x = ev.getX(pointerIndex)
        val y = ev.getY(pointerIndex)

        // Calculate the distance moved
        mDx = x - mInitialTouchX
        mDy = y - mInitialTouchY
        if (directionFlags and LEFT == 0) {
            mDx = Math.max(0f, mDx)
        }
        if (directionFlags and RIGHT == 0) {
            mDx = Math.min(0f, mDx)
        }
        if (directionFlags and UP == 0) {
            mDy = Math.max(0f, mDy)
        }
        if (directionFlags and DOWN == 0) {
            mDy = Math.min(0f, mDy)
        }
    }

//    private fun swipeIfNecessary(viewHolder: ViewHolder): Int {
//        if (mActionState == ACTION_STATE_DRAG) {
//            return 0
//        }
//        val originalMovementFlags = mCallback.getMovementFlags(mRecyclerView!!, viewHolder)
//        val absoluteMovementFlags = mCallback.convertToAbsoluteDirection(
//            originalMovementFlags,
//            ViewCompat.getLayoutDirection(mRecyclerView!!)
//        )
//        val flags = (absoluteMovementFlags
//                and ACTION_MODE_SWIPE_MASK) shr ACTION_STATE_SWIPE * DIRECTION_FLAG_COUNT
//        if (flags == 0) {
//            return 0
//        }
//        val originalFlags = (originalMovementFlags
//                and ACTION_MODE_SWIPE_MASK) shr ACTION_STATE_SWIPE * DIRECTION_FLAG_COUNT
//        var swipeDir: Int
//        if (Math.abs(mDx) > Math.abs(mDy)) {
//            if (checkHorizontalSwipe(viewHolder, flags).also { swipeDir = it } > 0) {
//                // if swipe dir is not in original flags, it should be the relative direction
//                return if (originalFlags and swipeDir == 0) {
//                    // convert to relative
//                    Callback.convertToRelativeDirection(
//                        swipeDir,
//                        ViewCompat.getLayoutDirection(mRecyclerView!!)
//                    )
//                } else swipeDir
//            }
//            if (checkVerticalSwipe(viewHolder, flags).also { swipeDir = it } > 0) {
//                return swipeDir
//            }
//        } else {
//            if (checkVerticalSwipe(viewHolder, flags).also { swipeDir = it } > 0) {
//                return swipeDir
//            }
//            if (checkHorizontalSwipe(viewHolder, flags).also { swipeDir = it } > 0) {
//                // if swipe dir is not in original flags, it should be the relative direction
//                return if (originalFlags and swipeDir == 0) {
//                    // convert to relative
//                    Callback.convertToRelativeDirection(
//                        swipeDir,
//                        ViewCompat.getLayoutDirection(mRecyclerView!!)
//                    )
//                } else swipeDir
//            }
//        }
//        return 0
//    }

//    private fun checkHorizontalSwipe(viewHolder: ViewHolder, flags: Int): Int {
//        if (flags and (LEFT or RIGHT) != 0) {
//            val dirFlag: Int =
//                if (mDx > 0) RIGHT else LEFT
//            if (mVelocityTracker != null && mActivePointerId > -1) {
//                mVelocityTracker!!.computeCurrentVelocity(
//                    PIXELS_PER_SECOND,
//                    mCallback.getSwipeVelocityThreshold(mMaxSwipeVelocity)
//                )
//                val xVelocity = mVelocityTracker!!.getXVelocity(mActivePointerId)
//                val yVelocity = mVelocityTracker!!.getYVelocity(mActivePointerId)
//                val velDirFlag: Int =
//                    if (xVelocity > 0f) RIGHT else LEFT
//                val absXVelocity = Math.abs(xVelocity)
//                if (velDirFlag and flags != 0 && dirFlag == velDirFlag && absXVelocity >= mCallback.getSwipeEscapeVelocity(
//                        mSwipeEscapeVelocity
//                    ) && absXVelocity > Math.abs(yVelocity)
//                ) {
//                    return velDirFlag
//                }
//            }
//            val threshold = mRecyclerView!!.width * mCallback
//                .getSwipeThreshold(viewHolder)
//            if (flags and dirFlag != 0 && Math.abs(mDx) > threshold) {
//                return dirFlag
//            }
//        }
//        return 0
//    }

//    private fun checkVerticalSwipe(viewHolder: ViewHolder, flags: Int): Int {
//        if (flags and (UP or DOWN) != 0) {
//            val dirFlag: Int =
//                if (mDy > 0) DOWN else UP
//            if (mVelocityTracker != null && mActivePointerId > -1) {
//                mVelocityTracker!!.computeCurrentVelocity(
//                    PIXELS_PER_SECOND,
//                    mCallback.getSwipeVelocityThreshold(mMaxSwipeVelocity)
//                )
//                val xVelocity = mVelocityTracker!!.getXVelocity(mActivePointerId)
//                val yVelocity = mVelocityTracker!!.getYVelocity(mActivePointerId)
//                val velDirFlag: Int =
//                    if (yVelocity > 0f) DOWN else UP
//                val absYVelocity = Math.abs(yVelocity)
//                if (velDirFlag and flags != 0 && velDirFlag == dirFlag && absYVelocity >= mCallback.getSwipeEscapeVelocity(
//                        mSwipeEscapeVelocity
//                    ) && absYVelocity > Math.abs(xVelocity)
//                ) {
//                    return velDirFlag
//                }
//            }
//            val threshold = mRecyclerView!!.height * mCallback
//                .getSwipeThreshold(viewHolder)
//            if ((flags and dirFlag != 0) && (Math.abs(mDy)) > threshold) {
//                return dirFlag
//            }
//        }
//        return 0
//    }

    private fun addChildDrawingOrderCallback() {
        if (Build.VERSION.SDK_INT >= 21) {
            return  // we use elevation on Lollipop
        }
        if (mChildDrawingOrderCallback == null) {
            mChildDrawingOrderCallback =
                ChildDrawingOrderCallback { childCount, i ->
                    if (mOverdrawChild == null) {
                        return@ChildDrawingOrderCallback i
                    }
                    var childPosition = mOverdrawChildPosition
                    if (childPosition == -1) {
                        childPosition = mRecyclerView!!.indexOfChild(mOverdrawChild)
                        mOverdrawChildPosition = childPosition
                    }
                    if (i == childCount - 1) {
                        return@ChildDrawingOrderCallback childPosition
                    }
                    if (i < childPosition) i else i + 1
                }
        }
        mRecyclerView!!.setChildDrawingOrderCallback(mChildDrawingOrderCallback)
    }

    fun  /* synthetic access */removeChildDrawingOrderCallbackIfNecessary(view: View) {
        if (view === mOverdrawChild) {
            mOverdrawChild = null
            // only remove if we've added
            if (mChildDrawingOrderCallback != null) {
                mRecyclerView!!.setChildDrawingOrderCallback(null)
            }
        }
    }

    /**
     * An interface which can be implemented by LayoutManager for better integration with
     * [ItemTouchHelper].
     */
    interface ViewDropHandler {
        /**
         * Called by the [ItemTouchHelper] after a View is dropped over another View.
         *
         *
         * A LayoutManager should implement this interface to get ready for the upcoming move
         * operation.
         *
         *
         * For example, LinearLayoutManager sets up a "scrollToPositionWithOffset" calls so that
         * the View under drag will be used as an anchor View while calculating the next layout,
         * making layout stay consistent.
         *
         * @param view   The View which is being dragged. It is very likely that user is still
         * dragging this View so there might be other calls to
         * `prepareForDrop()` after this one.
         * @param target The target view which is being dropped on.
         * @param x      The `left` offset of the View that is being dragged. This value
         * includes the movement caused by the user.
         * @param y      The `top` offset of the View that is being dragged. This value
         * includes the movement caused by the user.
         */
        fun prepareForDrop(view: View, target: View, x: Int, y: Int)
    }

    private inner class ItemTouchHelperGestureListener internal constructor() :
        SimpleOnGestureListener() {
        /**
         * Whether to execute code in response to the the invoking of
         * [ItemTouchHelperGestureListener.onLongPress].
         *
         * It is necessary to control this here because
         * [GestureDetector.SimpleOnGestureListener] can only be set on a
         * [GestureDetector] in a GestureDetector's constructor, a GestureDetector will call
         * onLongPress if an [MotionEvent.ACTION_DOWN] event is not followed by another event
         * that would cancel it (like [MotionEvent.ACTION_UP] or
         * [MotionEvent.ACTION_CANCEL]), the long press responding to the long press event
         * needs to be cancellable to prevent unexpected behavior.
         *
         * @see .doNotReactToLongPress
         */
        private var mShouldReactToLongPress = true

        /**
         * Call to prevent executing code in response to
         * [ItemTouchHelperGestureListener.onLongPress] being called.
         */
        fun doNotReactToLongPress() {
            mShouldReactToLongPress = false
        }

        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            if (!mShouldReactToLongPress) {
                return
            }
            val child = findChildView(e)
            if (child != null) {
                val vh = mRecyclerView!!.getChildViewHolder(child)
                if (vh != null) {
                    if (!mCallback.hasDragFlag(mRecyclerView!!, vh)) {
                        return
                    }
                    val pointerId = e.getPointerId(0)
                    // Long press is deferred.
                    // Check w/ active pointer id to avoid selecting after motion
                    // event is canceled.
                    if (pointerId == mActivePointerId) {
                        val index = e.findPointerIndex(mActivePointerId)
                        val x = e.getX(index)
                        val y = e.getY(index)
                        mInitialTouchX = x
                        mInitialTouchY = y
                        mDy = 0f
                        mDx = mDy
                        if (DEBUG) {
                            Log.d(
                                TAG,
                                "onlong press: x:$mInitialTouchX,y:$mInitialTouchY"
                            )
                        }
                        if (mCallback.isLongPressDragEnabled) {
                            select(vh, ACTION_STATE_DRAG)
                        }
                    }
                }
            }
        }
    }

    open inner class RecoverAnimation constructor(
        val mViewHolder: ViewHolder,
        val mAnimationType: Int,
        val mActionState: Int,
        val mStartDx: Float,
        val mStartDy: Float,
        val mTargetX: Float,
        val mTargetY: Float
    ) : Animator.AnimatorListener {

        private val mValueAnimator: ValueAnimator
        var mIsPendingCleanup = false
        var mX = 0f
        var mY = 0f

        // if user starts touching a recovering view, we put it into interaction mode again,
        // instantly.
        var mOverridden = false
        var mEnded = false
        private var mFraction = 0f
        fun setDuration(duration: Long) {
            mValueAnimator.duration = duration
        }

        fun start() {
            mViewHolder.setIsRecyclable(false)
            mValueAnimator.start()
        }

        fun cancel() {
            mValueAnimator.cancel()
        }

        fun setFraction(fraction: Float) {
            mFraction = fraction
        }

        /**
         * We run updates on onDraw method but use the fraction from animator callback.
         * This way, we can sync translate x/y values w/ the animators to avoid one-off frames.
         */
        fun update() {
            mX = if (mStartDx == mTargetX) {
                mViewHolder.itemView.translationX
            } else {
                mStartDx + mFraction * (mTargetX - mStartDx)
            }
            mY = if (mStartDy == mTargetY) {
                mViewHolder.itemView.translationY
            } else {
                mStartDy + mFraction * (mTargetY - mStartDy)
            }
        }

        override fun onAnimationStart(animation: Animator) {}
        override fun onAnimationEnd(animation: Animator) {
            if (!mEnded) {
                mViewHolder.setIsRecyclable(true)
            }
            mEnded = true
        }

        override fun onAnimationCancel(animation: Animator) {
            setFraction(1f) //make sure we recover the view's state.
        }

        override fun onAnimationRepeat(animation: Animator) {}

        init {
            mValueAnimator = ValueAnimator.ofFloat(0f, 1f)
            mValueAnimator.addUpdateListener { animation -> setFraction(animation.animatedFraction) }
            mValueAnimator.setTarget(mViewHolder.itemView)
            mValueAnimator.addListener(this)
            setFraction(0f)
        }
    }

    companion object {
        /**
         * Up direction, used for swipe & drag control.
         */
        const val UP = 1

        /**
         * Down direction, used for swipe & drag control.
         */
        const val DOWN = 1 shl 1

        /**
         * Left direction, used for swipe & drag control.
         */
        const val LEFT = 1 shl 2

        /**
         * Right direction, used for swipe & drag control.
         */
        const val RIGHT = 1 shl 3
        // If you change these relative direction values, update Callback#convertToAbsoluteDirection,
        // Callback#convertToRelativeDirection.
        /**
         * Horizontal start direction. Resolved to LEFT or RIGHT depending on RecyclerView's layout
         * direction. Used for swipe & drag control.
         */
        val START: Int = LEFT shl 2

        /**
         * Horizontal end direction. Resolved to LEFT or RIGHT depending on RecyclerView's layout
         * direction. Used for swipe & drag control.
         */
        val END: Int = RIGHT shl 2

        /**
         * ItemTouchHelper is in idle state. At this state, either there is no related motion event by
         * the user or latest motion events have not yet triggered a swipe or drag.
         */
        const val ACTION_STATE_IDLE = 0

        /**
         * A View is currently being swiped.
         */
        const val ACTION_STATE_SWIPE = 1

        /**
         * A View is currently being dragged.
         */
        const val ACTION_STATE_DRAG = 2

        /**
         * Animation type for views which are swiped successfully.
         */
        const val ANIMATION_TYPE_SWIPE_SUCCESS = 1 shl 1

        /**
         * Animation type for views which are not completely swiped thus will animate back to their
         * original position.
         */
        const val ANIMATION_TYPE_SWIPE_CANCEL = 1 shl 2

        /**
         * Animation type for views that were dragged and now will animate to their final position.
         */
        const val ANIMATION_TYPE_DRAG = 1 shl 3
        private const val TAG = "ItemTouchHelper"
        private const val DEBUG = false
        private const val ACTIVE_POINTER_ID_NONE = -1
        const val DIRECTION_FLAG_COUNT = 8
        private const val ACTION_MODE_IDLE_MASK = (1 shl DIRECTION_FLAG_COUNT) - 1
        const val ACTION_MODE_SWIPE_MASK: Int = ACTION_MODE_IDLE_MASK shl DIRECTION_FLAG_COUNT
        const val ACTION_MODE_DRAG_MASK: Int = ACTION_MODE_SWIPE_MASK shl DIRECTION_FLAG_COUNT

        /**
         * The unit we are using to track velocity
         */
        private const val PIXELS_PER_SECOND = 1000
        private fun hitTest(child: View, x: Float, y: Float, left: Float, top: Float): Boolean {
            return x >= left && x <= left + child.width && y >= top && y <= top + child.height
        }
    }
}

