package com.squareup.cycler

/**
 * Very minimal interface for the item list.
 * You can use extensions [Array.toDataSource] and [List.toDataSource].
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
