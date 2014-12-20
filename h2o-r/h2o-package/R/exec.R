#'
#' R -> AST
#'
#' This is the front-end of the execution interface between R and H2O.
#'
#' The workhorses of this front end are .h2o.unop, .h2o.binop, and .h2o.varop.
#'
#' Together, these three methods handle all of the available operations that can
#' be done with h2o.frame objects (this includes h2o.frame objects and ASTNode objects).

#'
#' Rapids End point
#'
.h2o.__RAPIDS <- "Rapids.json"

#'
#' Prefix Operation With A Single Argument
#'
#' Operation on an object that inherits from h2o.frame.
.h2o.unop<-
function(op, x) {
  if (!is.na(.op.map[op])) op <- .op.map[op]
  op <- new("ASTApply", op = op)
  if (x %i% "h2o.frame") x <- .get(x)
  else if (x %i% "ASTNode") x <- x
  else if (x %i% "numeric") x <- '#' %p0% x
  else if (x %i% "character") x <- deparse(eval(x))
  else if (x %i% "ASTEmpty") x <- '%' %p0% x@key
  else stop("operand type not handled")
  ast <- new("ASTNode", root=op, children=list(x))
  new("h2o.frame", ast = ast, key = .key.make(), h2o = .retrieveH2O())
}

#'
#' Binary Operation
#'
#' Operation between h2o.frame objects and/or base R objects.
.h2o.binop<-
function(op, e1, e2) {

  # Prep the op
  op <- new("ASTApply", op=.op.map[op])

  # Prep the LHS
  if (e1 %i% "h2o.frame")      lhs <- .get(e1)
  else if (e1 %i% "ASTNode")   lhs <- e1
  else if (e1 %i% "numeric")   lhs <- '#' %p0% e1
  else if (e2 %i% "integer")   lhs <- '#' %p0% as.numeric(e1)
  else if (e1 %i% "character") lhs <- deparse(eval(e1))
  else if (e1 %i% "ASTEmpty")  lhs <- '%' %p0% e1@key
  else stop("LHS operand type not handled")

  # Prep the RHS
  if (e2 %i% "h2o.frame")       rhs <- .get(e2)
  else if (e2 %i% "ASTNode")    rhs <- e2
  else if (e2 %i% "numeric")    rhs <- '#' %p0% e2
  else if (e2 %i% "integer")    rhs <- '#' %p0% as.numeric(e2)
  else if (e2 %i% "character")  rhs <- deparse(eval(e2))
  else if (e2 %i% "ASTEmpty")   rhs <- '%' %p0% e2@key
  else stop("RHS operand type not handled: " %p% class(e2))

  # Return an ASTNode
  ast <- new("ASTNode", root=op, children=list(left = lhs, right = rhs))
  new("h2o.frame", ast = ast, key = .key.make(), h2o = .retrieveH2O())
}

#'
#' Prefix Operation With Multiple Arguments
#'
#' Operation on an h2o.frame object with some extra parameters.
.h2o.varop<-
function(op, ..., .args=list(), useKey=NULL) {
  op <- new("ASTApply", op = op)
  if (length(.args) == 0) ASTargs <- .args.to.ast(...)
  else ASTargs <- .args.to.ast(.args=.args)
  ast <- new("ASTNode", root=op, children=ASTargs)
  key <- if(is.null(useKey)) .key.make() else useKey
  new("h2o.frame", ast = ast, key = key, h2o = .retrieveH2O())
}

#'
#' Ship AST to H2O for evaluation
#'
#' Force the evaluation of the AST.
#'
#' @param h2o: an h2o.client object
#' @param ast: an ast.node object
#' @param h2o.ID: the name of the key in h2o (hopefully matches top-most level user-defined variable)
#' @param parent.ID: the name of the object in the calling frame
#' @param env: the environment back to which we assign
#'
#' Here's a quick diagram to illustrate what is going on here
#'
.force.eval<-
function(ast, caller.ID=NULL, env = parent.frame(2), h2o.ID=NULL, h2o=NULL, new.assign=TRUE) {
  if (is.null(h2o)) h2o <- .retrieveH2O(parent.frame())
  ret <- ""
  if (is.null(h2o.ID)) h2o.ID <- .key.make()
  if (new.assign) ast <- h2o.ID %<-% ast
  expr <- visitor(ast)

  res <- .h2o.__remoteSend(h2o, .h2o.__RAPIDS, ast=expr$ast)
  if (!is.null(res$exception)) stop(res$exception, call.=FALSE)
  if (!is.null(res$string)) {
    ret <- res$string
    if (ret == "TRUE") ret = TRUE
    if (ret == "FALSE") ret = FALSE
  } else if (res$result == "") {
    ret <- .h2o.parsedData(h2o, res$key$name, res$num_rows, res$num_cols, res$col_names)
    ret@key <- h2o.ID
  } else {
    ret <- res$scalar
    if (ret == "NaN") ret <- NA
  }
  if (!is.null(caller.ID) && exists(caller.ID, envir=env)) {
    if (ret %i% "h2o.frame") {
      assign(caller.ID, ret, env)
    } else {
      expr <- caller.ID %p0% "@ast" %p% "<- NULL"
      eval(parse(text=expr), env)
#      expr <- caller.ID %p0% "@scalar" %p% "<- " %p0% ret
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
