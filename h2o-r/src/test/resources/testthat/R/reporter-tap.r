#' @include reporter.r
NULL

#' Test reporter: TAP format.
#'
#' This reporter will output results in the Test Anything Protocol (TAP),
#' a simple text-based interface between testing modules in a test harness.
#' For more information about TAP, see http://testanything.org
#'
#' @export
#' @export TapReporter
#' @aliases TapReporter
#' @keywords debugging
#' @param ... Arguments used to initialise class
TapReporter <- setRefClass("TapReporter", contains = "Reporter",
  fields = list(
    "results" = "list",
    "n" = "integer",
    "has_tests" = "logical",
    "contexts" = "character"),

  methods = list(

    start_context = function(desc) {
      contexts[n+1] <<- desc;
    },

    start_reporter = function() {
      results <<- list()
      n <<- 0L
      has_tests <<- FALSE
      contexts <<- NA_character_
    },

    add_result = function(result) {
      has_tests <<- TRUE
      n <<- n + 1L;
      if (!result$passed) {
        failed <<- TRUE
      }

      result$test <- if (is.null(test)) "(unknown)" else test
      results[[n]] <<- result
    },

    end_reporter = function() {
        if(has_tests) {
            cat("1..", n, '\n', sep='');
            for(i in 1:n) {
                if (! is.na(contexts[i])) {
                    cat("# Context", contexts[i], "\n")
                }
                result <- results[[i]];
                if (result$passed) {
                    cat('ok', i, result$test, '\n')
                } else {
                    cat('not ok', i, result$test, '\n')
                    msg <- gsub('\n', '\n  ', result$failure_msg)
                    cat(' ', msg, '\n')
                }
            }
        }
    }
  )
)
