package com.squareup.cycler

/**
 * If you happened to have a custom implementation of [DataSource] you can
 * subclass [AbstractList] which has the same abstract API surface.
 *
 * Ex:
 * fun <T> Array<T>.toDataSource(): DataSource<T> {
 *   return object : AbstractList<T>() {
 *     override fun get(i: Int): T = this@toDataSource[i]
 *     override val size get() = this@toDataSource.size
 *   }
 * }
 */
@Deprecated(message = "DataSource is now just an alias for List and will be removed in the future.")
typealias DataSource<T> = List<T>

@Deprecated(
  message = "Converting to DataSource is no longer necessary.",
  replaceWith = ReplaceWith("this")
)
fun <T> List<T>.toDataSource(): List<T> = this

@Deprecated(
  message = "Converting to DataSource is no longer necessary.",
  replaceWith = ReplaceWith("asList()")
)
fun <T> Array<T>.toDataSource(): List<T> = asList()
