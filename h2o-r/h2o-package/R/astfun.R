
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
#` {arg1 arg2 arg3 . body}
.fun.to.ast <- function(fun) {
  f <- formals(fun)
  if( is.null(f) ) stop(paste0("Function '",fun,"' not in .h2o.primitives list and not an anonymous function, unable to convert it to Currents"))
  args <- paste0(names(formals(fun)), collapse=" ")
  b <- body(fun)
  stmnts <- .process.body(b)
  paste0("{ ",args," . ",stmnts," }")
}

.process.body <- function(b) {
  stmnts <- as.list(b)
  tmp1 <- ""; tmp2 <- ""
  # Leading { means a list of statements, there is no trailing close-}
  if( identical(stmnts[[1L]], quote(`{`))) {
    stmnts <- stmnts[-1L]       # Lose the lone open-curly
    tmp1 <- "(, "; tmp2 <- ")"  # Wrap result in a comma-operator
  }
  # return a list of ast_stmnts
  paste0(tmp1,paste0(lapply(stmnts, .stmnt.to.ast.switchboard),collapse=" "),tmp2)
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
.stmnt.to.ast.switchboard <- function(stmnt) {
  if (is.null(stmnt)) return("")

  # convenience variable
  stmnt_list <- as.list(stmnt)
  s1 <- stmnt_list[[1L]]

  # check for `if`, `for`, `else`, `return`, `while` -- stop if `while`
  if (identical(quote(`if`),     s1)) return(.process.if.stmnt(stmnt))
  if (identical(quote(`for`),    s1)) return(.process.for.stmnt(stmnt))
  if (identical(quote(`else`),   s1)) return(.process.else.stmnt(stmnt))
  if (identical(quote(`return`), s1)) return(.process.return.stmnt(stmnt))
  if (identical(quote(`while`),  s1)) stop("*Unimplemented* `while` loops are not supported by h2o")

  # check assignment
  if(identical(quote(`<-`), s1)) return(.process.assign.stmnt(stmnt))
  if(identical(quote(`=`),  s1)) return(.process.assign.stmnt(stmnt))
  if(identical(quote(`->`), s1)) stop("Please use `<-` or `=` for assignment. Assigning to the right is not supported.")

  # everything else is a function call or operation
  .process.stmnt(stmnt)
}


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
.process.stmnt <- function(stmnt) {
  # convenience variable
  stmnt_list <- as.list(stmnt)
  s1 <- stmnt_list[[1L]]

  # is null
  if (is.atomic(s1) && is.null(s1)) return("()")

  if (is.atomic(s1))
    if (is.numeric(s1)   ||  # Got atomic numeric
        is.character(s1) ||  # Got atomic character
        is.logical(s1))      # Got atomic logical
      return(s1)

  # Got an Op
  if( length(stmnt) > 1L && (typeof(s1) == "builtin" || typeof(s1)=="symbol") ) {
    fname <- as.character(substitute(s1))
    if( fname %in% .h2o.primitives ) {
      args <- lapply( stmnt_list[-1L], .stmnt.to.ast.switchboard )
      return(paste0("(",fname," ",paste0(args,collapse=" "),")"))
    }
    if( fname=="[" ) 
      stop("[ unimpl")
  }

  # otherwise just got a variable name to either return (if last statement) or skip (if not last statement)
  # this `if` is just to make us all feel good... it doesn't do any interesting checking
  if( length(stmnt) == 1L ) {
    if (is.name(s1) && is.symbol(s1) && is.language(s1))
      return(s1)
    stop("return(.process.stmnt(s1))")
  }

  stop("Don't know what to do with statement: ", paste(stmnt,collapse=" "))
}
