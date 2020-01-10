package com.squareup.cycler

import androidx.recyclerview.widget.DiffUtil

/** Implements Android's DiffUtil.Callback (change comparison) based on an [ItemComparator]. */
class DataSourceDiff<T>(
  private val helper: ItemComparator<T>,
  private val oldList: DataSource<T>,
  private val newList: DataSource<T>
) : DiffUtil.Callback() {

  override fun getOldListSize(): Int = oldList.size
  override fun getNewListSize(): Int = newList.size

  override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
    helper.areSameIdentity(oldList[oldItemPosition], newList[newItemPosition])

  override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
    helper.areSameContent(oldList[oldItemPosition], newList[newItemPosition])

  /** If we ever need payload we should accept a [Recycler.Config.bind] lambda including payload. */
  override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int) = null
}

/**
 * Helps implement a [DiffUtil.Callback] but without having access to the full lists, just the two
 * items being compared at each time. The developer won't have to deal with the source list.
 * Think of it as an `Comparator<T>`.
 */
interface ItemComparator<in T> {
  fun areSameIdentity(oldItem: T, newItem: T): Boolean
  fun areSameContent(oldItem: T, newItem: T): Boolean
  /** No need to implement, as we are not passing the payload to the bind lambdas anyway. */
  fun getChangePayload(oldItem: T, newItem: T): Any? = null
}

/** Returns a ItemComparator based on a Comparator object. */
fun <T> itemComparatorFor(comparator: Comparator<T>) = ComparatorItemComparator<T>(comparator)

/** Returns a ItemComparator based on the Comparable type. */
fun <T : Comparable<T>> naturalItemComparator() = ComparatorItemComparator<T>(comparator())

/** Creates a Comparator<T> based on a Comparable<T>. Useful for using in [naturalItemComparator]. */
fun <T : Comparable<T>> comparator() = Comparator<T> { o1, o2 -> o1.compareTo(o2) }

/**
 * Implements ItemComparator based on a Comparator. It assumes equality is "same object".
 * Therefore areSameContent is only true if it's the same object. And getChangePayload won't be used
 * (if same object -> same content -> no call to getChangePayload).
 * @see Recycler.Config.itemComparator
 */
class ComparatorItemComparator<T>(
  private val comparator: Comparator<T>
) : ItemComparator<T> {
  override fun areSameIdentity(oldItem: T, newItem: T) = comparator.compare(oldItem, newItem) == 0
  override fun areSameContent(oldItem: T, newItem: T) = areSameIdentity(oldItem, newItem)
}

internal class DefaultItemComparator<I>(
  val idsProvider: StableIdProvider<I>,
  val contentComparator: ContentComparator<I>
) : ItemComparator<I> {
  override fun areSameIdentity(
    oldItem: I,
    newItem: I
  ): Boolean {
    return idsProvider(oldItem) == idsProvider(newItem)
  }

  override fun areSameContent(
    oldItem: I,
    newItem: I
  ): Boolean {
    return contentComparator(oldItem, newItem)
  }
}
