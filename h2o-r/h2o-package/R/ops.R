#'
#' Overview:
#' ---------
#'
#' R operators mixed with h2o.frame objects.
#'
#' Operating on an object of type h2o.frame triggers the rollup of the
#' expression _to be executed_ : the expression itself is not evaluated. Instead,
#' an AST is built up from the R expression using R's built-in parser (which handles
#' operator precedence), and, in the case of assignment, is stashed into the variable
#' in the assignment.
#'
#' The AST is bound to an R variable as a promise to evaluate the expression on demand.
#' When evaluation is forced, the AST is walked, converted to JSON, and shipped over to H2O.
#' The result returned by H2O is a key pointing to the newly created frame.
#'
#' Methods may have a non-h2o.frame return type. Any extra preprocessing of data returned by H2O
#' is discussed in each instance, as it varies from method to method.
#'
#'
#' What's implemented?
#' --------------------
#'
#' Many of R's generic S3 methods may be mixed with h2o.frame objects wherein the result
#' is coerced to the appropraitely typed object (typically an h2o.frame object).
#'
#' A list of R's generic methods may be found by calling `getGenerics()`. Likewise, a call to
#' `h2o.getGenerics()` will list the operations that are permissible with h2o.frame objects.
#'
#' S3 methods are divided into four groups: Math, Ops, Complex, and Summary.
#' h2o.frame methods follow these divisions as well, with the exception of Complex, which are
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

#' @describeIn h2o.frame
setMethod("Ops", signature(e1="missing",   e2="h2o.frame" ), function(e1,e2) .h2o.binop(.Generic,0,e2))
#' @describeIn h2o.frame
setMethod("Ops", signature(e1="h2o.frame",  e2="missing"  ), function(e1,e2) .h2o.binop(.Generic,e1,0))
#' @describeIn h2o.frame
setMethod("Ops", signature(e1="h2o.frame",  e2="h2o.frame" ), function(e1,e2) .h2o.binop(.Generic,e1,e2))
#' @describeIn h2o.frame
setMethod("Ops", signature(e1="numeric",   e2="h2o.frame" ), function(e1,e2) .h2o.binop(.Generic,e1,e2))
#' @describeIn h2o.frame
setMethod("Ops", signature(e1="h2o.frame",  e2="numeric"  ), function(e1,e2) .h2o.binop(.Generic,e1,e2))
#' @describeIn h2o.frame
setMethod("Ops", signature(e1="h2o.frame",  e2="character"), function(e1,e2) .h2o.binop(.Generic,e1,e2))
#' @describeIn h2o.frame
setMethod("Ops", signature(e1="character", e2="h2o.frame" ), function(e1,e2) .h2o.binop(.Generic,e1,e2))

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

#' @describeIn h2o.frame
setMethod("Math", signature(x = "h2o.frame"), function(x) { .h2o.unop(.Generic,x) })

#'
#' Math2 Generics:
#'
#' ‘"round"’, ‘"signif"’
#' @name MathGenerics2
NULL
#' @describeIn h2o.frame
setMethod("Math2", signature(x = "h2o.frame"), function(x, digits) .h2o.varop(.Generic,x,digits))

#'
#' Summary Generics:
#'
#' ‘"max"’, ‘"min"’, ‘"range"’, ‘"prod"’, ‘"sum"’, ‘"any"’, ‘"all"’
#' @name SummaryGenerics

NULL
#' @describeIn h2o.frame
setMethod("Summary", signature(x = "h2o.frame"), function(x, ..., na.rm = FALSE) {
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

#' @describeIn h2o.frame
setMethod("!",     "h2o.frame", function(x) .h2o.unop("!", x))
#' @describeIn h2o.frame
setMethod("is.na", "h2o.frame", function(x) .h2o.unop("is.na", x) )
#' @describeIn h2o.frame
setMethod("t",     "h2o.frame", function(x) .h2o.unop("t", x) )
#' @describeIn h2o.frame
setMethod("log",   "h2o.frame", function(x, ...) .h2o.varop("log", x, ...))
#' @describeIn h2o.frame
setMethod("trunc", "h2o.frame", function(x, ...) .h2o.varop("trunc", x, ...))

## WORK IN PROGRESS: Get these to work? (if possible...)
#'
#' These cannot be overriden: ‘"&&"’, ‘"||"’
#'
#`&&` <- function(e1, e2) UseMethod("&&", c(e1,e2))
#`&&.default`  <- function(e1, e2) {
#  if (e2 %<i-% "h2o.frame") .binops.fun(e1, e2)
#  else .Primitive("&&")(e1, e2)
#}
#`&&.h2o.frame` <- function(e1, e2) .binops.fun(e1,e2)

#`||` <- function(e1, e2) UseMethod("||", c(e1,e2))
#`||.default`  <- function(e1, e2) {
#  l <- (e2 %<i-% "h2o.frame")
#  if (l) .binops.fun(e1, e2)
#  else .Primitive("||")(e1, e2)
#}
#`||.h2o.frame` <- function(e1, e2) .binops.fun(e1,e2)
