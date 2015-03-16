#'
#' h2o-R Unit Test Utilities for hadoop
#'

heading <- function(message) {
    cat("\n")
    cat(message)
    cat("\n")
    cat("\n")
}

PASS_BANNER <- function() {
    cat("\n")
    cat("PASS\n")
    cat("\n")
}

get_args <- function(args) {
  fileName <- commandArgs()[grep('*\\.R',unlist(commandArgs()))]
  if (length(args) > 1) {
    m <- paste("Usage: R f ", paste(fileName, " --args H2OServer:Port",sep=""),sep="")
    stop(m);
  }

  if (length(args) == 0) {
    myIP   = "127.0.0.1"
    myPort = 54321
  } else {
    argsplit = strsplit(args[1], ":")[[1]]
    myIP     = argsplit[1]
    myPort   = as.numeric(argsplit[2])
  }
  return(list(myIP,myPort));
}
