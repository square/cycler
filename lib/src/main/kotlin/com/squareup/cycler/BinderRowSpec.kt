package com.squareup.cycler

import android.view.View
import com.squareup.cycler.Recycler.CreatorContext
import com.squareup.cycler.Recycler.RowSpec
import com.squareup.cycler.Recycler.ViewHolder

/**
 * Creation fixed case: extensions can fix a creator block and still allow access to [RowSpec].
 * Constructor is public to allow for inlining but should NOT be used directly.
 * @see Recycler.create
 * @hide
 */
class BinderRowSpec<I : Any, S : I, V : View>
@PublishedApi internal constructor(
  typeMatchBlock: (I) -> Boolean,
  val creatorBlock: (CreatorContext) -> V
) : RowSpec<I, S, V>(typeMatchBlock) {

  private lateinit var bindBlock: (Int, S, V) -> Unit

  /** Most of the time binding doesn't need the original position in the list. */
  inline fun bind(crossinline block: (S, V) -> Unit) {
    bind { _, item, view -> block(item, view) }
  }

  /** If you need the position to bind some event handler, you can receive it. */
  fun bind(block: (Int, S, V) -> Unit) {
    bindBlock = block
  }

  override fun createViewHolder(creatorContext: CreatorContext, subType: Int): ViewHolder<V> {
    require(subType == 0) {
      "Expected subType == 0 (${BinderRowSpec::class.java} doesn't use subTypes)."
    }
    val view = creatorBlock(creatorContext)
    if (view.layoutParams == null) view.layoutParams =
      createItemLayoutParams()
    return BinderViewHolder(view, bindBlock)
  }

  /** Each ViewHolder is created with its view and the bind block that receives view by argument. */
  private class BinderViewHolder<S, V : View>(
    view: V,
    val bindBlock: (Int, S, V) -> Unit
  ) : ViewHolder<V>(view) {
    override fun bind(
      index: Int,
      dataItem: Any
    ) {
      @Suppress("UNCHECKED_CAST")
      bindBlock(index, dataItem as S, itemView as V)
    }
  }
}
