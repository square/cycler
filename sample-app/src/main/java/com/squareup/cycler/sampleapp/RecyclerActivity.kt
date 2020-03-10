package com.squareup.cycler.sampleapp

import android.os.Bundle
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.squareup.cycler.Recycler
import com.squareup.cycler.sampleapp.RecyclerActivity.BaseItem.Discount
import com.squareup.cycler.sampleapp.RecyclerActivity.BaseItem.Item
import com.squareup.cycler.toDataSource

class RecyclerActivity : AppCompatActivity(R.layout.main_activity) {

  private lateinit var cycler: Recycler<BaseItem>
  private lateinit var data: List<BaseItem>

  private var showTotal = true

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    data = sampleList()
    findViewById<CheckBox>(R.id.show_total).apply {
      isChecked = showTotal
      setOnCheckedChangeListener { _, isChecked ->
        showTotal = isChecked
        update()
      }
    }
    configureRecycler()
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
  private fun configureRecycler() {
    val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
    cycler = Recycler.adopt(recyclerView) {

      // Cart-Item definition.
      row<Item, ItemView> {
        create { context ->
          view = ItemView(context)
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
  }

  /**
   * Updates the data to the already configured recycler.
   */
  private fun update() {

    val total = data.fold(0f) {
      acc, item -> acc + item.amount
    }.coerceAtLeast(0f)

    cycler.update {
      data = this@RecyclerActivity.data.toDataSource()
      extraItem = if (showTotal) GrandTotal(total) else null
    }
  }

  sealed class BaseItem {

    abstract val amount: Float

    data class Item(
      val id: Int,
      val name: String,
      val price: Float,
      val quantity: Int
    ) : BaseItem() {
      override val amount = price * quantity
    }

    data class Discount(
      val id: Int,
      override val amount: Float,
      val isStarred: Boolean = false
    ): BaseItem() {
      init { require(amount < 0f) }
    }
  }

  data class GrandTotal(val total: Float)
}



