package com.squareup.cycler

/**
 * DataSource that can mutate items (swap, copy, delete) using a [MutationMap].
 * The contract for DataSource disallow for underneath data changes, so this class should not
 * be used externally.
 */
internal class MutableDataSource<T>(
  private val originalDataSource: DataSource<T>
) : AbstractList<T>() {
  private val mutationMap = MutationMap(originalDataSource.size)
  override fun get(index: Int) = originalDataSource[mutationMap[index]]
  override val size get() = mutationMap.size
  fun move(from: Int, to: Int) = mutationMap.move(from, to)
  fun remove(index: Int) = mutationMap.remove(index)
  fun copyMutationMap() = MutationMap(mutationMap)
}
