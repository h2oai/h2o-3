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
#` AST Node/Environment Fields
#` E$op       <- Operation or opcode, a string
#` E$children <- A (possibly empty) list of dependent Nodes
#` # Only one of the next two fields is present:
#` # If the ID field is present, this Node is user-managed, and will NOT be
#` # deleted by GC or refcnting.  The refcnt field is missing.
#` E$id       <- A user-specified name, used in the H2O cluster; the refcnt field is missing
#` # If the REFCNT field is present, this Node is client-managed, and will be
#` # deleted by GC or refcnt falling to zero.  The ID field is missing.
#` E$refcnt   <- A count of outstanding references; when it falls to zero the item is deleted.  The ID field is missing
#` E$nuked    <- This REFCNT'd Node has been deleted from H2O
#` E$visit    <- A temporary field used to manage DAG visitation

# GC Finalizer - called when GC collects a Frame
# Must be defined ahead of constructors
.nodeFinalizer <- function(x) { .refdown(x,"GC_finalizer"); }

# Ref-count up-count, only if a Frame.  Return x, for flow coding
.refup <- function(x) {
  if( class(x)[1] == "Frame" && is.null(x$id) ) assign("refcnt",x$refcnt + 1,envir=x)
  x
}

# Ref-count down-count.  If it goes to zero, recursively ref-down-count the
# children, plus also remove the backing H2O store
.refdown <- function(x,xsub) {
  if( !is.null(x$id) ) return(); # Named, no refcnt, no GC
  # Ok to be here once from GC, and once from killing last link calling
  # .refdown - hence might be zero but never negative
  stopifnot(x$refcnt >= 0 )
  if( x$refcnt > 0 ) assign("refcnt",x$refcnt - 1,envir=x)
  if( x$refcnt == 0 && is.null(x$nuked) ) {
    lapply(x$children, function(child) { if( is.environment(child) ) .refdown(child,paste0(xsub,"child")) })
    print(paste("h2o.rm(",xsub,")"))
    x$nuked <- TRUE
  }
}

# Pick a name for this Node.  Just use the evironment's C pointer address, if
# one's not provided
.id <- function(x) {
  if( is.null(x$id ) ) {
    str <- capture.output(str(x))
    substring(str,nchar(str)-19,nchar(str)-2)
  } else x$id
}

# Internal recursive printer
.pfr <- function(x){
  if( !is.null(x$visit) ) return(.id(x))
  x$visit <- TRUE
  str <- if( x$refcnt > 1 ) paste0(.id(x),"<- ")
  res <- paste(sapply(x$children, function(child) { if( is.environment(child) ) .pfr(child) else child }),collapse=" ")
  paste0(str,"(",x$op," ",res," #",x$refcnt,")")
}

# Internal recursive clear-visit-flag function, goes hand-n-hand with a
# recursive visitor
.clearvisit <- function(x) {
  if( is.null(x$visit) ) return()
  rm("visit",envir=x);
  lapply(x$children, function(child) { if( is.environment(child) ) .clearvisit(child) } )
}

#` S3 overload print
#` Pretty print the reachable execution DAG from this Frame
"print.Frame" <- function(x) { print(.pfr(x)); .clearvisit(x); invisible() }

#'
#' Describe an Frame object
#'
#' @param object An Frame object.
#' @param cols Logical indicating whether or not to do the str for all columns.
#' @param \dots Extra args
#' @export
#str.Frame <- function(object, cols=FALSE, ...) {
#  if (length(l <- list(...)) && any("give.length" == names(l)))
#    invisible(NextMethod("str", ...))
#  else if( !cols ) invisible(NextMethod("str", give.length = FALSE, ...))
#
#  if( cols ) {
#    nc <- ncol(object)
#    nr <- nrow(object)
#    cc <- colnames(object)
#    width <- max(nchar(cc))
#    df <- as.data.frame(object[1L:10L,])
#    isfactor <- as.data.frame(is.factor(object))[,1]
#    num.levels <- as.data.frame(h2o.nlevels(object))[,1]
#    lvls <- as.data.frame(h2o.levels(object))
#    # header statement
#    cat("\nH2OFrame '", object@id, "':\t", nr, " obs. of  ", nc, " variable(s)", "\n", sep = "")
#    l <- list()
#    for( i in 1:nc ) {
#      cat("$ ", cc[i], rep(' ', width - max(na.omit(c(0,nchar(cc[i]))))), ": ", sep="")
#      first.10.rows <- df[,i]
#      if( isfactor[i] ) {
#        nl <- num.levels[i]
#        lvls.print <- lvls[1L:min(nl,2L),i]
#        cat("Factor w/ ", nl, " level(s) ", paste(lvls.print, collapse='","'), "\",..: ", sep="")
#        cat(paste(match(first.10.rows, lvls[,i]), collapse=" "), " ...\n", sep="")
#      } else
#        cat("num ", paste(first.10.rows, collapse=' '), if( nr > 10L ) " ...", "\n", sep="")
#    }
#  }
#}

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
  if( class(e) == "Fr" ) .refdown(e,xsub);
  # If the NEW value is about to be a Frame, up the ref-cnt
  .refup(y)
  # Dispatch to various assignment techniques
  if( is.symbol(xsub) || (is.character(xsub) && length(xsub)==1) )
    assign(as.character(xsub), y, envir=parent.frame())
  else if (xsub[[1]]=="$")
    assign(as.character(xsub[[3]]), y, envir=eval(xsub[[2]], parent.frame(), parent.frame()))
  else
    stop("NYI xsub = ", deparse(xsub))
  invisible(y)
})


# Make a raw named data frame.  The key will exist on the server, and will be
# the passed-in ID.  Because it is named, it is no GCd
.newFrame <- function(op,id) {
  assign("node", structure(new.env(parent = emptyenv()), class="Frame"))
  node$op <- op
  node$id <- id
  node$children <- list()
  node
}


# S3 Overload all standard operators.
# Just build a lazy-eval structure.
Ops.Frame <- function(x,y) {
  assign("node", structure(new.env(parent = emptyenv()), class="Frame"))
  node$op <- .Generic
  node$refcnt <- 0L
  node$children <- lapply(list(x,y), .refup)
  reg.finalizer(node, .nodeFinalizer, onexit = TRUE)
  node
}


#-----------------------------------------------------------------------------------------------------------------------
# Casting Operations: as.data.frame, as.factor,
#-----------------------------------------------------------------------------------------------------------------------

#'
#' R data.frame -> H2OFrame
#'
#' Import a local R data frame to the H2O cloud.
#'
#' @param object An \code{R} data frame.
#' @param destination_frame A string with the desired name for the H2O Frame.
#' @export
as.h2o <- function(object, destination_frame= "") {
  .key.validate(destination_frame)

  # TODO: Be careful, there might be a limit on how long a vector you can define in console
  if(!is.data.frame(object)) {
    object <- as.data.frame(object)
  }
  types <- sapply(object, class)
  types <- gsub("integer", "numeric", types)
  types <- gsub("double", "numeric", types)
  types <- gsub("complex", "numeric", types)
  types <- gsub("logical", "enum", types)
  types <- gsub("factor", "enum", types)
  types <- gsub("character", "string", types)
  types <- gsub("Date", "Time", types)
  tmpf <- tempfile(fileext = ".csv")
  write.csv(object, file = tmpf, row.names = FALSE, na="NA_h2o")
  h2f <- h2o.uploadFile(tmpf, destination_frame = destination_frame, header = TRUE, col.types=types,
                        col.names=colnames(object, do.NULL=FALSE, prefix="C"), na.strings=rep(c("NA_h2o"),ncol(object)))
  file.remove(tmpf)
  h2f
}

.h2o.gc <- function() {
  print("H2O triggered a GC in R")
  gc()
}
