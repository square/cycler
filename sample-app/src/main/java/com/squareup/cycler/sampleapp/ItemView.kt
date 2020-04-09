package com.squareup.cycler.sampleapp

import android.content.Context
import android.graphics.Color
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
import androidx.appcompat.widget.AppCompatTextView
import com.squareup.cycler.sampleapp.RecyclerActivity.BaseItem.Item

class ItemView(
  context: Context
) : LinearLayout(context, null, 0, R.style.LineContainer) {
  private val name = AppCompatTextView(context)
  private val amount = AppCompatTextView(context)

  init {
    // TODO: Consider using a layout file https://github.com/square/cycler/issues/24
    name.setTextAppearance(R.style.TextAppearance_Bold)
    addView(name, LayoutParams(0, WRAP_CONTENT, 1f))
    addView(amount, LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
  }

  fun show(item: Item, isEven: Boolean) {
    name.text = item.name
    amount.text = item.amount.format()
    setBackgroundColor(
        if (isEven) Color.WHITE else Color.rgb(230, 245, 255)
    )
  }
}

fun Float.format() = "%.2f".format(this)