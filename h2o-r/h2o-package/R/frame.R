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
#` nodes contain an operator and some outgoing edges.  There's a refcnt, to
#` count incoming edges and to control lifetimes.  Refcnts are raised when a
#` pointer is made to a Frame via using it in an expression.  Refcnts are
#` lowered when a Frame is overwritten via assignment, or GC reclaims it.  When
#` the refcnt falls to zero, the backing temp on H2O can be removed.
#`
#` Assignment is overloaded, to catch capturing of Frame pointers (causes a
#` refcnt up) or destruction (overwrite) of Frame pointers (causes a refcnt
#` down).  Top-level expressions can be made in the R REPL without assigning to
#` any variable; these expressions exist with a refcnt of 0.  The next GC will
#` reclaim them.  GC will also visit expressions that went dead previously,
#` there is a 'nuked' flag to prevent double-deletion.
#`
#` Frame/AST Node/environment Fields
#` E$op       <- Operation or opcode that produces this Frame, a string
#` E$children <- A (possibly empty) list of dependent Nodes.  Removed for evaluated ops
#` # Only one of the next two fields is present:
#` # If the ID field is present the refcnt field is missing.  This Node is
#` # user-managed, and will NOT be deleted by GC or refcnting.
#` E$id       <- A user-specified name, used in the H2O cluster; the refcnt field is missing
#` # If the REFCNT field is present, then the ID field is missing.  This Node
#` # is client-managed, and will be deleted by GC or refcnt falling to zero.
#` E$refcnt   <- A count of outstanding references; when it falls to zero the item is deleted.  The ID field is missing
#` E$internal_id <- internal name, not user managed, not user visible
#` E$nuked    <- This REFCNT'd Node has been deleted from H2O
#` E$visit    <- A temporary field used to manage DAG visitation
#` 
#` # A number of fields represent cached queries of an evaluated frame
#` E$data   <- an R dataframe holding the first N (typically 10) rows and all cols of the frame
#` E$nrow   <- the row count (total size, generally much larger than the local cached rows)


is.Frame <- function(fr) class(fr)[1]=="Frame"
#.isFr <- function(fr) is(fr,"Frame")

# GC Finalizer - called when GC collects a Frame
# Must be defined ahead of constructors
.nodeFinalizer <- function(x) { .refdown(x,"GC_finalizer"); }

# Ref-count up-count, only if a Frame.  Return x, for flow coding
.refup <- function(x) {
  if( is.Frame(x) && is.null(x$id) ) assign("refcnt",x$refcnt + 1,envir=x)
  x
}

# Ref-count down-count.  If it goes to zero, recursively ref-down-count the
# children, plus also remove the backing H2O store
.refdown <- function(x,xsub) {
  if( !is.Frame(x) ) return()
  if( is.null(x$refcnt) ) return(); # Named, no refcnt, no GC
  # Ok to be here once from GC, and once from killing last link calling
  # .refdown - hence might be zero but never negative
  stopifnot(x$refcnt >= 0 )
  if( x$refcnt > 0 ) assign("refcnt",x$refcnt - 1,envir=x)
  if( x$refcnt == 0 && is.null(x$nuked) ) {
    lapply(x$children, function(child) .refdown(child,paste0(xsub,"child")) )
    .h2o.__remoteSend(paste0(.h2o.__DKV, "/", .id(x)), method = "DELETE")
    assign("nuked",TRUE,envir=x)
  }
}

#
# Overload Assignment!
#
assign("<-", NULL )
assign("<-", function(x,y) {
  force(y)  # Do this eagerly, so it happens before we down refcnt on LHS
  # If the NEW value is about to be a Frame, up the ref-cnt
  if( is.Frame(y) ) .refup(y)
  # Get a symbol for 'x'
  assign("xsub",substitute(x))
  # Evaluate complex LHS arguments, attempting to get an OLD value
  assign("e", try(silent=TRUE,
      if (is.symbol(xsub))
          get(as.character(xsub), parent.frame(), inherits=FALSE)  # eval doesn't let us do inherits=FALSE for symbols
      else
          eval(xsub,parent.frame())
  ))
  # If the OLD value was a Frame, down the ref-cnt
  if( is.Frame(e) ) .refdown(e,xsub);

  # Dispatch to various assignment techniques
  if( is.symbol(xsub) || (is.character(xsub) && length(xsub)==1) )
    assign(as.character(xsub), y, envir=parent.frame())
  else if (xsub[[1]]=="$") {
    assign("lhs", eval(xsub[[2]], parent.frame(), parent.frame()))
    if( typeof(lhs)=="list" )
      `=`(lhs[[as.character(xsub[[3]])]],y)
    else 
      assign(as.character(xsub[[3]]), y, envir=eval(xsub[[2]], parent.frame(), parent.frame()))
  } else
    eval(as.call(list(.Primitive("<-"),xsub,y)), parent.frame())

  #if (is.symbol(xsub))
  #  assign(as.character(xsub), y, envir=parent.frame())
  #else
  #  eval(as.call(list(.Primitive("<-"),xsub,y)), parent.frame())

  invisible(y)
})

# Make a raw named data frame.  The key will exist on the server, and will be
# the passed-in ID.  Because it is named, it is not GCd.  It is fully evaluated.
.newFrame <- function(op,id) {
  stopifnot( is.character(id) )
  assign("node", structure(new.env(parent = emptyenv()), class="Frame"))
  assign("op",op,node)
  assign("id",id,node)
  node
}

# A new lazy expression
.newExpr <- function(op,...) {
  assign("node", structure(new.env(parent = emptyenv()), class="Frame"))
  assign("op",op,node)
  assign("refcnt",0L,node)
  assign("internal_id",.key.make("Op"),node)
  assign("children", lapply(list(...), function(x) if( is.Frame(x)) .refup(x) else x), node)
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
Ops.Frame <- function(x,y) .newExpr(.Generic,x,y)

Math.Frame <- function(x) .newExpr(.Generic,x)

# Pick a name for this Node.  Just use the evironment's C pointer address, if
# one's not provided
.id <- function(x) {
  if( is.null(x$id ) ) x$internal_id  
  else                 x$id
}

# Internal recursive clear-visit-flag function, goes hand-n-hand with a
# recursive visitor
.clearvisit <- function(x) {
  if( is.null(x$visit) ) return()
  rm("visit",envir=x);
  if( !is.null(x$children))
    lapply(x$children, function(child) { if( is.environment(child) ) .clearvisit(child) } )
}

# Internal recursive printer
.pfr <- function(x) {
  if( !is.null(x$visit) || is.null(x$children) ) return(.id(x))
  x$visit <- TRUE
  if( !is.null(x$refcnt) && x$refcnt > 1 ) { tmp1 <- paste0("(tmp= ",.id(x)," "); tmp2 <- ")" }
  else { tmp1 <- tmp2 <- "" }
  res <- ifelse( is.null(x$children), "EVALd",
                 paste(sapply(x$children, function(child) { if( is.Frame(child) ) .pfr(child) else child }),collapse=" "))
  paste0(tmp1,"(",x$op," ",res,")",tmp2)
}

# Pretty print the reachable execution DAG from this Frame, withOUT evaluating it
pfr <- function(x) { stopifnot(is.Frame(x)); print(.pfr(x)); .clearvisit(x); invisible() }

.eval.impl <- function(x) {
  if( is.null(x$children) ) return(.id(x))
  res <- paste(sapply(x$children, function(child) { if( is.Frame(child) ) .eval.impl(child) else child }),collapse=" ")
  rm("children",envir=x)     # Flag as executed
  res <- paste0("(",x$op," ",res,")")
  if( !is.null(x$refcnt) && x$refcnt > 1 )
    res <- paste0("(tmp= ",.id(x)," ",res,")")
  res
}

# Evaluate this Frame on demand.  The children field is used as a flag to
# signal that the node has already been executed.  
.eval.frame <- function(x) {
  stopifnot(is.Frame(x))
  if( !is.null(x$children) ) {
    exec_str <- .eval.impl(x)
    print(paste0("EXPR: ",exec_str))
    # Execute the AST on H2O
    res <- .h2o.__remoteSend(.h2o.__RAPIDS, h2oRestApiVersion = 99, ast=exec_str, id=.id(x), method = "POST")
    if( !is.null(res$error) ) stop(paste0("Error From H2O: ", res$error), call.=FALSE)
  }
  x
}

#' Returns the Dimensions of an H2O Frame
#'
#' Returns the number of rows and columns for a Frame object.
#'
#' @param x An \linkS4class{H2OFrame} object.
#' @seealso \code{\link[base]{dim}} for the base R method.
#' @examples
#' localH2O <- h2o.init()
#' iris.hex <- as.h2o(iris)
#' dim(iris.hex)
#' @export
dim.Frame <- function(x) { data <- .fetch.data(x,1); unlist(list(x$nrow,ncol(data))) }

#` Column names of an H2O Frame
dimnames.Frame <- function(x) .Primitive("dimnames")(.fetch.data(x,1))

#` First N rows of Frame
head.Frame <- function(x,n=6L) { 
  stopifnot(length(n) == 1L)
  n <- if (n < 0L) max(nrow(x) + n, 0L)
       else        min(n, nrow(x))
  if( n > 0L && n <= 1000L ) { # Short version, just report the cached internal DF
    head(.fetch.data(x,n),n)
  } else # Long version, fetch all asked for "the hard way"
    x[seq_len(n),]
}

#' Print An H2O Frame
#'
#' @param x An H2O Frame object
#' @export
print.Frame <- function(x) { cat(as.character(x)); invisible(x) }

#' Convert an H2O Frame to a String
#'
#' @param x An H2O Frame object
#' @export
as.character.Frame <- function(x) {
  nr <- nrow(x)
  nc <- ncol(x)
  res <- paste0("Frame with ",
      nr, ifelse(nr == 1L, " row and ", " rows and "),
      nc, ifelse(nc == 1L, " column\n", " columns\n"), collapse="")
  if( nr > 10L ) res <- paste0(res,"\nFirst 10 rows:\n")
  paste0(res,paste0(head(.fetch.data(x,10L), 10L),collapse="\n"),"\n")
}


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
  cat("\nFrame '", .id(x), "':\t", nr, " obs. of  ", nc, " variable(s)", "\n", sep = "")
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


# Convert a row or column selector to zero-based numbering and return a string
.row.col.selector <- function( sel ) {
  # number list for column selection; zero based
  ifelse( is.numeric(sel), paste0('[',paste0(lapply(sel,function(x) x-1),collapse=" "),']'), as.character(sel) )
}


#` Overload dataframe slice; build a lazy eval slice
`[.Frame` <- function(data,row,col) {
  stopifnot( is.Frame(data) )
  # Have a column selector?
  if( !missing(col) ) {
    if( is.logical(col) ) { # Columns by boolean choice
      print(col)
      stop("unimplemented1")
    } else if( is.character(col) ) { # Columns by name
      print(col)
      stop("unimplemented2")
    } else { # Generic R expression
      col <- .row.col.selector(col)
    }
    data <- .newExpr("cols",data,col) # Column selector
  }
  # Have a row selector?
  if( !missing(row) ) {
    if( is.Frame(row) ) { # Rows by boolean choice
      print(row)
      stop("unimplemented3")
    } else { # Generic R expression
      row <- .row.col.selector(row)
    }
    data <- .newExpr("rows",data,row) # Row selector
  }
  data
}


#-----------------------------------------------------------------------------------------------------------------------
# Assignment Operations: [<-, $<-, [[<-, colnames<-, names<-
#-----------------------------------------------------------------------------------------------------------------------
#` Overload dataframe slice assignment; build a lazy eval slice
`[<-.Frame` <- function(data,row,col,...,value) {
  stopifnot( is.Frame(data) )
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
  if( !is.Frame(value) && is.na(value) ) value <- NA_integer_  # pick an NA... any NA (the damned numeric one will do)
  if( !is.Frame(value) && !is.numeric(value) && !is.character(value))
    stop("`value` can only be an Frame object or a numeric or character vector")

  # Row arg is missing, means "all the rows"
  if(allRow) row <- nrow(data)
  rows <- .row.col.selector(row)

  name <- NA
  if( allCol ) {   # Col arg is missing, means "all the cols"
    idx <- ncol(data)
  } else {
    idx <- match(col, colnames(data))
    if( any(is.na(idx)) ) {
      if( is.numeric(col) ) {
        idx <- col
      } else {
        name <- col
        idx <- ncol(data)+1
      }
    }
  }
  cols <- .row.col.selector(idx)

  value <- if( is.Frame(value) )   value
           else if( is.na(value) ) "%NA"
           else                    force(value)
  res <- .newExpr("=", data, value, cols, rows)
  # Set col name and return updated frame
  if( !is.na(name) ) res <- .setColName(res,idx,name)
  res
}

# Set a column name.
.setColName <- function(fr, idx, name) .newExpr("colnames=", fr, idx-1, name)

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
    x <- as.data.frame(x)
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
                '?frame_id=', URLencode(.id(x)),
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
as.matrix.H2OFrame <- function(x, ...) as.matrix(as.data.frame(x, ...))

#' Produe a Vector of Random Uniform Numbers
#'
#' Creates a vector of random uniform numbers equal in length to the length of the specified H2O
#' dataset.
#'
#' @param x An \linkS4class{H2OFrame} object.
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
  stopifnot( is.Frame(x) )
  if (!is.numeric(seed) || length(seed) != 1L || !is.finite(seed)) stop("`seed` must be an integer >= 0")
  if (seed == -1) seed <- floor(runif(1,1,.Machine$integer.max*100))
  .newExpr("h2o.runif", x, seed)
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
  if( typeof(FUN) == "builtin" ) {
    fun.ast <- as.character(substitute(FUN))
    if( fname %in% .h2o.primitives ) return(.newExpr("apply",X,MARGIN,fun.ast))
    stop(paste0("Function '",fun,"' not in .h2o.primitives list and not an anonymous function, unable to convert it to Currents"))
  }
  # Explode anonymous function into a Currents AST
  .newExpr("apply",X,MARGIN,.fun.to.ast(FUN))
}
