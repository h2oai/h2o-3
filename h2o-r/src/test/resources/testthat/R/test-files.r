#' Generate default testing environment.
#'
#' We use a new environment which inherits from \code{\link{globalenv}}.
#' In an ideal world, we'd avoid putting the global environment on the
#' search path for tests, but it's not currently possible without losing
#' the ability to load packages in tests.
#'
#' @keywords internal
#' @export
test_env <- function() {
  new.env(parent = globalenv())
}

#' Run all of the tests in a directory.
#'
#' Test files start with \code{test} and are executed in alphabetical order
#' (but they shouldn't have dependencies). Helper files start with
#' \code{helper} and loaded before any tests are run.
#'
#' @param path path to tests
#' @param reporter reporter to use
#' @param filter If not \code{NULL}, only tests with file names matching this
#'   regular expression will be executed.  Matching will take on the file
#'   name after it has been stripped of \code{"test-"} and \code{".r"}.
#' @param env environment in which to execute test suite.
#' @return a data frame of the summary of test results
#' @export
test_dir <- function(path, filter = NULL, reporter = "summary",
                     env = test_env()) {
  current_reporter <- find_reporter(reporter)
  lister <- ListReporter$new()
  reporter <- MultiReporter$new(reporters = list(current_reporter, lister))

  source_dir(path, "^helper.*\\.[rR]$", env = env)

  files <- dir(path, "^test.*\\.[rR]$")
  if (!is.null(filter)) {
    test_names <- basename(files)
    test_names <- gsub("test-?", "", test_names)
    test_names <- gsub("\\.[rR]", "", test_names)

    files <- files[grepl(filter, test_names)]
  }

  .custom_test_file <- function(fname) {
    lister$start_file(fname)
    .test_file(file.path(path, fname), env)
  }
  with_reporter(reporter, lapply(files, .custom_test_file))

  invisible(lister$get_summary())
}

#' Load all source files in a directory.
#'
#' The expectation is that the files can be sourced in alphabetical order.
#'
#' @param path path to tests
#' @param pattern regular expression used to filter files
#' @param env environment in which to store results
#' @param chdir change working directory to path?
#' @keywords internal
#' @export
source_dir <- function(path, pattern = "\\.[rR]$", env = test_env(),
                       chdir = TRUE) {
  files <- normalizePath(sort(dir(path, pattern, full.names = TRUE)))
  if (chdir) {
    old <- setwd(path)
    on.exit(setwd(old))
  }

  lapply(files, sys.source2, envir = env)
}

#' Run all tests in specified file.
#'
#' @param path path to file
#' @param reporter reporter to use
#' @param env environment in which to execute the tests
#' @return a data frame of the summary of test results
#' @export
test_file <- function(path, reporter = "summary", env = test_env()) {
  current_reporter <- find_reporter(reporter)
  lister <- ListReporter$new()
  reporter <- MultiReporter$new(reporters = list(current_reporter, lister))
  lister$start_file(basename(path))
  with_reporter(reporter, .test_file(path, env))

  invisible(lister$get_summary())
}

.test_file <- function(path, parent_env) {
  old <- setwd(dirname(path))
  on.exit(setwd(old))

  sys.source2(basename(path), new.env(parent = parent_env))
  end_context()
}

sys.source2 <- function(file, envir = parent.frame()) {
  stopifnot(file.exists(file))
  stopifnot(is.environment(envir))

  lines <- readLines(file, warn = FALSE)
  srcfile <- srcfilecopy(file, lines, file.info(file)[1, "mtime"],
    isFile = TRUE)
  exprs <- parse(text = lines, n = -1, srcfile = srcfile)

  n <- length(exprs)
  if (n == 0L) return(invisible())


  invisible(eval(exprs, envir))
}

