# User-Defined Functions for H2O

## Intro
UDF were inspired by a similar solution in Spark. We can define our own function and distribute them over the cloud to calculate values on the fly, to be able to compose the functions, and to materialize the results, storing them in vectors or frames.

The whole library is statically typed.

## Data Types
Data are organized in `TypedFrame`s, which consist of `Column`s. You can work with individual `Column`s, or with a whole `TypedFrame`.

A `Column` is a sequence of individual values. Due to specifics of H2O, one can also view a `Column as an array of `TypedChunk`s, but that's not necessary. Any serializable type can work as a type of values in `Column`s.

The sequence of values is, conceptually, indexed by sequential numbers, that is, row numbers - but working with these indexes is not efficient. The only case when row numbers make sense is when we materialize a column with data that are read from a list or a file - in which case each value can be supplied with a number. Once in a column, these row numbers lose their meaning.

So rows are actually indexed by their positions. A position is a long consisting of two ints, the first one is chunk number, the second one is the index inside the chunk. As a result, there's no need to go seek a chunk for an index. A column has a method that returns an iterable of its positions, in case you want to scan the whole column.

Each value in a `Column` (in a `TypedChunk`) has the same known type. It can be anything, a `Boolean`, a `String`, an array of data structures. This is a pretty serious advantage over just 3-4 primitive types that core H2O supports, with no static typing.

## Kinds of Columns
There are two kinds of `Columns`: `DataColumn` and `Functional Column`. 

### `DataColumn`
This kind of `Column` refers to the actual data in an H2O `Vec`. Since the data are materialized, one can get the data and set the data; the only difference with plain `Vec` is that we know exactly the data type. The data coming from H2O, we are only limited by the following types: `Double`, `Enum` (aka `Cat`, represented as `int`s), and `String`. There's also `UUID`, but it's not implemented yet.

### `Functional Column`
Values in such a column can be of any serializable Java type. Where do such values come from? They are produced by applying a function to the values taken from argument columns. These argument columns can be functional too, or can be data columns. 

### Materializing Columns
We may need to store the values produced by functions. This is done by column materialization. Materialization is an "MR task". Of course one can only materialize values that are known to H2O.

In the case when the values in a column are lists, the result of materialization is a `TypedFrame` containing a plurality of columns.

Another case for materializing columns is producing columns from external sources, e.g. from a list. If you have a list, you can build a column. You can also build a column, given any function `Long` &rarr; `T`, where `T` is an acceptable type. The function here does not take value positions; this function should be defined on a range `0..n`, where `n+1` is the size of the collection (e.g. list). For example, in this piece of code

```
  private DataColumn<Double> five_x() throws java.io.IOException {
    return willDrop(Doubles.newColumn(1 << 20, new Function<Long, Double>() {
      public Double apply(Long i) { return i*5.0; }
    }));
  }
```

we build a column of `2<sup>20</sup>` integers: `0,5,10,15,...`.

## We Have Functions
The approach in this library is that (almost) everything is a function. We have `Function<X,Y>` interface, with a method `Y apply(X x)`, we have `Function<X,Y,Z>` interface, with a method `Z apply(X x, Y y)`, we have `Foldable<X,Y>` interface that folds a collection of values of type `X` into a value of type `Y`, we have `interface Unfoldable<X, Y> extends Function<X, List<Y>>`. 

More, a `Column<T>` is also a function, `Long &rarr; T`. The argument type of this function is `Long`, but it's actually `position` (see above). There's also a method that returns a column value at a given row number, but we all know it's pretty heavy, involving binary search, so it's not recommended.

### Stock Functions
The class PureFunctions contains a bunch of functions for manipulating numbers and strings. More will be added as/if demand grows.

## Unfolding Data
One of the function kinds is unfolding: given a value of type `X`, produce a list of type `Y`. How do we materialize it? We use `UnfoldingFrame`. To build such a frame, we should provide a source of data, a function `Long &rarr; List<T>` that takes a `position` and returns a list of values. When this frame is materialized, we produce a list of columns (all of the same type).

## Folding Data
Folding is the opposite to unfolding. Given a Folding Function (that is, an initial value and a binary operation), we can do left fold on an unlimited number of columns. The only problem is, we need a non-zero number of columns.

## Data Compatibility
When we build a virtual (functional) column, it can be viewed as a sequence of `TypedChunk`s. The layout of these chunks is the same as the layout of source data. If a column is built of more than one column, these columns must be compatible (this fact is checked on creation). E.g. when we fold columns, we can only fold columns with the same layout. `DataColumn`s get the layout of their underlying `Vec`s.

## Conclusion
See more details in UdfTest.java, this class has a bunch of examples.