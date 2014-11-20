#' Stub object for managing a reporter of tests.
#'
#' Do not clone directly from this object - children should implement all
#' methods.
#'
#' @keywords internal.
#' @export
#' @export Reporter
#' @aliases Reporter
#' @param ... Arguments used to initialise class
#' @importFrom methods setRefClass
Reporter <- setRefClass("Reporter",
  fields = list(
    context = "character",
    test = "ANY",
    failed = "logical",
    context_open = "logical"
  ), methods = list(
    initialize = function(...) {
      context_open <<- FALSE
      failed <<- FALSE
      test <<- NULL

      initFields(...)
    },
    start_reporter = function() {
      failed <<- FALSE
    },
    start_context = function(desc) {
      context <<- desc
    },
    start_test = function(desc) {
      test <<- desc
    },
    add_result = function(result) {},
    end_test = function() {
      test <<- ""
    },
    end_context = function() {},
    end_reporter = function() {}
  )
)
