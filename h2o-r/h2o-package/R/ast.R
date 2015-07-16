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
.visitor<-
function(node) {
  if (is.list(node))
    unlist(lapply(node, .visitor), use.names = FALSE)
  else if (is(node, "ASTNode") || is(node, "ASTSpan"))
    paste0("(", node@root@op, " ", paste0(.visitor(node@children), collapse = " "), ")")
  else if (is(node, "ASTSeries"))
    paste0(" ", node@op, paste0(.visitor(node@children), collapse = ";"), "}")
  else if (is(node, "ASTEmpty"))
    node@key
  else if (is(node, "H2OFrame"))
    .visitor(.get(node))
  else
    node
}

#'
#' Get the key or AST
#'
#' Key points to a bonified object in the H2O cluster
.get <- function(H2OFrame) {
  if(.is.eval(H2OFrame))
    paste0('%', H2OFrame@frame_id)
  else
    H2OFrame@mutable$ast
}

#'
#' Check if key points to bonified object in H2O cluster.
#'
.is.eval <- function(H2OFrame) {
  key <- H2OFrame@frame_id
  res <- .h2o.__remoteSend(H2OFrame@conn, h2oRestApiVersion = 99, paste0(.h2o.__RAPIDS, "/isEval"), ast_key=key)
  res$evaluated
}

#'
#' Get the class of the object from the envir.
#'
#' The environment is the parent frame

#'
#' Helper function to recursively unfurl an expression into a list of statements/exprs/calls/names.
#'
.as_list<-
function(expr) {
  if (is.call(expr))
    lapply(as.list(expr), .as_list)
  else
    expr
}

#'
#' Convert R expression to an AST.
#'
.eval<-
function(x, envir, sub_one = TRUE) {
  statements <- unlist(lapply(as.list(x), .as_list), recursive = TRUE)
  anyH2OFrame <- FALSE
  for (i in statements) {
    anyH2OFrame <- tryCatch(is(i, "H2OFrame") ||
                            is(get(as.character(i), envir), "H2OFrame"),
                            error = function(e) FALSE)
    if (anyH2OFrame)
      break
  }
  if (anyH2OFrame)
    x <- eval(x, envir)
  .ast.walker(x, envir, FALSE, sub_one)
}

#'
#' Walk the R AST directly
#'
#' Handles all of the 1 -> 0 indexing issues.
#' TODO: this method needs to be cleaned up and re-written
.ast.walker<-
function(expr, envir, neg = FALSE, sub_one = TRUE) {
  sub <- as.integer(sub_one)
  if (length(expr) == 1L) {
    if (is.symbol(expr)) { expr <- get(deparse(expr), envir); return(.ast.walker(expr, envir, neg, sub_one)) }
    if (is.numeric(expr[[1L]])) {
      if( expr < 0 ) sub <- 0
      return(paste0('#', eval(expr[[1L]], envir=envir) - sub))
    }
    if (is.character(expr[[1L]])) return(deparse(expr[[1L]]))
    if (is.character(expr)) return(deparse(expr))
  }
  if (isGeneric(deparse(expr[[1L]]))) {
    # Have a vector => make a list
    if ((expr[[1L]]) == quote(`c`)) {
      children <- lapply(expr[-1L], .ast.walker, envir, neg, sub_one)
      if( length(children)==1 ) {
        if( is(children[[1]], "ASTNode") ) return(children)
      }
      op <- new("ASTApply", op="llist")
      if( is(children[[1]], "ASTNode") ) { return(new("ASTNode", root=op, children=children)) }
      if( !(substr(children[[1]],1,1) == "#") ) { op <- new("ASTApply", op="slist") }
      return(new("ASTNode", root=op, children=children))

    # handle the negative indexing cases
    } else if (expr[[1L]] == quote(`-`)) {
      # got some negative indexing!

      # disallow binary ops here
      if (length(expr) == 3L) {  # have a binary operation, e.g. 50 - 1
        return(.eval(eval(expr,envir)))
      }

      new_expr <- as.list(expr[-1L])[[1L]]
      if (length(new_expr) == 1L) {
        if (is.symbol(new_expr)) new_expr <- get(deparse(new_expr), envir)
        if (is.numeric(new_expr[[1L]])) return(paste0('#-', eval(new_expr[[1L]], envir=envir)))  # do not do the +1
      }

      if (isGeneric(deparse(new_expr[[1L]]))) {
        if ((new_expr[[1L]]) == quote(`c`)) {
          if (!identical(new_expr[[2L]][[1L]], quote(`:`))) {
            children <- lapply(new_expr[-1L], .ast.walker, envir, neg, sub_one)
            children <- lapply(children, function(x) if (is.character(x)) gsub('#', '', paste0('-', x)) else -x) # scrape off the '#', put in the - and continue...
            children <- lapply(children, function(x) paste0('#', as.numeric(as.character(x)) - sub))
            op <- new("ASTApply", op="llist")
            return(new("ASTNode", root=op, children=children))
          } else {
            if (length(as.list(new_expr[-1L])) < 2L) new_expr <- as.list(new_expr[-1L])
            else return(.ast.walker(substitute(new_expr), envir, neg=TRUE, sub_one))
          }
        }
      }

      # otherwise `:` with negative indexing
      if (identical(new_expr[[1L]][[1L]], quote(`:`))) {
        return(new("ASTNode", root = new("ASTApply", op = ":"),
               children = list(paste0('#-', eval(new_expr[[1L]][[2L]], envir = envir)),
                               paste0('#-', eval(new_expr[[1L]][[3L]], envir = envir)))))
      }
    } else if (length(expr) == 3L) {  # have a binary operation, e.g. 50 - 1
      return(.eval(eval(expr,envir),envir))
    }
    # end negative expression cases
  }

  # Create a new ASTSpan
  if (identical(expr[[1L]], quote(`:`))) {
    if( eval(expr[[2L]],envir) < 0 ) {
      neg <- TRUE
      if( eval(expr[[3L]],envir) >= 0) stop("Index range must not include positive and negative values.")
    }
    if (neg)
      return(new("ASTNode", root = new("ASTApply", op = ":"),
                 children = list(paste0('#', eval(expr[[2L]], envir = envir)+1L),
                                 paste0('#', eval(expr[[3L]], envir = envir)+1L))))
    else
      return(new("ASTNode", root = new("ASTApply", op = ":"),
                 children = list(paste0('#', eval(expr[[2L]], envir = envir) - 1L),
                                 paste0('#', eval(expr[[3L]], envir = envir) - 1L))))
  }

  if (is.vector(expr) && is.numeric(expr)) {
    neg <- expr[1] < 0
    sub_one <- !neg
    if( neg ) { expr <- expr + 1 }
    children <- lapply(expr, .ast.walker, envir, neg, sub_one)
    op <- new("ASTApply", op="llist")
    if( !(substr(children[[1]],1,1) == "#") ) { op <- new("ASTApply", op="slist") }
    return(new("ASTNode", root=op, children=children))
  }
  stop("No suitable AST could be formed from the expression.")
}

#'
#' Retrieve values from arguments supplied in a function call.
#'
#' Developer Note: If a method takes a function as an argument and
#'                 you wish to pass arguments to that function by the way of `...`
#'                 then you before passing flowing control to .h2o.nary_op, you MUST
#'                 label the `...` and list it.
#'
#'                   e.g.: Inside of ddply, we have the following "fun_args" pattern:
#'                      .h2o.nary_op("ddply", .data, vars, .fun, fun_args=list(...), .progress)
.get.value.from.arg<-
function(a, name=NULL) {
  if (is(a, "H2OFrame")) {
    .get(a)
  } else if (is(a, "ASTNode")) {
    a
  } else if (is(a, "ASTFun")) {
    paste0('%', a@name)
  } else if (is(a, "ASTEmpty")) {
    paste0('%', a@key)
  } else {
    res <- eval(a)
    if (is.null(res)) {
      "()"
    } else if (is.vector(res)) {
      if (length(res) > 1L) {
        if (is.numeric(res)) res <- as.numeric(res)
        if( is.numeric(res) && all(res%%1==0)) {
          tt <- paste0('#', res, collapse=" ")
          paste0("(llist ", tt, ")")
        } else if( is.numeric(res) ) {
          tt <- paste0('#', res, collapse=" ")
          paste0("(dlist ", tt, ")")
        } else {
          tt <- paste0(unlist(lapply(res, deparse)), collapse= " ")
          paste0("(slist ", tt, ")")
        }
      } else if (is.numeric(res)) {
        paste0('#', res)
      } else if (is.logical(res)) {
        paste0('%', res)
      } else {
        deparse(eval(a))
      }
    } else {
      deparse(eval(a))
    }
  }
}

.args.to.ast<-
function(..., .args = list()) {
  l <- list(...)
  if (length(.args) != 0L) l <- .args
  arg.names <- names(as.list(substitute(l))[-1L])
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
  arg_values
}
