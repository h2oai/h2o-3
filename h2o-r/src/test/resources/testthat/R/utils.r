# Find expression that created a variable
find_expr <- function(name, env = parent.frame()) {
  subs <- do.call("substitute", list(as.name(name), env))
  paste0(deparse(subs, width.cutoff = 500), collapse = "\n")
}

# A version of grepl that's vectorised along pattern, not x
grepl2 <- function(pattern, x, ...) {
  stopifnot(length(x) == 1)

  vapply(pattern, grepl, x, ..., FUN.VALUE = logical(1), USE.NAMES = FALSE)
}

starts_with <- function(string, prefix) {
  substr(string, 1, nchar(prefix)) == prefix
}

is_directory <- function(x) file.info(x)$isdir
is_readable <- function(x) file.access(x, 4) == 0

is.error <- function(x) inherits(x, "error")

is.skip <- function(x) inherits(x, "skip")

#' Skip a test.
#'
#' This function allows you to skip a test if it's not currently available.
#' This will produce an informative message, but will not cause the test
#' suite to fail. \code{skip_on_cran()} uses \code{skip()}, together with
#' the \code{NOT_CRAN} environment variable (set by devtools), to automatically
#' skip tests that should not be run on CRAN.
#' \code{skip_on_travis} uses the environment variable \code{TRAVIS} which is
#' automatically set on Travis CI.
#'
#' @param message A message describing why the test was skipped.
#' @export
#' @examples
#' if (FALSE) skip("No internet connection")
skip <- function(message) {
  cond <- structure(list(message = message), class = c("skip", "condition"))
  stop(cond)
}

#' @export
#' @rdname skip
skip_on_cran <- function() {
  if (identical(Sys.getenv("NOT_CRAN"), "true")) return()

  skip("On CRAN")
}

#' @export
#' @rdname skip
skip_on_travis <- function() {
  if (!identical(Sys.getenv("TRAVIS"), "true")) return()

  skip("On Travis")
}
