#'
#' Transmogrify A User Defined Function Into A Cascade AST
#'
#' A function has three parts:
#'  1. A name
#'  2. Arguments
#'  3. A body
#'
#' If it has been deteremined that a function is user-defined, then it must become a Cascade AST.
#'
#' The overall strategy for collecting up a function is to avoid densely packed recursive calls, each attempting to handle
#' the various corner cases.
#'
#' Instead, the thinking is that there are a limited number of statement types in a function body:
#'  1. control flow: if, else, while, for, return
#'  2. assignments
#'  3. operations/function calls
#'  4. Implicit return statement
#'
#' Implicit return statements are the last statement in a closure. Statements that are not implicit return statements
#' are optimized away by the back end.
#'
#' Since the statement types can be explicitly defined, there is only a need for processing a statement of the 3rd kind.
#' Therefore, all recursive calls are funneled into a single statement processing function.
#'
#' From now on, statements will refer to statemets of the 3rd kind.
#'
#' Simple statements can be further grouped into the following ways (excuse abuse of `dispatch` lingo below):
#'
#'  1. Unary operations  (dispatch to .h2o.unop )
#'  2. Binary Operations (dispatch to .h2o.binop)
#'  3. Prefix Operations (dispatch to .h2o.varop)
#'  4. User Defined Function Call
#'  5. Anonymous closure
#'
#' Of course "real" statements are mixtures of these simple statements, but these statements are handled recursively.
#'
#' Case 4 spins off a new transmogrification for the encountered udf. If the udf is already defined in this **scope**, or in
#' some parent scope, then there is nothing to do.
#'
#' Case 5 spins off a new transmogrification for the encountered closure and replaced by an invocation of that closure.
#' If there's no assignment resulting from the closure, the closure is simply dropped (modification can only happen in the
#' global scope (scope used in usual sense here)).
#'
#'
#' NB:
#' **scope**: Here scopes are defined in terms of a closure.
#'            *this* scope knows about all functions and all if its parents functions.
#'            They are implemented as nested environments.
#'
#' The Finer Points
#' ----------------
#'
#' Mutually recursive functions:
#'
#'  For creating the
#'  .process.stmnt <=> .statement.to.ast.switchboard
#'
#'
#'

#'
#' Helper function for .is.udf
#'
#' Carefully examine an environment and determine if it's a user-defined closure.
#'
.is.closure <- function(e) {

  # if env is defined in the global environment --> it is user defined
  if (identical(e, .GlobalEnv)) return(TRUE)

  # otherwise may be a closure:

  # first check that it is not a named environment --> not part of a package
  isNamed <- environmentName(e) != ""
  if (isNamed) return(FALSE)
  # go to the parent and check again, until we hit the global, in which case return true
  .is.closure(parent.env(e))
}

#'
#' Check if the call is user defined.
#'
#' A call is user defined if its environment is the Global one, or it's a closure inside of a call existing in the Global env.
.is.udf<-
function(fun) {
  e <- tryCatch( environment(eval(fun)), error = function(x) FALSE) # get the environment of `fun`
  if (is.logical(e)) return(FALSE)                                  # if e is logical -> no environment found
  tryCatch(.is.closure(e), error = function(x) FALSE)                # environment found, but then has no parent.env
}

.is.op<-
function(o) {
  if(.is.unop(o) || .is.binop(o) || .is.varop(o) || .is.prefix(o)) return(TRUE)
  FALSE
}

.is.unop<-
function(o) {
  o <- deparse(o)
  if (o %in% names(.unop.map)) return(TRUE)
  FALSE
}

.is.binop<-
function(o) {
  o <- deparse(o)
  if (o %in% names(.binop.map)) return(TRUE)
  FALSE
}

.is.varop<-
function(o) {
  o <- deparse(o)
  if (o %in% names(.varop.map)) return(TRUE)
  FALSE
}

.is.prefix<-
function(o) {
  o <- deparse(o)
  if (o %in% names(.prefix.map)) return(TRUE)
  FALSE
}

.is.slice<-
function(o) {
  o <- deparse(o)
  if (o %in% c("[", "$")) return(TRUE)
  FALSE
}

#'
#' Statement Processor
#'
#' Converts the statement into an AST.
#'
#'
#' The possible types of statements to process:
#'
#'  1. A unary operation (calls .h2o.unop)
#'      A. `!` operator
#'
#'  2. A binary operation  (calls .h2o.binop)
#'      A. ‘"+"’, ‘"-"’, ‘"*"’, ‘"^"’, ‘"%%"’, ‘"%/%"’, ‘"/"’
#'         ‘"=="’, ‘">"’, ‘"<"’, ‘"!="’, ‘"<="’, ‘">="’
#'         ‘"&"’, ‘"|"’, ‘"**"’
#'
#'  3. A prefix operation
#'      A. Unary Prefix:  ‘"abs"’,   ‘"sign"’,   ‘"sqrt"’,   ‘"ceiling"’, ‘"floor"’,
#'                        ‘"trunc"’, ‘"cummax"’, ‘"cummin"’, ‘"cumprod"’, ‘"cumsum"’,
#'                        ‘"log"’,   ‘"log10"’,  ‘"log2"’,   ‘"log1p"’,   ‘"acos"’, ‘"acosh"’,
#'                        ‘"asin"’,  ‘"asinh"’,  ‘"atan"’,   ‘"atanh"’,   ‘"exp"’,  ‘"expm1"’,
#'                        ‘"cos"’,   ‘"cosh"’,   ‘"sin"’,    ‘"sinh"’,    ‘"tan"’,  ‘"tanh"’,
#'                        ‘"gamma"’, ‘"lgamma"’, ‘"digamma"’,‘"trigamma"’, ‘"is.na"’
#'
#'      B. .h2o.varop: ‘"round"’, ‘"signif"’
#'
#'      C. .h2o.varop: ‘"max"’, ‘"min"’, ‘"range"’, ‘"prod"’, ‘"sum"’, ‘"any"’, ‘"all"’
#'
#'      D. .h2o.varop: ‘"trunc"’, ‘"log"’  (could be either unop or varop)
#'
#' Each of the above types of statements will handle their own arguments and return an appropriate AST
.process.stmnt<-
function(stmnt) {

  # convenience variable
  stmnt_list <- as.list(stmnt)

  # Got an atomic numeric
  if (is.atomic(stmnt_list[[1]]) && class(stmnt_list[[1]]) == "numeric") {
    return('#' %p0% stmnt_list[[1]])

  # Got an atomic string
  } else if (is.atomic(stmnt_list[[1]]) && class(stmnt_list[[1]]) == "character") {
    return(deparse(stmnt_list[[1]]))

  # Got an atomic logical
  } else if (is.atomic(stmnt_list[[1]]) && class(stmnt_list[[1]]) == "logical") {
    return('$' %p0% stmnt_list[[1]])
  }

  # Got an Op
  if (.is.op(stmnt_list[[1]])) {

    # have an operator
    op <- stmnt_list[[1]]

    # Case 2 from the comment above
    if (.is.binop(op)) {
      e1 <- .statement.to.ast.switchboard(stmnt_list[[2]])
      e2 <- .statement.to.ast.switchboard(stmnt_list[[3]])
      return(.h2o.binop(deparse(op), e1, e2))

    # Case 1, 3A above unless it's `log`, or `[`, or `$`
    } else if (.is.unop(op)) {
      if (.is.slice(op)) return(.process.slice.stmnt(stmnt))
      x <- .statement.to.ast.switchboard(stmnt_list[[2]])
      return(.h2o.unop(deparse(op), x))

    # all varops
    } else if(.is.varop(op)) {
      args <- lapply(stmnt_list[-1], .statement.to.ast.switchboard)
      op <- new("ASTApply", op = deparse(op))
      return(new("ASTNode", root=op, children=args))

    # prefix op, 1 arg
    } else if(.is.prefix(op)) {
      arg <- .statement.to.ast.switchboard(stmnt_list[[2]])
      return(.h2o.unop(deparse(op), arg))

    # should never get here
    } else {
      stop(paste("Fail in statement processing to AST. Failing statement was: ", stmnt))
    }
  }

  # Got a user-defined function
  if (.is.udf(stmnt_list[[1]])) {
    stop("fcn within a fcn unimplemented")
  }

  # otherwise just got a variable name to either return (if last statement) or skip (if not last statement)
  # this `if` is just to make us all feel good... it doesn't do any interesting checking
  if (is.name(stmnt_list[[1]]) && is.symbol(stmnt_list[[1]]) && is.language(stmnt_list[[1]])) {
    ast <- '$' %<p0-% deparse(stmnt_list[[1]])
    return(ast)
  }
  stop(paste( "Don't know what to do with statement: ", stmnt))
}

.process.slice.stmnt<-
function(stmnt) {
  stmnt_list <- as.list(stmnt)
  i <- stmnt_list[[3]]  # rows
  if (length(stmnt_list) == 3) {
    if(missing(i)) return("")
    if(length(i) > 1) stop("[[]] may only select one column")
    if (!is.numeric(i)) stop("column selection within a function call must be numeric")
    op <- new("ASTApply", op='[')
    x <- '$' %<p0-% deparse(stmnt_list[[2]])
    rows <- deparse("null")
    cols <- .eval(substitute(i), parent.frame())
    return(new("ASTNode", root=op, children=list(x, rows, cols)))
  }
  j <- stmnt_list[[4]]  # columns
  op <- new("ASTApply", op='[')
  x <- '$' %<p0-% deparse(stmnt_list[[2]])
  rows <- if( missing(i)) deparse("null") else { if ( i %i% "ASTNode") eval(i, parent.frame()) else .eval(substitute(i), parent.frame()) }
  cols <- if( missing(j)) deparse("null") else .eval(substitute(j), parent.frame())
  new("ASTNode", root=op, children=list(x, rows, cols))
}

.process.if.stmnt<-
function(stmnt) {
  stmnt_list <- as.list(stmnt)         # drop the `if`
  has_else <- length(stmnt_list) == 4  # more if-elses are glommed together into the 4th item in the list ... ALWAYS!
  condition <- .statement.to.ast.switchboard(stmnt_list[[2]])
  body <- .process.body(stmnt_list[[3]])
  if (has_else) body <- c(body, .process.else.stmnt(stmnt_list[[4]]))
  new("ASTIf", condition = condition, body = new("ASTBody", statements = body))
}

.process.for.stmnt<-
function(stmnt) {
  stop("`for` unimplemented")
}

.process.else.stmnt<-
function(stmnt) {
  body <- .process.body(stmnt, TRUE)
  new("ASTElse", body = body)
}

.process.return.stmnt<-
function(stmnt) {
  stmnt_list <- as.list(stmnt)
  .h2o.unop("return", .statement.to.ast.switchboard(stmnt_list[[2]]))
}

.process.assign.stmnt<-
function(stmnt) {
  stmnt_list <- as.list(stmnt)
  s <- .statement.to.ast.switchboard(stmnt_list[[2]])
  lhs <- ""
  if (s %i% "ASTNode") lhs <- s
  else {
    x <- deparse(stmnt[[2]])
    lhs <- '!' %<p0-% x
  }
  y <- .statement.to.ast.switchboard(stmnt_list[[3]])
  new("ASTNode", root= new("ASTApply", op="="), children = list(left = lhs, right = y))
}

#'
#' Statement Parser Switchboard
#'
#' This function acts as a switchboard for the various types of statements that may exist in the body of a function.
#'
#' The possible types of statements:
#'
#'  1. Control Flow Statements:
#'      A. If
#'      B. Else
#'      C. for  -- to handle iterator-for (i in 1:5) (x in vector)
#'      D. return -- return the result
#'      E. while -- stops processing immediately. while loops are unsupported
#'
#'  2. Assignment
#'
#'  3. Function call / Operation
#'
#' This switchboard takes exactly ONE statement at a time.
.statement.to.ast.switchboard<-
function(stmnt) {

  # convenience variable
  stmnt_list <- as.list(stmnt)

  # check for `if`, `for`, `else`, `return`, `while` -- stop if `while`
  if (identical(quote(`if`),     stmnt_list[[1]])) return(.process.if.stmnt(stmnt))
  if (identical(quote(`for`),    stmnt_list[[1]])) return(.process.for.stmnt(stmnt))
  if (identical(quote(`else`),   stmnt_list[[1]])) return(.process.else.stmnt(stmnt))
  if (identical(quote(`return`), stmnt_list[[1]])) return(.process.return.stmnt(stmnt))
  if (identical(quote(`while`),  stmnt_list[[1]])) stop("*Unimplemented* `while` loops are not supported by h2o")

  # check assignment
  if(identical(quote(`<-`), stmnt_list[[1]])) return(.process.assign.stmnt(stmnt))
  if(identical(quote(`=`),  stmnt_list[[1]])) return(.process.assign.stmnt(stmnt))
  if(identical(quote(`->`), stmnt_list[[1]])) stop("Please use `<-` or `=` for assignment. Assigning to the right is not supported.")

  # everything else is a function call or operation
  .process.stmnt(stmnt)
}


#'
#' Produce a list of statements from a function body. The statements are ordered in first -> last.
.extract.statements<-
function(b) {
  # strip off the '{' if it's there
  stmnts <- as.list(b)
  if(identical(stmnts[[1]], quote(`{`))) stmnts <- stmnts[-1]
  stmnts
}


.process.body<-
function(b, is.single = FALSE) {
  stmnts <- .extract.statements(b)
  if (is.single) { stmnts <- list(.statement.to.ast.switchboard(stmnts))
  # return a list of ast_stmnts
  } else stmnts <- lapply(stmnts, .statement.to.ast.switchboard)
  new("ASTBody", statements = stmnts)
}

#'
#' Transmogrify A User Defined Function Into A Cascade AST
#'
#' A function has three parts:
#'  1. A name
#'  2. Arguments
#'  3. A body
#'
#' At this point, it's been determined that `fun` is a user defined function, and it must become an AST.
#' Pack the function call up into an AST.
#'
#' Two interesting cases to handle:
#'
#'  1. A closure defined in the body.
#'  2. A different UDF is called within the body.
#'
#'  1.
#'      A. Recognize closure declaration
#'      B. Parse the closure AST and store it to be shipped to H2O
#'      C. Swap out the declaration in the body of this function with an invocation of the closure.
#'
#'  2.
#'      A. Recognize the call
#'      B. If there's not an existing definition *in the current scope*, make one. TODO: handle closures more gracefully -- they aren't handled at all currently.
#'
#'
#' The result is something like the following:
#' (def "f" {arg1;arg2;arg3} {(stmnt1);;(stmnt2);;(stmnt3);;(stmnt4)})
.fun.to.ast<-
function(fun, name) {
  args <- '{' %p0% paste(names(formals(fun)), collapse = ";") %p0% '}'
  b <- body(fun)
  stmnts <- .process.body(b)
  new("ASTFun", name = name, arguments = args, body = stmnts)
}

.fun.visitor<-
function(astfun) {
  res <- "(def"
  res %p% astfun@name
  res %p% astfun@arguments
  body <- .body.visitor(astfun@body)
  for (b in body) {res %p% b; res %p0% ";;" }
  res %p0% ';)'
  list(ast = res)
}

.body.visitor<-
function(b) {
  stmnts <- lapply(b@statements, .stmnt.visitor)
}

.stmnt.visitor<-
function(s) {
  res <- ""
  if (s %i% "ASTBody") {
    return(.body.visitor(s))
  }
  if (s %i% "ASTIf") {
    res %p0% '('
    res %p0% s@op
    res %p% visitor(s@condition)$ast
    body <- .body.visitor(s@body)
    for (b in body) { res %p% b}  #; res %p0% ";;" }
    res %p0% ')'
    return(res)
  } else if (s %i% "ASTElse") {
    res %p0% '('
    res %p0% s@op
    body <- .body.visitor(s@body)
    for (b in body) {res %p% b}  #; res %p0% ";;" }
    res %p0% ')'
    return(res)
  } else if (s %i% "ASTFor") {
    stop("unimplemented")
  } else if (s %i% "ASTNode") {
    res %p% visitor(s)$ast
    return(res)
  } else if (s %i% "character") {
    res %p% s
    return(res)
  } else {
    print(s)
    stop("unimplemented")
  }
}