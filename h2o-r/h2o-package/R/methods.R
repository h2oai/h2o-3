#'
#' A Mix of H2O-specific and Overloaded R methods.
#'
#' Below we have a mix of h2o and overloaded R methods according to the following ToC:
#'
#'  H2O Methods:
#'  ------------
#'
#'      h2o.ls, h2o.rm, h2o.assign, h2o.createFrame, h2o.splitFrame, h2o.ignoreColumns, h2o.cut, h2o.table
#'
#'  Time & Date: '*' matches "Frame" and "ParsedData" --> indicates method dispatch via UseMethod
#'  ------------
#'
#'      year.H2O*, month.H2O*, diff.H2O*
#'
#'
#'
#' Methods are grouped according to the data types upon which they operate. There is a grouping of H2O specifc methods
#' and methods that are overloaded from the R language (e.g. summary, head, tail, dim, nrow).

#'
#' Important Developer Notes on the Lazy Evaluators:
#' --------------------------
#'
#' The H2OFrame "lazy" evaluators: Evaulate an AST.
#'
#' The pattern below is necessary in order to swap out S4 objects *in the calling frame*,
#' and the code re-use is necessary in order to safely assign back to the correct environment (i.e. back to the correct
#' calling scope). If you *absolutely* need to nest calls like this, you _MUST_ correctly track the names all the way down,
#' and then all the way back up the scopes.
#' Here's the example pattern: Number of columns
#'
#' Num Columns of an AST.
#'
#' Evaluate the AST and produce the ncol of the eval'ed AST.
#'
#'       ncol.H2OFrame <- function(x) {
#'         ID  <- as.list(match.call())$x                                    # try to get the ID from the call
#'         if(length(as.list(substitute(x))) > 1) ID <- "Last.value"         # get an appropriate ID
#'         .force.eval(.retrieveH2O(parent.frame()), x, ID = ID, rID = 'x')  # call the force eval
#'         ID <- ifelse(ID == "Last.value", ID, x@key)                       # bridge the IDs between the force.eval and the parent frame
#'         assign(ID, x, parent.frame())                                     # assign the eval'ed frame into the parent env
#'         ncol(get(ID, parent.frame()))                                     # get the object back from the parent and perform the op
#'       }
#'
#' Take this line-by-line:
#'    Line 1: grab the ID from the arg list, this ID is what we want the key to be in H2O
#'    Line 2: if there is no suitable ID (i.e. we have some object, not a named thing), assign to Last.value
#'    Line 3:
#'          1. Get a handle to h2o (see classes.R::.retrieveH2O)
#'          2. x is the ast we want to eval
#'          3. ID is the identifier we want the eventual object to have at the end of the day
#'          4. rID is used in .force.eval to assign back into *this* scope (i.e. child scope -> parent scope)
#'    Line 4: The identifier in the parent scope will either be Last.value, or the key of the H2OParsedData
#'             *NB: x is _guaranteed_ to be an H2OParsedData object at this point (this is post .force.eval)
#'    Line 5: assign from *this* scope, into the parent scope
#'    Line 6: Do

#-----------------------------------------------------------------------------------------------------------------------
# H2O Methods
#-----------------------------------------------------------------------------------------------------------------------

h2o.ls <- function(object) {
  if (missing(object)) object <- .retrieveH2O(parent.frame())
  ast <- new("ASTNode", root = new("ASTApply", op = "ls"))
  ret <- as.data.frame(ast)
  h2o.rm("ast")
  ret
}

#'
#' Remove All Keys on the H2O Cluster
#'
#' Removes the data from the h2o cluster, but does not remove the local references.
h2o.removeAll<-
function(object) {
  if (missing(object)) object <- .retrieveH2O(parent.frame())
  Log.info("Throwing away any keys on the H2O cluster")
  .h2o.__remoteSend(object, .h2o.__REMOVEALL)
}

#'
#' Log a message.
#'
#' Log a message `m`.
h2o.logIt <- function(m, tmp, commandOrErr, isPost = TRUE) .h2o.__logIt(m, tmp, commandOrErr, isPost)

#'
#' Make an HTTP request to the H2O backend.
#'
#' Useful for sending a REST command to H2O that is not currently supported.
h2o.remoteSend <- function(client, page, method = "GET", ..., .params = list()) .h2o.__remoteSend(client, page, method, ..., .params)

#'
#' Delete Objects In H2O
#'
#' Remove the h2o Big Data object(s) having the key name(s) from keys.
h2o.rm <- function(object, keys) {

  # If only object is supplied, then assume this is keys vector.
  if(missing(keys) && !missing(object)) {
    keys <- object
    object <- .retrieveH2O(parent.frame())
  }
  if(class(object) != "H2OClient") stop("object must be of class H2OClient")
  if(!is.character(keys)) stop("keys must be of class character")

  for(i in 1:length(keys))
    .h2o.__remoteSend(object, .h2o.__REMOVE, key=keys[[i]])
}

h2o.assign <- function(data, key) {
  if(data %<i-% "ASTNode") invisible(head(data))
  if(!(data %<i-% "H2OParsedData")) stop("data must be of class H2OParsedData")
  if(!is.character(key)) stop("key must be of class character")
  if(nchar(key) == 0) stop("key cannot be an empty string")
  if(key == data@key) stop(paste("Destination key must differ from data key", data@key))
  ast <- key %<-% (' $' %<p0-% data@key)
  .force.eval(.retrieveH2O(parent.frame()), ast, ID = NULL, rID = key)
  assign(deparse(substitute(data)), get(key), envir = parent.frame())
  invisible(get(key))
}

#'
#' Get the reference to a frame with the given key.
h2o.getFrame <- function(h2o, key) {
  if (missing(key)) {
    # means h2o is the one that's missing... retrieve it!
    key <- h2o
    h2o <- .retrieveH2O(parent.frame())
  }
  ast <- new("ASTNode", root = new("ASTApply", op = '$' %<p0-% key))
  ID <- key
  .force.eval(h2o, ast, ID = ID, rID = 'ast')
  ast
}

#h2o.createFrame <- function(object, key, rows, cols, seed, randomize, value, real_range, categorical_fraction, factors, integer_fraction, integer_range, missing_fraction, response_factors) {
#  if(!is.numeric(rows)) stop("rows must be a numeric value")
#  if(!is.numeric(cols)) stop("rows must be a numeric value")
#  if(!is.numeric(seed)) stop("rows must be a numeric value")
#  if(!is.logical(randomize)) stop("randomize must be a boolean value")
#  if(!is.numeric(value)) stop("value must be a numeric value")
#  if(!is.numeric(real_range)) stop("real_range must be a numeric value")
#  if(!is.numeric(categorical_fraction)) stop("categorical_fraction must be a numeric value")
#  if(!is.numeric(factors)) stop("factors must be a numeric value")
#  if(!is.numeric(integer_fraction)) stop("integer_fraction must be a numeric value")
#  if(!is.numeric(integer_range)) stop("integer_range must be a numeric value")
#  if(!is.numeric(missing_fraction)) stop("missing_fraction must be a numeric value")
#  if(!is.numeric(response_factors)) stop("response_factors must be a numeric value")
#
#  res <- .h2o.__remoteSend(object, .h2o.__PAGE_CreateFrame, key = key, rows = rows, cols = cols, seed = seed, randomize = as.numeric(randomize), value = value, real_range = real_range,
#                          categorical_fraction = categorical_fraction, factors = factors, integer_fraction = integer_fraction, integer_range = integer_range, missing_fraction = missing_fraction, response_factors = response_factors)
#  .h2o.exec2(expr = key, h2o = object, dest_key = key)
#}
#
#h2o.splitFrame <- function(data, ratios = 0.75, shuffle = FALSE) {
#  if(class(data) != "H2OParsedData") stop("data must be of class H2OParsedData")
#  if(!is.numeric(ratios)) stop("ratios must be numeric")
#  if(any(ratios < 0 | ratios > 1)) stop("ratios must be between 0 and 1 exclusive")
#  if(sum(ratios) >= 1) stop("sum of ratios must be strictly less than 1")
#  if(!is.logical(shuffle)) stop("shuffle must be a logical value")
#
#  res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_SplitFrame, source = data@key, ratios = ratios, shuffle = as.numeric(shuffle))
#  lapply(res$split_keys, function(key) { .h2o.exec2(expr = key, h2o = data@h2o, dest_key = key) })
#}

#h2o.ignoreColumns <- function(data, max_na = 0.2) {
#  if(ncol(data) > .MAX_INSPECT_COL_VIEW)
#    warning(data@key, " has greater than ", .MAX_INSPECT_COL_VIEW, " columns. This may take awhile...")
#  if(missing(data)) stop('Must specify object')
#  if(class(data) != 'H2OParsedData') stop('object not a h2o data type')
#  numRows = nrow(data)
#  naThreshold = numRows * max_na
#  cardinalityThreshold = numRows
#
#  res = .h2o.__remoteSend(data@h2o, .h2o.__PAGE_SUMMARY2, source=data@key, max_ncols=.Machine$integer.max)
#  columns = res$summaries
#  ignore = sapply(columns, function(col) {
#    if(col$stats$type != 'Enum'){# Numeric Column
#      if(col$stats$min==col$stats$max || col$nacnt >= naThreshold){
#        # If min=max then only one value in entire column
#        # If naCnt is higher than 20% of all entries
#        col$colname
#      }
#    }
#    else { # Categorical Column
#      if(col$stats$cardinality==cardinalityThreshold || col$nacnt >= naThreshold ){
#        # If only entry is a unique entry
#        # If naCnt is higher than 20% of all entries
#        col$colname
#      }
#    }
#  }
#  )
#  unlist(ignore)
#}


## TODO: H2O doesn't support any arguments beyond the single H2OParsedData object (with <= 2 cols)
#h2o.table <- function(x) {
#  if(missing(x)) stop("Must specify data set")
#  if(!inherits(x, "H2OParsedData")) stop(cat("\nData must be an H2O data set. Got ", class(x), "\n"))
#  if(ncol(x) > 2) stop("Unimplemented")
#  .h2o.unop("table", x)
#}

#'
#' cut a numeric column into factors
cut.H2OParsedData<-
function(x, breaks, labels = NULL, include.lowest = FALSE, right = TRUE, dig.lab = 3) {
  if(missing(x)) stop("Must specify data set")
  if(missing(breaks)) stop("`breaks` must be a numeric vector")
  if(ncol(x) > 1) stop("*Unimplemented* `x` must be a single column.")
  ast.cut <- .h2o.varop("cut", x, breaks, labels, include.lowest, right, dig.lab)
  ID <- "Last.value"
  .force.eval(.retrieveH2O(parent.frame()), ast.cut, ID = ID, rID = 'ast.cut')
  ast.cut
}

#'
#' cut a numeric column backed by an AST into factors
cut.H2OFrame<-
function(x, breaks, labels = NULL, include.lowest = FALSE, right = TRUE, dig.lab = 3) {
  if(missing(x)) stop("Must specify data set")
  if(missing(breaks)) stop("`breaks` must be a numeric vector")
  ast.cut <- .h2o.varop("cut", x, breaks, labels, include.lowest, right, dig.lab)
  print(ast.cut)

  #FIXME: Finish up here!
#  ID  <- as.list(match.call())$x
#  if(length(as.list(substitute(x))) > 1) ID <- "Last.value"
#  ID <- ifelse(ID == "Last.value", ID, ast.cut@key)
#  .force.eval(.retrieveH2O(parent.frame()), ast.cut, ID = ID, rID = 'ast.cut')
#  ast.cut
}

match <- function(x, table, nomatch = 0, incomparables = NULL) if (.isH2O(x)) UseMethod("match") else base::match(x,table, nomatch = 0, incomparables = NULL)

#'
#' `match` or %in% for an AST
#'
match.H2OFrame <-function(x, table, nomatch = 0, incomparables = NULL) {
#  if(missing(x)) stop("Must specify data set")
#  if(ncol(x) > 1) stop("`x` must be a single column.")
  ast.match <- .h2o.varop("match", x, table, nomatch, incomparables)
#  ID <- "Last.value"
#  .force.eval(.retrieveH2O(parent.frame()), ast.mean, ID = ID, rID = 'ast.cut')
  ast.match
}

#'
#' `match` or %in% for H2OParsedData
#'
match.H2OParsedData <- function(x, table, nomatch = NA_integer_, incomparables = NULL) {
#  if(missing(x)) stop("Must specify data set")
#  if(ncol(x) > 1) stop("`x` must be a single column.")
  ast.match <- .h2o.varop("match", x, table, nomatch, incomparables)
  ID <- "Last.value"
  .force.eval(.retrieveH2O(parent.frame()), ast.match, ID = ID, rID = 'ast.match')
  ast.match
}

#'
#' %in% method
#'
"%in%" <- function(x, table) {
  if (x %<i-% "ASTNode") match.H2OFrame(x, table, nomatch = 0) > 0                  # ASTNode case
  else if (x %<i-% "H2OParsedData") match.H2OParsedData(x, table, nomatch = 0) > 0  # H2OParsedData case
  else match(x, table, nomatch = 0) > 0                                             # base:: case
}

#-----------------------------------------------------------------------------------------------------------------------
# Time & Date
#-----------------------------------------------------------------------------------------------------------------------

# TODO: s4 year, month impls as well?
#h2o.year <- function(x){
#  if( missing(x) ) stop('must specify x')
#  if( !class(x) == 'H2OParsedData' ) stop('x must be an h2o data object')
#  res1 <- .h2o.unop('year', x)
#  .h2o.binop("-", res1, 1900)
#}
#
#h2o.month <- function(x){
#  if( missing(x) ) stop('must specify x')
#  if( !class(x) == 'H2OParsedData' ) stop('x must be an h2o data object')
#  .h2o.unop('month', x)
#}
#
#year <- function(x) UseMethod('year', x)
#year.H2OFrame <- h2o.year
#month <- function(x) UseMethod('month', x)
#month.H2OFrame <- h2o.month
#
#diff.H2OFrame <- function(x, lag = 1, differences = 1, ...) {
#  if(!is.numeric(lag)) stop("lag must be numeric")
#  if(!is.numeric(differences)) stop("differences must be numeric")
#
#  expr = paste("diff(", paste(x@key, lag, differences, sep = ","), ")", sep = "")
#  res = .h2o.__exec2(x@h2o, expr)
#  new("H2OParsedData", h2o=x@h2o, key=res$dest_key, logic=FALSE)
#}


#
#h2o.runif <- function(x, min = 0, max = 1, seed = -1) {
#  if(missing(x)) stop("Must specify data set")
#  if(!inherits(x, "H2OFrame")) stop(cat("\nData must be an H2O data set. Got ", class(x), "\n"))
#  if(!is.numeric(min)) stop("min must be a single number")
#  if(!is.numeric(max)) stop("max must be a single number")
#  if(length(min) > 1 || length(max) > 1) stop("Unimplemented")
#  if(min > max) stop("min must be a number less than or equal to max")
#  if(!is.numeric(seed)) stop("seed must be an integer >= 0")
#
#  .h2o.varop("runif", x, min, max, seed)
#}

#h2o.anyFactor<-
#function(x) {
#  if(!(x %<i-% "H2OParsedData")) stop("x must be an H2O parsed data object")
#  .force.eval
#}

#-----------------------------------------------------------------------------------------------------------------------
# Overloaded Base R Methods
#-----------------------------------------------------------------------------------------------------------------------

#-----------------------------------------------------------------------------------------------------------------------
# Slicing
#-----------------------------------------------------------------------------------------------------------------------

# i are the rows, j are the columns
setMethod("[", "H2OFrame", function(x, i, j, ..., drop = TRUE) {
  if (missing(i) && missing(j)) return(x)
  if (!missing(i) && (i %<i-% "ASTNode")) i <- eval(i)
  if (!missing(j) && is.character(j)) {
    col_names <- colnames(x)  # this is a bit expensive since we have to force the eval on x
    if (! any(j %in% col_names)) stop("Undefined column names specified")
    j <- match(j, col_names)
  }
  if (x %<i-% "H2OParsedData") x <- '$' %<p0-% x@key

  op <- new("ASTApply", op='[')
  rows <- if(missing(i)) deparse("null") else { if ( i %<i-% "ASTNode") eval(i, parent.frame()) else .eval(substitute(i), parent.frame()) }
  cols <- if(missing(j)) deparse("null") else .eval(substitute(j), parent.frame())
  new("ASTNode", root=op, children=list(x, rows, cols))
})

setMethod("$", "H2OFrame", function(x, name) {
  col_names <- colnames(x)
  if (!(name %in% col_names)) return(NULL)
  idx <- match(name, col_names)
  do.call("[", list(x=x, j=idx))
})

setMethod("[[", "H2OFrame", function(x, i, exact = TRUE) {
  if(missing(i)) return(x)
  if(length(i) > 1) stop("[[]] may only select one column")
  if(!(i %in% colnames(x)) ) return(NULL)
  do.call("[", list(x = x, j = match(i, colnames(x))))
})

#-----------------------------------------------------------------------------------------------------------------------
# Assignment Operations: [<-, $<-, [[<-, colnames<-, names<-
#-----------------------------------------------------------------------------------------------------------------------

setMethod("[<-", "H2OFrame", function(x, i, j, ..., value) {
   # TODO: Error checking on backend
   # numRows = nrow(x); numCols = ncol(x)

  if(!(missing(i) || is.numeric(i)) || !(missing(j) || is.numeric(j) || is.character(j))) stop("Row/column types not supported!")
  if(!inherits(value, "H2OFrame") && !is.numeric(value)) stop("value can only be numeric or a H2OParsedData object")
#  if(is.numeric(value) && length(value) != 1 && length(value) != numRows) stop("value must be either a single number or a vector of length ", numRows)

  if(!missing(i) && is.numeric(i)) {
    if(any(i == 0)) stop("Array index out of bounds")
#    if(any(i < 0 && abs(i) > numRows)) stop("Unimplemented: can't extend rows")
#    if(min(i) > numRows+1) stop("new rows would leave holes after existing rows")
  }
  if(!missing(j) && is.numeric(j)) {
    if(any(j == 0)) stop("Array index out of bounds")
#    if(any(j < 0 && abs(j) > numCols)) stop("Unimplemented: can't extend columns")
#    if(min(j) > numCols+1) stop("new columns would leave holes after existing columns")
  }

  if (missing(i) && missing(j)) {
    if (x %<i-% "H2OParsedData") x <- '$' %<p0-% x@key
    lhs <- x
  } else if (missing(i)) lhs <- do.call("[", list(x=x, j=substitute(j)))
    else if (missing(j)) lhs <- do.call("[", list(x=x, i=substitute(i)))
    else lhs <- do.call("[", list(x=x, i=substitute(i), j=substitute(j)))

  if (value %<i-% "ASTNode") rhs <- eval(value)
  else if(value %<i-% "H2OFrame") rhs <- value
  else rhs <- .eval(substitute(value), parent.frame(), FALSE)

  op <- new("ASTApply", op='=')
  new("ASTNode", root=op, children=list(lhs, rhs))
})

setMethod("$<-", "H2OParsedData", function(x, name, value) {
  if(missing(name) || !is.character(name) || nchar(name) == 0)
    stop("name must be a non-empty string")
  if(!inherits(value, "H2OFrame") && !is.numeric(value))
    stop("value can only be numeric or a H2OFrame object")

    # TODO: Error checking on backend
#  numCols = ncol(x); numRows = nrow(x)
#  if(is.numeric(value) && length(value) != 1 && length(value) != numRows)
#    stop("value must be either a single number or a vector of length ", numRows)

  col_names <- colnames(x)
  if (!(name %in% col_names)) idx <- length(col_names) + 1     # new column
  else idx <- match(name, col_names)                           # re-assign existing column
  lhs <- do.call("[", list(x=x, j=idx))                        # create the lhs ast

  if (value %<i-% "ASTNode") rhs <- eval(value)                # rhs is already ast, eval it
  else if(value %<i-% "H2OFrame") rhs <- value                 # rhs is some H2OFrame object
  else rhs <- .eval(substitute(value), parent.frame(), FALSE)  # rhs is R generic
  res <- new("ASTNode", root=op, children=list(lhs, rhs))      # create the rhs ast

  # TODO: force eval res, and then set column names ... return an H2OParsedData object
#
#  if(is.na(idx))
#    res = .h2o.__remoteSend(x@h2o, .h2o.__HACK_SETCOLNAMES2, source=x@key, cols=numCols, comma_separated_list=name)
#  return(new("H2OParsedData", h2o=x@h2o, key=x@key))
})

setMethod("[[<-", "H2OParsedData", function(x, i, value) {
  if( !( value %<i-% "H2OFrame")) stop('Can only append H2O data to H2O data')
#  if( ncol(value) > 1 ) stop('May only set a single column')
#  if( nrow(value) != nrow(x) ) stop(sprintf('Replacement has %d row, data has %d', nrow(value), nrow(x)))
  do.call("$<-", list(x=x, name=i, value=value))
})

#setMethod("colnames<-", signature(x="H2OParsedData", value="H2OParsedData"),
#  function(x, value) {
#    if(class(value) == "H2OParsedDataVA") stop("value must be a FluidVecs object")
#    else if(ncol(value) != ncol(x)) stop("Mismatched number of columns")
#    res = .h2o.__remoteSend(x@h2o, .h2o.__HACK_SETCOLNAMES2, source=x@key, copy_from=value@key)
#    return(x)
#})
#
#setMethod("colnames<-", signature(x="H2OParsedData", value="character"),
#  function(x, value) {
#    if(any(nchar(value) == 0)) stop("Column names must be of non-zero length")
#    else if(any(duplicated(value))) stop("Column names must be unique")
#    else if(length(value) != (num = ncol(x))) stop(paste("Must specify a vector of exactly", num, "column names"))
#    res = .h2o.__remoteSend(x@h2o, .h2o.__HACK_SETCOLNAMES2, source=x@key, comma_separated_list=value)
#    return(x)
#})
#
#setMethod("names<-", "H2OParsedData", function(x, value) { colnames(x) <- value; return(x) })


#-----------------------------------------------------------------------------------------------------------------------
# Inspection/Summary Operations
#-----------------------------------------------------------------------------------------------------------------------

nrow     <- function(x) if (.isH2O(x)) UseMethod("nrow"    ) else base::nrow(x)
ncol     <- function(x) if (.isH2O(x)) UseMethod("ncol"    ) else base::ncol(x)
colnames <- function(x) if (.isH2O(x)) UseMethod("colnames") else base::colnames(x)
names    <- function(x) if (.isH2O(x)) UseMethod("names"   ) else base::names(x)
length   <- function(x) if (.isH2O(x)) UseMethod("length"  ) else base::length(x)
dim      <- function(x) if (.isH2O(x)) UseMethod("dim"     ) else base::dim(x)

nrow.H2OParsedData     <- function(x) x@nrows
ncol.H2OParsedData     <- function(x) x@ncols
colnames.H2OParsedData <- function(x) x@col_names
names.H2OParsedData    <- function(x) colnames(x)
length.H2OParsedData   <- function(x) if (ncol(x) == 1) nrow(x) else ncol(x)
dim.H2OParsedData      <- function(x) c(x@nrows, x@ncols)

#'
#' Head of an H2O Data Frame
#'
#' Returns as an R data frame.
head.H2OParsedData <- function(x, n = 6L, ...) {
  #TODO: when 'x' is an expression
  numRows <- nrow(x)
  stopifnot(length(n) == 1L)
  n <- ifelse(n < 0L, max(numRows + n, 0L), min(n, numRows))
  if(n == 0) return(data.frame())

  tmp_head <- x[1:n,]
  x.slice <- as.data.frame(tmp_head)
  h2o.rm(tmp_head@key)
  return(x.slice)
}

#'
#' Tail of an H2O Data Frame
#'
#' Returns as an R data frame.
tail.H2OParsedData <- function(x, n = 6L, ...) {
  stopifnot(length(n) == 1L)
  endidx <- nrow(x)
  n <- ifelse(n < 0L, max(endidx + n, 0L), min(n, endidx))
  if(n == 0) return(data.frame())

  startidx <- max(1, endidx - n)
  idx <- startidx:endidx
  tmp_tail <- x[startidx:endidx,]
  x.slice <- as.data.frame(tmp_tail)
  h2o.rm(tmp_tail@h2o, tmp_tail@key)
  rownames(x.slice) <- idx
  return(x.slice)
}

#'
#' The H2OFrame "lazy" evaluators: Evaulate an AST.
#'
#' The pattern below is necessary in order to swap out S4 objects *in the calling frame*,
#' and the code re-use is necessary in order to safely assign back to the correct environment (i.e. back to the correct
#' calling scope).

#'
#' Num Rows of an AST.
#'
#' Evaluate the AST and produce the nrow of the eval'ed AST.
nrow.H2OFrame <- function(x) {
  ID  <- as.list(match.call())$x
  if(length(as.list(substitute(x))) > 1) ID <- "Last.value"
#  ID <- .eval.assign(x, ID, parent.frame(), environment())
  .force.eval(.retrieveH2O(parent.frame()), x, ID = ID, rID = 'x')
  ID <- ifelse(ID == "Last.value", ID, x@key)
  assign(ID, x, parent.frame())
  nrow(get(ID, parent.frame()))
}

#'
#' Num Columns of an AST.
#'
#' Evaluate the AST and produce the ncol of the eval'ed AST.
ncol.H2OFrame <- function(x) {
  ID  <- as.list(match.call())$x
  if(length(as.list(substitute(x))) > 1) ID <- "Last.value"
  .force.eval(.retrieveH2O(parent.frame()), x, ID = ID, rID = 'x')
  ID <- ifelse(ID == "Last.value", ID, x@key)
  assign(ID, x, parent.frame())
  ncol(get(ID, parent.frame()))
}

#'
#' Colnames of an AST.
#'
#' Evaluate the AST and produce the colnames of the eval'ed AST.
colnames.H2OFrame <- function(x) {
  ID  <- as.list(match.call())$x
  if(length(as.list(substitute(x))) > 1) ID <- "Last.value"
  .force.eval(.retrieveH2O(parent.frame()), x, ID = ID, rID = 'x')
  ID <- ifelse(ID == "Last.value", ID, x@key)
  assign(ID, x, parent.frame())
  colnames(get(ID, parent.frame()))
}

#'
#' Names of An AST.
#'
#' Evaluate the AST and produce the names of the eval'ed AST.
names.H2OFrame <- function(x) {
  ID  <- as.list(match.call())$x
  if(length(as.list(substitute(x))) > 1) ID <- "Last.value"
  .force.eval(.retrieveH2O(parent.frame()), x, ID = ID, rID = 'x')
  ID <- ifelse(ID == "Last.value", ID, x@key)
  assign(ID, x, parent.frame())
  names(get(ID, parent.frame()))
}

#'
#' Length of an AST.
#'
#' Evaluate the AST and produce the length of the eval'ed AST.
length.H2OFrame <- function(x) {
  ID  <- as.list(match.call())$x
  if(length(as.list(substitute(x))) > 1) ID <- "Last.value"
  .force.eval(.retrieveH2O(parent.frame()), x, ID = ID, rID = 'x')
  ID <- ifelse(ID == "Last.value", ID, x@key)
  assign(ID, x, parent.frame())
  length(get(ID, parent.frame()))
}

#'
#' Shape of an AST.
#'
#' Evaluate the AST and produce the dim of the eval'ed AST.
dim.H2OFrame <- function(x) {
  ID  <- as.list(match.call())$x
  if(length(as.list(substitute(x))) > 1) ID <- "Last.value"
  .force.eval(.retrieveH2O(parent.frame()), x, ID = ID, rID = 'x')
  ID <- ifelse(ID == "Last.value", ID, x@key)
  assign(ID, x, parent.frame())
  dim(get(ID, parent.frame()))
}

#'
#' Head of an AST.
#'
#' Evaluate the AST and produce the head of the eval'ed AST.
head.H2OFrame <- function(x, n = 6L, ...) {
  ID  <- as.list(match.call())$x
  if(length(as.list(substitute(x))) > 1) ID <- "Last.value"
  .force.eval(.retrieveH2O(parent.frame()), x, ID = ID, rID = 'x')
  if (.isH2O(x)) { ID <- ifelse(ID == "Last.value", ID, x@key)}  else ID <- "Last.value"
  assign(ID, x, parent.frame())
  head(get(ID, parent.frame()))
}

#'
#' Tail of an AST.
#'
#' Evaluate the AST and produce the tail of the eval'ed AST.
tail.H2OFrame <- function(x, n = 6L, ...) {
  ID  <- as.list(match.call())$x
  if(length(as.list(substitute(x))) > 1) ID <- "Last.value"
  .force.eval(.retrieveH2O(parent.frame()), x, ID = ID, rID = 'x')
  ID <- ifelse(ID == "Last.value", ID, x@key)
  assign(ID, x, parent.frame())
  tail(get(ID, parent.frame()))
}

#setMethod("levels", "H2OParsedData", function(x) {
#  if(ncol(x) != 1) return(NULL)
#  res = .h2o.__remoteSend(x@h2o, .h2o.__HACK_LEVELS2, source = x@key, max_ncols = .Machine$integer.max)
#  res$levels[[1]]
#})


#setMethod("is.factor", "H2OFrame", function(x) { as.logical(.h2o.unop("is.factor", x)) })

#quantile.H2OFrame <- function(x, probs = seq(0, 1, 0.25), na.rm = FALSE, names = TRUE, type = 7, ...) {
#  if((numCols = ncol(x)) != 1) stop("quantile only operates on a single column")
#  if(is.factor(x)) stop("factors are not allowed")
#  if(!na.rm && .h2o.__unop2("any.na", x)) stop("missing values and NaN's not allowed if 'na.rm' is FALSE")
#  if(!is.numeric(probs)) stop("probs must be a numeric vector")
#  if(any(probs < 0 | probs > 1)) stop("probs must fall in the range of [0,1]")
#  if(type != 2 && type != 7) stop("type must be either 2 (mean interpolation) or 7 (linear interpolation)")
#  if(type != 7) stop("Unimplemented: Only type 7 (linear interpolation) is supported from the console")
#
#  myProbs <- paste("c(", paste(probs, collapse = ","), ")", sep = "")
#  expr = paste("quantile(", x@key, ",", myProbs, ")", sep = "")
#  res = .h2o.__exec2(x@h2o, expr)
#  # res = .h2o.__remoteSend(x@h2o, .h2o.__PAGE_QUANTILES, source_key = x@key, column = 0, quantile = paste(probs, collapse = ","), interpolation_type = type, ...)
#  # col <- as.numeric(strsplit(res$result, "\n")[[1]][-1])
#  # if(numCols > .MAX_INSPECT_COL_VIEW)
#  #   warning(x@key, " has greater than ", .MAX_INSPECT_COL_VIEW, " columns. This may take awhile...")
#  # res2 = .h2o.__remoteSend(x@h2o, .h2o.__PAGE_INSPECT, key=res$dest_key, view=res$num_rows, max_column_display=.Machine$integer.max)
#  # col <- sapply(res2$rows, function(x) { x[[2]] })
#  col <- as.data.frame(new("H2OParsedData", h2o=x@h2o, key=res$dest_key))[[1]]
#  if(names) names(col) <- paste(100*probs, "%", sep="")
#  return(col)
#}
#
## setMethod("summary", "H2OParsedData", function(object) {
#summary.H2OFrame <- function(object, ...) {
#  digits = 12L
#  if(ncol(object) > .MAX_INSPECT_COL_VIEW)
#    warning(object@key, " has greater than ", .MAX_INSPECT_COL_VIEW, " columns. This may take awhile...")
#  res = .h2o.__remoteSend(object@h2o, .h2o.__PAGE_SUMMARY2, source=object@key, max_ncols=.Machine$integer.max)
#  cols <- sapply(res$summaries, function(col) {
#    if(col$stats$type != 'Enum') { # numeric column
#      if(is.null(col$stats$mins) || length(col$stats$mins) == 0) col$stats$mins = NaN
#      if(is.null(col$stats$maxs) || length(col$stats$maxs) == 0) col$stats$maxs = NaN
#      if(is.null(col$stats$pctile))
#        params = format(rep(signif(as.numeric(col$stats$mean), digits), 6), digits = 4)
#      else
#        params = format(signif(as.numeric(c(
#          col$stats$mins[1],
#          col$stats$pctile[4],
#          col$stats$pctile[6],
#          col$stats$mean,
#          col$stats$pctile[8],
#          col$stats$maxs[1])), digits), digits = 4)
#      result = c(paste("Min.   :", params[1], "  ", sep=""), paste("1st Qu.:", params[2], "  ", sep=""),
#                 paste("Median :", params[3], "  ", sep=""), paste("Mean   :", params[4], "  ", sep=""),
#                 paste("3rd Qu.:", params[5], "  ", sep=""), paste("Max.   :", params[6], "  ", sep=""))
#    }
#    else {
#      top.ix <- sort.int(col$hcnt, decreasing=TRUE, index.return=TRUE)$ix[1:6]
#      if(is.null(col$hbrk)) domains <- top.ix[1:6] else domains <- col$hbrk[top.ix]
#      counts <- col$hcnt[top.ix]
#
#      # TODO: Make sure "NA's" isn't a legal domain level
#      if(!is.null(col$nacnt) && col$nacnt > 0) {
#        idx <- ifelse(any(is.na(top.ix)), which(is.na(top.ix))[1], 6)
#        domains[idx] <- "NA's"
#        counts[idx] <- col$nacnt
#      }
#
#      # width <- max(cbind(nchar(domains), nchar(counts)))
#      width <- c(max(nchar(domains)), max(nchar(counts)))
#      result <- paste(domains,
#                      sapply(domains, function(x) { ifelse(width[1] == nchar(x), "", paste(rep(' ', width[1] - nchar(x)), collapse='')) }),
#                      ":",
#                      sapply(counts, function(y) { ifelse(width[2] == nchar(y), "", paste(rep(' ', width[2] - nchar(y)), collapse='')) }),
#                      counts,
#                      " ",
#                      sep='')
#      # result[is.na(top.ix)] <- NA
#      result[is.na(domains)] <- NA
#      result
#    }
#  })
#  # Filter out rows with nothing in them
#  cidx <- apply(cols, 1, function(x) { any(!is.na(x)) })
#  if(ncol(cols) == 1) { cols <- as.matrix(cols[cidx,]) } else { cols <- cols[cidx,] }
#  # cols <- as.matrix(cols[cidx,])
#
#  result = as.table(cols)
#  rownames(result) <- rep("", nrow(result))
#  colnames(result) <- sapply(res$summaries, function(col) col$colname)
#  result
#}

#-----------------------------------------------------------------------------------------------------------------------
# Summary Statistics Operations
#-----------------------------------------------------------------------------------------------------------------------

mean <- function(x, trim = 0, na.rm = FALSE, ...) if (.isH2O(x)) UseMethod("mean") else base::mean(x,trim,na.rm,...)
var  <- function(x, y = NULL, na.rm = FALSE, use) if (.isH2O(x)) UseMethod("var")  else stats::var(x,y,na.rm,use)
sd   <- function(x, na.rm = FALSE)                if (.isH2O(x)) UseMethod("sd")   else stats::sd(x,na.rm)

#'
#' Mean of a column.
#'
#' Obtain the mean of a column of data.
mean.H2OParsedData<-
function(x, trim = 0, na.rm = FALSE, ...) {
  if(ncol(x) != 1) stop("Can only compute the mean of a single column")
  if (trim != 0) stop("Unimplemented: trim must be 0", call.=FALSE)
  if (trim < 0) trim <- 0
  if (trim > .5) trim <- .5
  ast.mean <- .h2o.varop("mean", x, trim, na.rm, ...)
  ID <- "Last.value"
  .force.eval(.retrieveH2O(parent.frame()), ast.mean, ID = ID, rID = 'ast.mean')
  ast.mean
}

#'
#' Mean of a column, backed by AST.
#'
#' Expression is evaluated <=> this operation is top-level.
mean.H2OFrame<-
function(x, trim = 0, na.rm = FALSE, ...) {
  if (trim != 0) stop("Unimplemented: trim must be 0", call.=FALSE)
  if (trim < 0) trim <- 0
  if (trim > .5) trim <- .5
  ast.mean <- .h2o.varop("mean", x, trim, na.rm, ...)
  ID  <- as.list(match.call())$x
  if(length(as.list(substitute(x))) > 1) ID <- "Last.value"
  ID <- ifelse(ID == "Last.value", ID, ast.mean@key)
  .force.eval(.retrieveH2O(parent.frame()), ast.mean, ID = ID, rID = 'ast.mean')
  ast.mean
}

#'
#' Variance of a column.
#'
#' Obtain the variance of a column of data.
var.H2OParsedData<-
function(x, y = NULL, na.rm = FALSE, use = "everything") {
  if (use %in% c("pairwise.complete.obs", "na.or.complete")) stop("Unimplemented : `use` may be either \"everything\", \"all.obs\", or \"complete.obs\"")
  ast.var <- .h2o.varop("var", x, y, na.rm, use)
  ID <- "Last.value"
  .force.eval(.retrieveH2O(parent.frame()), ast.var, ID = ID, rID = 'ast.var')
  ast.var
}

#'
#' Variance of a column, backed by AST.
#'
#' Expression is evaluated <=> this operation is top-level.
var.H2OFrame<-
function(x, y = NULL, na.rm = FALSE, use = "everything") {
  if (use %in% c("pairwise.complete.obs", "na.or.complete")) stop("Unimplemented : `use` may be either \"everything\", \"all.obs\", or \"complete.obs\"")
  ast.var <- .h2o.varop("var", x, y, na.rm, use)
  ID  <- as.list(match.call())$x
  if(length(as.list(substitute(x))) > 1) ID <- "Last.value"
  ID <- ifelse(ID == "Last.value", ID, ast.var@key)
  .force.eval(.retrieveH2O(parent.frame()), ast.var, ID = ID, rID = 'ast.var')
  ast.var
}

#'
#' Standard Deviation of a column of data.
#'
#' Obtain the standard deviation of a column of data.
sd.H2OParsedData<-
function(x, na.rm = FALSE) {
  if(ncol(x) != 1) stop("Can only compute sd of a single column.")
  ast.sd <- .h2o.varop("sd", x, na.rm)
  ID <- "Last.value"
  .force.eval(.retrieveH2O(parent.frame()), ast.sd, ID = ID, rID = 'ast.sd')
  ast.sd
}

#'
#' Standard Deviation of a column, backed by AST.
#'
#' Expression is evaluated <=> this operation is top-level.
sd.H2OFrame<-
function(x, na.rm = FALSE) {
  ast.sd <- .h2o.varop("sd", x, na.rm)
  ID  <- as.list(match.call())$x
  if(length(as.list(substitute(x))) > 1) ID <- "Last.value"
  ID <- ifelse(ID == "Last.value", ID, ast.sd@key)
  .force.eval(.retrieveH2O(parent.frame()), ast.sd, ID = ID, rID = 'ast.sd')
  ast.sd
}

scale.H2OParsedData<-
function(x, center = TRUE, scale = TRUE) {
  ast.scale <- .h2o.varop("scale", x, center, scale)
  ID <- "Last.value"
  .force.eval(.retrieveH2O(parent.frame()), ast.scale, ID = ID, rID = 'ast.scale')
  ast.scale
}

scale.H2OFrame<-
function(x, center = TRUE, scale = TRUE) {
  ast.scale <- .h2o.varop("scale", x, center, scale)
  ID  <- as.list(match.call())$x
  if(length(as.list(substitute(x))) > 1) ID <- "Last.value"
  ID <- ifelse(ID == "Last.value", ID, ast.scale@key)
  .force.eval(.retrieveH2O(parent.frame()), ast.scale, ID = ID, rID = 'ast.scale')
  ast.scale
}

#-----------------------------------------------------------------------------------------------------------------------
# Casting Operations: as.data.frame, as.factor,
#-----------------------------------------------------------------------------------------------------------------------

#'
#' R data.frame -> H2OParsedData
#'
#' Import a local R data frame to the H2O cloud.
as.h2o <- function(client, object, key = "", header, sep = "") {
  if(missing(client) || class(client) != "H2OClient") stop("client must be a H2OClient object")
#  if(missing(object) || !is.numeric(object) && !is.data.frame(object)) stop("object must be numeric or a data frame")
  if(!is.character(key)) stop("key must be of class character")
  if( (missing(key) || nchar(key) == 0)  && !is.atomic(object)) key <- deparse(substitute(object))
  else if (missing(key) || nchar(key) == 0) key <- "Last.value"

  # TODO: Be careful, there might be a limit on how long a vector you can define in console
  if(is.numeric(object) && is.vector(object)) {
    object <- as.data.frame(object)
  }
    tmpf <- tempfile(fileext=".csv")
    write.csv(object, file=tmpf, quote = TRUE, row.names = FALSE)
    h2f <- h2o.importFile(client, tmpf, key = key, header = header, sep = sep)
    unlink(tmpf)
    return(h2f)
}

#'
#' AST -> R data.frame
#'
#' Evaluate the lazy expression, stash the lazy expression into Last.value, or the global variable, then return the R
#' data.frame.
as.data.frame.H2OFrame <- function(x, ...) {
  ID  <- as.list(match.call())$x
  if(length(as.list(substitute(x))) > 1) ID <- "Last.value"
  .force.eval(.retrieveH2O(parent.frame()), x, ID = ID, rID = 'x')
  ID <- ifelse(ID == "Last.value", ID, x@key)
  assign(ID, x, parent.frame())
  as.data.frame(get(ID, parent.frame()))
}

#'
#' H2O Data -> R data.frame
#'
#' Download the H2O data and then scan it in to R
as.data.frame.H2OParsedData <- function(x, ...) {
  if(class(x) != "H2OParsedData") stop("x must be of class H2OParsedData")
  # Versions of R prior to 3.1 should not use hex string.
  # Versions of R including 3.1 and later should use hex string.
  use_hex_string <- FALSE
  if (as.numeric(R.Version()$major) >= 3) {
    if (as.numeric(R.Version()$minor) >= 1) {
      use_hex_string = TRUE
    }
  }

  url <- paste('http://', x@h2o@ip, ':', x@h2o@port,
               '/2/DownloadDataset',
               '?key=', URLencode(x@key),
               '&hex_string=', as.numeric(use_hex_string),
               sep='')

  ttt <- getURL(url)
  n <- nchar(ttt)

  # Delete last 1 or 2 characters if it's a newline.
  # Handle \r\n (for windows) or just \n (for not windows).
  chars_to_trim = 0
  if (n >= 2) {
      c = substr(ttt, n, n)
      if (c == "\n") {
          chars_to_trim = chars_to_trim + 1
      }
      if (chars_to_trim > 0) {
          c = substr(ttt, n-1, n-1)
          if (c == "\r") {
              chars_to_trim = chars_to_trim + 1
          }
      }
  }

  if (chars_to_trim > 0) {
      ttt2 <- substr(ttt, 1, n-chars_to_trim)
      # Is this going to use an extra copy?  Or should we assign directly to ttt?
      ttt <- ttt2
  }

  # if((df.ncol = ncol(df)) != (x.ncol = ncol(x)))
  #  stop("Stopping conversion: Expected ", x.ncol, " columns, but data frame imported with ", df.ncol)
  # if(x.ncol > .MAX_INSPECT_COL_VIEW)
  #  warning(x@key, " has greater than ", .MAX_INSPECT_COL_VIEW, " columns. This may take awhile...")

  # Obtain the correct factor levels for each column
  # res = .h2o.__remoteSend(x@h2o, .h2o.__HACK_LEVELS2, source=x@key, max_ncols=.Machine$integer.max)
  # colClasses = sapply(res$levels, function(x) { ifelse(is.null(x), "numeric", "factor") })

  # Substitute NAs for blank cells rather than skipping
  df <- read.csv((tcon <- textConnection(ttt)), blank.lines.skip = FALSE, ...)
  # df = read.csv(textConnection(ttt), blank.lines.skip = FALSE, colClasses = colClasses, ...)
  close(tcon)
  return(df)
}

as.matrix.H2OParsedData <- function(x, ...) { as.matrix(as.data.frame(x, ...)) }
as.matrix.H2OFrame      <- function(x, ...) { as.matrix(as.data.frame(x, ...)) }

#setMethod("as.factor", "H2OParsedData", function(x) { .h2o.__unop2("factor", x) })

#-----------------------------------------------------------------------------------------------------------------------
# Model Plot/Summary Operations: PCA model summary and screeplot
#-----------------------------------------------------------------------------------------------------------------------

#summary.H2OPCAModel <- function(object, ...) {
#  # TODO: Save propVar and cumVar from the Java output instead of computing here
#  myVar = object@model$sdev^2
#  myProp = myVar/sum(myVar)
#  result = rbind(object@model$sdev, myProp, cumsum(myProp))   # Need to limit decimal places to 4
#  colnames(result) = paste("PC", seq(1, length(myVar)), sep="")
#  rownames(result) = c("Standard deviation", "Proportion of Variance", "Cumulative Proportion")
#
#  cat("Importance of components:\n")
#  print(result)
#}
#
#screeplot.H2OPCAModel <- function(x, npcs = min(10, length(x@model$sdev)), type = "barplot", main = paste("h2o.prcomp(", x@data@key, ")", sep=""), ...) {
#  if(type == "barplot")
#    barplot(x@model$sdev[1:npcs]^2, main = main, ylab = "Variances", ...)
#  else if(type == "lines")
#    lines(x@model$sdev[1:npcs]^2, main = main, ylab = "Variances", ...)
#  else
#    stop("type must be either 'barplot' or 'lines'")
#}

#-----------------------------------------------------------------------------------------------------------------------
# Merge Operations: ifelse, cbind, rbind, merge
#-----------------------------------------------------------------------------------------------------------------------

#.canBeCoercedToLogical<-
#function(vec) {
#  if (!(inherits(vec, "H2OParsedData"))) stop("Object must be a H2OParsedData object. Input was: ", vec)
#  # expects fr to be a vec.
#  as.logical(.h2o.__unop2("canBeCoercedToLogical", vec))
#}
#
#setMethod("ifelse", "H2OParsedData", function(test, yes, no) {
#  # if(!(is.numeric(yes) || class(yes) == "H2OParsedData") || !(is.numeric(no) || class(no) == "H2OParsedData"))
#  if(!(is.numeric(yes) || inherits(yes, "H2OParsedData")) || !(is.numeric(no) || inherits(no, "H2OParsedData")))
#    stop("Unimplemented")
#  if(!test@logic && !.canBeCoercedToLogical(test)) stop(test@key, " is not a H2O logical data type")
#  # yes = ifelse(class(yes) == "H2OParsedData", yes@key, yes)
#  # no = ifelse(class(no) == "H2OParsedData", no@key, no)
#  yes = ifelse(inherits(yes, "H2OParsedData"), yes@key, yes)
#  no = ifelse(inherits(no, "H2OParsedData"), no@key, no)
#  expr = paste("ifelse(", test@key, ",", yes, ",", no, ")", sep="")
#  res = .h2o.__exec2(test@h2o, expr)
#  if(res$num_rows == 0 && res$num_cols == 0)   # TODO: If logical operator, need to indicate
#    res$scalar
#  else
#    new("H2OParsedData", h2o=test@h2o, key=res$dest_key, logic=FALSE)
#})

cbind <- function(..., deparse.level = 1) if( .isH2O(list(...)[[1]])) UseMethod("cbind") else base::cbind(..., deparse.level)

# Note: right now, all things must be H2OParsedData
cbind.H2OFrame <- function(..., deparse.level = 1) {
  if(deparse.level != 1) stop("Unimplemented")
  ast <- .h2o.varop("cbind", ...)
  ast

#
#  l <- list(...)
#  # l_dep <- sapply(substitute(placeholderFunction(...))[-1], deparse)
#  if(length(l) == 0) stop('cbind requires an H2O parsed dataset')
#
#  klass <- 'H2OParsedData'
#  h2o <- l[[1]]@h2o
#  nrows <- nrow(l[[1]])
#  m <- Map(function(elem){ inherits(elem, klass) & elem@h2o@ip == h2o@ip & elem@h2o@port == h2o@port & nrows == nrow(elem) }, l)
#  compatible <- Reduce(function(l,r) l & r, x=m, init=T)
#  if(!compatible){ stop(paste('cbind: all elements must be of type', klass, 'and in the same H2O instance'))}
#
#  # If cbind(x,x), dupe colnames will automatically be renamed by H2O
#  # TODO: cbind(df[,1], df[,2]) should retain colnames of original data frame (not temp keys from slice)
#  if(is.null(names(l)))
#    tmp <- Map(function(x) x@key, l)
#  else
#    tmp <- mapply(function(x,n) { ifelse(is.null(n) || is.na(n) || nchar(n) == 0, x@key, paste(n, x@key, sep = "=")) }, l, names(l))
#
#  exec_cmd <- sprintf("cbind(%s)", paste(as.vector(tmp), collapse = ","))
#  res <- .h2o.__exec2(h2o, exec_cmd)
#  new('H2OParsedData', h2o=h2o, key=res$dest_key)
}

#-----------------------------------------------------------------------------------------------------------------------
# *ply methods: ddply, apply, lapply, sapply,
#-----------------------------------------------------------------------------------------------------------------------

#
#h2o.ddply <- function (.data, .variables, .fun = NULL, ..., .progress = 'none') {
#  if( missing(.data) ) stop('must specify .data')
#  if( !(class(.data) %in% c('H2OParsedData', 'H2OParsedDataVA')) ) stop('.data must be an h2o data object')
#  if( missing(.variables) ) stop('must specify .variables')
#  if( missing(.fun) ) stop('must specify .fun')
#
#  mm <- match.call()
#
#  # we accept eg .(col1, col2), c('col1', 'col2'), 1:2, c(1,2)
#  # as column names.  This is a bit complicated
#  if( class(.variables) == 'character'){
#    vars <- .variables
#    idx <- match(vars, colnames(.data))
#  } else if( class(.variables) == 'H2Oquoted' ){
#    vars <- as.character(.variables)
#    idx <- match(vars, colnames(.data))
#  } else if( class(.variables) == 'quoted' ){ # plyr overwrote our . fn
#    vars <- names(.variables)
#    idx <- match(vars, colnames(.data))
#  } else if( class(.variables) == 'integer' ){
#    vars <- .variables
#    idx <- .variables
#  } else if( class(.variables) == 'numeric' ){   # this will happen eg c(1,2,3)
#    vars <- .variables
#    idx <- as.integer(.variables)
#  }
#
#  #TODO: Put these on the back end
#  #  bad <- is.na(idx) | idx < 1 | idx > ncol(.data)
#  #  if( any(bad) ) stop( sprintf('can\'t recognize .variables %s', paste(vars[bad], sep=',')) )
#
#  .h2o.varop("ddply", .data, vars, .fun, fun_args=list(...), .progress)
#}
#
#ddply <- h2o.ddply

# TODO: how to avoid masking plyr?
#`h2o..` <- function(...) {
#  mm <- match.call()
#  mm <- mm[-1]
#  structure( as.list(mm), class='H2Oquoted')
#}
#
#`.` <- `h2o..`
#
#h2o.unique <- function(x, incomparables = FALSE, ...) {
#  # NB: we do nothing with incomparables right now
#  # NB: we only support MARGIN = 2 (which is the default)
#
#  if(!class(x) %in% c('H2OFrame', 'H2OParsedData', 'H2OParsedDataVA')) stop('h2o.unique: x is of the wrong type. Got: ', class(x))
##  if( nrow(x) == 0 | ncol(x) == 0) return(NULL) #TODO: Do this on the back end.
##  if( nrow(x) == 1) return(x)  #TODO: Do this on the back end.
#
#  args <- list(...)
#  if( 'MARGIN' %in% names(args) && args[['MARGIN']] != 2 ) stop('h2o unique: only MARGIN 2 supported')
#  .h2o.unop("unique", x)
#}
#unique.H2OFrame <- h2o.unique


## TODO: Need to change ... to environment variables and pass to substitute method,
##       Can't figure out how to access outside environment from within lapply
#setMethod("apply", "H2OParsedData", function(X, MARGIN, FUN, ...) {
#  if(missing(X) || !class(X) %in% c("H2OParsedData", "H2OParsedDataVA"))
#   stop("X must be a H2O parsed data object")
#  if(missing(MARGIN) || !(length(MARGIN) <= 2 && all(MARGIN %in% c(1,2))))
#    stop("MARGIN must be either 1 (rows), 2 (cols), or a vector containing both")
#  if(missing(FUN) || !is.function(FUN))
#    stop("FUN must be an R function")
#
#  myList <- list(...)
#  if(length(myList) > 0) {
#    stop("Unimplemented")
#    tmp = sapply(myList, function(x) { !class(x) %in% c("H2OParsedData", "H2OParsedDataVA", "numeric") } )
#    if(any(tmp)) stop("H2O only recognizes H2OParsedData and numeric objects")
#
#    idx = which(sapply(myList, function(x) { class(x) %in% c("H2OParsedData", "H2OParsedDataVA") }))
#    # myList <- lapply(myList, function(x) { if(class(x) %in% c("H2OParsedData", "H2OParsedDataVA")) x@key else x })
#    myList[idx] <- lapply(myList[idx], function(x) { x@key })
#
#    # TODO: Substitute in key name for H2OParsedData objects and push over wire to console
#    if(any(names(myList) == ""))
#      stop("Must specify corresponding variable names of ", myList[names(myList) == ""])
#  }
#
#  # Substitute in function name: FUN <- match.fun(FUN)
#  myfun = deparse(substitute(FUN))
#  len = length(myfun)
#  if(len > 3 && substr(myfun[1], nchar(myfun[1]), nchar(myfun[1])) == "{" && myfun[len] == "}")
#    myfun = paste(myfun[1], paste(myfun[2:(len-1)], collapse = ";"), "}")
#  else
#    myfun = paste(myfun, collapse = "")
#  params = c(X@key, MARGIN, myfun)
#  expr = paste("apply(", paste(params, collapse = ","), ")", sep="")
#  res = .h2o.__exec2(X@h2o, expr)
#  new("H2OParsedData", h2o=X@h2o, key=res$dest_key)
#})
#
#str.H2OFrame <- function(object, ...) {
#  if (length(l <- list(...)) && any("give.length" == names(l)))
#    invisible(NextMethod("str", ...))
#  else invisible(NextMethod("str", give.length = FALSE, ...))
#
#  if(ncol(object) > .MAX_INSPECT_COL_VIEW)
#    warning(object@key, " has greater than ", .MAX_INSPECT_COL_VIEW, " columns. This may take awhile...")
#  res = .h2o.__remoteSend(object@h2o, .h2o.__PAGE_INSPECT, key=object@key, max_column_display=.Machine$integer.max)
#  cat("\nH2O dataset '", object@key, "':\t", res$num_rows, " obs. of  ", (p <- res$num_cols),
#      " variable", if(p != 1) "s", if(p > 0) ":", "\n", sep = "")
#
#  cc <- unlist(lapply(res$cols, function(y) y$name))
#  width <- max(nchar(cc))
#  rows <- res$rows[1:min(res$num_rows, 10)]    # TODO: Might need to check rows > 0
#
#  if(class(object) == "H2OParsedDataVA")
#    res2 = .h2o.__remoteSend(object@h2o, .h2o.__HACK_LEVELS, key=object@key, max_column_display=.Machine$integer.max)
#  else
#    res2 = .h2o.__remoteSend(object@h2o, .h2o.__HACK_LEVELS2, source=object@key, max_ncols=.Machine$integer.max)
#  for(i in 1:p) {
#    cat("$ ", cc[i], rep(' ', width - nchar(cc[i])), ": ", sep = "")
#    rhead <- sapply(rows, function(x) { x[i+1] })
#    if(is.null(res2$levels[[i]]))
#      cat("num  ", paste(rhead, collapse = " "), if(res$num_rows > 10) " ...", "\n", sep = "")
#    else {
#      rlevels = res2$levels[[i]]
#      cat("Factor w/ ", (count <- length(rlevels)), " level", if(count != 1) "s", ' "', paste(rlevels[1:min(count, 2)], collapse = '","'), '"', if(count > 2) ",..", ": ", sep = "")
#      cat(paste(match(rhead, rlevels), collapse = " "), if(res$num_rows > 10) " ...", "\n", sep = "")
#    }
#  }
#}
#
#setMethod("findInterval", "H2OParsedData", function(x, vec, rightmost.closed = FALSE, all.inside = FALSE) {
#  if(any(is.na(vec)))
#    stop("'vec' contains NAs")
#  if(is.unsorted(vec))
#    stop("'vec' must be sorted non-decreasingly")
#  if(all.inside) stop("Unimplemented")
#
#  myVec = paste("c(", .seq_to_string(vec), ")", sep = "")
#  expr = paste("findInterval(", x@key, ",", myVec, ",", as.numeric(rightmost.closed), ")", sep = "")
#  res = .h2o.__exec2(x@h2o, expr)
#  new('H2OParsedData', h2o=x@h2o, key=res$dest_key)
#})
#
## setGeneric("histograms", function(object) { standardGeneric("histograms") })
## setMethod("histograms", "H2OParsedData", function(object) {
##   if(ncol(object) > .MAX_INSPECT_COL_VIEW)
##     warning(object@key, " has greater than ", .MAX_INSPECT_COL_VIEW, " columns. This may take awhile...")
##   res = .h2o.__remoteSend(object@h2o, .h2o.__PAGE_SUMMARY2, source=object@key, max_ncols=.Machine$integer.max)
##   list.of.bins <- lapply(res$summaries, function(x) {
##     if (x$stats$type == 'Enum') {
##       bins <- NULL
##     } else {
##       counts <- x$hcnt
##       breaks <- seq(x$hstart, by=x$hstep, length.out=length(x$hcnt) + 1)
##       bins <- list(counts,breaks)
##       names(bins) <- cbind('counts', 'breaks')
##     }
##     bins
##   })
##   return(list.of.bins)
## })