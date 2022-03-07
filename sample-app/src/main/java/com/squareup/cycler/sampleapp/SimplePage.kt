package com.squareup.cycler.sampleapp

import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.squareup.cycler.Recycler
import com.squareup.cycler.sampleapp.BaseItem.Discount
import com.squareup.cycler.sampleapp.BaseItem.Item
import com.squareup.cycler.toDataSource

object SimplePage : Page {

  override fun toString() = "Simple"

  private lateinit var cycler: Recycler<BaseItem>
  private var data: List<BaseItem> = sampleList()

  private var showTotal = false

  override val options: List<Pair<String, (Boolean) -> Unit>> = listOf(
      "Show total" to { isChecked -> showTotal(isChecked) }
  )

  private fun showTotal(isChecked: Boolean) {
    showTotal = isChecked
    update()
  }

  private fun sampleList() =
    listOf(
        Item(1, "Apples", 1.53f, 5),
        Item(2, "Coffee", 3.59f, 2),
        Discount(5, -2f),
        Item(3, "Bread", 1.14f, 1),
        Item(4, "Ice cream", 2.93f, 2),
        Discount(5, -5f, isStarred = true)
    )

  /**
   * This runs once and provides all the info needed to show the Recycler.
   * It's what defines what types of rows (items) we will have in it, and how they will be
   * 1) created and 2) data-bound.
   */
  override fun config(recyclerView: RecyclerView) {
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
      data = this@SimplePage.data.toDataSource()
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
