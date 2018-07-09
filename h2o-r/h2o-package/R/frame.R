#`
#` H2OFrame and AST Nodes
#`
#` To conveniently and safely pass messages between R and H2O, this package
#` relies on S3 objects to capture and pass state.  The end user will typically
#` never have to reason with these objects directly, as there are S3 accessor
#` methods provided for creating new objects.
#`
#` S3 H2OFrame class objects are pointers to either data in an H2O cluster, or
#` potential data (future calculations) in the cluster.  They are also classic
#` compiler AST Nodes (to hold future calculations).  They are implemented with
#` simple R environment objects.
#`
#` Like AST Nodes in compilers all over, Frames build a simple DAG where the
#` nodes contain an operator and some outgoing edges.  There is a GC finalizer
#` to delete the server-side copy of an H2OFrame
#`
#`
#` === H2OFrame/AST Node/environment Fields ===
#
#` E$op     <- Operation or opcode that produces this H2OFrame, a string
#
#` The combination of EVAL and ID fields determines the evaluation state:
#` EVAL is one of:
#` - TRUE : Node is evaluated, cluster has the ID, and an R GC finalizer will remove this temp ID
#` - FALSE: Node is evaluated, cluster has the ID, and the user has to explictly remove this permanent ID
#` - list of Nodes: Then further ID is one of:
#` - - missing: this Node is lazy and has never been evaluated
#` - - NA: this Node has been executed once, but no temp ID was made
#` - - String: this Node is mid-execution, with the given temp ID.  Once execution has completed the EVAL field will be set to TRUE
#`
#` # A number of fields represent cached queries of an evaluated frame.
#` E$data   <- A cached result; can be a scalar, or a R dataframe result holding
#`             the first N (typically 10) rows and all cols of the frame
#` E$nrow   <- the row count (total size, generally much larger than the local cached rows)
#` E$types  <- the H2O column types

#-----------------------------------------------------------------------------------------------------------------------
# Private/Internal Functions
#-----------------------------------------------------------------------------------------------------------------------
is.H2OFrame <- function(fr)  base::`&&`(!missing(fr), class(fr)[1]=="H2OFrame") 
chk.H2OFrame <- function(fr) if( is.H2OFrame(fr) ) fr else stop("must be an H2OFrame")
# Horrible internal shortcut to set our fields, using a more "normal"
# parameter order
.set <- function(x,name,value) attr(x,name) <- value

# GC Finalizer - called when GC collects an H2OFrame Must be defined ahead of constructors.
.nodeFinalizer <- function(x) {
  eval <- attr(x, "eval")
  if( is.logical(eval) && eval ) {
    #cat("=== Finalizer on ",attr(x, "id"),"\n")
    .h2o.__remoteSend(.h2o.__RAPIDS, h2oRestApiVersion = 99, ast=paste0("(rm ",attr(x, "id"),")"), session_id=h2o.getConnection()@mutable$session_id, method = "POST")
  }
}

# Make a raw named data frame.  The key will exist on the server, and will be
# the passed-in ID.  Because it is named, it is not GCd.  It is fully evaluated.
.newH2OFrame <- function(op,id,nrow,ncol) {
  stopifnot( base::is.character(id) )
  node <- structure(new.env(parent = emptyenv()), class="H2OFrame")
  .set(node,"op",op)
  .set(node,"id",id)
  .set(node,"eval",FALSE) # User-managed lifetime
  .set(node,"nrow",nrow)
  .set(node,"ncol",ncol)
  node
}

# A new lazy expression
.newExpr <- function(op,...) .newExprList(op,list(...))

.newExprList <- function(op,li) {
  node <- structure(new.env(parent = emptyenv()), class="H2OFrame")
  .set(node,"op",op)
  .set(node,"eval",li)
  reg.finalizer(node, .nodeFinalizer, onexit=TRUE)
  node
}

#
# Overload Assignment!
#
# Trying to remove excessive temp generation, by having the R interpreter tell
# H2O that some computation may be used, or not.  If the expression is only
# ever used once, then no temp is needed and the cluster can optimize the
# lifetime.  If the temp *may* be used again, the cluster needs a temp for
# the reuse, or else the computation needs to be "pure" and re-executed.
#
# After many many attempts, I think it's not reasonably possible to track
# lifetimes in R via assignment overload.  There are too many other paths
# that extend lifetimes that all must be caught (including, but not limited
# to: c, list, <-, =, and the *apply series)

# Internal recursive printer
.pfr <- function(x) {
  if( is.list(res<- attr(x,"eval")) )
    res <- paste0("(",attr(x, "op")," ",paste(sapply( attr(x,"eval"), function(child) { if( is.H2OFrame(child) ) .pfr(child) else child }),collapse=" "),")")
  paste0( attr(x, "id"), ":=", res)
}

# Pretty print the reachable execution DAG from this H2OFrame, withOUT evaluating it
pfr <- function(x) { chk.H2OFrame(x); .pfr(x) }

# Recursively build a rapids execution string; assign the "id" field to count
# executions; flip to using a temp on the 2nd execution.
#
# This call "counts"!!!
# On the 2nd .eval.impl call to any H2OFrame object, the object will be cached as
# a temp until the next R GC cycle - consuming memory.  Do Not Call This except
# when you need to do some other cluster operation on the evaluated object.
# Examples might be: lazy dataset time parse vs changing the global timezone.
# Global timezone change is eager, so the time parse as to occur in the correct
# order relative to the timezone change, so cannot be lazy.
.eval.impl <- function(x) {
  dat <- attr(x, "data")
  id  <- attr(x, "id")
  if( !is.null(dat) ) return( if( is.data.frame(dat) ) id else dat ) # Data already computed and cached
  if( !is.null( id) && !is.na(id) ) return( id ) # Data already computed under ID, but not cached
  # Build the eval expression
  eval<- attr(x, "eval")
  stopifnot(is.list(eval))
  op  <- attr(x, "op")
  res <- paste(sapply( eval, function(child) {
    if(      is.H2OFrame    (child) )                      .eval.impl(child)  # recurse
    else if( is.numeric  (child) && length(child) > 1L ) .num.list(child)  # [ numberz ]  TODO: sup with those NaNs tho
    else if( base::is.character(child) && length(child) > 1L ) .str.list(child)  # [ stringz ]
    else                                                           child   # base; e.g. raw single numbers or strings
  }),collapse=" ")
  res <- paste0("(",op," ",res,")")
  # First exec: ID is missing, convert to NA
  # 2nd exec: ID is NA, convert to unique string
  # 3rd exec: there is no 3rd exec, just use the ID string
  if( is.null(id) ) .set(x,"id",NA)  # 1st exec: missing->NA
  else {                             # 2nd exec: NA-> tmp name
    .set(x,"id", id <- .key.make("RTMP")) # Flag as code-emitted by assigning the cluster name
    res <- paste0("(tmp= ",id," ",res,")")
  }
  res
}

.clear.impl <- function(x) {
  if( !is.H2OFrame(x) ) return()
  eval <- attr(x, "eval")
  if( !is.list(eval) ) { stopifnot(base::is.character( attr(x, "id") )); return() }
  lapply(eval, function(child) .clear.impl(child))
  if( base::is.character( attr(x, "id")) )
    .set(x,"eval",TRUE) # GC-able temp
}

# Evaluate this H2OFrame, giving the result a name, and never re-execute it.
#
# Because of GC, this algo requires 2 passes over the DAG.  The first pass
# builds the expression string - but it cannot let any of the sub-parts go
# dead, lest GC delete frames on last use... before the expression string is
# shipped over the wire.  During the 2nd pass the internal DAG pointers are
# wiped out, and allowed to go dead (hence can be nuked by GC).
#
.eval.frame <- function(x) {
  id <- attr(chk.H2OFrame(x), "id")
  if( base::is.character(id) ) return(x)  # Already executed and named
  # H2OFrame does not have a name in the cluster?
  # Act "as if" they're on the 2nd execution - and
  # they will get assigned a temp
  .set(x,"id",NA)
  .eval.driver(x) # Return the evaluated and id'd result
}
.eval.scalar <- function(x) {
  dat <- attr(chk.H2OFrame(x), "data")
  if( !is.null(dat) ) return(dat)   # Return cached scalar
  stopifnot(is.null(attr(x, "id"))) # No names for scalars
  attr(.eval.driver(x),"data")      # Cache and return scalar
}
.eval.driver <- function(x) {
  # Build the AST; this will assign a name as needed
  exec_str <- .eval.impl(x)
  # Execute the AST on H2O
  #print(paste0("EXPR: ",exec_str))
  res <- .h2o.__remoteSend(.h2o.__RAPIDS, h2oRestApiVersion = 99, ast=exec_str, session_id=h2o.getConnection()@mutable$session_id, method = "POST")
  if( !is.null(res$error) ) stop(paste0("Error From H2O: ", res$error), call.=FALSE)
  if( !is.null(res$scalar) ) { # Fetch out a scalar answer
    y <- res$scalar
    if( length(y) == 1 ) {
      if( y=="TRUE" )       y <- TRUE
      else if( y=="FALSE" ) y <- FALSE
    }
    .set(x,"data",as.numeric(y))
  } else if( !is.null(res$funstr) ) {
    stop("Unimplemented: handling of function returns")
  } else if( !is.null(res$string) ) {
    .set(x,"data",res$string)
  } else if( !is.null(res$key) ) {
    .set(x,"nrow",res$num_rows)
    .set(x,"ncol",res$num_cols)
    # No data set, none fetched.  So no column names, nor preview data nor column types
  }
  # Now clear all internal DAG nodes, allowing GC to reclaim them
  .clear.impl(x)
  # Enable this GC to trigger rapid R GC cycles, and rapid R clearing of
  # temps... to help debug GC issues.
  #.h2o.gc()
  x
}

#` Fetch the first N rows on demand, caching them in x$data; also cache x$types.
#` nrow and ncol are usually already set, but for getFrame they are set to -1
#` and immediately set here.
.fetch.data <- function(x,M, N) {
  stopifnot(!missing(M))
  M <- max(M,10L)
  data = attr(chk.H2OFrame(x), "data")
  nstr = ifelse(missing(N),"",paste0("&column_count=",N))
  if( is.null(data) || (is.data.frame(data) && nrow(data) < M) ) {
    res <- .h2o.__remoteSend(paste0(.h2o.__FRAMES, "/", h2o.getId(x), "?row_count=",M,nstr))$frames[[1]]
    .set(x,"types",lapply(res$columns, function(c) c$type))
    nrow <- .set.nlen(x,"nrow",res$rows)
    ncol <- .set.nlen(x,"ncol",res$num_columns)
    if( res$row_count==0 ) {
      data <- as.data.frame(matrix(NA,ncol=ncol,nrow=0L))
      colnames(data) <- unlist(lapply(res$columns, function(c) c$label))
    } else {
      # Convert to data.frame
      L <- lapply(res$columns, function(c) {
        row <- if( c$type!="string" && c$type!="uuid" )  c$data  else  c$string_data
        #if( length(row)!=res$row_count ) browser()
        stopifnot(length(row)==res$row_count) # No short columns
        row
      })
      data <- data.frame(L)
      colnames(data) <- unlist(lapply(res$columns, function(c) c$label))
      for( i in 1:length(data) ) {  # Set factor levels
        dom <- res$columns[[i]]$domain
        if( !is.null(dom) && length(dom)>0 ) # H2O has a domain; force R to do so also
          data[,i] <- factor(data[,i],levels=seq(0,length(dom)-1),labels=dom)
        else if( is.factor(data[,i]) ) # R has a domain, but H2O does not
          data[,i] <- as.character(data[,i]) # Force to string type
      }
    }
    .set(x,"data",data)
  }
  attr(x,"data")
}

.set.nlen <- function(x,fld,nlen) {
  y <- attr(x,fld)
  if( is.null(y) || y == -1 ) .set(x,fld,(y=nlen))
  else stopifnot(y==nlen)
  y
}

#` Flush any cached data
.flush.data <- function(x) {
  if( !is.null(attr(x,"data")) ) attr(x, "data")  <- NULL
  if( !is.null(attr(x,"nrow")) ) attr(x, "nrow")  <- NULL
  if( !is.null(attr(x,"ncol")) ) attr(x, "ncol")  <- NULL
  if( !is.null(attr(x,"types"))) attr(x, "types") <- NULL
  x
}

#` Garbage collection via R gc()
.h2o.gc <- function() {
  gc()
}

.getExpanded <- function(data,interactions=NULL,useAll=FALSE,standardize=FALSE,interactionsOnly=FALSE) {
  interactions <- .collapse.char(interactions)
  if( interactions=="") interactions <- NULL
  res <- .h2o.__remoteSend("DataInfoFrame", method = "POST", frame=h2o.getId(data), interactions=interactions, use_all=useAll,standardize=standardize,interactions_only=interactionsOnly)
  h2o.getFrame(res$result$name)
}

#-----------------------------------------------------------------------------------------------------------------------
# Frame Operations
#-----------------------------------------------------------------------------------------------------------------------
#' Get back-end distributed key/value store id from an H2OFrame.
#'
#' @param x An H2OFrame
#' @return The id of the H2OFrame
#' @export
h2o.getId <- function(x) attr( .eval.frame(x), "id")

#' Get the types-per-column
#'
#' @param x An H2OFrame
#' @return A list of types per column
#' @export
h2o.getTypes <- function(x) attr( .eval.frame(x), "types")

#'
#' Rename an H2O object.
#'
#' Makes a copy of the data frame and gives it the desired the key.
#'
#' @param data An H2OFrame object
#' @param key The hex key to be associated with the H2O parsed data object
#'
#' @export
h2o.assign <- function(data, key) {
  .key.validate(key)
  id <- h2o.getId(data)
  if( key == id ) stop("Destination key must differ from input frame ", key)
  x = .eval.driver(.newExpr("assign", key, id)) # Eager eval, so can see it in cluster
  .set(x,"id",key)
  .set(x,"eval",NULL)
  gc()
  x
}

#' Data H2OFrame Creation in H2O
#'
#' Creates a data frame in H2O with real-valued, categorical, integer, and binary columns specified by the user.
#'
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
#' @param time_fraction The fraction of randomly created date/time columns.
#' @param string_fraction The fraction of randomly created string columns.
#' @param missing_fraction The fraction of total entries in the data frame that are set to NA.
#' @param response_factors If \code{has_response = TRUE}, then this is the number of factor levels in the response column.
#' @param has_response A logical value indicating whether an additional response column should be pre-pended to the final H2O data frame. If set to TRUE, the total number of columns will be \code{cols+1}.
#' @param seed A seed used to generate random values when \code{randomize = TRUE}.
#' @param seed_for_column_types A seed used to generate random column types when \code{randomize = TRUE}.
#' @return Returns an H2OFrame object.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' hex <- h2o.createFrame(rows = 1000, cols = 100, categorical_fraction = 0.1,
#'                        factors = 5, integer_fraction = 0.5, integer_range = 1,
#'                        has_response = TRUE)
#' head(hex)
#' summary(hex)
#'
#' hex2 <- h2o.createFrame(rows = 100, cols = 10, randomize = FALSE, value = 5,
#'                         categorical_fraction = 0, integer_fraction = 0)
#' summary(hex2)
#' }
#' @export
h2o.createFrame <- function(rows = 10000, cols = 10, randomize = TRUE,
                            value = 0, real_range = 100, categorical_fraction = 0.2, factors = 100,
                            integer_fraction = 0.2, integer_range = 100, binary_fraction = 0.1,
                            binary_ones_fraction = 0.02, time_fraction = 0, string_fraction = 0,
                            missing_fraction = 0.01, response_factors = 2,
                            has_response = FALSE, seed, seed_for_column_types) {
  if(!is.numeric(rows)) stop("`rows` must be a positive number")
  if(!is.numeric(cols)) stop("`cols` must be a positive number")
  if(!missing(seed) && !is.numeric(seed)) stop("`seed` must be a numeric value")
  if(!missing(seed_for_column_types) && !is.numeric(seed_for_column_types)) stop("`seed_for_column_types` must be a numeric value")
  if(!is.logical(randomize)) stop("`randomize` must be TRUE or FALSE")
  if(!is.numeric(value)) stop("`value` must be a numeric value")
  if(!is.numeric(real_range)) stop("`real_range` must be a numeric value")
  if(!is.numeric(categorical_fraction)) stop("`categorical_fraction` must be a numeric value")
  if(!is.numeric(factors)) stop("`factors` must be a numeric value")
  if(!is.numeric(integer_fraction)) stop("`integer_fraction` must be a numeric value")
  if(!is.numeric(integer_range)) stop("`integer_range` must be a numeric value")
  if(!is.numeric(binary_fraction)) stop("`binary_fraction` must be a numeric value")
  if(!is.numeric(binary_ones_fraction)) stop("`binary_ones_fraction` must be a numeric value")
  if(!is.numeric(time_fraction)) stop("`time_fraction` must be a numeric value")
  if(!is.numeric(string_fraction)) stop("`string_fraction` must be a numeric value")
  if(!is.numeric(missing_fraction)) stop("`missing_fraction` must be a numeric value")
  if(!is.numeric(response_factors)) stop("`response_factors` must be a numeric value")
  if(!is.logical(has_response)) stop("`has_response` must be a logical value")

  parms <- lapply(as.list(match.call(expand.dots = FALSE)[-1L]), eval.parent, 2)  # depth must be 2 in order to pop out of the lapply scope...
  parms$dest = .key.make("RTMP")

  res <- .h2o.__remoteSend(.h2o.__CREATE_FRAME, method = "POST", .params = parms)
  .h2o.__waitOnJob(res$key$name)
  fr <- .newH2OFrame("createFrame",parms$dest,-1,-1)
  .fetch.data(fr,1L)
  .set(fr,"eval",TRUE)  # Declare the result a named tmp
  reg.finalizer(fr, .nodeFinalizer, onexit=TRUE)
  fr
}

#' Categorical Interaction Feature Creation in H2O
#'
#' Creates a data frame in H2O with n-th order interaction features between categorical columns, as specified by the user.
#'
#' @param data An H2OFrame object containing the categorical columns.
#' @param destination_frame A string indicating the destination key. If empty, this will be auto-generated by H2O.
#' @param factors Factor columns (either indices or column names).
#' @param pairwise Whether to create pairwise interactions between factors (otherwise create one higher-order interaction). Only applicable if there are 3 or more factors.
#' @param max_factors Max. number of factor levels in pair-wise interaction terms (if enforced, one extra catch-all factor will be made)
#' @param min_occurrence Min. occurrence threshold for factor levels in pair-wise interaction terms
#' @return Returns an H2OFrame object.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#'
#' # Create some random data
#' myframe <- h2o.createFrame(rows = 20, cols = 5,
#'                          seed = -12301283, randomize = TRUE, value = 0,
#'                          categorical_fraction = 0.8, factors = 10, real_range = 1,
#'                          integer_fraction = 0.2, integer_range = 10,
#'                          binary_fraction = 0, binary_ones_fraction = 0.5,
#'                          missing_fraction = 0.2,
#'                          response_factors = 1)
#' # Turn integer column into a categorical
#' myframe[,5] <- as.factor(myframe[,5])
#' head(myframe, 20)
#'
#' # Create pairwise interactions
#' pairwise <- h2o.interaction(myframe, destination_frame = 'pairwise',
#'                             factors = list(c(1,2),c("C2","C3","C4")),
#'                             pairwise=TRUE, max_factors = 10, min_occurrence = 1)
#' head(pairwise, 20)
#' h2o.levels(pairwise,2)
#'
#' # Create 5-th order interaction
#' higherorder <- h2o.interaction(myframe, destination_frame = 'higherorder', factors = c(1,2,3,4,5),
#'                                pairwise=FALSE, max_factors = 10000, min_occurrence = 1)
#' head(higherorder, 20)
#'
#' # Limit the number of factors of the "categoricalized" integer column
#' # to at most 3 factors, and only if they occur at least twice
#' head(myframe[,5], 20)
#' trim_integer_levels <- h2o.interaction(myframe, destination_frame = 'trim_integers', factors = "C5",
#'                                        pairwise = FALSE, max_factors = 3, min_occurrence = 2)
#' head(trim_integer_levels, 20)
#'
#' # Put all together
#' myframe <- h2o.cbind(myframe, pairwise, higherorder, trim_integer_levels)
#' myframe
#' head(myframe,20)
#' summary(myframe)
#' }
#' @export
h2o.interaction <- function(data, destination_frame, factors, pairwise, max_factors, min_occurrence) {
  chk.H2OFrame(data)
  if(missing(factors)) stop("factors must be specified")
  if(!is.logical(pairwise)) stop("pairwise must be a boolean value")
  if(missing(max_factors)) stop("max_factors must be specified")
  if(missing(min_occurrence)) stop("min_occurrence must be specified")

  if (is.list(factors)) {
      res <- lapply(factors, function(factor) h2o.interaction(data, destination_frame=NULL, factor, pairwise, max_factors, min_occurrence))
    if (!missing(destination_frame)) {
      old <- h2o.cbind(res)
      new <- h2o.assign(old, destination_frame)
      return(new)
    } else {
      return(h2o.cbind(res))
    }
  }

  if(is.numeric(factors)) { factors <- colnames(data)[factors] }
  if(is.numeric(factors)) stop("factors cannot be numeric value(s)")

  if(is.null(factors)) stop("factors not found")
  if(max_factors < 1) stop("max_factors cannot be < 1")
  if(!is.numeric(max_factors)) stop("max_factors must be a numeric value")
  if(min_occurrence < 1) stop("min_occurrence cannot be < 1")
  if(!is.numeric(min_occurrence)) stop("min_occurrence must be a numeric value")

  parms <- list()
  if(missing(destination_frame) || !base::is.character(destination_frame) || !nzchar(destination_frame)){
    parms$dest = .key.make(prefix = "interaction")
  }else{
    parms$dest <- destination_frame
  }
  .key.validate(parms$dest)
  parms$source_frame <- h2o.getId(data)
  parms$factor_columns <- .collapse.char(factors)
  parms$pairwise <- pairwise
  parms$max_factors <- max_factors
  parms$min_occurrence <- min_occurrence

  res <- .h2o.__remoteSend(page = 'Interaction', method = "POST", .params = parms)

  job_key  <- res$key$name
  dest_key <- res$dest$name
  .h2o.__waitOnJob(job_key)
  h2o.getFrame(dest_key)
}

#' Replicate Elements of Vectors or Lists into H2O
#'
#' \code{h2o.rep_len} performs just as \code{rep} does. It replicates the values in
#' \code{x} in the H2O backend.
#'
#' @param x an H2O frame
#' @param length.out non negative integer. The desired length of the output
#'        vector.
#' @return Creates an H2OFrame of the same type as x
#' @export
h2o.rep_len <- function(x, length.out) {
  if (length.out <= 0)  NULL
  else                  .newExpr("rep_len", x, length.out)
}

#' Insert Missing Values into an H2OFrame
#'
#' Randomly replaces a user-specified fraction of entries in an H2O dataset with missing values.
#'
#' @param data An H2OFrame object representing the dataset.
#' @param fraction A number between 0 and 1 indicating the fraction of entries
#'        to replace with missing.
#' @param seed A random number used to select which entries to replace with
#'        missing values. Default of \code{seed = -1} will automatically
#'        generate a seed in H2O.
#' @return Returns an H2OFrame object.
#' @section WARNING: This will modify the original dataset. Unless this is intended,
#' this function should only be called on a subset of the original.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' irisPath <- system.file("extdata", "iris.csv", package = "h2o")
#' iris.hex <- h2o.importFile(path = irisPath)
#' summary(iris.hex)
#' irismiss.hex <- h2o.insertMissingValues(iris.hex, fraction = 0.25)
#' head(irismiss.hex)
#' summary(irismiss.hex)
#' }
#' @export
h2o.insertMissingValues <- function(data, fraction=0.1, seed=-1) {
  parms = list()
  parms$dataset <- h2o.getId(data) # Eager force evaluation
  parms$fraction <- fraction
  if( !missing(seed) )
    parms$seed <- seed
  json <- .h2o.__remoteSend(method = "POST", page = 'MissingInserter', .params = parms)
  .h2o.__waitOnJob(json$key$name)
  .flush.data(data); .fetch.data(data,10L) # Flush cache and return data
  data
}

#' Split an H2O Data Set
#'
#' Split an existing H2O data set according to user-specified ratios. The number of
#' subsets is always 1 more than the number of given ratios. Note that this does not give
#' an exact split. H2O is designed to be efficient on big data using a probabilistic
#' splitting method rather than an exact split. For example, when specifying a split of
#' 0.75/0.25, H2O will produce a test/train split with an expected value of 0.75/0.25
#' rather than exactly 0.75/0.25. On small datasets, the sizes of the resulting splits
#' will deviate from the expected value more than on big data, where they will be very
#' close to exact.
#'
#' @param data An H2OFrame object representing the dataste to split.
#' @param ratios A numeric value or array indicating the ratio of total rows
#'        contained in each split. Must total up to less than 1.
#' @param destination_frames An array of frame IDs equal to the number of ratios
#'        specified plus one.
#' @param seed Random seed.
#' @return Returns a list of split H2OFrame's
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' irisPath <- system.file("extdata", "iris.csv", package = "h2o")
#' iris.hex <- h2o.importFile(path = irisPath)
#' iris.split <- h2o.splitFrame(iris.hex, ratios = c(0.2, 0.5))
#' head(iris.split[[1]])
#' summary(iris.split[[1]])
#' }
#' @export
h2o.splitFrame <- function(data, ratios = 0.75, destination_frames, seed = -1) {
  chk.H2OFrame(data)

  if (! is.numeric(ratios)) stop("ratios must be of type numeric")
  if (length(ratios) < 1) stop("ratios must have length of at least 1")

  if (! missing(destination_frames)) {
    if (! base::is.character(destination_frames)) stop("destination_frames must be of type character")
    if ((length(ratios) + 1) != length(destination_frames)) {
      stop("The number of provided destination_frames must be one more than the number of provided ratios")
    }
  }

  if (! is.numeric(seed)) stop("seed must be an integer")

  num_slices = length(ratios) + 1
  boundaries = numeric(length(ratios))

  i = 1
  last_boundary = 0
  while (i < num_slices) {
    ratio = ratios[i]
    if (ratio < 0) {
      stop("Ratio must be greater than 0")
    }

    boundary = last_boundary + ratio
    if (boundary >= 1) {
      stop("Ratios must add up to less than 1.0")
    }

    boundaries[i] = boundary
    last_boundary = boundary

    i = i + 1
  }

  splits = list()
  tmp_runif = h2o.runif(data, seed)

  i = 1
  while (i <= num_slices) {
    if (i == 1) {
      # lower_boundary is 0.0
      upper_boundary = boundaries[i]
      tmp_slice = data[tmp_runif <= upper_boundary,]
    } else if (i == num_slices) {
      lower_boundary = boundaries[i-1]
      # upper_boundary is 1.0
      tmp_slice = data[tmp_runif > lower_boundary,]
    } else {
      lower_boundary = boundaries[i-1]
      upper_boundary = boundaries[i]
      tmp_slice = data[((tmp_runif > lower_boundary) & (tmp_runif <= upper_boundary)),]
    }

    if (missing(destination_frames)) {
      splits = c(splits, tmp_slice)
    } else {
      destination_frame_id = destination_frames[i]
      tmp_slice2 = h2o.assign(tmp_slice, destination_frame_id)
      splits = c(splits, tmp_slice2)
    }

    i = i + 1
  }

  return(splits)
}

#'
#' Filter NA Columns
#'
#' @param data A dataset to filter on.
#' @param frac The threshold of NAs to allow per column (columns >= this threshold are filtered)
#' @return Returns a numeric vector of indexes that pertain to non-NA columns
#' @export
h2o.filterNACols <- function(data, frac=0.2) .eval.scalar(.newExpr("filterNACols", data, frac)) + 1  # 0 to 1 based index

#' Cross Tabulation and Table Creation in H2O
#'
#' Uses the cross-classifying factors to build a table of counts at each combination of factor levels.
#'
#' @param x An H2OFrame object with at most two columns.
#' @param y An H2OFrame similar to x, or \code{NULL}.
#' @param dense A logical for dense representation, which lists only non-zero counts, 1 combination per row. Set to 
#'        FALSE to expand counts across all combinations.  
#' @return Returns a tabulated H2OFrame object.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath, destination_frame = "prostate.hex")
#' summary(prostate.hex)
#'
#' # Counts of the ages of all patients
#' head(h2o.table(prostate.hex[,3]))
#' h2o.table(prostate.hex[,3])
#'
#' # Two-way table of ages (rows) and race (cols) of all patients
#' head(h2o.table(prostate.hex[,c(3,4)]))
#' h2o.table(prostate.hex[,c(3,4)])
#' }
#' @export
h2o.table <- function(x, y = NULL, dense = TRUE) {
  chk.H2OFrame(x)
  if( !is.null(y) ) chk.H2OFrame(y)
  if( is.null(y) ) .newExpr("table",x,dense) else .newExpr("table",x,y,dense)
}

#' @rdname h2o.table
#' @export
table.H2OFrame <- h2o.table


#' H2O Unique
#'
#' Extract unique values in the column.
#'
#' @param x An H2OFrame object.
#' @return Returns an H2OFrame object.
#' @export
h2o.unique <- function(x) .newExpr("unique", x)

#' Cut H2O Numeric Data to Factor
#'
#' Divides the range of the H2O data into intervals and codes the values according to which interval they fall in. The
#' leftmost interval corresponds to the level one, the next is level two, etc.
#'
#' @param x An H2OFrame object with a single numeric column.
#' @param breaks A numeric vector of two or more unique cut points.
#' @param labels Labels for the levels of the resulting category. By default, labels are constructed sing "(a,b]"
#'        interval notation.
#' @param include.lowest \code{Logical}, indicationg if an 'x[i]' equal to the lowest (or highest, for \code{right =
#'        FALSE} 'breaks' value should be included
#' @param right /code{Logical}, indicating if the intervals should be closed on the right (opened on the left) or vice
#'        versa.
#' @param dig.lab Integer which is used when labels are not given, determines the number of digits used in formatting
#'        the break numbers.
#' @param ... Further arguments passed to or from other methods.
#' @return Returns an H2OFrame object containing the factored data with intervals as levels.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' irisPath <- system.file("extdata", "iris_wheader.csv", package="h2o")
#' iris.hex <- h2o.uploadFile(path = irisPath, destination_frame = "iris.hex")
#' summary(iris.hex)
#'
#' # Cut sepal length column into intervals determined by min/max/quantiles
#' sepal_len.cut <- cut(iris.hex$sepal_len, c(4.2, 4.8, 5.8, 6, 8))
#' head(sepal_len.cut)
#' summary(sepal_len.cut)
#' }
#' @export
h2o.cut <- function(x, breaks, labels = NULL, include.lowest = FALSE, right = TRUE, dig.lab = 3, ...) {
  if (!is.numeric(breaks) || length(breaks) == 0L || !all(is.finite(breaks)))
    stop("`breaks` must be a numeric vector")
  .newExpr("cut", chk.H2OFrame(x), breaks, labels, include.lowest, right, dig.lab)
}

#' @rdname h2o.cut
#' @export
cut.H2OFrame <- h2o.cut

# `match` or %in% for H2OFrame
#' Value Matching in H2O
#'
#' \code{match} and \code{\%in\%} return values similar to the base R generic
#' functions.
#'
#' @param x a categorical vector from an H2OFrame object with
#'        values to be matched.
#' @param table an R object to match \code{x} against.
#' @param nomatch the value to be returned in the case when no match is found.
#' @param incomparables a vector of calues that cannot be matched. Any value in
#'        \code{x} matching a value in this vector is assigned the
#'        \code{nomatch} value.
#' @return Returns a vector of the positions of (first) matches of its first argument in its second
#' @seealso \code{\link[base]{match}} for base R implementation.
#' @examples
#' \donttest{
#' h2o.init()
#' hex <- as.h2o(iris)
#' h2o.match(hex[,5], c("setosa", "versicolor"))
#' }
#' @export
h2o.match <- function(x, table, nomatch = 0, incomparables = NULL) {
  if( !is.H2OFrame(table) && length(table)==1 && base::is.character(table) ) table <- .quote(table)
  .newExpr("match", chk.H2OFrame(x), table, nomatch, incomparables)
}

#' @rdname h2o.match
#' @export
match.H2OFrame <- h2o.match

# %in% method
#' @rdname h2o.match
#' @export
`%in%` <- function(x,table) {
  if( is.H2OFrame(x) ) h2o.match(x,table,nomatch=0)
  else base::`%in%`(x,table)
}

#' Remove Rows With NAs
#'
#' @rdname h2o.na_omit
#' @param object H2OFrame object
#' @param ... Ignored
#' @return Returns an H2OFrame object containing non-NA rows.
#' @export
h2o.na_omit <- function(object, ...){
  .newExpr("na.omit", object)
}

#' @export
na.omit.H2OFrame <- h2o.na_omit

#' Obtain a list of columns that are specified by `coltype`
#'
#' @rdname h2o.columns_by_type
#' @param object H2OFrame object
#' @param coltype A character string indicating which column type to filter by. This must be one of the following:
#'   "numeric"      - Numeric, but not categorical or time
#'   "categorical"  - Integer, with a categorical/factor String mapping
#'   "string"       - String column
#'   "time"         - Long msec since the Unix Epoch - with a variety of display/parse options
#'   "uuid"         - UUID
#'   "bad"          - No none-NA rows (triple negative! all NAs or zero rows)
#' @param ... Ignored
#' @return A list of column indices that correspond to "type"
#' @examples
#' \donttest{
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath)
#' h2o.columns_by_type(prostate.hex,coltype="numeric")
#' }
#' @export
h2o.columns_by_type <- function(object,coltype="numeric",...){
  if(!is.H2OFrame(object)){
    stop("h2o.filter_type only operates on H2OFrames.")
  }
  if(!is.character(coltype)){
    stop("`coltype` variable should be of type character.")
  }
  if(!(coltype %in% c("numeric", "categorical", "string", "time", "uuid", "bad"))){
    stop(paste0("`coltype` must be one of the following: numeric, categorical, string, time, uuid, or bad but got "
    , coltype))
  }
  .eval.scalar(.newExpr("columnsByType", object,.quote(coltype))) + 1
}

#' Compute DCT of an H2OFrame
#'
#' Compute the Discrete Cosine Transform of every row in the H2OFrame
#'
#' @param data An H2OFrame object representing the dataset to transform
#' @param destination_frame A frame ID for the result
#' @param dimensions An array containing the 3 integer values for height, width, depth of each sample.
#'        The product of HxWxD must total up to less than the number of columns.
#'        For 1D, use c(L,1,1), for 2D, use C(N,M,1).
#' @param inverse Whether to perform the inverse transform
#' @return Returns an H2OFrame object.
#' @examples
#' \donttest{
#'   library(h2o)
#'   h2o.init()
#'   df <- h2o.createFrame(rows = 1000, cols = 8*16*24,
#'                         categorical_fraction = 0, integer_fraction = 0, missing_fraction = 0)
#'   df1 <- h2o.dct(data=df, dimensions=c(8*16*24,1,1))
#'   df2 <- h2o.dct(data=df1,dimensions=c(8*16*24,1,1),inverse=TRUE)
#'   max(abs(df1-df2))
#'
#'   df1 <- h2o.dct(data=df, dimensions=c(8*16,24,1))
#'   df2 <- h2o.dct(data=df1,dimensions=c(8*16,24,1),inverse=TRUE)
#'   max(abs(df1-df2))
#'
#'   df1 <- h2o.dct(data=df, dimensions=c(8,16,24))
#'   df2 <- h2o.dct(data=df1,dimensions=c(8,16,24),inverse=TRUE)
#'   max(abs(df1-df2))
#' }
#' @export
h2o.dct <- function(data, destination_frame, dimensions, inverse=FALSE) {
  if(!is.logical(inverse)) stop("inverse must be a boolean value")
  params <- list()
  params$dataset <- h2o.getId(data)
  params$dimensions <- .collapse(dimensions)
  if (!missing(destination_frame))
    params$destination_frame <- destination_frame
  params$inverse <- inverse

  res <- .h2o.__remoteSend(method="POST", h2oRestApiVersion = 99, "DCTTransformer", .params = params)
  job_key <- res$key$name
  .h2o.__waitOnJob(job_key)
  h2o.getFrame(res$dest$name)
}

#' Produce a Vector of Random Uniform Numbers
#'
#' Creates a vector of random uniform numbers equal in length to the length of the specified H2O
#' dataset.
#'
#' @param x An H2OFrame object.
#' @param seed A random seed used to generate draws from the uniform distribution.
#' @return A vector of random, uniformly distributed numbers. The elements are between 0 and 1.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.importFile(path = prosPath, destination_frame = "prostate.hex")
#' s <- h2o.runif(prostate.hex)
#' summary(s)
#'
#' prostate.train <- prostate.hex[s <= 0.8,]
#' prostate.train <- h2o.assign(prostate.train, "prostate.train")
#' prostate.test <- prostate.hex[s > 0.8,]
#' prostate.test <- h2o.assign(prostate.test, "prostate.test")
#' nrow(prostate.train) + nrow(prostate.test)
#' }
#' @export
h2o.runif <- function(x, seed = -1) {
  if (!is.numeric(seed) || length(seed) != 1L || !is.finite(seed)) stop("`seed` must be an integer >= 0")
if (seed == -1) seed <- floor(runif(1,1,.Machine$integer.max*100))
.newExpr("h2o.runif", chk.H2OFrame(x), seed)
}

#' Produce a k-fold column vector.
#'
#' Create a k-fold vector useful for H2O algorithms that take a fold_assignments argument.
#'
#' @param data A dataframe against which to create the fold column.
#' @param nfolds The number of desired folds.
#' @param seed A random seed, -1 indicates that H2O will choose one.
#' @return Returns an H2OFrame object with fold assignments.
#' @export
h2o.kfold_column <- function(data,nfolds,seed=-1) .eval.frame(.newExpr("kfold_column",data,nfolds,seed))

#' Check H2OFrame columns for factors
#'
#' Determines if any column of an H2OFrame object contains categorical data.
#'
#' @name h2o.anyFactor
#' @param x An \code{H2OFrame} object.
#' @return Returns a logical value indicating whether any of the columns in \code{x} are factors.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' irisPath <- system.file("extdata", "iris_wheader.csv", package="h2o")
#' iris.hex <- h2o.importFile(path = irisPath)
#' h2o.anyFactor(iris.hex)
#' }
#' @export
h2o.anyFactor <- function(x) as.logical(.eval.scalar(.newExpr("any.factor", x)))

#'
#' Quantiles of H2O Frames.
#'
#' Obtain and display quantiles for H2O parsed data.
#'
#' \code{quantile.H2OFrame}, a method for the \code{\link{quantile}} generic. Obtain and return quantiles for
#' an \code{H2OFrame} object.
#'
#' @name h2o.quantile
#' @param x An \code{H2OFrame} object with a single numeric column.
#' @param probs Numeric vector of probabilities with values in [0,1].
#' @param combine_method How to combine quantiles for even sample sizes. Default is to do linear interpolation.
#'                       E.g., If method is "lo", then it will take the lo value of the quantile. Abbreviations for average, low, and high are acceptable (avg, lo, hi).
#' @param weights_column (Optional) String name of the observation weights column in x or an \code{H2OFrame} object with a single numeric column of observation weights.
#' @param ... Further arguments passed to or from other methods.
#' @return A vector describing the percentiles at the given cutoffs for the \code{H2OFrame} object.
#' @examples
#' \donttest{
#' # Request quantiles for an H2O parsed data set:
#' library(h2o)
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath)
#' # Request quantiles for a subset of columns in an H2O parsed data set
#' quantile(prostate.hex[,3])
#' for(i in 1:ncol(prostate.hex))
#'    quantile(prostate.hex[,i])
#' }
#' @importFrom utils capture.output
#' @export
h2o.quantile <- function(x,
  # AUTOGENERATED params
  probs = c(0.001, 0.01, 0.1, 0.25, 0.333, 0.5, 0.667, 0.75, 0.9, 0.99, 0.999),
  combine_method = c("interpolate", "average", "avg", "low", "high"),
  weights_column = NULL,
  ...)
  {
    # verify input parameters
  if (!is(x, "H2OFrame")) stop("`x` must be an H2OFrame object")
  #if(!na.rm && .h2o.__unary_op("any.na", x)) stop("missing values and NaN's not allowed if 'na.rm' is FALSE")
  if(!is.numeric(probs) || length(probs) == 0L || any(!is.finite(probs) | probs < 0 | probs > 1))
  stop("`probs` must be between 0 and 1 exclusive")
  if (is.null(weights_column)) {
    weights_column <- "_" ##HACK: .newExpr() strips "", must use something else here.
  } else {
  if (!(is.character(weights_column) || (is(weights_column, "H2OFrame") && ncol(weights_column) ==1) && nrow(weights_column) == nrow(x)))
  stop("`weights_column` must be a String of a column name in x or an H2OFrame object with 1 column and same row count as x")
  if (is(weights_column, "H2OFrame")) {
    x <- h2o.cbind(x,weights_column)
  weights_column <- tail(names(x),1)
  }
  if (!(weights_column %in% names(x))) stop("`weights_column` must be a column in x")
  }

  combine_method = match.arg(combine_method)
  # match.arg converts partial string "lo"->"low", "hi"->"high" etc built in
  #           is the standard way to avoid warning: "the condition has length > 1 and only first will be used"
  #       and stops if argument wasn't found, built-in
  if (combine_method == "avg") combine_method = "average"  # 'avg'->'average' is too much for match.arg though

  #if(type != 2 && type != 7) stop("type must be either 2 (mean interpolation) or 7 (linear interpolation)")
  #if(type != 7) stop("Unimplemented: Only type 7 (linear interpolation) is supported from the console")
  res <- .newExpr("quantile", x, .num.list(probs), .quote(combine_method), weights_column)
  tr <- as.matrix(t(res))
  rownames(tr) <- colnames(res)
  # detecting potential issues
  non2dim <- length(dim(tr)) < 2L
  nonnum <- !is.numeric(tr[1,])
  if (non2dim || nonnum) {
    warn <- paste("If you are able to provide reproducible example of error please submit as bug report.\nStructure of object returned:\n", paste(capture.output(str(tr)), collapse="\n"), sep="")
  if (non2dim)
  warning("Object returned from quantile method have less than 2 dimensions and will probably fail on further calls.\n", warn)
  else if (nonnum)
  warning("Object returned from quantile method is not numeric and will probably fail on further calls.\n", warn)
  }
  colnames(tr) <- paste0(100*tr[1,],"%")
  tr[-1,]
}

#' @rdname h2o.quantile
#' @importFrom utils capture.output
#' @export
quantile.H2OFrame <- h2o.quantile
#' Basic Imputation of H2O Vectors
#'
#' Perform inplace imputation by filling missing values with aggregates
#' computed on the "na.rm'd" vector. Additionally, it's possible to perform imputation
#' based on groupings of columns from within data; these columns can be passed by index or
#' name to the by parameter. If a factor column is supplied, then the method must be
#' "mode".
#'
#' The default method is selected based on the type of the column to impute. If the column
#' is numeric then "mean" is selected; if it is categorical, then "mode" is selected. Other
#' column types (e.g. String, Time, UUID) are not supported.
#'
#' @param data The dataset containing the column to impute.
#' @param column A specific column to impute, default of 0 means impute the whole frame.
#' @param method "mean" replaces NAs with the column mean; "median" replaces NAs with the column median;
#'               "mode" replaces with the most common factor (for factor columns only);
#' @param combine_method If method is "median", then choose how to combine quantiles on even sample sizes. This parameter is ignored in all other cases.
#' @param by group by columns
#' @param groupByFrame Impute the column col with this pre-computed grouped frame.
#' @param values A vector of impute values (one per column). NaN indicates to skip the column
#' @return an H2OFrame with imputed values
#' @examples
#' \donttest{
#'  h2o.init()
#'  fr <- as.h2o(iris, destination_frame="iris")
#'  fr[sample(nrow(fr),40),5] <- NA  # randomly replace 50 values with NA
#'  # impute with a group by
#'  fr <- h2o.impute(fr, "Species", "mode", by=c("Sepal.Length", "Sepal.Width"))
#' }
#' @export
h2o.impute <- function(data, column=0, method=c("mean","median","mode"), # TODO: add "bfill","ffill"
  combine_method=c("interpolate", "average", "lo", "hi"), by=NULL, groupByFrame=NULL, values=NULL) {
  # TODO: "bfill" back fill the missing value with the next non-missing value in the vector
  # TODO: "ffill" front fill the missing value with the most-recent non-missing value in the vector.
  # TODO: #'  @param max_gap  The maximum gap with which to fill (either "ffill", or "bfill") missing values. If more than max_gap consecutive missing values occur, then those values remain NA.

  # this AST: (h2o.impute %fr #colidx method combine_method inplace max_gap by)
  chk.H2OFrame(data)
  if (!is.null(groupByFrame)) chk.H2OFrame(groupByFrame)
  else groupByFrame <- "_"  # NULL value for rapids backend

  if (is.null(values)) {
    values <- "_"  # TODO: exposes categorical-int mapping! Fix this with an object that hides mapping...
  } else {
    if (length(values) != ncol(data)) {
      stop("Length of values does not match length of columns")
    } else {
      values2 <- c()
      for (i in 1:length(values)) {
        if (is.factor(data[i]) && !(values[i] %in% h2o.levels(data[i]))) {
          stop(paste0("Impute value of: ",values[i]," not found in existing levels of column: ",colnames(data[i])))
        }
        values2[i] <- values[i]
      }
      values <- values2
    }
  }

  # sanity check `column` then convert to 0-based index.
  if( length(column) > 1L ) stop("`column` must be a single column.")
  col.id <- -1L
  if( is.numeric(column) ) col.id <- column - 1L
  else                     col.id <- match(column,colnames(data)) - 1L
  if( col.id > (ncol(data)-1L) ) stop("Column ", col.id, " out of range.")

  # choose "mean" by default for numeric columns. "mode" for factor columns
  if( length(method) > 1) method <- "mean"

  # choose "interplate" by default for combine_method
  if( length(combine_method) > 1L ) combine_method <- "interpolate"
  if( combine_method=="lo" ) combine_method <- "low"
  if( combine_method=="hi" ) combine_method <- "high"

  # sanity check method, column type, by parameters
  if( method=="median" ) {
    # no by and median
  if( !is.null(by) ) stop("Unimplemented: No `by` and `median`. Please select a different method.")
  }

  # handle the data
  gb.cols <- "[]"
  if( !is.null(by) ) {
    if(base::is.character(by)) {
    vars <- match(by, colnames(data))
  if( any(is.na(vars)) )
  stop('No column named ', by, ' in ', substitute(data), '.')
  } else if(is.integer(by)) { vars <- by }
  else if(is.numeric(by)) {   vars <- as.integer(by) }  # this will happen eg c(1,2,3)
  if( vars <= 0L || vars > (ncol(data)) )
  stop('Column ', vars, ' out of range for frame columns ', ncol(data), '.')
  gb.cols <- .row.col.selector(vars,envir=parent.frame())
  }

  if( gb.cols == "[]" && base::is.character(groupByFrame) ) {res <- .eval.scalar(.newExpr("h2o.impute",data, col.id, .quote(method), .quote(combine_method), gb.cols, groupByFrame, values)) }
  else { res <- .eval.frame(.newExpr("h2o.impute",data, col.id, .quote(method), .quote(combine_method), gb.cols, groupByFrame, values)) }
  .flush.data(data); .fetch.data(data,10L)
  res
}

#' Range of an H2O Column
#'
#' @param ... An H2OFrame object.
#' @param na.rm ignore missing values
#' @export
range.H2OFrame <- function(...,na.rm = TRUE) c(min(...,na.rm=na.rm), max(...,na.rm=na.rm))

#' Pivot a frame
#'
#' Pivot the frame designated by the three columns: index, column, and value. Index and column should be
#' of type enum, int, or time.
#' For cases of multiple indexes for a column label, the aggregation method is to pick the first occurrence in the data frame
#'
#' @param x an H2OFrame
#' @param index the column where pivoted rows should be aligned on
#' @param column the column to pivot
#' @param value values of the pivoted table
#' @return An H2OFrame with columns from the columns arg, aligned on the index arg, with values from values arg
#' @export
h2o.pivot <- function(x, index, column, value){
  if(! index %in% colnames(x)) stop("index column not found in dataframe")
  if(! column %in% colnames(x)) stop("column column not found in dataframe")
  if(! value %in% colnames(x)) stop("value column not found in dataframe")
  if( ! h2o.getTypes(x)[grep(index,colnames(x))] %in% c("enum","time","int")) {
    stop("index must be enum, time or int")
  }
  .newExpr("pivot", x, .quote(index), .quote(column), .quote(value))
}

# H2O topBottomN
#
# topBottomN function will will grab the top N percent or botom N percent of values of a column and return it in a
#  H2OFrame.
#
# @param x an H2OFrame
# @param column is a column name or column index to grab the top N percent value from
# @param nPercent a top percentage values to grab
# @param grabTopN if -1 grab bottom percentage, 1 grab top percentage
# @return An H2OFrame with 2 columns: first column is the original row indices, second column contains the values
h2o.topBottomN <- function(x, column, nPercent, grabTopN){
  cnames = names(x)
  colIndex=0
  if (typeof(column)=="character") {  # verify column
    if (!column %in% cnames) stop("column name not found in dataframe")
    colIndex = ((which(column==cnames ))-1)

  } else {  # column is number
    if ((column <= 0) || (column > ncol(x))) stop("Illegal column index")
    colIndex = (column-1)
  }

  # verify nPercent
  if ((nPercent <  0) || nPercent > 100) stop("nPercent is between 0 and 100.")
  if (nPercent*0.01*nrow(x) < 1) stop("Increase nPercent.  Current value will result in top 0 row.")
  if (!h2o.isnumeric(x[colIndex+1])) stop("Wrong column type!  Selected column must be numeric.")

  .newExpr("topn", x, colIndex, nPercent,grabTopN)
}

#' H2O topN
#'
#' Extract the top N percent  of values of a column and return it in a H2OFrame.
#'
#' @param x an H2OFrame
#' @param column is a column name or column index to grab the top N percent value from
#' @param nPercent is a top percentage value to grab
#' @return An H2OFrame with 2 columns.  The first column is the original row indices, second column contains the topN values
#' @export
h2o.topN <- function(x, column, nPercent) {
  h2o.topBottomN(x, column, nPercent, 1)
}
#' H2O bottomN
#'
#' bottomN function will will grab the bottom N percent of values of a column and return it in a H2OFrame.
#' Extract the top N percent of values of a column and return it in a H2OFrame.
#'
#' @param x an H2OFrame
#' @param column is a column name or column index to grab the top N percent value from
#' @param nPercent is a bottom percentage value to grab
#' @return An H2OFrame with 2 columns.  The first column is the original row indices, second column contains the bottomN values
#' @export
h2o.bottomN <- function(x, column, nPercent) {
  h2o.topBottomN(x, column, nPercent, -1)
}

#-----------------------------------------------------------------------------------------------------------------------
# Time & Date
#-----------------------------------------------------------------------------------------------------------------------

#' Convert Milliseconds to Years in H2O Datasets
#'
#' Convert the entries of an H2OFrame object from milliseconds to years, indexed
#' starting from 1900.
#'
#' This method calls the function of the MutableDateTime class in Java.
#' @param x An H2OFrame object.
#' @return An H2OFrame object containing the entries of \code{x} converted to years
#' @seealso \code{\link{h2o.month}}
#' @export
h2o.year <- function(x) .newExpr("year", chk.H2OFrame(x))


#' Convert Milliseconds to Months in H2O Datasets
#'
#' Converts the entries of an H2OFrame object from milliseconds to months (on a 1 to
#' 12 scale).
#'
#' @param x An H2OFrame object.
#' @return An H2OFrame object containing the entries of \code{x} converted to months of
#'         the year.
#' @seealso \code{\link{h2o.year}}
#' @export
h2o.month <- function(x) .newExpr("month", chk.H2OFrame(x))

#' Convert Milliseconds to Week of Week Year in H2O Datasets
#'
#' Converts the entries of an H2OFrame object from milliseconds to weeks of the week
#' year (starting from 1).
#'
#' @param x An H2OFrame object.
#' @return An H2OFrame object containing the entries of \code{x} converted to weeks of
#'         the week year.
#' @seealso \code{\link{h2o.month}}
#' @export
h2o.week <- function(x) .newExpr("week", chk.H2OFrame(x))

#' Convert Milliseconds to Day of Month in H2O Datasets
#'
#' Converts the entries of an H2OFrame object from milliseconds to days of the month
#' (on a 1 to 31 scale).
#'
#' @param x An H2OFrame object.
#' @return An H2OFrame object containing the entries of \code{x} converted to days of
#'         the month.
#' @seealso \code{\link{h2o.month}}
#' @export
h2o.day <- function(x) .newExpr("day", chk.H2OFrame(x))

#' Convert Milliseconds to Day of Week in H2O Datasets
#'
#' Converts the entries of an H2OFrame object from milliseconds to days of the week
#' (on a 0 to 6 scale).
#'
#' @param x An H2OFrame object.
#' @return An H2OFrame object containing the entries of \code{x} converted to days of
#'         the week.
#' @seealso \code{\link{h2o.day}, \link{h2o.month}}
#' @export
h2o.dayOfWeek <- function(x) .newExpr("dayOfWeek", chk.H2OFrame(x))

#' Convert Milliseconds to Hour of Day in H2O Datasets
#'
#' Converts the entries of an H2OFrame object from milliseconds to hours of the day
#' (on a 0 to 23 scale).
#'
#' @param x An H2OFrame object.
#' @return An H2OFrame object containing the entries of \code{x} converted to hours of
#'         the day.
#' @seealso \code{\link{h2o.day}}
#' @export
h2o.hour <- function(x) .newExpr("hour", chk.H2OFrame(x))

#' @rdname h2o.year
#' @export
year <- function(x) UseMethod('year', x)
#' @rdname h2o.year
#' @export
year.H2OFrame <- h2o.year

#' @rdname h2o.month
#' @export
month <- function(x) UseMethod('month', x)
#' @rdname h2o.month
#' @export
month.H2OFrame <- h2o.month

#' @rdname h2o.week
#' @export
week <- function(x) UseMethod('week', x)
#' @rdname h2o.week
#' @export
week.H2OFrame <- h2o.week

#' @rdname h2o.day
#' @export
day <- function(x) UseMethod('day', x)
#' @rdname h2o.day
#' @export
day.H2OFrame <- h2o.day

#' @rdname h2o.dayOfWeek
#' @export
dayOfWeek <- function(x) UseMethod('dayOfWeek', x)
#' @rdname h2o.dayOfWeek
#' @export
dayOfWeek.H2OFrame <- h2o.dayOfWeek

#' @rdname h2o.hour
#' @export
hour <- function(x) UseMethod('hour', x)
#' @rdname h2o.hour
#' @export
hour.H2OFrame <- h2o.hour

#' Compute msec since the Unix Epoch
#'
#' @param year Defaults to 1970
#' @param month zero based (months are 0 to 11)
#' @param day zero based (days are 0 to 30)
#' @param hour hour
#' @param minute minute
#' @param second second
#' @param msec msec
#' @export
h2o.mktime <- function(year=1970,month=0,day=0,hour=0,minute=0,second=0,msec=0) {
  # All units are zero-based (including months and days).  Missing year defaults to 1970.
  # H2OH2OFrame of one column containing the date in millis since the epoch.
  .newExpr("mktime", year,month,day,hour,minute,second,msec)
}

#' Convert between character representations and objects of Date class
#'
#' Functions to convert between character representations and objects of class "Date" representing calendar dates.
#'
#' @param x H2OFrame column of strings or factors to be converted
#' @param format A character string indicating date pattern
#' @param ... Further arguments to be passed from or to other methods.
#' @export
h2o.as_date <- function(x, format, ...) {
  if(!base::is.character(format)) stop("format must be a string")
  .newExpr("as.Date", chk.H2OFrame(x), .quote(format), ...)
}

#' @export
as.Date.H2OFrame <- h2o.as_date

#' Set the Time Zone on the H2O Cloud
#'
#' @param tz The desired timezone.
#' @export
h2o.setTimezone <- function(tz) .eval.scalar(.newExpr("setTimeZone",.quote(tz)))

#' Get the Time Zone on the H2O Cloud
#' Returns a string
#'
#' @export
h2o.getTimezone <- function() .eval.scalar(.newExpr("getTimeZone"))

#' List all of the Time Zones Acceptable by the H2O Cloud.
#'
#' @export
h2o.listTimezones <- function() .fetch.data(.newExpr("listTimeZones"),1000L)

#-----------------------------------------------------------------------------------------------------------------------
# Overloaded Base R Methods
#-----------------------------------------------------------------------------------------------------------------------

#-----------------------------------------------------------------------------------------------------------------------
# Slicing
#-----------------------------------------------------------------------------------------------------------------------

# Convert to Currents number-list syntax
.num.list <- function(nl) paste0('[',paste0(nl,collapse=" "),']')

# Convert to Currents string-list syntax
.quote <- function(x) paste0('"',x,'"')
.str.list <- function(sl) paste0('[',paste0('"',sl,'"',collapse=" "),']')

# Convert a row or column selector to zero-based numbering and return a string
.row.col.selector <- function( sel, raw_sel=NULL, envir=NULL ) {
  if( !is.symbol(sel) && is.language(sel) && sel[[1]] == ":" ) {
    lo <- eval(sel[[2]], envir=envir)
    hi <- eval(sel[[3]], envir=envir)
    if( hi < lo ) { tmp <- hi; hi <- lo; lo <- tmp }
    return(paste0("[", (if(lo<0) lo else (lo-1)), ":", hi-lo+1L, "]"))
  }

  sel <- if( !is.null(raw_sel) ) raw_sel else eval(sel)
  if( is.numeric(sel) ) { # number list for column selection; zero based
    sel2 <- lapply(sel,function(x) if( x==0 ) stop("Cannot select row or column 0") else if( x > 0 ) x-1 else x)
    .num.list(sel2)
  } else {
    if( is.null(sel) ) "[]" # Empty selector
    else as.character(sel)
  }
}

#' Extract or Replace Parts of an H2OFrame Object
#'
#' Operators to extract or replace parts of H2OFrame objects.
#'
#' @name H2OFrame-Extract
NULL

#' @aliases [,H2OFrame-method
#' @rdname H2OFrame-Extract
#' @param data object from which to extract element(s) or in which to replace element(s).
#' @param row index specifying row element(s) to extract or replace. Indices are numeric or
#'        character vectors or empty (missing) or will be matched to the names.
#' @param col index specifying column element(s) to extract or replace.
#' @param drop Unused
#' @export
`[.H2OFrame` <- function(data,row,col,drop=TRUE) {
  chk.H2OFrame(data)

  # This function is called with a huge variety of argument styles
  # Here's the breakdown:
  #   Style          Type  #args  Description
  # df[]             - na na 2    both missing, identity with df
  # df["colname"]    - c  na 2    single column by name, df$colname
  # df[3]            - X  na 2    if ncol > 1 then column else row
  # df[,]            - na na 3    both missing, identity with df
  # df[2,]           - r  na 3    constant row, all cols
  # df[1:150,]       - r  na 3    selection of rows, all cols
  # df[,3]           - na c  3    constant column
  # df[,1:10]        - na c  3    selection of columns
  # df[,"colname"]   - na c  3    single column by name
  # df[2,"colname"]  - r  c  3    row slice and column-by-name
  # df[2,3]          - r  c  3    single element
  # df[1:150,1:10]   - r  c  3    rectangular slice
  # df[a<b,]         - f  na 3    boolean row slice
  # df[a<b,c]        - f  c  3    boolean row slice
  # df[1,-1]         - r  c  3    selection of first row minus the first column
  # df[-1,-1]        - r  c  3    get rid of first row and first column
  # df[-1,1]         - r  c  3    get rid of first row and keep first column
  # df[-1:-20,-1:-3] - r  c  3    get rid of first 20 rows and first 3 columns
  # df[-1:-20,1:3]   - r  c  3    get rid of first 20 rows and keep first 3 columns
  # df[1:20,-1:-3]   - r  c  3    keep first 20 rows and remove first 3 columns

  #Some type checking
  if(!missing(col) && !(base::is.character(col)) && !(base::is.logical(col)) && !(base::is.numeric(col)) && !(is.h2o(col))){
    stop(paste0("Column must be selected as an integer index, character, logical, or H2OFrame but got ", class(col)))
  }
  if(!missing(row) && !(base::is.character(row)) && !(base::is.logical(row)) && !(base::is.numeric(row))  && !(is.h2o(row))){
    stop(paste0("Row must be selected as an integer index, character, logical, or H2OFrame but got ", class(row)))
  }
  # Boolean check for negative indexes
  is_neg_idx <- !missing(col) && !missing(row) && !is.H2OFrame(row) && !is.H2OFrame(col) && ((is.numeric(col) && col <= 0) || (is.numeric(row) && row <= 0))
  # Have a row & column selector with negative col or negative row indexes?
  if(is_neg_idx){
    if( is.logical(col) ) { # Columns by boolean choice
      col <- which(col)     # Pick out all the TRUE columns by index
    }else if (base::is.character(col)) {
      idx <- match(col, colnames(data))
      if (any(is.na(idx)))
        stop(paste0("No column(s) '", paste(col[is.na(idx)], collapse=","), "' found in ",
        paste(colnames(data), collapse = ",")))
        col <- idx
    }
    idx <- .row.col.selector(col,envir=parent.frame())
    data <- .newExpr("cols",data,idx) # Column selector
    row <- .row.col.selector(substitute(row), row,envir=parent.frame())
    data <- .newExpr("rows",data,row) # Row selector
  }

  is1by1 <- !missing(col) && !missing(row) && !is.H2OFrame(row) && length(col) == 1 && length(row) == 1 && !(is_neg_idx)
  if( nargs() == 2 &&   # Only row, no column; nargs==2 distinguishes "df[2,]" (row==2) from "df[2]" (col==2)
      # is.char tells cars["cylinders"], or if there are multiple columns.
      # Single column with numeric selector is row: car$cylinders[100]
      (base::is.character(row) || ncol(data) > 1) && !(is_neg_idx)) {
    # Row is really column: cars[3] or cars["cylinders"] or cars$cylinders
    col <- row
    row <- NA
  }

  # Have a column selector?
  if( !missing(col) && !(is_neg_idx)) {
    if( is.logical(col) ) { # Columns by boolean choice
      col <- which(col)     # Pick out all the TRUE columns by index
    } else if (base::is.character(col)) {
       idx <- match(col, colnames(data))
       if (any(is.na(idx)))
          stop(paste0("No column(s) '", paste(col[is.na(idx)], collapse=","), "' found in ",
            paste(colnames(data), collapse = ",")))
            col <- idx
     }
    idx <- .row.col.selector(col,envir=parent.frame()) # Generic R expression
    data <- .newExpr("cols",data,idx) # Column selector
  }

  # Have a row selector?
  if( !missing(row) && (is.H2OFrame(row) || !is.na(row)) && !(is_neg_idx)) {
    if( !is.H2OFrame(row) )    # Generic R expression
      row <- .row.col.selector(substitute(row), row,envir=parent.frame())
    data <- .newExpr("rows",data,row) # Row selector
  }

  if( is1by1 ) .fetch.data(data,1L)[[1]]
  else         data
}

#' @rdname H2OFrame-Extract
#' @param x An H2OFrame
#' @param name a literal character string or a name (possibly backtick quoted).
#' @export
`$.H2OFrame` <- function(x, name) { x[[name, exact = FALSE]] }

#' @rdname H2OFrame-Extract
#' @param i index
#' @param exact controls possible partial matching of \code{[[} when extracting
#'              a character
#' @export
`[[.H2OFrame` <- function(x, i, exact = TRUE) {
  if( missing(i) )  return(x)
  if( length(i) > 1L )  stop("`[[` can only select one column")
  if( base::is.character(i)) {
    if( exact )  i <-  match(i, colnames(x))
    else         i <- pmatch(i, colnames(x))
  }
  if( is.na(i) ) NULL
  else           x[,i]
}

#' S3 Group Generic Functions for H2O
#'
#' Methods for group generic functions and H2O objects.
#'
#' @rdname H2OFrame
#' @param e1 object
#' @param e2 object
#' @export
Ops.H2OFrame <- function(e1,e2) {

  if( missing(e2) && .Generic=="-" ) return(1-e1)
  .newExpr(.Generic,
           if( base::is.character(e1) ) .quote(e1) else e1,
           if( base::is.character(e2) ) .quote(e2) else e2)
}

#' @rdname H2OFrame
#' @param x object
#' @export
Math.H2OFrame <- function(x) .newExpr(.Generic,x)

#' @rdname H2OFrame
#' @param y object
#' @export
Math.H2OFrame <- function(x,y) .newExpr(.Generic,x,y)

#' @rdname H2OFrame
#' @param ... Further arguments passed to or from other methods.
#' @export
Math.H2OFrame <- function(x,...) .newExprList(.Generic,list(x,...))

#' @rdname H2OFrame
#' @param na.rm logical. whether or not missing values should be removed
#' @export
Summary.H2OFrame <- function(x,...,na.rm) {
#  if( na.rm ) stop("na.rm versions not impl")
  # Eagerly evaluation, to produce a scalar
  if( na.rm )
    res <- .eval.scalar(.newExprList(paste0(.Generic,"NA"),list(x,...)))
  else
    res <- .eval.scalar(.newExprList(.Generic,list(x,...)))
  if( .Generic=="all" ) as.logical(res) else res
}


#' @rdname H2OFrame
#' @export
`!.H2OFrame` <- function(x) .newExpr("!!",x)

#' @rdname H2OFrame
#' @export
is.na.H2OFrame <- function(x) .newExpr("is.na", x)

#' @rdname H2OFrame
#' @export
t.H2OFrame <- function(x) .newExpr("t",x)

#' @rdname H2OFrame
#' @export
log <- function(x, ...) {
  if( !is.H2OFrame(x) ) .Primitive("log")(x,...)
  else {
    dots <- list(...)
    base <- if (length(dots) > 0) dots[[1]] else exp(1) 
    if (base == exp(1)) .newExpr("log",x)
    else if (base == 10) .newExpr("log10",x)
    else if (base == 2) .newExpr("log2",x)
    else .newExpr("log",x) / .newExpr("log",base)
  }
}


#' @rdname H2OFrame
#' @export
log10 <- function(x) {
  if( !is.H2OFrame(x) ) .Primitive("log10")(x)
  else .newExpr("log10",x)
}

#' @rdname H2OFrame
#' @export
log2 <- function(x) {
  if( !is.H2OFrame(x) ) .Primitive("log2")(x)
  else .newExpr("log2",x)
}

#' @rdname H2OFrame
#' @export
log1p <- function(x) {
  if( !is.H2OFrame(x) ) .Primitive("log1p")(x)
  else .newExpr("log1p",x)
}

#' @rdname H2OFrame
#' @export
trunc <- function(x, ...) {
  if( !is.H2OFrame(x) ) .Primitive("trunc")(x, ...)
  else .newExpr("trunc",x)
}

#' @rdname H2OFrame
#' @export
`%*%` <- function(x, y) {
  if( !is.H2OFrame(x) ) .Primitive("%*%")(x,y)
  else .newExpr("x",x,y)
}

#' Which indices are TRUE?
#'
#' Give the TRUE indices of a logical object, allowing for array indices.
#'
#' @param x An H2OFrame object.
#' @return Returns an H2OFrame object.
#' @seealso \code{\link[base]{which}} for the base R method.
#' @examples
#' \donttest{
#' h2o.init()
#' iris.hex <- as.h2o(iris)
#' h2o.which(iris.hex[,1]==4.4)
#' }
#' @export
h2o.which <- function(x) {
  if( !is.H2OFrame(x) ) stop("must be an H2OFrame")
  else .newExpr("which",x) + 1
}

#' Which indice contains the max value?
#'
#' Get the index of the max value in a column or row
#'
#' @param x An H2OFrame object.
#' @param na.rm \code{logical}. Indicate whether missing values should be removed.
#' @param axis \code{integer}. Indicate whether to calculate the mean down a column (0) or across a row (1).
#' @return Returns an H2OFrame object.
#' @seealso \code{\link[base]{which.max}} for the base R method.
#' @export
h2o.which_max <- function(x,na.rm = TRUE,axis = 0) {
  if( !is.H2OFrame(x) ){
    stop("must be an H2OFrame")
  }
  .newExpr("which.max", chk.H2OFrame(x), na.rm, axis) + 1
}

#' @rdname h2o.which_max
#' @export
which.max.H2OFrame <- h2o.which_max

#' Which index contains the min value?
#'
#' Get the index of the min value in a column or row
#'
#' @param x An H2OFrame object.
#' @param na.rm \code{logical}. Indicate whether missing values should be removed.
#' @param axis \code{integer}. Indicate whether to calculate the mean down a column (0) or across a row (1).
#' @return Returns an H2OFrame object.
#' @seealso \code{\link[base]{which.min}} for the base R method.
#' @export
h2o.which_min <- function(x,na.rm = TRUE,axis = 0) {
  if( !is.H2OFrame(x) ) stop("must be an H2OFrame")
  else .newExpr("which.min",x,na.rm,axis) + 1
}

#' @rdname h2o.which_max
#' @export
which.min.H2OFrame <- h2o.which_min

#' Count of NAs per column
#'
#' Gives the count of NAs per column.
#'
#' @param x An H2OFrame object.
#' @return Returns a list containing the count of NAs per column
#' @examples
#' \donttest{
#' h2o.init()
#' iris.hex <- as.h2o(iris)
#' h2o.nacnt(iris.hex)  # should return all 0s
#' h2o.insertMissingValues(iris.hex)
#' h2o.nacnt(iris.hex)
#' }
#' @export
h2o.nacnt <- function(x)
  .eval.scalar(.newExpr("naCnt", x))

#' Returns the Dimensions of an H2OFrame
#'
#' Returns the number of rows and columns for an H2OFrame object.
#'
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{dim}} for the base R method.
#' @examples
#' \donttest{
#' h2o.init()
#' iris.hex <- as.h2o(iris)
#' dim(iris.hex)
#' }
#' @export
dim.H2OFrame <- function(x) { .eval.frame(x); .fetch.data(x,10L); c(attr(x, "nrow"), attr(x,"ncol")) }

#' @rdname H2OFrame
#' @export
nrow.H2OFrame <- function(x) { .fetch.data(x,10L); attr(.eval.frame(x), "nrow") }

#' @rdname H2OFrame
#' @export
ncol.H2OFrame <- function(x) { .fetch.data(x,10L); attr(.eval.frame(x), "ncol") }

#' Column names of an H2OFrame
#'
#' Set column names of an H2O Frame
#' @param x An H2OFrame
#' @examples
#' \donttest{
#' h2o.init()
#' n <- 2000
#' #  Generate variables V1, ... V10
#' X <- matrix(rnorm(10*n), n, 10)
#' #  y = +1 if sum_i x_{ij}^2 > chisq median on 10 df
#' y <- rep(-1, n)
#' y[apply(X*X, 1, sum) > qchisq(.5, 10)] <- 1
#' #  Assign names to the columns of X:
#' dimnames(X)[[2]] <- c("V1", "V2", "V3", "V4", "V5", "V6", "V7", "V8", "V9", "V10")
#' }
#' @export
dimnames.H2OFrame <- function(x) .Primitive("dimnames")(.fetch.data(x,1L))

#' Column names of an H2OFrame
#' @param x An H2OFrame
#' @export
names.H2OFrame <- function(x) .Primitive("names")(.fetch.data(x,1L))

#' Returns the column names of an H2OFrame
#'
#' @param x An H2OFrame object.
#' @param do.NULL logical. If FALSE and names are NULL, names are created.
#' @param prefix for created names.
#' @examples
#' \donttest{
#' h2o.init()
#' iris.hex <- as.h2o(iris)
#' colnames(iris)  # Returns "Sepal.Length" "Sepal.Width"  "Petal.Length" "Petal.Width"  "Species"
#' }
#' @export
colnames <- function(x, do.NULL=TRUE, prefix = "col") {
  if (is.data.frame(x)) {
    # PUBDEV-3821 workaround for slow do.NULL=F
    nm <- names(x)
    if (do.NULL || !is.null(nm))
      return(nm)
    else
      return(paste0(prefix, seq_along(x)))
  }
  if (!is.H2OFrame(x))
    return(base::colnames(x,do.NULL=do.NULL,prefix=prefix))
  return(names.H2OFrame(x))
}

#' @rdname H2OFrame
#' @export
length.H2OFrame <- function(x) { .fetch.data(x,10L); attr(.eval.frame(x),"ncol") }

#' @rdname H2OFrame
#' @export
h2o.length <- length.H2OFrame

#'
#' Return the levels from the column requested column.
#'
#' @param x An H2OFrame object.
#' @param i Optional, the index of the column whose domain is to be returned.
#' @seealso \code{\link[base]{levels}} for the base R method.
#' @examples
#' \donttest{
#' iris.hex <- as.h2o(iris)
#' h2o.levels(iris.hex, 5)  # returns "setosa"     "versicolor" "virginica"
#' }
#' @export
h2o.levels <- function(x, i) {
  df <- .fetch.data(x,1L)
  res <- list()
  if( missing(i) ) {
    for (col in 1:ncol(df)) {
      res <- c(res, list(levels(df[[col]])))
    }
    if (length(res) == 1) res <- res[[1]]
  }
  else res <- levels(df[[i]])
  res
}


#'
#' Get the number of factor levels for this frame.
#'
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{nlevels}} for the base R method.
#' @export
h2o.nlevels <- function(x) {
  levels <- h2o.levels(x)
  if (!is.list(levels)) length(levels)
  else lapply(levels,length)
}


#'
#' Set Levels of H2O Factor Column
#'
#' Works on a single categorical vector. New domains must be aligned with the old domains.
#' This call has SIDE EFFECTS and mutates the column in place (change of the levels will also affect all the frames
#' that are referencing this column). If you want to make a copy of the column instead, use parameter in.place = FALSE.
#'
#' @param x A single categorical column.
#' @param levels A character vector specifying the new levels. The number of new levels must match the number of old levels.
#' @param in.place Indicates whether new domain will be directly applied to the column (in place change) or if a copy
#'        of the column will be created with the given domain levels.
#' @export
#' @examples
#' \donttest{
#' h2o.init()
#' iris.hex <- as.h2o(iris)
#' new.levels <- c("setosa", "versicolor", "caroliniana")
#' iris.hex$Species <- h2o.setLevels(iris.hex$Species, new.levels, in.place = FALSE)
#' h2o.levels(iris.hex$Species)
#' }
h2o.setLevels <- function(x, levels, in.place = TRUE) .newExpr("setDomain", chk.H2OFrame(x), in.place, levels)


#'
#' Return the Head or Tail of an H2O Dataset.
#'
#' Returns the first or last rows of an H2OFrame object.
#'
#' @name h2o.head
#' @param x An H2OFrame object.
#' @param n (Optional) A single integer. If positive, number of rows in x to return. If negative, all but the n first/last number of rows in x.
#' @param ... Ignored.
#' @return An H2OFrame containing the first or last n rows of an H2OFrame object.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init(ip <- "localhost", port = 54321, startH2O = TRUE)
#' ausPath <- system.file("extdata", "australia.csv", package="h2o")
#' australia.hex <- h2o.uploadFile(path = ausPath)
#' head(australia.hex, 10)
#' tail(australia.hex, 10)
#' }
#' @export
h2o.head <- function(x,n=6L,...) {
  stopifnot(length(n) == 1L)
  n <- if (n < 0L) max(nrow(x) + n, 0L)
       else        min(n, nrow(x))
  if( n >= 0L && n <= 1000L ) # Short version, just report the cached internal DF
    head(.fetch.data(x,n),n)
  else # Long version, fetch all asked for "the hard way"
    as.data.frame(.newExpr("rows",x,paste0("[0:",n,"]")))
}

#' @rdname h2o.head
#' @export
head.H2OFrame <- h2o.head

#' @rdname h2o.head
#' @export
h2o.tail <- function(x,n=6L,...) {
  endidx <- nrow(x)
  n <- ifelse(n < 0L, max(endidx + n, 0L), min(n, endidx))
  if( n==0L ) head(x,n=0L)
  else {
    startidx <- max(1L, endidx - n + 1)
    .fetch.data(.newExpr("rows",x,paste0("[",startidx-1,":",(endidx-startidx+1),"]")),n)
  }
}

#' @rdname h2o.head
#' @export
tail.H2OFrame <- h2o.tail

#' Check if factor
#'
#' @rdname is.factor
#' @param x An H2OFrame object
#' @export
is.factor <- function(x) {
  # Eager evaluate and use the cached result to return a scalar
  if( is.H2OFrame(x) ) {
    sapply(.eval.scalar(.newExpr("is.factor", x)), as.logical)
  } else {
    base::is.factor(x)
  }
}

#' Check if numeric
#'
#' @rdname is.numeric
#' @param x An H2OFrame object
#' @export
is.numeric <- function(x) {
  if( !is.H2OFrame(x) ) .Primitive("is.numeric")(x)
  else sapply(.eval.scalar(.newExpr("is.numeric", x)), as.logical)
}

#' Check if character
#'
#' @rdname is.character
#' @param x An H2OFrame object
#' @export
is.character <- function(x) {
  if( !is.H2OFrame(x) ) .Primitive("is.character")(x)
  else sapply(.eval.scalar(.newExpr("is.character", x)), as.logical)
}

#' Print An H2OFrame
#'
#' @param x An H2OFrame object
#' @param n An (Optional) A single integer. If positive, number of rows in x to return. If negative, all but the n first/last number of rows in x.
#'          Anything bigger than 20 rows will require asking the server (first 20 rows are cached on the client).
#' @param ... Further arguments to be passed from or to other methods.
#' @export
print.H2OFrame <- function(x,n=6L, ...) {
  print(head(x,n))
  rowString = if (nrow(x) > 1) " rows x " else " row x "
  colString = if (ncol(x) > 1) " columns]" else " column]"
  cat(paste0("\n[", nrow(x), rowString, ncol(x), colString), "\n")
}

#' Display the structure of an H2OFrame object
#'
#' @param object An H2OFrame.
#' @param ... Further arguments to be passed from or to other methods.
#' @param cols Print the per-column str for the H2OFrame
#' @importFrom utils str
#' @export
str.H2OFrame <- function(object, ..., cols=FALSE) {
  if (length(l <- list(...)) && any("give.length" == names(l)))
    invisible(NextMethod("str", ...))
  else if( !cols ) invisible(NextMethod("str", give.length = FALSE, ...))

  if( cols ) {
    nc <- ncol(object)
    nr <- nrow(object)
    cc <- colnames(object)
    width <- max(nchar(cc))
    df <- head(.fetch.data(object,10L),10L)

    # header statement
    cat("\nH2OFrame '", attr(object, "id"), "':\t", nr, " obs. of  ", nc, " variable(s)", "\n", sep = "")
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
}

#' @rdname H2OFrame-Extract
#' @export
`$.H2OFrame` <- function(x, name) { x[[name, exact = FALSE]] }

#' @rdname H2OFrame-Extract
#' @export
`[[.H2OFrame` <- function(x, i, exact = TRUE) {
  if( missing(i) )  return(x)
  if( length(i) > 1L )  stop("`[[` can only select one column")
  if( base::is.character(i)) {
    if( exact )  i <-  match(i, colnames(x))
    else         i <- pmatch(i, colnames(x))
  }
  if( is.na(i) ) NULL
  else           x[,i]
}


#-----------------------------------------------------------------------------------------------------------------------
# Assignment Operations: [<-, $<-, [[<-, colnames<-, names<-
#-----------------------------------------------------------------------------------------------------------------------
#' @rdname H2OFrame-Extract
#' @param ... Further arguments passed to or from other methods.
#' @param value To be assigned
#' @export
`[<-.H2OFrame` <- function(data,row,col,...,value) {
  chk.H2OFrame(data)
  allRow <- missing(row)
  allCol <- missing(col)
  if( !allCol && is.na(col) ) col <- as.list(match.call())$col

  # Named column assignment; the column name was passed in as "row"
  # fr["baz"] <- qux
  # fr$ baz   <- qux
  if( !allRow && base::is.character(row) && allCol ) {
    allRow <- TRUE
    allCol <- FALSE
    col <- row
  }

  if(!allRow && !is.numeric(row))
    stop("`row` must be missing or a numeric vector")
  if(!allCol && !is.numeric(col) && !base::is.character(col))
    stop("`col` must be missing or a numeric or character vector")
  if( !is.null(value) && !is.H2OFrame(value) ) {
    if( is.na(value) ) value <- NA_integer_  # pick an NA... any NA (the damned numeric one will do)
    else if( !is.numeric(value) && !base::is.character(value) )
      stop("`value` can only be an H2OFrame object or a numeric or character vector")
  }

  # Row arg is missing, means "all the rows"
  if(allRow) rows <- paste0("[]")  # Shortcut for "all rows"
  else {
    if( !is.H2OFrame(row) )    # Generic R expression
      rows <- .row.col.selector(substitute(row), row,envir=parent.frame())
    else
      rows <- row
  }

  name <- NA
  if( allCol ) {   # Col arg is missing, means "all the cols"
    cols <- paste0("[]")  # Shortcut for "all cols"
  } else {
    if( base::is.character(col) ) {
      idx <- match(col, colnames(data))
      if( any(is.na(idx)) ) { # Any unknown names?
        if( length(col) > 1 ) stop("unknown column names")
        else { idx <- ncol(data)+1; name <- col } # Append 1 unknown column
      }
    } else idx <- col
    if( is.null(value) ) return(`[.H2OFrame`(data,row=-idx)) # Assign a null: delete by selecting inverse columns
      if( idx==(ncol(data)+1) && is.na(name) ) name <- paste0("C",idx)
    cols <- .row.col.selector(idx, envir=parent.frame())
  }

  if( base::is.character(value) ) value <- .quote(value)
  # Set col name and return updated frame
  if( is.na(name) ) .newExpr(":=", data, value, cols, rows)
  else              .newExpr("append", data, value, .quote(name))
}

#' @rdname H2OFrame-Extract
#' @export
`$<-.H2OFrame`  <- function(data, name, value) `[<-.H2OFrame`(data,row=name,value=value)

#' @rdname H2OFrame-Extract
#' @export
`[[<-.H2OFrame` <- function(data, name, value) `[<-.H2OFrame`(data,row=name,value=chk.H2OFrame(value))

#' @rdname H2OFrame
#' @param value To be assigned
#' @export
`names<-.H2OFrame` <- function(x, value) {
  .newExpr("colnames=", x, paste0("[0:",ncol(x),"]"), .str.list(value))
}

#' @rdname H2OFrame
#' @export
`colnames<-` <- function(x, value) {
  if( !is.H2OFrame(x) ) return(base::`colnames<-`(x,value))
  return(`names<-.H2OFrame`(x,if( is.H2OFrame(value) ) colnames(value) else value))
}


#-----------------------------------------------------------------------------------------------------------------------
# Summary Statistics Operations
#-----------------------------------------------------------------------------------------------------------------------

#'
#' Summarizes the columns of an H2OFrame.
#'
#' A method for the \code{\link{summary}} generic. Summarizes the columns of an H2O data frame or subset of
#' columns and rows using vector notation (e.g. dataset[row, col]).
#'
#' By default it uses approximated version of quantiles computation, however, user can modify
#' this behavior by setting up exact_quantiles argument to true.
#'
#' @name h2o.summary
#' @param object An H2OFrame object.
#' @param factors The number of factors to return in the summary. Default is the top 6.
#' @param exact_quantiles Compute exact quantiles or use approximation. Default is to use approximation.
#' @param ... Further arguments passed to or from other methods.
#' @return A table displaying the minimum, 1st quartile, median, mean, 3rd quartile and maximum for each
#' numeric column, and the levels and category counts of the levels in each categorical column.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.importFile(path = prosPath)
#' summary(prostate.hex)
#' summary(prostate.hex$GLEASON)
#' summary(prostate.hex[,4:6])
#' summary(prostate.hex, exact_quantiles=TRUE)
#' }
#' @export
h2o.summary <- function(object, factors=6L, exact_quantiles=FALSE, ...) {
  SIG.DIGITS    <- 12L
  FORMAT.DIGITS <- 4L
  cnames <- colnames(object)
  missing <- list()

  # for each numeric column, collect [min,1Q,median,mean,3Q,max]
  # for each categorical column, collect the first 6 domains
  # allow for optional parameter in ... factors=N, for N domain levels. Or could be the string "all". N=6 by default.
  fr.sum <- .h2o.__remoteSend(paste0("Frames/", attr(object, "id"), "/summary"), method = "GET")$frames[[1]]
  col.sums <- fr.sum$columns
  default_percentiles <- fr.sum$default_percentiles
  cols <- sapply(col.sums, function(col) {
    col.sum <- col
  col.type <- col.sum$type  # enum, string, int, real, time, uuid

  # numeric column: [min,1Q,median,mean,3Q,max]
  if( col.type %in% c("real", "int") ) {
    cmin <- cmax <- cmean <- c1Q <- cmedian <- c3Q <- NaN                                              # all 6 values are NaN by default
  if( !(is.null(col.sum$mins) || length(col.sum$mins) == 0L) ) cmin <- min(col.sum$mins,na.rm=TRUE)  # set the min
  if( !(is.null(col.sum$maxs) || length(col.sum$maxs) == 0L) ) cmax <- max(col.sum$maxs,na.rm=TRUE)  # set the max
  if( !(is.null(col.sum$mean))                               ) cmean<- col.sum$mean                  # set the mean

  if (exact_quantiles) {
    quantiles <- h2o.quantile(object[col.sum$label],c(.25,.5,.75)) # set the 1st quartile, median, and 3rd quartile
  if( !is.null(quantiles) ) {
    c1Q     <- quantiles[1]
  cmedian <- quantiles[2]
  c3Q     <- quantiles[3]
  }
  } else {
    indexes <- which(default_percentiles == 0.25 | default_percentiles == 0.5 | default_percentiles == 0.75)
  values <- col.sum$percentiles[indexes]
  c1Q     <- values[1]
  cmedian <- values[2]
  c3Q     <- values[3]
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
  histo <- col.sum$histogram_bins
  base <- col.sum$histogram_base
  domain.cnts <- numeric(length(domains))
  for( i in 1:length(histo) )
  domain.cnts[i+base] <- histo[i]
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
  if( !is.null(missing.count) && !is.na(missing.count) && missing.count > 0L ) df.domains.subset <- rbind( df.domains.subset, c("NA", missing.count))

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
  if( is.null(result) || dim(result) == 0 ) return(NULL)
  colnames(result) <- cnames
  rownames(result) <- rep("", nrow(result))
  # Print warning if approx quantiles are computed
  if (!exact_quantiles) {
    warning("Approximated quantiles computed! If you are interested in exact quantiles, please pass the `exact_quantiles=TRUE` parameter.")
  }
  result
}

#' H2O Description of A Dataset
#'
#' Reports the "Flow" style summary rollups on an instance of H2OFrame. Includes
#' information about column types, mins/maxs/missing/zero counts/stds/number of levels
#'
#' @name h2o.describe
#' @param frame An H2OFrame object.
#' @return A table with the Frame stats.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.importFile(path = prosPath)
#' h2o.describe(prostate.hex)
#' }
#' @export
h2o.describe <- function(frame) {
  fr.sum <- .h2o.__remoteSend(paste0("Frames/", h2o.getId(frame), "/summary"), method = "GET", `_exclude_fields`="frames/columns/data,frames/columns/domain,frames/columns/histogram_bins,frames/columns/percentiles")$frames[[1]]
  res <- data.frame(t(sapply(fr.sum$columns, function(col) {
                                                          c(col$label,
                                                          col$type,
                                                          col$missing_count,
                                                          col$zero_count,
                                                          col$positive_infinity_count,
                                                          col$negative_infinity_count,
                                                          col$mins[1],
                                                          col$maxs[1],
                                                          ifelse(col$mean=="NaN", NA, col$mean),
                                                          ifelse(col$sigma=="NaN",NA, col$sigma),
                                                          ifelse(col$type=="enum", col$domain_cardinality, NA)
                                                          )
                                                          })))
  names(res) <- c("Label", "Type", "Missing", "Zeros", "PosInf", "NegInf", "Min", "Max", "Mean", "Sigma", "Cardinality")

  res2 <- apply(res[,3:ncol(res)], 2, as.numeric)
  res2 <- cbind(res[,1:2], res2)
  return(res2)
}

#' @rdname h2o.summary
#' @usage \method{summary}{H2OFrame}(object, factors, exact_quantiles, ...)
#' @method summary H2OFrame
#' @export
summary.H2OFrame <- h2o.summary

#' H2O Median
#'
#' Compute the median of an H2OFrame.
#'
#' @param x An H2OFrame object.
#' @param na.rm a logical, indicating whether na's are omitted.
#' @return Returns a list containing the median for each column (NaN for non-numeric columns)
#' @examples
#' \donttest{
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath, destination_frame = "prostate.hex")
#' h2o.median(prostate.hex)
#' }
#' @export
h2o.median <- function(x, na.rm = TRUE) .eval.scalar(.newExpr("median",x,na.rm))

#' @rdname h2o.median
median.H2OFrame <- h2o.median

#'
#' Compute the frame's mean by-column (or by-row).
#'
#' @name h2o.mean
#' @param x An H2OFrame object.
#' @param na.rm \code{logical}. Indicate whether missing values should be removed.
#' @param axis \code{integer}. Indicate whether to calculate the mean down a column (0) or across a row (1).
#'                             NOTE: This is only applied when return_frame is set to TRUE. Otherwise, this parameter
#'                             is ignored.
#' @param return_frame \code{logical}. Indicate whether to return an H2O frame or a list. Default is FALSE (returns a list).
#' @param ... Further arguments to be passed from or to other methods.
#' @seealso \code{\link[base]{mean}} , \code{\link[base]{rowMeans}}, or \code{\link[base]{colMeans}} for the base R implementation
#' @return Returns a list containing the mean for each column (NaN for non-numeric columns) if return_frame is set to FALSE.
#'         If return_frame is set to TRUE, then it will return an H2O frame with means per column or row (depends on axis argument).
#' @examples
#' \donttest{
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath)
#' # Default behavior. Will return list of means per column.
#' h2o.mean(prostate.hex$AGE)
#' # return_frame set to TRUE. This will return an H2O Frame
#' # with mean per row or column (depends on axis argument)
#' h2o.mean(prostate.hex,na.rm=TRUE,axis=1,return_frame=TRUE)
#' }
#' @export
h2o.mean <- function(x, na.rm = FALSE, axis = 0, return_frame = FALSE, ...) {
  if(return_frame){
    .newExpr("mean", chk.H2OFrame(x), na.rm, axis)
  }else{
    .eval.scalar(.newExpr("getrow", .newExpr("mean",x,na.rm)))
  }
}

#' @rdname h2o.mean
#' @export
mean.H2OFrame <- h2o.mean

#'
#' Skewness of a column
#'
#' Obtain the skewness of a column of a parsed H2O data object.
#'
#' @name h2o.skewness
#' @param x An H2OFrame object.
#' @param ... Further arguments to be passed from or to other methods.
#' @param na.rm A logical value indicating whether \code{NA} or missing values should be stripped before the computation.
#' @return Returns a list containing the skewness for each column (NaN for non-numeric columns).
#' @examples
#' \donttest{
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath)
#' h2o.skewness(prostate.hex$AGE)
#' }
#' @export
h2o.skewness <- function(x, ...,na.rm=TRUE) .eval.scalar(.newExpr("skewness",x,na.rm))

#' @rdname h2o.skewness
#' @export
skewness.H2OFrame <- h2o.skewness

#'
#' Kurtosis of a column
#'
#' Obtain the kurtosis of a column of a parsed H2O data object.
#'
#' @name h2o.kurtosis
#' @param x An H2OFrame object.
#' @param ... Further arguments to be passed from or to other methods.
#' @param na.rm A logical value indicating whether \code{NA} or missing values should be stripped before the computation.
#' @return Returns a list containing the kurtosis for each column (NaN for non-numeric columns).
#' @examples
#' \donttest{
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath)
#' h2o.kurtosis(prostate.hex$AGE)
#' }
#' @export
h2o.kurtosis <- function(x, ...,na.rm=TRUE) .eval.scalar(.newExpr("kurtosis",x,na.rm))

#' @rdname h2o.kurtosis
#' @export
kurtosis.H2OFrame <- h2o.kurtosis

#
#" Mode of a enum or int column.
#" Returns single string or int value or an array of strings and int that are tied.
# TODO: figure out functionality/use for documentation
# h2o.mode <-
# function(x) {
#  if(!is(x, "H2OFrame")) || nrow(x) > 1L) stop('`x` must be an H2OFrame object')
# tabularx = invisible(table(x))
#  maxCount = max(tabularx$Count)
#  modes = tabularx$row.names[tabularx$Count == maxCount]
#  return(unlist(as.list(as.matrix(modes))))
#}

#'
#' Variance of a column or covariance of columns.
#'
#' Compute the variance or covariance matrix of one or two H2OFrames.
#'
#' @param x An H2OFrame object.
#' @param y \code{NULL} (default) or an H2OFrame. The default is equivalent to y = x.
#' @param na.rm \code{logical}. Should missing values be removed?
#' @param use An optional character string indicating how to handle missing values. This must be one of the following: 
#'   "everything"            - outputs NaNs whenever one of its contributing observations is missing
#'   "all.obs"               - presence of missing observations will throw an error
#'   "complete.obs"          - discards missing values along with all observations in their rows so that only complete observations are used
#' @seealso \code{\link[stats]{var}} for the base R implementation. \code{\link{h2o.sd}} for standard deviation.
#' @examples
#' \donttest{
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath)
#' var(prostate.hex$AGE)
#' }
#' @export
h2o.var <- function(x, y = NULL, na.rm = FALSE, use) {
  symmetric <- FALSE
  if( is.null(y) ) {
    y <- x
    symmetric <- TRUE
  }
  if(missing(use)) {
    if (na.rm) use <- "complete.obs" else use <- "everything"
  }
  # Eager, mostly to match prior semantics but no real reason it need to be
  expr <- .newExpr("var",x,y,.quote(use),symmetric)
  if( (nrow(x)==1L || (ncol(x)==1L && ncol(y)==1L)) ) .eval.scalar(expr)
  else .fetch.data(expr,ncol(x))
}

#' @rdname h2o.var
#' @export
var <- function(x, y = NULL, na.rm = FALSE, use)  {
  if( is.H2OFrame(x) ) h2o.var(x,y,na.rm,use)
  else stats::var(x,y,na.rm,use)
}

#'
#' Correlation of columns.
#'
#' Compute the correlation matrix of one or two H2OFrames.
#'
#' @param x An H2OFrame object.
#' @param y \code{NULL} (default) or an H2OFrame. The default is equivalent to y = x.
#' @param na.rm \code{logical}. Should missing values be removed?
#' @param use An optional character string indicating how to handle missing values. This must be one of the following:
#'   "everything"            - outputs NaNs whenever one of its contributing observations is missing
#'   "all.obs"               - presence of missing observations will throw an error
#'   "complete.obs"          - discards missing values along with all observations in their rows so that only complete observations are used
#' @examples
#' \donttest{
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath)
#' cor(prostate.hex$AGE)
#' }
#' @export
h2o.cor <- function(x, y=NULL,na.rm = FALSE, use){
  # Eager, mostly to match prior semantics but no real reason it need to be
  if( is.null(y) ){
    y <- x
  }
  if(missing(use)) {
    if (na.rm) use <- "complete.obs" else use <- "everything"
  }
  # Eager, mostly to match prior semantics but no real reason it need to be
  expr <- .newExpr("cor",x,y,.quote(use))
  if( (nrow(x)==1L || (ncol(x)==1L && ncol(y)==1L)) ) .eval.scalar(expr)
  else .fetch.data(expr,ncol(x))
}

#'
#' Compute a pairwise distance measure between all rows of two numeric H2OFrames.
#'
#' @param x An H2OFrame object (large, references).
#' @param y An H2OFrame object (small, queries).
#' @param measure An optional string indicating what distance measure to use. Must be one of:
#'   "l1"                   - Absolute distance (L1-norm, >=0)
#'   "l2"                   - Euclidean distance (L2-norm, >=0)
#'   "cosine"               - Cosine similarity (-1...1)
#'   "cosine_sq"            - Squared Cosine similarity (0...1)
#' @examples
#' \donttest{
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath)
#' h2o.distance(prostate.hex[11:30,], prostate.hex[1:10,], "cosine")
#' }
#' @export
h2o.distance <- function(x, y, measure){
  if(missing(measure)) {
    measure <- "l2"
  }
  .newExpr("distance",x,y,.quote(measure))
}

#' @rdname h2o.cor
#' @param ... Further arguments to be passed down from other methods.
#' @export
cor <- function (x, ...)
{
    if (is.H2OFrame(x))
        h2o.cor(x, ...)
    else stats::cor(x, ...)
}

#'
#' Standard Deviation of a column of data.
#'
#' Obtain the standard deviation of a column of data.
#'
#' @name h2o.sd
#' @param x An H2OFrame object.
#' @param na.rm \code{logical}. Should missing values be removed?
#' @seealso \code{\link{h2o.var}} for variance, and \code{\link[stats]{sd}} for the base R implementation.
#' @examples
#' \donttest{
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath)
#' sd(prostate.hex$AGE)
#' }
#' @export
h2o.sd <- function(x, na.rm = FALSE) {
  if( ncol(x)==1L ) .eval.scalar(.newExpr("sd",x, na.rm))
  else .fetch.data(.newExpr("sd",x,na.rm),1L)
}

#' @rdname h2o.sd
#' @export
sd <- function(x, na.rm=FALSE) {
  if( is.H2OFrame(x) ) h2o.sd(x,na.rm)
  else stats::sd(x,na.rm)
}

#'
#' Round doubles/floats to the given number of significant digits.
#'
#' @name h2o.signif
#' @param x An H2OFrame object.
#' @param digits Number of significant digits to round doubles/floats.
#' @seealso \code{\link[base]{signif}} for the base R implementation.
#' @export
h2o.signif <- function(x, digits=6) .newExpr("signif",chk.H2OFrame(x),digits)

#' @rdname h2o.signif
#' @export
signif <- function(x, digits=6) {
  if( is.H2OFrame(x) ) h2o.signif(x,digits)
  else base::signif(x,digits)
}

#'
#' Round doubles/floats to the given number of decimal places.
#'
#' @name h2o.round
#' @param x An H2OFrame object.
#' @param digits Number of decimal places to round doubles/floats. Rounding to a negative number of decimal places is 
#         not supported. For rounding off a 5, the IEC 60559 standard is used, 'go to the even digit'. Therefore 
#         rounding 2.5 gives 2 and rounding 3.5 gives 4.
#' @seealso \code{\link[base]{round}} for the base R implementation.
#' @export
h2o.round <- function(x, digits=0) .newExpr("round",chk.H2OFrame(x),digits)


#' @rdname h2o.round
#' @export
round <- function(x, digits=0) {
  if( is.H2OFrame(x) ) h2o.round(x,digits)
  else base::round(x,digits)
}

#'
#' Scaling and Centering of an H2OFrame
#'
#' Centers and/or scales the columns of an H2O dataset.
#'
#' @name h2o.scale
#' @param x An H2OFrame object.
#' @param center either a \code{logical} value or numeric vector of length equal to the number of columns of x.
#' @param scale either a \code{logical} value or numeric vector of length equal to the number of columns of x.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' irisPath <- system.file("extdata", "iris_wheader.csv", package="h2o")
#' iris.hex <- h2o.uploadFile(path = irisPath, destination_frame = "iris.hex")
#' summary(iris.hex)
#'
#' # Scale and center all the numeric columns in iris data set
#' scale(iris.hex[, 1:4])
#' }
#' @export
h2o.scale <- function(x, center = TRUE, scale = TRUE) .newExpr("scale", chk.H2OFrame(x), center, scale)

#' @rdname h2o.scale
#' @export
scale.H2OFrame <- h2o.scale

#-----------------------------------------------------------------------------------------------------------------------
# Below takes H2O primitives that do not start with "h2o.*" and appends "h2o.*" to ensure all H2O primitives exist
# with "h2o.*" in addition to original implementation.
#
#    log10, log2, log1p, trunc, dim, dimname, names, colnames, is.factor, is.numeric, is.character,
#    print, str, as.numeric, as.character, as.factor, cos, sin, acos, cosh, tan, tanh, exp, log,
#    sqrt, abs, ceiling, floor, mean, sd, sum, prod, all, any, min, max, nrow, ncol, and range
#-----------------------------------------------------------------------------------------------------------------------

#'
#' Compute the log10 of x
#'
#' @name h2o.log10
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{log10}} for the base R implementation.
#' @export
h2o.log10 <- function(x) {
  log10(x)
}

#'
#' Compute the log2 of x
#'
#' @name h2o.log2
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{log2}} for the base R implementation.
#' @export
h2o.log2 <- function(x) {
  log2(x)
}

#'
#' Compute the log1p of x
#'
#' @name h2o.log1p
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{log1p}} for the base R implementation.
#' @export
h2o.log1p <- function(x) {
  log1p(x)
}

#' Truncate values in x toward 0
#' 
#' trunc takes a single numeric argument x and returns a numeric vector containing the integers
#' formed by truncating the values in x toward 0.
#'
#' @name h2o.trunc
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{trunc}} for the base R implementation.
#' @export
h2o.trunc <- function(x) {
  trunc(x)
}

#'
#' Returns the number of rows and columns for an H2OFrame object.
#'
#' @name h2o.dim
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{dim}} for the base R implementation.
#' @export
h2o.dim <- function(x) {
  dim(x)
}

#'
#' Column names of an H2OFrame
#'
#' @name h2o.dimnames
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{dimnames}} for the base R implementation.
#' @export
h2o.dimnames <- function(x) {
  dimnames(x)
}

#'
#' Column names of an H2OFrame
#'
#' @name h2o.names
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{names}} for the base R implementation.
#' @export
h2o.names <- function(x) {
  names(x)
}

#'
#' Return column names of an H2OFrame
#'
#' @name h2o.colnames
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{colnames}} for the base R implementation.
#' @export
h2o.colnames <- function(x) {
  colnames(x)
}

#'
#' Check if factor
#'
#' @name h2o.isfactor
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{is.factor}} for the base R implementation.
#' @export
h2o.isfactor <- function(x) {
  is.factor(x)
}

#'
#' Check if numeric
#'
#' @name h2o.isnumeric
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{is.numeric}} for the base R implementation.
#' @export
h2o.isnumeric <- function(x) {
  is.numeric(x)
}

#'
#' Check if character
#'
#' @name h2o.ischaracter
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{is.character}} for the base R implementation.
#' @export
h2o.ischaracter <- function(x) {
  is.character(x)
}

#'
#' Convert H2O Data to Factors
#'
#' @name h2o.asfactor
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{as.factor}} for the base R implementation.
#' @export
h2o.asfactor <- function(x) {
  as.factor(x)
}

#'
#' Convert H2O Data to Numerics
#'
#' @name h2o.asnumeric
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{as.numeric}} for the base R implementation.
#' @export
h2o.asnumeric <- function(x) {
  as.numeric(x)
}

#'
#' Convert H2O Data to Characters
#'
#' @name h2o.ascharacter
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{as.character}} for the base R implementation.
#' @export
h2o.ascharacter <- function(x) {
  as.character(x)
}

#' Print An H2OFrame
#'
#' @param x An H2OFrame object
#' @param n An (Optional) A single integer. If positive, number of rows in x to return. If negative, all but the n first/last number of rows in x.
#'          Anything bigger than 20 rows will require asking the server (first 20 rows are cached on the client).
#' @param ... Further arguments to be passed from or to other methods.
#' @export
h2o.print <- function(x,n=6L) {
  print(x,n = n)
}

#' Display the structure of an H2OFrame object
#'
#' @param object An H2OFrame.
#' @param ... Further arguments to be passed from or to other methods.
#' @param cols Print the per-column str for the H2OFrame
#' @export
h2o.str <- function(object, ..., cols=FALSE) {
  str(object, ..., cols = cols)
}

#'
#' Compute the cosine of x
#'
#' @name h2o.cos
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{cos}} for the base R implementation.
#' @export
h2o.cos <- function(x) {
  cos(x)
}

#'
#' Compute the sine of x
#'
#' @name h2o.sin
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{sin}} for the base R implementation.
#' @export
h2o.sin <- function(x) {
  sin(x)
}

#'
#' Compute the arc cosine of x
#'
#' @name h2o.acos
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{acos}} for the base R implementation.
#' @examples
#' \donttest{
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath)
#' h2o.acos(prostate.hex[,2])
#' }
#' @export
h2o.acos <- function(x) {
  acos(x)
}

#'
#' Compute the hyperbolic cosine of x
#'
#' @name h2o.cosh
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{cosh}} for the base R implementation.
#' @export
h2o.cosh <- function(x) {
  cosh(x)
}

#'
#' Compute the tangent of x
#'
#' @name h2o.tan
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{tan}} for the base R implementation.
#' @export
h2o.tan <- function(x) {
  tan(x)
}

#'
#' Compute the hyperbolic tangent of x
#'
#' @name h2o.tanh
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{tanh}} for the base R implementation.
#' @export
h2o.tanh <- function(x) {
  tanh(x)
}

#'
#' Compute the exponential function of x
#'
#' @name h2o.exp
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{exp}} for the base R implementation.
#' @export
h2o.exp <- function(x) {
  exp(x)
}

#'
#' Compute the logarithm of x
#'
#' @name h2o.log
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{log}} for the base R implementation.
#' @export
h2o.log <- function(x) {
  log(x)

}

#'
#' Compute the square root of x
#'
#' @name h2o.sqrt
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{sqrt}} for the base R implementation.
#' @export
h2o.sqrt <- function(x) {
  sqrt(x)
}

#'
#' Compute the absolute value of x
#'
#' @name h2o.abs
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{abs}} for the base R implementation.
#' @examples
#' \donttest{
#' h2o.init()
#' url <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/smtrees.csv"
#' smtreesH2O <- h2o.importFile(url)
#' smtreesR <- read.csv("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/smtrees.csv")
#' fith2o <- h2o.gbm(x=c("girth", "height"), y="vol", ntrees=3, max_depth=1, distribution="gaussian", 
#'                  min_rows=2, learn_rate=.1, training_frame=smtreesH2O)
#' pred <- as.data.frame(predict(fith2o, newdata=smtreesH2O))
#' diff <- pred-smtreesR[,4]
#' diff_abs <- abs(diff)
#' print(diff_abs)
#' }
#' @export
h2o.abs <- function(x) {
  abs(x)
}

#' Take a single numeric argument and return a numeric vector with the smallest integers
#'
#' ceiling takes a single numeric argument x and returns a
#' numeric vector containing the smallest integers not less than the
#' corresponding elements of x.
#'
#' @name h2o.ceiling
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{ceiling}} for the base R implementation.
#' @export
h2o.ceiling <- function(x) {
  ceiling(x)
}

#' Take a single numeric argument and return a numeric vector with the largest integers
#'
#' floor takes a single numeric argument x and returns a numeric
#' vector containing the largest integers not greater than the
#' corresponding elements of x.
#'
#' @name h2o.floor
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{floor}} for the base R implementation.
#' @export
h2o.floor <- function(x) {
  floor(x)
}

#'
#' Compute the frame's sum by-column (or by-row).
#'
#' @name h2o.sum
#' @param x An H2OFrame object.
#' @param na.rm \code{logical}. indicating whether missing values should be removed.
#' @param axis An int that indicates whether to do down a column (0) or across a row (1).
#' @param return_frame A boolean that indicates whether to return an H2O frame or one single aggregated value. Default is FALSE.
#' @seealso \code{\link[base]{sum}} for the base R implementation.
#' @export
h2o.sum <- function(x, na.rm = FALSE, axis = 0, return_frame = FALSE) {
   if(return_frame){
      .newExpr("sumaxis", chk.H2OFrame(x), na.rm, axis)
  }else{
    sum(x,na.rm = na.rm)
  }
}

#'
#' Return the product of all the values present in its arguments.
#'
#' @name h2o.prod
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{prod}} for the base R implementation.
#' @export
h2o.prod <- function(x) {
  prod(x)
}

#'
#' Return the cumulative sum over a column or across a row
#'
#' @name h2o.cumsum
#' @param x An H2OFrame object.
#' @param axis An int that indicates whether to do down a column (0) or across a row (1).
#' @seealso \code{\link[base]{cumsum}} for the base R implementation.
#' @export
h2o.cumsum <- function(x, axis = 0){
  .newExpr("cumsum", chk.H2OFrame(x), axis)
}

#'
#' Return the cumulative product over a column or across a row
#'
#' @name h2o.cumprod
#' @param x An H2OFrame object.
#' @param axis An int that indicates whether to do down a column (0) or across a row (1).
#' @seealso \code{\link[base]{cumprod}} for the base R implementation.
#' @export
h2o.cumprod <- function(x, axis = 0){
  .newExpr("cumprod", chk.H2OFrame(x), axis)
}

#'
#' Return the cumulative min over a column or across a row
#'
#' @name h2o.cummin
#' @param x An H2OFrame object.
#' @param axis An int that indicates whether to do down a column (0) or across a row (1).
#' @seealso \code{\link[base]{cummin}} for the base R implementation.
#' @export
h2o.cummin <- function(x, axis = 0){
  .newExpr("cummin", chk.H2OFrame(x), axis)
}

#'
#' Return the cumulative max over a column or across a row
#'
#' @name h2o.cummax
#' @param x An H2OFrame object.
#' @param axis An int that indicates whether to do down a column (0) or across a row (1).
#' @seealso \code{\link[base]{cummax}} for the base R implementation.
#' @export
h2o.cummax <- function(x, axis = 0){
  .newExpr("cummax", chk.H2OFrame(x), axis)
}

#'
#' Given a set of logical vectors, are all of the values true?
#'
#' @name h2o.all
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{all}} for the base R implementation.
#' @export
h2o.all <- function(x) {
  all(x)
}

#'
#' Given a set of logical vectors, is at least one of the values true?
#'
#' @name h2o.any
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{all}} for the base R implementation.
#' @export
h2o.any <- function(x) {
  any(x)
}

#'
#' Returns the minima of the input values.
#'
#' @name h2o.min
#' @param x An H2OFrame object.
#' @param na.rm \code{logical}. indicating whether missing values should be removed.
#' @seealso \code{\link[base]{min}} for the base R implementation.
#' @export
h2o.min <- function(x,na.rm = FALSE) {
  min(x,na.rm = na.rm)
}

#'
#' Returns the maxima of the input values.
#'
#' @name h2o.max
#' @param x An H2OFrame object.
#' @param na.rm \code{logical}. indicating whether missing values should be removed.
#' @seealso \code{\link[base]{max}} for the base R implementation.
#' @export
h2o.max <- function(x,na.rm = FALSE) {
  max(x,na.rm = na.rm)
}

#'
#' Return the number of rows present in x.
#'
#' @name h2o.nrow
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{nrow}} for the base R implementation.
#' @export
h2o.nrow <- function(x) {
 nrow(x)
}

#'
#' Return the number of columns present in x.
#'
#' @name h2o.ncol
#' @param x An H2OFrame object.
#' @seealso \code{\link[base]{ncol}} for the base R implementation.
#' @export
h2o.ncol <- function(x) {
 ncol(x)
}

#'
#' Returns a vector containing the minimum and maximum of all the given arguments.
#'
#' @name h2o.range
#' @param x An H2OFrame object.
#' @param na.rm \code{logical}. indicating whether missing values should be removed.
#' @param finite \code{logical}. indicating if all non-finite elements should be omitted.
#' @seealso \code{\link[base]{range}} for the base R implementation.
#' @export
h2o.range <- function(x,na.rm = FALSE,finite = FALSE) {
  range(x,na.rm = na.rm,finite)
}

#-----------------------------------------------------------------------------------------------------------------------
# Casting Operations: as.data.frame, as.factor
#-----------------------------------------------------------------------------------------------------------------------

#'
#' Is H2O Frame object
#'
#' Test if object is H2O Frame.
#'
#' @param x An \code{R} object.
#' @export
is.h2o <- function(x) inherits(x, "H2OFrame")

h2o.class.map <- function() {
  c("integer64"="numeric",
    "integer"="numeric",
    "double"="numeric",
    "complex"="numeric",
    "logical"="enum",
    "factor"="enum",
    "character"="string",
    "Date"="Time")
}

destination_frame.guess <- function(x) {
  valid.key = isTRUE(try(.key.validate(x), silent=TRUE)) # simplify after .key.validate improvement
  if (valid.key) x else ""
}

#' @title Use optional package
#' @description
#' Testing availability of optional package, its version, and extra global default.
#' This function is used internally. It is exported and documented because user can
#' control behavior of the function by global option.
#' @param package character scalar name of a package that we Suggests or Enhances on.
#' @param version character scalar required version of a package.
#' @param use logical scalar, extra escape option, to be used as global option.
#' @details
#' We use this function to control csv read/write with optional \link[data.table]{data.table} package.
#' Currently data.table is disabled by default, to enable it set \code{options("h2o.use.data.table"=TRUE)}.
#' It is possible to control just \code{\link[data.table]{fread}} or \code{\link[data.table]{fwrite}} with \code{options("h2o.fread"=FALSE, "h2o.fwrite"=FALSE)}.
#' \code{h2o.fread} and \code{h2o.fwrite} options are not handled in this function but next to \emph{fread} and \emph{fwrite} calls.
#' @export
#' @seealso \code{\link{as.h2o.data.frame}}, \code{\link{as.data.frame.H2OFrame}}
#' @examples
#' op <- options("h2o.use.data.table" = TRUE)
#' if (use.package("data.table")) {
#'   cat("optional package data.table 1.9.8+ is available\n")
#' } else {
#'   cat("optional package data.table 1.9.8+ is not available\n")
#' }
#' options(op)
use.package <- function(package, 
                        version="1.9.8"[package=="data.table"], 
                        use=getOption("h2o.use.data.table", FALSE)[package=="data.table"]) {
  ## methods that depends on use.package default arguments (to have control in single place):
  # as.h2o.data.frame
  # as.data.frame.H2OFrame
  stopifnot(is.character(package), length(package)==1L,
            is.character(version), length(version)==1L,
            is.logical(use), length(use)==1L)

  # if (package=="data.table" && use) { # not sure if this is needed.  Keeping it for now.
  #   if (!("bit64" %in% rownames(installed.packages())) || (packageVersion("bit64") < as.package_version("0.9.7"))) {
  #      # print out warning to install bit64 in order to use data.table
  #     warning("data.table cannot be used without R package bit64 version 0.9.7 or higher.  Please upgrade to take advangage of data.table speedups.")
  #     return(FALSE)
  #   }
  # }
  use && requireNamespace(package, quietly=TRUE) && (packageVersion(package) >= as.package_version(version))
}

#'
#' Create H2OFrame
#'
#' Import R object to the H2O cloud.
#'
#' @param x An \code{R} object.
#' @param destination_frame A string with the desired name for the H2OFrame.
#' @param \dots arguments passed to method arguments.
#' @export
#' @examples 
#' \donttest{
#' h2o.init()
#' hi <- as.h2o(iris)
#' he <- as.h2o(euro)
#' hl <- as.h2o(letters)
#' hm <- as.h2o(state.x77)
#' hh <- as.h2o(hi)
#' stopifnot(is.h2o(hi), dim(hi)==dim(iris),
#'           is.h2o(he), dim(he)==c(length(euro),1L),
#'           is.h2o(hl), dim(hl)==c(length(letters),1L),
#'           is.h2o(hm), dim(hm)==dim(state.x77),
#'           is.h2o(hh), dim(hh)==dim(hi))
#' if (requireNamespace("Matrix", quietly=TRUE)) {
#'   data <- rep(0, 100)
#'   data[(1:10)^2] <- 1:10 * pi
#'   m <- matrix(data, ncol = 20, byrow = TRUE)
#'   m <- Matrix::Matrix(m, sparse = TRUE)
#'   hs <- as.h2o(m)
#'   stopifnot(is.h2o(hs), dim(hs)==dim(m))
#' }
#' }
as.h2o <- function(x, destination_frame="", ...) {
  .key.validate(destination_frame)
  UseMethod("as.h2o")
}

#' @rdname as.h2o
#' @method as.h2o default
#' @export
as.h2o.default <- function(x, destination_frame="", ...) {
  if( destination_frame=="" ) destination_frame <- deparse(substitute(x)) # guessing is done in as.h2o.data.frame
  x <- if( length(x)==1L )
    data.frame(C1=x)
  else
    as.data.frame(x, ...)
  as.h2o.data.frame(x, destination_frame=destination_frame)
}

#' @rdname as.h2o
#' @method as.h2o H2OFrame
#' @export
as.h2o.H2OFrame <- function(x, destination_frame="", ...) {
  if( destination_frame=="" ) {
    subx <- destination_frame.guess(deparse(substitute(x)))
    destination_frame <- .key.make(if(nzchar(subx)) subx else "copy")
  }
  h2o.assign(x, key=destination_frame)
}

#' @rdname as.h2o
#' @method as.h2o data.frame
#' @details 
#' Method \code{as.h2o.data.frame} will use \code{\link[data.table]{fwrite}} if data.table package is installed in required version.
#' @seealso \code{\link{use.package}}
#' @references \url{http://blog.h2o.ai/2016/04/fast-csv-writing-for-r/}
#' @export
as.h2o.data.frame <- function(x, destination_frame="", ...) {
  if( destination_frame=="" )
    destination_frame <- deparse(substitute(x))
  
  destination_frame <- destination_frame.guess(destination_frame) # filter out invalid i.e. "abc::fun()"
  .key.validate(destination_frame) # h2o.uploadFile already handle ""
  
  # TODO: Be careful, there might be a limit on how long a vector you can define in console
  tmpf <- tempfile(fileext = ".csv")
  # remap R data types to java data types
  types <- sapply(x, function(x) class(x)[1L]) # ensure vector returned
  class.map <- h2o.class.map()
  types[types %in% names(class.map)] <- class.map[types[types %in% names(class.map)]]
  verbose <- getOption("h2o.verbose", FALSE)
  if (verbose) pt <- proc.time()[[3]]
  if (getOption("h2o.fwrite", TRUE) && use.package("data.table")) {
    data.table::fwrite(x, tmpf, na="NA_h2o", row.names=FALSE, showProgress=FALSE)
    fun <- "fwrite"
  } else {
    write.csv(x, file = tmpf, row.names = FALSE, na="NA_h2o")
    fun <- "write.csv"
  }
  if (verbose) cat(sprintf("writing csv to disk using '%s' took %.2fs\n", fun, proc.time()[[3]]-pt))
  #if (verbose) pt <- proc.time()[[3]] # timings inside
  h2f <- h2o.uploadFile(tmpf, destination_frame = destination_frame, header = TRUE, col.types=types,
                        col.names=colnames(x, do.NULL=FALSE, prefix="C"), na.strings=rep(c("NA_h2o"),ncol(x)))
  #if (verbose) cat(sprintf("uploading csv to h2o using 'h2o.uploadFile' took %.2fs\n", proc.time()[[3]]-pt))
  file.remove(tmpf)
  h2f
}

#' @rdname as.h2o
#' @method as.h2o Matrix
#' @details
#' To speedup execution time for large sparse matrices, use h2o datatable.  Make sure you have installed and imported data.table and slam packages.
#' Turn on h2o datatable by options("h2o.use.data.table"=TRUE)
#' @export
as.h2o.Matrix <- function(x, destination_frame="", ...) {
  
  if( destination_frame=="")
    destination_frame <- deparse(substitute(x))

  destination_frame <- destination_frame.guess(destination_frame) # filter out invalid i.e. "abc::fun()"
  .key.validate(destination_frame)
  if ( destination_frame=="" ) # .h2o.readSVMLight wont handle ""
    destination_frame <- .key.make("Matrix") # only used if `x` variable name not valid key

  if (use.package("data.table") && use.package("slam", version="0.1.40", TRUE)) {
    drs <- slam::as.simple_triplet_matrix(x)# need to convert sparse matrix x to a simple triplet matrix format
    thefile <- tempfile()
    .h2o.write_stm_svm(drs, file = thefile)
    h2f <<- h2o.uploadFile(thefile, parse_type = "SVMLight", destination_frame=destination_frame)
    unlink(thefile)
    h2f[, -1]   # remove the first column
  } else {
    warning("as.h2o can be slow for large sparse matrices.  Install packages data.table and slam to speed up as.h2o.")
    tmpf <- tempfile(fileext = ".svm")
    .h2o.write.matrix.svmlight(x, file = tmpf)
    h2f <- .h2o.readSVMLight(tmpf, destination_frame = destination_frame)
    file.remove(tmpf)
    h2f
  }
}

.h2o.write.matrix.svmlight <- function(matrix, file) {
  on.exit(sink())
  sink(file)
  sapply(1:nrow(matrix), function(i) {
    r <- matrix[i, ]
    val.indices <- which(r != 0)
    val.indices <- val.indices[val.indices > 1]
    target <- r[1]
    features <- paste(sprintf("%d", val.indices - 1), r[val.indices], collapse = " ", sep = ":")
    line <- sprintf("%s %s\n", target, features)
    cat(line)
  })
}

.h2o.calc_stm_svm <- function(stm, y){
  # Convert a simple triplet matrix to svm format
  # author Peter Ellis
  # return a character vector of length n
  # fixed bug to return rows of zeros instead of repeating other rows
  # returns a character vector of length y ready for writing in svm format
  if(!"simple_triplet_matrix" %in% class(stm)){
    stop("stm must be a simple triple matrix")
  }
  if(!is.vector(y) | nrow(stm) != length(y)){
    stop("y should be a vector of length equal to number of rows of stm")
  }
  n <- length(y)

  # data table solution thanks to roland
  rowLeft <- setdiff(c(1:n), unique(stm$i))
  nrowLeft <- length(rowLeft)
  i=NULL  # serves no purpose except to pass the R cmd cran check
  j=NULL
  v=NULL
  jv=NULL
  stm2 <- data.table::data.table(i = c(stm$i,rowLeft), j = c(stm$j,rep(1,nrowLeft)), v = c(stm$v,rep(0,nrowLeft)))
  res <- stm2[, list(i, jv = paste(j, v, sep = ":"))][order(i), list(res = paste(jv, collapse = " ")), by = i][["res"]]

  out <- paste(y, res)

  return(out)
}

.h2o.write_stm_svm <- function(stm, y = rep(1, nrow(stm)), file){
  # param stm a simple triplet matrix (class exported slam) of features (ie explanatory variables)
  # param y a vector of labels.  If not provided, a dummy of 1s is provided
  # param file file to write to.
  # author Peter Ellis
  out <- .h2o.calc_stm_svm(stm, y)
  writeLines(out, con = file)
}

#'
#' Converts parsed H2O data into an R data frame
#'
#' Downloads the H2O data and then scans it in to an R data frame.
#'
#' @param x An H2OFrame object.
#' @param ... Further arguments to be passed down from other methods.
#' @details
#' Method \code{as.data.frame.H2OFrame} will use \code{\link[data.table]{fread}} if data.table package is installed in required version.
#' @seealso \code{\link{use.package}}
#' @examples
#' \donttest{
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath)
#' as.data.frame(prostate.hex)
#' }
#' @export
as.data.frame.H2OFrame <- function(x, ...) {
  # Force loading of the types
  .fetch.data(x,1L)
  # Versions of R prior to 3.1 should not use hex string.
  # Versions of R including 3.1 and later should use hex string.
  use_hex_string <- getRversion() >= "3.1"

  urlSuffix <- paste0('DownloadDataset',
                      '?frame_id=', URLencode( h2o.getId(x)),
                      '&hex_string=', as.numeric(use_hex_string))
  
  verbose <- getOption("h2o.verbose", FALSE)
  if (verbose) pt <- proc.time()[[3]]
  ttt <- .h2o.doSafeGET(urlSuffix = urlSuffix)
  if (verbose) cat(sprintf("fetching from h2o frame to R using '.h2o.doSafeGET' took %.2fs\n", proc.time()[[3]]-pt))
  n <- nchar(ttt)

  # Delete last 1 or 2 characters if it's a newline.
  # Handle \r\n (for windows) or just \n (for not windows).
  chars_to_trim <- 0L
  if (n >= 2L) {
    c <- substr(ttt, n, n)
    if (c == "\n") chars_to_trim <- chars_to_trim + 1L
    if (chars_to_trim > 0L) {
      c <- substr(ttt, n-1L, n-1L)
      if (c == "\r") chars_to_trim <- chars_to_trim + 1L
    }
  }

  if (chars_to_trim > 0L) {
    ttt2 <- substr(ttt, 1L, n-chars_to_trim)
    ttt <- ttt2
  }

  # Get column types from H2O to set the dataframe types correctly
  colClasses <- attr(x, "types")
  colClasses <- gsub("numeric", NA, colClasses) # let R guess the appropriate numeric type
  colClasses <- gsub("int", NA, colClasses) # let R guess the appropriate numeric type
  colClasses <- gsub("real", NA, colClasses) # let R guess the appropriate numeric type
  colClasses <- gsub("enum", "factor", colClasses)
  colClasses <- gsub("uuid", "character", colClasses)
  colClasses <- gsub("string", "character", colClasses)
  colClasses <- gsub("time", NA, colClasses) # change to Date after ingestion
  
  # Convert all date columns to POSIXct
  dates <- attr(x, "types") %in% "time"
  
  if (verbose) pt <- proc.time()[[3]]
  if (getOption("h2o.fread", TRUE) && use.package("data.table")) {
    df <- data.table::fread(ttt, blank.lines.skip = FALSE, na.strings = "", colClasses = colClasses, showProgress=FALSE, data.table=FALSE, ...)
    if (sum(dates))
      for (i in which(dates)) data.table::setattr(df[[i]], "class", "POSIXct")
    fun <- "fread"
  } else {
    # Substitute NAs for blank cells rather than skipping
    df <- read.csv((tcon <- textConnection(ttt)), blank.lines.skip = FALSE, na.strings = "", colClasses = colClasses, ...)
    close(tcon)
    if (sum(dates))
      for (i in which(dates)) class(df[[i]]) = "POSIXct"
    fun <- "read.csv"
  }
  if (verbose) cat(sprintf("reading csv from disk using '%s' took %.2fs\n", fun, proc.time()[[3]]-pt))
  
  df
}

#' Convert an H2OFrame to a matrix
#'
#' @param x An H2OFrame object
#' @param ... Further arguments to be passed down from other methods.
#' @examples
#' \donttest{
#' h2o.init()
#' irisPath <- system.file("extdata", "iris.csv", package="h2o")
#' iris <- h2o.uploadFile(path = irisPath)
#' iris.hex <- as.h2o(iris)
#' describe <- h2o.describe(iris.hex)
#' mins = as.matrix(apply(iris.hex, 2, min))
#' print(mins)
#' }
#' @export
as.matrix.H2OFrame <- function(x, ...) as.matrix(as.data.frame.H2OFrame(x, ...))

#' Convert an H2OFrame to a vector
#'
#' @param x An H2OFrame object
#' @param mode Mode to coerce vector to
#' @usage \method{as.vector}{H2OFrame}(x,mode)
#' @method as.vector H2OFrame
#' @examples
#' \donttest{
#' h2o.init()
#' irisPath <- system.file("extdata", "iris.csv", package="h2o")
#' iris <- h2o.uploadFile(path = irisPath)
#' hex <- as.h2o(iris)
#' cor_R <- cor(as.matrix(iris[,1]))
#' cor_h2o <- cor(hex[,1])
#' iris_Rcor <- cor(iris[,1:4])
#' iris_H2Ocor <- as.data.frame(cor(hex[,1:4]))
#' h2o_vec <- as.vector(unlist(iris_H2Ocor))
#' r_vec <- as.vector(unlist(iris_Rcor))
#' }
#' @export
as.vector.H2OFrame <- function(x, mode="any") base::as.vector(as.matrix.H2OFrame(x), mode=mode)

#' @export		
as.logical.H2OFrame <- function(x, ...) as.vector.H2OFrame(x, "logical")


#' Logical or for H2OFrames
#' @name Logical-or
#' @param x An H2OFrame object
#' @param y An H2OFrame object
#' @export	
`||` <- function (x, y) {
  if( is.H2OFrame(x) ) .newExpr("||", x,y)
  else base::`||`(x,y)
}

#' Logical and for H2OFrames
#' 
#' @param x An H2OFrame object
#' @param y An H2OFrame object
#' @export	
`&&` <- function (x, y) {
  if( is.H2OFrame(x)  ) .newExpr("&&", x,y)
  else base::`&&`(x,y)
}

#' Convert H2O Data to Factors
#'
#' Convert a column into a factor column.
#' @param x a column from an H2OFrame data set.
#' @seealso \code{\link{as.factor}}.
#' @examples
#' \donttest{
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath)
#' prostate.hex[,2] <- as.factor(prostate.hex[,2])
#' summary(prostate.hex)
#' }
#' @export
as.factor <- function(x) {
  if( is.H2OFrame(x) ) .newExpr("as.factor",x)
  else base::as.factor(x)
}

#' Convert an H2OFrame to a String
#'
#' @param x An H2OFrame object
#' @param ... Further arguments to be passed from or to other methods.
#' @examples
#' \donttest{
#' h2o.init()
#' pretrained.frame <- as.h2o(data.frame(
#'        C1 = c("a", "b"), C2 = c(0, 1), C3 = c(1, 0), C4 = c(0.2, 0.8),
#'        stringsAsFactors = FALSE))
#' pretrained.w2v <- h2o.word2vec(pre_trained = pretrained.frame, vec_size = 3)
#' words <- as.character(as.h2o(c("b", "a", "c", NA, "a")))
#' vecs <- h2o.transform(pretrained.w2v, words = words)
#' }
#' @export
as.character.H2OFrame <- function(x, ...) {
  if( is.H2OFrame(x) ) .newExpr("as.character",x)
  else base::as.character(x)
}

#' Convert H2O Data to Numeric
#'
#' Converts an H2O column into a numeric value column.
#' @param x a column from an H2OFrame data set.
#' @param ... Further arguments to be passed from or to other methods.
#' @examples
#' \donttest{
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath)
#' prostate.hex[,2] <- as.factor (prostate.hex[,2])
#' prostate.hex[,2] <- as.numeric(prostate.hex[,2])
#' }
#' @export
as.numeric <- function(x) {
  if( is.H2OFrame(x) ) .newExpr("as.numeric",x)
  else base::as.numeric(x)
}

#'
#' Delete Columns from an H2OFrame
#'
#' Delete the specified columns from the H2OFrame.  Returns an H2OFrame without the specified
#' columns.
#'
#' @param data The H2OFrame.
#' @param cols The columns to remove.
#' @export
h2o.removeVecs <- function(data, cols) {
  .Deprecated("data[-c(cols)]")
}

#-----------------------------------------------------------------------------------------------------------------------
# Merge Operations: ifelse, cbind, rbind, merge
#-----------------------------------------------------------------------------------------------------------------------

#' H2O Apply Conditional Statement
#'
#' Applies conditional statements to numeric vectors in H2O parsed data objects when the data are
#' numeric.
#'
#' Both numeric and categorical values can be tested. However when returning a yes and no condition
#' both conditions must be either both categorical or numeric.
#'
#' @name h2o.ifelse
#' @param test A logical description of the condition to be met (>, <, =, etc...)
#' @param yes The value to return if the condition is TRUE.
#' @param no The value to return if the condition is FALSE.
#' @return Returns a vector of new values matching the conditions stated in the ifelse call.
#' @examples
#' \donttest{
#' h2o.init()
#' ausPath <- system.file("extdata", "australia.csv", package="h2o")
#' australia.hex <- h2o.importFile(path = ausPath)
#' australia.hex[,9] <- ifelse(australia.hex[,3] < 279.9, 1, 0)
#' summary(australia.hex)
#' }
#' @export
h2o.ifelse <- function(test, yes, no) {
  if( !is.H2OFrame(yes) && base::is.character(yes) ) yes <- .quote(yes)
  if( !is.H2OFrame(no)  && base::is.character(no ) ) no  <- .quote(no )
  .newExpr("ifelse",test,yes,no)
}

#' @rdname h2o.ifelse
#' @export
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
        if( is.H2OFrame(yes) ) return(yes[,1])
      } else {
        if( length(no) == 1 && is.null(attributes(no)) )
          return(no)
        if( is.H2OFrame(no) ) return(no[,1])
      }
    }
  }
  if( is.H2OFrame(test) || is.H2OFrame(yes) || is.H2OFrame(no) ) return(h2o.ifelse(test,yes,no))
  else base::ifelse(test,yes,no)
}

#' Combine H2O Datasets by Columns
#'
#' Takes a sequence of H2O data sets and combines them by column
#'
#' @name h2o.cbind
#' @param \dots A sequence of H2OFrame arguments. All datasets must exist on the same H2O instance
#'        (IP and port) and contain the same number of rows.
#' @return An H2OFrame object containing the combined \dots arguments column-wise.
#' @seealso \code{\link[base]{cbind}} for the base \code{R} method.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath)
#' prostate.cbind <- h2o.cbind(prostate.hex, prostate.hex)
#' head(prostate.cbind)
#' }
#' @export
h2o.cbind <- function(...) {
  li <- list(unlist(list(...)))
  use.args <- FALSE
  if( length(li)==1 && is.list(li[[1]]) ) {
    li <- li[[1]]
    use.args <- TRUE
  } else li <- list(...)
  lapply(li, function(l) chk.H2OFrame(l) )
  .newExprList("cbind",li)
}

#' Combine H2O Datasets by Rows
#'
#' Takes a sequence of H2O data sets and combines them by rows
#'
#' @name h2o.rbind
#' @param \dots A sequence of H2OFrame arguments. All datasets must exist on the same H2O instance
#'        (IP and port) and contain the same number and types of columns.
#' @return An H2OFrame object containing the combined \dots arguments row-wise.
#' @seealso \code{\link[base]{rbind}} for the base \code{R} method.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath)
#' prostate.cbind <- h2o.rbind(prostate.hex, prostate.hex)
#' head(prostate.cbind)
#' }
#' @export
h2o.rbind <- function(...) {
  ls <- list(...)
  l <- unlist(ls)
  if( !is.list(l) ) l <- ls
  klazzez <- unlist(lapply(l, function(i) is.H2OFrame(i)))
  if (any(!klazzez)) stop("`h2o.rbind` accepts only H2OFrame objects")
  .newExprList("rbind", l)
}

# Helper function for merge and sort inputs
checkMatch = function(x,y) {
  tt = match(x,y,nomatch=NA)
  if (anyNA(tt)) stop("Column '", x[is.na(tt)[1]], "' in ", substitute(x), " not found")
  tt
}

#' Merge Two H2O Data Frames
#'
#' Merges two H2OFrame objects with the same arguments and meanings
#' as merge() in base R.  However, we do not support all=TRUE, all.x=TRUE and all.y=TRUE.  The default method is auto
#' and it will default to the
#' radix method.  The radix method will return the correct merge result regardless of duplicated rows
#' in the right frame.  In addition, the radix method can perform merge even if you have string columns
#' in your frames.  If there are duplicated rows in your rite frame, they will not be included if you use
#' the hash method.  The hash method cannot perform merge if you have string columns in your left frame.
#' Hence, we consider the radix method superior to the hash method and is the default method to use.
#'
#' @param x,y H2OFrame objects
#' @param by columns used for merging by default the common names
#' @param by.x x columns used for merging by name or number
#' @param by.y y columns used for merging by name or number
#' @param all TRUE includes all rows in x and all rows in y even if there is no match to the other
#' @param all.x If all.x is true, all rows in the x will be included, even if there is no matching
#'        row in y, and vice-versa for all.y.
#' @param all.y see all.x
#' @param method auto(default), radix, hash
#' @examples
#' \donttest{
#' h2o.init()
#' left <- data.frame(fruit = c('apple', 'orange', 'banana', 'lemon', 'strawberry', 'blueberry'),
#' color <- c('red', 'orange', 'yellow', 'yellow', 'red', 'blue'))
#' right <- data.frame(fruit = c('apple', 'orange', 'banana', 'lemon', 'strawberry', 'watermelon'),
#' citrus <- c(FALSE, TRUE, FALSE, TRUE, FALSE, FALSE))
#' l.hex <- as.h2o(left)
#' r.hex <- as.h2o(right)
#' left.hex <- h2o.merge(l.hex, r.hex, all.x = TRUE)
#' }
#' @export
h2o.merge <- function(x, y, by=intersect(names(x), names(y)), by.x=by, by.y=by, all=FALSE, all.x=all, all.y=all, method="auto") {
  if (length(by.x) != length(by.y)) stop("`by.x` and `by.y` must be the same length.")
  if (!length(by.x)) stop("`by` or `by.x` must specify at least one column") 
  if (!is.numeric(by.x)) by.x = checkMatch(by.x, names(x))
  else if (any(is.na(by.x) | by.x<1 | by.x>ncol(x))) stop("by.x contains NA or an item outside range [1,ncol(x)]")
  if (!is.numeric(by.y)) by.y = checkMatch(by.y, names(y))
  else if (any(is.na(by.y) | by.y<1 | by.y>ncol(y))) stop("by.y contains NA or an item outside range [1,ncol(y)]")
  if (anyDuplicated(by.x)) stop("by.x contains duplicates")
  if (anyDuplicated(by.y)) stop("by.y contains duplicates")
  # -1L to be clear rapids in 0-based
  .newExpr("merge", x, y, all.x, all.y, by.x-1L, by.y-1L, .quote(method))
}


#' Sorts an H2O frame by columns
#'
#' Sorts H2OFrame by the columns specified. H2OFrame can contain String columns but should not sort on any
#' String columns.  Otherwise, an error will
#'  be thrown.  To sort column c1 in descending order, do desc(c1).  Returns a new H2OFrame, like dplyr::arrange.
#'
#' @param x The H2OFrame input to be sorted.
#' @param \dots The column names to sort by.
#'
#' @export
h2o.arrange <- function(x, ...) {
  by <- as.character(substitute(list(...))[-1])
  ascend <- as.character(substitute(list(...))[-1]) # initialize to same length as by
  if (!length(by)) stop("Please provide at least one column to sort by")
  for (index in c(1:length(by))) {
    if (sapply("desc", grepl, by[index])) {
      ascend[index]<- -1
      trueName <- sub("\\).*", "", sub(".*\\(", "", by[index]))
      by[index] <- trueName
    } else {
      ascend[index]<- 1
    }
  }
  by <- checkMatch(by, names(x))
  if (anyDuplicated(by)) stop("Some duplicate column names have been provided")
  .newExpr("sort", x, by-1L, as.numeric(ascend))
}


#' CHeck to see if the column names/indices entered is valid for the dataframe given.  This is an internal function
#'
#' @param data The H2OFrame whose column names or indices are entered as a list
#' @param by The column names/indices in a list.
#'
#' @export
generate_col_ind <-function(data, by) {
  ### handle the columns
  # we accept: c('col1', 'col2'), 1:2, c(1,2) as column names.
  if(base::is.character(by)) {
    group.cols <- match(by, colnames(data))
    if (any(is.na(group.cols)))
    stop('No column named ', by, ' in ', substitute(data), '.')
  } else if(is.integer(by)) {
    group.cols <- by
  } else if(is.numeric(by)) {   # this will happen eg c(1,2,3)
    group.cols <- as.integer(by)
  }

  if(group.cols <= 0L || group.cols > ncol(data)) {
   stop('Column ', group.cols, ' out of range for frame columns ', ncol(data), '.')
  }

  if (anyDuplicated(by)) stop("Some duplicate column names have been provided")

  return(group.cols)
}

#' This function will add a new column rank where the ranking is produced as follows:
#' 1. sorts the H2OFrame by columns sorted in by columns specified in group_by_cols and sort_cols in the directions
#'   specified by the ascending for the sort_cols.  The sort directions for the group_by_cols are ascending only.
#' 2. A new rank column is added to the frame which will contain a rank assignment performed next.  The user can
#'   choose to assign a name to this new column.  The default name is New_Rank_column.
#' 3. For each groupby groups, a rank is assigned to the row starting from 1, 2, ... to the end of that group.
#' 4. If sort_cols_sorted is TRUE, a final sort on the frame will be performed frame according to the sort_cols and
#'    the sort directions in ascending.  If sort_cols_sorted is FALSE (by default), the frame from step 3 will be
#'    returned as is with no extra sort.  This may provide a small speedup if desired.
#'
#'
#' @param x The H2OFrame input to be sorted.
#' @param group_by_cols a list of column names or indices to form the groupby groups
#' @param sort_cols a list of column names or indices for sorting
#' @param ascending a list of Boolean to determine if ascending sort (set to TRUE) is needed for each column in
#'  sort_cols (optional).  Default is ascending sort for all.  To perform descending sort, set value to FALSE
#' @param new_col_name new column name for the newly added rank column if specified (optional).  Default name is
#'  New_Rank_column.
#' @param sort_cols_sorted Boolean to determine if the final returned frame is to be sorted according to the sort_cols
#'  and sort directions in ascending.  Default is FALSE.
#'
#' The following example is generated by Nidhi Mehta.
#'
#' If the input frame is train:
#'
#' ID Group_by_column        num data Column_to_arrange_by       num_1 fdata
#' 12               1   2941.552    1                    3  -3177.9077     1
#' 12               1   2941.552    1                    5 -13311.8247     1
#' 12               2 -22722.174    1                    3  -3177.9077     1
#' 12               2 -22722.174    1                    5 -13311.8247     1
#' 13               3 -12776.884    1                    5 -18421.6171     0
#' 13               3 -12776.884    1                    4  28080.1607     0
#' 13               1  -6049.830    1                    5 -18421.6171     0
#' 13               1  -6049.830    1                    4  28080.1607     0
#' 15               3 -16995.346    1                    1  -9781.6373     0
#' 16               1 -10003.593    0                    3 -61284.6900     0
#' 16               3  26052.495    1                    3 -61284.6900     0
#' 16               3 -22905.288    0                    3 -61284.6900     0
#' 17               2 -13465.496    1                    2  12094.4851     1
#' 17               2 -13465.496    1                    3 -11772.1338     1
#' 17               2 -13465.496    1                    3   -415.1114     0
#' 17               2  -3329.619    1                    2  12094.4851     1
#' 17               2  -3329.619    1                    3 -11772.1338     1
#' 17               2  -3329.619    1                    3   -415.1114     0
#'
#' If the following commands are issued:
#' rankedF1 <- h2o.rank_within_group_by(train, c("Group_by_column"), c("Column_to_arrange_by"), c(TRUE))
#' h2o.summary(rankedF1)
#'
#' The returned frame rankedF1 will look like this:
#' ID Group_by_column        num fdata Column_to_arrange_by       num_1 fdata.1 New_Rank_column
#' 12               1   2941.552     1                    3  -3177.9077       1               1
#' 16               1 -10003.593     0                    3 -61284.6900       0               2
#' 13               1  -6049.830     0                    4  28080.1607       0               3
#' 12               1   2941.552     1                    5 -13311.8247       1               4
#' 13               1  -6049.830     0                    5 -18421.6171       0               5
#' 17               2 -13465.496     0                    2  12094.4851       1               1
#' 17               2  -3329.619     0                    2  12094.4851       1               2
#' 12               2 -22722.174     1                    3  -3177.9077       1               3
#' 17               2 -13465.496     0                    3 -11772.1338       1               4
#' 17               2 -13465.496     0                    3   -415.1114       0               5
#' 17               2  -3329.619     0                    3 -11772.1338       1               6
#' 17               2  -3329.619     0                    3   -415.1114       0               7
#' 12               2 -22722.174     1                    5 -13311.8247       1               8
#' 15               3 -16995.346     1                    1  -9781.6373       0               1
#' 16               3  26052.495     0                    3 -61284.6900       0               2
#' 16               3 -22905.288     1                    3 -61284.6900       0               3
#' 13               3 -12776.884     1                    4  28080.1607       0               4
#' 13               3 -12776.884     1                    5 -18421.6171       0               5
#'
#' If the following commands are issued:
#' rankedF1 <- h2o.rank_within_group_by(train, c("Group_by_column"), c("Column_to_arrange_by"), c(TRUE), sort_cols_sorted=TRUE)
#' h2o.summary(rankedF1)
#'
#' The returned frame will be sorted according to sortCols and hence look like this instead:
#' ID Group_by_column        num fdata Column_to_arrange_by       num_1 fdata.1 New_Rank_column
#' 15               3 -16995.346     1                    1  -9781.6373       0               1
#' 17               2 -13465.496     0                    2  12094.4851       1               1
#' 17               2  -3329.619     0                    2  12094.4851       1               2
#' 12               1   2941.552     1                    3  -3177.9077       1               1
#' 12               2 -22722.174     1                    3  -3177.9077       1               3
#' 16               1 -10003.593     0                    3 -61284.6900       0               2
#' 16               3  26052.495     0                    3 -61284.6900       0               2
#' 16               3 -22905.288     1                    3 -61284.6900       0               3
#' 17               2 -13465.496     0                    3 -11772.1338       1               4
#' 17               2 -13465.496     0                    3   -415.1114       0               5
#' 17               2  -3329.619     0                    3 -11772.1338       1               6
#' 17               2  -3329.619     0                    3   -415.1114       0               7
#' 13               3 -12776.884     1                    4  28080.1607       0               4
#' 13               1  -6049.830     0                    4  28080.1607       0               3
#' 12               1   2941.552     1                    5 -13311.8247       1               4
#' 12               2 -22722.174     1                    5 -13311.8247       1               8
#' 13               3 -12776.884     1                    5 -18421.6171       0               5
#' 13               1  -6049.830     0                    5 -18421.6171       0               5
#'
#' @export
h2o.rank_within_group_by <- function(x, group_by_cols, sort_cols, ascending=NULL, new_col_name="New_Rank_column", sort_cols_sorted=FALSE) {
  group.cols = generate_col_ind(x, group_by_cols)
  sort.cols = generate_col_ind(x, sort_cols)
  numSort <- length(sort.cols)
  sortdir <- 1^(runif(numSort,1,1)) # default sort direction is ascending
  if (length(ascending) > 0) {
    if (length(ascending) != numSort) stop("Sizes of sorting columns and sorting directions must be the same.")
    for (ind in c(1:numSort)) {
      if (is.logical(ascending[ind])) {
        if (ascending[ind]) {
          sortdir[ind] = 1
        } else {
          sortdir[ind]=-1
        }
      } else {
        stop("ascending must contain either TRUE for ascending sort, FALSE for descending sort for each column in sortCols.")
      }
    }
  }
  finalSortedOrder=0
  if (sort_cols_sorted) {
    finalSortedOrder=1
  }
  .newExpr("rank_within_groupby", x, group.cols-1L, sort.cols-1L, sortdir, new_col_name, finalSortedOrder)
}

#' Reorders levels of an H2O factor, similarly to standard R's relevel.
#'
#' The levels of a factor are reordered os that the reference level is at level 0, remaining levels are moved down as needed.
#'
#' @param x factor column in h2o frame
#' @param y reference level (string)
#' @return new reordered factor column
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#'
#' # Convert iris dataset to an H2OFrame
#' hf <- as.h2o(iris)
#' # Look at current ordering of the Species column levels
#' h2o.levels(hf["Species"])
#' # "setosa"     "versicolor" "virginica" 
#' # Change the reference level to "virginica"
#' hf["Species"] <- h2o.relevel(x = hf["Species"], y = "virginica")
#' # Observe new ordering
#' h2o.levels(hf["Species"])
#' # "virginica"  "setosa"     "versicolor"
#' }
#' @export
h2o.relevel <- function(x,y) {
  .newExpr("relevel", x, .quote(y))
}


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
#' Note that to specify a list of column names in the \code{gb.control} list, you must add the 
#' \code{col.names} argument. Similar to \code{na.methods}, \code{col.names} will pad the list with 
#' the default column names if the length is less than the number of colums groups supplied. 
#'
#' Supported functions include \code{nrow}. This function is required and accepts a string for the 
#' name of the generated column. Other supported aggregate functions accept \code{col} and \code{na} 
#' arguments for specifying columns and the handling of NAs (\code{"all"}, \code{"ignore"}, and 
#  \code{"rm"}) and include the following: \code{count} counts the number of rows in each group of a 
#' GroupBy object; \code{max} calculates the maximum of each column specified in \code{col} for each 
#' group of a GroupBy object; \code{mean} calculates the mean of each column specified in \code{col} 
#' for each group of a GroupBy object; \code{min} calculates the minimum of each column specified in 
#' \code{col} for each group of a GroupBy object; \code{mode} calculates the mode of each column 
#' specified in \code{col} for each group of a GroupBy object; \code{sd} calculates the standard 
#' deviation of each column specified in \code{col} for each group of a GroupBy object; \code{ss} 
#' calculates the sum of squares of each column specified in \code{col} for each group of a GroupBy 
#' object; \code{sum} calculates the sum of each column specified in \code{col} for each group of a 
#' GroupBy object; and \code{var} calculates the variance of each column specified in \code{col} for 
#' each group of a GroupBy object. If an aggregate is provided without a value (for example, as 
#' \code{max} in \code{sum(col="X1", na="all").mean(col="X5", na="all").max()}), then it is assumed 
#' that the aggregation should apply to all columns except the GroupBy columns. However, operations
#'  will not be performed on String columns.  They will be skipped.  Note again that
#' \code{nrow} is required and cannot be empty.
#'
#' @param data an H2OFrame object.
#' @param by a list of column names
#' @param \dots any supported aggregate function. See \code{Details:} for more help.
#' @param gb.control a list of how to handle \code{NA} values in the dataset as well as how to name
#'        output columns. The method is specified using the \code{rm.method} argument. See 
#'        \code{Details:} for more help.
#' @return Returns a new H2OFrame object with columns equivalent to the number of
#'         groups created
#' @export
h2o.group_by <- function(data, by, ..., gb.control=list(na.methods=NULL, col.names=NULL)) {
  # Build the argument list: (GB data, [group.by] {agg col "na"}...)
  args <- list(chk.H2OFrame(data))

  ### handle the columns
  # we accept: c('col1', 'col2'), 1:2, c(1,2) as column names.
  if(base::is.character(by)) {
    group.cols <- match(by, colnames(data))
    if (any(is.na(group.cols)))
      stop('No column named ', by, ' in ', substitute(data), '.')
  } else if(is.integer(by)) {
    group.cols <- by
  } else if(is.numeric(by)) {   # this will happen eg c(1,2,3)
    group.cols <- as.integer(by)
  }
  if(group.cols <= 0L || group.cols > ncol(data))
    stop('Column ', group.cols, ' out of range for frame columns ', ncol(data), '.')
  args <- c(args,.row.col.selector(group.cols,envir=parent.frame()))

  a <- substitute(list(...))
  a[[1]] <- NULL  # drop the wrapping list()
  nAggs <- length(a)  # the number of aggregates
  # for each aggregate, build this list: (agg,col.idx,na.method)
  agg.methods <- unlist(lapply(a, function(agg) {
    ag <- as.character(agg[[1]])
    if( ag=="sd" ) ag <- "\"sdev\""
    ag
  }))
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

  for (colIndex in col.idxs) {
    if (h2o.ischaracter(data[colIndex])) {
      warning(paste0("Column ", names(data)[colIndex], " is a String column.  Groupby operations are not performend on it."))
    }
  }
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

  # Append the aggregates!  Append triples: aggregate, column, na-handling
  for( idx in 1:nAggs ) {
    args <- c(args, agg.methods[idx], eval(col.idxs[idx]), .quote(gb.control$na.methods[idx]))
  }

  # Create the group by AST
  .newExprList("GB",args)
}

h2o.groupedPermute <- function(fr, permCol, permByCol, groupByCols, keepCol) {
  .newExpr("grouped_permute", fr, permCol-1, groupByCols-1, permByCol-1, keepCol-1)
}

#-----------------------------------------------------------------------------------------------------------------------
# *ply methods: ddply, apply, lapply, sapply,
#-----------------------------------------------------------------------------------------------------------------------
# TODO: Cleanup the cruft!
#' Split H2O Dataset, Apply Function, and Return Results
#'
#' For each subset of an H2O data set, apply a user-specified function, then combine the results.  This is an experimental feature.
#'
#' @param X An H2OFrame object to be processed.
#' @param .variables Variables to split \code{X} by, either the indices or names of a set of columns.
#' @param FUN Function to apply to each subset grouping.
#' @param ... Additional arguments passed on to \code{FUN}.
#' @param .progress Name of the progress bar to use. #TODO: (Currently unimplemented)
#' @return Returns an H2OFrame object containing the results from the split/apply operation, arranged
#          row-by-row
#' @seealso \code{\link[plyr]{ddply}} for the plyr library implementation.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#'
#' # Import iris dataset to H2O
#' irisPath <- system.file("extdata", "iris_wheader.csv", package = "h2o")
#' iris.hex <- h2o.uploadFile(path = irisPath, destination_frame = "iris.hex")
#' # Add function taking mean of sepal_len column
#' fun <- function(df) { sum(df[,1], na.rm = TRUE)/nrow(df) }
#' # Apply function to groups by class of flower
#' # uses h2o's ddply, since iris.hex is an H2OFrame object
#' res <- h2o.ddply(iris.hex, "class", fun)
#' head(res)
#' }
#' @export
h2o.ddply <- function (X, .variables, FUN, ..., .progress = 'none') {
  .h2o.gc()
  chk.H2OFrame(X)

  # we accept eg .(col1, col2), c('col1', 'col2'), 1:2, c(1,2)
  # as column names.  This is a bit complicated
  if(base::is.character(.variables)) {
    vars <- match(.variables, colnames(X))
    if (any(is.na(vars)))
      stop('No column named ', .variables, ' in ', substitute(X), '.')
  } else if(is(.variables, 'H2Oquoted')) {
    vars <- match(.variables, colnames(X))
  } else if(inherits(.variables, 'quoted')) { # plyr overwrote our . fn
    vars <- match(.variables, colnames(X))
  } else if(is.integer(.variables)) {
    vars <- .variables
  } else if(is.numeric(.variables)) {   # this will happen eg c(1,2,3)
    vars <- as.integer(.variables)
  }

  # Change cols from 1 base notation to 0 base notation then verify the column is within range of the dataset
  if(vars <= 0L || vars > ncol(X))
    stop('Column ', vars, ' out of range for frame columns ', ncol(X), '.')
  vars <- .row.col.selector(vars,envir=parent.frame())

  # Deal with extra arguments
  l <- list(...)
  extra_args = list()
  if(length(l) > 0L)
    extra_args <- sapply(l, .process.stmnt, list(), sys.parent(1))

  # Process the function. Decide if it's an anonymous fcn, or a named one.
  fname <- as.character(substitute(FUN))
  if( typeof(FUN) == "builtin" || typeof(FUN) == "symbol") {
    if( fname %in% .h2o.primitives ) return(.newExpr("ddply",X,vars,fname))
    stop(paste0("Function '",fname,"' not in .h2o.primitives list and not an anonymous function, unable to convert it to Currents"))
  }

  # Look for an H2O function that works on an H2OFrame; it will be handed an H2OFrame of 1 col
  fr.name <- paste0(fname,".H2OFrame")
  if( exists(fr.name) ) {
    FUN <- get(fr.name)         # Resolve function to the H2O flavor
    # Add in any default args
    args <- formals(FUN)[-1L]
    nargs <- length(args) - length(extra_args)
    if( nargs > 0 ) extra_args <- c(extra_args,tail(args,nargs))
    fcn <- if( length(extra_args)==0 ) fname
           else paste0("{ COL . (",fname," COL ",paste(extra_args,collapse=" "),")}")
    return(.newExpr("ddply",X,vars,fcn))
  }

  # Explode anonymous function into a Currents AST.  Pass along the dynamic
  # environment (not the static environment the H2O wrapper itself is compiled
  # in).  Unknown variables in the function body will be looked up in the
  # dynamic scope.
  funstr <- .fun.to.ast(FUN, list(), sys.parent(1))
  .newExpr("ddply",X,vars,funstr)
}

#' Apply on H2O Datasets
#'
#' Method for apply on H2OFrame objects.
#'
#' @param X an H2OFrame object on which \code{apply} will operate.
#' @param MARGIN the vector on which the function will be applied over, either
#'        \code{1} for rows or \code{2} for columns.
#' @param FUN the function to be applied.
#' @param \dots optional arguments to \code{FUN}.
#' @return Produces a new H2OFrame of the output of the applied
#'         function. The output is stored in H2O so that it can be used in
#'         subsequent H2O processes.
#' @seealso \link[base]{apply} for the base generic
#' @examples
#' \donttest{
#' h2o.init()
#' irisPath <- system.file("extdata", "iris.csv", package="h2o")
#' iris.hex <- h2o.importFile(path = irisPath, destination_frame = "iris.hex")
#' summary(apply(iris.hex, 2, sum))
#' }
#' @export
apply <- function(X, MARGIN, FUN, ...) {
  if( !is.H2OFrame(X) ) return(base::apply(X,MARGIN,FUN,...))

  # Margin must be 1 or 2 and specified
  if( missing(MARGIN) || !(length(MARGIN) <= 2L && all(MARGIN %in% c(1L, 2L))) )
    stop("MARGIN must be either 1 (rows), 2 (cols), or a vector containing both")
  # Basic sanity checking on function
  if( missing(FUN) ) stop("FUN must be an R function")
  .FUN <- NULL
  if( base::is.character(FUN) ) .FUN <- get(FUN)
  if( !is.null(.FUN) && !is.function(.FUN) )    stop("FUN must be an R function!")
  else if( is.null(.FUN) && !is.function(FUN) ) stop("FUN must be an R function")
  if( !is.null(.FUN) ) FUN <- as.name(FUN)

  # Deal with extra arguments
  l <- list(...)
  extra_args = list()
  if(length(l) > 0L)
    extra_args <- sapply(l, .process.stmnt, list(), sys.parent(1))

  # Process the function. Decide if it's an anonymous fcn, or a named one.
  fname <- as.character(substitute(FUN))
  if( typeof(FUN) == "builtin" || typeof(FUN) == "symbol") {
    if( fname %in% .h2o.primitives ) return(.newExpr("apply",X,MARGIN,fname))
    stop(paste0("Function '",fname,"' not in .h2o.primitives list and not an anonymous function, unable to convert it to Currents"))
  }

  # Look for an H2O function that works on an H2OFrame; it will be handed an H2OFrame of 1 col
  fr.name <- paste0(fname,".H2OFrame")
  if( exists(fr.name) ) {
    FUN <- get(fr.name)         # Resolve function to the H2O flavor
    # Add in any default args
    args <- formals(FUN)[-1L]
    nargs <- length(args) - length(extra_args)
    if( nargs > 0 ) extra_args <- c(extra_args,tail(args,nargs))
    fcn <- if( length(extra_args)==0 ) fname
           else paste0("{ COL . (",fname," COL ",paste(extra_args,collapse=" "),")}")

    if(fname == "which.max" || fname == "which.min"){
      return(.newExpr("apply",X,MARGIN,fcn) + 1)
    }
    return(.newExpr("apply",X,MARGIN,fcn))
  }

  # Explode anonymous function into a Currents AST.  Pass along the dynamic
  # environment (not the static environment the H2O wrapper itself is compiled
  # in).  Unknown variables in the function body will be looked up in the
  # dynamic scope.
  funstr <- .fun.to.ast(FUN, list(), sys.parent(1))
  .newExpr("apply",X,MARGIN,funstr)
}

#'
#' Compute A Histogram
#'
#' Compute a histogram over a numeric column. If breaks=="FD", the MAD is used over the IQR
#' in computing bin width. Note that we do not beautify the breakpoints as R does.
#'
#'
#'
#' @param x A single numeric column from an H2OFrame.
#' @param breaks Can be one of the following:
#'               A string: "Sturges", "Rice", "sqrt", "Doane", "FD", "Scott"
#'               A single number for the number of breaks splitting the range of the vec into number of breaks bins of equal width
#'               A vector of numbers giving the split points, e.g., c(-50,213.2123,9324834)
#' @param plot A logical value indicating whether or not a plot should be generated (default is TRUE).
#' @export
h2o.hist <- function(x, breaks="Sturges", plot=TRUE) {
  if( base::is.character(breaks) ) {
    if( breaks=="Sturges" ) breaks <- "sturges"
    if( breaks=="Rice"    ) breaks <- "rice"
    if( breaks=="Doane"   ) breaks <- "doane"
    if( breaks=="FD"      ) breaks <- "fd"
    if( breaks=="Scott"   ) breaks <- "scott"
    h <- as.data.frame(.newExpr("hist", chk.H2OFrame(x), .quote(breaks)))
  } else {
    h <- as.data.frame(.newExpr("hist", chk.H2OFrame(x), breaks))
  }
  counts <- stats::na.omit(h[,2])
  mids <- stats::na.omit(h[,4])
  histo <- list()
  histo$breaks <- h$breaks
  histo$counts <- as.numeric(counts)
  histo$density <- as.numeric(histo$counts / sum(histo$counts) * 1 / diff(histo$breaks))
  histo$mids   <- as.numeric(mids)
  histo$xname  <- deparse(substitute(x))
  oldClass(histo) <- "histogram"
  if( plot ) {
    plot(histo)
    invisible(histo)
  } else histo
}
#-----------------------------------------------------------------------------------------------------------------------
# Time Series Operations
#-----------------------------------------------------------------------------------------------------------------------

#' Conduct a lag 1 transform on a numeric H2OFrame column
#'
#' @rdname h2o.diff
#' @param object H2OFrame object
#' @return Returns an H2OFrame object.
#' @export
h2o.difflag1 <- function(object){
  .newExpr("difflag1", object)
}

#'
#' iSAX
#'
#' Compute the iSAX index for a DataFrame which is assumed to be numeric time series data
#'
#' @param x an H2OFrame
#' @param num_words Number of iSAX words for the timeseries. ie granularity along the time series
#' @param max_cardinality Maximum cardinality of the iSAX word. Each word can have less than the max
#' @param optimize_card An optimization flag that will find the max cardinality regardless of what is passed in for max_cardinality.
#' @return An H2OFrame with the name of time series, string representation of iSAX word, followed by binary representation
#' @references http://www.cs.ucr.edu/~eamonn/iSAX_2.0.pdf
#' @references http://www.cs.ucr.edu/~eamonn/SAX.pdf
#' @export
h2o.isax <- function(x, num_words, max_cardinality, optimize_card = FALSE){
  if(num_words <= 0){
    stop("num_words must be greater than 0!")
  }
  if(max_cardinality <= 0){
    stop("max_cardinality must be greater than 0!")
  }
  .newExpr("isax", x, num_words, max_cardinality, optimize_card)
}


#'
#' fillNA
#'
#' Fill NA's in a sequential manner up to a specified limit
#'
#' @param x an H2OFrame
#' @param method A String: "forward" or "backward"
#' @param axis An Integer 1 for row-wise fill (default), 2 for column-wise fill
#' @param maxlen An Integer for maximum number of consecutive NA's to fill
#' @return An H2OFrame after filling missing values
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' fr.with.nas = h2o.createFrame(categorical_fraction=0.0,missing_fraction=0.7,rows=6,cols=2,seed=123)
#' fr <- h2o.fillna(fr.with.nas, "forward", axis=1, maxlen=2L)
#' }
#' @export
h2o.fillna <- function(x, method="forward", axis=1, maxlen=1L) {
  if(! axis %in% c(1,2)) stop("axis must be 1 or 2")
  if(axis == 2) axis_arg=0 else axis_arg=1
  if(! method %in% c("forward","backward")) stop("method must be forward or backward")
  if(! is.integer(maxlen)) stop("max len must be an integer (e.g., 2L)")
  .newExpr("h2o.fillna", x, .quote(method), axis_arg, maxlen)
}

#-----------------------------------------------------------------------------------------------------------------------
# String Operations
#-----------------------------------------------------------------------------------------------------------------------

#'
#' String Split
#'
#' @param x The column whose strings must be split.
#' @param split The pattern to split on.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' string_to_split <- as.h2o("Split at every character.")
#' split_string <- h2o.strsplit(string_to_split,"")
#' }
#' @return An H2OFrame where each column is the outcome of the string split.
#' @export
h2o.strsplit <- function(x, split) { .newExpr("strsplit", x, .quote(split)) }

#'
#' Tokenize String
#'
#' h2o.tokenize is similar to h2o.strsplit, the difference between them is that h2o.tokenize will store the tokenized
#' text into a single column making it easier for additional processing (filtering stop words, word2vec algo, ...).
#'
#' @param x The column or columns whose strings to tokenize.
#' @param split The regular expression to split on.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' string_to_tokenize <- as.h2o("Split at every character and tokenize.")
#' tokenize_string <- h2o.tokenize(as.character(string_to_tokenize),"")
#' }
#' @return An H2OFrame with a single column representing the tokenized Strings. Original rows of the input DF are separated by NA.
#' @export
h2o.tokenize <- function(x, split) { .newExpr("tokenize", x, .quote(split)) }

#'
#' Convert strings to lowercase
#'
#' @param x An H2OFrame object whose strings should be lower cased
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' string_to_lower <- as.h2o("ABCDE")
#' lowered_string <- h2o.tolower(string_to_lower)
#' }
#' @return An H2OFrame with all entries in lowercase format
#' @export
h2o.tolower <- function(x) .newExpr("tolower", x)

#'
#' Convert strings to uppercase
#'
#' @param x An H2OFrame object whose strings should be upper cased
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' string_to_upper <- as.h2o("abcde")
#' upper_string <- h2o.toupper(string_to_upper)
#' }
#' @return An H2OFrame with all entries in uppercase format
#' @export
h2o.toupper <- function(x) .newExpr("toupper", x)

#' Search for matches to an argument pattern
#' 
#' Searches for matches to argument `pattern` within each element
#'  of a string column.
#'
#' This function has similar semantics as R's native grep function
#' and it supports a subset of its parameters. Default behavior is
#' to return indices of the elements matching the pattern. Parameter
#' `output.logical` can be used to return a logical vector indicating
#' if the element matches the pattern (1) or not (0).
#'
#' @param pattern A character string containing a regular expression.
#' @param x An H2O frame that wraps a single string column.
#' @param ignore.case If TRUE case is ignored during matching.
#' @param invert Identify elements that do not match the pattern.
#' @param output.logical If TRUE returns logical vector of indicators instead of list of matching positions
#' @return H2OFrame holding the matching positions or a logical vector
#' if `output.logical` is enabled.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' addresses <- as.h2o(c("2307", "Leghorn St", "Mountain View", "CA", "94043"))
#' zip.codes <- addresses[h2o.grep("[0-9]{5}", addresses, output.logical = TRUE),]
#' }
#' @export
h2o.grep <- function(pattern, x, ignore.case = FALSE, invert = FALSE, output.logical = FALSE) {
  result <- .newExpr("grep", x, .quote(pattern), ignore.case, invert, output.logical)
  if (! output.logical)
    result <- result + 1 # R indices start at 1
  result
}

#'
#' String Substitute
#'
#' Creates a copy of the target column in which each string has the first occurence of
#' the regex pattern replaced with the replacement substring.
#'
#' @param pattern The pattern to replace.
#' @param replacement The replacement pattern.
#' @param x The column on which to operate.
#' @param ignore.case Case sensitive or not
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' string_to_sub <- as.h2o("r tutorial")
#' sub_string <- h2o.sub("r ","H2O ",string_to_sub)
#' }
#' @export
h2o.sub <- function(pattern,replacement,x,ignore.case=FALSE) .newExpr("replacefirst", x, .quote(pattern), .quote(replacement),ignore.case)

#'
#' String Global Substitute
#'
#' Creates a copy of the target column in which each string has all occurence of
#' the regex pattern replaced with the replacement substring.
#'
#' @param pattern The pattern to replace.
#' @param replacement The replacement pattern.
#' @param x The column on which to operate.
#' @param ignore.case Case sensitive or not
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' string_to_gsub <- as.h2o("r tutorial")
#' sub_string <- h2o.gsub("r ","H2O ",string_to_gsub)
#' }
#' @export
h2o.gsub <- function(pattern,replacement,x,ignore.case=FALSE) .newExpr("replaceall", x, .quote(pattern), .quote(replacement),ignore.case)

#'
#' Trim Space
#'
#' @param x The column whose strings should be trimmed.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' string_to_trim <- as.h2o("r tutorial")
#' trim_string <- h2o.trim(string_to_trim)
#' }
#' @export
h2o.trim <- function(x) .newExpr("trim", x)

#'
#' String length
#'
#' @param x The column whose string lengths will be returned.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' string_to_nchar <- as.h2o("r tutorial")
#' nchar_string <- h2o.nchar(string_to_nchar)
#' }
#' @export
h2o.nchar <- function(x) .newExpr("strlen", x)

#'
#' Substring
#'
#' 
#' Returns a copy of the target column that is a substring at the specified start 
#' and stop indices, inclusive. If the stop index is not specified, then the substring extends
#' to the end of the original string. If start is longer than the number of characters
#' in the original string, or is greater than stop, an empty string is returned. Negative start
#' is coerced to 0. 
#'
#' @param x The column on which to operate.
#' @param start The index of the first element to be included in the substring.
#' @param stop Optional, The index of the last element to be included in the substring.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' string_to_substring <- as.h2o("1234567890")
#' substr <- h2o.substring(string_to_substring,2) #Get substring from second index onwards
#' }
#' @export
h2o.substring <- function(x, start, stop="[]") .newExpr("substring", x, start-1, stop)

#' @rdname h2o.substring
h2o.substr <- h2o.substring

#'
#' Strip set from left
#'
#' Return a copy of the target column with leading characters removed. The set argument
#' is a string specifying the set of characters to be removed. If omitted, the set
#' argument defaults to removing whitespace.
#'
#' @param x   The column whose strings should be lstrip-ed.
#' @param set string of characters to be removed
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' string_to_lstrip <- as.h2o("1234567890")
#' lstrip_string <- h2o.lstrip(string_to_lstrip,"123") #Remove "123"
#' }
#' @export
h2o.lstrip <- function(x, set = " ") .newExpr("lstrip", x, .quote(set))

#'
#' Strip set from right
#'
#' Return a copy of the target column with trailing characters removed. The set argument
#' is a string specifying the set of characters to be removed. If omitted, the set
#' argument defaults to removing whitespace.
#'
#' @param x   The column whose strings should be rstrip-ed.
#' @param set string of characters to be removed
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' string_to_rstrip <- as.h2o("1234567890")
#' rstrip_string <- h2o.rstrip(string_to_rstrip,"890") #Remove "890"
#' }
#' @export
h2o.rstrip <- function(x, set = " ") .newExpr("rstrip", x, .quote(set))


#'
#' Shannon entropy
#'
#' Return the Shannon entropy of a string column. If the string is empty, the entropy is 0.
#'
#' @param x   The column on which to calculate the entropy.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' buys <- as.h2o(c("no", "no", "yes", "yes", "yes", "no", "yes", "no", "yes", "yes","no"))
#' buys_entropy <- h2o.entropy(buys)
#' }
#' @export
h2o.entropy <- function(x) .newExpr("entropy", x)

#'
#' Count of substrings >= 2 chars that are contained in file
#'
#' Find the count of all possible substrings >= 2 chars that are contained in the specified line-separated text file. 
#'
#' @param x     The column on which to calculate the number of valid substrings.
#' @param path  Path to text file containing line-separated strings to be referenced. 
#' @export
h2o.num_valid_substrings <- function(x, path) .newExpr("num_valid_substrings", x, .quote(path))

#' Compute element-wise string distances between two H2OFrames
#'
#' Compute element-wise string distances between two H2OFrames. Both frames need to have the same
#' shape (N x M) and only contain string/factor columns. Return a matrix (H2OFrame) of shape N x M.
#'
#' @param x An H2OFrame
#' @param y A comparison H2OFrame
#' @param compare_empty if set to FALSE, empty strings will be handled as NaNs
#' @param method A string identifier indicating what string distance measure to use. Must be one of:
#'   "lv"                   - Levenshtein distance
#'   "lcs"                  - Longest common substring distance
#'   "qgram"                - q-gram distance
#'   "jaccard"              - Jaccard distance between q-gram profiles
#'   "jw"                   - Jaro, or Jaro-Winker distance
#'   "soundex"              - Distance based on soundex encoding
#' @examples
#' \donttest{
#' h2o.init()
#' x <- as.h2o(c("Martha", "Dwayne", "Dixon"))
#' y <- as.character(as.h2o(c("Marhta", "Duane", "Dicksonx")))
#' h2o.stringdist(x, y, method = "jw")
#' }
#' @export
h2o.stringdist <- function(x, y, method = c("lv", "lcs", "qgram", "jaccard", "jw", "soundex"), compare_empty = TRUE) {
  if (! is.H2OFrame(x)) stop("`x` parameter needs to be an H2OFrame")
  if (! is.H2OFrame(y)) stop("`y` parameter needs to be an H2OFrame")
  method <- match.arg(method)
  .newExpr("strDistance", x, y, .quote(method), compare_empty)
}

#'
#' Create Target Encoding Map
#' 
#' Creates a target encoding map based on group-by columns (`x`) and a numeric or binary target column (`y`). 
#' Computing target encoding for high cardinality categorical columns can improve performance of supervised 
#' learning models. A Target Encoding tutorial is available here: \url{https://github.com/h2oai/h2o-tutorials/blob/master/best-practices/categorical-predictors/target_encoding.md}.
#' 
#' @param data An H2OFrame object with which to create the target encoding map.
#' @param x A list containing the names or indices of the variables to encode.  A target encoding map will be created for each element in the list.  Items in the list can be multiple columns.  For example, if `x = list(c("A"), c("B", "C"))`, then there will be one mapping frame for A and one mapping frame for B & C (in this case, we group by two columns). 
#' @param y The name or column index of the response variable in the data. The response variable can be either numeric or binary.
#' @param fold_column (Optional) The name or column index of the fold column in the data. Defaults to NULL (no `fold_column`).
#' @return Returns a list of H2OFrame objects containing the target encoding mapping for each column in `x`.
#' @seealso \code{\link{h2o.target_encode_apply}} for applying the target encoding mapping to a frame.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' 
#' # Get Target Encoding Map on bank-additional-full data with numeric response
#' data <- h2o.importFile(
#' path = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/demos/bank-additional-full.csv",
#' destination_frame = "data")
#' mapping_age <- h2o.target_encode_create(data = data, x = list(c("job"), c("job", "marital")), 
#' y = "age")
#' head(mapping_age)
#' 
#' # Get Target Encoding Map on bank-additional-full data with binary response
#' mapping_y <- h2o.target_encode_create(data = data, x = list(c("job"), c("job", "marital")), 
#' y = "y")
#' head(mapping_y)
#' 
#' }
#' @export

h2o.target_encode_create <- function(data, x, y, fold_column = NULL){
  
  if (missing(data)) {
    stop("argument 'data' is missing, with no default")
  }  
  if (missing(y)) {
    stop("argument 'y' is missing, with no default")
  }  
  if (missing(x)) {
    stop("argument 'x' is missing, with no default")
  }
  if (!is.list(x)) {
    stop("argument 'x' must be a list")
  }
  if (!is.h2o(data)) {
    stop("argument `data` must be a valid H2OFrame")
  }
  if (any(is.numeric(data[unlist(x)])) || length(data[unlist(x)]) == 0L) {
    stop("`x` must be categorical columns")
  }
  if (is.factor(data[[y]])) {
    y_levels <- h2o.levels(data[[y]])
    if (length(y_levels) == 2) {
      data[[y]] <- h2o.ifelse(is.na(data[[y]]), NA, h2o.ifelse(data[[y]] == y_levels[[1]], 0, 1))
    } else { 
      stop(paste0("`y` must be a numeric or binary vector - has ", length(y_levels), " levels"))
    }
  }
  
  if (is.numeric(unlist(x))) {
    x <- sapply(x, function(i) colnames(data)[i])
  }
  if (is.numeric(y)) {
    y <- colnames(data)[y]
  }
  if (is.numeric(fold_column)) {
    fold_column <- colnames(data)[fold_column]
  }
  
  # Remove records where y is NA
  encoding_data <- data[!is.na(data[[y]]), ]
  
  # Calculate target encoding mapping for each x
  te_mapping <- list()
  for(cols in x){
    
    # Calculate sum of y and number of rows per level of data
    if (is.null(fold_column)) {
      x_mapping <- h2o.group_by(encoding_data, cols, sum(y), nrow(y))
    } else {
      x_mapping <- h2o.group_by(encoding_data, c(cols, fold_column), sum(y), nrow(y))
    }
    
    colnames(x_mapping)[which(colnames(x_mapping) == paste0("sum_", y))] <- "numerator"
    colnames(x_mapping)[which(colnames(x_mapping) == "nrow")] <- "denominator"
    
    te_mapping <- c(te_mapping, x_mapping)
  }
  
  names(te_mapping) <- sapply(x, function(i) paste(i, collapse = ":"))
  
  return(te_mapping)
}

#' Apply Target Encoding Map to Frame
#' 
#' Applies a target encoding map to an H2OFrame object.  Computing target encoding for high cardinality 
#' categorical columns can improve performance of supervised learning models. A Target Encoding tutorial 
#' is available here: \url{https://github.com/h2oai/h2o-tutorials/blob/master/best-practices/categorical-predictors/target_encoding.md}.
#' 
#' @param data An H2OFrame object with which to apply the target encoding map.
#' @param x A list containing the names or indices of the variables to encode.  A target encoding column will be created for each element in the list.  Items in the list can be multiple columns.  For example, if `x = list(c("A"), c("B", "C"))`, then the resulting frame will have a target encoding column for A and a target encoding column for B & C (in this case, we group by two columns). 
#' @param y The name or column index of the response variable in the data. The response variable can be either numeric or binary.
#' @param target_encode_map A list of H2OFrame objects that is the results of the \code{\link{h2o.target_encode_create}} function.
#' @param holdout_type The holdout type used. Must be one of: "LeaveOneOut", "KFold", "None".
#' @param fold_column (Optional) The name or column index of the fold column in the data. Defaults to NULL (no `fold_column`). Only required if `holdout_type` = "KFold".
#' @param blended_avg \code{Logical}. (Optional) Whether to perform blended average.
#' @param noise_level (Optional) The amount of random noise added to the target encoding.  This helps prevent overfitting. Defaults to 0.01 * range of y.
#' @param seed (Optional) A random seed used to generate draws from the uniform distribution for random noise. Defaults to -1.
#' @return Returns an H2OFrame object containing the target encoding per record.
#' @seealso \code{\link{h2o.target_encode_create}} for creating the target encoding map
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' 
#' # Get Target Encoding Frame on bank-additional-full data with numeric `y`
#' data <- h2o.importFile(
#' path = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/demos/bank-additional-full.csv",
#' destination_frame = "data")
#' splits <- h2o.splitFrame(data, seed = 1234)
#' train <- splits[[1]]
#' test <- splits[[2]]
#' mapping <- h2o.target_encode_create(data = train, x = list(c("job"), c("job", "marital")), 
#' y = "age")
#' 
#' # Apply mapping to the training dataset
#' train_encode <- h2o.target_encode_apply(data = train, x = list(c("job"), c("job", "marital")), 
#' y = "age", mapping, holdout_type = "LeaveOneOut")
#' # Apply mapping to a test dataset
#' test_encode <- h2o.target_encode_apply(data = test, x = list(c("job"), c("job", "marital")), 
#' y = "age", target_encode_map = mapping, holdout_type = "None")
#' 
#' }
#' @export
h2o.target_encode_apply <- function(data, x, y, target_encode_map, holdout_type, 
                                    fold_column = NULL, blended_avg = TRUE, noise_level = NULL, seed = -1) {
  
  if (missing(data)) {
    stop("argument 'data' is missing, with no default")
  }
  if (missing(target_encode_map)) {
    stop("argument 'target_encode_map' is missing, with no default")
  }
  if (!is.h2o(data)) {
    stop("argument `data` must be a valid H2OFrame")
  }  
  if (!is.logical(blended_avg)) {
    stop("`blended_avg` must be logical")
  }
  if (holdout_type == "KFold") {
    if (is.null(fold_column)) {
      stop("`fold_column` must be provided for `holdout_type = KFold")
    }
  }
  if (!is.null(noise_level)) {
    if (!is.numeric(noise_level) || length(noise_level) > 1L) {
      stop("`noise_level` must be a numeric vector of length 1")
    }  else if (noise_level < 0) {
    stop("`noise_level` must be non-negative")
    }  
  }
  if (is.numeric(y)) {
    y <- colnames(data)[y]
  }
  if (is.numeric(unlist(x))) {
    x <- sapply(x, function(i) colnames(data)[i])
  }
  if (is.numeric(fold_column)) {
    fold_column <- colnames(data)[fold_column]
  }
  
  if (is.null(noise_level)) {
    # If `noise_level` is NULL, value chosen based on `y` distribution
    noise_level <- ifelse(is.factor(data[[y]]), 0.01, (max(data[[y]], na.rm = TRUE) - min(data[[y]], na.rm = TRUE))*0.01)
  }
  
  # Remove string columns from `data` (see: https://0xdata.atlassian.net/browse/PUBDEV-5266)
  dd <- h2o.describe(data)
  string_cols <- as.character(dd[which(dd$Type == "string"), "Label"])
  if (length(string_cols) > 0) {
    data <- data[setdiff(colnames(data), string_cols)]
    warning(paste0("The string columns: ", paste(string_cols, collapse = ", "), " were dropped from the dataset"))
  }
  
  te_frame <- data
  for (cols in x){
    
    x_map <- target_encode_map[[paste(cols, collapse = ":")]]
    
    if (holdout_type == "KFold") {
      
      holdout_encode_map <- NULL
      
      folds <- as.matrix(h2o.unique(x_map[[fold_column]]))[, 1]
      for (i in folds){
        out_fold <- x_map[x_map[[fold_column]] != i, ]
        
        # Calculate sum of y and number of rows per level on out of fold data
        out_fold <- h2o.group_by(out_fold, cols, sum("numerator"), sum("denominator"))
        colnames(out_fold)[which(colnames(out_fold) == "sum_numerator")] <- "numerator"
        colnames(out_fold)[which(colnames(out_fold) == "sum_denominator")] <- "denominator"
        out_fold$fold <- i
        colnames(out_fold)[ncol(out_fold)] <- fold_column
        
        holdout_encode_map <- h2o.rbind(holdout_encode_map, out_fold)
      }
      
      te_frame <- h2o.merge(te_frame, holdout_encode_map, by = c(cols, fold_column), all.x = TRUE)
    }
    
    if (holdout_type == "LeaveOneOut") {
      
      # Merge Target Encoding Mapping to data
      te_frame <- h2o.merge(te_frame, x_map, by = cols, all.x = TRUE, all.y = FALSE)
      
      # Calculate Numerator and Denominator
      te_frame$numerator <- h2o.ifelse(is.na(te_frame[[y]]), 
                                       te_frame$numerator, 
                                       te_frame$numerator - te_frame[[y]])
      
      te_frame$denominator <- h2o.ifelse(is.na(te_frame[[y]]),
                                         te_frame$denominator, 
                                         te_frame$denominator - 1)
    }
    if (holdout_type == "None") {
      
      if (!is.null(fold_column)) {
        # Roll up to the x level - we do not need to know per fold information
        x_map <- h2o.group_by(x_map, cols, sum("numerator"), sum("denominator"))
        colnames(x_map)[which(colnames(x_map) == "sum_denominator")] <- "denominator"
        colnames(x_map)[which(colnames(x_map) == "sum_numerator")] <- "numerator"
      }
      # Merge Target Encoding Mapping to data
      te_frame <- h2o.merge(te_frame, x_map, by = cols, all.x = TRUE, all.y = FALSE)
    }
    
    # Calculate Mean Per Group
    if (blended_avg) {
      
      # Calculate Blended Mean Per Group
      # See https://kaggle2.blob.core.windows.net/forum-message-attachments/225952/7441/high%20cardinality%20categoricals.pdf
      # Equations (3), (4)
      k <- 20
      f <- 10
      global_mean <- sum(x_map$numerator)/sum(x_map$denominator)
      lambda <- 1/(1 + exp((-1)* (te_frame$denominator - k)/f))
      te_frame$target_encode <- ((1 - lambda) * global_mean) + (lambda * te_frame$numerator/te_frame$denominator)
      
    } else {
      
      # Calculate Mean Target per Group
      te_frame$target_encode  <- te_frame$numerator/te_frame$denominator
    }
    
    # Add Random Noise
    if (noise_level > 0) {
      # Generate random floats sampled from a uniform distribution  
      random_noise <- h2o.runif(te_frame, seed = seed)
      # Scale within noise_level
      random_noise <- random_noise * 2 * noise_level - noise_level
      # Add noise to target_encoding
      te_frame$target_encode  <- te_frame$target_encode  + random_noise
    }
    
    te_frame$numerator <- NULL
    te_frame$denominator <- NULL
    colnames(te_frame)[which(colnames(te_frame) == "target_encode")] <- paste0("TargetEncode_", paste(cols, collapse = ":"))
  }
  
  
  return(te_frame)
}
