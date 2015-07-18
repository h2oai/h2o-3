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
ARY    <- 0
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

  if (is(x, "H2OFrame")) {
    conn <- x@conn
    x <- .get(x)
  } else {
    conn <- h2o.getConnection()
    if (is(x, "ASTNode"))       x <- x
    else if (is.numeric(x))     x <- paste0('#', x)
    else if (is.character(x))   x <- deparse(eval(x))
    else if (is(x, "ASTEmpty")) x <- paste0('%', x@key)
    else stop("operand type not handled: ", class(x))
  }
  new("ASTNode", root=op, children=list(x))
}

.h2o.unary_scalar_op<-
function(op, x) {
  ast <- .h2o.unary_op_ast(op, x)
  .h2o.eval.scalar(x@conn, ast)
}

.h2o.unary_frame_op<-
function(op, x, nrows = NA_integer_, ncols = NA_integer_, col_names = NA_character_) {
  if (is(x, "H2OFrame")) finalizers <- x@finalizers
  else                   finalizers <- list()

  ast <- .h2o.unary_op_ast(op, x)
  mutable <- new("H2OFrameMutableState", ast = ast, nrows = nrows, ncols = ncols, col_names = col_names)
  .newH2OFrame("H2OFrame", conn = x@conn, frame_id = .key.make(x@conn, "unary_op"),
                finalizers = finalizers, linkToGC = TRUE, mutable = mutable)
}

.h2o.unary_row_op<-
function(op, x) {
  .h2o.unary_frame_op(op, x, nrows = x@mutable$nrows, ncols = x@mutable$ncols, col_names = x@mutable$col_names)
}

#'
#' Binary Operation
#'
#' Operation between H2OFrame objects and/or base R objects.
.h2o.binary_op_conn <-
function(e1, e2) {
  if (is(e1, "H2OFrame")) lhsconn <- e1@conn
  else                    lhsconn <- NULL

  if (is(e2, "H2OFrame")) rhsconn <- e2@conn
  else                    rhsconn <- NULL

  if (is.null(lhsconn))   lhsconn <- rhsconn
  else if (!is.null(rhsconn) && (lhsconn@ip != rhsconn@ip || lhsconn@port != rhsconn@port))
    stop("LHS and RHS are using different H2O connections")

  lhsconn
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
  conn <- .h2o.binary_op_conn(e1, e2)
  ast  <- .h2o.binary_op_ast(op, e1, e2)
  .h2o.eval.scalar(conn, ast)
}

.h2o.binary_frame_op<-
function(op, e1, e2, nrows = NA_integer_, ncols = NA_integer_, col_names = NA_character_) {
  if (is(e1, "H2OFrame") && is(e2, "H2OFrame")) finalizers <- c(e1@finalizers, e2@finalizers)
  else if (is(e1, "H2OFrame"))                  finalizers <- e1@finalizers
  else                                          finalizers <- e2@finalizers

  conn <- .h2o.binary_op_conn(e1, e2)
  ast  <- .h2o.binary_op_ast(op, e1, e2)
  mutable <- new("H2OFrameMutableState", ast = ast, nrows = nrows, ncols = ncols, col_names = col_names)
  .newH2OFrame("H2OFrame", conn = conn, frame_id= .key.make(conn, "binary_op"),
                finalizers = finalizers, linkToGC = TRUE, mutable = mutable)
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
  .h2o.eval.scalar(x@conn, ast)
}

.h2o.nary_frame_op<-
function(op, ..., .args = list(...), key = .key.make(h2o.getConnection(), "nary_op"), linkToGC = TRUE,
         nrows = NA_integer_, ncols = NA_integer_, col_names = NA_character_) {
  finalizers <- do.call(c, lapply(.args, function(x) if (is(x, "H2OFrame")) x@finalizers else list()))

  ast <- .h2o.nary_op_ast(op, .args = .args)
  mutable <- new("H2OFrameMutableState", ast = ast, nrows = nrows, ncols = ncols, col_names = col_names)
  .newH2OFrame("H2OFrame", conn = h2o.getConnection(), frame_id = key,
                finalizers = finalizers, linkToGC = linkToGC, mutable = mutable)
}

.h2o.nary_row_op<-
function(op, ..., .args = list(...)) {
  x <- .args[[1L]]
  .h2o.nary_frame_op(op, .args = .args, nrows = x@mutable$nrows, ncols = x@mutable$ncols, col_names = x@mutable$col_names)
}

.h2o.raw_expr_op<-
function(expr, ..., .args=list(...), key = .key.make(h2o.getConnection(), "raw_expr_op"), linkToGC = TRUE) {
  res <- .h2o.__remoteSend(h2o.getConnection(), h2oRestApiVersion = 99, .h2o.__RAPIDS, ast=expr, method = "POST")
  h2o.getFrame(key, h2o.getConnection(), linkToGC=linkToGC)
}

#'
#' Ship AST to H2O for evaluation
#'
#' Force the evaluation of the AST.
#'
#' @param conn an H2OConnection object
#' @param ast an ASTNode object
#' @param key the name of the key in h2o (hopefully matches top-most level user-defined variable)
#' @param finalizers a list of environment that trigger the removal of keys at R gc()
#' @param new.assign a logical flag
#' @param deparsedExpr the deparsed expression in the calling frame
#' @param env the environment back to which we assign
#'
#' Here's a quick diagram to illustrate what is going on here
#'
.h2o.eval.scalar<-
function(conn, ast) {
  key <- .key.make(conn, "rapids")
  ast <- new("ASTNode", root=new("ASTApply", op="="), children=list(left=paste0('!', key), right=ast))
  ast <- .visitor(ast)

  # Process the results
  res <- .h2o.__remoteSend(conn, h2oRestApiVersion = 99, .h2o.__RAPIDS, ast=ast, method = "POST")
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

.h2o.eval.frame<-
function(conn, ast, frame_id=.key.make(conn, "rapids"), linkToGC=FALSE) {
  # Prepare the AST
  ast <- new("ASTNode", root=new("ASTApply", op="="), children=list(left=paste0('!', frame_id), right=ast))
  ast <- .visitor(ast)

  # Process the results
  res <- .h2o.__remoteSend(conn, h2oRestApiVersion = 99, .h2o.__RAPIDS, ast=ast, method = "POST")
  if( !is.null(res$error) ) stop(paste0("Error From H2O: ", res$error), call.=FALSE)
  gc()
  h2o.getFrame(frame_id, conn, linkToGC=linkToGC)
}

.h2o.replace.frame<-
function(conn, ast, frame_id, finalizers) {
  # Prepare the AST
  ast <- .visitor(ast)

  # Process the results
  res <- .h2o.__remoteSend(conn, h2oRestApiVersion = 99, .h2o.__RAPIDS, ast=ast, method = "POST")
  if (!is.null(res$error)) stop(paste0("Error From H2O: ", res$error), call.=FALSE)

  res <- h2o.getFrame(frame_id, conn, linkToGC=FALSE)
  res@finalizers <- finalizers
  gc()
  res
}

.byref.update.frame<-
function(x, scalarAsFrame = TRUE) {
  .update_x <- function(x, temp) {
    x@mutable$ast <- temp@mutable$ast
    # FIXME: JSON returns a 1 x 1 Frame as a scalar (nrows = 0, ncols = 0)
    if ((temp@mutable$nrows == 0L) && (temp@mutable$ncols == 0L)) {
      if (scalarAsFrame) {
        x@mutable$nrows     <- 1L
        x@mutable$ncols     <- 1L
        x@mutable$col_names <- colnames(as.data.frame(x))
      }
    } else {
      x@mutable$nrows     <- temp@mutable$nrows
      x@mutable$ncols     <- temp@mutable$ncols
      x@mutable$col_names <- temp@mutable$col_names
    }
    invisible(x)
  }

  if (is.na(x@frame_id)) {
    # Nothing to do
  } else if (!is.null(x@mutable$ast) && !.is.eval(x)) {
    temp <- .h2o.eval.frame(conn = x@conn, ast = x@mutable$ast, frame_id = x@frame_id, linkToGC = FALSE)
    .update_x(x, temp)
  } else if (is.na(x@mutable$nrows) || is.na(x@mutable$ncols) || is.na(x@mutable$col_names[1L])) {
    temp <- h2o.getFrame(x@frame_id, x@conn, linkToGC = FALSE)
    .update_x(x, temp)
  }
  invisible(x)
}

#'
#' Have H2O Learn A New Function
#'
#' Convenient to have a POST function method.
.h2o.post.function<-
function(fun.ast) {
  gc()
  expr <- .fun.visitor(fun.ast)
  .h2o.__remoteSend(h2o.getConnection(), h2oRestApiVersion = 99, .h2o.__RAPIDS, fun=expr$ast, method = "POST")
}
