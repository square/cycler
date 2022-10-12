package com.squareup.cycler

import android.annotation.SuppressLint
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DiffUtil.DiffResult
import androidx.recyclerview.widget.RecyclerView.Adapter
import com.squareup.cycler.Recycler.Config
import com.squareup.cycler.Update.UpdateMode.Async
import com.squareup.cycler.Update.UpdateMode.Sync
import com.squareup.cycler.Update.UpdateType.Adding
import com.squareup.cycler.Update.UpdateType.NoOp
import com.squareup.cycler.Update.UpdateType.Populating
import com.squareup.cycler.Update.UpdateType.Replacing
import kotlin.properties.Delegates

/**
 * Holds the info for a call to [update]. This object is mutable to allow the caller to change it
 * with an Update.() -> Unit block but it won't mutate after that. It will be then (if needed)
 * passed to the async coroutine to process the diffing and calculate the changes, and returned back
 * to main thread to apply them (if the update is still valid).
 * If a second call to update happens this update object won't be mutated, but a new instance will
 * be created as a copy. Even if the coroutine is reading the old update object, we can read it as
 * well to create a safe copy for the new update. Copying it is important so the next call to update
 * will see the previous changes, even if we couldn't yet apply them to the UI.
 *
 * Update process:
 * - currentUpdate = new Update() with current data or copy of currentUpdate (to-be-applied data).
 * - call user's block on currentUpdate. Sets whatever they want.
 * - we now can asynchronously calculate the difference between currentData and currentUpdate
 *   (both objects will be immutable).
 * - when diffing is ready we check if currentUpdate is still the same, and apply changes.
 * - if currentUpdate change we let the other call (the one that changed it) to apply changes.
 * - That call would have got all our changes too as its "currentUpdate" was a copy of ours.
 */
class Update<I : Any>(private val oldRecyclerData: RecyclerData<I>) {

  init {
    // Nobody will change oldRecyclerData anymore.
    // The generated new RecyclerData (which should not be frozen) will be a different object.
    oldRecyclerData.frozen = true
  }

  /**
   * Controls the update behavior when populating a list (i.e. going from an empty dataset to a non-
   * empty dataset) for the first time.
   *
   * If set to [Sync], a call to [Recycler.update] will update the recycler view (send adapter
   * notifications) in the same call without posting / using coroutines.
   *
   * If set to [Async], a call to [Recycler.update] will update the recycler view by posting / using
   * coroutines. This isn't actually necessary when populating the list for the first time but it
   * mimics the previous behavior of this library.
   *
   * If no [itemComparator] is provided in your [Recycler.Config], initial dataset updates will
   * always be processed synchronously.
   *
   * Set to [Async] by default as, depending on the usage, calls to update might happen when the
   * recycler view is scrolling or laying out and recycler view throws an exception (and this
   * library cannot find out). Its use however is encouraged provided no calls to update are
   * triggered inside an onScrollChanged or a relayout pending (when showing/hiding a keyboard for
   * instance).
   */
  var populateMode: UpdateMode = Async

  /**
   * Controls the update behavior when replacing a list (i.e. going from a non-empty dataset to
   * another non-empty dataset). This covers each dataset update after the initial one.
   *
   * If set to [Sync], a call to [Recycler.update] will update the recycler view (send adapter
   * notifications) in the same call without posting / using coroutines.
   *
   * If set to [Async], a call to [Recycler.update] will process dataset changes in the background
   * using coroutines and post recycler view updates to the UI thread.
   *
   * [Async] incurs a 1-frame penalty when updating the list, so consider your use case and the size
   * of your dataset when choosing your [UpdateMode]. For smaller datasets, [Sync] may be preferable
   * but for very large datasets [Async] is generally recommended.
   *
   * If no [itemComparator] is provided in your [Recycler.Config], dataset updates will always be
   * processed synchronously.
   *
   * Set to [Async] by default as, depending on the usage, calls to update might happen when the
   * recycler view is scrolling or laying out and recycler view throws an exception (and this
   * library cannot find out). Its use however is encouraged provided no calls to update are
   * triggered inside an onScrollChanged or a relayout pending (when showing/hiding a keyboard for
   * instance).
   */
  var replaceMode: UpdateMode = Async

  // New values, initialized to the old ones.
  var data by Delegates.observable<DataSource<I>>(
    oldRecyclerData.data
  ) { _, _, _ -> addedChunks.clear() }
  var extraItem: Any? = oldRecyclerData.extraItem

  /**
   * Enabled by default, which allows item diffing to find changes in relative position. It may
   * be disabled for large data sets as an optimization if this functionality is not needed -
   * time complexity goes from N^2 to N when it is disabled.
   *
   * ```
   * recycler.update {
   *   detectMoves = false // Don't try to find changes in relative position.
   *   data = ...
   * }
   * ```
   */
  var detectMoves: Boolean = true

  private val addedChunks = mutableListOf<List<I>>()
  private val newData get() = data

  constructor(otherUpdate: Update<I>) : this(otherUpdate.oldRecyclerData) {
    // Bring the changed values from the otherUpdate (not the event listeners!).
    data = otherUpdate.data
    extraItem = otherUpdate.extraItem
    addedChunks.addAll(otherUpdate.addedChunks)
  }

  fun addChunk(chunk: List<I>) {
    if (chunk.isNotEmpty()) {
      addedChunks += chunk
    }
  }

  internal var onReady: (() -> Unit) = {}
    private set
  internal var onCancelled: (() -> Unit) = {}
    private set

  fun onReady(block: () -> Unit) {
    onReady = block
  }

  fun onCancelled(block: () -> Unit) {
    onCancelled = block
  }

  /**
   * Result object for [generateDiffWork]. It contains:
   * @param isSynchronous whether [work] should be executed on the UI thread
   * @param updateType [UpdateType] for this work
   * @param work List of lambdas that will calculate item diffs.
   * @param notifications List of lambdas receiving the adapter that will notify of the changes.
   */
  internal class UpdateWork(
    val isSynchronous: Boolean,
    val updateType: UpdateType,
    val work: List<() -> Unit>,
    val notifications: List<(Adapter<*>) -> Unit>
  )

  /**
   * Calculates the work needed to apply this update. It will return a
   * `Pair<List<lambda>, List<lambda>>` where the first list is the work that needs to be done
   * asynchronously and the second list is to apply calculated notifications to the recycler adapter
   * on the main thread. This allows the caller (see [Recycler.update]) to decide if it needs to
   * go async (and missing a couple of frames) or it can be applied at once.
   */
  @SuppressLint("NotifyDataSetChanged")
  internal fun generateUpdateWork(itemComparator: ItemComparator<I>?): UpdateWork {
    val extraItemChanged = oldRecyclerData.extraItem != extraItem

    val work = mutableListOf<() -> Unit>()
    val notifications = mutableListOf<(Adapter<*>) -> Unit>()

    val updateType = when {
      oldRecyclerData.data.isEmpty && !data.isEmpty -> Populating
      oldRecyclerData.data != data -> Replacing
      addedChunks.isNotEmpty() -> Adding
      else -> NoOp
    }

    lateinit var updateMode: UpdateMode
    when (updateType) {
      is NoOp -> return UpdateWork(
          isSynchronous = true,
          updateType = updateType,
          work = emptyList(),
          notifications = emptyList()
      )
      is Populating -> {
        updateMode = populateMode
        notifications += { adapter -> adapter.notifyItemRangeInserted(0, data.size) }
      }
      is Replacing -> {
        updateMode = replaceMode

        if (itemComparator == null) {
          notifications.add { adapter -> adapter.notifyDataSetChanged() }
        } else {
          if (extraItemChanged) {
            // We notify first the extraItem change, so we use its position in oldRecyclerData.
            notifications += notifyChangesExtraItem()
          }

          with(generateDiffWork(itemComparator)) {
            work.add(diffing)
            notifications.add(notifying)
          }
        }
      }
      is Adding -> {
        updateMode = Async

        notifications += { adapter ->
          val positionAt = oldRecyclerData.data.size
          val count = addedChunks.asSequence()
              .map(List<I>::size)
              .sum()
          adapter.notifyItemRangeInserted(positionAt, count)
        }
      }
    }

    return UpdateWork(
        work = work,
        notifications = notifications,
        updateType = updateType,
        isSynchronous = updateMode is Sync
    )
  }

  /**
   * Creates the diffing and notifying lambdas for [UpdateType.Replacing] operations.
   */
  internal fun generateDiffWork(
    itemComparator: ItemComparator<I>
  ): DiffWork {
    lateinit var diffResult: DiffResult
    return DiffWork(
      diffing = { diffResult = calculateDataChanges(itemComparator) },
      notifying = { adapter -> diffResult.dispatchUpdatesTo(adapter) }
    )
  }

  /**
   * Calculates the changes that need to be notified to a RecyclerView to change from the oldData
   * to the newData. It's better to execute this on a separate thread.
   * @return A [DiffResult] to be applied on "notification" time (see [UpdateWork.notifications]).
   */
  private fun calculateDataChanges(
    itemComparator: ItemComparator<I>
  ): DiffResult {
    val callback = DataSourceDiff(itemComparator, oldRecyclerData.data, newData)
    return DiffUtil.calculateDiff(callback, detectMoves)
  }

  /**
   * @return A lambda that will update the adapter according to the change in the extra item.
   */
  private fun notifyChangesExtraItem(): (Adapter<*>) -> Unit = { adapter ->
    val hadExtraItem = oldRecyclerData.hasExtraItem
    val hasExtraItem = extraItem != null
    when {
      hadExtraItem && hasExtraItem -> adapter.notifyItemChanged(oldRecyclerData.extraItemIndex)
      hadExtraItem && !hasExtraItem -> adapter.notifyItemRemoved(oldRecyclerData.extraItemIndex)
      !hadExtraItem && hasExtraItem -> adapter.notifyItemInserted(oldRecyclerData.extraItemIndex)
    }
  }

  /**
   * Creates the new RecyclerData.
   * We understand the updates so we can properly build the new data source and extra item.
   */
  fun createNewRecyclerData(config: Config<I>, updateType: UpdateType): RecyclerData<I> {
    val newData = when {
      updateType is Adding -> concatenateAddedChunks()
      else -> data
    }
    return RecyclerData(config, newData, extraItem)
  }

  /**
   * TODO (DESIGNSYS-316): We should be able to concatenate DataSource's faster.
   * Also mutationMap gets "flattened" because we create a new whole datasource from the mutable one.
   * We could instead do:
   * - original <- with mutationMap
   * - original + new chunks <= with mutationMap
   * As mutationMap just changes items in the already existing range.
   */
  private fun concatenateAddedChunks() = mutableListOf<I>().apply {
    addAll((0 until data.size).asSequence().map(data::get))
    addAll(addedChunks.asSequence().flatten())
  }.toDataSource()

  /**
   * Determines whether dataset diffing happens on a background thread or on the UI thread. For
   * large datasets, [Async] is recommended but it incurs a 1 frame penalty when dispatching the
   * updates. For smaller datasets, [Sync] will dispatch updates immediately and avoid the 1 frame
   * penalty.
   */
  sealed class UpdateMode {
    object Async : UpdateMode()
    object Sync : UpdateMode()
  }

  sealed class UpdateType {
    /**
     * Used when we're replacing an empty dataset with a non-empty dataset. Since the existing
     * dataset is empty, we know that there's no diffing that needs to be done.
     */
    object Populating : UpdateType()

    /**
     * Used when we're replacing a non-empty dataset with any other dataset (even an empty one).
     * We may need to do some diffing if an [ItemComparator] has been provided.
     */
    object Replacing : UpdateType()

    /**
     * Used when we're appending items to the end of the dataset.
     */
    object Adding : UpdateType()

    /**
     * Used when there's no change to the data,
     */
    object NoOp : UpdateType()
  }

  internal data class DiffWork(
    val diffing: (() -> Unit),
    val notifying: (Adapter<*>) -> Unit
  )
}
