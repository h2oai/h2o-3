#'
#' R -> AST
#'
#' This is the front-end of the execution interface between R and H2O.
#'
#' The workhorses of this front end are .h2o.unop, .h2o.binop, and .h2o.varop.
#'
#' Together, these three methods handle all of the available operations that can
#' be done with H2OFrame objects (this includes H2OParsedData objects and ASTNode objects).

#'
#' Prefix Operation With A Single Argument
#'
#' Operation on an object that inherits from H2OFrame.
.h2o.unop<-
function(op, x) {
  if (!is.null(.op.map[[op]])) op <- .op.map[[op]]
  op <- new("ASTApply", op = op)
  if (x %i% "ASTNode") x <- x
  if (x %i% "numeric") x <- '#' %p0% x
  if (x %i% "character") x <- deparse(eval(x))
  if (x %i% "H2OParsedData") x <- '$' %p0% x@key
  new("ASTNode", root=op, children=list(x))
}

#'
#' Binary Operation
#'
#' Operation between H2OFrame objects and/or base R objects.
.h2o.binop<-
function(op, e1, e2) {

  # Prep the op
  op <- new("ASTApply", op=.op.map[[op]])

  # Prep the LHS
  if (e1 %i% "ASTNode")       lhs <- e1
  if (e1 %i% "numeric")       lhs <- '#' %p0% e1
  if (e1 %i% "character")     lhs <- deparse(eval(e1))
  if (e1 %i% "H2OParsedData") lhs <- '$' %p0% e1@key
  # TODO: e1 inherits ASTFun ?

  # Prep the RHS
  if (e2 %i% "ASTNode")       rhs <- e2
  if (e2 %i% "numeric")       rhs <- '#' %p0% e2
  if (e2 %i% "character")     rhs <- deparse(eval(e2))
  if (e2 %i% "H2OParsedData") rhs <- '$' %p0% e2@key
  # TODO: e2 inherits ASTFun ?

  # Return an ASTNode
  new("ASTNode", root=op, children=list(left = lhs, right = rhs))
}

#'
#' Prefix Operation With Multiple Arguments
#'
#' Operation on an H2OFrame object with some extra parameters.
.h2o.varop<-
function(op, ...) {
  op <- new("ASTApply", op = op)
  ASTargs <- .argsToAST(...)
  new("ASTNode", root=op, children=ASTargs)
}

#'
#' Ship AST to H2O for evaluation.
#'
#' Force the evaluation of an AST
#'
#' This function is never called directly. The object shall never be a phrase!
.force.eval<-
function(client, Last.value, ID, rID = NULL, env = parent.frame()) {
  ret <- ""
  if(length(as.list(substitute(Last.value))) > 1)
    stop(paste("Found phrase: ", substitute(Last.value), ". Illegal usage.", sep = ""))

  if (!is.null(ID)) Last.value <- ID %<-% Last.value
  expr <- visitor(Last.value)

  print("AST: ")
  print(expr$ast)
#  stop("end")

  # Have H2O evaluate the AST
  res <- .h2o.__remoteSend(client, .h2o.__RAPIDS, ast=expr$ast)

  if (!is.null(res$exception)) stop(res$exception, call.=FALSE)
  ID <- ifelse(ID == "Last.value", ID, as.character(as.list(match.call())$Last.value))
  if (!is.null(rID)) ID <- rID
  if (!is.null(res$string)) {
    ret <- res$string
    if (ret == "TRUE") ret = TRUE
    if (ret == "FALSE") ret = FALSE
  }
  else if (res$result == "") {
    ret <- .h2o.parsedData(client, res$key$name, res$num_rows, res$num_cols, res$col_names)
  } else {
    ret <- res$scalar
    if (ret == "NaN") ret <- NA
  }
  if (!is.null(ID)) assign(ID, ret, env = env)
  else assign(rID, ret, env = env)
}

#cat(toJSON(visitor(h2o.cut(hex[,1], seq(0,1,0.01)))), "\n")
#cat(toJSON(visitor( h2o.ddply(hex, .("Sepal.Length", "Sepal.Width", "Petal.Length"), f, "hello", "from", "ddply") )), "\n")
