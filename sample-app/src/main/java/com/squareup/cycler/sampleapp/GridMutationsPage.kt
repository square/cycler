package com.squareup.cycler.sampleapp

import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.cycler.Recycler
import com.squareup.cycler.enableMutations
import com.squareup.cycler.sampleapp.BaseItem.Item

/**
 * What makes this page different from [MutationsPage] is that
 * 1. we set a [GridLayoutManager] on the [RecyclerView],
 * 2. enable [com.squareup.cycler.MutationExtensionSpec.dragAndDropEnabled]
 * 3. enable [com.squareup.cycler.MutationExtensionSpec.longPressDragEnabled]
 * 4. enable [com.squareup.cycler.MutationExtensionSpec.leftRightDragAndDropEnabled]
 * 5. disable [com.squareup.cycler.MutationExtensionSpec.swipeToRemoveEnabled]
 */
class GridMutationsPage : Page {

  override fun toString() = "Grid Drag and Drop"

  override val options: List<Pair<String, (Boolean) -> Unit>> = listOf()

  lateinit var cycler: Recycler<Item>

  override fun config(recyclerView: RecyclerView) {
    recyclerView.layoutManager = GridLayoutManager(recyclerView.context, GRID_SPAN_COUNT)

    cycler = Recycler.adopt(recyclerView) {
      row<Item, ItemView> {
        create { context ->
          view = ItemView(context, showDragHandle = false)
          bind { index, item ->
            view.show(item, index % 2 == 0)
          }
        }
      }
      enableMutations {
        dragAndDropEnabled = true
        longPressDragEnabled = true
        leftRightDragAndDropEnabled = true
        onMove { from, to ->
          // we receive an onMove call on every position change, as user drags *before* finger lift.
          // Can be very spammy to log here.
        }

        onDragDrop { from, to ->
          Toast.makeText(
            recyclerView.context,
            "DragDrop from index $from to index $to",
            Toast.LENGTH_SHORT
          ).show()
        }
      }
    }
    update()
  }

  private fun update() {
    cycler.data = gridSampleData()
  }
}
