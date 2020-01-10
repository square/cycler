package com.squareup.cycler

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MutationMapTest {
  @Test
  fun testEmpty() {
    val map = MutationMap()
    for (i in 0..10) {
      assertThat(map[i]).isEqualTo(i)
    }
  }

  @Test
  fun test1Move() {
    val map = MutationMap()
    map.move(1, 2)
    assertThat(map[0]).isEqualTo(0)
    assertThat(map[1]).isEqualTo(2)
    assertThat(map[2]).isEqualTo(1)
    assertThat(map[3]).isEqualTo(3)
  }

  @Test
  fun testMove_acrossMultiplePositions() {
    val map = MutationMap()
    // drag from 5 to 0
    map.move(5, 0)
    // final list should be 501234
    assertThat(map[0]).isEqualTo(5)
    assertThat(map[1]).isEqualTo(0)
    assertThat(map[2]).isEqualTo(1)
    assertThat(map[3]).isEqualTo(2)
    assertThat(map[4]).isEqualTo(3)
    assertThat(map[5]).isEqualTo(4)
  }

  @Test
  fun testDistantSwapOnEmpty() {
    // tests that really big indexes are ok (the internal structure grows always ok).
    val map = MutationMap()
    map.move(1, 1000)
    assertThat(map[0]).isEqualTo(0)
    assertThat(map[1]).isEqualTo(2)
    assertThat(map[2]).isEqualTo(3)
    assertThat(map[1000]).isEqualTo(1)
  }

  @Test fun testRemove() {
    val map = MutationMap(7)
    map.remove(1)
    assertThat(map[6]).isEqualTo(6)
    assertThat(map[5]).isEqualTo(6)
    assertThat(map[4]).isEqualTo(5)
    assertThat(map[3]).isEqualTo(4)
    assertThat(map[2]).isEqualTo(3)
    assertThat(map[1]).isEqualTo(2)
    assertThat(map.size).isEqualTo(6)
  }
}
