#' Evaluate a promise, capturing all types of output.
#'
#' This uses \code{\link[evaluate]{evaluate}} a promise, returning the
#' result, test, messages and warnings that the code creates in a list.
#' It is used to evaluate code for all test that tests, ensuring that
#' (as much as possible) any spurious output is suppressed during the
#' testing process.
#'
#' @param code Code to evaluate. This should be an unevaluated expression.
#' @param print If \code{TRUE} and the result of evaluating \code{code} is
#'   visible this will print the result, ensuring that the output of printing
#'   the object is included in the overall output
#' @export
#' @return A list containing
#'  \item{result}{The result of the function}
#'  \item{output}{A string containing all the output from the function}
#'  \item{warnings}{A character vector containing the text from each warning}
#'  \item{messages}{A character vector containing the text from each message}
#' @examples
#' evaluate_promise({
#'   print("1")
#'   message("2")
#'   warning("3")
#'   4
#' })
evaluate_promise <- function(code, print = FALSE) {
  warnings <- character()
  wHandler <- function(w) {
    warnings <<- c(warnings, w$message)
    invokeRestart("muffleWarning")
  }

  messages <- character()
  mHandler <- function(m) {
    messages <<- c(messages, m$message)
    invokeRestart("muffleMessage")
  }

  temp <- file()
  on.exit(close(temp))

  result <- with_sink(temp,
    withCallingHandlers(
      withVisible(code), warning = wHandler, message = mHandler
    )
  )
  if (result$visible && print) {
    with_sink(temp, print(result$value))
  }

  output <- paste0(readLines(temp, warn = FALSE), collapse = "\n")

  list(
    result = result$result,
    output = output,
    warnings = warnings,
    messages = messages
  )
}

with_sink <- function(connection, code, ...) {
  sink(connection, ...)
  on.exit(sink())

  code
}
