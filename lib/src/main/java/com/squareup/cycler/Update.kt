package com.squareup.cycler

import android.annotation.SuppressLint
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DiffUtil.DiffResult
import androidx.recyclerview.widget.RecyclerView.Adapter
import com.squareup.cycler.Recycler.Config
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
  private val dataReplaced get() = oldRecyclerData.data != data
  private val dataAdded get() = !dataReplaced && addedChunks.isNotEmpty()
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
   * Result object for [generateUpdateWork]. It contains:
   * @param asyncWork List of lambdas to be executed asynchronously (can be empty).
   * @param notifications List of lambdas receiving the adapter that will notify of the changes.
   */
  internal class UpdateWork(
    val asyncWork: List<() -> Unit>,
    val notifications: List<(Adapter<*>) -> Unit>
  )
  /**
   * Calculates the work needed to apply this update. It will return a
   * `Pair<List<lambda>, List<lambda>>` where the first list is the work that needs to be done
   * asynchronously and the second list is to apply calculated notifications to the recycler adapter
   * on the main thread. This allows the caller (see [Recycler.update]) to decide if it needs to
   * go async (and missing a couple of frames) or it can be applied at once.
   */
  internal fun generateUpdateWork(itemComparator: ItemComparator<I>?): UpdateWork {

    val extraItemChanged = oldRecyclerData.extraItem != extraItem
    val refreshAllNeeded = dataReplaced && itemComparator == null

    val asyncWork = mutableListOf<() -> Unit>()
    val notifications = mutableListOf<(Adapter<*>) -> Unit>()

    if (refreshAllNeeded) {
      @SuppressLint("NotifyDataSetChanged")
      notifications += { adapter -> adapter.notifyDataSetChanged() }
    } else {

      if (extraItemChanged) {
        // We notify first the extraItem change, so we use its position in oldRecyclerData.
        notifications += notifyChangesExtraItem()
      }

      when {
        dataReplaced -> {
          // refreshAllNeeded == false => itemComparator != null. We are going async.
          lateinit var diffResult: DiffResult
          asyncWork += {
            diffResult = calculateDataChanges(itemComparator!!)
          }
          notifications += { adapter -> diffResult.dispatchUpdatesTo(adapter) }
        }
        dataAdded -> {
          notifications += { adapter ->
            val positionAt = oldRecyclerData.data.size
            val count = addedChunks.asSequence()
              .map(List<I>::size)
              .sum()
            adapter.notifyItemRangeInserted(positionAt, count)
          }
        }
      }
    }

    return UpdateWork(asyncWork, notifications)
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
  fun createNewRecyclerData(config: Config<I>): RecyclerData<I> {
    val newData = when {
      dataAdded -> concatenateAddedChunks()
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
}
