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
#' Check if any item in the expression is an H2OFrame object.
#'
#' Useful when trying to unravel an expression
.any.h2o<-
function(expr, envir) {
  l <- unlist(lapply(as.list(expr), .as_list), recursive = TRUE)
  any("H2OFrame" == unlist(lapply(l, .eval_class, envir)))
}

#'
#' Assign the value into the correct environment.
.eval.assign<-
function(x, ID, top_level_envir, calling_envir) {
  .force.eval(h2o.getConnection(), x, ID = ID, rID = 'x')
  ID <- ifelse(ID == "Last.value", ID, x@key)
  assign(ID, x, top_level_envir)
  ID
}

#'
#' Convert R expression to an AST.
#'
.eval<-
function(x, envir, sub_one = TRUE) {
  if (.any.h2o(x, envir)) return(.ast.walker(eval(x,envir),envir,FALSE,sub_one))
  .ast.walker(x,envir, FALSE, sub_one)
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
    if (is.numeric(expr[[1L]])) return(paste0('#', eval(expr[[1L]], envir=envir) - sub))
    if (is.character(expr[[1L]])) return(deparse(expr[[1L]]))
    if (is.character(expr)) return(deparse(expr))
  }
  if (isGeneric(deparse(expr[[1L]]))) {
    # Have a vector => ASTSeries
    if ((expr[[1L]]) == quote(`c`)) {
      children <- lapply(expr[-1L], .ast.walker, envir, neg, sub_one)
      return(new("ASTSeries", op="{", children = children))

    # handle the negative indexing cases
    } else if (expr[[1L]] == quote(`-`)) {
      # got some negative indexing!

      # disallow binary ops here
      if (length(expr) == 3L) {  # have a binary operation, e.g. 50 - 1
        stop("Unimplemented: binary operations (+, -, *, /) within a slice.")
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
            return(new("ASTSeries", op="{", children=children))
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
    }
    # end negative expression cases
  }

  # Create a new ASTSpan
  if (identical(expr[[1L]], quote(`:`))) {
    if (neg)
      return(new("ASTNode", root = new("ASTApply", op = ":"),
                 children = list(paste0('#-', eval(expr[[2L]], envir = envir)),
                                 paste0('#-', eval(expr[[3L]], envir = envir)))))
    else
      return(new("ASTNode", root = new("ASTApply", op = ":"),
                 children = list(paste0('#', eval(expr[[2L]], envir = envir) - 1L),
                                 paste0('#', eval(expr[[3L]], envir = envir) - 1L))))
  }

  if (is.vector(expr) && is.numeric(expr)) {
    children <- lapply(expr, .ast.walker, envir, neg, sub_one)
    return(new("ASTSeries", op="{", children = children))
  }
  stop("No suitable AST could be formed from the expression.")
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
    if (is.null(res)) return(deparse("null"))
    if (is.vector(res)) {
      if (length(res) > 1L) {
        if (is.numeric(res)) res <- as.numeric(res)
        # wrap the vector up into a ';' separated {} thingy
        tt <- paste(unlist(lapply(res, deparse)), collapse = ';', sep = ';')
        return(paste0('{', tt, '}'))
      } else {
        if (is.numeric(res)) return(paste0('#', res))
        if (is.logical(res)) return(paste0('%', res))
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