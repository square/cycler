package com.squareup.cycler

import android.view.View
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.squareup.cycler.SwipeDirection.Companion.BOTH
import com.squareup.cycler.SwipeDirection.Companion.BOTH_ABSOLUTE
import com.squareup.cycler.SwipeDirection.END
import com.squareup.cycler.SwipeDirection.LEFT
import com.squareup.cycler.SwipeDirection.RIGHT
import com.squareup.cycler.SwipeDirection.START
import java.util.EnumSet
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests the basic algoritms that translate swipe movement into our API values.
 */
@RunWith(RobolectricTestRunner::class)
class SwipeUnderviewTests {

  private fun createSwipe(
    width: Int,
    dX: Float,
    layoutDirection: Int,
    allowedDirections: Set<SwipeDirection>
  ): SwipeBindData<Int> {
    val swipedView = mock<View>()
    whenever(swipedView.layoutDirection).thenReturn(layoutDirection)
    whenever(swipedView.width).thenReturn(width)
    return SwipeBindData(
        dataItem = 1,
        swipedView = swipedView,
        allowedDirections = allowedDirections
    ).apply { bind(dX) }
  }

  @Test
  fun both_and_right() {
    val data = createSwipe(
        width = 100,
        dX = 10f,
        layoutDirection = View.LAYOUT_DIRECTION_LTR,
        allowedDirections = BOTH
    )
    assertThat(data.isValid).isTrue()
    assertThat(data.direction).isEqualTo(SwipeDirection.END)
    assertThat(data.percentage).isEqualTo(.1f)
  }

  @Test
  fun both_and_right_rtl() {
    val data = createSwipe(
        width = 100,
        dX = 10f,
        layoutDirection = View.LAYOUT_DIRECTION_RTL,
        allowedDirections = BOTH
    )
    assertThat(data.isValid).isTrue()
    assertThat(data.direction).isEqualTo(SwipeDirection.START)
    assertThat(data.percentage).isEqualTo(.1f)
  }

  @Test
  fun start_and_ltr() {
    val data = createSwipe(
        width = 100,
        dX = -5f,
        layoutDirection = View.LAYOUT_DIRECTION_LTR,
        allowedDirections = EnumSet.of(START)
    )
    assertThat(data.isValid).isTrue()
    assertThat(data.direction).isEqualTo(START)
    assertThat(data.percentage).isEqualTo(.05f)
  }

  @Test
  fun left_and_rtl() {
    val data = createSwipe(
        width = 100,
        dX = -5f,
        layoutDirection = View.LAYOUT_DIRECTION_RTL,
        allowedDirections = EnumSet.of(LEFT)
    )
    assertThat(data.isValid).isTrue()
    assertThat(data.direction).isEqualTo(LEFT)
    assertThat(data.percentage).isEqualTo(.05f)
  }

  @Test
  fun both_absolute_and_trl() {
    val data = createSwipe(
        width = 100,
        dX = 5f,
        layoutDirection = View.LAYOUT_DIRECTION_RTL,
        allowedDirections = BOTH_ABSOLUTE
    )
    assertThat(data.isValid).isTrue()
    assertThat(data.direction).isEqualTo(RIGHT)
    assertThat(data.percentage).isEqualTo(.05f)
  }

  @Test
  fun zero_width() {
    val data = createSwipe(
        width = 0,
        dX = -5f,
        layoutDirection = View.LAYOUT_DIRECTION_LTR,
        allowedDirections = BOTH
    )
    assertThat(data.isValid).isFalse()
  }

  @Test
  fun invalid_width() {
    val data = createSwipe(
        width = -1,
        dX = -5f,
        layoutDirection = View.LAYOUT_DIRECTION_LTR,
        allowedDirections = BOTH
    )
    assertThat(data.isValid).isFalse()
  }

  @Test
  fun disallowed_direction() {
    val data = createSwipe(
        width = 100,
        dX = 5f,
        layoutDirection = View.LAYOUT_DIRECTION_LTR,
        allowedDirections = EnumSet.of(LEFT)
    )
    assertThat(data.isValid).isFalse()
  }

  @Test
  fun prefers_relative_direction() {
    val data = createSwipe(
        width = 100,
        dX = -5f,
        layoutDirection = View.LAYOUT_DIRECTION_LTR,
        allowedDirections = EnumSet.of(LEFT, START)
    )
    assertThat(data.isValid).isTrue()
    assertThat(data.direction).isEqualTo(START)
    assertThat(data.percentage).isEqualTo(.05f)

    val data2 = createSwipe(
        width = 100,
        dX = -5f,
        layoutDirection = View.LAYOUT_DIRECTION_RTL,
        allowedDirections = EnumSet.of(LEFT, END)
    )
    assertThat(data2.isValid).isTrue()
    assertThat(data2.direction).isEqualTo(END)
    assertThat(data2.percentage).isEqualTo(.05f)
  }

  @Test
  fun percentage_caps_at_100() {
    val data = createSwipe(
        width = 100,
        dX = -150f,
        layoutDirection = View.LAYOUT_DIRECTION_LTR,
        allowedDirections = BOTH
    )
    assertThat(data.isValid).isTrue()
    assertThat(data.direction).isEqualTo(START)
    assertThat(data.percentage).isEqualTo(1f)

    val data2 = createSwipe(
        width = 100,
        dX = 150f,
        layoutDirection = View.LAYOUT_DIRECTION_RTL,
        allowedDirections = BOTH
    )
    assertThat(data.isValid).isTrue()
    assertThat(data.direction).isEqualTo(START)
    assertThat(data.percentage).isEqualTo(1f)
  }
}
