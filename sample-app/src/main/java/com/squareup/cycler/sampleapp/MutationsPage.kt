package com.squareup.cycler.sampleapp

import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.squareup.cycler.Recycler
import com.squareup.cycler.dragHandle
import com.squareup.cycler.enableMutations
import com.squareup.cycler.sampleapp.BaseItem.Item
import com.squareup.cycler.toDataSource

class MutationsPage : Page {

  override fun toString() = "Drag & Swipe"

  override val options: List<Pair<String, (Boolean) -> Unit>> = listOf()

  lateinit var cycler: Recycler<Item>

  override fun config(recyclerView: RecyclerView) {
    // Use a slightly different view with a drag handle.
    // Make swipeable discounts
    cycler = Recycler.adopt(recyclerView) {
      // Cart-Item definition.
      row<Item, ItemView> {
        create { context ->
          view = ItemView(context, showDragHandle = true)
          dragHandle(view.dragHandle)
          bind { index, item ->
            view.show(item, index % 2 == 0)
          }
        }
      }
      enableMutations {
        dragAndDropEnabled = true
        swipeToRemoveEnabled = true
        onMove { from, to ->
          Toast.makeText(
              recyclerView.context,
              "Moved index $from to index $to",
              Toast.LENGTH_SHORT
          ).show()
        }

        onSwiped { it ->
          Toast.makeText(
              recyclerView.context,
              "Swiped ${it.name} away",
              Toast.LENGTH_SHORT
          ).show()
        }
      }
    }
    update()
  }

  private fun update() {
    cycler.data = sampleData().toDataSource()
  }

  fun sampleData() = (1..20).map {
    Item(it, "Product #$it", (it * 13.73).rem(100).toFloat(), 1)
  }.toList()
}
