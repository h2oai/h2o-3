#' Describe the context of a set of tests.
#'
#' A context defines a set of tests that test related functionality.  Usually
#' you will have one context per file, but you may have multiple contexts
#' in a single file if you so choose.
#'
#' @param desc description of context.  Should start with a capital letter.
#' @export
#' @examples
#' context("String processing")
#' context("Remote procedure calls")
context <- function(desc) {
  rep <- get_reporter()
  if (rep$context_open) {
    rep$end_context()
  } else {
    rep$context_open <- TRUE
  }
  rep$start_context(desc)
}

end_context <- function() {
  rep <- get_reporter()
  if (!rep$context_open) return(invisible())
  rep$end_context()
  rep$context_open <- FALSE
  invisible()
}
