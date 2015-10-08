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
  myIP   <- "127.0.0.1"
  myPort <- 54321
  i <- 1
  while (i <= length(args)) {
    s <- args[i]
    if (s == "--usecloud") {
      i <- i + 1
      argsplit <- strsplit(args[i], ":")[[1]]
      myIP     <- argsplit[1]
      myPort   <- as.numeric(argsplit[2])
    }
    i <- i + 1
  }
  return(list(myIP,myPort));
}
