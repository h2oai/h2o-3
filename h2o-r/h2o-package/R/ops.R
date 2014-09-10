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
#' More precicely, the group divisions follow the S4 divisions: Ops, Math, Math2, Summary.
#'
#' See also groupGeneric.

##'
##' Get the prefix op.
#.getValidPrefix<-
#function() {
#  stack <- as.list(sys.calls())
#  for (s in stack)
#
#}

#'
#' Prefix, Binary Infix (Ops), and Variable Ops Generics
#'
#' Handle all of the binary infix operations with this simple function!
#'
#' Scrape the function call for the operator. Cast the sys.calls() to a list and extracting the root.
.unops.fun  <- function(x)      .h2o.unop (deparse(as.list(as.list(sys.calls())[[1]])[[1]]), x     )
.binops.fun <- function(e1,e2)  .h2o.binop(.getValidBinOp(), e1, e2)
.varops.fun <- function(x, ...) .h2o.varop(deparse(as.list(as.list(sys.calls())[[1]])[[1]]), x, ...)

#'
#' Get the binary operator off the stack.
#'
#' Return the first valid binary operator encountered
.getValidBinOp<-
function() {
  stack <- as.list(sys.calls())
  for (s in stack) {
    if (!is.null(.op.map[[ deparse(as.list(s)[[1]]) ]])) return(deparse(as.list(s)[[1]]))
  }
}

#'
#' Ops Generics:
#'
#' ‘"+"’, ‘"-"’, ‘"*"’, ‘"^"’, ‘"%%"’, ‘"%/%"’, ‘"/"’
#' ‘"=="’, ‘">"’, ‘"<"’, ‘"!="’, ‘"<="’, ‘">="’
#' ‘"&"’, ‘"|"’
#'
#' Bonus Operators: ‘"**"’
setMethod("Ops", signature(e1="missing",   e2="H2OFrame" ), function(e1,e2) .binops.fun(0, e1))
setMethod("Ops", signature(e1="H2OFrame",  e2="missing"  ), function(e1,e2) .binops.fun(e1, 0))
setMethod("Ops", signature(e1="H2OFrame",  e2="H2OFrame" ), function(e1,e2) .binops.fun(e1,e2))
setMethod("Ops", signature(e1="numeric",   e2="H2OFrame" ), function(e1,e2) .binops.fun(e1,e2))
setMethod("Ops", signature(e1="H2OFrame",  e2="numeric"  ), function(e1,e2) .binops.fun(e1,e2))
setMethod("Ops", signature(e1="H2OFrame",  e2="character"), function(e1,e2) .binops.fun(e1,e2))
setMethod("Ops", signature(e1="character", e2="H2OFrame" ), function(e1,e2) .binops.fun(e1,e2))

#'
#' Math Generics:
#'
#' ‘"abs"’,   ‘"sign"’,   ‘"sqrt"’,   ‘"ceiling"’, ‘"floor"’,
#' ‘"trunc"’, ‘"cummax"’, ‘"cummin"’, ‘"cumprod"’, ‘"cumsum"’,
#' ‘"log"’,   ‘"log10"’,  ‘"log2"’,   ‘"log1p"’,   ‘"acos"’, ‘"acosh"’,
#' ‘"asin"’,  ‘"asinh"’,  ‘"atan"’,   ‘"atanh"’,   ‘"exp"’,  ‘"expm1"’,
#' ‘"cos"’,   ‘"cosh"’,   ‘"sin"’,    ‘"sinh"’,    ‘"tan"’,  ‘"tanh"’,
#' ‘"gamma"’, ‘"lgamma"’, ‘"digamma"’,‘"trigamma"’
setMethod("Math", signature(x = "H2OFrame"), function(x) .unops.fun(x))

#'
#' Math2 Generics:
#'
#' ‘"round"’, ‘"signif"’
setMethod("Math2", signature(x = "H2OFrame"), function(x, digits) .varops.fun(x, digits))

#'
#' Summary Generics:
#'
#' ‘"max"’, ‘"min"’, ‘"range"’, ‘"prod"’, ‘"sum"’, ‘"any"’, ‘"all"’
setMethod("Summary", signature(x = "H2OFrame"), function(x, ..., na.rm = FALSE) {
  ast <- .varops.fun(x, ..., na.rm)
#  ID  <- as.list(match.call())$x
#  if(length(as.list(substitute(x))) > 1) ID <- "Last.value"
#  ID <- ifelse(ID == "Last.value", ID, ast@key)
  ID <- "Last.value"
  .force.eval(.retrieveH2O(parent.frame()), ast, ID = ID, rID = 'ast')
  ast
})

#'
#' Methods that don't fit into the S4 group generics
#'
#' This also handles the cases where the Math ops have multiple args (e.g. ’log’ and ‘trunc’)
#'
#' ‘"!"’, ‘"is.na"’, ‘"t"’
setMethod("!",     "H2OFrame", function(x) .h2o.unop("!", x))
setMethod("is.na", "H2OFrame", function(x) .h2o.unop("is.na", x) )
setMethod("t",     "H2OFrame", function(x) .h2o.unop("t", x) )
setMethod("log",   "H2OFrame", function(x, ...) .h2o.varop("log", x, ...))
setMethod("trunc", "H2OFrame", function(x, ...) .h2o.varop("trunc", x, ...))
