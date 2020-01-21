package com.squareup.cycler

import android.content.Context
import android.view.View
import androidx.annotation.LayoutRes
import com.squareup.cycler.Recycler.CreatorContext
import com.squareup.cycler.Recycler.RowSpec
import com.squareup.cycler.Recycler.ViewHolder

/**
 * Normal case: `row { create { bind { ... } } }`
 * Constructor is public to allow for inlining but should NOT be used directly.
 * @see Recycler.create
 * @hide
 */
class StandardRowSpec<I : Any, S : I, V : View>
@PublishedApi internal constructor(
  typeMatchBlock: (I) -> Boolean
) : RowSpec<I, S, V>(typeMatchBlock) {

  private lateinit var createBlock: Creator<I, S, V>.(Context) -> Unit

  fun create(block: Creator<I, S, V>.(Context) -> Unit) {
    createBlock = block
  }

  /**
   * Create version that receives a layoutId. It inflates the layout and let you use the Creator
   * as usual, so you can get references to sub-views and call bind when you are ready.
   */
  inline fun create(
    @LayoutRes layoutId: Int,
    crossinline block: Creator<I, S, V>.() -> Unit
  ) {
    create { context ->
      @Suppress("UNCHECKED_CAST")
      view = View.inflate(context, layoutId, null) as V
      block()
    }
  }

  override fun createViewHolder(creatorContext: CreatorContext, subType: Int): ViewHolder<V> {
    require(subType == 0) {
      "Expected subType == 0 (${StandardRowSpec::class.java} doesn't use subTypes)."
    }
    val creator = Creator<I, S, V>(creatorContext)
    creator.createBlock(creatorContext.context)
    val view = creator.view
    // LayoutParams should not be set, and item should have a good wrap_content behavior.
    // But... legacy code.
    if (view.layoutParams == null) view.layoutParams =
      createItemLayoutParams()
    return StandardViewHolder(view, creator.bindBlock)
  }

  @RecyclerApiMarker
  class Creator<I : Any, S : I, V : View>(val creatorContext: CreatorContext) {
    lateinit var view: V
    internal var bindBlock: (Int, S) -> Unit = { _, _ -> }
      private set

    /** Most of the time binding doesn't need the original position in the list. */
    inline fun bind(crossinline block: (S) -> Unit) {
      bind { _, item -> block(item) }
    }

    /** If you need the position to bind some event handler, you can receive it. */
    fun bind(block: (Int, S) -> Unit) {
      bindBlock = block
    }
  }

  /** Each ViewHolder is created with its view and the bind block with internal references. */
  private class StandardViewHolder<S, V : View>(
    view: V,
    val bindBlock: (Int, S) -> Unit
  ) : ViewHolder<V>(view) {
    override fun bind(
      index: Int,
      dataItem: Any
    ) {
      @Suppress("UNCHECKED_CAST")
      bindBlock(index, dataItem as S)
    }
  }
}
