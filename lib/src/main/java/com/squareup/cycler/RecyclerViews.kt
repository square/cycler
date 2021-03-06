package com.squareup.cycler

import android.content.Context
import android.view.View
import androidx.annotation.IdRes
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.LayoutManager
import com.squareup.cycler.Recycler.Companion.create
import com.squareup.cycler.Recycler.Config

/**
 * Factory method to create a Recycler by finding a [RecyclerView] in this view.
 *
 * The [block] is a lambda on [Recycler.Config] specifying all row types
 * and extra options. Same as [create] but it takes an already existing [RecyclerView].
 */
inline fun <I : Any> View.adoptRecyclerById(
  @IdRes recyclerId: Int,
  noinline layoutProvider: ((Context) -> LayoutManager)? = null,
  crossinline block: Config<I>.() -> Unit
): Recycler<I> = Recycler.adopt(findViewById(recyclerId), layoutProvider, block)
