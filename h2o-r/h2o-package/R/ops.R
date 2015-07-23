##"
##" Overview:
##" ---------
##"
##" R operators mixed with H2OFrame objects.
##"
##" Operating on an object of type H2OFrame triggers the rollup of the
##" expression _to be executed_ : the expression itself is not evaluated. Instead,
##" an AST is built up from the R expression using R's built-in parser (which handles
##" operator precedence), and, in the case of assignment, is stashed into the variable
##" in the assignment.
##"
##" The AST is bound to an R variable as a promise to evaluate the expression on demand.
##" When evaluation is forced, the AST is walked, converted to JSON, and shipped over to H2O.
##" The result returned by H2O is a key pointing to the newly created frame.
##"
##" Methods may have a non-H2OFrame return type. Any extra preprocessing of data returned by H2O
##" is discussed in each instance, as it varies from method to method.
##"
##"
##" What's implemented?
##" --------------------
##"
##" Many of R's generic S3 methods may be mixed with H2OFrame objects wherein the result
##" is coerced to the appropraitely typed object (typically an H2OFrame object).
##"
##" A list of R's generic methods may be found by calling `getGenerics()`. Likewise, a call to
##" `h2o.getGenerics()` will list the operations that are permissible with H2OFrame objects.
##"
##" S3 methods are divided into four groups: Math, Ops, Complex, and Summary.
##" H2OFrame methods follow these divisions as well, with the exception of Complex, which are
##" unimplemented.
##"
##" More precicely, the group divisions follow the S4 divisions: Ops, Math, Math2, Summary.
##"
##" See also groupGeneric.


# Ops Generic:
# \code{"+"}, \code{"-"}, \code{"*"}, \code{"^"}, \code{"\%\%"}, \code{"\%/\%"}, \code{"/"}
# \code{"=="}, \code{">"}, \code{"<"}, \code{"!="}, \code{"<="}, \code{">="},
# \code{"&"}, \code{"|"}, \code{"**"}
#              \code{"abs"}, \code{"sign"}, \code{"sqrt"}, \code{"ceiling"}, \code{"floor"},
#              \code{"trunc"}, \code{"cummax"}, \code{"cummin"}, \code{"cumprod"}, \code{"cumsum"},
#              \code{"log"}, \code{"log10"}, \code{"log2"}, \code{"log1p"}, \code{"acos"}, \code{"acosh"},
#              \code{"asin"}, \code{"asinh"}, \code{"atan"}, \code{"atanh"}, \code{"exp"}, \code{"expm1"},
#              \code{"cos"}, \code{"cosh"}, \code{"cospi"}, \code{"sin"}, \code{"sinh"}, \code{"sinpi"},
#              \code{"tan"}, \code{"tanh"}, \code{"tanpi"},
#              \code{"gamma"}, \code{"lgamma"}, \code{"digamma"}, \code{"trigamma"}


#' S4 Group Generic Functions for H2O
#'
#' Methods for group generic functions and H2O objects.
#'


#' @param x,y,e1,e2 objects.
#' @param digits number of digits to be used in \code{round} or \code{signif}
#' @param \dots further arguments passed to or from methods
#' @param na.rm logical: should missing values be removed?
#' @name H2OS4groupGeneric
NULL
#' @rdname H2OS4groupGeneric
#' @export
setMethod("Ops", signature(e1="missing",   e2="H2OFrame" ), function(e1,e2) .h2o.binary_row_op(.Generic,0,e2))

#' @rdname H2OS4groupGeneric
#' @export
setMethod("Ops", signature(e1="H2OFrame",  e2="missing"  ), function(e1,e2) .h2o.binary_row_op(.Generic,e1,0))

#' @rdname H2OS4groupGeneric
#' @export
setMethod("Ops", signature(e1="H2OFrame",  e2="H2OFrame" ), function(e1,e2) .h2o.binary_row_op(.Generic,e1,e2))

#' @rdname H2OS4groupGeneric
#' @export
setMethod("Ops", signature(e1="numeric",   e2="H2OFrame" ), function(e1,e2) .h2o.binary_row_op(.Generic,e1,e2))

#' @rdname H2OS4groupGeneric
#' @export
setMethod("Ops", signature(e1="H2OFrame",  e2="numeric"  ), function(e1,e2) .h2o.binary_row_op(.Generic,e1,e2))

#' @rdname H2OS4groupGeneric
#' @export
setMethod("Ops", signature(e1="H2OFrame",  e2="character"), function(e1,e2) .h2o.binary_row_op(.Generic,e1,e2))

#' @rdname H2OS4groupGeneric
#' @export
setMethod("Ops", signature(e1="character", e2="H2OFrame" ), function(e1,e2) .h2o.binary_row_op(.Generic,e1,e2))

# Math Generics
#
#' @rdname H2OS4groupGeneric
#'
#' @export
setMethod("Math", signature(x = "H2OFrame"), function(x) { .h2o.unary_row_op(.Generic,x) })

# Math2 Generics
#
#' @rdname H2OS4groupGeneric
#'
#             \code{"round"}, \code{"signif"}
#' @export
setMethod("Math2", signature(x = "H2OFrame"), function(x, digits) .h2o.nary_row_op(.Generic,x,digits))


# Summary Generics:

#' @rdname H2OS4groupGeneric
#'
#             \code{"max"}, \code{"min"}, \code{"range"}, \code{"prod"}, \code{"sum"}, \code{"any"}, \code{"all"}
#' @export
setMethod("Summary", signature(x = "H2OFrame"), function(x, ..., na.rm = FALSE) {
  if( na.rm ) stop("Unimplemented, passing the NA flag")
  res <- .h2o.nary_scalar_op(.Generic, x, ...)
  if( .Generic=="all" ) res <- as.logical(res)
  res
})

##`
##` Methods that don't fit into the S4 group generics:
##`
##` This also handles the cases where the Math ops have multiple args (e.g. 'log' and 'trunc')
##`
##` "!", "is.na", "t", "trunc"

#' @rdname H2OS4groupGeneric
#' @export
setMethod("!",     "H2OFrame", function(x) .h2o.unary_row_op("!!", x))
#' @rdname H2OS4groupGeneric
#' @export
setMethod("is.na", "H2OFrame", function(x) .h2o.unary_row_op("is.na", x) )
#' @rdname H2OS4groupGeneric
#' @export
setMethod("t",     "H2OFrame", function(x) .h2o.unary_frame_op("t", x) )
#' @rdname H2OS4groupGeneric
#' @export
setMethod("log",   "H2OFrame", function(x, ...) .h2o.nary_row_op("log", x, ...))
#' @rdname H2OS4groupGeneric
#' @export
setMethod("trunc", "H2OFrame", function(x, ...) .h2o.nary_row_op("trunc", x, ...))
#' @rdname H2OS4groupGeneric
#' @export
setMethod("%*%", c("H2OFrame", "H2OFrame"), function(x,y) .h2o.binary_frame_op("%*%",x,y))
