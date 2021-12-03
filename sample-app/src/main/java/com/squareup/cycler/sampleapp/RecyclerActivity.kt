package com.squareup.cycler.sampleapp

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView

class RecyclerActivity : AppCompatActivity(R.layout.main_activity), OnItemSelectedListener {

  private val pages = arrayOf(SimplePage, MutationsPage(), GridPage, GridMutationsPage())

  override fun onNothingSelected(parent: AdapterView<*>?) = Unit

  override fun onItemSelected(
    parent: AdapterView<*>?,
    view: View?,
    position: Int,
    id: Long
  ) {
    optionsContainer.removeAllViews()
    val page = pages[position]
    page.options.forEach { (label, action) ->
      CheckBox(this).apply {
        text = label
        setOnCheckedChangeListener { _, isChecked ->
          action(isChecked)
        }
      }.also(optionsContainer::addView)
    }
    recyclerView.adapter = null
    pages[position].config(recyclerView)
  }

  private lateinit var optionsContainer: LinearLayout
  private lateinit var recyclerView: RecyclerView

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    optionsContainer = findViewById(R.id.options)
    recyclerView = findViewById(R.id.recycler_view)

    findViewById<Spinner>(R.id.page_selection).apply {
      adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, pages)
      setSelection(0)
      onItemSelectedListener = this@RecyclerActivity
    }
  }
}
