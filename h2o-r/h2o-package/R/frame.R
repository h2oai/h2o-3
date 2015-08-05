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
#` E$eval     <- A (possibly empty) list of dependent Nodes.  Set to an ID string for evaluated ops
#` # If the ID field is present, this Node is user  -managed, and will NOT be deleted by GC.
#` # If the ID field is missing, this Node is client-managed, and will     be deleted by GC.
#` E$id       <- A user-specified name, used in the H2O cluster
#` E$visit    <- A temporary field used to manage DAG visitation
#` 
#` # A number of fields represent cached queries of an evaluated frame.
#` E$data <- A cached result; can be a scalar, or a R dataframe result holding
#`           the first N (typically 10) rows and all cols of the frame
#` E$nrow   <- the row count (total size, generally much larger than the local cached rows)


is.Frame <- function(fr) class(fr)[1]=="Frame"
#.isFr <- function(fr) is(fr,"Frame")

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
           if( is.character(x) ) paste0('"',x,'"') else x,
           if( is.character(y) ) paste0('"',y,'"') else y)

Math.Frame <- function(x) .newExpr(.Generic,x)

Math.Frame <- function(x,y) .newExpr(.Generic,x,y)

Math.Frame <- function(x,...) .newExprList(.Generic,list(x,...))

Summary.Frame <- function(x,...,na.rm) {
  if( na.rm ) stop("na.rm versions not impl") 
  res <- .newExprList(.Generic,list(x,...))
  # Eager evaluation, to produce a scalar
  res <- .fetch.data(res,1)
  if( .Generic=="all" ) res <- as.logical(res)
  stopifnot( !is.data.frame(res) )
  res
}


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
pfr <- function(x) { stopifnot(is.Frame(x)); e<-new.env(); .set(e,"cnt",0); print(.pfr(x),e); .clearvisit(x); invisible() }

.eval.impl <- function(x, toplevel) {
  if( is.character(xchild<-x:eval) ) return( if(is.data.frame(x:data) || is.null(x:data) ) xchild else x:data )
  res <- paste(sapply(xchild, function(child) { if( is.Frame(child) ) .eval.impl(child,F) else child }),collapse=" ")
  res <- paste0("(",x:op," ",res,")")
  .set(x,"eval", xchild <- .key.make("RTMP")) # Flag as code-emitted
  if( .shared(x) && !toplevel) 
    res <- paste0("(tmp= ",xchild," ",res,")")
  res
}

# Evaluate this Frame on demand.  The eval field is used as a flag to
# signal that the node has already been executed.  
.eval.frame <- function(x) {
  stopifnot(is.Frame(x))
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
#' @param x An \linkS4class{H2OFrame} object.
#' @seealso \code{\link[base]{dim}} for the base R method.
#' @examples
#' localH2O <- h2o.init()
#' iris.hex <- as.h2o(iris)
#' dim(iris.hex)
#' @export
dim.Frame <- function(x) { data <- .fetch.data(x,1); unlist(list(x:nrow,ncol(data))) }

#` Column names of an H2O Frame
dimnames.Frame <- function(x) .Primitive("dimnames")(.fetch.data(x,1))

#'
#' Return the Head or Tail of an H2O Dataset.
#'
#' Returns the first or last rows of an H2O parsed data object.
#'
#' @param x An \linkS4class{H2OFrame} object.
#' @param n (Optional) A single integer. If positive, number of rows in x to return. If negative, all but the n first/last number of rows in x.
#' @param ... Further arguments passed to or from other methods.
#' @return A data frame containing the first or last n rows of an \linkS4class{H2OFrame} object.
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
  if( n >= 0L && n <= 1000L ) { # Short version, just report the cached internal DF
    head(.fetch.data(x,n),n)
  } else # Long version, fetch all asked for "the hard way"
    x[seq_len(n),]
}

#' @rdname h2o.head
#' @export
tail.Frame <- function(x,n=6L) { 
  endidx <- nrow(x)
  n <- ifelse(n < 0L, max(endidx + n, 0L), min(n, endidx))
  if( n==0L ) head(x,n=0L)
  else {
    startidx <- max(1L, endidx - n + 1)
    x[startidx:endidx,]
  }
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
  data <- .fetch.data(x,10L)
  if( !is.data.frame(data) ) return(as.character(data))
  nr <- nrow(x)
  nc <- ncol(x)
  res <- paste0("Frame with ",
      nr, ifelse(nr == 1L, " row and ", " rows and "),
      nc, ifelse(nc == 1L, " column\n", " columns\n"), collapse="")
  if( nr > 10L ) res <- paste0(res,"\nFirst 10 rows:\n")
  paste0(res,paste0(head(data, 10L),collapse="\n"),"\n")
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


# Convert a row or column selector to zero-based numbering and return a string
.row.col.selector <- function( sel ) {
  # number list for column selection; zero based
  sel2 <- lapply(sel,function(x) if( x==0 ) stop("Cannot select row or column 0") else if( x > 0 ) x-1 else x)
  ifelse( is.numeric(sel), paste0('[',paste0(sel2,collapse=" "),']'), as.character(sel) )
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
      col <- match(col,colnames(data))-1 # Match on name, then zero-based
    } else { # Generic R expression
      col <- .row.col.selector(col)
    }
    data <- .newExpr("cols",data,col) # Column selector
  }
  # Have a row selector?
  if( !missing(row) ) {
    if( !is.Frame(row) )   # Generic R expression
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
  if(allRow) rows <- paste0("[0:",nrow(data),"]")
  else       rows <- .row.col.selector(row)

  name <- NA
  if( allCol ) {   # Col arg is missing, means "all the cols"
    cols <- paste0("[0:",ncol(data),"]")
  } else {
    idx <- match(col, colnames(data))
    if( any(is.na(idx)) ) {
      idx <- if( is.numeric(col) ) col else ncol(data)+1
      if( !is.numeric(col) ) name <- col
    } else name <- col
    cols <- .row.col.selector(idx)
  }

  if( !is.Frame(value) && is.na(value) ) value <- "%NA"
  res <- .newExpr("=", data, value, cols, rows)
  # Set col name and return updated frame
  if( !is.na(name) )  res <- .newExpr("colnames=", res, idx-1, paste0('"',name,'"'))
  res
}

#' @rdname Frame-Extract
#' @export
`$<-.Frame` <- function(data, name, value) {
  `[<-.Frame`(data,row=name,value=value) # col is missing on purpose
}

#' @rdname Frame-Extract
#' @export
`[[<-.Frame` <- function(data,name,value) {
  if( !is.Frame(value )) stop("Can only append a Frame to a Frame")
  `[<-`(data, row=name,value=value)
}

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
  lapply(li, function(l) if( !is.Frame(l) ) stop("`h2o.cbind` accepts only of Frame objects"))
  .newExprList("cbind",li)
}

#' Produe a Vector of Random Uniform Numbers
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
