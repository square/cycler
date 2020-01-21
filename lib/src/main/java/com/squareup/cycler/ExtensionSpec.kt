package com.squareup.cycler

/**
 * Configuration object for an [Extension]. See [Extension] documentation for details.
 * In the same way [Recycler.Config] is the configuration for the [Recycler], [ExtensionSpec] is the
 * configuration for the [Extension] runtime object.
 * Normally it will be added to the [Recycler.Config] through an extension method as
 * `fun [Config.stickyHeaders]` which creates the Spec object and calls [Config.extension] to add it.
 * @param I type for the recycler data items (the common type).
 */
interface ExtensionSpec<I : Any> {
  /**
   * Any extension spec (created/added with domain-specific extensions like
   * `fun Config.stickyHeaders(...)`. This method is called when the [Recycler] runtime object is
   * created and we need to convert this extension specification into an extension runtime.
   * The [Extension] returned will be the object configuring the add-ons. For example sticky headers
   * will add the sticky headers decoration and configure it according to the sticky headers spec.
   */
  fun create(): Extension<I>
}
