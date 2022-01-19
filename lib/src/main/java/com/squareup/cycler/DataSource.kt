package com.squareup.cycler

/**
 * Very minimal interface for the item list.
 * You can use extensions [Array.toDataSource] and [List.toDataSource].
 *
 * The [DataSource] is preferred over a bare list because it doesn't implement comparison-by-value
 * and prevents an exhaustive (and potentially costly) full scan on comparison with other data
 * sources. This does not happen in Cycler code (except for the proper diffing of the updated
 * source) but it might be an issue for client code comparing these objects and/or container data
 * classes.
 */
interface DataSource<out T> {
  operator fun get(i: Int): T
  val size: Int
  val isEmpty: Boolean
    get() = size == 0
}

fun <T> List<T>.toDataSource(): DataSource<T> {
  return object : DataSource<T> {
    override fun get(i: Int): T = this@toDataSource[i]
    override val size get() = this@toDataSource.size
  }
}

fun <T> Array<T>.toDataSource(): DataSource<T> {
  return object : DataSource<T> {
    override fun get(i: Int): T = this@toDataSource[i]
    override val size get() = this@toDataSource.size
  }
}
