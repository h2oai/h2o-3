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
#` === Frame/AST Node/environment Fields ===
#
#` E$op     <- Operation or opcode that produces this Frame, a string
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


is.Frame <- function(fr) !missing(fr) && class(fr)[1]=="Frame"
chk.Frame <- function(fr) if( is.Frame(fr) ) fr else stop("must be a Frame")
# Horrible internal shortcut to set our fields, using a more "normal"
# parameter order
.set <- function(x,name,value) attr(x,name) <- value

#' Get back-end distributed key/value store id from a Frame.
#'
#' @param x A Frame
#' @return The id
#' @export
h2o.getId <- function(x) attr( .eval.frame(x), "id")

#' Get the types-per-column
#'
#' @param x A Frame
#' @return A list of types
#' @export
h2o.getTypes <- function(x) attr( .eval.frame(x), "types")

.h2o.gc <- function() {
  gc()
}

# GC Finalizer - called when GC collects a Frame Must be defined ahead of constructors.
.nodeFinalizer <- function(x) {
  eval <- attr(x, "eval")
  if( is.logical(eval) && eval ) {
    #cat("=== Finalizer on ",attr(x, "id"),"\n")
    .h2o.__remoteSend(.h2o.__RAPIDS, h2oRestApiVersion = 99, ast=paste0("(rm ",attr(x, "id"),")"), method = "POST")
  }
}

# Make a raw named data frame.  The key will exist on the server, and will be
# the passed-in ID.  Because it is named, it is not GCd.  It is fully evaluated.
.newFrame <- function(op,id,nrow,ncol) {
  stopifnot( is.character(id) )
  node <- structure(new.env(parent = emptyenv()), class="Frame")
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
  node <- structure(new.env(parent = emptyenv()), class="Frame")
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
    res <- paste0("(",attr(x, "op")," ",paste(sapply( attr(x,"eval"), function(child) { if( is.Frame(child) ) .pfr(child) else child }),collapse=" "),")")
  paste0( attr(x, "id"), ":=", res)
}

# Pretty print the reachable execution DAG from this Frame, withOUT evaluating it
pfr <- function(x) { chk.Frame(x); .pfr(x) }

# Recursively build a rapids execution string; assign the "id" field to count
# executions; flip to using a temp on the 2nd execution.
#
# This call "counts"!!!
# On the 2nd .eval.impl call to any Frame object, the object will be cached as
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
    if(      is.Frame    (child) )                      .eval.impl(child)  # recurse
    else if( is.numeric  (child) && length(child) > 1L ) .num.list(child)  # [ numberz ]  TODO: sup with those NaNs tho
    else if( is.character(child) && length(child) > 1L ) .str.list(child)  # [ stringz ]
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
  if( !is.Frame(x) ) return()
  eval <- attr(x, "eval")
  if( !is.list(eval) ) { stopifnot(is.character( attr(x, "id") )); return() }
  lapply(eval, function(child) .clear.impl(child))
  if( is.character( attr(x, "id")) )
    .set(x,"eval",TRUE) # GC-able temp
}

# Evaluate this Frame, giving the result a name, and never re-execute it.
#
# Because of GC, this algo requires 2 passes over the DAG.  The first pass
# builds the expression string - but it cannot let any of the sub-parts go
# dead, lest GC delete frames on last use... before the expression string is
# shipped over the wire.  During the 2nd pass the internal DAG pointers are
# wiped out, and allowed to go dead (hence can be nuked by GC).
#
.eval.frame <- function(x) {
  id <- attr(chk.Frame(x), "id")
  if( is.character(id) ) return(x)  # Already executed and named
  # Frame does not have a name in the cluster?
  # Act "as if" they're on the 2nd execution - and
  # they will get assigned a temp
  .set(x,"id",NA)
  .eval.driver(x) # Return the evaluated and id'd result
}
.eval.scalar <- function(x) {
  dat <- attr(chk.Frame(x), "data")
  if( !is.null(dat) ) return(dat)   # Return cached scalar
  stopifnot(is.null(attr(x, "id"))) # No names for scalars
  attr(.eval.driver(x),"data")      # Cache and return scalar
}
.eval.driver <- function(x) {
  # Build the AST; this will assign a name as needed
  exec_str <- .eval.impl(x)
  # Execute the AST on H2O
  #print(paste0("EXPR: ",exec_str))
  res <- .h2o.__remoteSend(.h2o.__RAPIDS, h2oRestApiVersion = 99, ast=exec_str, method = "POST")
  if( !is.null(res$error) ) stop(paste0("Error From H2O: ", res$error), call.=FALSE)
  if( !is.null(res$scalar) ) { # Fetch out a scalar answer
    y <- res$scalar
    if( y=="TRUE" ) y <- TRUE
    else if( y=="FALSE" ) y <- FALSE
    .set(x,"data",y)
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
.fetch.data <- function(x,N) {
  stopifnot(!missing(N))
  N <- max(N,10L)  # At least as many as the default head/tail use
  data = attr(chk.Frame(x), "data")
  if( is.null(data) || (is.data.frame(data) && nrow(data) < N) ) {
    res <- .h2o.__remoteSend(paste0(.h2o.__FRAMES, "/", h2o.getId(x), "?row_count=",N))$frames[[1]]
    .set(x,"types",lapply(res$columns, function(c) c$type))
    nrow <- .set.nlen(x,"nrow",res$rows)
    ncol <- .set.nlen(x,"ncol",length(res$columns))
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
        if( !is.null(dom) ) # H2O has a domain; force R to do so also
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
  if( !is.null(attr(x,"data")) ) rm("data" ,envir=x)
  if( !is.null(attr(x,"data")) ) rm("types",envir=x)
  if( !is.null(attr(x,"data")) ) rm("nrow" ,envir=x)
  if( !is.null(attr(x,"data")) ) rm("ncol" ,envir=x)
  x
}

#'
#' Rename an H2O object.
#'
#' Makes a copy of the data frame and gives it the desired the key.
#'
#' @param data An H2O Frame object
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
  x
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
#' @return Returns a Frame object.
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

#' Categorical Interaction Feature Creation in H2O
#'
#' Creates a data frame in H2O with n-th order interaction features between categorical columns, as specified by the user.
#'
#' @param data An H2O Frame object containing the categorical columns.
#' @param destination_frame A string indicating the destination key. If empty, this will be auto-generated by H2O.
#' @param factors Factor columns (either indices or column names).
#' @param pairwise Whether to create pairwise interactions between factors (otherwise create one higher-order interaction). Only applicable if there are 3 or more factors.
#' @param max_factors Max. number of factor levels in pair-wise interaction terms (if enforced, one extra catch-all factor will be made)
#' @param min_occurrence Min. occurrence threshold for factor levels in pair-wise interaction terms
#' @return Returns a Frame object.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#'
#' # Create some random data
#' myframe = h2o.createFrame('framekey', rows = 20, cols = 5,
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
  chk.Frame(data)
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
  if(missing(destination_frame) || !is.character(destination_frame) || !nzchar(destination_frame))
    parms$dest = .key.make(prefix = "interaction")
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
#' \code{h2o.rep} performs just as \code{rep} does. It replicates the values in
#' \code{x} in the H2O backend.
#'
#' @param x a vector (of any mode including a list) or a factor
#' @param length.out non negative integer. The desired length of the output
#'        vector.
#' @return Creates a Frame vector of the same type as x
#' @export
h2o.rep_len <- function(x, length.out) {
  if (length.out <= 0)  NULL
  else                  .newExpr("rep_len", x, length.out)
}

#' Inserting Missing Values to an H2O DataFrame
#'
#' *This is primarily used for testing*. Randomly replaces a user-specified fraction of
#' entries in a H2O dataset with missing values.
#'
#' @param data An H2O Frame object representing the dataset.
#' @param fraction A number between 0 and 1 indicating the fraction of entries
#'        to replace with missing.
#' @param seed A random number used to select which entries to replace with
#'        missing values. Default of \code{seed = -1} will automatically
#'        generate a seed in H2O.
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
  .flush.data(data)  # Flush cache and return data
}

#' Split an H2O Data Set
#'
#' Split an existing H2O data set according to user-specified ratios.
#'
#' @param data An H2O Frame object representing the dataste to split.
#' @param ratios A numeric value or array indicating the ratio of total rows
#'        contained in each split. Must total up to less than 1.
#' @param destination_frames An array of frame IDs equal to the number of ratios
#'        specified plus one.
#' @param seed Random seed.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' irisPath = system.file("extdata", "iris.csv", package = "h2o")
#' iris.hex = h2o.importFile(path = irisPath)
#' iris.split = h2o.splitFrame(iris.hex, ratios = c(0.2, 0.5))
#' head(iris.split[[1]])
#' summary(iris.split[[1]])
#' }
#' @export
h2o.splitFrame <- function(data, ratios = 0.75, destination_frames, seed = -1) {
  chk.Frame(data)

  if (! is.numeric(ratios)) stop("ratios must be of type numeric")
  if (length(ratios) < 1) stop("ratios must have length of at least 1")

  if (! missing(destination_frames)) {
    if (! is.character(destination_frames)) stop("destination_frames must be of type character")
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
#' @export
h2o.filterNACols <- function(data, frac=0.2) {
  (as.data.frame(.newExpr("filterNACols", data, frac)) + 1)[,1]  # 0 to 1 based index
}

#' Cross Tabulation and Table Creation in H2O
#'
#' Uses the cross-classifying factors to build a table of counts at each combination of factor levels.
#'
#' @param x An H2O Frame object with at most two columns.
#' @param y An H2O Frame similar to x, or \code{NULL}.
#' @return Returns a tabulated Frame object.
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
h2o.table <- function(x, y = NULL) {
  chk.Frame(x)
  if( !is.null(y) ) chk.Frame(y)
  if( is.null(y) ) .newExpr("table",x) else .newExpr("table",x,y)
}

#' @rdname h2o.table
#' @export
table.Frame <- h2o.table

#' H2O Median
#'
#' Compute the median of a Frame.
#'
#' @param x An H2O Frame object.
#' @param na.rm a logical, indicating whether na's are omitted.
#' @examples
#' \donttest{
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath, destination_frame = "prostate.hex")
#' }
#' @export
h2o.median <- function(x, na.rm = TRUE) .eval.scalar(.newExpr("median",x,na.rm))

#' @rdname h2o.median
median.Frame <- h2o.median

#' Cut H2O Numeric Data to Factor
#'
#' Divides the range of the H2O data into intervals and codes the values according to which interval they fall in. The
#' leftmost interval corresponds to the level one, the next is level two, etc.
#'
#' @param x An H2O Frame object with numeric columns.
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
#' @return Returns an H2O Frame object containing the factored data with intervals as levels.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' irisPath <- system.file("extdata", "iris_wheader.csv", package="h2o")
#' iris.hex <- h2o.uploadFile(path = irisPath, destination_frame = "iris.hex")
#' summary(iris.hex)
#'
#' # Cut sepal length column into intervals determined by min/max/quantiles
#' sepal_len.cut = cut(iris.hex$sepal_len, c(4.2, 4.8, 5.8, 6, 8))
#' head(sepal_len.cut)
#' summary(sepal_len.cut)
#' }
#' @export
h2o.cut <- function(x, breaks, labels = NULL, include.lowest = FALSE, right = TRUE, dig.lab = 3, ...) {
  if (!is.numeric(breaks) || length(breaks) == 0L || !all(is.finite(breaks)))
    stop("`breaks` must be a numeric vector")
  .newExpr("cut", chk.Frame(x), breaks, labels, include.lowest, right, dig.lab)
}

#' @rdname h2o.cut
#' @export
cut.Frame <- h2o.cut

# `match` or %in% for Frame
#' Value Matching in H2O
#'
#' \code{match} and \code{\%in\%} return values similar to the base R generic
#' functions.
#'
#' @param x a categorical vector from an H2O Frame object with
#'        values to be matched.
#' @param table an R object to match \code{x} against.
#' @param nomatch the value to be returned in the case when no match is found.
#' @param incomparables a vector of calues that cannot be matched. Any value in
#'        \code{x} matching a value in this vector is assigned the
#'        \code{nomatch} value.
#' @seealso \code{\link[base]{match}} for base R implementation.
#' @examples
#' \donttest{
#' h2o.init()
#' hex <- as.h2o(iris)
#' h2o.match(hex[,5], c("setosa", "versicolor"))
#' }
#' @export
h2o.match <- function(x, table, nomatch = 0, incomparables = NULL) {
  if( !is.Frame(table) && length(table)==1 && is.character(table) ) table <- .quote(table)
  .newExpr("match", chk.Frame(x), table, nomatch, incomparables)
}

#' @rdname h2o.match
#' @export
match.Frame <- h2o.match

# %in% method
#' @rdname h2o.match
#' @export
`%in%` <- function(x,table) {
  if( is.Frame(x) ) h2o.match(x,table,nomatch=0)
  else base::`%in%`(x,table)
}

#' Remove Rows With NAs
#'
#' @rdname na.omit
#' @param object Frame object
#' @param ... Ignored
#' @export
na.omit.Frame <- function(object, ...) .newExpr("na.omit", object)

#' Compute DCT of an H2O Frame
#'
#' Compute the Discrete Cosine Transform of every row in the Frame
#'
#' @param data An H2O Frame object representing the dataset to transform
#' @param destination_frame A frame ID for the result
#' @param dimensions An array containing the 3 integer values for height, width, depth of each sample.
#'        The product of HxWxD must total up to less than the number of columns.
#'        For 1D, use c(L,1,1), for 2D, use C(N,M,1).
#' @param inverse Whether to perform the inverse transform
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
  h2o.getFrame(res$destination_frame$name)
}

#-----------------------------------------------------------------------------------------------------------------------
# Time & Date
#-----------------------------------------------------------------------------------------------------------------------

#' Convert Milliseconds to Years in H2O Datasets
#'
#' Convert the entries of a Frame object from milliseconds to years, indexed
#' starting from 1900.
#'
# is this still true?
#' This method calls the function of the MutableDateTime class in Java.
#' @param x An H2O Frame object.
#' @return A Frame object containig the entries of \code{x} converted to years
#'         starting from 1900, e.g. 69 corresponds to the year 1969.
#' @seealso \code{\link{h2o.month}}
#' @export
h2o.year <- function(x) .newExpr("-",.newExpr("year", chk.Frame(x)),1900)

#' Convert Milliseconds to Months in H2O Datasets
#'
#' Converts the entries of a Frame object from milliseconds to months (on a 1 to
#' 12 scale).
#'
#' @param x An H2O Frame object.
#' @return A Frame object containing the entries of \code{x} converted to months of
#'         the year.
#' @seealso \code{\link{h2o.year}}
#' @export
h2o.month <- function(x) .newExpr("month", chk.Frame(x))

#' Convert Milliseconds to Week of Week Year in H2O Datasets
#'
#' Converts the entries of a Frame object from milliseconds to weeks of the week
#' year (starting from 1).
#'
#' @param x An H2O Frame object.
#' @return A Frame object containing the entries of \code{x} converted to weeks of
#'         the week year.
#' @seealso \code{\link{h2o.month}}
#' @export
h2o.week <- function(x) .newExpr("week", chk.Frame(x))

#' Convert Milliseconds to Day of Month in H2O Datasets
#'
#' Converts the entries of a Frame object from milliseconds to days of the month
#' (on a 1 to 31 scale).
#'
#' @param x An H2O Frame object.
#' @return A Frame object containing the entries of \code{x} converted to days of
#'         the month.
#' @seealso \code{\link{h2o.month}}
#' @export
h2o.day <- function(x) .newExpr("day", chk.Frame(x))

#' Convert Milliseconds to Day of Week in H2O Datasets
#'
#' Converts the entries of a Frame object from milliseconds to days of the week
#' (on a 0 to 6 scale).
#'
#' @param x An H2O Frame object.
#' @return A Frame object containing the entries of \code{x} converted to days of
#'         the week.
#' @seealso \code{\link{h2o.day}, \link{h2o.month}}
#' @export
h2o.dayOfWeek <- function(x) .newExpr("dayOfWeek", chk.Frame(x))

#' Convert Milliseconds to Hour of Day in H2O Datasets
#'
#' Converts the entries of a Frame object from milliseconds to hours of the day
#' (on a 0 to 23 scale).
#'
#' @param x An H2O Frame object.
#' @return A Frame object containing the entries of \code{x} converted to hours of
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

#' Produce a Vector of Random Uniform Numbers
#'
#' Creates a vector of random uniform numbers equal in length to the length of the specified H2O
#' dataset.
#'
#' @param x An H2O Frame object.
#' @param seed A random seed used to generate draws from the uniform distribution.
#' @return A vector of random, uniformly distributed numbers. The elements are between 0 and 1.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
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
#' }
#' @export
h2o.runif <- function(x, seed = -1) {
  if (!is.numeric(seed) || length(seed) != 1L || !is.finite(seed)) stop("`seed` must be an integer >= 0")
  if (seed == -1) seed <- floor(runif(1,1,.Machine$integer.max*100))
  .newExpr("h2o.runif", chk.Frame(x), seed)
}

#' Check Frame columns for factors
#'
#' Determines if any column of an H2O Frame object contains categorical data.
#'
#' @name h2o.anyFactor
#' @param x An \code{Frame} object.
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
    sub <- if( eval(sel[[2]], envir=envir) < 0 ) 0 else 1L
    s <- paste0( "[", eval(sel[[2]], envir=envir) - sub, ":", abs(eval(sel[[3]], envir=envir) - eval(sel[[2]], envir=envir))+1L, "]")
    return( s )
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

#' Extract or Replace Parts of an H2O Frame Object
#'
#' Operators to extract or replace parts of Frame objects.
#'
#' @name Frame-Extract
NULL

#' @aliases [,Frame-method
#' @rdname Frame-Extract
#' @param data object from which to extract element(s) or in which to replace element(s).
#' @param row index specifying row element(s) to extract or replace. Indices are numeric or
#'        character vectors or empty (missing) or will be matched to the names.
#' @param col index specifying column element(s) to extract or replace.
#' @param drop Unused
#' @export
`[.Frame` <- function(data,row,col,drop=TRUE) {
  chk.Frame(data)

  # This function is called with a huge variety of argument styles
  # Here's the breakdown:
  #   Style          Type #args  Description
  # df[]           - na na 2    both missing, identity with df
  # df["colname"]  - c  na 2    single column by name, df$colname
  # df[3]          - X  na 2    if ncol > 1 then column else row
  # df[,]          - na na 3    both missing, identity with df
  # df[2,]         - r  na 3    constant row, all cols
  # df[1:150,]     - r  na 3    selection of rows, all cols
  # df[,3]         - na c  3    constant column
  # df[,1:10]      - na c  3    selection of columns
  # df[,"colname"] - na c  3    single column by name
  # df[2,"colname"]- r  c  3    row slice and column-by-name
  # df[2,3]        - r  c  3    single element
  # df[1:150,1:10] - r  c  3    rectangular slice
  # df[a<b,]       - f  na 3    boolean row slice
  # df[a<b,c]      - f  c  3    boolean row slice
  is1by1 <- !missing(col) && !missing(row) && !is.Frame(row) && length(col) == 1 && length(row) == 1
  if( nargs() == 2 &&   # Only row, no column; nargs==2 distinguishes "df[2,]" (row==2) from "df[2]" (col==2)
      # is.char tells cars["cylinders"], or if there are multiple columns.
      # Single column with numeric selector is row: car$cylinders[100]
      (is.character(row) || ncol(data) > 1) ) {
    # Row is really column: cars[3] or cars["cylinders"] or cars$cylinders
    col <- row
    row <- NA
  }
  if( !missing(col) ) {     # Have a column selector?
    if( is.logical(col) ) { # Columns by boolean choice
      col <- which(col)     # Pick out all the TRUE columns by index
    } else if( is.character(col) ) {   # Columns by name
      idx <- match(col,colnames(data)) # Match on name
      if( any(is.na(idx)) ) stop(paste0("No column '",col,"' found in ",paste(colnames(data),collapse=",")))
      col <- idx
    }
    idx <- .row.col.selector(col,envir=parent.frame()) # Generic R expression
    data <- .newExpr("cols",data,idx) # Column selector
  }
  # Have a row selector?
  if( !missing(row) && (is.Frame(row) || !is.na(row)) ) {
    if( !is.Frame(row) )    # Generic R expression
      row <- .row.col.selector(substitute(row), row,envir=parent.frame())
    data <- .newExpr("rows",data,row) # Row selector
  }
  if( is1by1 ) .fetch.data(data,1L)[[1]]
  else         data
}

#' @rdname Frame-Extract
#' @param x An H2O Frame
#' @param name a literal character string or a name (possibly backtick quoted).
#' @export
`$.Frame` <- function(x, name) { x[[name, exact = FALSE]] }

#' @rdname Frame-Extract
#' @param i index
#' @param exact controls possible partial matching of \code{[[} when extracting
#'              a character
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

#' S3 Group Generic Functions for H2O
#'
#' Methods for group generic functions and H2O objects.
#'
#' @rdname Frame
#' @param e1 object
#' @param e2 object
#' @export
Ops.Frame <- function(e1,e2)
  .newExpr(.Generic,
           if( is.character(e1) ) .quote(e1) else e1,
           if( is.character(e2) ) .quote(e2) else e2)

#' @rdname Frame
#' @param x object
#' @export
Math.Frame <- function(x) .newExpr(.Generic,x)

#' @rdname Frame
#' @param y object
#' @export
Math.Frame <- function(x,y) .newExpr(.Generic,x,y)

#' @rdname Frame
#' @param ... Further arguments passed to or from other methods.
#' @export
Math.Frame <- function(x,...) .newExprList(.Generic,list(x,...))

#' @rdname Frame
#' @param na.rm logical. whether or not missing values should be removed
#' @export
Summary.Frame <- function(x,...,na.rm) {
  if( na.rm ) stop("na.rm versions not impl")
  # Eagerly evaluation, to produce a scalar
  res <- .eval.scalar(.newExprList(.Generic,list(x,...)))
  if( .Generic=="all" ) as.logical(res) else res
}


#' @rdname Frame
#' @export
`!.Frame` <- function(x) .newExpr("!!",x)

#' @rdname Frame
#' @export
is.na.Frame <- function(x) .newExpr("is.na", x)

#' @rdname Frame
#' @export
t.Frame <- function(x) .newExpr("t",x)

#' @rdname Frame
#' @export
log <- function(x, ...) {
  if( !is.Frame(x) ) .Primitive("log")(x)
  else .newExpr("log",x)
}

#' @rdname Frame
#' @export
trunc <- function(x, ...) {
  if( !is.Frame(x) ) .Primitive("trunc")(x)
  else .newExpr("trunc",x)
}

#' @rdname Frame
#' @export
`%*%` <- function(x, y) {
  if( !is.Frame(x) ) .Primitive("%*%")(x,y)
  else .newExpr("x",x,y)
}

#' Returns the Dimensions of an H2O Frame
#'
#' Returns the number of rows and columns for a Frame object.
#'
#' @param x An H2O Frame object.
#' @seealso \code{\link[base]{dim}} for the base R method.
#' @examples
#' \donttest{
#' h2o.init()
#' iris.hex <- as.h2o(iris)
#' dim(iris.hex)
#' }
#' @export
dim.Frame <- function(x) { .eval.frame(x); c(attr(x, "nrow"), attr(x,"ncol")) }

#' @rdname Frame
#' @export
nrow.Frame <- function(x) attr(.eval.frame(x), "nrow")

#' @rdname Frame
#' @export
ncol.Frame <- function(x) attr(.eval.frame(x), "ncol")

#' Column names of an H2O Frame
#' @param x A Frame
#' @export
dimnames.Frame <- function(x) .Primitive("dimnames")(.fetch.data(x,1L))

#' Column names of an H2O Frame
#' @param x A Frame
#' @export
names.Frame <- function(x) .Primitive("names")(.fetch.data(x,1L))

#' Returns the column names of a Frame
#'
#' @param x An H2O Frame object.
#' @param do.NULL logical. If FALSE and names are NULL, names are created.
#' @param prefix for created names.
#' @export
colnames <- function(x, do.NULL=TRUE, prefix = "col") {
  if( !is.Frame(x) ) return(base::colnames(x,do.NULL,prefix))
  return(names.Frame(x))
}

#' @rdname Frame
#' @export
length.Frame <- function(x) attr(.eval.frame(x),"ncol")

#' @rdname Frame
#' @export
h2o.length <- length.Frame

#'
#' Return the levels from the column requested column.
#'
#' @param x An H2O Frame object.
#' @param i The index of the column whose domain is to be returned.
#' @seealso \code{\link[base]{levels}} for the base R method.
#' @examples
#' \donttest{
#' iris.hex <- as.h2o(iris)
#' h2o.levels(iris.hex, 5)  # returns "setosa"     "versicolor" "virginica"
#' }
#' @export
h2o.levels <- function(x, i) {
  df <- .fetch.data(x,1L)
  if( missing(i) ) levels(df[[1]])
  else levels(df[[i]])
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
#' Returns the first or last rows of an H2O Frame object.
#'
#' @name h2o.head
#' @param x An H2O Frame object.
#' @param n (Optional) A single integer. If positive, number of rows in x to return. If negative, all but the n first/last number of rows in x.
#' @param ... Further arguments passed to or from other methods.
#' @return A Frame containing the first or last n rows of an H2O Frame object.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init(ip = "localhost", port = 54321, startH2O = TRUE)
#' ausPath <- system.file("extdata", "australia.csv", package="h2o")
#' australia.hex <- h2o.uploadFile(path = ausPath)
#' head(australia.hex, 10)
#' tail(australia.hex, 10)
#' }
#' @export
h2o.head <- function(x, ..., n=6L) {
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
head.Frame <- h2o.head

#' @rdname h2o.head
#' @export
h2o.tail <- function(x, ..., n=6L) {
  endidx <- nrow(x)
  n <- ifelse(n < 0L, max(endidx + n, 0L), min(n, endidx))
  if( n==0L ) head(x,n=0L)
  else {
    startidx <- max(1L, endidx - n + 1)
    as.data.frame(.newExpr("rows",x,paste0("[",startidx-1,":",(endidx-startidx+1),"]")))
  }
}

#' @rdname h2o.head
#' @export
tail.Frame <- h2o.tail

#' Check if factor
#'
#' @rdname is.factor
#' @param x An H2O Frame object
#' @export
is.factor <- function(x) {
  # Eager evaluate and use the cached result to return a scalar
  if( is.Frame(x) ) {
    x <- .fetch.data(x,1L)
    if( ncol(x)==1L ) x <- x[,1]
  }
  base::is.factor(x)
}

#' Check if numeric
#'
#' @rdname is.numeric
#' @param x An H2O Frame object
#' @export
is.numeric <- function(x) {
  if( !is.Frame(x) ) .Primitive("is.numeric")(x)
  else as.logical(.eval.scalar(.newExpr("is.numeric",x)))
}

#' Print An H2O Frame
#'
#' @param x An H2O Frame object
#' @param ... Further arguments to be passed from or to other methods.
#' @export
print.Frame <- function(x, ...) { 
  print(head(x))
  cat(paste0("[", nrow(x), " rows x ", ncol(x), " columns]"), "\n")
}

#' Display the structure of an H2O Frame object
#'
#' @param object An H2O Frame.
#' @param ... Further arguments to be passed from or to other methods.
#' @param cols Print the per-column str for the Frame
#' @export
str.Frame <- function(object, ..., cols=FALSE) {
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
    cat("\nFrame '", attr(object, "id"), "':\t", nr, " obs. of  ", nc, " variable(s)", "\n", sep = "")
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
#' @param ... Further arguments passed to or from other methods.
#' @param value To be assigned
#' @export
`[<-.Frame` <- function(data,row,col,...,value) {
  chk.Frame(data)
  allRow <- missing(row)
  allCol <- missing(col)
  if( !allCol && is.na(col) ) col <- as.list(match.call())$col

  # Named column assignment; the column name was passed in as "row"
  # fr["baz"] <- qux
  # fr$ baz   <- qux
  if( !allRow && is.character(row) && allCol ) {
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
      stop("`value` can only be an H2O Frame object or a numeric or character vector")
  }

  # Row arg is missing, means "all the rows"
  if(allRow) rows <- paste0("[]") # Shortcut for "all rows"
  else       rows <- .row.col.selector(substitute(row), row,envir=parent.frame())

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
    if( idx==ncol(data)+1 && is.na(name) ) name <- paste0("C",idx)
    cols <- .row.col.selector(idx, envir=parent.frame())
  }

  if( is.character(value) ) value <- .quote(value)
  # Set col name and return updated frame
  if( is.na(name) ) .newExpr(":=", data, value, cols, rows)
  else              .newExpr("append", data, value, .quote(name))
}

#' @rdname Frame-Extract
#' @export
`$<-.Frame`  <- function(data, name, value) `[<-.Frame`(data,row=name,value=value)

#' @rdname Frame-Extract
#' @export
`[[<-.Frame` <- function(data, name, value) `[<-.Frame`(data,row=name,value=chk.Frame(value))

#' @rdname Frame
#' @param value To be assigned
#' @export
`names<-.Frame` <- function(x, value) {
  .newExpr("colnames=", x, paste0("[0:",ncol(x),"]"), .str.list(value))
}

#' @rdname Frame
#' @export
`colnames<-` <- function(x, value) {
  if( !is.Frame(x) ) return(base::`colnames<-`(x,value))
  return(`names<-.Frame`(x,if( is.Frame(value) ) colnames(value) else value))
}

#'
#' Quantiles of H2O Frames.
#'
#' Obtain and display quantiles for H2O parsed data.
#'
#' \code{quantile.Frame}, a method for the \code{\link{quantile}} generic. Obtain and return quantiles for
#' an \code{Frame} object.
#'
#' @name h2o.quantile
#' @param x An \code{Frame} object with a single numeric column.
#' @param probs Numeric vector of probabilities with values in [0,1].
#' @param combine_method How to combine quantiles for even sample sizes. Default is to do linear interpolation.
#'                       E.g., If method is "lo", then it will take the lo value of the quantile. Abbreviations for average, low, and high are acceptable (avg, lo, hi).
#' @param ... Further arguments passed to or from other methods.
#' @return A vector describing the percentiles at the given cutoffs for the \code{Frame} object.
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
#' @export
h2o.quantile <- function(x,
                     # AUTOGENERATED params
                     probs = c(0.001, 0.01, 0.1, 0.25, 0.333, 0.5, 0.667, 0.75, 0.9, 0.99, 0.999),
                     combine_method = c("interpolate", "average", "avg", "low", "high"),
                     ...)
{
  # verify input parameters
  if (!is(x, "Frame")) stop("`x` must be an H2O Frame object")
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

#' @rdname h2o.quantile
#' @export
quantile.Frame <- h2o.quantile

#'
#' Summarizes the columns of a H2O data frame.
#'
#' A method for the \code{\link{summary}} generic. Summarizes the columns of an H2O data frame or subset of
#' columns and rows using vector notation (e.g. dataset[row, col])
#'
#' @name h2o.summary
#' @param object An H2O Frame object.
#' @param factors The number of factors to return in the summary. Default is the top 6.
#' @param ... Further arguments passed to or from other methods.
#' @return A table displaying the minimum, 1st quartile, median, mean, 3rd quartile and maximum for each
#' numeric column, and the levels and category counts of the levels in each categorical column.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' prosPath = system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex = h2o.importFile(path = prosPath)
#' summary(prostate.hex)
#' summary(prostate.hex$GLEASON)
#' summary(prostate.hex[,4:6])
#' }
#' @export
h2o.summary <- function(object, factors=6L, ...) {
  SIG.DIGITS    <- 12L
  FORMAT.DIGITS <- 4L
  cnames <- colnames(object)
  missing <- list()

  # for each numeric column, collect [min,1Q,median,mean,3Q,max]
  # for each categorical column, collect the first 6 domains
  # allow for optional parameter in ... factors=N, for N domain levels. Or could be the string "all". N=6 by default.
  fr.sum <- .h2o.__remoteSend(paste0("Frames/", attr(object, "id"), "/summary"), method = "GET")$frames[[1]]
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
      if( length(domain.cnts) < length(domains) ) {
        if( length(domain.cnts) == 1 )  {   # Constant categorical column
          cnt <- domain.cnts[1]
          domain.cnts <- rep(NA, length(domains))
          domain.cnts[col.sum$data[1]+1] <- cnt
        } else
          domain.cnts <- c(domain.cnts, rep(NA, length(domains) - length(domain.cnts)))
      }
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
  result
}

#' @rdname h2o.summary
#' @S3method summary Frame
#' @usage \\method{summary}{Frame}(object, factors, ...)
#' @export
summary.Frame <- h2o.summary

#-----------------------------------------------------------------------------------------------------------------------
# Summary Statistics Operations
#-----------------------------------------------------------------------------------------------------------------------

#'
#' Mean of a column
#'
#' Obtain the mean of a column of a parsed H2O data object.
#'
#' @name h2o.mean
#' @param x An H2O Frame object.
#' @param ... Further arguments to be passed from or to other methods.
#' @param na.rm A logical value indicating whether \code{NA} or missing values should be stripped before the computation.
#' @seealso \code{\link[base]{mean}} for the base R implementation.
#' @examples
#' \donttest{
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath)
#' mean(prostate.hex$AGE)
#' }
#' @export
h2o.mean <- function(x, ..., na.rm=TRUE) .eval.scalar(.newExpr("mean",x,na.rm))

#' @rdname h2o.mean
#' @export
mean.Frame <- h2o.mean

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
#' @param x An H2O Frame object.
#' @param y \code{NULL} (default) or a column of an H2O Frame object. The default is equivalent to y = x (but more efficient).
#' @param na.rm \code{logical}. Should missing values be removed?
#' @param use An optional character string to be used in the presence of missing values. This must be one of the following strings. "everything", "all.obs", or "complete.obs".
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
  if( na.rm ) stop("na.rm versions not impl")
  if( is.null(y) ) y <- x
  if(!missing(use)) {
    if (use %in% c("pairwise.complete.obs", "na.or.complete"))
      stop("Unimplemented : `use` may be either \"everything\", \"all.obs\", or \"complete.obs\"")
  } else
    use <- "everything"
  # Eager, mostly to match prior semantics but no real reason it need to be
  expr <- .newExpr("var",x,y,.quote(use))
  if( (nrow(x)==1L || ncol(x)==1L) ) .eval.scalar(expr)
  else .fetch.data(expr,ncol(x))
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
#' @name h2o.sd
#' @param x An H2O Frame object.
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
  if( is.Frame(x) ) h2o.sd(x,na.rm)
  else stats::sd(x,na.rm)
}

#'
#' Scaling and Centering of an H2O Frame
#'
#' Centers and/or scales the columns of an H2O dataset.
#'
#' @name h2o.scale
#' @param x An H2O Frame object.
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
h2o.scale <- function(x, center = TRUE, scale = TRUE) .newExpr("scale", chk.Frame(x), center, scale)

#' @rdname h2o.scale
#' @export
scale.Frame <- h2o.scale

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
  types <- gsub("integer64", "numeric", types)
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
#' @param x An H2O Frame object.
#' @param ... Further arguments to be passed down from other methods.
#' @examples
#' \donttest{
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath)
#' as.data.frame(prostate.hex)
#' }
#' @export
as.data.frame.Frame <- function(x, ...) {
  # Force loading of the types
  .fetch.data(x,1L)
  # Versions of R prior to 3.1 should not use hex string.
  # Versions of R including 3.1 and later should use hex string.
  use_hex_string <- getRversion() >= "3.1"
  conn = h2o.getConnection()

  url <- paste0('http://', conn@ip, ':', conn@port,
                '/3/DownloadDataset',
                '?frame_id=', URLencode( h2o.getId(x)),
                '&hex_string=', as.numeric(use_hex_string))

  ttt <- getURL(url)
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
  # Substitute NAs for blank cells rather than skipping
  df <- read.csv((tcon <- textConnection(ttt)), blank.lines.skip = FALSE, na.strings = "", colClasses = colClasses, ...)
  close(tcon)
  # Convert all date columns to POSIXct
  dates <- attr(x, "types") %in% "time"
  if (length(dates) > 0) # why do some frames come in with no attributes but many columns?
    for (i in 1:length(dates)) { if (dates[[i]]) class(df[[i]]) = "POSIXct" }
  df
}

#' Convert an H2O Frame to a matrix
#'
#' @param x An H2O Frame object
#' @param ... Further arguments to be passed down from other methods.
#' @export
as.matrix.Frame <- function(x, ...) as.matrix(as.data.frame(x, ...))

#' Convert an H2O Frame to a vector
#'
#' @name as.vector
#' @param x An H2O Frame object
#' @param mode Unused
#' @S3method as.vector Frame
#' @usage \\method{as.vector}{Frame}(x,mode)
#' @export
as.vector.Frame <- function(x, mode) base::as.vector(as.matrix.Frame(x))

#`
#' @export
as.double.Frame <- function(x, ...) {
  res <- .fetch.data(x,1L) # Force evaluation
  if( nrow(res)!=1L || ncol(res)!=1L ) stop("Cannot convert multi-element Frame into a double")
  res <- res[1,1]
  .Primitive("as.double")(res)
}

#' @export
as.logical.Frame <- function(x, ...) {
  res <- .fetch.data(x,1L) # Force evaluation
  if( nrow(res)!=1L || ncol(res)!=1L ) stop("Cannot convert multi-element Frame into a logical")
  res <- res[1,1]
  .Primitive("as.logical")(res)
}

#' @export
as.integer.Frame <- function(x, ...) {
  x <- .fetch.data(x,1L) # Force evaluation
  if( nrow(x)!=1L || ncol(x)!=1L ) stop("Cannot convert multi-element Frame into an integer")
  x <- x[1,1]
  .Primitive("as.integer")(x)
}

#' Convert H2O Data to Factors
#'
#' Convert a column into a factor column.
#' @param x a column from an H2O Frame data set.
#' @seealso \code{\link{is.factor}}.
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
  if( is.Frame(x) ) .newExpr("as.factor",x)
  else base::as.factor(x)
}


#' Convert an H2O Frame to a String
#'
#' @param x An H2O Frame object
#' @param ... Further arguments to be passed from or to other methods.
#' @export
as.character.Frame <- function(x, ...) {
  if( is.Frame(x) ) .newExpr("as.character",x)
  else base::as.character(x)
}

#' Convert H2O Data to Numeric
#'
#' Converts an H2O column into a numeric value column.
#' @param x a column from an H2O Frame data set.
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
  if( is.Frame(x) ) .newExpr("as.numeric",x)
  else base::as.numeric(x)
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
  .newExpr("cols",data,.row.col.selector(-del.cols,envir=parent.frame()))
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
#' @name h2o.ifelse
#' @param test A logical description of the condition to be met (>, <, =, etc...)
#' @param yes The value to return if the condition is TRUE.
#' @param no The value to return if the condition is FALSE.
#' @return Returns a vector of new values matching the conditions stated in the ifelse call.
#' @examples
#' \donttest{
#' h2o.init()
#' ausPath = system.file("extdata", "australia.csv", package="h2o")
#' australia.hex = h2o.importFile(path = ausPath)
#' australia.hex[,9] <- ifelse(australia.hex[,3] < 279.9, 1, 0)
#' summary(australia.hex)
#' }
#' @export
h2o.ifelse <- function(test, yes, no) {
  if( !is.Frame(yes) && is.character(yes) ) yes <- .quote(yes)
  if( !is.Frame(no)  && is.character(no ) ) no  <- .quote(no )
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
        if( is.Frame(yes) ) return(yes[,1])
      } else {
        if( length(no) == 1 && is.null(attributes(no)) )
          return(no)
        if( is.Frame(no) ) return(no[,1])
      }
    }
  }
  if( is.Frame(test) || is.Frame(yes) || is.Frame(no) ) return(h2o.ifelse(test,yes,no))
  else base::ifelse(test,yes,no)
}

#' Combine H2O Datasets by Columns
#'
#' Takes a sequence of H2O data sets and combines them by column
#'
#' @name h2o.cbind
#' @param \dots A sequence of Frame arguments. All datasets must exist on the same H2O instance
#'        (IP and port) and contain the same number of rows.
#' @return An H2O Frame object containing the combined \dots arguments column-wise.
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
  lapply(li, function(l) chk.Frame(l) )
  .newExprList("cbind",li)
}

#' Combine H2O Datasets by Rows
#'
#' Takes a sequence of H2O data sets and combines them by rows
#'
#' @name h2o.rbind
#' @param \dots A sequence of Frame arguments. All datasets must exist on the same H2O instance
#'        (IP and port) and contain the same number of rows.
#' @return An H2O Frame object containing the combined \dots arguments column-wise.
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
  klazzez <- unlist(lapply(l, function(i) is.Frame(i)))
  if (any(!klazzez)) stop("`h2o.rbind` accepts only Frame objects")
  .newExprList("rbind", l)
}

#' Merge Two H2O Data Frames
#'
#' Merges two Frame objects by shared column names. Unlike the
#' base R implementation, \code{h2o.merge} only supports merging through shared
#' column names.
#'
#' In order for \code{h2o.merge} to work in multinode clusters, one of the
#' datasets must be small enough to exist in every node. Currently, this
#' function only supports \code{all.x = TRUE}. All other permutations will fail.
#'
#' @param x,y Frame objects
#' @param all.x a logical value indicating whether or not shared values are
#'        preserved or ignored in \code{x}.
#' @param all.y a logical value indicating whether or not shared values are
#'        preserved or ignored in \code{y}.
#' @examples
#' \donttest{
#' h2o.init()
#' left <- data.frame(fruit = c('apple', 'orange', 'banana', 'lemon', 'strawberry', 'blueberry'),
#' color = c('red', 'orange', 'yellow', 'yellow', 'red', 'blue'))
#' right <- data.frame(fruit = c('apple', 'orange', 'banana', 'lemon', 'strawberry', 'watermelon'),
#' citrus = c(FALSE, TRUE, FALSE, TRUE, FALSE, FALSE))
#' l.hex <- as.h2o(left)
#' r.hex <- as.h2o(right)
#' left.hex <- h2o.merge(l.hex, r.hex, all.x = TRUE)
#' }
#' @export
h2o.merge <- function(x, y, all.x = TRUE, all.y = FALSE) .newExpr("merge", x, y, all.x, all.y)

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
#' @param data an H2O Frame object.
#' @param by a list of column names
#' @param \dots any supported aggregate function.
#' @param order.by Takes a vector column names or indices specifiying how to order the group by result.
#' @param gb.control a list of how to handle \code{NA} values in the dataset as well as how to name
#'        output columns. See \code{Details:} for more help.
#' @return Returns a new Frame object with columns equivalent to the number of
#'         groups created
#' @export
h2o.group_by <- function(data, by, ..., order.by=NULL, gb.control=list(na.methods=NULL, col.names=NULL)) {
  # Build the argument list: (GB data, [group.by] [order.by] {agg col "na"}...)
  args <- list(chk.Frame(data))

  ### handle the columns
  # we accept: c('col1', 'col2'), 1:2, c(1,2) as column names.
  if(is.character(by)) {
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

  ### ORDER BY ###
  order.by.cols <- NULL
  if( !is.null(order.by) ) {
    if(is.character(order.by)) {
        order.by.cols <- match(order.by, by)
        if (any(is.na(order.by.cols)))
          stop('No column named ', order.by, ' in ', by, '.')
    } else if(is.integer(order.by)) {
      order.by.cols <- order.by
    } else if(is.numeric(order.by)) {   # this will happen eg c(1,2,3)
      order.by.cols <- as.integer(order.by)
    }
    if(order.by.cols < 1L || order.by.cols > ncol(data)) stop('Column ', order.by.cols, ' out of range for frame columns ', ncol(data), '.')
  }
  args <- c(args,.row.col.selector(order.by.cols,envir=parent.frame()))


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

#'
#' Basic Imputation of H2O Vectors
#'
#'  Perform simple imputation on a single vector by filling missing values with aggregates
#'  computed on the "na.rm'd" vector. Additionally, it's possible to perform imputation
#'  based on groupings of columns from within data; these columns can be passed by index or
#'  name to the by parameter. If a factor column is supplied, then the method must be one
#'  "mode". Anything else results in a full stop.
#'
#'  The default method is selected based on the type of the column to impute. If the column
#'  is numeric then "mean" is selected; if it is categorical, then "mode" is selected. Otherwise
#'  column types (e.g. String, Time, UUID) are not supported.
#'
#'  @param data The dataset containing the column to impute.
#'  @param column The column to impute.
#'  @param method "mean" replaces NAs with the column mean; "median" replaces NAs with the column median;
#'                "mode" replaces with the most common factor (for factor columns only);
#'  @param combine_method If method is "median", then choose how to combine quantiles on even sample sizes. This parameter is ignored in all other cases.
#'  @param by group by columns
#'  @param inplace Perform the imputation inplace or make a copy. Default is to perform the imputation in place.
#'
#'  @return a Frame with imputed values
#'  @examples
#' \donttest{
#'  h2o.init()
#'  fr <- as.h2o(iris, destination_frame="iris")
#'  fr[sample(nrow(fr),40),5] <- NA  # randomly replace 50 values with NA
#'  # impute with a group by
#'  h2o.impute(fr, "Species", "mode", by=c("Sepal.Length", "Sepal.Width"))
#' }
#'  @export
h2o.impute <- function(data, column, method=c("mean","median","mode"), # TODO: add "bfill","ffill"
                       combine_method=c("interpolate", "average", "lo", "hi"), by=NULL, inplace=FALSE) {
  # TODO: "bfill" back fill the missing value with the next non-missing value in the vector
  # TODO: "ffill" front fill the missing value with the most-recent non-missing value in the vector.
  # TODO: #'  @param max_gap  The maximum gap with which to fill (either "ffill", or "bfill") missing values. If more than max_gap consecutive missing values occur, then those values remain NA.

  # this AST: (h2o.impute %fr #colidx method combine_method inplace max_gap by)
  chk.Frame(data)

  # sanity check `column` then convert to 0-based index.
  if( length(column) > 1L ) stop("`column` must be a single column.")
  col.id <- -1L
  if( is.numeric(column) ) col.id <- column - 1L
  else                     col.id <- match(column,colnames(data)) - 1L
  if( col.id < 0L || col.id > (ncol(data)-1L) ) stop("Column ", col.id, " out of range.")

  # choose "mean" by default for numeric columns. "mode" for factor columns
  if( length(method) > 1) {
    if( is.factor(data[column]) ) method <- "mode"
    method <- "mean"
  }

  # choose "interplate" by default for combine_method
  if( length(combine_method) > 1L ) combine_method <- "interpolate"
  if( combine_method=="lo" ) combine_method <- "low"
  if( combine_method=="hi" ) combine_method <- "high"

  # sanity check method, column type, by parameters
  if( method=="median" ) {
    # no by and median
    if( !is.null(by) ) stop("Unimplemented: No `by` and `median`. Please select a different method.")
  }

  # check that method isn't median or mean for factor columns.
  if( is.factor(data[column]) && !(method %in% c("ffill", "bfill", "mode")) )
    stop("Column is categorical, method must not be mean or median.")

  # handle the data
  gb.cols <- "[]"
  if( !is.null(by) ) {
    if(is.character(by)) {
      vars <- match(by, colnames(data))
      if( any(is.na(vars)) )
        stop('No column named ', by, ' in ', substitute(data), '.')
      } else if(is.integer(by)) { vars <- by }
      else if(is.numeric(by)) {   vars <- as.integer(by) }  # this will happen eg c(1,2,3)
      if( vars <= 0L || vars > (ncol(data)) )
        stop('Column ', vars, ' out of range for frame columns ', ncol(data), '.')
      gb.cols <- .row.col.selector(vars,envir=parent.frame())
  }

  res <- .newExpr("h2o.impute",data, col.id, .quote(method), .quote(combine_method), gb.cols)
  # In-place updates we force right now, because the user expects future uses
  # of 'data' to show the imputed changed.
  if( inplace ) stop("unimpl")
  res
}

#' Range of an H2O Column
#'
#' @param ... An H2O Frame object.
#' @param na.rm ignore missing values
#' @export
range.Frame <- function(...,na.rm = TRUE) c(min(...,na.rm=na.rm), max(...,na.rm=na.rm))

#-----------------------------------------------------------------------------------------------------------------------
# *ply methods: ddply, apply, lapply, sapply,
#-----------------------------------------------------------------------------------------------------------------------
# TODO: Cleanup the cruft!
#' Split H2O Dataset, Apply Function, and Return Results
#'
#' For each subset of an H2O data set, apply a user-specified function, then combine the results.  This is an experimental feature.
#'
#' @param X An H2O Frame object to be processed.
#' @param .variables Variables to split \code{X} by, either the indices or names of a set of columns.
#' @param FUN Function to apply to each subset grouping.
#' @param ... Additional arguments passed on to \code{FUN}.
#' @param .progress Name of the progress bar to use. #TODO: (Currently unimplemented)
#' @return Returns a Frame object containing the results from the split/apply operation, arranged
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
#' fun = function(df) { sum(df[,1], na.rm = TRUE)/nrow(df) }
#' # Apply function to groups by class of flower
#' # uses h2o's ddply, since iris.hex is a Frame object
#' res = h2o.ddply(iris.hex, "class", fun)
#' head(res)
#' }
#' @export
h2o.ddply <- function (X, .variables, FUN, ..., .progress = 'none') {
  .h2o.gc()
  chk.Frame(X)

  # we accept eg .(col1, col2), c('col1', 'col2'), 1:2, c(1,2)
  # as column names.  This is a bit complicated
  if(is.character(.variables)) {
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

  # Look for an H2O function that works on a Frame; it will be handed a Frame of 1 col
  fr.name <- paste0(fname,".Frame")
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
#' Method for apply on Frame objects.
#'
#' @param X an H2O Frame object on which \code{apply} will operate.
#' @param MARGIN the vector on which the function will be applied over, either
#'        \code{1} for rows or \code{2} for columns.
#' @param FUN the function to be applied.
#' @param \dots optional arguments to \code{FUN}.
#' @return Produces a new Frame of the output of the applied
#'         function. The output is stored in H2O so that it can be used in
#'         subsequent H2O processes.
#' @seealso \link[base]{apply} for the base generic
#' @examples
#' \donttest{
#' h2o.init()
#' irisPath = system.file("extdata", "iris.csv", package="h2o")
#' iris.hex = h2o.importFile(path = irisPath, destination_frame = "iris.hex")
#' summary(apply(iris.hex, 2, sum))
#' }
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
  extra_args = list()
  if(length(l) > 0L)
    extra_args <- sapply(l, .process.stmnt, list(), sys.parent(1))

  # Process the function. Decide if it's an anonymous fcn, or a named one.
  fname <- as.character(substitute(FUN))
  if( typeof(FUN) == "builtin" || typeof(FUN) == "symbol") {
    if( fname %in% .h2o.primitives ) return(.newExpr("apply",X,MARGIN,fname))
    stop(paste0("Function '",fname,"' not in .h2o.primitives list and not an anonymous function, unable to convert it to Currents"))
  }

  # Look for an H2O function that works on a Frame; it will be handed a Frame of 1 col
  fr.name <- paste0(fname,".Frame")
  if( exists(fr.name) ) {
    FUN <- get(fr.name)         # Resolve function to the H2O flavor
    # Add in any default args
    args <- formals(FUN)[-1L]
    nargs <- length(args) - length(extra_args)
    if( nargs > 0 ) extra_args <- c(extra_args,tail(args,nargs))
    fcn <- if( length(extra_args)==0 ) fname
           else paste0("{ COL . (",fname," COL ",paste(extra_args,collapse=" "),")}")
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
#' @param x A single numeric column from an H2O Frame.
#' @param breaks Can be one of the following:
#'               A string: "Sturges", "Rice", "sqrt", "Doane", "FD", "Scott"
#'               A single number for the number of breaks splitting the range of the vec into number of breaks bins of equal width
#'               A vector of numbers giving the split points, e.g., c(-50,213.2123,9324834)
#' @param plot A logical value indicating whether or not a plot should be generated (default is TRUE).
#' @export
h2o.hist <- function(x, breaks="Sturges", plot=TRUE) {
  if( is.character(breaks) ) {
    if( breaks=="Sturges" ) breaks <- "sturges"
    if( breaks=="Rice"    ) breaks <- "rice"
    if( breaks=="Doane"   ) breaks <- "doane"
    if( breaks=="FD"      ) breaks <- "fd"
    if( breaks=="Scott"   ) breaks <- "scott"
  }
  h <- as.data.frame(.newExpr("hist", chk.Frame(x), .quote(breaks)))
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
#' @param x A Frame object whose strings should be lower'd
#' @export
h2o.tolower <- function(x) .newExpr("tolower", x)

#'
#' To Upper
#'
#' @param x A Frame object whose strings should be upper'd
#' @export
h2o.toupper <- function(x) .newExpr("toupper", x)

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
#' @export
h2o.gsub <- function(pattern,replacement,x,ignore.case=FALSE) .newExpr("replaceall", x, .quote(pattern), .quote(replacement),ignore.case)

#'
#' Trim Space
#'
#' @param x The column whose strings should be trimmed.
#' @export
h2o.trim <- function(x) .newExpr("trim", x)

#'
#' String length
#'
#' @param x The column whose string lengths will be returned.
#' @export
h2o.nchar <- function(x) .newExpr("length", x)