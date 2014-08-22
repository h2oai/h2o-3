# How R Expressions are Sent to H2O for Evaluation

An H2O data frame is represented in R by an S4 object of class
H2OParsedData.  The S4 object has a @key slot which is a reference to
the big data object inside H2O.

The H2O R package overloads generic operations like 'summary' and '+'
with this new H2OParsedData class.  The R core parser makes callbacks
into the H2O R package, and these operations then get shipped over an
HTTP connection to the H2O cloud.

The H2O cloud performs the big data operation (say '+' on two columns
of a dataset imported into H2O) and returns a reference to the result.
This reference is stored in a new H2OParsedData S4 object inside the
R program.

