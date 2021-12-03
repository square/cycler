package com.squareup.cycler.sampleapp

import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.cycler.Recycler
import com.squareup.cycler.sampleapp.BaseItem.Discount
import com.squareup.cycler.sampleapp.BaseItem.Item

/**
 * What makes this page different from [SimplePage] is that we set a [GridLayoutManager] on
 * the [RecyclerView].
 */
object GridPage : Page {

  override fun toString() = "Grid"

  private lateinit var cycler: Recycler<BaseItem>
  private var data: List<BaseItem> = gridSampleData()

  private var showTotal = false

  override val options: List<Pair<String, (Boolean) -> Unit>> = listOf(
    "Show total" to { isChecked -> showTotal(isChecked) }
  )

  private fun showTotal(isChecked: Boolean) {
    showTotal = isChecked
    update()
  }

  /**
   * This runs once and provides all the info needed to show the Recycler.
   * It's what defines what types of rows (items) we will have in it, and how they will be
   * 1) created and 2) data-bound.
   */
  override fun config(recyclerView: RecyclerView) {
    recyclerView.layoutManager = GridLayoutManager(recyclerView.context, GRID_SPAN_COUNT)
    cycler = Recycler.adopt(recyclerView) {
      // Cart-Item definition.
      row<Item, ItemView> {
        create { context ->
          view = ItemView(context, showDragHandle = false)
          bind { index, item ->
            view.show(item, index % 2 == 0)
          }
        }
      }

      // Discount definition.
      row<Discount, ConstraintLayout> {
        // We are only defining for those Discount that are starred.
        forItemsWhere { it.isStarred }
        create(R.layout.starred_discount) {
          val amount = view.findViewById<TextView>(R.id.amount)
          bind { discount -> amount.text = discount.amount.format() }
        }
      }

      // Discount definition.
      row<Discount, ConstraintLayout> {
        // The Discount that are not starred will fall into this definition instead.
        create(R.layout.discount) {
          val amount = view.findViewById<TextView>(R.id.amount)
          bind { discount -> amount.text = discount.amount.format() }
        }
      }

      // We define by separate if we want a footer item (it doesn't need to extend BaseItem).
      extraItem<GrandTotal, TextView> {
        create { context ->
          view = TextView(context)
          view.setTextAppearance(R.style.TextAppearance_Bold)
          bind { total ->
            view.text = "Grand total: ${total.total.format()}"
          }
        }
      }
    }
    update()
  }

  /**
   * Updates the data to the already configured recycler.
   */
  private fun update() {

    val total = data
      .sumByFloat { it.amount }
      .coerceAtLeast(0f)

    cycler.update {
      data = this@GridPage.data
      extraItem = if (showTotal) GrandTotal(total) else null
    }
  }
}

/**
 * Kotlin only offers [Iterable.sumBy] (Int) and [Iterable.sumByDouble].
 */
private inline fun <T : Any> Iterable<T>.sumByFloat(block: (T) -> Float): Float {
  return sumByDouble { block(it).toDouble() }.toFloat()
}