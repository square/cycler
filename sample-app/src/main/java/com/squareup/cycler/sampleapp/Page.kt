package com.squareup.cycler.sampleapp

import androidx.recyclerview.widget.RecyclerView

interface Page {
  val options: List<Pair<String, (Boolean) -> Unit>>
  fun config(recyclerView: RecyclerView)
}
