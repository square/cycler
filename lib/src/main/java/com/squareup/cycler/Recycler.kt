package com.squareup.cycler

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.LayoutManager
import com.squareup.cycler.Recycler.Config
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

typealias StableIdProvider<I> = (I) -> Long
typealias ContentComparator<I> = (I, I) -> Boolean

@DslMarker
annotation class RecyclerApiMarker

/**
 * Represents a [RecyclerView].
 *
 * It provides the methods to access:
 * - val view, to add it wherever you need.
 * - var data, to refresh the recycler view.
 * - var extraItem, to refresh the extra item -like "loading" or "no results".
 * How to use:
 * ```
 * val recycler = Recycler.create<Fruits> {
 *
 *   // Item identified by sub-type (Apple) and arbitrary forItemsWhere.
 *   row<Apple> {
 *     forItemsWhere { it.color == GREEN }
 *     create {
 *       view = // build something
 *       val nameField = view.findById(...)
 *       bind { apple ->
 *         view.image = apple.image
 *         subView.text = apple.name
 *       }
 *     }
 *   }
 *
 *   // item identified by sub-type (Apple) and arbitrary forItemsWhere.
 *   row<Apple> {
 *     forItemsWhere { ... } // if omitted it will match all apples not in the previous row.
 *     create {
 *       view = // build something
 *       val nameField = view.findViewById(...)
 *       bind { apple ->
 *         view.image = apple.image
 *         subView.text = apple.name
 *       }
 *     }
 *   }
 *
 *   // item identified only by sub-type, using an inflated layout.
 *   row<Banana> {
 *     create(R.layout.minion) {
 *       // still you can get references to inflated views.
 *       val numberField = view.findViewById(R.id.number)
 *       val stateField = view.findViewById(R.id.state)
 *       // and use them in bind for maximum performance.
 *       bind { banana ->
 *         numberField.text = "${banana.number}"
 *         stateField.text = banana.state
 *       }
 *     }
 *   }
 *
 *   // specifies how to show the extraItem when it's the "no results" one.
 *   // This will be shown when `recycler.extraItem = NoResults("tropical fruits")`
 *   // ExtraItems can have data and be bound: imagine NoResults has a category property.
 *   extraItem<NoResults, FooterView> {
 *     create(R.layout.no_results_message_view) {
 *       bind { item ->
 *         view.title = "No ${item.category} found"
 *       }
 *     }
 *   }
 *
 *   // specifies how to show the extraItem when it's the "loading more" one
 *   // This will be shown when `recycler.extraItem = LoadingMore()`
 *   extraItem<LoadingMore, Footer> {
 *     create { context ->
 *       view = TextView(context).apply { text = "Loading more..." }
 *       bind { /* no bind needed for a fixed message */ }
 *     }
 *   }
 * }
 * ```
 *
 * Diffing:
 *
 * If an ItemComparator is provided (or both stableIds and compareItemsContent are implemented)
 * the recycler view data will be diff'ed (see Android's DiffUtil) instead of replaced all at once.
 * That diffing will happen in background keeping the UI thread light. That means that when you
 * set the [data] property the items shown might still be from the previous list.
 *
 * In the unlikely case you want to provide a different CoroutineDispatcher to process the diffing
 * you can set it through the [Config.backgroundDispatcher] property.
 */
class Recycler<I : Any> internal constructor(
  val view: RecyclerView,
  private val config: Config<I>
) {
  private val extensions = config.extensionSpecs.map(ExtensionSpec<I>::create)
  private val adapter = Adapter(CreatorContext(this), config)

  inline fun <reified T : Any> extension() = extension(T::class.java)

  @PublishedApi internal fun <T : Any> extension(type: Class<T>): T? {
    @Suppress("UNCHECKED_CAST")
    return extensions.firstOrNull(type::isInstance) as? T
  }

  /**
   * Passed to [StandardRowSpec.Creator] to access runtime Recycler object (for further configuration).
   * Example: Drag and Drop handle set up. It needs to access [Extension] objects.
   * @param recycler it's a Recycler<*> (any ItemType) to be able to use it with extraItem creators.
   * If a Recycler<I> used a CreatorContext<I> with Recycler<I> it wouldn't be accepted by the
   * [RowSpec]s used for extra items (defined as RowSpec for ItemType = Any). Anyway the ItemType is
   * not needed here.
   */
  class CreatorContext(val recycler: Recycler<*>) {
    val context: Context get() = recycler.view.context

    /** Returns a runtime [Extension] (not the spec). */
    inline fun <reified T : Any> extension(): T? = recycler.extension()
  }

  fun clear() = update {
    data = listOf<I>().toDataSource()
    extraItem = null
  }

  /**
   * Property to assign the concrete data for the recycler.
   * @deprecated Use [update].
   */
  var data: DataSource<I>
    get() = adapter.currentRecyclerData.data
    set(value) {
      update { data = value }
    }

  /**
   * Property to assign the concrete data for the extra item.
   * @deprecated Use [update].
   */
  var extraItem: Any?
    get() = adapter.currentRecyclerData.extraItem
    set(value) {
      update { extraItem = value }
    }

  /** Represents the last Update object that is being diffed and will be applied. */
  private var currentUpdate: Update<I>? = null

  // This scope will automatically get its own job.
  private val mainScope = CoroutineScope(config.mainDispatcher)
  private val backgroundContext = mainScope.coroutineContext + config.backgroundDispatcher

  /**
   * Use this method to update the [RecyclerData] (data+extraItem) at once.
   * It also provides callbacks for when it's updated (or cancelled by another concurrent update).
   */
  fun update(block: Update<I>.() -> Unit) {
    val newUpdate = currentUpdate?.let {
      Update(it)
    } ?: Update(adapter.currentRecyclerData)
    newUpdate.apply(block)
    // This tells which update is the last one, the one that will be applied in case of race.
    currentUpdate = newUpdate
    mainScope.launch {
      // This might not suspend if there's no item comparator and a general refresh is done.
      // That's why we pass the backgroundContext for [generateDataChangesLambdas] to decide.
      val notifications = newUpdate.generateDataChangesLambdas(
          adapter.itemComparator,
          backgroundContext
      )
      // If the change is still valid (no other call wants to update it).
      if (currentUpdate == newUpdate) {
        // Update the adapter and extensions' data.
        val newRecyclerData = newUpdate.createNewRecyclerData(config)
        adapter.currentRecyclerData = newRecyclerData
        extensions.forEach { it.data = newRecyclerData }
        // Tell adapter its data changed.
        notifications.forEach { it(adapter) }
        if (view.adapter == null) {
          // Only sets view's adapter when first data is ready. Otherwise,
          // a bogus empty adapter set for some frames will consume saved state (scroll position).
          view.adapter = adapter
        }
        currentUpdate = null
        newUpdate.onReady.invoke()
      } else {
        newUpdate.onCancelled.invoke()
      }
    }
  }

  /**
   * Re binds an item (if visible) without the need of re-setting the data.
   * That is helpful when binding might be affected by other factors besides the data itself.
   * Example: a list of checks/radios. The "selectedItem" value probably is not part of the data.
   * So we set the checked property to true if that particular item == selectedItem. When selection
   * changes we will need to re-bind those affected.
   * @param position the position in [data] of the item that needs to be re-bound (if visible).
   */
  fun refresh(position: Int) {
    adapter.notifyItemChanged(position)
  }

  /**
   * Same as [refresh(Int)] but for a range. It will refresh items in the range `[from, until)`.
   */
  fun refresh(
    from: Int,
    until: Int
  ) {
    require(from <= until) { "From ($from) cannot be greater than until ($until)" }
    adapter.notifyItemRangeChanged(from, until - from)
  }

  init {
    extensions.forEach { it.attach(this) }
  }

  /*
   * The rest of the classes are included inside [Recycler] to provide name-spacing.
   * They are not part of Recycler's workings.
   * Building phase: [Config], [RowSpec]
   * Running phase: [Adapter], [ViewHolder]
   */
  @RecyclerApiMarker
  class Config<I : Any> @PublishedApi internal constructor() {

    /**
     * Returns the extension spec related to the particular row.
     * It should be used only from running extensions ([Extension]) to get the proper row-type
     * configuration for a given item. For example: [EdgesExtension.Provider].
     * General method which is called from Extension code. DON'T USE DIRECTLY.
     * @hide
     */
    @PublishedApi internal inline fun <reified T : Any> rowExtension(item: I): T? {
      val rowSpec = rowSpecs.first { it.matches(item) }
      return rowSpec.extension()
    }

    /**
     * Returns the extension spec related to the extra Item.
     * It should be used only from running extensions ([Extension]) to get the proper row-type
     * configuration for a given item. For example: [EdgesExtension.Provider].
     */
    @PublishedApi internal inline fun <reified T : Any> extraRowExtension(item: Any): T? {
      val rowSpec = extraItemSpecs.first { it.matches(item) }
      return rowSpec.extension()
    }

    @PublishedApi internal val rowSpecs = mutableListOf<RowSpec<I, *, *>>()
    @PublishedApi internal val extraItemSpecs = mutableListOf<RowSpec<Any, *, *>>()
    internal val extensionSpecs = mutableListOf<ExtensionSpec<I>>()

    internal var hasStableIdProvider = false
    internal var stableIdProvider: StableIdProvider<I> = {
      throw java.lang.IllegalStateException("stableIdProvider not set.")
    }
    internal var contentComparator: ContentComparator<I>? = null

    /**
     * Must be set to the [CoroutineDispatcher] to use for the main thread.
     */
    var mainDispatcher: CoroutineDispatcher = Dispatchers.Main

    /**
     * You can set a different [CoroutineDispatcher] to handle the data diffing.
     * TODO: not ideal, not sure if creating an executor on demand or what...
     */
    var backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO

    /**
     * This will help "diff" the items and make only needed updates if you refresh the list.
     * If you provide a [stableIdProvider] and a [contentComparator] you don't need to set this.
     */
    var itemComparator: ItemComparator<I>? = null

    /** Configures a lambda that maps each item into a stable id. */
    fun stableId(block: StableIdProvider<I>) {
      stableIdProvider = block
      hasStableIdProvider = true
    }

    /** Configures a lambda that compares two items by content (all relevant content must be equal). */
    fun compareItemsContent(block: ContentComparator<I>) {
      contentComparator = block
    }

    /**
     * Most common row constructor: you indicate the item subtype with generics and pass a lambda on
     * the StandardRowSpec.
     */
    inline fun <reified S : I, reified V : View> row(
      crossinline specBlock: StandardRowSpec<I, S, V>.() -> Unit
    ) {
      row(
          StandardRowSpec<I, S, V> { it is S }.apply {
            specBlock()
          }
      )
    }

    /**
     * This builder receives creator and binder by separate.
     * This helps provide custom components extensions that can live outside the library.
     * [bindBlock] is just the bind-lambda. This is a shorthand for a [BinderRowSpec] when no
     * configuration is needed besides binding.
     */
    inline fun <reified S : I, reified V : View> row(
      noinline creatorBlock: (CreatorContext) -> V,
      noinline bindBlock: (Int, S, V) -> Unit
    ) {
      // Converts the bindBlock in a BinderRowSpec block.
      row<S, V>(creatorBlock) { -> bind(bindBlock) }
    }

    /**
     * This builder receives creator by separate. The second lambda (specBlock) runs on a
     * BinderRowSpec (instead of a `create {...}` method, a `bind {....}` method).
     * This helps provide custom components extensions that can live outside the library.
     * [specBlock] is a lambda that runs on a [BinderRowSpec].
     * It has all the methods but instead of `create` it only has `bind` (creation is provided).
     */
    inline fun <reified S : I, reified V : View> row(
      noinline creatorBlock: (CreatorContext) -> V,
      crossinline specBlock: BinderRowSpec<I, S, V>.() -> Unit
    ) {
      row(
          BinderRowSpec<I, S, V>(
              typeMatchBlock = { it is S },
              creatorBlock = creatorBlock
          ).apply { specBlock() }
      )
    }

    /**
     * Same as row but to define how to process the "extra" item, if any.
     */
    inline fun <reified S : Any, reified V : View> extraItem(
      crossinline specBlock: StandardRowSpec<Any, S, V>.() -> Unit
    ) {
      extraItem(
          StandardRowSpec<Any, S, V> { it is S }.apply {
            specBlock()
          }
      )
    }

    /**
     * Returns an ItemComparator following this rule:
     * - if a custom itemComparator was set, return that.
     * - if it has stable ids and a content comparator, create an ItemComparator based on that.
     * - else null.
     */
    internal fun createItemComparator(): ItemComparator<I>? {
      return itemComparator ?: stableIdProvider.let { ids ->
        contentComparator?.let { contentComp ->
          DefaultItemComparator(ids, contentComp)
        }
      }
    }

    fun row(rowSpec: RowSpec<I, *, *>) {
      rowSpecs.add(rowSpec)
    }

    @PublishedApi internal fun extraItem(rowSpec: RowSpec<Any, *, *>) {
      extraItemSpecs.add(rowSpec)
    }

    /**
     * General method which is called from Extension code. DON'T USE DIRECTLY.
     * @hide
     */
    fun extension(spec: ExtensionSpec<I>) {
      extensionSpecs.add(spec)
    }

    /**
     * Public method only for inlining, don't use directly. Use [Recycler.create] instead.
     * @hide
     */
    @PublishedApi internal fun setUp(view: RecyclerView): Recycler<I> {
      return Recycler(view, this)
    }
  }

  /**
   * The adapter implementation for the RecyclerView.
   * @param I type for the recycler data items (the common type).
   */
  private class Adapter<I : Any>(
    val creatorContext: CreatorContext,
    val config: Config<I>
  ) : RecyclerView.Adapter<ViewHolder<View>>() {

    private val stableIdProvider: StableIdProvider<I>
    internal val itemComparator: ItemComparator<I>?

    init {
      setHasStableIds(config.hasStableIdProvider)
      stableIdProvider = config.stableIdProvider

      itemComparator = config.createItemComparator()
    }

    var currentRecyclerData: RecyclerData<I> = RecyclerData.empty()

    override fun onCreateViewHolder(
      parent: ViewGroup,
      viewType: Int
    ): ViewHolder<View> {
      return if (viewType < 0) {
        // TODO (DESIGNSYS-388): Move extra item's index magic to methods.
        val extraRowSpec = config.extraItemSpecs[-(viewType + 1)]
        extraRowSpec.createViewHolder(creatorContext, 0)
      } else {
        val rowType = viewType.rowType()
        val subType = viewType.subType()
        config.rowSpecs[rowType].createViewHolder(creatorContext, subType)
      }
    }

    override fun getItemViewType(position: Int): Int {
      // Extra item types are negative (-1 to -N).
      return currentRecyclerData.forPosition(
          position,
          onDataItem = { dataItem ->
            val rowSpecIndex = config.rowSpecs.indexOfFirst { it.matches(dataItem) }
            require(rowSpecIndex >= 0) {
              "Data item type not found. Check row definitions. Item: $dataItem"
            }
            val subType = config.rowSpecs[rowSpecIndex].subType(position, dataItem)
            makeViewType(rowSpecIndex, subType)
          },
          onExtraItem = { extraItem ->
            val extraRowSpecIndex = config.extraItemSpecs.indexOfFirst { it.matches(extraItem) }
            require(extraRowSpecIndex >= 0) {
              "Extra item type not found. Check extra row definitions. Item: $extraItem"
            }
            -(1 + extraRowSpecIndex)
          },
          orElse = {
            throw IllegalStateException("getItemViewType for invalid position ($position)")
          }
      )
    }

    /**
     * We encode the viewType integer for RecyclerView as rowType (RowSpec index) + subType.
     * That way RecyclerView knows which views are compatible with each other even if each RowSpec
     * produces several types of views.
     * The 8 least significant bits are the subType. The rest are the rowType.
     * `viewType [ ....... rowType (24) / subType (8) ]`.
     */
    private fun makeViewType(
      rowType: Int,
      subType: Int
    ) = rowType shl 8 or subType

    /**
     * Extracts the rowType from the viewType the RecyclerView handles.
     * @see makeViewType
     */
    private fun Int.rowType() = this shr 8

    /**
     * Extracts the subType from the viewType the RecyclerView handles.
     * @see makeViewType
     */
    private fun Int.subType() = this and 0xff

    override fun getItemCount(): Int = currentRecyclerData.totalCount

    override fun onBindViewHolder(
      holder: ViewHolder<View>,
      position: Int
    ) {
      val dataItem = currentRecyclerData.forPosition(
          position,
          onDataItem = { it },
          onExtraItem = { it },
          orElse = {
            throw IllegalStateException("onBindViewHolder for invalid position ($position)")
          }
      )
      holder.bind(position, dataItem)
    }

    override fun getItemId(position: Int): Long {
      return currentRecyclerData.forPosition(
          position,
          onDataItem = { stableIdProvider(it) },
          onExtraItem = { Long.MIN_VALUE },
          orElse = { throw IllegalStateException("getItemId for invalid position ($position)") }
      )
    }
  }

  /**
   * Base type for all the builders for rows (like in `row { ... }` idioms).
   * It providers the common configuration for rows: what type of items to process. If there's more
   * configuration for rows (like what edges to paint, events listeners to hook, etc) that should be
   * added here.
   * There are two concrete classes:
   * - [StandardRowSpec] offers a `create` method to let the developer create/bind the view.
   * - [BinderRowSpec] represents a fixed creation and offer a `bind' method to bind to it. It's useful
   *   for defining extensions for common components. Or app-specific rows.
   */
  @RecyclerApiMarker
  abstract class RowSpec<I : Any, out S : I, out V : View>(
    private val typeMatchBlock: (I) -> Boolean
  ) {
    private var itemTypeBlock: (S) -> Boolean = { true }
    @PublishedApi internal val extensions = mutableMapOf<Class<*>, Any>()

    /**
     * Returns the proper row extension, or null if not present.
     * Public to be used from features extensions. DON'T USE DIRECTLY.
     */
    inline fun <reified T> extension(): T? = extensions[T::class.java] as T?

    /**
     * Returns the proper row extension, creating it with the lambda if not present.
     * Public to be used from features extensions. DON'T USE DIRECTLY.
     */
    inline fun <reified T : Any> extension(createLambda: () -> T): T {
      return extensions.getOrPut(T::class.java, createLambda) as T
    }

    fun forItemsWhere(block: (S) -> Boolean) {
      itemTypeBlock = block
    }

    // Alternative to unchecked cast here: have some Kt1.3 requires that ensures it's S.
    // Or receiving the class and either using isInstance... or reifying this class.
    // But it seems it could add a lot of code. We know the typeMatchBlock will do the right thing.
    @Suppress("UNCHECKED_CAST")
    fun matches(any: I) = typeMatchBlock(any) && itemTypeBlock(any as S)

    /**
     * If the row spec has subtypes (it produces views that are incompatible / cannot adopt data from
     * other subtypes) this should return a different number. The default is 0 as that's only the case
     * for Mosaic-generated views.
     */
    open fun subType(
      index: Int,
      any: I
    ) = 0

    abstract fun createViewHolder(
      creatorContext: CreatorContext,
      subType: Int
    ): ViewHolder<V>
  }

  /** Each ViewHolder is created with its view and the bind block that knows how to bind to it. */
  abstract class ViewHolder<out V : View>(
    view: V
  ) : RecyclerView.ViewHolder(view) {
    abstract fun bind(
      index: Int,
      dataItem: Any
    )
  }

  companion object {
    /**
     * Factory method to create a Recycler.
     * The [block] is a lambda on
     * @param context context to create the [RecyclerView].
     * @param id id for the [RecyclerView]. Defaults to [View.NO_ID].
     * @param layoutProvider lambda generating a [RecyclerView.LayoutManager] for the view. If not
     * provided a [LinearLayoutManager] will be used.
     * @param block this is the lambda on [Recycler.Config] specifying all the content configuration.
     *
     */
    inline fun <I : Any> create(
      context: Context,
      id: Int = View.NO_ID,
      noinline layoutProvider: (Context) -> LayoutManager = { LinearLayoutManager(it) },
      crossinline block: Config<I>.() -> Unit
    ): Recycler<I> {
      val view = RecyclerView(context)
      view.id = id
      return adopt(view, layoutProvider, block)
    }

    /**
     * Factory method to create a Recycler.
     * The [block] is a lambda on [Recycler.Config] specifying all row types
     * and extra options. Same as [create] but it takes an already existing [RecyclerView].
     *
     * @param view the RecyclerView to adopt
     * @param layoutProvider a lambda that will create the layout manager. If omitted (null)
     * the RecyclerView needs to have a layout manager already assigned.
     * @param block a lambda on [Recycler.Config] including the configuration.
     */
    inline fun <I : Any> adopt(
      view: RecyclerView,
      noinline layoutProvider: ((Context) -> LayoutManager)? = null,
      crossinline block: Config<I>.() -> Unit
    ): Recycler<I> {
      layoutProvider?.invoke(view.context)
          .let { view.layoutManager = it }
      requireNotNull(view.layoutManager) {
        "RecyclerView needs a layoutManager assigned. " +
            "Assign one to the view, or pass a layoutProvider argument."
      }
      return Config<I>()
          .apply(block)
          .setUp(view)
    }
  }
}
