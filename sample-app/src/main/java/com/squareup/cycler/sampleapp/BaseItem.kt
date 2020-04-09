package com.squareup.cycler.sampleapp

sealed class BaseItem {

  abstract val amount: Float

  data class Item(
    val id: Int,
    val name: String,
    val price: Float,
    val quantity: Int
  ) : BaseItem() {
    override val amount = price * quantity
  }

  data class Discount(
    val id: Int,
    override val amount: Float,
    val isStarred: Boolean = false
  ): BaseItem() {
    init { require(amount < 0f) }
  }
}

data class GrandTotal(val total: Float)
