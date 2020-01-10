package com.squareup.cycler

import kotlin.math.max

/**
 * Represents a mapping for mutating positions: maps a current position to an original position.
 * The initial state doesn't have any mutations and it will return the same position as provided.
 * When mutations are added that is tracked so following calls to [get] will return the
 * original position for the item in the requested position.
 *
 * For instance:
 * List: A, B, C, D, E (not stored here, just for visualization).
 *
 * Swap: 1 <-> 2
 * New List: A, C, B, D, E
 *
 * mutationMap[1] returns 2 // so it's C in the original list.
 * mutationMap[2] returns 1 // so it's B in the original list.
 */
class MutationMap() {

  constructor(size: Int) : this() {
    this.size = size
  }

  /** Clones a MutationMap. */
  constructor(src: MutationMap) : this() {
    positions = src.positions.clone()
  }

  // This could be a SparseIntArray
  // but it's an Android class and I got the "Stub!" error (lack of robolectric runner?).
  // Also I'm not very excited about it needing to shift part of the array each time a key is inserted.
  private var positions = EMPTY_ARRAY
  var size: Int = 0

  operator fun get(i: Int) = if (i >= positions.size) i else positions[i]

  fun move(from: Int, to: Int) {
    if (from < to) {
      for (i in from until to) {
        swap(i, i + 1)
      }
    } else {
      for (i in from downTo to + 1) {
        swap(i, i - 1)
      }
    }
  }

  fun remove(index: Int) {
    ensureSize(size)
    positions.copyInto(positions, index, index + 1, size)
    size--
  }

  private fun swap(a: Int, b: Int) {
    ensureSize(max(a, b))
    val aux = positions[a]
    positions[a] = positions[b]
    positions[b] = aux
  }

  private fun ensureSize(pos: Int) {
    val requiredSize = pos + 1
    if (positions.size < requiredSize) {
      // We could be more conservative for large numbers and avoid doubling the initialSize.
      // But that sounds like micro optimization. We only store an int for each needed position.
      // Which means 2 ints for each needed position in the worst-case.
      val nextLogicalSize = if (positions.isEmpty()) MINIMUM_SIZE else positions.size * 2
      val newSize = max(nextLogicalSize, requiredSize)
      val newPositions = IntArray(newSize)
      positions.copyInto(newPositions)
      for (i in positions.size until newSize) {
        newPositions[i] = i
      }
      positions = newPositions
    }
  }
}

private const val MINIMUM_SIZE = 16
private val EMPTY_ARRAY = intArrayOf()
