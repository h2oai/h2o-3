#'
#' Overview:
#' ---------
#'
#' R operators mixed with H2OFrame objects.
#'
#' Operating on an object of type H2OFrame triggers the rollup of the
#' expression _to be executed_ : the expression itself is not evaluated. Instead,
#' an AST is built up from the R expression using R's built-in parser (which handles
#' operator precedence), and, in the case of assignment, is stashed into the variable
#' in the assignment.
#'
#' The AST is bound to an R variable as a promise to evaluate the expression on demand.
#' When evaluation is forced, the AST is walked, converted to JSON, and shipped over to H2O.
#' The result returned by H2O is a key pointing to the newly created frame.
#'
#' Methods may have a non-H2OFrame return type. Any extra preprocessing of data returned by H2O
#' is discussed in each instance, as it varies from method to method.
#'
#'
#' What's implemented?
#' --------------------
#'
#' Many of R's generic S3 methods may be mixed with H2OFrame objects wherein the result
#' is coerced to the appropraitely typed object (typically an H2OParsedData object).
#'
#' A list of R's generic methods may be found by calling `getGenerics()`. Likewise, a call to
#' `h2o.getGenerics()` will list the operations that are permissible with H2OParsedData objects.
#'
#' S3 methods are divided into four groups: Math, Ops, Complex, and Summary.
#' H2OFrame methods follow these divisions as well, with the exception of Complex, which are
#' unimplemented.
#'
#' See also groupGeneric.

#'
#' Handle all of the binary infix operations with this simple function!
#'
#' Scrape the function call by casting the sys.call() to a list and extracting the root.
.ops.function <- function(e1,e2) { .h2o.binop(deparse(as.list(sys.call())[[1]]), e1, e2) }

#TODO: Do the returns need to be wrapped by `invisble` ?
setMethod("Ops", signature(e1="H2OFrame",  e2="missing"),  function(e1,e2) { .h2o.binop(deparse(as.list(sys.call())[[1]]), e1, 0) })
setMethod("Ops", signature(e1="H2OFrame",  e2="H2OFrame"), function(e1,e2) { .h2o.binop(deparse(as.list(sys.call())[[1]]), e1, e2) })
setMethod("Ops", signature(e1="numeric",   e2="H2OFrame"), function(e1,e2) { .h2o.binop(deparse(as.list(sys.call())[[1]]), e1, e2) })
setMethod("Ops", signature(e1="H2OFrame",  e2="numeric"),  function(e1,e2) { .h2o.binop(deparse(as.list(sys.call())[[1]]), e1, e2) })
setMethod("Ops", signature(e1="H2OFrame",  e2="character"),function(e1,e2) { .h2o.binop(deparse(as.list(sys.call())[[1]]), e1, e2) })
setMethod("Ops", signature(e1="character", e2="H2OFrame"), function(e1,e2) { .h2o.binop(deparse(as.list(sys.call())[[1]]), e1, e2) })

#
##TODO: This needs a method .h2o.varop() to handle varargs
##setMethod("Math", signature(e1="H2OFrame"),
##  function(x) {
##    .h2o.unop(deparse(as.list(sys.call())[[1]]), x)
##  }
##)
#
#setMethod("Summary", signature(x="H2OFrame"),
#  function(x) {
#    .h2o.binop(deparse(as.list(sys.call())[[1]]), x)
#  }
#)
#
#
##setMethod("!",       "H2OParsedData",                function(x) {      .h2o.__unop2("!",     x) })
#setMethod("abs",     "H2OParsedData",                function(x) {      .h2o.__unop2("abs",   x) })
#setMethod("sign",    "H2OParsedData",                function(x) {      .h2o.__unop2("sgn",   x) })
#setMethod("sqrt",    "H2OParsedData",                function(x) {      .h2o.__unop2("sqrt",  x) })
#setMethod("ceiling", "H2OParsedData",                function(x) {      .h2o.__unop2("ceil",  x) })
#setMethod("floor",   "H2OParsedData",                function(x) {      .h2o.__unop2("floor", x) })
#setMethod("log",     "H2OParsedData",                function(x) {      .h2o.__unop2("log",   x) })
#setMethod("exp",     "H2OParsedData",                function(x) {      .h2o.__unop2("exp",   x) })
#setMethod("is.na",   "H2OParsedData",                function(x) {      .h2o.__unop2("is.na", x) })
#setMethod("t",       "H2OParsedData",                function(x) {      .h2o.__unop2("t",     x) })
