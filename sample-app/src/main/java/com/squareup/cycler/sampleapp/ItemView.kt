package com.squareup.cycler.sampleapp

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM
import android.view.View
import android.widget.ImageView
import android.widget.ImageView.ScaleType.CENTER
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.squareup.cycler.sampleapp.BaseItem.Item

/**
 * Custom view to instance programmatically from the configuration of pages.
 */
class ItemView(
  context: Context,
  val showDragHandle: Boolean
) : LinearLayout(context, null, 0, R.style.LineContainer) {

  lateinit var dragHandle: ImageView
  private val name = AppCompatTextView(context)
  private val amount = AppCompatTextView(context)

  init {
    name.setTextAppearance(R.style.TextAppearance_Bold)
    if (showDragHandle) {
      dragHandle = AppCompatImageView(context)
      dragHandle.visibility = if (showDragHandle) View.VISIBLE else View.GONE
      dragHandle.setImageResource(R.drawable.drag_handle)
      dragHandle.scaleType = CENTER
      addView(dragHandle, LayoutParams(WRAP_CONTENT, MATCH_PARENT).apply { marginEnd = 20 })
      // Use a background that doesn't depend on position if we are moving items around.
      background = GradientDrawable(
          TOP_BOTTOM,
          intArrayOf(
              Color.rgb(240, 250, 255),
              Color.WHITE
          )
      )
    }
    // TODO: Consider a layout.xml file https://github.com/square/cycler/issues/24
    addView(name, LayoutParams(0, WRAP_CONTENT, 1f))
    addView(amount, LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
  }

  fun show(
    item: Item,
    isEven: Boolean
  ) {
    name.text = item.name
    amount.text = item.amount.format()
    if (!showDragHandle) {
      setBackgroundColor(
          if (isEven) Color.WHITE else Color.rgb(230, 245, 255)
      )
    }
  }
}

fun Float.format() = "%.2f".format(this)
