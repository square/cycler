package com.squareup.cycler

import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import androidx.annotation.VisibleForTesting
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.squareup.cycler.Recycler.CreatorContext
import com.squareup.cycler.Recycler.RowSpec
import com.squareup.cycler.StandardRowSpec.Creator
import com.squareup.cycler.SwipeDirection.Companion.BOTH
import com.squareup.cycler.SwipeDirection.Companion.NONE
import com.squareup.cycler.SwipeDirection.END
import com.squareup.cycler.SwipeDirection.LEFT
import com.squareup.cycler.SwipeDirection.RIGHT
import com.squareup.cycler.SwipeDirection.START
import java.lang.IllegalStateException
import java.util.EnumSet
import kotlin.math.abs

fun <I : Any> Recycler.Config<I>.enableMutations(block: MutationExtensionSpec<I>.() -> Unit) {
  extension(MutationExtensionSpec<I>().apply(block))
}

/**
 * Used in swipe related methods. They can be absolute or text-direction relative ([START]/[END]).
 * The values used will depend on what is returned in [MutationExtensionSpec.canSwipe].
 */
enum class SwipeDirection(
  val absolute: Boolean,
  val platformValue: Int
) {
  START(false, ItemTouchHelper.START),
  END(false, ItemTouchHelper.END),
  LEFT(true, ItemTouchHelper.LEFT),
  RIGHT(true, ItemTouchHelper.RIGHT);

  companion object {
    val NONE: Set<SwipeDirection> = EnumSet.noneOf(SwipeDirection::class.java)
    val BOTH: Set<SwipeDirection> = EnumSet.of(START, END)
    val BOTH_ABSOLUTE: Set<SwipeDirection> = EnumSet.of(LEFT, RIGHT)
  }
}

class MutationExtensionSpec<T : Any> : ExtensionSpec<T> {
  override fun create(): Extension<T> = MutationExtension(this)
  internal var onMove: ((Int, Int) -> Unit)? = null
  internal var onSwiped: ((T, direction: SwipeDirection) -> Unit)? = null
  internal var canSwipeItem: ((T) -> Set<SwipeDirection>) = {
    // It defaults to the behavior it had before this property existed:
    // swipeToRemoveEnabled means both sides. Otherwise it won't swipe either way.
    if (swipeToRemoveEnabled) BOTH else NONE
  }
  internal var canDropOverItem: ((T) -> Boolean) = { true }
  internal var onDragDrop: ((originalIndex: Int, dropIndex: Int) -> Unit)? = null

  @PublishedApi
  internal val swipeUnderViewSpecs = mutableListOf<RowSpec<Any, SwipeBindData<*>, View>>()

  /**
   * Determine whether items can be swiped left to right or right to left to remove them from the
   * recycler view or not
   */
  var swipeToRemoveEnabled = false

  /**
   * Determine whether items can be dragged up or down the recycler view or not
   */
  var dragAndDropEnabled = false

  /**
   * Determine whether a drag can be started by long pressing an item.
   */
  var longPressDragEnabled = false

  /**
   * Register a callback that will be invoked when an item is moved from one position to another
   * (using drag and drop)
   */
  fun onMove(block: (Int, Int) -> Unit) {
    onMove = block
  }

  /**
   * Allows to define a [RowSpec] (usual `create/bind` mechanism) for a view that will be drawn
   * below a swiped row. It's created when the swipe is initiated and bind is called with every
   * movement receiving [SwipeBindData] so:
   *
   * - the original swiped view can be retouched accorindg to the percentage (alpha for instance)
   * - the original dataItem, in case its data is needed for the underview, and the underview itself.
   *
   * WARNING: this API might evolve as currently:
   *
   * - under view is only defined at top level, and cannot be customized for each row-type,
   *   which should be the case using Cycler.
   * - probably there should be extra callbacks or customizations available for swiping. For
   *   instance: to restore the original alpha of the swiped view.
   */
  inline fun <reified S : T, V : View> swipeUnderViewSpec(
    block: StandardRowSpec<Any, SwipeBindData<S>, V>.() -> Unit
  ) {
    // RowSpec's super type is Any. We cannot use the proper T
    // because we are not passing the proper S, but SwipeBindData<...>.
    swipeUnderViewSpecs += StandardRowSpec<Any, SwipeBindData<S>, V> { it is S }.apply(block)
  }

  @Deprecated("Use onSwiped", ReplaceWith("onSwiped(block)"))
  inline fun onSwipeToRemove(crossinline block: (T) -> Unit) {
    onSwiped { item: T, _: SwipeDirection -> block(item) }
  }

  /**
   * Register a callback that will be invoked when an item is removed by swiping it.
   */
  inline fun onSwiped(crossinline block: (T) -> Unit) {
    onSwiped { item: T, _: SwipeDirection -> block(item) }
  }

  @Deprecated("Use onSwiped", ReplaceWith("onSwiped(block)"))
  fun onSwipeToRemove(block: (T, direction: SwipeDirection) -> Unit) {
    onSwiped(block)
  }

  /**
   * Register a callback that will be invoked when an item is removed by swiping it.
   * @param block receives the swiped data item and the direction.
   * @see SwipeDirection
   */
  fun onSwiped(block: (T, direction: SwipeDirection) -> Unit) {
    onSwiped = block
  }

  @Deprecated(
      message = "Use the version that specifies which side is allowed.",
      replaceWith = ReplaceWith(
          expression = "canSwipe { if (block(it)) SwipeDirection.BOTH else SwipeDirection.NONE }"
      )
  )
  inline fun canSwipeToRemoveItem(crossinline block: (T) -> Boolean) {
    canSwipe { if (block(it)) SwipeDirection.BOTH else SwipeDirection.NONE }
  }

  /**
   * Register a check that will be invoked for each item to decide whether that item can be swiped
   * or not. This should return which directions are allowed.
   *
   * See the other version of the method, that returns which direction is allowed.
   */
  fun canSwipe(block: (T) -> Set<SwipeDirection>) {
    canSwipeItem = block
  }

  /**
   * Register a check that will be invoked for each item to decide whether that item can have dragged
   * items dropped over it.
   */
  fun canDropOverItem(block: (T) -> Boolean) {
    canDropOverItem = block
  }

  /**
   * Register a handler to be called for when the user drops an item after dragging
   * (when the finger is released). [block] will receive the [originalIndex] and new [dropIndex] for
   * the item that was dragged. This means the new data shown is equivalent to
   * `previousData.swap(originalIndex, dropIndex)`. If the item wasn't moved (dropped before moving
   * any distance) then [originalIndex] == [dropIndex].
   */
  fun onDragDrop(block: (originalIndex: Int, dropIndex: Int) -> Unit) {
    onDragDrop = block
  }
}

/**
 * Define the extension method to get the current position-changes from a [Recycler].
 * It returns the mutation-map respect to the originally set [DataSource].
 */
fun <I : Any> Recycler<I>.getCurrentMutations(): MutationMap {
  return extension<MutationExtension<I>>()?.data?.copyMutationMap() ?: MutationMap()
}

/**
 * Define the extension method to get the [DataSource] including all the changes from a [Recycler].
 * It returns a copy of the DataSource so it's immutable (and developer can't change the internal one).
 */
fun <I : Any> Recycler<I>.getMutatedData(): DataSource<I> {
  return data.let { source ->
    mutableListOf<I>().apply {
      addAll((0 until source.size).asSequence().map(source::get))
    }.toDataSource()
  }
}

/**
 * Code configuring a [Recycler] should call this method inside the `create {}` block to set up
 * the view that will be the drag handle.
 * Convenience methods using the [BinderRowSpec] (like Recycler.Config.nohoRow)
 * should call it inside the creation lambda.
 */
fun <I : Any, S : I, V : View> Creator<I, S, V>.dragHandle(view: View) {
  requireNotNull(creatorContext.extension<MutationExtension<I>>()) {
    "Drag and drop extension was not configured for the Recycler."
  }.setUpDragHandle(view)
}

private class SwipeState<T : Any, V : View>(
  val viewHolder: Recycler.ViewHolder<V>,
  var bindData: SwipeBindData<T>
) {

  /**
   * @return true if the binding was successful. It is, the under-view should be painted.
   */
  fun bind(viewTranslation: Float): Boolean {
    val valid = bindData.bind(viewTranslation)
    if (valid) {
      viewHolder.bind(0, bindData)
    }
    return valid
  }
}

/**
 * Passed as data for the [RowSpec.bind] defined in [MutationExtensionSpec.swipeUnderViewSpec].
 */
class SwipeBindData<T : Any>
@VisibleForTesting
internal constructor(
  val dataItem: T,
  val swipedView: View,
  private val allowedDirections: Set<SwipeDirection>
) {

  private val rightToLeft: Boolean = swipedView.layoutDirection == View.LAYOUT_DIRECTION_RTL

  /**
   * It will tell if the [bind] was called successfully and there's a [direction] and [percentage]
   * to provide to the developer's handler.
   */
  internal var isValid = false

  /**
   * The direction the swipe is happening at the moment.
   * If both directions are allowed this might change for the same swipe.
   */
  lateinit var direction: SwipeDirection
    internal set

  /**
   * Represents the percentage of swiping to the size. 0f <= percentage <= 1f.
   * - 0f means the view is exactly in its normal place.
   * - 1f means the view is completely swiped to the [direction].
   */
  var percentage: Float = 0f
    internal set

  /**
   * @return true if the binding was successful: valid direction and percentage.
   */
  fun bind(viewTranslation: Float): Boolean {
    isValid = doBind(viewTranslation)
    return isValid
  }

  private fun doBind(viewTranslation: Float): Boolean {
    val viewWidth = swipedView.width
    if (viewWidth <= 0) {
      return false
    }
    val swipingLeft = viewTranslation < 0
    val candidateDirections = when {
      swipingLeft && !rightToLeft -> leftDirections
      !swipingLeft && !rightToLeft -> rightDirections
      swipingLeft && rightToLeft -> leftDirectionsRtl
      !swipingLeft && rightToLeft -> rightDirectionsRtl
      else -> throw IllegalStateException("Unreachable code")
    }
    direction = candidateDirections
        .firstOrNull(allowedDirections::contains)
        ?: return false // This should not happen (but if it does, don't crash).
    percentage = abs(viewTranslation / viewWidth).coerceAtMost(1f)
    return true
  }

  companion object {
    val leftDirections = listOf(START, LEFT)
    val rightDirections = listOf(END, RIGHT)
    val leftDirectionsRtl = listOf(END, LEFT)
    val rightDirectionsRtl = listOf(START, RIGHT)
  }
}

private class MutationExtension<T : Any>(val spec: MutationExtensionSpec<T>) : Extension<T> {

  private lateinit var recycler: Recycler<T>
  private val callback = MyItemTouchCallback()
  private val touchHelper = ItemTouchHelper(callback)

  override fun attach(recycler: Recycler<T>) {
    this.recycler = recycler
    touchHelper.attachToRecyclerView(this.recycler.view)
    this.recycler.view.addItemDecoration(touchHelper)
  }

  override lateinit var data: RecyclerData<T>

  internal fun setUpDragHandle(view: View) {
    view.setOnTouchListener { touchedView, event ->
      if (event.action == MotionEvent.ACTION_DOWN) {
        val viewHolder = recycler.view.findContainingViewHolder(touchedView)!!
        touchHelper.startDrag(viewHolder)
      }
      false
    }
  }

  private inner class MyItemTouchCallback : ItemTouchHelper.Callback() {

    private var lastActionState = ItemTouchHelper.ACTION_STATE_IDLE
    private var draggedItemOriginalIndex = -1
    private var draggedItemCurrentIndex = -1
    private var swipeState: SwipeState<*, *>? = null

    override fun getMovementFlags(
      recyclerView: RecyclerView,
      viewHolder: RecyclerView.ViewHolder
    ): Int {
      if (data.frozen) {
        // If the data has been frozen (undergoing update) we cannot swap / drag anymore!
        return makeMovementFlags(0, 0)
      }
      val moreThanOne = data.data.size > 1
      val dragFlags = if (spec.dragAndDropEnabled && moreThanOne) {
        data.forPosition(
            viewHolder.adapterPosition,
            onDataItem = { ItemTouchHelper.UP or ItemTouchHelper.DOWN },
            onExtraItem = { 0 },
            orElse = { 0 }
        )
      } else {
        0
      }
      val swipeFlags = if (spec.swipeToRemoveEnabled) {
        data.forPosition(
            viewHolder.adapterPosition,
            onDataItem = {
              val directions = spec.canSwipeItem(it)
              if (directions.isNotEmpty()) {
                createSwipeStateFor(it, viewHolder.itemView, directions)
              }
              directions.toPlatformValue()
            },
            onExtraItem = { 0 },
            orElse = { 0 }
        )
      } else {
        0
      }
      return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun isLongPressDragEnabled(): Boolean {
      return spec.longPressDragEnabled
    }

    override fun canDropOver(
      recyclerView: RecyclerView,
      current: RecyclerView.ViewHolder,
      target: RecyclerView.ViewHolder
    ): Boolean {
      val to = target.adapterPosition
      return data.forPosition(
          to,
          onDataItem = {
            spec.canDropOverItem(it)
          },
          onExtraItem = {
            false
          },
          orElse = {
            false
          }
      )
    }

    override fun onMove(
      recyclerView: RecyclerView,
      viewHolder: RecyclerView.ViewHolder,
      target: RecyclerView.ViewHolder
    ): Boolean {
      if (data.frozen) {
        // If the data has been frozen (undergoing update) we cannot swap / drag anymore!
        // See [RecyclerData.frozen] for an explanation.
        return false
      }
      val from = viewHolder.adapterPosition
      val to = target.adapterPosition
      val allow = data.forPosition(
          to,
          onDataItem = {
            // It's ok to move one data item to the position of another data item.
            data.move(from, to)
            recycler.view.adapter!!.notifyItemMoved(from, to)
            // Tell the callback.
            spec.onMove?.invoke(from, to)
            true
          },
          // It's not ok to move to the position of the extra item.
          onExtraItem = { false },
          orElse = { false }
      )

      if (allow) {
        draggedItemCurrentIndex = to
      }

      return allow
    }

    override fun onSelectedChanged(
      viewHolder: ViewHolder?,
      actionState: Int
    ) {
      super.onSelectedChanged(viewHolder, actionState)
      when (actionState) {
        ItemTouchHelper.ACTION_STATE_DRAG -> {
          draggedItemOriginalIndex = viewHolder?.adapterPosition ?: -1
          draggedItemCurrentIndex = draggedItemOriginalIndex
        }
        ItemTouchHelper.ACTION_STATE_IDLE -> {
          if (lastActionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            spec.onDragDrop?.invoke(draggedItemOriginalIndex, draggedItemCurrentIndex)
            draggedItemOriginalIndex = -1
            draggedItemCurrentIndex = -1
          }
        }
      }
      lastActionState = actionState
    }

    private fun removeSwipeState() {
      swipeState = null
    }

    private fun createSwipeStateFor(
      dataItem: T,
      itemView: View,
      allowedDirections: Set<SwipeDirection>
    ) {
      // TODO (https://github.com/square/cycler/issues/14): custom underView specs for rows.
      val underViewRowSpec = spec.swipeUnderViewSpecs.firstOrNull { it.matches(dataItem) }
      underViewRowSpec?.createViewHolder(
          CreatorContext(recycler),
          subType = 0
      )?.let { holder ->
        val underView = holder.itemView
        underView.measure(
            MeasureSpec.makeMeasureSpec(itemView.width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(itemView.height, MeasureSpec.EXACTLY)
        )
        underView.layout(0, 0, itemView.width, itemView.height)
        swipeState = SwipeState(holder, SwipeBindData(dataItem, itemView, allowedDirections))
      }
    }

    /**
     * This customizes drawing for swiping by drawing below the dragged view.
     * We still call the standard method even for swiping as we want any standard processing
     * (like translucency) to be applied.
     */
    override fun onChildDraw(
      canvas: Canvas,
      recyclerView: RecyclerView,
      viewHolder: ViewHolder,
      dX: Float,
      dY: Float,
      actionState: Int,
      isCurrentlyActive: Boolean
    ) {
      if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
        data.forPosition(
            viewHolder.adapterPosition,
            onDataItem = { _ ->
              swipeState?.let { state ->
                if (state.bind(dX)) {
                  val count = canvas.save()
                  canvas.translate(
                      viewHolder.itemView.left.toFloat(),
                      viewHolder.itemView.top.toFloat()
                  )
                  val underView = state.viewHolder.itemView
                  underView.draw(canvas)
                  canvas.restoreToCount(count)
                }
              }
            },
            onExtraItem = { 0 },
            orElse = { 0 }
        )
      }
      super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    override fun onSwiped(
      viewHolder: RecyclerView.ViewHolder,
      direction: Int
    ) {
      removeSwipeState()
      if (data.frozen) {
        // If the data has been frozen (undergoing update) we cannot swap / drag anymore!
        // See [RecyclerData.frozen] for an explanation.
        return
      }

      val position = viewHolder.adapterPosition
      val dataItem = data.data[position]
      data.remove(position)
      recycler.view.adapter!!.notifyItemRemoved(position)
      // Tell the callback.
      spec.onSwiped?.invoke(dataItem, direction.toSwipeDirection())
    }
  }
}

private fun Set<SwipeDirection>.toPlatformValue() = fold(0) { acc, direction ->
  acc or direction.platformValue
}

/**
 * We use this conversion only when we get notified from the Recycler.
 * That notification only uses values approved by us previously, and those values come from
 * [SwipeDirection]. Therefore, the value *must* be one of them.
 */
private fun Int.toSwipeDirection() = SwipeDirection.values().first {
  it.platformValue == this
}
