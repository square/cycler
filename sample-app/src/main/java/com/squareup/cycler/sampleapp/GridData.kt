package com.squareup.cycler.sampleapp

import com.squareup.cycler.sampleapp.BaseItem.Item

fun gridSampleData() = (1..20).map {
  Item(it, "Product #$it", (it * 13.73).rem(100).toFloat(), 1)
}