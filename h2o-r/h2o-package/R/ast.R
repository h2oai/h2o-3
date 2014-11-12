#
# A collection of methods to parse expressions and produce ASTs.
#
# R expressions convolved with H2O objects evaluate lazily.


#'
#' The AST visitor method.
#'
#' This method represents a map between an AST S4 object and a regular R list, which is suitable for rjson::toJSON
#'
#' Given a node, the `visitor` function recursively Lisp'ifies the node's S4 slots and then returns the list.
#'
#' The returned list has two main pieces: the ast to execute and function defintions:
#'
#'  { 'ast' : { ... }, 'funs' : {[ ... ]} }
#'
#' All ASTNodes have children. All nodes with the @@root slot has a list in the @@children slot that represent operands.
visitor<-
function(node) {
  res <- ""
  if (.hasSlot(node, "root")) {
    res %p0% '('
    res %p0% node@root@op
    children <- lapply(node@children, visitor)
    for (child in children) res %p% child
    res %p0% ')'
    list( ast = res)

  } else if (node %i% "ASTSeries") {
    res %p% node@op
    children <- unlist(lapply(node@children, visitor))
    children <- paste(children, collapse=";",sep="")
    res %p0% children
    res %p0% "}"
    res
  } else if (node %i% "ASTEmpty") {
    node@key
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
.any.h2o<-
function(expr, envir) {
  l <- unlist(recursive = T, lapply(as.list(expr), .as_list))
  a <- any( "H2OParsedData" == unlist(lapply(l, .eval_class, envir)))
  b <- any("H2OFrame" == unlist(lapply(l, .eval_class, envir)))
  any(a|b)
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
function(x, envir, sub_one = TRUE) {
  if (.any.h2o(x, envir)) return(eval(x),envir)
  .ast.walker(x,envir, FALSE, sub_one)
}

#'
#' Walk the R AST directly
#'
#' Handles all of the 1 -> 0 indexing issues.
.ast.walker<-
function(expr, envir, neg = FALSE, sub_one = TRUE) {
  sub <- ifelse(sub_one, 1, 0)
  if (length(expr) == 1) {
    if (is.symbol(expr)) { expr <- get(deparse(expr), envir); return(.ast.walker(expr, envir, neg, sub_one)) }
    if (is.numeric(expr[[1]])) return('#' %p0% (eval(expr[[1]], envir=envir) - sub))
    if (is.character(expr[[1]])) return(deparse(expr[[1]]))
    if (is.character(expr)) return(deparse(expr))
  }
  if (isGeneric(deparse(expr[[1]]))) {
    # Have a vector => ASTSeries
    if ((expr[[1]]) == quote(`c`)) {
      children <- lapply(expr[-1], .ast.walker, envir, neg, sub_one)
      # ASTSeries single numbers should have no leading '#', so strip it.
      children <- lapply(children, function(x) if (is.character(x)) gsub('#', '', x) else x)
      return(new("ASTSeries", op="{", children = children))

    # handle the negative indexing cases
    } else if (expr[[1]] == quote(`-`)) {
      # got some negative indexing!
      new_expr <- as.list(expr[-1])[[1]]
      if (length(new_expr) == 1) {
        if (is.symbol(new_expr)) new_expr <- get(deparse(new_expr), envir)
        if (is.numeric(new_expr[[1]])) return ('#-' %p0% (eval(new_expr[[1]], envir=envir)))  # do not do the +1
      }

      if (isGeneric(deparse(new_expr[[1]]))) {
        if ((new_expr[[1]]) == quote(`c`)) {
          if (!identical(new_expr[[2]][[1]], quote(`:`))) {
            children <- lapply(new_expr[-1], .ast.walker, envir, neg, sub_one)
            children <- lapply(children, function(x) if (is.character(x)) gsub('#', '', '-' %p0% x) else -x)
            children <- lapply(children, function(x) as.character( as.numeric(as.character(x)) - sub))
            return(new("ASTSeries", op="{", children=children))
          } else {
            if (length( as.list(new_expr[-1])) < 2) new_expr <- as.list(new_expr[-1])
            else return(.ast.walker(substitute(new_expr), envir, neg=TRUE, sub_one))
          }
        }
      } # otherwise `:` with negative indexing

      if (identical(new_expr[[1]], quote(`:`))) {
        return(new("ASTNode", root=new("ASTApply", op=":"),children = list('#-' %p0% (eval(new_expr[[2]],envir=envir)), '#-' %p0% (eval(new_expr[[3]],envir=envir)))))
      }
    }
    # end negative expression cases
  }

  # Create a new ASTSpan
  if (identical(expr[[1]], quote(`:`))) {
    if (!neg) return(new("ASTNode", root=new("ASTApply", op=":"), children = list('#' %p0% (eval(expr[[2]],envir=envir) - 1), '#' %p0% (eval(expr[[3]],envir=envir) - 1))))
    return(new("ASTNode", root=new("ASTApply", op=":"),children = list('#-' %p0% (eval(expr[[2]],envir=envir)), '#-' %p0% (eval(expr[[3]],envir=envir)))))
  }

  if (is.vector(expr) && is.numeric(expr)) {
      children <- lapply(expr, .ast.walker, envir, neg, sub_one)
      # ASTSeries single numbers should have no leading '#', so strip it.
      children <- lapply(children, function(x) if (is.character(x)) gsub('#', '', x) else x)
      return(new("ASTSeries", op="{", children = children))
    }
}

#'
#' Retrieve values from arguments supplied in a function call.
#'
#' Developer Note: If a method takes a function as an argument and
#'                 you wish to pass arguments to that function by the way of `...`
#'                 then you before passing flowing control to .h2o.varop, you MUST
#'                 label the `...` and list it.
#'
#'                   e.g.: Inside of ddply, we have the following "fun_args" pattern:
#'                      .h2o.varop("ddply", .data, vars, .fun, fun_args=list(...), .progress)
.get.value.from.arg<-
function(a, name=NULL) {
  if (a %i%"H2OParsedData") {
    '$' %p0% a@key
  } else if (a %i% "ASTNode") {
    a
  } else if (a %i% "ASTFun") {
    '$' %p0% a@name
  } else if (a %i% "ASTEmpty") {
    '$' %p0% a@key
  } else {
    res <- eval(a)
    if (is.null(res)) return(deparse("null"))
    if (is.vector(res)) {
      if (length(res) > 1) {
        # wrap the vector up into a ';' separated {} thingy
        tt <- paste(unlist(lapply(res, deparse)), collapse = ';', sep = ';')
        return('{' %p0%   tt  %p0% '}')
      } else {
        if (is.numeric(res)) return('#' %p0% res)
        if (is.logical(res)) return('$' %p0% res)
        else return(deparse(eval(a)))
      }
    } else {
      return(deparse(eval(a)))
    }
  }
}

.args.to.ast<-
function(..., .args = list()) {
  l <- list(...)
  if (length(.args) != 0) l <- .args
  arg.names <- names(as.list(substitute(l))[-1])
  arg_values <- NULL
  if ("fun_args" %in% arg.names) {
    arg_values <- lapply(seq_along(l), function(i) {
        if (names(l[i]) == "fun_args") {
          paste(unlist(lapply(unlist(l[i]), function(i) { .get.value.from.arg(i, "") })), collapse= ' ')
        } else .get.value.from.arg(l[[i]], names(l)[i])
      })
  } else {
    arg_values <- lapply(seq_along(l), function(i) { .get.value.from.arg(l[[i]], names(l)[i]) })
  }
  return(arg_values)
}