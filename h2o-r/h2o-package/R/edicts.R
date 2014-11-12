#'
#' Append a <key,value> pair to a list.
#'
#' Contained here are a set of helper methods that perform type checking on the value passed in.
#'
#' @param parms a list to add the <k,v> pair to
#' @param k a key, typically the name of some algorithm parameter
#' @param v a value, the value of the algorithm parameter

.addParm <- function(parms, k, v) {
  cmd <- sprintf("parms$%s = v", k)
  eval(parse(text=cmd))
  return(parms)
}

.addStringParm <- function(parms, k, v) {
  if (! missing(v)) {
    if (! is.character(v)) stop(sprintf("%s must be of type character"), k)
    parms <- .addParm(parms, k, v)
  }
  return(parms)
}

.addBooleanParm <- function(parms, k, v) {
  if (! missing(v)) {
    if (! is.logical(v)) stop(sprintf("%s must be of type logical"), k)
    parms <- .addParm(parms, k, as.numeric(v))
  }
  return(parms)
}

.addNumericParm <- function(parms, k, v) {
  if (! missing(v)) {
    if (! is.numeric(v)) stop(sprintf("%s must be of type numeric"), k)
    parms <- .addParm(parms, k, v)
  }
  return(parms)
}

.addDoubleParm <- function(parms, k, v) {
  parms = .addNumericParm(parms, k, v)
  return(parms)
}

.addFloatParm <- function(parms, k, v) {
  parms = .addNumericParm(parms, k, v)
  return(parms)
}

.addLongParm <- function(parms, k, v) {
  parms = .addNumericParm(parms, k, v)
  return(parms)
}

.addIntParm <- function(parms, k, v) {
  parms = .addNumericParm(parms, k, v)
  return(parms)
}

.addNumericArrayParm <- function(parms, k, v) {
  if (! missing(v)) {
    # if (! is.numeric(v)) stop(sprintf("%s must be of type numeric"), k)
    # arrAsString = paste(v, collapse=",")
    if(!all(sapply(v, is.numeric))) stop(sprintf("%s must contain all numeric elements"), k)
    # if(is.list(v) && length(v) == 1) v = v[[1]]
    arrAsString = sapply(v, function(x) {
        if(length(x) <= 1) return(x)
        paste("(", paste(x, collapse=","), ")", sep = "")
      })
    arrAsString = paste(arrAsString, collapse = ",")
    parms = .addParm(parms, k, arrAsString)
  }
  return(parms)
}

.addDoubleArrayParm <- function(parms, k, v) {
  parms = .addNumericArrayParm(parms, k, v)
}

.addIntArrayParm <- function(parms, k, v) {
  parms = .addNumericArrayParm(parms, k, v)
}

.putParm <- function(parms, t, k, v) {
  switch(type,
    int     = .addIntParm(parms,k,v),
    long    = .addLongParm(parms,k,v),
    float   = .addFloatParm(parms,k,v),
    double  = .addDoubleParm(parms,k,v),
    numeric = .addNumericParm(parms,k,v),
    boolean = .addBooleanParm(parms,k,v),
    string  = .addStringParm(parms,k,v),
    numary  = .addNumericArrayParm(parms,k,v)
  )
}