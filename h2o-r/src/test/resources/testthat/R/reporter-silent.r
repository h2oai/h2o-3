#' @include reporter.r
NULL

#' Test reporter: gather all errors silently.
#'
#' This reporter quietly runs all tests, simply gathering the results
#' for later use. This is helpful for programmatically inspecting errors
#' after a test run.
#'
#' @export
#' @export SilentReporter
#' @aliases SilentReporter
#' @keywords debugging
#' @param ... Arguments used to initialise class
SilentReporter <- setRefClass("SilentReporter", contains = "Reporter",
  fields = c("failures"),
  methods = list(
    initialize = function(...) {
      failures <<- list()
      callSuper(...)
    },
    start_test = function(desc) {
      test <<- desc
    },

    end_test = function() {
      test <<- NULL
    },

    add_result = function(result) {
      if (result$passed) return()
      failures[[test]] <<- result
    }
  )
)
