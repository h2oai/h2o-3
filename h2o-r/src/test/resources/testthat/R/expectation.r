#' Expectation class.
#'
#' Any expectation should return objects of this class - see the built in
#' expectations for details.
#'
#' @param passed a single logical value indicating whether the test passed
#'  (\code{TRUE}), failed (\code{FALSE}), or threw an error (\code{NA})
#' @param failure_msg A text description of failure
#' @param success_msg A text description of success
#' @aliases expectation print.expectation format.expectation
#' @keywords internal
#' @export
expectation <- function(passed, failure_msg, success_msg = "unknown") {
  structure(
    list(
      passed = passed,
      error = FALSE,
      skipped = FALSE,
      failure_msg = failure_msg,
      success_msg = success_msg
    ),
    class = "expectation"
  )
}

expectation_error <- function(error, calls) {
  msg <- gsub("Error.*?: ", "", as.character(error))

  if (length(calls) > 0) {
    traceback <- create_traceback(calls)
    user_calls <- paste0(traceback, collapse = "\n")
    msg <- paste0(msg, "\n", user_calls)
  } else {
    # Need to remove trailing newline from error message to be consistent
    # with other messages
    msg <- gsub("\n$", "", msg)
  }

  structure(
    list(
      passed = FALSE,
      error = TRUE,
      skipped = FALSE,
      failure_msg = msg,
      success_msg = "no error occured"
    ),
    class = "expectation"
  )
}

expectation_skipped <- function(error) {
  msg <- gsub("Error.*?: ", "", as.character(error))

  structure(
    list(
      passed = FALSE,
      error = FALSE,
      skipped = TRUE,
      failure_msg = msg,
      success_msg = "not skipped"
    ),
    class = "expectation"
  )
}

#' @export
#' @rdname expectation
#' @param x object to test for class membership
is.expectation <- function(x) inherits(x, "expectation")

#' @export
print.expectation <- function(x, ...) cat(format(x), "\n")

#' @export
format.expectation <- function(x, ...) {
  if (x$passed) {
    paste0("As expected: ", x$success_msg)
  } else {
    paste0("Not expected: ", x$failure_msg, ".")
  }
}

#' @export
as.character.expectation <- function(x, ...) format(x)

negate <- function(expt) {
  stopifnot(is.expectation(expt))

  # If it's an error, don't need to do anything
  if (expt$error) return(expt)

  opp <- expt
  opp$passed <- !expt$passed
  opp$failure_msg <- expt$success_msg
  opp$success_msg <- expt$failure_msg
  opp

}
