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
  if( is.list(node) )                                    unlist(lapply(node, .visitor), use.names = FALSE)
  else if( is(node, "ASTNode") || is(node, "ASTSpan") )  paste0("(", node@root@op, " ", paste0(.visitor(node@children), collapse = " "), ")")
  else if( is(node, "ASTSeries") )                       paste0(" ", node@op, paste0(.visitor(node@children), collapse = ";"), "}")
  else if( is(node, "ASTEmpty") )                        node@key
  else if( is(node, "H2OFrame") ) {                              # Frames often cache results in temps
    if( node@mutable$computed )                          node@id # Precomputed, re-use via temp name
    else {
      ast <- .visitor(node@mutable$ast)                          # Build compute expression
      if( !node@GC )                                     ast     # Not GCd', so not ever in a temp, so recompute via full 'ast'
      else {                                                     # Else compute, and assign expr result to temp
        res <- paste0("(tmp= ",node@id," ",ast,")")
        node@mutable$computed <- T
        node@mutable$ast <- NULL
        res
      }
    }
  } else                                                 node
}

#'
#' Get the key or AST.  ASTs are evaluated and assigned, and the id will be
#' used instead of re-inlining the AST for the next use... which means the AST
#' has to be visited in execution order (typically not a problem).
#'
#' Key points to a bonified object in the H2O cluster
.get <- function(fr) {
  if( fr@mutable$computed ) fr@id
  else fr
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
  if (is.call(expr)) lapply(as.list(expr), .as_list)
  else               expr
}

#'
#' Convert R expression to a Rapids string
#'
.eval<-
function(x, envir) {
  statements  <- unlist(lapply(as.list(x), .as_list), recursive = TRUE)
  anyH2OFrame <- FALSE
  for( i in statements ) {
    anyH2OFrame <- tryCatch(is(i, "H2OFrame") ||
                            is(get(as.character(i), envir), "H2OFrame"),
                            error = function(e) FALSE)
    if( anyH2OFrame ) break
  }
  if( anyH2OFrame )   x <- eval(x, envir)
  return(paste0('[',.ast.walker(x, envir, FALSE),']',collapse=" "))
}

#'
#' Walk the R AST directly and flatten it
#'
#' Flattens "c" expressions.  
#' Symbols are looked up, and recursively converted to numbers or strings.
#' Minus is distributed through "c" lists, and only applies to numbers.
#' 
#' Input is:
#'   expr := 17
#'        := a_symbol, and the symbol's looked up value is itself an expr
#'        := "baz"
#'        := (: expr expr)
#'        := (c expr...)
#'        := (- expr)
#'
#' Returns:
#'   a flattened string of numbers, spans and strings surrounded by square brackets.
#    Spans are input as (: lo hi) and output as lo:cnt
#'   Example: (c 1 "baz" (c (: 3 7) 17))  ==>
#'            "[1 baz 3:4 17]"
.ast.walker<-
function(expr, envir, neg) {
  sexpr <- deparse(expr) # Stringified expr

  # expr := a_symbol
  if( is.symbol(expr)) return(.ast.walker(get(sexpr, envir), envir, neg))

  # expr := 17
  expr1 <- expr[[1L]]    # First token of lists
  if( length(expr) == 1L && is.numeric(expr1) ) return(as.character(ifelse(neg,-expr1,expr1-1L)))   # if not neg-index, then do 1 -> 0 indexing (expr1 - 1L)

  # expr := "baz"
  if( length(expr) == 1L && is.character(expr ) ) {
    if( neg ) stop("Trying to negate a column name")
    return(sexpr)
  }

  # expr := (c expr...)
  if( expr1 == quote(`c`)) return(paste0(lapply(expr[-1L], .ast.walker, envir, neg),collapse=" "))

  # expr := (: lo hi)
  if( expr1 == quote(`:`)) {
    if( length(expr) != 3L ) stop("Spans need exactly a lower bound and an upper bound")
    lb <- .ast.walker(expr[[2L]],envir,neg)
    ub <- .ast.walker(expr[[3L]],envir,neg)
    cnt <- as.numeric(ub)-as.numeric(lb)+1
    if( length(lb) != 1L ) stop("Bounds must be 1 value")
    if( length(ub) != 1L ) stop("Bounds must be 1 value")
    return(paste0(lb,':',cnt))
  }
  
  # list of numbers
  if( is.vector(expr) && is.numeric(expr) ) {
    if( expr[1] < 0 ) stop("leading negative sign unhandled");
    children <- lapply(expr, .ast.walker, envir, neg)
    return(paste0(children,collapse=','))
  }

  # expr := - expr
  if( expr1 == quote(`-`)) {
    if( length(expr) != 2L ) stop("Spans can negate exactly 1 expr")
    return(.ast.walker(expr[[2L]],envir,!neg))
  }

  # Generic unknown evaluation
  .ast.walker(eval(expr,envir),envir,neg)
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
  if (is(a, "H2OFrame"))      a
  else if (is(a, "ASTNode"))  a
  else if (is(a, "ASTFun"))   .fun.visitor(a)
  else if (is(a, "ASTEmpty")) paste0('%', a@key)
  else {
    res <- eval(a)
    if (is.null(res)) "()"
    else if (is.vector(res)) {
      if (length(res) == 0L)  "[]"
      else if (length(res) > 1L) {
        if (is.numeric(res))  res <- as.numeric(res)
        if( is.numeric(res) ) paste0("[", paste0(res, collapse=" "), "]")
        else                  paste0("[", paste0(unlist(lapply(res, deparse)), collapse= " "), "]")
      } else if( is.numeric(res) || is.logical(res)) res
      else deparse(eval(a))
    } else deparse(eval(a))
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
