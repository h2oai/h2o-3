#`
#` Transmogrify A User Defined Function Into A Rapids AST
#`
#` A function has three parts:
#`  1. A name
#`  2. Arguments
#`  3. A body
#`
#` If it has been deteremined that a function is user-defined, then it must become a Rapids AST.
#`
#` The overall strategy for collecting up a function is to avoid densely packed recursive calls, each attempting to handle
#` the various corner cases.
#`
#` Instead, the thinking is that there are a limited number of statement types in a function body:
#`  1. control flow: if, else, while, for, return
#`  2. assignments
#`  3. operations/function calls
#`  4. Implicit return statement
#`
#` Implicit return statements are the last statement in a closure. Statements that are not implicit return statements
#` are optimized away by the back end.
#`
#` Since the statement types can be explicitly defined, there is only a need for processing a statement of the 3rd kind.
#` Therefore, all recursive calls are funneled into a single statement processing function.
#`
#` From now on, statements will refer to statemets of the 3rd kind.
#`
#` Simple statements can be further grouped into the following ways (excuse abuse of `dispatch` lingo below):
#`
#`  1. Unary operations  (dispatch to .h2o.unary_op )
#`  2. Binary Operations (dispatch to .h2o.binary_op)
#`  3. Prefix Operations (dispatch to .h2o.nary_op)
#`  4. User Defined Function Call
#`  5. Anonymous closure
#`
#` Of course "real" statements are mixtures of these simple statements, but these statements are handled recursively.
#`
#` Case 4 spins off a new transmogrification for the encountered udf. If the udf is already defined in this **scope**, or in
#` some parent scope, then there is nothing to do.
#`
#` Case 5 spins off a new transmogrification for the encountered closure and replaced by an invocation of that closure.
#` If there's no assignment resulting from the closure, the closure is simply dropped (modification can only happen in the
#` global scope (scope used in usual sense here)).
#`
#`
#` NB:
#` **scope**: Here scopes are defined in terms of a closure.
#`            *this* scope knows about all functions and all of its parents functions.
#`            They are implemented as nested environments.
#`
#` The Finer Points
#` ----------------
#`
#` Mutually recursive functions:
#`
#`  For processing a raw function:
#`   .process.stmnt <=> .stmnt.to.ast.switchboard
#`
#`  For postprocessing the ast:
#`   .body.visitor <=> .stmnt.visitor


#`
#` Helper function for .is.udf
#`
#` Carefully examine an environment and determine if it's a user-defined closure.
#`
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

#`
#` Check if the call is user defined.
#`
#` A call is user defined if its environment is the Global one, or it's a closure inside of a call existing in the Global env.
.is.udf<-
function(fun) {
  if (.is.op(fun)) return(FALSE)
  e <- tryCatch( environment(eval(fun)), error = function(x) FALSE)  # get the environment of `fun`
  if (is.logical(e)) return(FALSE)                                   # if e is logical -> no environment found
  tryCatch(.is.closure(e), error = function(x) FALSE)                # environment found, but then has no parent.env
}

.is.in<-
function(o, map) {
  o <- deparse(o)
  o %in% names(map)
}

.is.unary_op  <- function(o) .is.in(o, .unary_op.map)
.is.binary_op <- function(o) .is.in(o, .binary_op.map)
.is.nary_op   <- function(o) .is.in(o, .nary_op.map)
.is.prefix    <- function(o) .is.in(o, .prefix.map)
.is.slice     <- function(o) .is.in(o, .slice.map)
.is.op        <- function(o) .is.unary_op(o) || .is.binary_op(o) || .is.nary_op(o) || .is.prefix(o)

#`
#` Statement Processor
#`
#` Converts the statement into an AST.
#`
#`
#` The possible types of statements to process:
#`
#`  1. A unary operation (calls .h2o.unary_op)
#`      A. `!` operator
#`
#`  2. A binary operation  (calls .h2o.binary_op)
#`      A. '"+"', '"-"', '"*"', '"^"', '"%%"', '"%/%"', '"/"'
#`         '"=="', '">"', '"<"', '"!="', '"<="', '">="'
#`         '"&"', '"|"', '"**"'
#`
#`  3. A prefix operation
#`      A. Unary Prefix:  '"abs"',   '"sign"',   '"sqrt"',   '"ceiling"', '"floor"',
#`                        '"trunc"', '"cummax"', '"cummin"', '"cumprod"', '"cumsum"',
#`                        '"log"',   '"log10"',  '"log2"',   '"log1p"',   '"acos"', '"acosh"',
#`                        '"asin"',  '"asinh"',  '"atan"',   '"atanh"',   '"exp"',  '"expm1"',
#`                        '"cos"',   '"cosh"',   '"cospi"',  '"sin"',     '"sinh"', '"sinpi"',
#`                        '"tan"',   '"tanh"',   '"tanpi"',
#`                        '"gamma"', '"lgamma"', '"digamma"','"trigamma"', '"is.na"'
#`
#`      B. .h2o.nary_op: '"round"', '"signif"'
#`
#`      C. .h2o.nary_op: '"max"', '"min"', '"range"', '"prod"', '"sum"', '"any"', '"all"'
#`
#`      D. .h2o.nary_op: '"trunc"', '"log"'  (could be either unary_op or nary_op)
#`
#` Each of the above types of statements will handle their own arguments and return an appropriate AST
.process.stmnt<-
function(stmnt) {
  # convenience variable
  stmnt_list <- as.list(stmnt)

  # is null
  if (is.atomic(stmnt_list[[1L]]) && is.null(stmnt_list[[1L]])) return("()")

  if (is.atomic(stmnt_list[[1L]]))
    if (is.numeric(stmnt_list[[1L]])   ||  # Got atomic numeric
        is.character(stmnt_list[[1L]]) ||  # Got atomic character
        is.logical(stmnt_list[[1L]]))      # Got atomic logical
        return(stmnt_list[[1L]])

  # Got an Op
  if (.is.op(stmnt_list[[1L]])) {

    # have an operator
    op <- stmnt_list[[1L]]

    # Case 2 from the comment above
    if (.is.binary_op(op)) {
      e1 <- .stmnt.to.ast.switchboard(stmnt_list[[2L]])
      e2 <- .stmnt.to.ast.switchboard(stmnt_list[[3L]])
      return(.h2o.binary_op_ast(deparse(op), e1, e2))

    # Case 1, 3A above unless it's `log`, or `[`, or `$`
    } else if (.is.unary_op(op)) {
      if (.is.slice(op)) return(.process.slice.stmnt(stmnt))
      x <- .stmnt.to.ast.switchboard(stmnt_list[[2L]])
      return(.h2o.unary_op_ast(deparse(op), x))

    # all nary_ops
    } else if(.is.nary_op(op)) {
      args <- lapply(stmnt_list[-1L], .stmnt.to.ast.switchboard)
      arg1 <- args[1L]
      if (is(arg1[[1L]], "ASTEmpty")) arg1[[1L]] <- .get.value.from.arg(arg1[[1L]])
      if (is(arg1[[1L]], "ASTNode"))  arg1[[1L]] <- .visitor(.get.value.from.arg(arg1[[1L]]))
      args[[1L]] <- arg1

      # Grab defaults and exchange them with any passed in args
      op_args <- (stmnt_list[-1L])[-1L]         # these are any additional args passed to this op
      l <- NULL
      if (is.primitive(match.fun(op))) l <- formals(args(match.fun(op)))  # primitive methods are special
      else {
        l <- formals(getMethod(as.character(op), "H2OFrame"))[-1L]
        if (length(l) == 1 && names(l) == "...") {
          l <- formals(as.list(as.list(getMethod(as.character(op), "H2OFrame"))[[3]][[2]])[[3]])[-1L]
        }
      }
      #if (is.null(l)) stop("Could not find args for the op: ", as.character(op))
      if( as.character(op) == "log" ) l <- NULL   # special case for plain olde log
      l <- lapply(l, function(i)
        if (length(i) != 0L) {
          if(i == "") NULL else i
        } else i)
      add_args <- l[names(l) != "..."]        # remove any '...' args

      # if some args were passed in then update those values in add_args
      if (length(op_args) != 0L) {
        for (a in names(add_args)) {
          if (a %in% names(op_args)) add_args[a] <- op_args[a]
        }
        args <- arg1
      }

      # simplify the list, names in the list makes things go kablooey
      names(add_args) <- NULL
      add_args <- lapply(add_args, .stmnt.to.ast.switchboard)
      add_args <- lapply(add_args, .get.value.from.arg)

      # update the args list and then return the node
      args <- c(args, add_args)
      return(new("ASTNode", root=new("ASTApply", op = deparse(op)), children=args))

    # prefix op, 1 arg
    } else if(.is.prefix(op)) {
      arg <- .stmnt.to.ast.switchboard(stmnt_list[[2L]])
      return(.h2o.unary_op_ast(deparse(op), arg))

    # should never get here
    } else {
      stop("Fail in statement processing to AST. Failing statement was: ", stmnt, "\n",
           "Please contact support@h2oai.com")
    }
  }

  # Got a user-defined function
  if (.is.udf(stmnt_list[[1L]])) stop("fcn within a fcn unimplemented")

  # otherwise just got a variable name to either return (if last statement) or skip (if not last statement)
  # this `if` is just to make us all feel good... it doesn't do any interesting checking
  if (is.name(stmnt_list[[1L]]) && is.symbol(stmnt_list[[1L]]) && is.language(stmnt_list[[1L]])) {
    return(new("ASTEmpty", key = as.character(stmnt_list[[1L]])))
  }

  if (length(stmnt) == 1L) return(.process.stmnt(stmnt[[1L]]))
  stop("Don't know what to do with statement: ", stmnt)
}

.process.slice.stmnt<-
function(stmnt) {
  stmnt_list <- as.list(stmnt)
  i <- stmnt_list[[3L]]  # rows
  if (length(stmnt_list) == 3L) {
    if (missing(i)) return("")
    if (length(i) > 1L) stop("`[[` can only select one column")
    if (!is.numeric(i)) stop("column selection within a function call must be numeric")
    rows <- "()"
    cols <- .eval(substitute(i), parent.frame())
  } else {
    j <- stmnt_list[[4L]]  # columns
    if (missing(i))
      rows <- "()"
    else if (is(i, "ASTNode"))
      rows <- eval(i, parent.frame())
    else
      rows <- .eval(substitute(i), parent.frame())
    if (missing(j))
      cols <- "()"
    else
      cols <- .eval(substitute(j), parent.frame())
  }
  ast <- deparse(stmnt_list[[2L]])
  if( !identical(cols,"()") ) ast <- new("ASTNode", root = new("ASTApply", op = "cols"), children = list(ast, cols))
  if( !identical(rows,"()") ) ast <- new("ASTNode", root = new("ASTApply", op = "rows"), children = list(ast, rows))
  ast  
}

.process.if.stmnt<-
function(stmnt) {
  stmnt_list <- as.list(stmnt)         # drop the `if`
  has_else <- length(stmnt_list) == 4L # more if-elses are glommed together into the 4th item in the list ... ALWAYS!
  condition <- .stmnt.to.ast.switchboard(stmnt_list[[2L]])
  body <- .process.body(stmnt_list[[3L]])
  if (has_else) body <- c(body, .process.else.stmnt(stmnt_list[[4L]]))
  new("ASTIf", condition = condition, body = new("ASTBody", statements = body))
}

.process.for.stmnt    <- function(stmnt) stop("`for` unimplemented")
.process.else.stmnt   <- function(stmnt) new("ASTElse", body = .process.body(stmnt, TRUE))
.process.return.stmnt <- function(stmnt) .h2o.unary_op_ast("return", .stmnt.to.ast.switchboard(as.list(stmnt)[[2L]]))

.process.assign.stmnt<-
function(stmnt) {
  stmnt_list <- as.list(stmnt)
  s <- .stmnt.to.ast.switchboard(stmnt_list[[2L]])
  lhs <- ""
  if (is(s, "ASTNode")) lhs <- s
  else                  lhs <- deparse(stmnt[[2L]])
  y <- .stmnt.to.ast.switchboard(stmnt_list[[3L]])
  new("ASTNode", root= new("ASTApply", op="="), children = list(left = lhs, right = y))
}

#`
#` Statement Parser Switchboard
#`
#` This function acts as a switchboard for the various types of statements that may exist in the body of a function.
#`
#` The possible types of statements:
#`
#`  1. Control Flow Statements:
#`      A. If
#`      B. Else
#`      C. for  -- to handle iterator-for (i in 1:5) (x in vector)
#`      D. return -- return the result
#`      E. while -- stops processing immediately. while loops are unsupported
#`
#`  2. Assignment
#`
#`  3. Function call / Operation
#`
#` This switchboard takes exactly ONE statement at a time.
.stmnt.to.ast.switchboard<-
function(stmnt) {
  if (is.null(stmnt)) return(NULL)

  # convenience variable
  stmnt_list <- as.list(stmnt)

  # check for `if`, `for`, `else`, `return`, `while` -- stop if `while`
  if (identical(quote(`if`),     stmnt_list[[1L]])) return(.process.if.stmnt(stmnt))
  if (identical(quote(`for`),    stmnt_list[[1L]])) return(.process.for.stmnt(stmnt))
  if (identical(quote(`else`),   stmnt_list[[1L]])) return(.process.else.stmnt(stmnt))
  if (identical(quote(`return`), stmnt_list[[1L]])) return(.process.return.stmnt(stmnt))
  if (identical(quote(`while`),  stmnt_list[[1L]])) stop("*Unimplemented* `while` loops are not supported by h2o")

  # check assignment
  if(identical(quote(`<-`), stmnt_list[[1L]])) return(.process.assign.stmnt(stmnt))
  if(identical(quote(`=`),  stmnt_list[[1L]])) return(.process.assign.stmnt(stmnt))
  if(identical(quote(`->`), stmnt_list[[1L]])) stop("Please use `<-` or `=` for assignment. Assigning to the right is not supported.")

  # everything else is a function call or operation
  .process.stmnt(stmnt)
}

.process.body<-
function(b, is.single = FALSE) {
  stmnts <- as.list(b)
  if(identical(stmnts[[1L]], quote(`{`))) stmnts <- stmnts[-1L]
  if (is.single) { stmnts <- list(.stmnt.to.ast.switchboard(stmnts))
  # return a list of ast_stmnts
  } else { stmnts <- lapply(stmnts, .stmnt.to.ast.switchboard) }
  new("ASTBody", statements = stmnts)
}

#`
#` Transmogrify A User Defined Function Into A Rapids AST
#`
#` A function has two parts:
#`  1. Arguments
#`  2. A body
#`
#` At this point, it's been determined that `fun` is a user defined function, and it must become an AST.
#` Pack the function call up into an AST.
#`
#` Two interesting cases to handle:
#`
#`  1. A closure defined in the body.
#`  2. A different UDF is called within the body.
#`
#`  1.
#`      A. Recognize closure declaration
#`      B. Parse the closure AST and store it to be shipped to H2O
#`      C. Swap out the declaration in the body of this function with an invocation of the closure.
#`
#`  2.
#`      A. Recognize the call
#`      B. If there's not an existing definition *in the current scope*, make one. TODO: handle closures more gracefully -- they aren't handled at all currently.
#`
#`
#` The result is something like the following:
#` (arg1 arg2 arg3 . body)
.fun.to.ast<-
function(fun) {
  args <- paste0(names(formals(fun)), collapse=" ")
  b <- body(fun)
  stmnts <- .process.body(b)
  new("ASTFun", arguments = args, body = stmnts)
}

.fun.visitor<-
function(astfun) {
  body <- paste0("(,", paste0(unlist(.body.visitor(astfun@body), use.names = FALSE), collapse=" "), ")")
  list(ast = paste0("{", astfun@arguments, " . ", body , "}"))
}

.body.visitor <- function(b) lapply(b@statements, .stmnt.visitor)

.stmnt.visitor<-
function(s) {
  if (is(s, "ASTBody")) {
    .body.visitor(s)
  } else if (is(s, "ASTIf")) {
    body <- paste0(unlist(.body.visitor(s@body), use.names = FALSE), collapse = " ")
    paste0("(", s@op, " ", .visitor(s@condition), " ", body, ")")
  } else if (is(s, "ASTElse")) {
    body <- paste0(unlist(.body.visitor(s@body), use.names = FALSE), collapse = " ")
    paste0("(", s@op, " ", body, ")")
  } else if (is(s, "ASTFor")) {
    .NotYetImplemented()
  } else if (is(s, "ASTNode")) {
    paste0(" ", .visitor(s))
  } else if (is.character(s)) {
    paste0(" ", s)
  } else if (is(s, "H2OFrame")) {
    .NotYetImplemented()
    tmp <- .get(s)
    if (is(tmp, "ASTNode")) {
      paste0(" ", .visitor(s@mutable$ast))
    } else {
      paste0(" ", tmp)
    }
  } else if (is(s, "ASTEmpty")) {
    paste0(" %", s@key)
  } else {
    print(s)
    print(class(s))
    .NotYetImplemented()
  }
}
