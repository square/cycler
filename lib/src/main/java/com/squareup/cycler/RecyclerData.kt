package com.squareup.cycler

import com.squareup.cycler.Recycler.Config

/**
 * Represents all the data in the recycler (data + extraItem)
 * plus access to configuration for extensions.
 */
class RecyclerData<I : Any>(
  @PublishedApi internal val config: Config<I>,
  originalData: DataSource<I>,
  val extraItem: Any?
) {
  /**
   * Tells if this can be changed or not.
   * If an [Update] is handling it, it will be frozen so it's immutable (no data mutations).
   *
   * When Recycler updates its data it calculates the differences between old and new
   * asynchronously. That means two things:
   * a) Old datasource should not change while it's being compared.
   * b) There shouldn't be mutations: deltas are supposed to be applied over that old datasource.
   * That's ok from the point of view of the user: data is being updated, there's no point in
   * moving around items that will be changed in a few milliseconds.
   */
  var frozen: Boolean = false
    internal set

  fun move(
    from: Int,
    to: Int
  ) {
    ensureNotFrozen()
    mutableData.move(from, to)
  }

  fun remove(index: Int) {
    ensureNotFrozen()
    mutableData.remove(index)
  }

  private fun ensureNotFrozen() {
    require(!frozen) { "Cannot change items in a frozen MutableDataSource!" }
  }

  fun copyMutationMap() = mutableData.copyMutationMap()

  private val mutableData = MutableDataSource(originalData)
  val data: DataSource<I> get() = mutableData
  val hasExtraItem get() = extraItem != null
  val extraItemIndex get() = data.size
  val totalCount get() = data.size + if (hasExtraItem) 1 else 0

  /** Helps extensions to execute code conditionally on position with proper typing. */
  inline fun <T> forPosition(
    position: Int,
    onDataItem: (I) -> T,
    onExtraItem: (Any) -> T,
    orElse: () -> T
  ): T {
    return when (position) {
      extraItemIndex -> {
        if (hasExtraItem) onExtraItem(extraItem!!)
        else orElse()
      }
      in 0 until data.size -> onDataItem(data[position])
      else -> orElse()
    }
  }

  inline fun <reified T : Any> extension(position: Int): T? = forPosition(
      position,
      onDataItem = { item -> config.rowExtension(item) },
      onExtraItem = { item -> config.extraRowExtension(item) },
      orElse = { null }
  )

  companion object {
    @Suppress("EXPERIMENTAL_API_USAGE")
    fun <T : Any> empty(): RecyclerData<T> =
      RecyclerData(Config(), emptyList<T>().toDataSource(), null)
  }
}
