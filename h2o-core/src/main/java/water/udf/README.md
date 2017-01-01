# User-Defined Functions for H2O

## Intro
UDF were inspired by a similar solution in Spark. We can define our own function and distribute them over the cloud to calculate values on the fly, to be able to compose the functions, and to materialize the results, storing them in vectors or frames.

The whole library is statically typed.

## Data Types
Data are organized in TypedFrames, which consist of Columns. You can work with individual Columns, or with a whole TypedFrame.

A Column is a sequence of individual values. Due to specifics of H2O, one can also view a Column as an array of TypedChunks, but that's not necessary. Any serializable type can work as a type of values in Columns.

The sequence of values is, conceptually, indexed by sequential numbers, that is, row numbers - but working with these indexes is not efficient. The only case when row numbers make sense is when we materialize a column with data that are read from a list or a file - in which case each value can be supplied with a number. Once in a column, these row numbers lose their meaning.

So rows are actually indexed by 