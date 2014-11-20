#' @include reporter.r
NULL

#' Multi reporter: combine several reporters in one.
#'
#' This reporter is useful to use several reporters at the same time, e.g. 
#' adding a custom reporter without removing the current one.
#' 
#' @export
#' @export MultiReporter
#' @aliases MultiReporter
#' @keywords debugging
#' @param ... Arguments used to initialise class
MultiReporter <- setRefClass("MultiReporter", contains = "Reporter",
  fields = list(reporters = 'list'),
    
  methods = list(
    start_reporter = function() {
      .oapply(reporters, 'start_reporter')
    },
    start_context = function(desc) {
      .oapply(reporters, 'start_context', desc) 
    },
    start_test = function(desc) {
      .oapply(reporters, 'start_test', desc) 
    },
    add_result = function(result) {
      .oapply(reporters, 'add_result', result)
    },
    end_test = function() {
      .oapply(reporters, 'end_test') 
    },
    end_context = function() {
      .oapply(reporters, 'end_context') 
    },
    end_reporter = function() {
      .oapply(reporters, 'end_reporter')
    }
  )
)

.oapply <- function(objects, method, ...) {
  for (o in objects) 
    eval(substitute(o$FUN(...), list(FUN = method, ...)))
}

