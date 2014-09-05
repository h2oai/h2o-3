#'
#' A collection of methods to parse expressions and produce ASTs.
#'
#' R expressions convolved with H2O objects evaluate lazily.


#'
#' The AST visitor method.
#'
#' This method represents a map between an AST S4 object and a regular R list, which is suitable for rjson::toJSON call.
#'
#' Given a node, the `visitor` function recursively Lisp'ifies the node's S4 slots and then returns the list.
#'
#' The returned list has two main pieces: the ast to execute and function defintions:
#'
#'  { 'ast' : { ... }, 'funs' : {[ ... ]} }
#'
#' All ASTNodes have children. All nodes with the @root slot has a list in the @children slot that represent operands.
visitor<-
function(node) {
  res <- ""
  if (.hasSlot(node, "root")) {
    res %<p0-% '('
    res %<p0-% node@root@op
    children <- lapply(node@children, visitor)
    for (child in children) res %<p-% child
    res %<p0-% ')'
    list( ast = res)

  } else if (node %<i-% "ASTSeries") {
    res %<p-% node@op
    children <- unlist(lapply(node@children, visitor))
    children <- paste(children, collapse=";",sep="")
    res %<p0-% children
    res %<p0-% "}"
    res
  } else {
    node
  }
}

#'
#' Get the class of the object from the envir.
#'
#' The environment is the parent frame
.eval_class<-
function(i, envir) {
  val <- tryCatch(class(get(as.character(i), envir)), error = function(e) {
    tryCatch(class(i), error = function(e) {
      return(NA)
    })
  })
}

#'
#' Helper function to recursively unfurl an expression into a list of statements/exprs/calls/names.
#'
.as_list<-
function(expr) {
  if (is.call(expr)) {
    return(lapply(as.list(expr), .as_list))
  }
  return(expr)
}

#'
#' Check if any item in the expression is an H2OParsedData object.
#'
#' Useful when trying to unravel an expression
.anyH2O<-
function(expr, envir) {
 l <- unlist(recursive = T, lapply(as.list(expr), .as_list))
 a <- any( "H2OParsedData" == unlist(lapply(l, .eval_class, envir)))
 b <- any("H2OFrame" == unlist(lapply(l, .eval_class, envir)))
 any(a | b)
}

#'
#' Assign the value into the correct environment.
.eval.assign<-
function(x, ID, top_level_envir, calling_envir) {
  .force.eval(.retrieveH2O(top_level_envir), x, ID = ID, rID = 'x')
  ID <- ifelse(ID == "Last.value", ID, x@key)
  assign(ID, x, top_level_envir)
  ID
}

#'
#' Convert R expression to an AST.
#'
.eval<-
function(x, envir) {
  if (.anyH2O(x, envir)) return(eval(x),envir)
  .ast.walker(x,envir)
}

#'
#' Walk the R AST directly
#'
.ast.walker<-
function(expr, envir) {
  if (length(expr) == 1) {
    if (is.numeric(expr[[1]])) return('#' %<p0-% (eval(expr[[1]], envir=envir) - 1))
  }
  if (isGeneric(deparse(expr[[1]]))) {
    # Have a vector => ASTSeries
    if ((expr[[1]]) == quote(`c`)) {
    children <- lapply(expr[-1], .ast.walker, envir)
    # ASTSeries single numbers should have no leading '#', so strip it.
    children <- lapply(children, function(x) if (is.character(x)) gsub('#', '', x) else x)
    return(new("ASTSeries", op="{", children = children))
    }
  }

  # Create a new ASTSpan
  if (identical(expr[[1]], quote(`:`))) {
    return(new("ASTNode", root=new("ASTApply", op=":"), children = list('#' %<p0-% (eval(expr[[2]],envir=envir) - 1), '#' %<p0-% (eval(expr[[3]],envir=envir) - 1))))
  }
}
