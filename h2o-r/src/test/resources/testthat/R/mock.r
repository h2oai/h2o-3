#' Mock functions in a package.
#'
#' Executes code after temporarily substituting implementations of package
#' functions.  This is useful for testing code that relies on functions that are
#' slow, have unintended side effects or access resources that may not be
#' available when testing.
#'
#' This works by using some C code to temporarily modify the mocked function \emph{in place}.
#' On exit (regular or error), all functions are restored to their previous state.
#' This is somewhat abusive of R's internals, and is still experimental, so use with care.
#'
#' @param ... named parameters redefine mocked functions, unnamed parameters
#'   will be evaluated after mocking the functions
#' @param .env the environment in which to patch the functions,
#'   defaults to the top-level environment.  A character is interpreted as
#'   package name.
#' @return The result of the last unnamed parameter
#' @references Suraj Gupta (2012): \href{http://obeautifulcode.com/R/How-R-Searches-And-Finds-Stuff}{How R Searches And Finds Stuff}
#' @export
#' @examples
#' with_mock(
#'   all.equal = function(x, y, ...) TRUE,
#'   expect_equal(2 * 3, 4),
#'   .env = "base"
#' )
#' with_mock(
#'   `base::identical` = function(x, y, ...) TRUE,
#'   `base::all.equal` = function(x, y, ...) TRUE,
#'   expect_equal(x <- 3 * 3, 6),
#'   expect_identical(x + 4, 9)
#' )
#' throws_error()(expect_equal(3, 5))
#' throws_error()(expect_identical(3, 5))
with_mock <- function(..., .env = topenv()) {
  new_values <- eval(substitute(alist(...)))
  mock_qual_names <- names(new_values)

  if (all(mock_qual_names == "")) {
    warning("Not mocking anything. Please use named parameters to specify the functions you want to mock.")
    code_pos <- TRUE
  } else {
    code_pos <- (mock_qual_names == "")
  }
  code <- new_values[code_pos]

  mocks <- extract_mocks(new_values = new_values[!code_pos], .env = .env)

  on.exit(lapply(mocks, reset_mock), add = TRUE)
  lapply(mocks, set_mock)

  # Evaluate the code
  ret <- invisible(NULL)
  for (expression in code) {
    ret <- eval(expression, parent.frame())
  }
  ret
}

pkg_rx <- ".*[^:]"
colons_rx <- "::(?:[:]?)"
name_rx <- ".*"
pkg_and_name_rx <- sprintf("^(?:(%s)%s)?(%s)$", pkg_rx, colons_rx, name_rx)

extract_mocks <- function(new_values, .env) {
  if (is.environment(.env))
    .env <- environmentName(.env)
  mock_qual_names <- names(new_values)

  lapply(
    setNames(nm = mock_qual_names),
    function(qual_name) {
      pkg_name <- gsub(pkg_and_name_rx, "\\1", qual_name)

      name <- gsub(pkg_and_name_rx, "\\2", qual_name)

      if (pkg_name == "")
        pkg_name <- .env

      env <- asNamespace(pkg_name)

      if (!exists(name, envir = env, mode = "function"))
        stop("Function ", name, " not found in environment ",
             environmentName(env), ".")
      mock(name = name, env = env, new = eval(new_values[[qual_name]]))
    }
  )
}

#' @useDynLib testthat duplicate_
mock <- function(name, env, new) {
  target_value <- get(name, envir = env, mode = "function")
  structure(list(
    env = env, name = as.name(name),
    orig_value = .Call(duplicate_, target_value), target_value = target_value,
    new_value = new), class = "mock")
}

#' @useDynLib testthat reassign_function
set_mock <- function(mock) {
  .Call(reassign_function, mock$name, mock$env, mock$target_value, mock$new_value)
}

#' @useDynLib testthat reassign_function
reset_mock <- function(mock) {
  .Call(reassign_function, mock$name, mock$env, mock$target_value, mock$orig_value)
}
