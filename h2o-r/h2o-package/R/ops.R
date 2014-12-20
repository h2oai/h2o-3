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
#' is coerced to the appropraitely typed object (typically an H2OFrame object).
#'
#' A list of R's generic methods may be found by calling `getGenerics()`. Likewise, a call to
#' `h2o.getGenerics()` will list the operations that are permissible with H2OFrame objects.
#'
#' S3 methods are divided into four groups: Math, Ops, Complex, and Summary.
#' H2OFrame methods follow these divisions as well, with the exception of Complex, which are
#' unimplemented.
#'
#' More precicely, the group divisions follow the S4 divisions: Ops, Math, Math2, Summary.
#'
#' See also groupGeneric.
#'
#'
#'
#' Ops Generics:
#'
#' ‘"+"’, ‘"-"’, ‘"*"’, ‘"^"’, ‘"%%"’, ‘"%/%"’, ‘"/"’
#' ‘"=="’, ‘">"’, ‘"<"’, ‘"!="’, ‘"<="’, ‘">="’
#' ‘"&"’, ‘"|"’
#'
#' Bonus Operators: ‘"**"’
#' @name OpsIntro
NULL

#' @describeIn H2OFrame
setMethod("Ops", signature(e1="missing",   e2="H2OFrame" ), function(e1,e2) .h2o.binop(.Generic,0,e2))
#' @describeIn H2OFrame
setMethod("Ops", signature(e1="H2OFrame",  e2="missing"  ), function(e1,e2) .h2o.binop(.Generic,e1,0))
#' @describeIn H2OFrame
setMethod("Ops", signature(e1="H2OFrame",  e2="H2OFrame" ), function(e1,e2) .h2o.binop(.Generic,e1,e2))
#' @describeIn H2OFrame
setMethod("Ops", signature(e1="numeric",   e2="H2OFrame" ), function(e1,e2) .h2o.binop(.Generic,e1,e2))
#' @describeIn H2OFrame
setMethod("Ops", signature(e1="H2OFrame",  e2="numeric"  ), function(e1,e2) .h2o.binop(.Generic,e1,e2))
#' @describeIn H2OFrame
setMethod("Ops", signature(e1="H2OFrame",  e2="character"), function(e1,e2) .h2o.binop(.Generic,e1,e2))
#' @describeIn H2OFrame
setMethod("Ops", signature(e1="character", e2="H2OFrame" ), function(e1,e2) .h2o.binop(.Generic,e1,e2))

#'
#' Math Generics:
#'
#' ‘"abs"’,   ‘"sign"’,   ‘"sqrt"’,   ‘"ceiling"’, ‘"floor"’,
#' ‘"trunc"’, ‘"cummax"’, ‘"cummin"’, ‘"cumprod"’, ‘"cumsum"’,
#' ‘"log"’,   ‘"log10"’,  ‘"log2"’,   ‘"log1p"’,   ‘"acos"’, ‘"acosh"’,
#' ‘"asin"’,  ‘"asinh"’,  ‘"atan"’,   ‘"atanh"’,   ‘"exp"’,  ‘"expm1"’,
#' ‘"cos"’,   ‘"cosh"’,   ‘"cospi"’,  ‘"sin"’,     ‘"sinh"’, ‘"sinpi"’,
#' ‘"tan"’,   ‘"tanh"’,   ‘"tanpi"’,
#' ‘"gamma"’, ‘"lgamma"’, ‘"digamma"’,‘"trigamma"’
#' @name MathGenerics
NULL

#' @describeIn H2OFrame
setMethod("Math", signature(x = "H2OFrame"), function(x) { .h2o.unop(.Generic,x) })

#'
#' Math2 Generics:
#'
#' ‘"round"’, ‘"signif"’
#' @name MathGenerics2
NULL
#' @describeIn H2OFrame
setMethod("Math2", signature(x = "H2OFrame"), function(x, digits) .h2o.varop(.Generic,x,digits))

#'
#' Summary Generics:
#'
#' ‘"max"’, ‘"min"’, ‘"range"’, ‘"prod"’, ‘"sum"’, ‘"any"’, ‘"all"’
#' @name SummaryGenerics

NULL
#' @describeIn H2OFrame
setMethod("Summary", signature(x = "H2OFrame"), function(x, ..., na.rm = FALSE) {
  ast <- .h2o.varop(.Generic, x, ..., na.rm)
  .force.eval(ast@ast)
})

#'
#' Methods that don't fit into the S4 group generics
#'
#' This also handles the cases where the Math ops have multiple args (e.g. ’log’ and ‘trunc’)
#'
#' ‘"!"’, ‘"is.na"’, ‘"t"’, ‘"trunc"’
#' @name MethodsMisc
NULL

#' @describeIn H2OFrame
setMethod("!",     "H2OFrame", function(x) .h2o.unop("!", x))
#' @describeIn H2OFrame
setMethod("is.na", "H2OFrame", function(x) .h2o.unop("is.na", x) )
#' @describeIn H2OFrame
setMethod("t",     "H2OFrame", function(x) .h2o.unop("t", x) )
#' @describeIn H2OFrame
setMethod("log",   "H2OFrame", function(x, ...) .h2o.varop("log", x, ...))
#' @describeIn H2OFrame
setMethod("trunc", "H2OFrame", function(x, ...) .h2o.varop("trunc", x, ...))

## WORK IN PROGRESS: Get these to work? (if possible...)
#'
#' These cannot be overriden: ‘"&&"’, ‘"||"’
#'
#`&&` <- function(e1, e2) UseMethod("&&", c(e1,e2))
#`&&.default`  <- function(e1, e2) {
#  if (e2 %<i-% "H2OFrame") .binops.fun(e1, e2)
#  else .Primitive("&&")(e1, e2)
#}
#`&&.H2OFrame` <- function(e1, e2) .binops.fun(e1,e2)

#`||` <- function(e1, e2) UseMethod("||", c(e1,e2))
#`||.default`  <- function(e1, e2) {
#  l <- (e2 %<i-% "H2OFrame")
#  if (l) .binops.fun(e1, e2)
#  else .Primitive("||")(e1, e2)
#}
#`||.H2OFrame` <- function(e1, e2) .binops.fun(e1,e2)
