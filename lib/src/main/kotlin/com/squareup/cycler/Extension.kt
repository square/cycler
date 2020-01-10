package com.squareup.cycler

/**
 * Extensions (plugins) to [Recycler] use a spec and a runtime object.
 * In the same way that [Recycler.Config] is the specification and Recycler is the runtime object...
 * [ExtensionSpec] is the specification object, with all the options the developers can touch.
 * It will be included inside [Recycler.Config].
 * [Extension] is the runtime object that will work as a plugin alongside [Recycler].
 * For instance: Sticky Headers.
 * - The Spec implementation will collect the lambdas to group objects, to create views, etc.
 * - The runtime implementation will configure the sticky headers decoration.
 */
interface Extension<ItemType : Any> {
  /**
   * This method is called when the runtime extension object is attached to the runtime [Recycler]
   * object. This method implementation will be able to get references to the particular
   * [RecyclerView], the [Context], and plug decorations and other configurations.
   */
  fun attach(recycler: Recycler<ItemType>)

  /**
   * This data is set every time the data item list changes. Extensions will probably need access
   * to this data to know the size of data, to derive objects (like the headers for a list) or to
   * get the right configuration for each item (think edges defined differently for each row type).
   */
  var data: RecyclerData<ItemType>
}
