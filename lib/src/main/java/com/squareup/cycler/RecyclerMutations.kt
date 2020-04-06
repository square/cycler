package com.squareup.cycler

import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.squareup.cycler.StandardRowSpec.Creator

fun <I : Any> Recycler.Config<I>.enableMutations(block: MutationExtensionSpec<I>.() -> Unit) {
  extension(MutationExtensionSpec<I>().apply(block))
}

class MutationExtensionSpec<T : Any> : ExtensionSpec<T> {
  override fun create(): Extension<T> = MutationExtension(this)
  internal var onMove: ((Int, Int) -> Unit)? = null
  internal var onSwipeToRemove: ((T) -> Unit)? = null
  internal var canSwipeToRemoveItem: ((T) -> Boolean) = { true }
  internal var canDropOverItem: ((T) -> Boolean) = { true }
  internal var onDragDrop: ((originalIndex: Int, dropIndex: Int) -> Unit)? = null

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
   * Register a callback that will be invoked when an item is removed by swiping it
   */
  fun onSwipeToRemove(block: (T) -> Unit) {
    onSwipeToRemove = block
  }

  /**
   * Register a check that will be invoked for each item to decide whether that item can be swiped to
   * remove it or not. This allows a hook for recyclers to specify which items do and do not support
   * swipe to remove.
   */
  fun canSwipeToRemoveItem(block: (T) -> Boolean) {
    canSwipeToRemoveItem = block
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
    }
        .toDataSource()
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
              if (spec.canSwipeToRemoveItem(it)) {
                ItemTouchHelper.START or ItemTouchHelper.END
              } else {
                0
              }
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

    override fun onSwiped(
      viewHolder: RecyclerView.ViewHolder,
      direction: Int
    ) {
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
      spec.onSwipeToRemove?.invoke(dataItem)
    }
  }
}
