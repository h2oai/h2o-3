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
#` E$nuked    <- This REFCNT'd Node has been deleted from H2O
#` E$visit    <- A temporary field used to manage DAG visitation
#` 
#` # A number of fields represent cached queries of an evaluated frame
#` E$data   <- an R dataframe holding the first N (typically 10) rows and all cols of the frame
#` E$nrow   <- the row count (total size, generally much larger than the local cached rows)

is.Frame <- function(x) is(x, "Frame")

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
  # If the NEW value is about to be a Frame, up the ref-cnt
  .refup(y)

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
.newFrame <- function(op,id,...) {
  assign("node", structure(new.env(parent = emptyenv()), class="Frame"))
  assign("op",op,node)
  assign("id",id,node)
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
Ops.Frame <- function(x,y) {
  assign("node", structure(new.env(parent = emptyenv()), class="Frame"))
  assign("op",.Generic,node)
  assign("refcnt",0L,node)
  assign("children", lapply(list(x,y), .refup), node)
  reg.finalizer(node, .nodeFinalizer, onexit = TRUE)
  node
}


# Pick a name for this Node.  Just use the evironment's C pointer address, if
# one's not provided
.id <- function(x) {
  if( is.null(x$id ) ) {
    str <- capture.output(str(x))
    paste0("RTMP",substring(str,nchar(str)-19,nchar(str)-2))
  } else x$id
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
.pfr <- function(x){
  if( !is.null(x$visit) || is.null(x$refcnt) ) return(.id(x))
  x$visit <- TRUE
  tmp1 <- if( x$refcnt > 1 ) paste0("(tmp= ",.id(x))
  tmp2 <- if( x$refcnt > 1 ) paste0(")")
  res <- ifelse( is.null(x$children), "EVALd",
                 paste(sapply(x$children, function(child) { if( is.environment(child) ) .pfr(child) else child }),collapse=" "))
  paste0(tmp1,"(",x$op," ",res,")",tmp2)
}

# Pretty print the reachable execution DAG from this Frame, withOUT evaluating it
pfr <- function(x) { stopifnot(is.Frame(x)); print(.pfr(x)); .clearvisit(x); invisible() }

# Evaluate this Frame on demand
.eval.frame <- function(x) {
  stopifnot(is.Frame(x))
  if( !is.null(x$children) ) {
    exec_str <- .pfr(x);  .clearvisit(x)
    print(paste0("EXPR: ",exec_str))
    # Execute the AST on H2O
    res <- .h2o.__remoteSend(.h2o.__RAPIDS, h2oRestApiVersion = 99, ast=exec_str, id=.id(x), method = "POST")
    if( !is.null(res$error) ) stop(paste0("Error From H2O: ", res$error), call.=FALSE)
    # Flag as executed
    rm("children",envir=x)
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

#' Print An H2O Frame
#'
#' @param x An H2O Frame object
#' @export
print.Frame <- function(x) {
  nr <- nrow(x)
  nc <- ncol(x)
  cat("Frame with ",
      nr, ifelse(!is.na(nr) && nr == 1L, " row and ", " rows and "),
      nc, ifelse(!is.na(nc) && nc == 1L, " column\n", " columns\n"), sep = "")
  if( nr > 10L ) cat("\nFirst 10 rows:\n")
  print(head(.fetch.data(x,10L), 10L))
  invisible(x)
}

#' Display the structure of an H2O Frame object
#'
#' @param An H2O Frame object
#' @param cols Print the per-column str for the Frame
#' @export
Qstr.Frame <- function(x, cols=FALSE, ...) {
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

.h2o.gc <- function() {
  print("H2O triggered a GC in R")
  gc()
}

