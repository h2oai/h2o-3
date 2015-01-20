#'
#' R -> AST
#'
#' This is the front-end of the execution interface between R and H2O.
#'
#' The workhorses of this front end are .h2o.unary_op, .h2o.binary_op, and .h2o.nary_op.
#'
#' Together, these three methods handle all of the available operations that can
#' be done with H2OFrame objects (this includes H2OFrame objects and ASTNode objects).

#'
#' Prefix Operation With A Single Argument
#'
#' Operation on an object that inherits from H2OFrame.
.h2o.unary_op<-
function(op, x) {
  if (!is.na(.op.map[op])) op <- .op.map[op]
  op <- new("ASTApply", op = op)

  if (is(x, "H2OFrame")) {
    conn <- x@conn
    finalizers <- x@finalizers
    x <- .get(x)
  } else {
    conn <- h2o.getConnection()
    finalizers <- list()
    if (is(x, "ASTNode"))       x <- x
    else if (is.numeric(x))     x <- paste0('#', x)
    else if (is.character(x))   x <- deparse(eval(x))
    else if (is(x, "ASTEmpty")) x <- paste0('%', x@key)
    else stop("operand type not handled: ", class(x))
  }
  ast <- new("ASTNode", root=op, children=list(x))
  .newH2OObject("H2OFrame", ast = ast, conn = conn, key = .key.make(conn, "unary_op"), finalizers = finalizers, linkToGC = TRUE)
}

#'
#' Binary Operation
#'
#' Operation between H2OFrame objects and/or base R objects.
.h2o.binary_op<-
function(op, e1, e2) {
  # Prep the op
  op <- new("ASTApply", op=.op.map[op])

  # Prep the LHS
  if (is(e1, "H2OFrame")) {
    lhsconn <- e1@conn
    finalizers <- e1@finalizers
    lhs <- .get(e1)
  } else {
    lhsconn <- h2o.getConnection()
    finalizers <- list()
    if (is(e1, "ASTNode"))        lhs <- e1
    else if (is.numeric(e1))      lhs <- paste0('#', e1)
    else if (is.character(e1))    lhs <- deparse(eval(e1))
    else if (is(e1, "ASTEmpty"))  lhs <- paste0('%', e1@key)
    else stop("LHS operand type not handled: ", class(e1))
  }

  # Prep the RHS
  if (is(e2, "H2OFrame")) {
    rhsconn <- e2@conn
    finalizers <- c(finalizers, e2@finalizers)
    rhs <- .get(e2)
  } else {
    rhsconn <- h2o.getConnection()
    if (is(e2, "ASTNode"))        rhs <- e2
    else if (is.numeric(e2))      rhs <- paste0('#', e2)
    else if (is.character(e2))    rhs <- deparse(eval(e2))
    else if (is(e2, "ASTEmpty"))  rhs <- paste0('%', e2@key)
    else stop("RHS operand type not handled: ", class(e2))
  }

  if (lhsconn@ip != rhsconn@ip || lhsconn@port != rhsconn@port)
    stop("LHS and RHS are using different H2O connections")

  ast <- new("ASTNode", root=op, children=list(left = lhs, right = rhs))
  .newH2OObject("H2OFrame", ast = ast, conn = lhsconn, key = .key.make(lhsconn, "binary_op"), finalizers = finalizers, linkToGC = TRUE)
}

#'
#' Prefix Operation With Multiple Arguments
#'
#' Operation on an H2OFrame object with some extra parameters.
.h2o.nary_op<-
function(op, ..., .args = list(...), .key = .key.make(h2o.getConnection(), "nary_op")) {
  op <- new("ASTApply", op = op)
  finalizers <- do.call(c, lapply(.args, function(x) if (is(x, "H2OFrame")) x@finalizers else list()))
  children <- .args.to.ast(.args = .args)
  ast <- new("ASTNode", root = op, children = children)
  .newH2OObject("H2OFrame", ast = ast, conn = h2o.getConnection(), key = .key, finalizers = finalizers, linkToGC = TRUE)
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
.force.eval<-
function(conn, ast, key=.key.make(conn, "rapids"), finalizers=list(), new.assign=TRUE, deparsedExpr=NULL, env=parent.frame()) {
  # Prepare the AST
  if (new.assign) {
    if (is(key, "H2OFrame")) key <- key@key
    ast <- new("ASTNode", root=new("ASTApply", op="="), children=list(left=paste0('!', key), right=ast))
  }
  ast <- .visitor(ast)

  # Process the results
  res <- .h2o.__remoteSend(conn, .h2o.__RAPIDS, ast=ast, method = "POST")
  if (!is.null(res$error)) stop(res$error, call.=FALSE)
  if (!is.null(res$string)) {
    # String or boolean result
    ret <- res$string
    if (ret == "TRUE")  ret <- TRUE
    if (ret == "FALSE") ret <- FALSE
  } else if (res$result == "") {
    # H2OFrame result
    ret <- .h2o.parsedData(conn, res$key$name, res$num_rows, res$num_cols, res$col_names, linkToGC=FALSE)
    ret@key <- if(is.null(key)) NA_character_ else key
    ret@finalizers <- c(ret@finalizers, finalizers)
  } else {
    # Scalar result
    ret <- res$scalar
    if (ret == "NaN") ret <- NA_real_
  }

  # Modify an H2OFrame object in another environment
  # TODO Refactor H2OFrame class to be an S4 RefClass to avoid this non-standard operation
  if (!is.null(deparsedExpr) && exists(deparsedExpr, envir=env)) {
    if (is(ret, "H2OFrame")) {
      assign(deparsedExpr, ret, env)
    } else {
      expr <- paste0(deparsedExpr, "@ast <- NULL")
      eval(parse(text=expr), env)
    }
  }

  # Return the value
  ret
}

#'
#' Have H2O Learn A New Function
#'
#' Convenient to have a POST function method.
.h2o.post.function<-
function(fun.ast) {
  expr <- .fun.visitor(fun.ast)
  .h2o.__remoteSend(h2o.getConnection(), .h2o.__RAPIDS, funs=.collapse(expr), method = "POST")
}
