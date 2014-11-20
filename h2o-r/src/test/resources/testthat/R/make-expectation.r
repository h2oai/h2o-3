#' Make an equality test.
#'
#' This a convenience function to make a expectation that checks that
#' input stays the same.
#'
#' @param x a vector of values
#' @param expectation the type of equality you want to test for
#'   (\code{equals}, \code{is_equivalent_to}, \code{is_identical_to})
#' @export
#' @examples
#' x <- 1:10
#' make_expectation(x)
#'
#' make_expectation(mtcars$mpg)
#'
#' df <- data.frame(x = 2)
#' make_expectation(df)
make_expectation <- function(x, expectation = "equals") {
  obj <- substitute(x)
  expectation <- match.arg(expectation,
    c("equals", "is_equivalent_to", "is_identical_to"))

  dput(substitute(expect_that(obj, expectation(values)),
    list(obj = obj, expectation = as.name(expectation), values = x)))
}
