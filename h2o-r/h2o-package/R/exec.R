#'
#' R -> AST
#'
#' This is the front-end of the execution interface between R and H2O.
#'
#' The workhorses of this front end are .h2o.unop, .h2o.binop, and .h2o.varop.
#'
#' Together, these three methods handle all of the available operations that can
#' be done with H2OFrame objects (this includes H2OFrame objects and ASTNode objects).

#'
#' Rapids End point
#'
.h2o.__RAPIDS <- "Rapids.json"

#'
#' Prefix Operation With A Single Argument
#'
#' Operation on an object that inherits from H2OFrame.
.h2o.unop<-
function(op, x) {
  if (!is.na(.op.map[op])) op <- .op.map[op]
  op <- new("ASTApply", op = op)

  if (is(x, "H2OFrame"))      x <- .get(x)
  else if (is(x, "ASTNode"))  x <- x
  else if (is.numeric(x))     x <- paste0('#', x)
  else if (is.character(x))   x <- deparse(eval(x))
  else if (is(x, "ASTEmpty")) x <- paste0('%', x@key)
  else stop("operand type not handled: ", class(x))

  ast <- new("ASTNode", root=op, children=list(x))
  new("H2OFrame", ast = ast, key = .key.make(), h2o = .retrieveH2O())
}

#'
#' Binary Operation
#'
#' Operation between H2OFrame objects and/or base R objects.
.h2o.binop<-
function(op, e1, e2) {
  # Prep the op
  op <- new("ASTApply", op=.op.map[op])

  # Prep the LHS
  if (is(e1, "H2OFrame"))       lhs <- .get(e1)
  else if (is(e1, "ASTNode"))   lhs <- e1
  else if (is.numeric(e1))      lhs <- paste0('#', e1)
  else if (is.character(e1))    lhs <- deparse(eval(e1))
  else if (is(e1, "ASTEmpty"))  lhs <- paste0('%', e1@key)
  else stop("LHS operand type not handled: ", class(e1))

  # Prep the RHS
  if (is(e2, "H2OFrame"))       rhs <- .get(e2)
  else if (is(e2, "ASTNode"))   rhs <- e2
  else if (is.numeric(e2))      rhs <- paste0('#', e2)
  else if (is.character(e2))    rhs <- deparse(eval(e2))
  else if (is(e2, "ASTEmpty"))  rhs <- paste0('%', e2@key)
  else stop("RHS operand type not handled: ", class(e2))

  # Return an ASTNode
  ast <- new("ASTNode", root=op, children=list(left = lhs, right = rhs))
  new("H2OFrame", ast = ast, key = .key.make(), h2o = .retrieveH2O())
}

#'
#' Prefix Operation With Multiple Arguments
#'
#' Operation on an H2OFrame object with some extra parameters.
.h2o.varop<-
function(op, ..., .args = list(...), .key = .key.make()) {
  op <- new("ASTApply", op = op)
  children <- .args.to.ast(.args = .args)
  ast <- new("ASTNode", root = op, children = children)
  new("H2OFrame", ast = ast, key = .key, h2o = .retrieveH2O())
}

#'
#' Ship AST to H2O for evaluation
#'
#' Force the evaluation of the AST.
#'
#' @param ast an ASTNode object
#' @param caller.ID the name of the object in the calling frame
#' @param env the environment back to which we assign
#' @param h2o.ID the name of the key in h2o (hopefully matches top-most level user-defined variable)
#' @param conn an H2OConnection object
#' @param new.assign a logical flag
#'
#' Here's a quick diagram to illustrate what is going on here
#'
.force.eval<-
function(ast, caller.ID=NULL, env = parent.frame(2), h2o.ID=NULL, conn=NULL, new.assign=TRUE) {
  if (is.null(conn)) conn <- .retrieveH2O(parent.frame())
  ret <- ""
  if (is.null(h2o.ID)) h2o.ID <- .key.make()
  if (new.assign) ast <- h2o.ID %<-% ast
  ast <- visitor(ast)
  res <- .h2o.__remoteSend(conn, .h2o.__RAPIDS, ast=ast)
  if (!is.null(res$error)) stop(res$error, call.=FALSE)
  if (!is.null(res$string)) {
    ret <- res$string
    if (ret == "TRUE")  ret <- TRUE
    if (ret == "FALSE") ret <- FALSE
  } else if (res$result == "") {
    ret <- .h2o.parsedData(conn, res$key$name, res$num_rows, res$num_cols, res$col_names)
    ret@key <- if(is.null(h2o.ID)) NA_character_ else h2o.ID
  } else {
    ret <- res$scalar
    if (ret == "NaN") ret <- NA
  }
  if (!is.null(caller.ID) && exists(caller.ID, envir=env)) {
    if (is(ret, "H2OFrame")) {
      assign(caller.ID, ret, env)
    } else {
      expr <- paste0(caller.ID, "@ast <- NULL")
      eval(parse(text=expr), env)
#      expr <- paste0(caller.ID, "@scalar <- ", ret)
#      eval(parse(text=expr), env)
    }
  }
  ret
}

#'
#' Have H2O Learn A New Function
#'
#' Convenient to have a POST function method.
.h2o.post.function<-
function(fun.ast) {
  expr <- .fun.visitor(fun.ast)
  res <- .h2o.__remoteSend(.retrieveH2O(parent.frame()), .h2o.__RAPIDS, funs=.collapse(expr))
}
