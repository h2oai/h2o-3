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
#`      B. If there's not an existing definition *in the current scope*, make
#`      one. TODO: handle closures more gracefully -- they aren't handled at all
#`      currently.
#`
#`
#` The result is something like the following:
#` {arg1 arg2 arg3 . body}
.fun.to.ast <- function(fun,oldformals,envnum) {
  force(envnum)
  fs <- formals(fun)
  b <- body(fun)
  stmnts <- .process.body(b,c(names(fs),oldformals),envnum)
  paste0("{ ",paste0(names(fs), collapse=" ")," . ",stmnts," }")
}

.process.body <- function(b,formalz,envs) {
  stmnts <- as.list(b)
  tmp1 <- ""; tmp2 <- ""
  # Leading '{' means a list of statements, there is no trailing close '}'
  if( identical(stmnts[[1L]], quote(`{`)) ) {
    stmnts <- stmnts[-1L]         # Lose the lone open-curly
    if( length(stmnts) > 1 ) {    # If there multiple statements, wrap them in a comma operator
      tmp1 <- "(, "; tmp2 <- ")"  # Wrap result in a comma-operator
    } else if( length(stmnts)==0 )
      stmnts = list(NaN)
  }
  # return a list of ast_stmnts
  stmnts_str <- lapply(stmnts, .stmnt.to.ast.switchboard, formalz, envs)
  paste0(tmp1,paste0(stmnts_str,collapse=" "),tmp2)
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
.stmnt.to.ast.switchboard <- function(stmnt,formalz, envs) {
  if( is.null(stmnt) ) return("")

  # convenience variable
  stmnt_list <- as.list(stmnt)
  s1 <- stmnt_list[[1L]]
  if( missing(s1) ) return("")

  # check for `if`, `for`, `else`, `return`, `while` -- stop if `while`
#  if (identical(quote(`if`),     s1)) return(.process.if.stmnt(stmnt))
#  if (identical(quote(`for`),    s1)) return(.process.for.stmnt(stmnt))
#  if (identical(quote(`else`),   s1)) return(.process.else.stmnt(stmnt))
#  if (identical(quote(`return`), s1)) return(.process.return.stmnt(stmnt))
#  if (identical(quote(`while`),  s1)) stop("*Unimplemented* `while` loops are not supported by h2o")
#
#  # check assignment
#  if(identical(quote(`<-`), s1)) return(.process.assign.stmnt(stmnt))
#  if(identical(quote(`=`),  s1)) return(.process.assign.stmnt(stmnt))
#  if(identical(quote(`->`), s1)) stop("Please use `<-` or `=` for assignment. Assigning to the right is not supported.")

  # everything else is a function call or operation
  .process.stmnt(stmnt, formalz, envs)
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
.process.stmnt <- function(stmnt, formalz, envs) {
  force(formalz)
  force(envs)
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

  fname <- as.character(substitute(s1))
  if( fname=="T" ) return(TRUE)
  if( fname=="F" ) return(FALSE)

  # Got an Op; function call of some sort
  if( length(stmnt) > 1L && (typeof(s1) == "builtin" || typeof(s1)=="symbol") ) {
    # Convert all args to a list of Currents strings
    args <- lapply( stmnt_list[-1L], .stmnt.to.ast.switchboard, formalz, envs )

    # Slice '[]' needs a little work: row and col break out into 2 nested calls,
    # and row/col numbers need conversion from 1-based to zero based.
    if( fname=="[" ) {
      if( length(args)==2 ) { # "hex[qux]"
        stop("hex[qux]")
      } else if( length(args)==3 ) { # "hex[row,col]"
        res <- .row_col_adjust(args[[1L]],args[[3L]],"cols")
        return(.row_col_adjust( res      ,args[[2L]],"rows"))
      } else stop("Only 1 or 2 args allowed for slice")
    }

    # Sequence ':', turn into the syntax for a number-list
    if( fname==":" ) {
      stopifnot(length(args)==2)
      return(paste0("[",args[1L],":",args[2L],"]"))
    }

    # H2O primitives we invoke directly
    fr.name <- paste0(fname,".H2OFrame")
    if( fname %in% .h2o.primitives || exists(fr.name) ) {
      if( exists(fr.name) ) { # Append any missing default args
        formal_args <- formals(get(fr.name))
        nargs <- length(formal_args) - length(args)
        if( nargs > 0 ) args <- c(args,lapply( tail(formal_args,nargs), .stmnt.to.ast.switchboard, formalz, envs ))
      }
      return(paste0("(",fname," ",paste0(args,collapse=" "),")"))
    }
  }

  # Look up unknown symbols calls in the calling environment, and directly
  # inline their current value
  if( typeof(s1)=="symbol" ) {
    # One of the declared formals?  Then will be in-scope in the called
    # function, and can use it's textual name directly.
    if( fname %in% formalz ) return(fname)
    # Lookup the unknown symbol in the calling environment
    sym = .lookup(fname,envs)
    if( is.list(sym) ) { # Found something?
      sym <- sym[[1L]]   # List-of-1 means: "found something" and nothing more.  Peel the list wrapper off.
      if( typeof(sym) == "closure" )
       return(paste0("(",.fun.to.ast(sym,formalz,envs)," ",paste0(args,collapse=" "),")"))
      if( typeof(sym) == "double" )
        return(as.character(sym))
      if( is(sym, "H2OFrame") )
        return(h2o.getId(sym))
      stop(paste0("Found symbol ",fname," of type ",typeof(sym),", but do not know how to convert to a Currents expression"))
    }
    # If we get here, the lookup failed and it's an unknown variable name in
    # the function body.  This is a classic R syntactic error, masked by R's
    # lazy-evaluation semantics.  For H2O, this is an eager error.
  }

  stop("Don't know what to do with statement: ", paste(stmnt,collapse=" "))
}

# Subtract 1 from the text form of "idx" (zero based indexing),
# then wrap "frame" with a call from "rowcol_op": '(rowcol_op frame idx)'
# If "idx" is empty, skip the whole thing, as it defaults to "all"
.row_col_adjust <- function(frame,idx,rowcol_op) {
  if( idx=="" ) return(frame)
  # Raw number
  nidx <- suppressWarnings(as.numeric(idx))
  if( !is.na(nidx) ) return(paste0("(",rowcol_op," ",frame," ",nidx-1,")"))
  # Numeric range [lo:hi], convert to [lo-1:(hi-1o)]
  s2 <- unlist(strsplit(idx,"\\[|:|]"))
  lo <- suppressWarnings(as.numeric(s2[2L]))
  hi <- suppressWarnings(as.numeric(s2[3L]))
  if( length(s2) == 3 && !is.na(lo) && !is.na(hi) )
    return(paste0("(",rowcol_op," ",frame," [",lo-1,":",hi-lo+1,"] )"))
  stop(idx)
}

# Lookup symbols found in a function body in the user's lexical scope, not in
# the H2O wrapper scope.  Return FALSE if not found.  Return a list of the one
# lookup result if found.
.lookup <- function(sym,envnum) {
  # Full normal lookup, NA if symbol is undefined.  Search the passed in
  # dynamic environment in reverse order.
  while( envnum >= 0 ) {
    env = sys.frame(envnum)
    if( exists(sym,envir=env,inherits=FALSE) ) return(list(get(sym,env)))
    envnum = envnum-1
  }
  print(paste0("Lookup failed to find ",sym))
  return(FALSE)
}
