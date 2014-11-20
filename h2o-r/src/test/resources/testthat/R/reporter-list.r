#' @include reporter.r
NULL


setOldClass('proc_time')

#' List reporter: gather all test results along with elapsed time and 
#' file information.
#'
#' This reporter gathers all results, adding additional information such as
#' test elapsed time, and test filename if available. Very useful for reporting.
#'
#' @export
#' @export ListReporter
#' @aliases ListReporter
#' @keywords debugging
#' @param ... Arguments used to initialise class
ListReporter <- setRefClass("ListReporter", contains = "Reporter",
  fields = list(
    start_test_time = 'proc_time',
    file = 'character',
    results = 'list',
    current_test_results = 'list'),
    
  methods = list(
    ### overriden methods from Reporter
    start_reporter = function(...) {
      callSuper(...)
      results <<- list()
      current_test_results <<- list()
    },
    
    start_test = function(desc) {
      callSuper(desc)
      current_test_results <<- list()
      start_test_time <<- proc.time()
    },
    
    end_test = function() {
      el <- as.double(proc.time() - start_test_time)
      fname <- if (length(file)) file else ''
      test_info <- list(file = fname, context = context, test = test, 
        user = el[1], system = el[2], real = el[3], 
        results = current_test_results)
      results <<- c(results, list(test_info))
      current_test_results <<- list()
      
      callSuper() # at the end because it resets the test name
    },

    add_result = function(result) {
      callSuper(result)
      current_test_results <<- c(current_test_results, list(result))
    },

    ### new methods
    start_file = function(name) {
      file <<- name
    },

    get_summary = function() {
      summarize_results(results)
    }

  )
)

# format results from ListReporter
summarize_results <- function(results_list) {
  rows <- lapply(results_list, .sumarize_one_test_results)
  do.call(rbind, rows)
}

.sumarize_one_test_results <- function(test) {
  test_results <- test$results
  nb_tests <- length(test_results)
  
  nb_failed <- 0L
  error <- FALSE

  if (nb_tests > 0) {
    # error reports should be handled differently. 
    # They may not correspond to an expect_that() test so remove them
    last_test <- test_results[[nb_tests]]
    error <- last_test$error
    if (error) {
      test_results <- test_results[- nb_tests]
      nb_tests <- length(test_results)
    }
    
    nb_failed <- sum(!vapply(test_results, '[[', TRUE, 'passed'))
  }
  
  context <- if (length(test$context)) test$context else ''
  res <- data.frame(file = test$file, context = context, test = test$test,
    nb = nb_tests, failed = nb_failed, error = error, user = test$user, 
    system = test$system, real = test$real, stringsAsFactors = FALSE)
  
  res
}



