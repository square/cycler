# Square Cycler – a RecyclerView API

The Square Cycler API allows
you to **easily configure** an Android RecyclerView declaratively
in a **succinct way**.

## Design principles

- It should be **declarative**. You tell us what you want, not what to do.
- It should have **all the code regarding one type of row together**. The less switch statements the better (some existing libraries and Android recycler itself group all creation together, and all binder together elsewhere; that's close to the metal but far from developer needs).
- It should be able to cover **common needs**, specially making adapter access unnecessary. Access to the `RecyclerView` for ad-hoc configuration is allowed.
- It should be **strongly typed**.
- It should include **common features**: edge decoration, sticky headers, etc.
- It should make it **easy to inflate** rows or to create them programmatically.
- It should make it **easy to create common custom items**.

## How to use it

- Configure the recycler view when you create your view.
- Provide data each time it changes.

### Configuring block

The configuring block is the essence of the recycler view.
It contains all the row definitions and how to bind data.

You can ask the API to create the RecyclerView object for you
–using the `create` method– or configuring an existing instance
–through the `adopt` method. The later is useful if you already
have a layout which the recycler view is part of.

**Examples:**

```kotlin
val recycler = Recycler.create<ItemType>(context, id = R.id.myrecycler) {
  ...
}
```

```kotlin
val recycler = Recycler.adopt(findViewById(R.id.my_recycler)) {
  ...
}
```

In both cases you will receive a Recycler object which
represents the RecyclerView and allows you to set data afterwards.

The configuring block will have some general configurations,
for instance an item comparator, and a row definition for
every type of row you need.

#### Generics

The generics used along this documentation are as follow:

- `I: ` ItemType. General type for all the data items of the rows.
- `S: ` ItemSubType. Data item type for the particular row being defined.
- `V: ` ViewType. View type for the particular row being defined.

#### Row definitions

Using a layout:

```kotlin
row<I, S, V> {
  forItemsWhere { subitem -> ...boolean... }
  create(R.layout.my_layout) {
    // you can get references to sub-elements inside view
    val subView = view.findViewById(...)
    bind { subItem ->
      // assign values from subItem to view or sub-elements
    }
  }
  ...more row options...
}
```

The subtype `S` will automatically make the row definitions
only be used for that type of item `I`.

`forItemsWhere` clause is optional. In case you need to filter
by an arbitrary predicate on `S` (notice you don't need to cast).

`create` will inflate the layout and assign it to a `var view: V`.
You can get references to sub-components using `findViewById`.

`bind` receives the subItem (again, already cast to `S`).
You can use `view` and your own captured references from
the `create` block to assign values. Notice that you don't need to
cast `view as V`. It's already of that type.

General approach:

```kotlin
row<I, S, V> {
  forItemsWhere { subitem -> ...boolean... }
  create { context ->
    view = MyView(context)
    // you can get references to sub-elements inside view
    val subView = view.findViewById(...)
    bind { subItem ->
      // assign values from subItem to view or sub-elements
    }
  }
  ...more row options...
}
```

This is the general case. Instead of inflating a layout create
provides a context for you to create a view of type `V` and assign
it to `view`. As usual you can use that `view` reference or any
other you obtain inside the `bind` block.

#### Extra item definitions

Recycler views allow for the inclusion of one extra (but optional)
item. This is useful when you want to show your state.
For example: "no results" or "loading more...".
The extraItem is independent from the main data list and
doesn't need to be of type `I`.

Definitions for extraItems are analogous to normal rows
and follow the same convention. But are only applied to the
extra item you provide along with the data (if any).

```kotlin
extraItem<I, S, V> {
  forItemsWhere { subitem -> ...boolean... }
  create { context ->
    ...
    bind { subItem -> ... }
  }
  ...more row options...
}
```

Notice that you can define several different extraItem
blocks, with the same or different sub-types `S` and
optional `forItemWhere`.

`bind` is also provided in case your extra item has data.
Imagine you are filtering by fruit. If you selected "apples"
you want to show "No more apples" instead of "No more fruits".
That can be achieved with an extra item of type
`NoMore(val fruitName: String)`.

#### More row options

Recycler API offers an extension mechanism.
Extensions are useful for cross-cutting concerns like
edges or headers which will be discussed separately.

These extensions will be configured in the same way,
through a definition block.

Extensions might offer special configuration for certain
types of row. For example, edges can define a default
edge configuration, but use different values for the rows
of type `Banana`. In that case the `row<Banana>` definition
will include its special configuration.

See extensions section for more details.

#### General configuration

The RecyclerView uses certain general definitions that can
be configured here as well.

`stableIds { item -> ...long... }`

If you provide a function that returns an id of type `Long`
for every item in the data, the recycler view will be able
to identify unchanged items when data is updated, and
animate them accordingly.

`itemComparator = ...`

When data is updated the RecyclerView compares both datasets
to find which item moved where, and check if they changed any
data at all.

Android's RecyclerView's can do that calculation but it needs
to compare the items. The developer must provide the comparison.
You can provide an `ItemComparator` implementation which is
simpler than the required `DiffUtil.Callback` one.

An `ItemComparator` provides two methods:
- `areSameIdentity` returns true if they represent the
same thing (even if data changed).
- `areSameContent` tells if any data changed, requiring re-binding.

If your items are `Comparable` or you have a `Comparator`
you can create an automatic `ItemComparator`. Just use:

- `fun itemComparatorFor(Comparator<T>): ItemComparator<T>`
- `fun naturalItemComparator(): ItemComparator<T>` if `T` is `Comparable<T>`

It will implement both: identity and content-comparison
based on `Comparator` or `Comparable`. That means that items
will either be different or identical, therefore never updated.
But for immutable (or practically immutable) items
it works pretty well.

## Data providing

Once you configured your recycler view you just need to
give it data.

The `Recycler` object returned by the configuring block
represents your recycler view. It has three properties:

- `view`: the RecyclerView. You can add it to your layout
  if it was created by the API.
- `data`: the list of items to show.
- `extraItem`: the extra item to add to the end (or null).

Notice that `data` is of type `DataSource<I>`.

`DataSource` is a simplified `List` interface:

```kotlin
interface DataSource<out T> {
  operator fun get(i: Int): T
  val size: Int
}
```

You can convert an `Array` or a `List` to a DataSource
using the extension method `toDataSource()`:
`arrayOf(1, 2, 3).toDataSource()`.

The advantage over requiring a Kotlin `List` is that you
can implement your arbitrary DataSource without having to
implement the whole `List` interface, which is bigger.

## Extensions

Extensions are a mechanism to add simple-to-configure features
to Recyclers without adding dependencies to this library.

### Row type extensions

You can create extensions for common custom views in your project:

```kotlin
myCustomItem<I, S> {
  forItemsWhere { ... }
  bind { item, view ->
    view.title  = ...
    view.message = ...
    ...
  }
}
```

The extension method just needs to use a different row definition
method that lets you define how to create the view by separate.

For instance:

```kotlin
/**
 * Extension method for a custom item, allowing full control.
 * ```
 * myCustomItem<I, S> { // this: BinderRowSpec<...>
 *    // you can configure extra stuff:
 *   forItemsWhere { ... }
 *   // and then define your bind lambda:
 *   bind { item, view ->
 *     view.title  = ...
 *     view.message = ...
 *     ...
 *   }
 * }
 * ```
 */
@RecyclerApiMarker
inline fun <I : Any, reified S : I> Recycler.Config<I>.myCustomItem(
  crossinline specBlock: BinderRowSpec<I, S, CustomView>.() -> Unit
) {
  row(
      creatorBlock = { creatorContext ->
        CustomView(creatorContext.context)
        .apply { ... }
      },
      specBlock = specBlock
  )
}

/**
 * Extension method for passing just a bind lambda.
 * ```
 * myCustomItem<I, S> { item, view ->
 *   view.title  = ...
 *   view.message = ...
 *   ...
 * }
 * ```
 */
 @RecyclerApiMarker
 inline fun <I : Any, reified S : I> Recycler.Config<I>.myCustomItem(
   noinline bindBlock: (S, CustomView) -> Unit
 ) {
   row(
       creatorBlock = { creatorContext ->
         CustomView(creatorContext.context)
        .apply { ... }
       },
       bindBlock = bindBlock
   )
 }
```

Notice:
- You don't need to declare extension methods for each row.
  It's just a shorthand for those things your project uses repeatedly.
- You can also use analogous methods that provide the index of the item
  in binding.

### Decoration extensions

```
TODO: code and documentation need to be added.
```

# License

Copyright 2019 Square Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
