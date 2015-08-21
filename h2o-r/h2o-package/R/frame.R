#`
#` Frame and AST Nodes
#`
#` To conveniently and safely pass messages between R and H2O, this package
#` relies on S3 objects to capture and pass state.  The end user will typically
#` never have to reason with these objects directly, as there are S3 accessor
#` methods provided for creating new objects.
#`
#` S3 Frame class objects are pointers to either data in an H2O cluster, or
#` potential data (future calculations) in the cluster.  They are also classic
#` compiler AST Nodes (to hold future calculations).  They are implemented with
#` simple R environment objects.
#`
#` Like AST Nodes in compilers all over, Frames build a simple DAG where the
#` nodes contain an operator and some outgoing edges.  There is a GC finalizer
#` to delete the server-side copy of a Frame
#`
#`
#` Frame/AST Node/environment Fields
#` E$op       <- Operation or opcode that produces this Frame, a string
#` # If the ID field is present, this Node is user  -managed, and will NOT be deleted by GC.
#` # If the ID field is missing, this Node is client-managed, and will     be deleted by GC.
#` E$id       <- A user-specified name, used in the H2O cluster
#` # If the ID field is present, this node is evaluated on creation and the
#` # EVAL field is set equal to the ID field.  If this ID field is missing, the
#` # EVAL field holds either a list-of-Nodes representing the lazy evaluation
#` # semantics, OR a tmp ID representing the H2O-cluster name for the evaluated
#` # result.  Thus for all evaluated nodes the EVAL field holds the cluster name.
#` E$eval     <- A (possibly empty) list of dependent Nodes.  Set to an ID string for evaluated ops
#` E$visit    <- A temporary field used to manage DAG visitation
#` 
#` # A number of fields represent cached queries of an evaluated frame.
#` E$data <- A cached result; can be a scalar, or a R dataframe result holding
#`           the first N (typically 10) rows and all cols of the frame
#` E$nrow   <- the row count (total size, generally much larger than the local cached rows)


is.Frame <- function(fr) !missing(fr) && class(fr)[1]=="Frame"
chk.Frame <- function(fr) if( is.Frame(fr) ) fr else stop("must be a Frame")

# Horrible internal shortcut to get at our fields in Frame environments via
# "frame:id".  Using "$" calls the overloaded dataframe column resolution - and
# doesn't look for our internal fields.
`:` <- function(x,y) {
  if( !is.Frame(x) ) return(.Primitive(":")(x,y))
  fld <- as.character(substitute(y))
  if( exists(fld,x,inherits=FALSE) ) get(fld,envir=x, inherits=FALSE)
  else NULL
}

.set <- function(x,name,value) assign(name,value,pos=x)


# GC Finalizer - called when GC collects a Frame Must be defined ahead of constructors.
.nodeFinalizer <- function(x) {
  if( !exists("id",envir=x) && is.character(x:eval) )
    .h2o.__remoteSend(paste0(.h2o.__DKV, "/", x:eval), method = "DELETE")
}

# Make a raw named data frame.  The key will exist on the server, and will be
# the passed-in ID.  Because it is named, it is not GCd.  It is fully evaluated.
.newFrame <- function(op,id) {
  stopifnot( is.character(id) )
  node <- structure(new.env(parent = emptyenv()), class="Frame")
  .set(node,"op",op)
  .set(node,"id",id)
  .set(node,"eval",id)
  node
}

# A new lazy expression
.newExpr <- function(op,...) .newExprList(op,list(...))

.newExprList <- function(op,li) {
  node <- structure(new.env(parent = emptyenv()), class="Frame")
  .set(node,"op",op)
  .set(node,"eval",li)
  reg.finalizer(node, .nodeFinalizer, onexit=TRUE)
  node
}


#' S3 Group Generic Functions for H2O
#'
#' Methods for group generic functions and H2O objects.
#'


#' @param x,y H2O Frame objects.
#' @param digits number of digits to be used in \code{round} or \code{signif}
#' @param \dots further arguments passed to or from methods
#' @param na.rm logical: should missing values be removed?
#' @name H2OS3GroupGeneric
NULL

#' @rdname H2OS3GroupGeneric
#' @export
Ops.Frame <- function(x,y) 
  .newExpr(.Generic,
           if( is.character(x) ) .quote(x) else x,
           if( is.character(y) ) .quote(y) else y)

Math.Frame <- function(x) .newExpr(.Generic,x)

Math.Frame <- function(x,y) .newExpr(.Generic,x,y)

Math.Frame <- function(x,...) .newExprList(.Generic,list(x,...))

Summary.Frame <- function(x,...,na.rm) {
  if( na.rm ) stop("na.rm versions not impl")
  # Eagerly evaluation, to produce a scalar
  res <- .eval.frame(.newExprList(.Generic,list(x,...))):data
  if( .Generic=="all" ) as.logical(res) else res
}

#' @rdname H2OS4groupGeneric
#' @export
is.na.Frame <- function(x) .newExpr("is.na", x)

`!.Frame` <- function(x) .newExpr("!!",x)

# True if this Node appears to be shared, and thus need a server-side temp
#require(pryr)
.shared <- function(x) {
#  q <- .Call("named", x, PACKAGE="named")
#  print(paste0("REF: ",q))
#  q <- pryr:::named2(substitute(x),parent.frame())
#  q <- refs(x)
#  q>=1
  return(TRUE)
}

# Internal recursive clear-visit-flag function, goes hand-n-hand with a
# recursive visitor
.clearvisit <- function(x) {
  if( !is.null(x:visit) ) return()
  rm("visit",envir=x);
  if( !is.null(x:eval) )
    lapply(x:eval, function(child) { if( is.environment(child) ) .clearvisit(child) } )
}

# Internal recursive printer
.pfr <- function(x,e) {
  if( !is.null(xid<- x:id  ) ) return(xid)
  if( !is.null(xid<- x:visit) ) return(xid)
  .set(x,"visit", xid <- paste0("tmp",e:cnt))
  .set(e,"cnt", e:cnt+1)
  res <- ifelse( is.null(x:eval), "EVALd",
                 paste(sapply(x:eval, function(child) { if( is.Frame(child) ) .pfr(child) else child }),collapse=" "))
  res <- paste0("(",x:op," ",res,")")
  if( .shared(x) ) 
    res<-paste0("(tmp= ",xid," ",res,")")
  res
}

# Pretty print the reachable execution DAG from this Frame, withOUT evaluating it
pfr <- function(x) { chk.Frame(x); e<-new.env(); .set(e,"cnt",0); print(.pfr(x),e); .clearvisit(x); invisible() }

.eval.impl <- function(x, toplevel) {
  if( is.character(xchild<-x:eval) ) return( if(is.data.frame(x:data) || is.null(x:data) ) xchild else x:data )
  res <- paste(sapply(xchild, function(child) { if( is.Frame(child) ) .eval.impl(child,F) else child }),collapse=" ")
  res <- paste0("(",x:op," ",res,")")
  .set(x,"eval", xchild <- .key.make("RTMP")) # Flag as code-emitted
  if( .shared(x) && !toplevel) 
    res <- paste0("(tmp= ",xchild," ",res,")")
  res
}

# Evaluate this Frame on demand.  The EVAL field is used as a flag to
# signal that the node has already been executed.  Once evaluted
# the EVAL field holds the cluster name; thus:
#    .eval.frame(hex):eval
# Always yields the cluster's name for the evaluated results.
.eval.frame <- function(x) {
  chk.Frame(x)
  if( !is.character(x:eval) ) {
    exec_str <- .eval.impl(x,TRUE)
    print(paste0("EXPR: ",exec_str))
    # Execute the AST on H2O
    res <- .h2o.__remoteSend(.h2o.__RAPIDS, h2oRestApiVersion = 99, ast=exec_str, id=x:eval, method = "POST")
    if( !is.null(res$error) ) stop(paste0("Error From H2O: ", res$error), call.=FALSE)
    if( !is.null(res$scalar) ) .set(x,"data",res$scalar)
  }
  x
}

#' Returns the Dimensions of an H2O Frame
#'
#' Returns the number of rows and columns for a Frame object.
#'
#' @param x An \linkS4class{Frame} object.
#' @seealso \code{\link[base]{dim}} for the base R method.
#' @examples
#' localH2O <- h2o.init()
#' iris.hex <- as.h2o(iris)
#' dim(iris.hex)
#' @export
dim.Frame <- function(x) { data <- .fetch.data(x,1); unlist(list(x:nrow,ncol(data))) }

#` Column names of an H2O Frame
dimnames.Frame <- function(x) .Primitive("dimnames")(.fetch.data(x,1))

#` Column names of an H2O Frame
names.Frame <- function(x) .Primitive("names")(.fetch.data(x,1))

colnames <- function(x, do.NULL=TRUE, prefix = "col") {
  if( !is.Frame(x) ) return(base::colnames(x,do.NULL,prefix))
  return(names.Frame(x))
}

#` Length - number of columns
length.Frame <- function(x) { data <- .fetch.data(x,1); if( is.data.frame(data) ) ncol(data) else 1; }
h2o.length <- length.Frame

#'
#' Return the levels from the column requested column.
#'
#' @param x An \linkS4class{Frame} object.
#' @param i The index of the column whose domain is to be returned.
#' @seealso \code{\link[base]{levels}} for the base R method.
#' @examples
#' iris.hex <- as.h2o(iris)
#' h2o.levels(iris.hex, 5)  # returns "setosa"     "versicolor" "virginica"
#' @export
h2o.levels <- function(x, i) {
  chk.Frame(x)
  if( missing(i) ) {
    if( ncol(x) > 1 ) return( .newExpr("levels", x) )
    i <- 1
  } else if( is.character(i) ) i <- match(i, colnames(x))
  if( is.na(i) ) stop("no such column found")
  col_idx <- i
  if (col_idx <= 0) col_idx <- 1
  if (col_idx >= ncol(x)) col_idx <- ncol(x)
  res <- .h2o.__remoteSend(.h2o.__COL_DOMAIN(x:eval, colnames(x)[col_idx]), method="GET")
  res$domain[[1]]
}

#'
#' Set Levels of H2O Factor Column
#'
#' Works on a single categorical vector. New domains must be aligned with the old domains.
#' This call has SIDE EFFECTS and mutates the column in place (does not make a copy).
#'
#' @param x A single categorical column.
#' @param levels A character vector specifying the new levels. The number of new levels must match the number of old levels.
#' @export
h2o.setLevels <- function(x, levels) .newExpr("setDomain", chk.Frame(x), levels)

#'
#' Return the Head or Tail of an H2O Dataset.
#'
#' Returns the first or last rows of an H2O parsed data object.
#'
#' @name h2o.head
#' @param x An \linkS4class{Frame} object.
#' @param n (Optional) A single integer. If positive, number of rows in x to return. If negative, all but the n first/last number of rows in x.
#' @param ... Further arguments passed to or from other methods.
#' @return A data frame containing the first or last n rows of an \linkS4class{Frame} object.
#' @examples
#' library(h2o)
#' localH2O <- h2o.init(ip = "localhost", port = 54321, startH2O = TRUE)
#' ausPath <- system.file("extdata", "australia.csv", package="h2o")
#' australia.hex <- h2o.uploadFile(localH2O, path = ausPath)
#' head(australia.hex, 10)
#' tail(australia.hex, 10)
#' @export
head.Frame <- function(x,n=6L) {
  stopifnot(length(n) == 1L)
  n <- if (n < 0L) max(nrow(x) + n, 0L)
       else        min(n, nrow(x))
  if( n >= 0L && n <= 1000L ) # Short version, just report the cached internal DF
    head(.fetch.data(x,n),n)
  else # Long version, fetch all asked for "the hard way"
    .newExpr("rows",x,paste0("[0:",n,"]"))
}

#' @rdname h2o.head
#' @export
tail.Frame <- function(x,n=6L) {
  endidx <- nrow(x)
  n <- ifelse(n < 0L, max(endidx + n, 0L), min(n, endidx))
  if( n==0L ) head(x,n=0L)
  else {
    startidx <- max(1L, endidx - n + 1)
    .newExpr("rows",x,paste0("[",startidx-1,":",(endidx-startidx),"]"))
  }
}

is.factor <- function(x) {
  # Eager evaluate and use the cached result to return a scalar
  if( is.Frame(x) ) {
    x <- .fetch.data(x,1)
    if( ncol(x)==1L ) x <- x[,1]
  }
  base::is.factor(x)
}


#' Print An H2O Frame
#'
#' @param x An H2O Frame object
#' @export
print.Frame <- function(x) { cat(as.character(x)); invisible(x) }

#' Display the structure of an H2O Frame object
#'
#' @param An H2O Frame object
#' @param cols Print the per-column str for the Frame
#' @export
str.Frame <- function(x, cols=FALSE, ...) {
  if (length(l <- list(...)) && any("give.length" == names(l)))
    invisible(NextMethod("str", ...))
  else if( !cols ) invisible(NextMethod("str", give.length = FALSE, ...))

  nc <- ncol(x)
  nr <- nrow(x)
  cc <- colnames(x)
  width <- max(nchar(cc))
  df <- head(.fetch.data(x,10L),10L)

  # header statement
  cat("\nFrame '", x:eval, "':\t", nr, " obs. of  ", nc, " variable(s)", "\n", sep = "")
  l <- list()
  for( i in 1:nc ) {
    cat("$ ", cc[i], rep(' ', width - max(na.omit(c(0,nchar(cc[i]))))), ": ", sep="")
    first.10.rows <- df[,i]
    if( is.factor(first.10.rows) ) {
      lvls <- levels(first.10.rows)
      nl <- length(lvls)
      lvls.print <- lvls[1L:min(nl,2L)]
      cat("Factor w/ ", nl, " level(s) ", paste(lvls.print, collapse='","'), "\",..: ", sep="")
      cat(paste(match(first.10.rows, lvls), collapse=" "), " ...\n", sep="")
    } else
      cat("num ", paste(first.10.rows, collapse=' '), if( nr > 10L ) " ...", "\n", sep="")
  }
}

.h2o.gc <- function() {
  print("H2O triggered a GC in R")
  gc()
}

# Convert to Currents number-list syntax
.num.list <- function(nl) paste0('[',paste0(nl,collapse=" "),']')

# Convert to Currents string-list syntax
.quote <- function(x) paste0('"',x,'"')
.str.list <- function(sl) paste0('[',paste0('"',sl,'"',collapse=" "),']')

# Convert a row or column selector to zero-based numbering and return a string
.row.col.selector <- function( sel ) {
  if( is.numeric(sel) ) { # number list for column selection; zero based
    sel2 <- lapply(sel,function(x) if( x==0 ) stop("Cannot select row or column 0") else if( x > 0 ) x-1 else x)
    .num.list(sel2) 
  } else as.character(sel)
}


#' Extract or Replace Parts of an Frame Object
#'
#' Operators to extract or replace parts of Frame objects.
#'
#' @name Frame-Extract
#' @param x object from which to extract element(s) or in which to replace element(s).
#' @param row,col indices specifying elements to extract or replace. Indices are numeric or
#'        character vectors or empty (missing) or will be matched to the names.
#' @param name a literal character string or a name (possibly backtick quoted).
#' @param exact controls possible partial matching of \code{[[} when extracting
#'              a character
#' @param value an array-like H2O object similar to \code{x}.
NULL

#' @aliases [,Frame-method
#' @rdname Frame-Extract
#' @export
`[.Frame` <- function(data,row,col) {
  chk.Frame(data)

  # This function is called with a huge variety of argument styles
  # Here's the breakdown: 
  #   Style          Type #args  Description
  # df[]           - na na 2    both missing, identity with df
  # df[,]          - na na 3    both missing, identity with df
  # df[2,]         - r  na 3    constant row, all cols
  # df[1:150,]     - r  na 3    selection of rows, all cols
  # df[3]          - c  na 2    constant column, not constant row
  # df[,3]         - na c  3    constant column
  # df[,1:10]      - na c  3    selection of columns
  # df["colname"]  - c  na 2    single column by name, df$colname
  # df[,"colname"] - na c  3    single column by name
  # df[2,"colname"]- r  c  3    row slice and column-by-name
  # df[2,3]        - r  c  3    single element
  # df[1:150,1:10] - r  c  3    rectangular slice
  # df[a<b,]       - f  na 3    boolean row slice
  # df[a<b,c]      - f  c  3    boolean row slice

  if( nargs() == 2 ) {      # Only row, no column; nargs==2 distiguishes "df[2,]" (row==2) from "df[2]" (col==2)
    # Row is really column: cars[3] or cars["cylinders"] or cars$cylinders
    col <- row
    row <- NA
  }
  if( !missing(col) ) {     # Have a column selector?
    if( is.logical(col) ) { # Columns by boolean choice
      print(col)
      stop("unimplemented1")
    } else if( is.character(col) ) { # Columns by name
      idx <- match(col,colnames(data))-1 # Match on name, then zero-based
      if( is.na(idx) ) stop(paste0("No column '",col,"' found in ",paste(colnames(data),collapse=",")))
    } else { # Generic R expression
      idx <- .row.col.selector(col)
    }
    data <- .newExpr("cols",data,idx) # Column selector
  }
  # Have a row selector?
  if( !missing(row) && (is.Frame(row) || !is.na(row)) ) {
    if( !is.Frame(row) )    # Generic R expression
      row <- .row.col.selector(row)
    data <- .newExpr("rows",data,row) # Row selector
  }
  data
}

#' @rdname Frame-Extract
#' @export
`$.Frame` <- function(x, name) { x[[name, exact = FALSE]] }

#' @rdname Frame-Extract
#' @export
`[[.Frame` <- function(x, i, exact = TRUE) {
  if( missing(i) )  return(x)
  if( length(i) > 1L )  stop("`[[` can only select one column")
  if( is.character(i)) {
    if( exact )  i <-  match(i, colnames(x))
    else         i <- pmatch(i, colnames(x))
  }
  if( is.na(i) ) NULL
  else           x[,i]
}


#-----------------------------------------------------------------------------------------------------------------------
# Assignment Operations: [<-, $<-, [[<-, colnames<-, names<-
#-----------------------------------------------------------------------------------------------------------------------
#' @rdname Frame-Extract
#' @export
`[<-.Frame` <- function(data,row,col,...,value) {
  chk.Frame(data)
  allRow <- missing(row)
  allCol <- missing(col)
  if( !allCol && is.na(col) ) col <- as.list(match.call())$col

  if( !allRow && is.character(row) && allCol ) {  ## case where fr["baz"] <- qux
    allRow <- TRUE
    allCol <- FALSE
    col <- row
  }

  if(!allRow && !is.numeric(row))
    stop("`row` must be missing or a numeric vector")
  if(!allCol && !is.numeric(col) && !is.character(col))
    stop("`col` must be missing or a numeric or character vector")
  if( !is.null(value) && !is.Frame(value) ) {
    if( is.na(value) ) value <- NA_integer_  # pick an NA... any NA (the damned numeric one will do)
    else if( !is.numeric(value) && !is.character(value) )
      stop("`value` can only be an Frame object or a numeric or character vector")
  }

  # Row arg is missing, means "all the rows"
  if(allRow) rows <- paste0("[]") # Shortcut for "all rows"
  else       rows <- .row.col.selector(row)

  name <- NA
  if( allCol ) {   # Col arg is missing, means "all the cols"
    cols <- paste0("[]") # Shortcut for "all cols"
  } else {
    if( is.character(col) ) {
      idx <- match(col, colnames(data))
      if( any(is.na(idx)) ) { # Any unknown names?
        if( length(col) > 1 ) stop("unknown column names")
        else { idx <- ncol(data)+1; name <- col } # Append 1 unknown column
      }
    } else idx <- col
    if( is.null(value) ) return(`[.Frame`(data,row=-idx)) # Assign a null: delete by selecting inverse columns
    cols <- .row.col.selector(idx)
  }

  if( is.character(value) ) value <- .quote(value)
  res <- .newExpr("=", data, value, cols, rows)
  # Set col name and return updated frame
  if( !is.na(name) )  res <- .newExpr("colnames=", res, idx-1, .quote(name))
  res
}

#' @rdname Frame-Extract
#' @export
`$<-.Frame` <- function(data, name, value) {
  `[<-.Frame`(data,row=name,value=value) # col is missing on purpose
}

#' @rdname Frame-Extract
#' @export
`[[<-.Frame` <- function(data,name,value) `[<-`(data, row=name,value=chk.Frame(value))

`names<-.Frame` <- function(x, value) {
  .newExpr("colnames=", x, paste0("[0:",ncol(x),"]"), .str.list(value))
}

`colnames<-` <- function(x, value) {
  if( !is.Frame(x) ) return(base::`colnames<-`(x,value))
  return(`names<-.Frame`(x,if( is.Frame(value) ) colnames(value) else value))
}

#'
#' Quantiles of H2O Data Frame.
#'
#' Obtain and display quantiles for H2O parsed data.
#'
#' \code{quantile.Frame}, a method for the \code{\link{quantile}} generic. Obtain and return quantiles for
#' an \code{\linkS4class{Frame}} object.
#'
#' @param x An \code{\linkS4class{Frame}} object with a single numeric column.
#' @param probs Numeric vector of probabilities with values in [0,1].
#' @param combine_method How to combine quantiles for even sample sizes. Default is to do linear interpolation.
#'                       E.g., If method is "lo", then it will take the lo value of the quantile. Abbreviations for average, low, and high are acceptable (avg, lo, hi).
#' @param ... Further arguments passed to or from other methods.
#' @return A vector describing the percentiles at the given cutoffs for the \code{\linkS4class{Frame}} object.
#' @examples
#' # Request quantiles for an H2O parsed data set:
#' library(h2o)
#' localH2O <- h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(localH2O, path = prosPath)
#' # Request quantiles for a subset of columns in an H2O parsed data set
#' quantile(prostate.hex[,3])
#' for(i in 1:ncol(prostate.hex))
#'    quantile(prostate.hex[,i])
#' @export
h2o.quantile <- function(x,
                     # AUTOGENERATED params
                     probs = c(0.001, 0.01, 0.1, 0.25, 0.333, 0.5, 0.667, 0.75, 0.9, 0.99, 0.999),
                     combine_method = c("interpolate", "average", "avg", "low", "high"),
                     ...)
{
  # verify input parameters
  if (!is(x, "Frame")) stop("`x` must be an Frame object")
  #if(!na.rm && .h2o.__unary_op("any.na", x)) stop("missing values and NaN's not allowed if 'na.rm' is FALSE")
  if(!is.numeric(probs) || length(probs) == 0L || any(!is.finite(probs) | probs < 0 | probs > 1))
    stop("`probs` must be between 0 and 1 exclusive")

  combine_method = match.arg(combine_method)
  # match.arg converts partial string "lo"->"low", "hi"->"high" etc built in
  #           is the standard way to avoid warning: "the condition has length > 1 and only first will be used"
  #       and stops if argument wasn't found, built-in
  if (combine_method == "avg") combine_method = "average"  # 'avg'->'average' is too much for match.arg though

  #if(type != 2 && type != 7) stop("type must be either 2 (mean interpolation) or 7 (linear interpolation)")
  #if(type != 7) stop("Unimplemented: Only type 7 (linear interpolation) is supported from the console")
  res <- .newExpr("quantile", x, .num.list(probs), .quote(combine_method))
  res <- as.matrix(res)
  col <- as.numeric(res[,-1])
  names(col) <- paste0(100*res[,1], "%")
  col
}

#' @export
quantile.Frame <- h2o.quantile

#'
#' Summarizes the columns of a H2O data frame.
#'
#' A method for the \code{\link{summary}} generic. Summarizes the columns of an H2O data frame or subset of
#' columns and rows using vector notation (e.g. dataset[row, col])
#'
#' @name h2o.summary
#' @param object An \linkS4class{Frame} object.
#' @param factors The number of factors to return in the summary. Default is the top 6.
#' @param ... Further arguments passed to or from other methods.
#' @return A table displaying the minimum, 1st quartile, median, mean, 3rd quartile and maximum for each
#' numeric column, and the levels and category counts of the levels in each categorical column.
#' @examples
#' library(h2o)
#' localH2O = h2o.init()
#' prosPath = system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex = h2o.importFile(path = prosPath)
#' summary(prostate.hex)
#' summary(prostate.hex$GLEASON)
#' summary(prostate.hex[,4:6])
NULL

#' @rdname h2o.summary
#' @export
summary.Frame <- function(object, factors=6L, ...) {
  SIG.DIGITS    <- 12L
  FORMAT.DIGITS <- 4L
  cnames <- colnames(object)
  missing <- list()

  # for each numeric column, collect [min,1Q,median,mean,3Q,max]
  # for each categorical column, collect the first 6 domains
  # allow for optional parameter in ... factors=N, for N domain levels. Or could be the string "all". N=6 by default.
  fr.sum <- .h2o.__remoteSend(paste0("Frames/", object:eval, "/summary"), method = "GET")$frames[[1]]
  col.sums <- fr.sum$columns
  cols <- sapply(col.sums, function(col) {
    col.sum <- col
    col.type <- col.sum$type  # enum, string, int, real, time, uuid

    # numeric column: [min,1Q,median,mean,3Q,max]
    if( col.type %in% c("real", "int") ) {
      cmin <- cmax <- cmean <- c1Q <- cmedian <- c3Q <- NaN                                              # all 6 values are NaN by default
      if( !(is.null(col.sum$mins) || length(col.sum$mins) == 0L) ) cmin <- min(col.sum$mins,na.rm=TRUE)  # set the min
      if( !(is.null(col.sum$maxs) || length(col.sum$maxs) == 0L) ) cmax <- max(col.sum$maxs,na.rm=TRUE)  # set the max
      if( !(is.null(col.sum$mean))                               ) cmean<- col.sum$mean                  # set the mean

      if( !is.null(col.sum$percentiles) ){# set the 1st quartile, median, and 3rd quartile
        c1Q     <- col.sum$percentiles[4] # p=.25 col.rest$frames[[1]]$default_percentiles ==  c(0.001, 0.01, 0.1, 0.25, 0.333, 0.5, 0.666, 0.75, 0.9, 0.99, 0.999)
        cmedian <- col.sum$percentiles[6] # p=.5
        c3Q     <- col.sum$percentiles[8] # p=.75
      }

      missing.count <- NULL
      if( !is.null(col.sum$missing_count) && col.sum$missing_count > 0L ) missing.count <- col.sum$missing_count    # set the missing count

      params <- format(signif( as.numeric( c(cmin, c1Q, cmedian, cmean, c3Q, cmax) ), SIG.DIGITS), digits=FORMAT.DIGITS)   # do some formatting for pretty printing
      result <- c(paste0("Min.   :", params[1L], "  "), paste0("1st Qu.:", params[2L], "  "),
                  paste0("Median :", params[3L], "  "), paste0("Mean   :", params[4L], "  "),
                  paste0("3rd Qu.:", params[5L], "  "), paste0("Max.   :", params[6L], "  "))

      # return summary string for this column
      if( is.null(missing.count) ) result <- result
      else                         result <- c(result, paste0("NA's   :",missing.count,"  "))

      result
    } else if( col.type == "enum" ) {
      domains <- col.sum$domain
      domain.cnts <- col.sum$histogram_bins
      if( length(domain.cnts) < length(domains) ) domain.cnts <- c(domain.cnts, rep(NA, length(domains) - length(domain.cnts)))
      missing.count <- 0L
      if( !is.null(col.sum$missing_count) && col.sum$missing_count > 0L ) missing.count <- col.sum$missing_count    # set the missing count
      # create a dataframe of the counts and factor levels, then sort in descending order (most frequent levels at the top)
      df.domains <- data.frame(domain=domains,cnts=domain.cnts, stringsAsFactors=FALSE)
      df.domains <- df.domains[with(df.domains, order(-cnts)),]  # sort in descending order

      # TODO: check out that NA is valid domain level in enum column... get missing and NA together here, before subsetting
      row.idx.NA <- which( df.domains[,1L] == "NA")
      if( length(row.idx.NA) != 0 ) {
        missing.count <- missing.count + df.domains[row.idx.NA,2L]  # combine the missing and NAs found here
        df.domains <- df.domains[-row.idx.NA,]  # remove the NA level
      }

      factors <- min(factors, nrow(df.domains))
      df.domains.subset <- df.domains[1L:factors,]      # subset to the top `factors` (default is 6)

      # if there are any missing levels, plonk them down here now after we've subset.
      if( missing.count > 0L ) df.domains.subset <- rbind( df.domains.subset, c("NA", missing.count))

      # fish out the domains
      domains <- as.character(df.domains.subset[,1L])

      # fish out the counts
      counts <- as.character(df.domains.subset[,2L])

      # compute a width for the factor levels and also one for the counts
      width <- c( max(nchar(domains),0L, na.rm = TRUE), max(nchar(counts),0L, na.rm = TRUE) )
      # construct the result
      paste0(domains,sapply(domains, function(x) {
                      x <- max(0, nchar(x), na.rm = TRUE)
                      ifelse(width[1L] == x, "", paste(rep(' ', width[1L] - x), collapse='')) }),":",
                     sapply(counts,  function(y) {
                      y <- max(0, nchar(y), na.rm = TRUE)
                      ifelse(width[2L] == y, "", paste(rep(' ', width[2L] - y), collapse='')) }), counts, " ")

    } else {
      # types are time, uuid, string ... ignore for now?
#      c(paste0(col.type, ": ignored"))
      NULL
    }
  })
  names(cols) <- cnames
  result <- NULL
  if( is.matrix(cols) && ncol(cols) == 1L ) {
    result <- as.table(as.matrix(as.data.frame(cols, stringsAsFactors=FALSE)))
  } else {
    # need to normalize the result
    max.len <- max(sapply(cols, function(col) { length(col) }))
    # here's where normalization is done
    if( is.matrix(cols) ) {
      result <- as.table(cols)
    } else {
      cols <- data.frame( lapply(cols, function(col) {
                  if( length(col) < max.len ) c(col, rep("", max.len-length(col)))  # pad out result with "" for the prettiest of pretty printing... my pretty... and your little dog TOO! MUAHAHHAHA
                  else col                                                          # no padding necessary!
                }), stringsAsFactors=FALSE)                                         # keep as strings...

      result <- as.table(as.matrix(cols))
    }
  }
  colnames(result) <- cnames
  if( is.null(result) ) return(NULL)
  rownames(result) <- rep("", nrow(result))
  result
}

#-----------------------------------------------------------------------------------------------------------------------
# Summary Statistics Operations
#-----------------------------------------------------------------------------------------------------------------------

#'
#' Mean of a column
#'
#' Obtain the mean of a column of a parsed H2O data object.
#'
#' @param x An \linkS4class{Frame} object.
#' @param trim The fraction (0 to 0.5) of observations to trim from each end of \code{x} before the mean is computed.
#' @param na.rm A logical value indicating whether \code{NA} or missing values should be stripped before the computation.
#' @param ... Further arguments to be passed from or to other methods.
#' @seealso \code{\link[base]{mean}} for the base R implementation.
#' @examples
#' localH2O <- h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(localH2O, path = prosPath)
#' mean(prostate.hex$AGE)
#' @export
h2o.mean <- function(x, ...) .eval.frame(.newExpr("mean",x)):data

#' @rdname h2o.mean
#' @export
mean.Frame <- function(x, ...) {
  if( is.Frame(x) ) h2o.mean(x,...)
  else base::mean(x,...)
}

#' H2O Median
#'
#' Compute the median of a \linkS4class{Frame}.
#'
#' @param x An \linkS4class{Frame} object.
#' @param na.rm a logical, indicating whether na's are omitted.
#' @examples
#' localH2O <- h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(localH2O, path = prosPath, destination_frame = "prostate.hex")
#' @export
h2o.median <- function(x, na.rm = TRUE) .eval.frame(.newExpr("median",x,na.rm)):data

#' @rdname h2o.median
#' @export
median.Frame <- function(x, ...) {
  if( is.Frame(x) ) h2o.median(x,...)
  else base::median(x,...)
}

#
#" Mode of a enum or int column.
#" Returns single string or int value or an array of strings and int that are tied.
# TODO: figure out funcionality/use for documentation
# h2o.mode <-
# function(x) {
#  if(!is(x, "Frame")) || nrow(x) > 1L) stop('`x` must be a Frame object')
# tabularx = invisible(table(x))
#  maxCount = max(tabularx$Count)
#  modes = tabularx$row.names[tabularx$Count == maxCount]
#  return(unlist(as.list(as.matrix(modes))))
#}

#'
#' Variance of a column.
#'
#' Obtain the variance of a column of a parsed H2O data object.
#'
#' @param x An \linkS4class{Frame} object.
#' @param y \code{NULL} (default) or a column of an \linkS4class{Frame} object. The default is equivalent to y = x (but more efficient).
#' @param na.rm \code{logical}. Should missing values be removed?
#' @param use An optional character string to be used in the presence of missing values. This must be one of the following strings. "everything", "all.obs", or "complete.obs".
#' @seealso \code{\link[stats]{var}} for the base R implementation. \code{\link{h2o.sd}} for standard deviation.
#' @examples
#' localH2O <- h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(localH2O, path = prosPath)
#' var(prostate.hex$AGE)
#' @export
h2o.var <- function(x, y = NULL, na.rm = FALSE, use) {
  if( na.rm ) stop("na.rm versions not impl") 
  if( is.null(y) ) y <- x
  if(!missing(use)) {
    if (use %in% c("pairwise.complete.obs", "na.or.complete"))
      stop("Unimplemented : `use` may be either \"everything\", \"all.obs\", or \"complete.obs\"")
  } else
    use <- "everything"
  # Eager, mostly to match prior semantics but no real reason it need to be
  .fetch.data(.newExpr("var",x,y,.quote(use)),ncol(x))
}

#' @rdname h2o.var
#' @export
var <- function(x, y = NULL, na.rm = FALSE, use)  {
  if( is.Frame(x) ) h2o.var(x,y,na.rm,use)
  else stats::var(x,y,na.rm,use)
}

#'
#' Standard Deviation of a column of data.
#'
#' Obtain the standard deviation of a column of data.
#'
#' @param x An \linkS4class{Frame} object.
#' @param na.rm \code{logical}. Should missing values be removed?
#' @seealso \code{\link{h2o.var}} for variance, and \code{\link[stats]{sd}} for the base R implementation.
#' @examples
#' localH2O <- h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(localH2O, path = prosPath)
#' sd(prostate.hex$AGE)
#' @export
h2o.sd <- function(x, na.rm = FALSE) {
  if( na.rm ) stop("na.rm versions not impl") 
  .eval.frame(.newExpr("sd",x)):data
}

#' @rdname h2o.sd
#' @export
sd <- function(x, na.rm=FALSE) {
  if( is.Frame(x) ) h2o.sd(x)
  else stats::sd(x,na.rm)
}

#-----------------------------------------------------------------------------------------------------------------------
# Time & Date
#-----------------------------------------------------------------------------------------------------------------------

#' Convert Milliseconds to Years in H2O Datasets
#'
#' Convert the entries of a \linkS4class{Frame} object from milliseconds to years, indexed
#' starting from 1900.
#'
# is this still true?
#' This method calls the function of the MutableDateTime class in Java.
#' @param x An \linkS4class{Frame} object.
#' @return A \linkS4class{Frame} object containig the entries of \code{x} converted to years
#'         starting from 1900, e.g. 69 corresponds to the year 1969.
#' @seealso \code{\link{h2o.month}}
#' @export
h2o.year <- function(x) .newExpr("-",.newExpr("year", chk.Frame(x)),1900)

#' Convert Milliseconds to Months in H2O Datasets
#'
#' Converts the entries of a \linkS4class{Frame} object from milliseconds to months (on a 0 to
#' 11 scale).
#'
#' @param x An \linkS4class{Frame} object.
#' @return A \linkS4class{Frame} object containing the entries of \code{x} converted to months of
#'         the year.
#' @seealso \code{\link{h2o.year}}
#' @export
h2o.month <- function(x) .newExpr("month", chk.Frame(x))

#' Convert Milliseconds to Week of Week Year in H2O Datasets
#'
#' Converts the entries of a \linkS4class{Frame} object from milliseconds to weeks of the week
#' year (starting from 1).
#'
#' @param x An \linkS4class{Frame} object.
#' @return A \linkS4class{Frame} object containing the entries of \code{x} converted to weeks of
#'         the week year.
#' @seealso \code{\link{h2o.month}}
#' @export
h2o.week <- function(x) .newExpr("week", chk.Frame(x))

#' Convert Milliseconds to Day of Month in H2O Datasets
#'
#' Converts the entries of a \linkS4class{Frame} object from milliseconds to days of the month
#' (on a 1 to 31 scale).
#'
#' @param x An \linkS4class{Frame} object.
#' @return A \linkS4class{Frame} object containing the entries of \code{x} converted to days of
#'         the month.
#' @seealso \code{\link{h2o.month}}
#' @export
h2o.day <- function(x) .newExpr("day", chk.Frame(x))

#' Convert Milliseconds to Day of Week in H2O Datasets
#'
#' Converts the entries of a \linkS4class{Frame} object from milliseconds to days of the week
#' (on a 0 to 6 scale).
#'
#' @param x An \linkS4class{Frame} object.
#' @return A \linkS4class{Frame} object containing the entries of \code{x} converted to days of
#'         the week.
#' @seealso \code{\link{h2o.day}, \link{h2o.month}}
#' @export
h2o.dayOfWeek <- function(x) .newExpr("dayOfWeek", chk.Frame(x))

#' Convert Milliseconds to Hour of Day in H2O Datasets
#'
#' Converts the entries of a \linkS4class{Frame} object from milliseconds to hours of the day
#' (on a 0 to 23 scale).
#'
#' @param x An \linkS4class{Frame} object.
#' @return A \linkS4class{Frame} object containing the entries of \code{x} converted to hours of
#'         the day.
#' @seealso \code{\link{h2o.day}}
#' @export
h2o.hour <- function(x) .newExpr("hour", chk.Frame(x))

#' @rdname h2o.year
#' @export
year <- function(x) UseMethod('year', x)
#' @rdname h2o.year
#' @export
year.Frame <- h2o.year

#' @rdname h2o.month
#' @export
month <- function(x) UseMethod('month', x)
#' @rdname h2o.month
#' @export
month.Frame <- h2o.month

#' @rdname h2o.week
#' @export
week <- function(x) UseMethod('week', x)
#' @rdname h2o.week
#' @export
week.Frame <- h2o.week

#' @rdname h2o.day
#' @export
day <- function(x) UseMethod('day', x)
#' @rdname h2o.day
#' @export
day.Frame <- h2o.day

#' @rdname h2o.dayOfWeek
#' @export
dayOfWeek <- function(x) UseMethod('dayOfWeek', x)
#' @rdname h2o.dayOfWeek
#' @export
dayOfWeek.Frame <- h2o.dayOfWeek

#' @rdname h2o.hour
#' @export
hour <- function(x) UseMethod('hour', x)
#' @rdname h2o.hour
#' @export
hour.Frame <- h2o.hour

#' @export
as.Date.Frame <- function(x, format, ...) {
  if(!is.character(format)) stop("format must be a string")
  .newExpr("as.Date", chk.Frame(x), .quote(format), ...)
}

#' Set the Time Zone on the H2O Cloud
#'
#' @param tz The desired timezone.
#' @export
h2o.setTimezone <- function(tz) .newExpr("setTimeZone",.quote(tz))

#' Get the Time Zone on the H2O Cloud
#'
#' @export
h2o.getTimezone <- function() {
  ret <- .fetch.data(gtz <- .newExpr("getTimeZone"))
  h2o.rm(gtz:eval)
  ret
}

#' List all of the Time Zones Acceptable by the H2O Cloud.
#'
#' @export
h2o.listTimezones <- function() {
  ret <- .fetch.data(gtz <- .newExpr("listTimeZones"))
  h2o.rm(gtz:eval)
  ret
}

#-----------------------------------------------------------------------------------------------------------------------
# Casting Operations: as.data.frame, as.factor,
#-----------------------------------------------------------------------------------------------------------------------

#'
#' R data.frame -> Frame
#'
#' Import a local R data frame to the H2O cloud.
#'
#' @param x An \code{R} data frame.
#' @param destination_frame A string with the desired name for the H2O Frame.
#' @export
as.h2o <- function(x, destination_frame= "") {
  .key.validate(destination_frame)

  # TODO: Be careful, there might be a limit on how long a vector you can define in console
  if(!is.data.frame(x))
    if( length(x)==1L ) x <- data.frame(C1=x)
    else                x <- as.data.frame(x)
  types <- sapply(x, class)
  types <- gsub("integer", "numeric", types)
  types <- gsub("double", "numeric", types)
  types <- gsub("complex", "numeric", types)
  types <- gsub("logical", "enum", types)
  types <- gsub("factor", "enum", types)
  types <- gsub("character", "string", types)
  types <- gsub("Date", "Time", types)
  tmpf <- tempfile(fileext = ".csv")
  write.csv(x, file = tmpf, row.names = FALSE, na="NA_h2o")
  h2f <- h2o.uploadFile(tmpf, destination_frame = destination_frame, header = TRUE, col.types=types,
                        col.names=colnames(x, do.NULL=FALSE, prefix="C"), na.strings=rep(c("NA_h2o"),ncol(x)))
  file.remove(tmpf)
  h2f
}

#'
#' Converts a Parsed H2O data into a Data Frame
#'
#' Downloads the H2O data and then scans it in to an R data frame.
#'
#' @param x An \linkS4class{Frame} object.
#' @param ... Further arguments to be passed down from other methods.
#' @examples
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath)
#' as.data.frame(prostate.hex)
#' @export
as.data.frame.Frame <- function(x, ...) {
  .eval.frame(x)

  # Versions of R prior to 3.1 should not use hex string.
  # Versions of R including 3.1 and later should use hex string.
  use_hex_string <- getRversion() >= "3.1"
  conn = h2o.getConnection()

  url <- paste0('http://', conn@ip, ':', conn@port,
                '/3/DownloadDataset',
                '?frame_id=', URLencode(x:eval),
                '&hex_string=', as.numeric(use_hex_string))

  ttt <- getURL(url)
  n <- nchar(ttt)

  # Delete last 1 or 2 characters if it's a newline.
  # Handle \r\n (for windows) or just \n (for not windows).
  chars_to_trim <- 0L
  if (n >= 2L) {
      c <- substr(ttt, n, n)
      if (c == "\n") {
          chars_to_trim <- chars_to_trim + 1L
      }
      if (chars_to_trim > 0L) {
          c <- substr(ttt, n-1L, n-1L)
          if (c == "\r") {
              chars_to_trim <- chars_to_trim + 1L
          }
      }
  }

  if (chars_to_trim > 0L) {
    ttt2 <- substr(ttt, 1L, n-chars_to_trim)
    # Is this going to use an extra copy?  Or should we assign directly to ttt?
    ttt <- ttt2
  }

  # Substitute NAs for blank cells rather than skipping
  df <- read.csv((tcon <- textConnection(ttt)), blank.lines.skip = FALSE, ...)
  # df <- read.csv(textConnection(ttt), blank.lines.skip = FALSE, colClasses = colClasses, ...)
  close(tcon)
  df
}

#' @export
as.matrix.Frame <- function(x, ...) as.matrix(as.data.frame(x, ...))

#' @export
as.vector.Frame <- function(x, mode) base::as.vector(as.matrix.Frame(x))

#` 
as.double.Frame <- function(x) {
  res <- .fetch.data(x,1) # Force evaluation
  if( is.data.frame(res) ) {
    if( nrow(res)!=1L || ncol(res)!=1L ) stop("Cannot convert multi-element Frame into a double")
    res <- res[1,1]
  } 
  .Primitive("as.double")(res)
}

as.logical.Frame <- function(x) {
  res <- .fetch.data(x,1) # Force evaluation
  if( is.data.frame(res) ) {
    if( nrow(res)!=1L || ncol(res)!=1L ) stop("Cannot convert multi-element Frame into a logical")
    res <- res[1,1]
  } 
  .Primitive("as.logical")(res)
}

as.integer.Frame <- function(x) {
  x <- .fetch.data(x,1) # Force evaluation
  if( is.data.frame(x) ) {
    if( nrow(x)!=1L || ncol(x)!=1L ) stop("Cannot convert multi-element Frame into an integer")
    x <- x[1,1]
  }
  .Primitive("as.integer")(x)
}

#' Convert H2O Data to Factors
#'
#' Convert a column into a factor column.
#' @param x a column from an \linkS4class{Frame} data set.
#' @seealso \code{\link{is.factor}}.
#' @examples
#' localH2O <- h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(localH2O, path = prosPath)
#' prostate.hex[,2] <- as.factor(prostate.hex[,2])
#' summary(prostate.hex)
#' @export
as.factor <- function(x) {
  if( is.Frame(x) ) .newExpr("as.factor",x)
  else base::as.factor(x)
}


#' Convert an H2O Frame to a String
#'
#' @param x An H2O Frame object
#' @export
as.character.Frame <- function(x) {
  data <- .fetch.data(x,10L)
  if( !is.data.frame(data) ) return(as.character(data))
  nr <- nrow(x)
  nc <- ncol(x)
  if( nr==1L && nc==1L ) return(as.character(data[1,1]))
  res <- paste0("Frame with ",
      nr, ifelse(nr == 1L, " row and ", " rows and "),
      nc, ifelse(nc == 1L, " column\n", " columns\n"), collapse="")
  if( nr > 10L ) res <- paste0(res,"\nFirst 10 rows:\n")
  paste0(res,paste0(head(data, 10L),collapse="\n"),"\n")
}


#' Convert H2O Data to Numeric
#'
#' Converts an H2O column into a numeric value column.
#' @param x a column from an \linkS4class{Frame} data set.
#' localH2O <- h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(localH2O, path = prosPath)
#' prostate.hex[,2] <- as.factor (prostate.hex[,2])
#' prostate.hex[,2] <- as.numeric(prostate.hex[,2])
#' @export
as.numeric.Frame <- function(x) { .newExpr("as.numeric",x) }

as.numeric <- function(x) {
  if( !is.Frame(x) ) .Primitive("as.double")(x)
  else as.numeric.Frame(x)
}

#'
#' Delete Columns from a Frame
#'
#' Delete the specified columns from the Frame.  Returns a Frame without the specified
#' columns.
#'
#' @param data The Frame.
#' @param cols The columns to remove.
#' @export
h2o.removeVecs <- function(data, cols) {
  chk.Frame(data)
  if( missing(cols) ) stop("`cols` must be specified")
  del.cols <- cols
  if( is.character(cols) ) del.cols <- sort(match(cols,colnames(data)))
  .newExpr("cols",data,.row.col.selector(-del.cols))
}

#-----------------------------------------------------------------------------------------------------------------------
# Merge Operations: ifelse, cbind, rbind, merge
#-----------------------------------------------------------------------------------------------------------------------

#' H2O Apply Conditional Statement
#'
#' Applies conditional statements to numeric vectors in H2O parsed data objects when the data are
#' numeric.
#'
#' Only numeric values can be tested, and only numeric results can be returned for either condition.
#' Categorical data is not currently supported for this funciton and returned values cannot be
#' categorical in nature.
#'
#' @param test A logical description of the condition to be met (>, <, =, etc...)
#' @param yes The value to return if the condition is TRUE.
#' @param no The value to return if the condition is FALSE.
#' @return Returns a vector of new values matching the conditions stated in the ifelse call.
#' @examples
#' localH2O = h2o.init(ip = "localhost", port = 54321, startH2O = TRUE)
#' ausPath = system.file("extdata", "australia.csv", package="h2o")
#' australia.hex = h2o.importFile(path = ausPath)
#' australia.hex[,9] <- ifelse(australia.hex[,3] < 279.9, 1, 0)
#' summary(australia.hex)
#' @export
h2o.ifelse <- function(test, yes, no) .newExpr("ifelse",test,yes,no)

ifelse <- function(test, yes, no) {
  if( is.atomic(test) ) {
    if (typeof(test) != "logical") 
      storage.mode(test) <- "logical"
    if (length(test) == 1 && is.null(attributes(test))) {
      if (is.na(test)) {
        return(NA)
      } else if (test) {
        if( length(yes) == 1 && is.null(attributes(yes)) )
          return(yes)
        if( is.Frame(yes) ) return(yes[,1])
      } else {
        if( length(no) == 1 && is.null(attributes(no)) )
          return(no)
        if( is.Frame(no) ) return(no[,1])
      }
    }
  }

  if( is.Frame(test) || is.Frame(yes) || is.Frame(no) ) h2o.ifelse(test,yes,no)
  else base::ifelse(test,yes,no)
}

#' Combine H2O Datasets by Columns
#'
#' Takes a sequence of H2O data sets and combines them by column
#'
#' @name h2o.cbind
#' @param \dots A sequence of \linkS4class{Frame} arguments. All datasets must exist on the same H2O instance
#'        (IP and port) and contain the same number of rows.
#' @return An \linkS4class{Frame} object containing the combined \dots arguments column-wise.
#' @seealso \code{\link[base]{cbind}} for the base \code{R} method.
#' @examples
#' library(h2o)
#' localH2O <- h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(localH2O, path = prosPath)
#' prostate.cbind <- h2o.cbind(prostate.hex, prostate.hex)
#' head(prostate.cbind)
#' @export
h2o.cbind <- function(...) {
  li <- list(unlist(list(...)))
  use.args <- FALSE
  if( length(li)==1 && is.list(li[[1]]) ) {
    li <- li[[1]]
    use.args <- TRUE
  } else li <- list(...)
  lapply(li, function(l) chk.Frame(l) )
  .newExprList("cbind",li)
}

#' Combine H2O Datasets by Rows
#'
#' Takes a sequence of H2O data sets and combines them by rows
#'
#' @name h2o.rbind
#' @param \dots A sequence of \linkS4class{Frame} arguments. All datasets must exist on the same H2O instance
#'        (IP and port) and contain the same number of rows.
#' @return An \linkS4class{Frame} object containing the combined \dots arguments column-wise.
#' @seealso \code{\link[base]{rbind}} for the base \code{R} method.
#' @examples
#' library(h2o)
#' localH2O <- h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(localH2O, path = prosPath)
#' prostate.cbind <- h2o.rbind(prostate.hex, prostate.hex)
#' head(prostate.cbind)
#' @export
h2o.rbind <- function(...) {
  ls <- list(...)
  l <- unlist(ls)
  if( !is.list(l) ) l <- ls
  klazzez <- unlist(lapply(l, function(i) is.Frame(i)))
  if (any(!klazzez)) stop("`h2o.rbind` accepts only Frame objects")
  .newExprList("rbind", l)
}

#' Merge Two H2O Data Frames
#'
#' Merges two \linkS4class{Frame} objects by shared column names. Unlike the
#' base R implementation, \code{h2o.merge} only supports merging through shared
#' column names.
#'
#' In order for \code{h2o.merge} to work in multinode clusters, one of the
#' datasets must be small enough to exist in every node. Currently, this
#' function only supports \code{all.x = TRUE}. All other permutations will fail.
#'
#' @param x,y \linkS4class{Frame} objects
#' @param all.x a logical value indicating whether or not shared values are
#'        preserved or ignored in \code{x}.
#' @param all.y a logical value indicating whether or not shared values are
#'        preserved or ignored in \code{y}.
#' @examples
#' h2o.init()
#' left <- data.frame(fruit = c('apple', 'orange', 'banana', 'lemon', 'strawberry', 'blueberry'),
#' color = c('red', 'orange', 'yellow', 'yellow', 'red', 'blue'))
#' right <- data.frame(fruit = c('apple', 'orange', 'banana', 'lemon', 'strawberry', 'watermelon'),
#' citrus = c(FALSE, TRUE, FALSE, TRUE, FALSE, FALSE))
#' l.hex <- as.h2o(left)
#' r.hex <- as.h2o(right)
#' left.hex <- h2o.merge(l.hex, r.hex, all.x = TRUE)
#' @export
h2o.merge <- function(x, y, all.x = FALSE, all.y = FALSE) .newExpr("merge", x, y, all.x, all.y)

#' Group and Apply by Column
#'
#' Performs a group by and apply similar to ddply.
#'
#' In the case of \code{na.methods} within \code{gb.control}, there are three possible settings.
#' \code{"all"} will include \code{NAs} in computation of functions. \code{"rm"} will completely
#' remove all \code{NA} fields. \code{"ignore"} will remove \code{NAs} from the numerator but keep
#' the rows for computational purposes. If a list smaller than the number of columns groups is
#' supplied, the list will be padded by \code{"ignore"}.
#'
#' Similar to \code{na.methods}, \code{col.names} will pad the list with the default column names if
#' the length is less than the number of colums groups supplied.
#' @param data an \linkS4class{Frame} object.
#' @param by a list of column names
#' @param \dots any supported aggregate function.
#' @param order.by Takes a vector column names or indices specifiying how to order the group by result.
#' @param gb.control a list of how to handle \code{NA} values in the dataset as well as how to name
#'        output columns. See \code{Details:} for more help.
#' @return Returns a new \linkS4class{Frame} object with columns equivalent to the number of
#'         groups created
#' @export
h2o.group_by <- function(data, by, ..., order.by=NULL, gb.control=list(na.methods=NULL, col.names=NULL)) {
  chk.Frame(data)  

  # handle the columns
  # we accept: c('col1', 'col2'), 1:2, c(1,2) as column names.
  if(is.character(by)) {
    vars <- match(by, colnames(data))
    if (any(is.na(vars)))
      stop('No column named ', by, ' in ', substitute(data), '.')
  } else if(is.integer(by)) {
    vars <- by
  } else if(is.numeric(by)) {   # this will happen eg c(1,2,3)
    vars <- as.integer(by)
  }
  if(vars <= 0L || vars > ncol(data))
    stop('Column ', vars, ' out of range for frame columns ', ncol(data), '.')

  a <- substitute(list(...))
  a[[1]] <- NULL  # drop the wrapping list()
  nAggs <- length(a)  # the number of aggregates
  # for each aggregate, build this list: (agg,col.idx,na.method,col.name)
  agg.methods <- unlist(lapply(a, function(agg) as.character(agg[[1]]) ))
  col.idxs    <- unlist(lapply(a, function(agg, envir) {
    # to get the column index, check if the column passed in the agg (@ agg[[2]]) is numeric
    # if numeric, then eval it and return
    # otherwise, as.character the *name* and look it up in colnames(data) and fail/return appropriately
    agg[[2]] <- eval(agg[[2]], envir)
    if( is.numeric(agg[[2]]) || is.integer(agg[[2]]) ) { return(eval(agg[[2]])) }
    col.name <- eval(as.character(agg[[2]]), parent.frame())
    col.idx <- match(col.name, colnames(data))

    # no such column, stop!
    if( is.na(col.idx) ) stop('No column named ', col.name, ' in ', substitute(data), '.')

    # got a good column index, return it.
    col.idx
  }, parent.frame()))

  # default to "all" na.method
  na.methods.defaults <- rep("all", nAggs)

  # default to agg_col.name for the column names
  col.names.defaults  <- paste0(agg.methods, "_", colnames(data)[col.idxs])

  # 1 -> 0 based indexing of columns
  col.idxs <- col.idxs - 1

  ### NA handling ###

  # go with defaults
  if( is.null(gb.control$na.methods) ) {
    gb.control$na.methods <- na.methods.defaults

  # have fewer na.methods passed in than aggregates to compute -- pad with defaults
  } else if( length(gb.control$na.methods) < nAggs ) {

    # special case where only 1 method was passed, and so that is the method for all aggregates
    if( length(gb.control$na.methods) == 1L ) {
      gb.control$na.methods <- rep(gb.control$na.methods, nAggs)
    } else {
      n.missing <- nAggs - length(gb.control$na.methods)
      gb.control$na.methods <- c(gb.control$na.methods, rep("all", n.missing))
    }

  # have more na.methods than aggregates -- rm extras
  } else if( length(gb.control$na.methods) > nAggs ) {
    gb.control$na.methods <- gb.control$na.methods[1:nAggs]
  } else {
    # no problem...
  }

  ### End NA handling ###

  ### Column Name Handling ###

  # go with defaults
  if( is.null(gb.control$col.names) ) {
    gb.control$col.names <- col.names.defaults

  # have fewer col.names passed in than aggregates -- pad with defaults
  } else if( length(gb.control$col.names) < nAggs ) {

    # no special case for only 1 column!
    n.missing <- nAggs - length(gb.control$col.names)
    gb.control$col.names <- c(gb.control$col.names, col.names.defaults[(nAggs-n.missing+1):nAggs])

  # have more col.names than aggregates -- rm extras
  } else if( length(gb.control$col.names) > nAggs ) {
    gb.control$col.names <- gb.control$col.names[1:nAggs]
  }

  ### End Column Name handling ###


  # Build the aggregates! reminder => build this list: (agg,col.idx,na.method,col.name)
  aggs <- unlist(recursive=F, lapply(1:nAggs, function(idx) {
    list(agg.methods[idx], eval(col.idxs[idx]), gb.control$na.methods[idx], gb.control$col.names[idx])
  }))


  ### ORDER BY ###
  vars2 <- NULL
  if( !is.null(order.by) ) {
    if(is.character(order.by)) {
        vars2 <- match(order.by, by)
        if (any(is.na(vars2)))
          stop('No column named ', order.by, ' in ', by, '.')
    } else if(is.integer(order.by)) {
      vars2 <- order.by
    } else if(is.numeric(order.by)) {   # this will happen eg c(1,2,3)
      vars2 <- as.integer(order.by)
    }
    if(vars2 < 1L || vars2 > ncol(data)) stop('Column ', vars2, ' out of range for frame columns ', ncol(data), '.')
  }

  ### END ORDER BY ###

  # create the AGG AST
  AGG <- .newExprList("agg",aggs)

  # create the group by AST
  .newExpr("groupby",data,.row.col.selector(vars),AGG,.row.col.selector(vars2))
}

#' Produce a Vector of Random Uniform Numbers
#'
#' Creates a vector of random uniform numbers equal in length to the length of the specified H2O
#' dataset.
#'
#' @param x An \linkS4class{Frame} object.
#' @param seed A random seed used to generate draws from the uniform distribution.
#' @return A vector of random, uniformly distributed numbers. The elements are between 0 and 1.
#' @examples
#' library(h2o)
#' localH2O = h2o.init()
#' prosPath = system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex = h2o.importFile(path = prosPath, destination_frame = "prostate.hex")
#' s = h2o.runif(prostate.hex)
#' summary(s)
#'
#' prostate.train = prostate.hex[s <= 0.8,]
#' prostate.train = h2o.assign(prostate.train, "prostate.train")
#' prostate.test = prostate.hex[s > 0.8,]
#' prostate.test = h2o.assign(prostate.test, "prostate.test")
#' nrow(prostate.train) + nrow(prostate.test)
#' @export
h2o.runif <- function(x, seed = -1) {
  if (!is.numeric(seed) || length(seed) != 1L || !is.finite(seed)) stop("`seed` must be an integer >= 0")
  if (seed == -1) seed <- floor(runif(1,1,.Machine$integer.max*100))
  .newExpr("h2o.runif", chk.Frame(x), seed)
}

#-----------------------------------------------------------------------------------------------------------------------
# *ply methods: ddply, apply, lapply, sapply,
#-----------------------------------------------------------------------------------------------------------------------
#' Apply on H2O Datasets
#'
#' Method for apply on \linkS4class{Frame} objects.
#'
#' @param X an \linkS4class{Frame} object on which \code{apply} will operate.
#' @param MARGIN the vector on which the function will be applied over, either
#'        \code{1} for rows or \code{2} for columns.
#' @param FUN the function to be applied.
#' @param \dots optional arguments to \code{FUN}.
#' @return Produces a new \linkS4class{Frame} of the output of the applied
#'         function. The output is stored in H2O so that it can be used in
#'         subsequent H2O processes.
#' @seealso \link[base]{apply} for the base generic
#' @examples
#' h2o.init()
#' irisPath = system.file("extdata", "iris.csv", package="h2o")
#' iris.hex = h2o.importFile(path = irisPath, destination_frame = "iris.hex")
#' summary(apply(iris.hex, 2, sum))
#' @export
apply <- function(X, MARGIN, FUN, ...) {
  if( !is.Frame(X) ) return(base::apply(X,MARGIN,FUN,...))

  # Margin must be 1 or 2 and specified
  if( missing(MARGIN) || !(length(MARGIN) <= 2L && all(MARGIN %in% c(1L, 2L))) )
    stop("MARGIN must be either 1 (rows), 2 (cols), or a vector containing both")
  # Basic sanity checking on function
  if( missing(FUN) ) stop("FUN must be an R function")
  .FUN <- NULL
  if( is.character(FUN) ) .FUN <- get(FUN)
  if( !is.null(.FUN) && !is.function(.FUN) )    stop("FUN must be an R function!")
  else if( is.null(.FUN) && !is.function(FUN) ) stop("FUN must be an R function")
  if( !is.null(.FUN) ) FUN <- as.name(FUN)

  # Deal with extra arguments
  l <- list(...)
  if(length(l) > 0L) {
    tmp <- sapply(l, function(x) { !class(x) %in% c("Frame", "numeric", "character", "logical") } )
    if(any(tmp)) stop("H2O only recognizes Frame, numeric, and character objects.")

    idx <- which(sapply(l, function(x) is(x, "Frame")))
    extra_arg_names <- as.list(match.call())
    for (i in idx) {
      key <- as.character(extra_arg_names[[i]])
      if( is.Frame(x)) next
      x <- l[idx]
      h2o.assign(x, key)
      l[idx] <- x
    }
    stop("Currents 'apply' does not accept extra function arguments")
  }

  # Process the function. Decide if it's an anonymous fcn, or a named one.
  if( typeof(FUN) == "builtin" || typeof(FUN) == "symbol") {
    fname <- as.character(substitute(FUN))
    if( fname %in% .h2o.primitives ) return(.newExpr("apply",X,MARGIN,fname))
    stop(paste0("Function '",fname,"' not in .h2o.primitives list and not an anonymous function, unable to convert it to Currents"))
  }

  # Explode anonymous function into a Currents AST.  Pass along the dynamic
  # environment (not the static environment the H2O wrapper itself is compiled
  # in).  Unknown variables in the function body will be looked up in the
  # dynamic scope.
  .newExpr("apply",X,MARGIN,.fun.to.ast(FUN, list(), sys.parent(1) ))
}

#' Inserting Missing Values to an H2O DataFrame
#'
#' *This is primarily used for testing*. Randomly replaces a user-specified fraction of
#' entries in a H2O dataset with missing values.
#'
#' @param data An \linkS4class{Frame} object representing the dataset.
#' @param fraction A number between 0 and 1 indicating the fraction of entries
#'        to replace with missing.
#' @param seed A random number used to select which entries to replace with
#'        missing values. Default of \code{seed = -1} will automatically
#'        generate a seed in H2O.
#' @section WARNING: This will modify the original dataset. Unless this is intended,
#' this function should only be called on a subset of the original.
#' @examples
#' library(h2o)
#' localH2O <- h2o.init()
#' irisPath <- system.file("extdata", "iris.csv", package = "h2o")
#' iris.hex <- h2o.importFile(localH2O, path = irisPath)
#' summary(iris.hex)
#' irismiss.hex <- h2o.insertMissingValues(iris.hex, fraction = 0.25)
#' head(irismiss.hex)
#' summary(irismiss.hex)
#' @export
h2o.insertMissingValues <- function(data, fraction=0.1, seed=-1) {
  parms = list()
  parms$dataset <- .eval.frame(data):eval # Eager force evaluation
  parms$fraction <- fraction
  if( !missing(seed) )
    parms$seed <- seed
  json <- .h2o.__remoteSend(method = "POST", page = 'MissingInserter', .params = parms)
  .h2o.__waitOnJob(json$key$name)
  data
}

#' Data Frame Creation in H2O
#'
#' Creates a data frame in H2O with real-valued, categorical, integer, and binary columns specified by the user.
#'
#' @param key A string indicating the destination key. If empty, this will be auto-generated by H2O.
#' @param rows The number of rows of data to generate.
#' @param cols The number of columns of data to generate. Excludes the response column if \code{has_response = TRUE}.
#' @param randomize A logical value indicating whether data values should be randomly generated. This must be TRUE if either \code{categorical_fraction} or \code{integer_fraction} is non-zero.
#' @param value If \code{randomize = FALSE}, then all real-valued entries will be set to this value.
#' @param real_range The range of randomly generated real values.
#' @param categorical_fraction The fraction of total columns that are categorical.
#' @param factors The number of (unique) factor levels in each categorical column.
#' @param integer_fraction The fraction of total columns that are integer-valued.
#' @param integer_range The range of randomly generated integer values.
#' @param binary_fraction The fraction of total columns that are binary-valued.
#' @param binary_ones_fraction The fraction of values in a binary column that are set to 1.
#' @param missing_fraction The fraction of total entries in the data frame that are set to NA.
#' @param response_factors If \code{has_response = TRUE}, then this is the number of factor levels in the response column.
#' @param has_response A logical value indicating whether an additional response column should be pre-pended to the final H2O data frame. If set to TRUE, the total number of columns will be \code{cols+1}.
#' @param seed A seed used to generate random values when \code{randomize = TRUE}.
#' @return Returns a \linkS4class{Frame} object.
#' @examples
#' \dontrun{
#' library(h2o)
#' localH2O <- h2o.init()
#' hex <- h2o.createFrame(localH2O, rows = 1000, cols = 100, categorical_fraction = 0.1,
#'                        factors = 5, integer_fraction = 0.5, integer_range = 1,
#'                        has_response = TRUE)
#' head(hex)
#' summary(hex)
#'
#' hex2 <- h2o.createFrame(localH2O, rows = 100, cols = 10, randomize = FALSE, value = 5,
#'                         categorical_fraction = 0, integer_fraction = 0)
#' summary(hex2)
#' }
#' @export
h2o.createFrame <- function(key = "", rows = 10000, cols = 10, randomize = TRUE,
                            value = 0, real_range = 100, categorical_fraction = 0.2, factors = 100,
                            integer_fraction = 0.2, integer_range = 100, binary_fraction = 0.1,
                            binary_ones_fraction = 0.02, missing_fraction = 0.01, response_factors = 2,
                            has_response = FALSE, seed) {
  if(!is.numeric(rows)) stop("`rows` must be a positive number")
  if(!is.numeric(cols)) stop("`cols` must be a positive number")
  if(!missing(seed) && !is.numeric(seed)) stop("`seed` must be a numeric value")
  if(!is.logical(randomize)) stop("`randomize` must be TRUE or FALSE")
  if(!is.numeric(value)) stop("`value` must be a numeric value")
  if(!is.numeric(real_range)) stop("`real_range` must be a numeric value")
  if(!is.numeric(categorical_fraction)) stop("`categorical_fraction` must be a numeric value")
  if(!is.numeric(factors)) stop("`factors` must be a numeric value")
  if(!is.numeric(integer_fraction)) stop("`integer_fraction` must be a numeric value")
  if(!is.numeric(integer_range)) stop("`integer_range` must be a numeric value")
  if(!is.numeric(binary_fraction)) stop("`binary_fraction` must be a numeric value")
  if(!is.numeric(binary_ones_fraction)) stop("`binary_ones_fraction` must be a numeric value")
  if(!is.numeric(missing_fraction)) stop("`missing_fraction` must be a numeric value")
  if(!is.numeric(response_factors)) stop("`response_factors` must be a numeric value")
  if(!is.logical(has_response)) stop("`has_response` must be a logical value")

  .cframe.map <- c("key" = "dest")
  parms <- lapply(as.list(match.call(expand.dots = FALSE)[-1L]), eval.parent, 2)  # depth must be 2 in order to pop out of the lapply scope...
  if(missing(key) || !is.character(key) || !nzchar(key))
    parms$key = .key.make(prefix = "frame")
  .key.validate(parms$key)
  names(parms) <- lapply(names(parms), function(i) { if( i %in% names(.cframe.map) ) i <- .cframe.map[[i]]; i })

  res <- .h2o.__remoteSend(.h2o.__CREATE_FRAME, method = "POST", .params = parms)

  job_key  <- res$key$name
  dest_key <- res$dest$name
  .h2o.__waitOnJob(job_key)
  h2o.getFrame(dest_key)
}

#' Split an H2O Data Set
#'
#' Split an existing H2O data set according to user-specified ratios.
#'
#' @param data An \linkS4class{Frame} object representing the dataste to split.
#' @param ratios A numeric value or array indicating the ratio of total rows
#'        contained in each split. Must total up to less than 1.
#' @param destination_frames An array of frame IDs equal to the number of ratios
#'        specified plus one.
#' @examples
#' library(h2o)
#' localH2O = h2o.init()
#' irisPath = system.file("extdata", "iris.csv", package = "h2o")
#' iris.hex = h2o.importFile(path = irisPath)
#' iris.split = h2o.splitFrame(iris.hex, ratios = c(0.2, 0.5))
#' head(iris.split[[1]])
#' summary(iris.split[[1]])
#' @export
h2o.splitFrame <- function(data, ratios = 0.75, destination_frames) {
  params <- list()
  params$dataset <- .eval.frame(chk.Frame(data)):eval
  params$ratios <- .collapse(ratios)
  if (!missing(destination_frames))
    params$destination_frames <- .collapse.char(destination_frames)

  res <- .h2o.__remoteSend(method="POST", "SplitFrame", .params = params)
  job_key <- res$key$name
  .h2o.__waitOnJob(job_key)

  splits <- lapply(res$destination_frames, function(s) h2o.getFrame(s$name))
}

#'
#' Filter NA Coluns
#'
#' @param data A dataset to filter on.
#' @param frac The threshold of NAs to allow per column (columns >= this threshold are filtered)
#' @export
h2o.filterNACols <- function(data, frac=0.2) {
  (as.data.frame(.newExpr("filterNACols", data, frac)) + 1)[,1]  # 0 to 1 based index
}

#' Remove Rows With NAs
#'
#' @param object Frame object
#' @param ... Ignored
#' @export
na.omit.Frame <- function(object, ...) .newExpr("na.omit", object)

#' Cross Tabulation and Table Creation in H2O
#'
#' Uses the cross-classifying factors to build a table of counts at each combination of factor levels.
#'
#' @param x An \linkS4class{Frame} object with at most two columns.
#' @param y An \linkS4class{Frame} similar to x, or \code{NULL}.
#' @return Returns a tabulated \linkS4class{Frame} object.
#' @examples
#' library(h2o)
#' localH2O <- h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(localH2O, path = prosPath, destination_frame = "prostate.hex")
#' summary(prostate.hex)
#'
#' # Counts of the ages of all patients
#' head(h2o.table(prostate.hex[,3]))
#' h2o.table(prostate.hex[,3])
#'
#' # Two-way table of ages (rows) and race (cols) of all patients
#' head(h2o.table(prostate.hex[,c(3,4)]))
#' h2o.table(prostate.hex[,c(3,4)])
#' @export
h2o.table <- function(x, y = NULL) {
  chk.Frame(x)
  if( !is.null(y) ) chk.Frame(y)
  if( is.null(y) ) .newExpr("table",x) else .newExpr("table",x,y)
}

#'
#' String Split
#'
#' @param x The column whose strings must be split.
#' @param split The pattern to split on.
#' @export
h2o.strsplit <- function(x, split) { .newExpr("strsplit", x, .quote(split)) }

#'
#' To Lower
#'
#' Mutates the input!
#'
#' @param x A Frame object whose strings should be lower'd
#' @export
h2o.tolower <- function(x) .newExpr("tolower", x)

#'
#' To Upper
#'
#' Mutates the input!
#'
#' @param x A Frame object whose strings should be upper'd
#' @export
h2o.toupper <- function(x) .newExpr("toupper", x)

#'
#' String Substitute
#'
#' Mutates the input. Changes the first occurence of pattern with replacement.
#'
#' @param pattern The pattern to replace.
#' @param replacement The replacement pattern.
#' @param x The column on which to operate.
#' @param ignore.case Case sensitive or not
#' @export
h2o.sub <- function(pattern,replacement,x,ignore.case=FALSE) .newExpr("sub", .quote(pattern), .quote(replacement),x,ignore.case)

#'
#' String Global Substitute
#'
#' Mutates the input. Changes the all occurences of pattern with replacement.
#'
#' @param pattern The pattern to replace.
#' @param replacement The replacement pattern.
#' @param x The column on which to operate.
#' @param ignore.case Case sensitive or not
#' @export
h2o.gsub <- function(pattern,replacement,x,ignore.case=FALSE) .newExpr("gsub", .quote(pattern), .quote(replacement),x,ignore.case)

#'
#' Trim Space
#'
#' @param x The column whose strings should be trimmed.
#' @export
h2o.trim <- function(x) .newExpr("trim", x)
