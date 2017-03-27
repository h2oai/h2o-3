/**
 * Provides functionality similar to Spark UDF (user-defined functions).
 * 
 * Unlike core h2o classes, in udf columns have types.
 * The following data types are standard:
 * - {@code Date}
 * - {@code Double}
 * - {@code Enum} (aka category)
 * - {@code Integer}
 * - {@code String}
 * 
 * Besides standard types, any column type is allowed; but you may not be able to materialize it.
 * 
 * Two kinds of columns have very differen nature:
 * - {@code DataColumn}, which refers the data stored in H2O vec (but knows the data type);
 * - {@code FunColumn}, which refers a Column (or more than one {@code Column}, if required), and a function that calculates column value for each argument (or a tuple of arguments).
 * 
 * Columns can be organized into frames. There's a limitation: the frame build from columns cannot be heterogeneous, all columns are of the same type.
 * 
 * If you have a "virtual" column, that is, the one build from other columns using functions, you can materialize it, and produce a regular DataColumn which keeps its data in a vec.
 * 
 * The functions that produce column values can be written by the user, or taken 'instant' from fp.PureFunctions class.
 * 
 * Examples of usage can be found in UdfTest.java
 *
 * A Scala version is available in module h2o-scala.
 */
package water.udf;