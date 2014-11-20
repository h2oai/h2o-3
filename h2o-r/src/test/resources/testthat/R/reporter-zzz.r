#' @include reporter-stop.r
NULL

#' Get/set reporter; execute code in specified reporter.
#'
#' Changes global reporter to that specified, runs code and the returns
#' global reporter back to previous value.
#'
#' @keywords internal
#' @param reporter test reporter to use
#' @param code code block to execute
#' @name reporter-accessors
NULL

testthat_env <- new.env()

# Default has to be the stop reporter, since it is this that will be run by
# default from the command line and in R CMD test.
testthat_env$reporter <- StopReporter$new()

#' @rdname reporter-accessors
#' @export
set_reporter <- function(reporter) {
  old <- testthat_env$reporter
  testthat_env$reporter <- reporter
  old
}

#' @rdname reporter-accessors
#' @export
get_reporter <- function() {
  testthat_env$reporter
}

#' @rdname reporter-accessors
#' @export
with_reporter <- function(reporter, code) {
  reporter <- find_reporter(reporter)

  old <- set_reporter(reporter)
  on.exit(set_reporter(old))

  reporter$start_reporter()
  force(code)
  reporter$end_reporter()

  invisible(reporter)
}

#' Find reporter object given name
#'
#' If not found, will return informative error message
#'
#' @param reporter name of reporter
#' @keywords internal
find_reporter <- function(reporter) {
  if (inherits(reporter, "Reporter")) return(reporter)

  name <- reporter
  substr(name, 1, 1) <- toupper(substr(name, 1, 1))
  name <- paste0(name, "Reporter")

  if (!exists(name)) {
    stop("Can not find test reporter ", reporter, call. = FALSE)
  }

  get(name)$new()
}
