package com.jessenw.customdraganddropexample

import android.graphics.Canvas
import android.view.animation.Interpolator
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchUIUtil
import androidx.recyclerview.widget.RecyclerView

abstract class ConvertedCallback {
    private var mCachedMaxScrollSpeed = -1

    /**
     * Should return a composite flag which defines the enabled move directions in each state
     * (idle, swiping, dragging).
     *
     *
     * Instead of composing this flag manually, you can use [.makeMovementFlags]
     * or [.makeFlag].
     *
     *
     * This flag is composed of 3 sets of 8 bits, where first 8 bits are for IDLE state, next
     * 8 bits are for SWIPE state and third 8 bits are for DRAG state.
     * Each 8 bit sections can be constructed by simply OR'ing direction flags defined in
     * [ItemTouchHelper].
     *
     *
     * For example, if you want it to allow swiping LEFT and RIGHT but only allow starting to
     * swipe by swiping RIGHT, you can return:
     * <pre>
     * makeFlag(ACTION_STATE_IDLE, RIGHT) | makeFlag(ACTION_STATE_SWIPE, LEFT | RIGHT);
    </pre> *
     * This means, allow right movement while IDLE and allow right and left movement while
     * swiping.
     *
     * @param recyclerView The RecyclerView to which ItemTouchHelper is attached.
     * @param viewHolder   The ViewHolder for which the movement information is necessary.
     * @return flags specifying which movements are allowed on this ViewHolder.
     * @see .makeMovementFlags
     * @see .makeFlag
     */
    abstract fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int

    /**
     * Converts a given set of flags to absolution direction which means [.START] and
     * [.END] are replaced with [.LEFT] and [.RIGHT] depending on the layout
     * direction.
     *
     * @param flags           The flag value that include any number of movement flags.
     * @param layoutDirection The layout direction of the RecyclerView.
     * @return Updated flags which includes only absolute direction values.
     */
    private fun convertToAbsoluteDirection(flags: Int, layoutDirection: Int): Int {
        var flags = flags
        val masked = flags and RELATIVE_DIR_FLAGS
        if (masked == 0) {
            return flags // does not have any relative flags, good.
        }
        flags = flags and masked.inv() //remove start / end
        if (layoutDirection == ViewCompat.LAYOUT_DIRECTION_LTR) {
            // no change. just OR with 2 bits shifted mask and return
            flags =
                flags or (masked shr 2) // START is 2 bits after LEFT, END is 2 bits after RIGHT.
            return flags
        } else {
            // add START flag as RIGHT
            flags = flags or (masked shr 1 and RELATIVE_DIR_FLAGS.inv())
            // first clean start bit then add END flag as LEFT
            flags = flags or (masked shr 1 and RELATIVE_DIR_FLAGS shr 2)
        }
        return flags
    }

    private fun getAbsoluteMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val flags = getMovementFlags(recyclerView, viewHolder)
        return convertToAbsoluteDirection(flags, ViewCompat.getLayoutDirection(recyclerView))
    }

    fun hasDragFlag(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Boolean {
        val flags = this.getAbsoluteMovementFlags(recyclerView, viewHolder)
        return flags and ConvertedItemTouchHelper.ACTION_MODE_DRAG_MASK != 0
    }

    fun hasSwipeFlag(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Boolean {
        val flags = this.getAbsoluteMovementFlags(recyclerView, viewHolder)
        return flags and ConvertedItemTouchHelper.ACTION_MODE_SWIPE_MASK != 0
    }

    /**
     * Return true if the current ViewHolder can be dropped over the the target ViewHolder.
     *
     *
     * This method is used when selecting drop target for the dragged View. After Views are
     * eliminated either via bounds check or via this method, resulting set of views will be
     * passed to [.chooseDropTarget].
     *
     *
     * Default implementation returns true.
     *
     * @param recyclerView The RecyclerView to which ItemTouchHelper is attached to.
     * @param current      The ViewHolder that user is dragging.
     * @param target       The ViewHolder which is below the dragged ViewHolder.
     * @return True if the dragged ViewHolder can be replaced with the target ViewHolder, false
     * otherwise.
     */
    fun canDropOver(
        recyclerView: RecyclerView, current: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return true
    }

    /**
     * Called when ItemTouchHelper wants to move the dragged item from its old position to
     * the new position.
     *
     *
     * If this method returns true, ItemTouchHelper assumes `viewHolder` has been moved
     * to the adapter position of `target` ViewHolder
     * ([ ViewHolder#getAdapterPosition()][ViewHolder.getAdapterPosition]).
     *
     *
     * If you don't support drag & drop, this method will never be called.
     *
     * @param recyclerView The RecyclerView to which ItemTouchHelper is attached to.
     * @param viewHolder   The ViewHolder which is being dragged by the user.
     * @param target       The ViewHolder over which the currently active item is being
     * dragged.
     * @return True if the `viewHolder` has been moved to the adapter position of
     * `target`.
     * @see .onMoved
     */
    abstract fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
    ): Boolean

    /**
     * Returns whether ItemTouchHelper should start a drag and drop operation if an item is
     * long pressed.
     *
     *
     * Default value returns true but you may want to disable this if you want to start
     * dragging on a custom view touch using [.startDrag].
     *
     * @return True if ItemTouchHelper should start dragging an item when it is long pressed,
     * false otherwise. Default value is `true`.
     * @see .startDrag
     */
    val isLongPressDragEnabled: Boolean
        get() = true

    /**
     * Returns whether ItemTouchHelper should start a swipe operation if a pointer is swiped
     * over the View.
     *
     *
     * Default value returns true but you may want to disable this if you want to start
     * swiping on a custom view touch using [.startSwipe].
     *
     * @return True if ItemTouchHelper should start swiping an item when user swipes a pointer
     * over the View, false otherwise. Default value is `true`.
     * @see .startSwipe
     */
    val isItemViewSwipeEnabled: Boolean
        get() = true

    /**
     * When finding views under a dragged view, by default, ItemTouchHelper searches for views
     * that overlap with the dragged View. By overriding this method, you can extend or shrink
     * the search box.
     *
     * @return The extra margin to be added to the hit box of the dragged View.
     */
    val boundingBoxMargin: Int
        get() = 0

    /**
     * Returns the fraction that the user should move the View to be considered as swiped.
     * The fraction is calculated with respect to RecyclerView's bounds.
     *
     *
     * Default value is .5f, which means, to swipe a View, user must move the View at least
     * half of RecyclerView's width or height, depending on the swipe direction.
     *
     * @param viewHolder The ViewHolder that is being dragged.
     * @return A float value that denotes the fraction of the View size. Default value
     * is .5f .
     */
    fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
        return .5f
    }

    /**
     * Returns the fraction that the user should move the View to be considered as it is
     * dragged. After a view is moved this amount, ItemTouchHelper starts checking for Views
     * below it for a possible drop.
     *
     * @param viewHolder The ViewHolder that is being dragged.
     * @return A float value that denotes the fraction of the View size. Default value is
     * .5f .
     */
    open fun getMoveThreshold(viewHolder: RecyclerView.ViewHolder): Float {
        return .5f
    }

    /**
     * Defines the minimum velocity which will be considered as a swipe action by the user.
     *
     *
     * You can increase this value to make it harder to swipe or decrease it to make it easier.
     * Keep in mind that ItemTouchHelper also checks the perpendicular velocity and makes sure
     * current direction velocity is larger then the perpendicular one. Otherwise, user's
     * movement is ambiguous. You can change the threshold by overriding
     * [.getSwipeVelocityThreshold].
     *
     *
     * The velocity is calculated in pixels per second.
     *
     *
     * The default framework value is passed as a parameter so that you can modify it with a
     * multiplier.
     *
     * @param defaultValue The default value (in pixels per second) used by the
     * ItemTouchHelper.
     * @return The minimum swipe velocity. The default implementation returns the
     * `defaultValue` parameter.
     * @see .getSwipeVelocityThreshold
     * @see .getSwipeThreshold
     */
    fun getSwipeEscapeVelocity(defaultValue: Float): Float {
        return defaultValue
    }

    /**
     * Defines the maximum velocity ItemTouchHelper will ever calculate for pointer movements.
     *
     *
     * To consider a movement as swipe, ItemTouchHelper requires it to be larger than the
     * perpendicular movement. If both directions reach to the max threshold, none of them will
     * be considered as a swipe because it is usually an indication that user rather tried to
     * scroll then swipe.
     *
     *
     * The velocity is calculated in pixels per second.
     *
     *
     * You can customize this behavior by changing this method. If you increase the value, it
     * will be easier for the user to swipe diagonally and if you decrease the value, user will
     * need to make a rather straight finger movement to trigger a swipe.
     *
     * @param defaultValue The default value(in pixels per second) used by the ItemTouchHelper.
     * @return The velocity cap for pointer movements. The default implementation returns the
     * `defaultValue` parameter.
     * @see .getSwipeEscapeVelocity
     */
    fun getSwipeVelocityThreshold(defaultValue: Float): Float {
        return defaultValue
    }

    /**
     * Called by ItemTouchHelper to select a drop target from the list of ViewHolders that
     * are under the dragged View.
     *
     *
     * Default implementation filters the View with which dragged item have changed position
     * in the drag direction. For instance, if the view is dragged UP, it compares the
     * `view.getTop()` of the two views before and after drag started. If that value
     * is different, the target view passes the filter.
     *
     *
     * Among these Views which pass the test, the one closest to the dragged view is chosen.
     *
     *
     * This method is called on the main thread every time user moves the View. If you want to
     * override it, make sure it does not do any expensive operations.
     *
     * @param selected    The ViewHolder being dragged by the user.
     * @param dropTargets The list of ViewHolder that are under the dragged View and
     * candidate as a drop.
     * @param curX        The updated left value of the dragged View after drag translations
     * are applied. This value does not include margins added by
     * [RecyclerView.ItemDecoration]s.
     * @param curY        The updated top value of the dragged View after drag translations
     * are applied. This value does not include margins added by
     * [RecyclerView.ItemDecoration]s.
     * @return A ViewHolder to whose position the dragged ViewHolder should be
     * moved to.
     */
    open fun chooseDropTarget(
        selected: RecyclerView.ViewHolder,
        dropTargets: List<RecyclerView.ViewHolder>, curX: Int, curY: Int
    ): RecyclerView.ViewHolder? {
        val right = curX + selected.itemView.width
        val bottom = curY + selected.itemView.height
        var winner: RecyclerView.ViewHolder? = null
        var winnerScore = -1
        val dx = curX - selected.itemView.left
        val dy = curY - selected.itemView.top
        val targetsSize = dropTargets.size
        for (i in 0 until targetsSize) {
            val target = dropTargets[i]
            if (dx > 0) {
                val diff = target.itemView.right - right
                if (diff < 0 && target.itemView.right > selected.itemView.right) {
                    val score = Math.abs(diff)
                    if (score > winnerScore) {
                        winnerScore = score
                        winner = target
                    }
                }
            }
            if (dx < 0) {
                val diff = target.itemView.left - curX
                if (diff > 0 && target.itemView.left < selected.itemView.left) {
                    val score = Math.abs(diff)
                    if (score > winnerScore) {
                        winnerScore = score
                        winner = target
                    }
                }
            }
            if (dy < 0) {
                val diff = target.itemView.top - curY
                if (diff > 0 && target.itemView.top < selected.itemView.top) {
                    val score = Math.abs(diff)
                    if (score > winnerScore) {
                        winnerScore = score
                        winner = target
                    }
                }
            }
            if (dy > 0) {
                val diff = target.itemView.bottom - bottom
                if (diff < 0 && target.itemView.bottom > selected.itemView.bottom) {
                    val score = Math.abs(diff)
                    if (score > winnerScore) {
                        winnerScore = score
                        winner = target
                    }
                }
            }
        }
        return winner
    }

    /**
     * Called when a ViewHolder is swiped by the user.
     *
     *
     * If you are returning relative directions ([.START] , [.END]) from the
     * [.getMovementFlags] method, this method
     * will also use relative directions. Otherwise, it will use absolute directions.
     *
     *
     * If you don't support swiping, this method will never be called.
     *
     *
     * ItemTouchHelper will keep a reference to the View until it is detached from
     * RecyclerView.
     * As soon as it is detached, ItemTouchHelper will call
     * [.clearView].
     *
     * @param viewHolder The ViewHolder which has been swiped by the user.
     * @param direction  The direction to which the ViewHolder is swiped. It is one of
     * [.UP], [.DOWN],
     * [.LEFT] or [.RIGHT]. If your
     * [.getMovementFlags]
     * method
     * returned relative flags instead of [.LEFT] / [.RIGHT];
     * `direction` will be relative as well. ([.START] or [                   ][.END]).
     */
    abstract fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int)

    /**
     * Called when the ViewHolder swiped or dragged by the ItemTouchHelper is changed.
     *
     *
     * If you override this method, you should call super.
     *
     * @param viewHolder  The new ViewHolder that is being swiped or dragged. Might be null if
     * it is cleared.
     * @param actionState One of [ItemTouchHelper.ACTION_STATE_IDLE],
     * [ItemTouchHelper.ACTION_STATE_SWIPE] or
     * [ItemTouchHelper.ACTION_STATE_DRAG].
     * @see .clearView
     */
    fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        if (viewHolder != null) {
            ItemTouchUIUtilImpl.INSTANCE.onSelected(viewHolder.itemView)
        }
    }

    private fun getMaxDragScroll(recyclerView: RecyclerView): Int {
        if (mCachedMaxScrollSpeed == -1) {
            mCachedMaxScrollSpeed = recyclerView.resources.getDimensionPixelSize(
                R.dimen.item_touch_helper_max_drag_scroll_per_frame
            )
        }
        return mCachedMaxScrollSpeed
    }

    /**
     * Called when [.onMove] returns true.
     *
     *
     * ItemTouchHelper does not create an extra Bitmap or View while dragging, instead, it
     * modifies the existing View. Because of this reason, it is important that the View is
     * still part of the layout after it is moved. This may not work as intended when swapped
     * Views are close to RecyclerView bounds or there are gaps between them (e.g. other Views
     * which were not eligible for dropping over).
     *
     *
     * This method is responsible to give necessary hint to the LayoutManager so that it will
     * keep the View in visible area. For example, for LinearLayoutManager, this is as simple
     * as calling [LinearLayoutManager.scrollToPositionWithOffset].
     *
     * Default implementation calls [RecyclerView.scrollToPosition] if the View's
     * new position is likely to be out of bounds.
     *
     *
     * It is important to ensure the ViewHolder will stay visible as otherwise, it might be
     * removed by the LayoutManager if the move causes the View to go out of bounds. In that
     * case, drag will end prematurely.
     *
     * @param recyclerView The RecyclerView controlled by the ItemTouchHelper.
     * @param viewHolder   The ViewHolder under user's control.
     * @param fromPos      The previous adapter position of the dragged item (before it was
     * moved).
     * @param target       The ViewHolder on which the currently active item has been dropped.
     * @param toPos        The new adapter position of the dragged item.
     * @param x            The updated left value of the dragged View after drag translations
     * are applied. This value does not include margins added by
     * [RecyclerView.ItemDecoration]s.
     * @param y            The updated top value of the dragged View after drag translations
     * are applied. This value does not include margins added by
     * [RecyclerView.ItemDecoration]s.
     */
    fun onMoved(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder, fromPos: Int, target: RecyclerView.ViewHolder,
        toPos: Int, x: Int, y: Int
    ) {
        val layoutManager = recyclerView.layoutManager
        if (layoutManager is ItemTouchHelper.ViewDropHandler) {
            (layoutManager as ItemTouchHelper.ViewDropHandler).prepareForDrop(
                viewHolder.itemView,
                target.itemView, x, y
            )
            return
        }

        // if layout manager cannot handle it, do some guesswork
        if (layoutManager!!.canScrollHorizontally()) {
            val minLeft = layoutManager.getDecoratedLeft(target.itemView)
            if (minLeft <= recyclerView.paddingLeft) {
                recyclerView.scrollToPosition(toPos)
            }
            val maxRight = layoutManager.getDecoratedRight(target.itemView)
            if (maxRight >= recyclerView.width - recyclerView.paddingRight) {
                recyclerView.scrollToPosition(toPos)
            }
        }
        if (layoutManager.canScrollVertically()) {
            val minTop = layoutManager.getDecoratedTop(target.itemView)
            if (minTop <= recyclerView.paddingTop) {
                recyclerView.scrollToPosition(toPos)
            }
            val maxBottom = layoutManager.getDecoratedBottom(target.itemView)
            if (maxBottom >= recyclerView.height - recyclerView.paddingBottom) {
                recyclerView.scrollToPosition(toPos)
            }
        }
    }

    fun onDraw(
        c: Canvas, parent: RecyclerView, selected: RecyclerView.ViewHolder?,
        recoverAnimationList: List<ConvertedItemTouchHelper.RecoverAnimation>,
        actionState: Int, dX: Float, dY: Float
    ) {
        val recoverAnimSize = recoverAnimationList.size
        for (i in 0 until recoverAnimSize) {
            val anim = recoverAnimationList[i]
            anim.update()
            val count = c.save()
            onChildDraw(
                c, parent, anim.mViewHolder, anim.mX, anim.mY, anim.mActionState,
                false
            )
            c.restoreToCount(count)
        }
        if (selected != null) {
            val count = c.save()
            onChildDraw(c, parent, selected, dX, dY, actionState, true)
            c.restoreToCount(count)
        }
    }

    fun onDrawOver(
        c: Canvas, parent: RecyclerView, selected: RecyclerView.ViewHolder?,
        recoverAnimationList: MutableList<ConvertedItemTouchHelper.RecoverAnimation>,
        actionState: Int, dX: Float, dY: Float
    ) {
        val recoverAnimSize = recoverAnimationList.size
        for (i in 0 until recoverAnimSize) {
            val anim = recoverAnimationList[i]
            val count = c.save()
            onChildDrawOver(
                c, parent, anim.mViewHolder, anim.mX, anim.mY, anim.mActionState,
                false
            )
            c.restoreToCount(count)
        }
        if (selected != null) {
            val count = c.save()
            onChildDrawOver(c, parent, selected, dX, dY, actionState, true)
            c.restoreToCount(count)
        }
        var hasRunningAnimation = false
        for (i in recoverAnimSize - 1 downTo 0) {
            val anim = recoverAnimationList[i]
            if (anim.mEnded && !anim.mIsPendingCleanup) {
                recoverAnimationList.removeAt(i)
            } else if (!anim.mEnded) {
                hasRunningAnimation = true
            }
        }
        if (hasRunningAnimation) {
            parent.invalidate()
        }
    }

    /**
     * Called by the ItemTouchHelper when the user interaction with an element is over and it
     * also completed its animation.
     *
     *
     * This is a good place to clear all changes on the View that was done in
     * [.onSelectedChanged],
     * [.onChildDraw] or
     * [.onChildDrawOver].
     *
     * @param recyclerView The RecyclerView which is controlled by the ItemTouchHelper.
     * @param viewHolder   The View that was interacted by the user.
     */
    open fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        ItemTouchUIUtilImpl.INSTANCE.clearView(viewHolder.itemView)
    }

    /**
     * Called by ItemTouchHelper on RecyclerView's onDraw callback.
     *
     *
     * If you would like to customize how your View's respond to user interactions, this is
     * a good place to override.
     *
     *
     * Default implementation translates the child by the given `dX`,
     * `dY`.
     * ItemTouchHelper also takes care of drawing the child after other children if it is being
     * dragged. This is done using child re-ordering mechanism. On platforms prior to L, this
     * is
     * achieved via [android.view.ViewGroup.getChildDrawingOrder] and on L
     * and after, it changes View's elevation value to be greater than all other children.)
     *
     * @param c                 The canvas which RecyclerView is drawing its children
     * @param recyclerView      The RecyclerView to which ItemTouchHelper is attached to
     * @param viewHolder        The ViewHolder which is being interacted by the User or it was
     * interacted and simply animating to its original position
     * @param dX                The amount of horizontal displacement caused by user's action
     * @param dY                The amount of vertical displacement caused by user's action
     * @param actionState       The type of interaction on the View. Is either [                          ][.ACTION_STATE_DRAG] or [.ACTION_STATE_SWIPE].
     * @param isCurrentlyActive True if this view is currently being controlled by the user or
     * false it is simply animating back to its original state.
     * @see .onChildDrawOver
     */
    open fun onChildDraw(
        c: Canvas, recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
    ) {
        ItemTouchUIUtilImpl.INSTANCE.onDraw(
            c, recyclerView, viewHolder.itemView, dX, dY,
            actionState, isCurrentlyActive
        )
    }

    /**
     * Called by ItemTouchHelper on RecyclerView's onDraw callback.
     *
     *
     * If you would like to customize how your View's respond to user interactions, this is
     * a good place to override.
     *
     *
     * Default implementation translates the child by the given `dX`,
     * `dY`.
     * ItemTouchHelper also takes care of drawing the child after other children if it is being
     * dragged. This is done using child re-ordering mechanism. On platforms prior to L, this
     * is
     * achieved via [android.view.ViewGroup.getChildDrawingOrder] and on L
     * and after, it changes View's elevation value to be greater than all other children.)
     *
     * @param c                 The canvas which RecyclerView is drawing its children
     * @param recyclerView      The RecyclerView to which ItemTouchHelper is attached to
     * @param viewHolder        The ViewHolder which is being interacted by the User or it was
     * interacted and simply animating to its original position
     * @param dX                The amount of horizontal displacement caused by user's action
     * @param dY                The amount of vertical displacement caused by user's action
     * @param actionState       The type of interaction on the View. Is either [                          ][.ACTION_STATE_DRAG] or [.ACTION_STATE_SWIPE].
     * @param isCurrentlyActive True if this view is currently being controlled by the user or
     * false it is simply animating back to its original state.
     * @see .onChildDrawOver
     */
    open fun onChildDrawOver(
        c: Canvas, recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
    ) {
        ItemTouchUIUtilImpl.INSTANCE.onDrawOver(
            c, recyclerView, viewHolder.itemView, dX, dY,
            actionState, isCurrentlyActive
        )
    }

    /**
     * Called by the ItemTouchHelper when user action finished on a ViewHolder and now the View
     * will be animated to its final position.
     *
     *
     * Default implementation uses ItemAnimator's duration values. If
     * `animationType` is [.ANIMATION_TYPE_DRAG], it returns
     * [RecyclerView.ItemAnimator.getMoveDuration], otherwise, it returns
     * [RecyclerView.ItemAnimator.getRemoveDuration]. If RecyclerView does not have
     * any [RecyclerView.ItemAnimator] attached, this method returns
     * `DEFAULT_DRAG_ANIMATION_DURATION` or `DEFAULT_SWIPE_ANIMATION_DURATION`
     * depending on the animation type.
     *
     * @param recyclerView  The RecyclerView to which the ItemTouchHelper is attached to.
     * @param animationType The type of animation. Is one of [.ANIMATION_TYPE_DRAG],
     * [.ANIMATION_TYPE_SWIPE_CANCEL] or
     * [.ANIMATION_TYPE_SWIPE_SUCCESS].
     * @param animateDx     The horizontal distance that the animation will offset
     * @param animateDy     The vertical distance that the animation will offset
     * @return The duration for the animation
     */
    fun getAnimationDuration(
        recyclerView: RecyclerView, animationType: Int,
        animateDx: Float, animateDy: Float
    ): Long {
        val itemAnimator = recyclerView.itemAnimator
        return if (itemAnimator == null) {
            if (animationType == ItemTouchHelper.ANIMATION_TYPE_DRAG) DEFAULT_DRAG_ANIMATION_DURATION.toLong() else DEFAULT_SWIPE_ANIMATION_DURATION.toLong()
        } else {
            if (animationType == ItemTouchHelper.ANIMATION_TYPE_DRAG) itemAnimator.moveDuration else itemAnimator.removeDuration
        }
    }

    /**
     * Called by the ItemTouchHelper when user is dragging a view out of bounds.
     *
     *
     * You can override this method to decide how much RecyclerView should scroll in response
     * to this action. Default implementation calculates a value based on the amount of View
     * out of bounds and the time it spent there. The longer user keeps the View out of bounds,
     * the faster the list will scroll. Similarly, the larger portion of the View is out of
     * bounds, the faster the RecyclerView will scroll.
     *
     * @param recyclerView        The RecyclerView instance to which ItemTouchHelper is
     * attached to.
     * @param viewSize            The total size of the View in scroll direction, excluding
     * item decorations.
     * @param viewSizeOutOfBounds The total size of the View that is out of bounds. This value
     * is negative if the View is dragged towards left or top edge.
     * @param totalSize           The total size of RecyclerView in the scroll direction.
     * @param msSinceStartScroll  The time passed since View is kept out of bounds.
     * @return The amount that RecyclerView should scroll. Keep in mind that this value will
     * be passed to [RecyclerView.scrollBy] method.
     */
    fun interpolateOutOfBoundsScroll(
        recyclerView: RecyclerView,
        viewSize: Int, viewSizeOutOfBounds: Int,
        totalSize: Int, msSinceStartScroll: Long
    ): Int {
        val maxScroll = getMaxDragScroll(recyclerView)
        val absOutOfBounds = Math.abs(viewSizeOutOfBounds)
        val direction = Math.signum(viewSizeOutOfBounds.toFloat()).toInt()
        // might be negative if other direction
        val outOfBoundsRatio = Math.min(1f, 1f * absOutOfBounds / viewSize)
        val cappedScroll = (direction * maxScroll
                * sDragViewScrollCapInterpolator.getInterpolation(outOfBoundsRatio)).toInt()
        val timeRatio: Float
        timeRatio = if (msSinceStartScroll > DRAG_SCROLL_ACCELERATION_LIMIT_TIME_MS) {
            1f
        } else {
            msSinceStartScroll.toFloat() / DRAG_SCROLL_ACCELERATION_LIMIT_TIME_MS
        }
        val value = (cappedScroll * sDragScrollInterpolator
            .getInterpolation(timeRatio)).toInt()
        return if (value == 0) {
            if (viewSizeOutOfBounds > 0) 1 else -1
        } else value
    }

    companion object {
        const val DEFAULT_DRAG_ANIMATION_DURATION = 200
        const val DEFAULT_SWIPE_ANIMATION_DURATION = 250
        const val RELATIVE_DIR_FLAGS = (ItemTouchHelper.START or ItemTouchHelper.END
                or (ItemTouchHelper.START or ItemTouchHelper.END shl ConvertedItemTouchHelper.DIRECTION_FLAG_COUNT)
                or (ItemTouchHelper.START or ItemTouchHelper.END shl 2 * ConvertedItemTouchHelper.DIRECTION_FLAG_COUNT))
        private const val ABS_HORIZONTAL_DIR_FLAGS = (ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                or (ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT shl ConvertedItemTouchHelper.DIRECTION_FLAG_COUNT)
                or (ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT shl 2 * ConvertedItemTouchHelper.DIRECTION_FLAG_COUNT))
        private val sDragScrollInterpolator =
            Interpolator { t -> t * t * t * t * t }
        private val sDragViewScrollCapInterpolator =
            Interpolator { t ->
                var t = t
                t -= 1.0f
                t * t * t * t * t + 1.0f
            }

        /**
         * Drag scroll speed keeps accelerating until this many milliseconds before being capped.
         */
        private const val DRAG_SCROLL_ACCELERATION_LIMIT_TIME_MS: Long = 2000

        /**
         * Returns the [ItemTouchUIUtil] that is used by the [Callback] class for
         * visual
         * changes on Views in response to user interactions. [ItemTouchUIUtil] has different
         * implementations for different platform versions.
         *
         *
         * By default, [Callback] applies these changes on
         * [RecyclerView.ViewHolder.itemView].
         *
         *
         * For example, if you have a use case where you only want the text to move when user
         * swipes over the view, you can do the following:
         * <pre>
         * public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder){
         * getDefaultUIUtil().clearView(((ItemTouchViewHolder) viewHolder).textView);
         * }
         * public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
         * if (viewHolder != null){
         * getDefaultUIUtil().onSelected(((ItemTouchViewHolder) viewHolder).textView);
         * }
         * }
         * public void onChildDraw(Canvas c, RecyclerView recyclerView,
         * RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState,
         * boolean isCurrentlyActive) {
         * getDefaultUIUtil().onDraw(c, recyclerView,
         * ((ItemTouchViewHolder) viewHolder).textView, dX, dY,
         * actionState, isCurrentlyActive);
         * return true;
         * }
         * public void onChildDrawOver(Canvas c, RecyclerView recyclerView,
         * RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState,
         * boolean isCurrentlyActive) {
         * getDefaultUIUtil().onDrawOver(c, recyclerView,
         * ((ItemTouchViewHolder) viewHolder).textView, dX, dY,
         * actionState, isCurrentlyActive);
         * return true;
         * }
        </pre> *
         *
         * @return The [ItemTouchUIUtil] instance that is used by the [Callback]
         */
        val defaultUIUtil: ItemTouchUIUtil
            get() = ItemTouchUIUtilImpl.INSTANCE

        /**
         * Replaces a movement direction with its relative version by taking layout direction into
         * account.
         *
         * @param flags           The flag value that include any number of movement flags.
         * @param layoutDirection The layout direction of the View. Can be obtained from
         * [ViewCompat.getLayoutDirection].
         * @return Updated flags which uses relative flags ([.START], [.END]) instead
         * of [.LEFT], [.RIGHT].
         * @see .convertToAbsoluteDirection
         */
        fun convertToRelativeDirection(flags: Int, layoutDirection: Int): Int {
            var flags = flags
            val masked = flags and ABS_HORIZONTAL_DIR_FLAGS
            if (masked == 0) {
                return flags // does not have any abs flags, good.
            }
            flags = flags and masked.inv() //remove left / right.
            if (layoutDirection == ViewCompat.LAYOUT_DIRECTION_LTR) {
                // no change. just OR with 2 bits shifted mask and return
                flags =
                    flags or (masked shl 2) // START is 2 bits after LEFT, END is 2 bits after RIGHT.
                return flags
            } else {
                // add RIGHT flag as START
                flags = flags or (masked shl 1 and ABS_HORIZONTAL_DIR_FLAGS.inv())
                // first clean RIGHT bit then add LEFT flag as END
                flags = flags or (masked shl 1 and ABS_HORIZONTAL_DIR_FLAGS shl 2)
            }
            return flags
        }

        /**
         * Convenience method to create movement flags.
         *
         *
         * For instance, if you want to let your items be drag & dropped vertically and swiped
         * left to be dismissed, you can call this method with:
         * `makeMovementFlags(UP | DOWN, LEFT);`
         *
         * @param dragFlags  The directions in which the item can be dragged.
         * @param swipeFlags The directions in which the item can be swiped.
         * @return Returns an integer composed of the given drag and swipe flags.
         */
        fun makeMovementFlags(dragFlags: Int, swipeFlags: Int): Int {
            return (makeFlag(ItemTouchHelper.ACTION_STATE_IDLE, swipeFlags or dragFlags)
                    or makeFlag(ItemTouchHelper.ACTION_STATE_SWIPE, swipeFlags)
                    or makeFlag(ItemTouchHelper.ACTION_STATE_DRAG, dragFlags))
        }

        /**
         * Shifts the given direction flags to the offset of the given action state.
         *
         * @param actionState The action state you want to get flags in. Should be one of
         * [.ACTION_STATE_IDLE], [.ACTION_STATE_SWIPE] or
         * [.ACTION_STATE_DRAG].
         * @param directions  The direction flags. Can be composed from [.UP], [.DOWN],
         * [.RIGHT], [.LEFT] [.START] and [.END].
         * @return And integer that represents the given directions in the provided actionState.
         */
        fun makeFlag(actionState: Int, directions: Int): Int {
            return directions shl actionState * ConvertedItemTouchHelper.DIRECTION_FLAG_COUNT
        }
    }
}

