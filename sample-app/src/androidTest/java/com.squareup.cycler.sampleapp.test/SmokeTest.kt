package com.squareup.cycler.sampleapp.test

import androidx.test.rule.ActivityTestRule
import com.squareup.cycler.sampleapp.RecyclerActivity
import org.junit.Test

class SmokeTest {

  val rule = ActivityTestRule(
      RecyclerActivity::class.java,
      /* initialTouchMode */ true,
      /*launchActivity*/ false
  )

  @Test
  fun startApp() {
    val activity = rule.launchActivity(null)
    activity.finish()
  }
}
