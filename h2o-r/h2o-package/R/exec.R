#~
#~ R -> AST
#~
#~ This is the front-end of the execution interface between R and H2O.
#~
#~ The workhorses of this front end are .h2o.unary_op, .h2o.binary_op, and .h2o.nary_op.
#~
#~ Together, these three methods handle all of the available operations that can
#~ be done with H2OFrame objects (this includes H2OFrame objects and ASTNode objects).

# Result Types
ARY    <- 0L
NUM    <- 1L
STR    <- 2L
ARYNUM <- 3L
ARYSTR <- 4L


#'
#' Prefix Operation With A Single Argument
#'
#' Operation on an object that inherits from H2OFrame.
.h2o.unary_op_ast<-
function(op, x) {
  if (!is.na(.op.map[op])) op <- .op.map[op]
  op <- new("ASTApply", op = op)

  if( is(x, "H2OFrame") )      x <- .get(x)
  else if( is(x, "ASTNode") )  x <- x
  else if( is.numeric(x) )     x <- paste0('#', x)
  else if( is.character(x) )   x <- deparse(eval(x))
  else if( is(x, "ASTEmpty") ) x <- paste0('%', x@key)
  else stop("operand type not handled: ", class(x))
  new("ASTNode", root=op, children=list(x))
}

.h2o.unary_scalar_op<-
function(op, x) {
  ast <- .h2o.unary_op_ast(op, x)
  .h2o.eval.scalar(ast)
}

.h2o.unary_frame_op<-
function(op, x, nrows = NA_integer_, ncols = NA_integer_, col_names = NA_character_) {
  ast <- .h2o.unary_op_ast(op, x)
  mutable <- new("H2OFrameMutableState", ast = ast, nrows = nrows, ncols = ncols, col_names = col_names)
  .newH2OFrame(id = .key.make("unary_op"), mutable = mutable)
}

.h2o.unary_row_op<-
function(op, x) {
  .h2o.unary_frame_op(op, x, nrows = x@mutable$nrows, ncols = x@mutable$ncols, col_names = x@mutable$col_names)
}

.h2o.binary_op_ast<-
function(op, e1, e2) {
  # Prep the op
  op <- new("ASTApply", op=.op.map[op])

  # Prep the LHS
  if (is(e1, "H2OFrame"))         lhs <- .get(e1)
  else {
    if (is(e1, "ASTNode"))        lhs <- e1
    else if (is.numeric(e1))      lhs <- paste0('#', e1)
    else if (is.character(e1))    lhs <- deparse(eval(e1))
    else if (is(e1, "ASTEmpty"))  lhs <- paste0('%', e1@key)
    else stop("LHS operand type not handled: ", class(e1))
  }

  # Prep the RHS
  if (is(e2, "H2OFrame"))         rhs <- .get(e2)
  else {
    if (is(e2, "ASTNode"))        rhs <- e2
    else if (is.numeric(e2))      rhs <- paste0('#', e2)
    else if (is.character(e2))    rhs <- deparse(eval(e2))
    else if (is(e2, "ASTEmpty"))  rhs <- paste0('%', e2@key)
    else stop("RHS operand type not handled: ", class(e2))
  }

  new("ASTNode", root=op, children=list(left = lhs, right = rhs))
}

.h2o.binary_scalar_op<-
function(op, e1, e2) {
  ast  <- .h2o.binary_op_ast(op, e1, e2)
  .h2o.eval.scalar(ast)
}

.h2o.binary_frame_op<-
function(op, e1, e2, nrows = NA_integer_, ncols = NA_integer_, col_names = NA_character_) {
  ast  <- .h2o.binary_op_ast(op, e1, e2)
  mutable <- new("H2OFrameMutableState", ast = ast, nrows = nrows, ncols = ncols, col_names = col_names)
  .newH2OFrame(id= .key.make("binary_op"), mutable = mutable)
}

.h2o.binary_row_op<-
function(op, e1, e2) {
  if (is(e1, "H2OFrame")) .h2o.binary_frame_op(op, e1, e2, nrows = e1@mutable$nrows, ncols = e1@mutable$ncols, col_names = e1@mutable$col_names)
  else                    .h2o.binary_frame_op(op, e1, e2, nrows = e2@mutable$nrows, ncols = e2@mutable$ncols, col_names = e2@mutable$col_names)
}

#'
#' Prefix Operation With Multiple Arguments
#'
#' Operation on an H2OFrame object with some extra parameters.
.h2o.nary_op_ast<-
function(op, ..., .args = list(...)) {
  op <- new("ASTApply", op = op)
  children <- .args.to.ast(.args = .args)
  new("ASTNode", root = op, children = children)
}

.h2o.nary_scalar_op<-
function(op, ..., .args = list(...)) {
  x <- .args[[1L]]
  ast <- .h2o.nary_op_ast(op, .args = .args)
  .h2o.eval.scalar(ast)
}

.h2o.nary_frame_op<-
function(op, ..., .args = list(...), key = .key.make("nary_op"),
         nrows = NA_integer_, ncols = NA_integer_, col_names = NA_character_) {

  ast <- .h2o.nary_op_ast(op, .args = .args)
  mutable <- new("H2OFrameMutableState", ast = ast, nrows = nrows, ncols = ncols, col_names = col_names)
  .newH2OFrame(id = key, mutable = mutable)
}

.h2o.nary_row_op<-
function(op, ..., .args = list(...)) {
  x <- .args[[1L]]
  .h2o.nary_frame_op(op, .args = .args, nrows = x@mutable$nrows, ncols = x@mutable$ncols, col_names = x@mutable$col_names)
}

.h2o.raw_expr_op<-
function(expr, ..., .args=list(...), key = .key.make("raw_expr_op")) {
  invisible(.h2o.__remoteSend(.h2o.__RAPIDS, ast=expr, id=key, method = "POST"))
}

#'
#' Ship AST to H2O for evaluation
#'
#' Force the evaluation of the AST.
#'
#' @param ast an ASTNode object
#' @param key the name of the key in h2o (hopefully matches top-most level user-defined variable)
#' @param new.assign a logical flag
#' @param deparsedExpr the deparsed expression in the calling frame
#' @param env the environment back to which we assign
#'
#' Here's a quick diagram to illustrate what is going on here
#'
.h2o.eval.scalar<-
function(ast) {
  ast <- .visitor(ast)

  # Process the results
  res <- .h2o.__remoteSend(.h2o.__RAPIDS, ast=ast, method = "POST")
  if (!is.null(res$error)) stop(paste0("Error From H2O: ", res$error), call.=FALSE)

  if (!is.null(res$string)) {
    # String or boolean result
    ret <- res$string
    if (ret == "TRUE")  ret <- TRUE
    if (ret == "FALSE") ret <- FALSE
  } else {
    # Scalar result
    ret <- res$scalar
    if (ret == "NaN") ret <- NA_real_
  }
  gc()
  ret
}

# Lazily evaluate a frame's AST.  If evaluated, fill in the nrows, ncols and col_names fields
.h2o.eval.frame<-
function(fr) {
  if( fr@mutable$computed ) return(fr)
  # Prepare the AST
  ast <- .visitor(fr@mutable$ast)

  # Execute the AST on H2O
  res <- .h2o.__remoteSend(.h2o.__RAPIDS, ast=ast, id=fr@id, method = "POST")
  if( !is.null(res$error) ) stop(paste0("Error From H2O: ", res$error), call.=FALSE)
  # Get rows, cols, col_names
  res <- .h2o.__remoteSend(paste0(.h2o.__FRAMES, "/", fr@id))$frames[[1]]
  fr@mutable$nrows = res$rows
  fr@mutable$ncols = length(res$columns)
  fr@mutable$col_names = unlist(lapply(res$columns, function(c) c$label))
  # Computed now!  No need to compute again
  fr@mutable$computed <- T
  fr@mutable$ast <- NULL
  # Clean up any dead expressions
  gc()
}

.h2o.replace.frame<-
function(ast, id) {
  # Prepare the AST
  ast <- .visitor(ast)

  # Process the results
  res <- .h2o.__remoteSend(.h2o.__RAPIDS, ast=ast, method = "POST")
  if (!is.null(res$error)) stop(paste0("Error From H2O: ", res$error), call.=FALSE)
  gc()
  .h2o.getGCFrame(id)
}

