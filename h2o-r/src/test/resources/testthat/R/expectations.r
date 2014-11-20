#' Expectation: does the object inherit from a class?
#'
#' Tests whether or not an object inherits from any of a list of classes.
#'
#' @param class character vector of class names
#' @seealso \code{\link{inherits}}
#' @family expectations
#' @export
#' @examples
#' expect_is(1, "numeric")
#' a <- matrix(1:10, nrow = 5)
#' expect_is(a, "matrix")
#'
#' expect_is(mtcars, "data.frame")
#' # alternatively for classes that have an is method
#' expect_true(is.data.frame(mtcars))
is_a <- function(class) {
  function(x) {
    actual <- paste0(class(x), collapse = ", ")
    expectation(
      inherits(x, class),
      paste0("inherits from ", actual, " not ", class),
      paste0("inherits from ", class)
    )
  }
}
#' @export
#' @rdname is_a
#' @inheritParams expect_that
expect_is <- function(object, class, info = NULL, label = NULL) {
  if (is.null(label)) {
    label <- find_expr("object")
  }
  expect_that(object, is_a(class), info, label)
}

#' Expectation: is the object true/false?
#'
#' These are fall-back expectations that you can use when none of the other
#' more specific expectations apply. The disadvantage is that you may get
#' a less informative error message.
#'
#' Attributes are ignored.
#'
#' @seealso \code{\link{is_false}} for complement
#' @family expectations
#' @export
#' @examples
#' expect_true(2 == 2)
#' # Failed expectations will throw an error
#' \dontrun{
#' expect_true(2 != 2)
#' }
#' expect_true(!(2 != 2))
#' # or better:
#' expect_false(2 != 2)
#'
#' a <- 1:3
#' expect_true(length(a) == 3)
#' # but better to use more specific expectation, if available
#' expect_equal(length(a), 3)
is_true <- function() {
  function(x) {
    expectation(
      identical(as.vector(x), TRUE),
      "isn't true",
      "is true"
    )
  }
}
#' @export
#' @rdname is_true
#' @inheritParams expect_that
expect_true <- function(object, info = NULL, label = NULL) {
  if (is.null(label)) {
    label <- find_expr("object")
  }
  expect_that(object, is_true(), info, label)
}

#' @export
#' @rdname is_true
is_false <- function() {
  function(x) {
    expectation(
      identical(as.vector(x), FALSE),
      "isn't false",
      "is false"
    )
  }
}
#' @export
#' @rdname is_true
#' @inheritParams expect_that
expect_false <- function(object, info = NULL, label = NULL) {
  if (is.null(label)) {
    label <- find_expr("object")
  }
  expect_that(object, is_false(), info, label)
}

#' Expectation: is the object NULL?
#'
#' @family expectations
#' @export
#' @examples
#' expect_null(NULL)
#'
is_null <- function() {
  function(x) {
    expectation(
      identical(x, NULL),
      "isn't null",
      "is null"
    )
  }
}
#' @export
#' @rdname is_null
#' @inheritParams expect_that
expect_null <- function(object, info = NULL, label = NULL) {
  if (is.null(label)) {
    label <- find_expr("object")
  }
  expect_that(object, is_null(), info, label)
}

#' Expectation: is the object equal (with numerical tolerance) to a value?
#'
#' Comparison performed using \code{\link{all.equal}}.
#
#' @param expected Expected value
#' @param label For full form, label of expected object used in error
#'   messages. Useful to override default (deparsed expected expression) when
#'   doing tests in a loop.  For short cut form, object label. When
#'   \code{NULL}, computed from deparsed object.
#' @param expected.label Equivalent of \code{label} for shortcut form.
#' @param ... other values passed to \code{\link{all.equal}}
#' @family expectations
#' @export
#' @examples
#' a <- 10
#' expect_equal(a, 10)
#'
#' # Use equals() when testing for numeric equality
#' sqrt(2) ^ 2 - 1
#' expect_equal(sqrt(2) ^ 2, 2)
#' # Neither of these forms take floating point representation errors into
#' # account
#' \dontrun{
#' expect_true(sqrt(2) ^ 2 == 2)
#' expect_identical(sqrt(2) ^ 2, 2)
#' }
#'
#' # You can pass on additional arguments to all.equal:
#' \dontrun{
#' # Test the ABSOLUTE difference is within .002
#' expect_equal(object = 10.01, expected = 10, tolerance = .002,
#'   scale = 1)
#'
#' # Test the RELATIVE difference is within .002
#' expectedValue <- 10
#' expect_equal(object = 10.01, expected = expectedValue, tolerance = 0.002,
#'   scale = expectedValue)
#' }
equals <- function(expected, label = NULL, ...) {
  if (is.null(label)) {
    label <- find_expr("expected")
  } else if (!is.character(label) || length(label) != 1) {
    label <- deparse(label)
  }

  function(actual) {
    same <- compare(expected, actual, ...)

    expectation(
      same$equal,
      paste0("not equal to ", label, "\n", same$message),
      paste0("equals ", label)
    )
  }
}
#' @export
#' @rdname equals
#' @inheritParams expect_that
expect_equal <- function(object, expected, ..., info = NULL, label = NULL,
                         expected.label = NULL) {
  if (is.null(label)) {
    label <- find_expr("object")
  }
  if (is.null(expected.label)) {
    expected.label <- find_expr("expected")
  }
  expect_that(object, equals(expected, label = expected.label, ...),
              info = info, label = label)
}


#' Expectation: is the object equal to a reference value stored in a file?
#'
#' This expectation is equivalent to \code{\link{equals}}, except that the
#' expected value is stored in an RDS file instead of being specified
#' literally. This can be helpful when the value is necessarily complex. If
#' the file does not exist then it will be created using the value of the
#' specified object, and subsequent tests will check for consistency against
#' that generated value. The test can be reset by deleting the RDS file.
#
#' @param file The file name used to store the object. Should have an "rds"
#'   extension.
#' @param label For the full form, a label for the expected object, which is
#'   used in error messages. Useful to override the default (which is based
#'   on the file name), when doing tests in a loop. For the short-cut form,
#'   the object label, which is computed from the deparsed object by default.
#' @param expected.label Equivalent of \code{label} for shortcut form.
#' @param ... other values passed to \code{\link{equals}}
#' @family expectations
#' @export
#' @examples
#' \dontrun{
#' expect_equal_to_reference(1, "one.rds")
#' }
equals_reference <- function(file, label = NULL, ...) {
  if (file.exists(file)) {
    reference <- readRDS(file)
    if (is.null(label)) {
      label <- paste("reference from", file)
    }
    equals(reference, label = label, ...)
  } else {
    function(actual) {
      # saveRDS() returns no useful information to use for the expectation
      saveRDS(actual, file)
      expectation(TRUE, "should never fail", "saved to file")
    }
  }
}
#' @export
#' @rdname equals_reference
#' @inheritParams expect_that
expect_equal_to_reference <- function(object, file, ..., info = NULL,
                                      label = NULL, expected.label = NULL) {
  if (is.null(label)) {
    label <- find_expr("object")
  }
  if (is.null(expected.label)) {
    expected.label <- paste("reference from", file)
  }
  expect_that(object, equals_reference(file, label = expected.label, ...),
              info = info, label = label)
}


#' Expectation: is the object equivalent to a value?
#' This expectation tests for equivalency: are two objects equal once their
#' attributes have been removed.
#'
#' @inheritParams equals
#' @family expectations
#' @export
#' @examples
#' a <- b <- 1:3
#' names(b) <- letters[1:3]
#' expect_equivalent(a, b)
is_equivalent_to <- function(expected, label = NULL) {
  if (is.null(label)) {
    label <- find_expr("expected")
  } else if (!is.character(label) || length(label) != 1) {
    label <- deparse(label)
  }
  function(actual) {
    equals(expected, check.attributes = FALSE)(actual)
  }
}
#' @export
#' @rdname is_equivalent_to
#' @inheritParams expect_that
expect_equivalent <- function(object, expected, info = NULL, label = NULL,
                              expected.label=NULL) {
  if (is.null(label)) {
    label <- find_expr("object")
  }
  if (is.null(expected.label)) {
    expected.label <- find_expr("expected")
  }
  expect_that(object, is_equivalent_to(expected, label = expected.label),
              info = info, label = label)
}

#' Expectation: is the object identical to another?
#'
#' Comparison performed using \code{\link{identical}}.
#'
#' @inheritParams equals
#' @family expectations
#' @export
#' @examples
#' a <- letters[1:3]
#' expect_identical(a, c("a", "b", "c"))
#'
#' # Identical does not take into account numeric tolerance
#' \dontrun{
#' expect_identical(sqrt(2) ^ 2, 2)
#' }
is_identical_to <- function(expected, label = NULL) {
  if (is.null(label)) {
    label <- find_expr("expected")
  } else if (!is.character(label) || length(label) != 1) {
    label <- deparse(label)
  }

  function(actual) {
    if (identical(actual, expected)) {
      diff <- ""
    } else {
      same <- all.equal(expected, actual)
      if (isTRUE(same)) {
        diff <- "Objects equal but not identical"
      } else {
        diff <- paste0(same, collapse = "\n")
      }
    }

    expectation(
      identical(actual, expected),
      paste0("is not identical to ", label, ". Differences: \n", diff),
      paste0("is identical to ", label)
    )
  }
}
#' @export
#' @rdname is_identical_to
#' @inheritParams expect_that
expect_identical <- function(object, expected, info = NULL, label = NULL,
                             expected.label = NULL) {
  if (is.null(label)) {
    label <- find_expr("object")
  }
  if (is.null(expected.label)) {
    expected.label <- find_expr("expected")
  }
  expect_that(object, is_identical_to(expected, label = expected.label),
              info = info, label = label)
}

#' Expectation: does string match regular expression?
#'
#' If the object to be tested has length greater than one, all elements of
#' the vector must match the pattern in order to pass.
#'
#' @param regexp regular expression to test against
#' @param all should all elements of actual value match \code{regexp} (TRUE),
#'    or does only one need to match (FALSE)
#' @param ... For \code{matches}: other arguments passed on to
#'   \code{\link{grepl}}. For \code{expect_match}: other arguments passed on
#'   to \code{matches}.
#' @family expectations
#' @export
#' @examples
#' expect_match("Testing is fun", "fun")
#' expect_match("Testing is fun", "f.n")
matches <- function(regexp, all = TRUE, ...) {
  stopifnot(is.character(regexp), length(regexp) == 1)
  function(char) {
    matches <- grepl(regexp, char, ...)
    if (length(char) > 1) {
      values <- paste0("Actual values:\n",
        paste0("* ", encodeString(char), collapse = "\n"))
    } else {
      values <- paste0("Actual value: \"", encodeString(char), "\"")
    }

    expectation(
      length(matches) > 0 && if (all) all(matches) else any(matches),
      paste0("does not match '", regexp, "'. ", values),
      paste0("matches '", regexp, "'")
    )
  }
}
#' @export
#' @rdname matches
#' @inheritParams expect_that
expect_match <- function(object, regexp, ..., info = NULL, label = NULL) {
  if (is.null(label)) {
    label <- find_expr("object")
  }
  expect_that(object, matches(regexp, ...), info = info, label = label)
}

#' Expectation: does printed output match a regular expression?
#'
#' @param regexp regular expression to test against
#' @param ... other arguments passed to \code{\link{matches}}
#' @family expectations
#' @export
#' @examples
#' str(mtcars)
#' expect_output(str(mtcars), "32 obs")
#' expect_output(str(mtcars), "11 variables")
#'
#' # You can use the arguments of grepl to control the matching
#' expect_output(str(mtcars), "11 VARIABLES", ignore.case = TRUE)
#' expect_output(str(mtcars), "$ mpg", fixed = TRUE)
prints_text <- function(regexp, ...) {
  function(expr) {
    output <- evaluate_promise(expr, print = TRUE)$output
    matches(regexp, ...)(output)
  }
}
#' @export
#' @rdname prints_text
#' @inheritParams expect_that
expect_output <- function(object, regexp, ..., info = NULL, label = NULL) {
  if (is.null(label)) {
    label <- find_expr("object")
  }
  expect_that(object, prints_text(regexp, ...), info = info, label = label)
}

#' Expectation: does expression throw an error?
#'
#' @param regexp optional regular expression to match. If not specified, just
#'   asserts that expression throws some error.
#' @param ... other arguments passed to \code{\link{matches}}
#' @family expectations
#' @export
#' @examples
#' f <- function() stop("My error!")
#' expect_error(f())
#' expect_error(f(), "My error!")
#'
#' # You can use the arguments of grepl to control the matching
#' expect_error(f(), "my error!", ignore.case = TRUE)
throws_error <- function(regexp = NULL, ...) {
  function(expr) {
    res <- try(force(expr), TRUE)

    no_error <- !inherits(res, "try-error")
    if (no_error) {
      return(expectation(FALSE,
        "code did not generate an error",
        "code generated an error"
      ))
    }

    if (!is.null(regexp)) {
      matches(regexp, ...)(res)
    } else {
      expectation(TRUE, "no error thrown", "threw an error")
    }
  }
}
#' @export
#' @rdname throws_error
#' @inheritParams expect_that
expect_error <- function(object, regexp = NULL, ..., info = NULL,
                         label = NULL) {
  if (is.null(label)) {
    label <- find_expr("object")
  }
  expect_that(object, throws_error(regexp, ...), info = info, label = label)
}

#' Expectation: does expression give a warning?
#'
#' Needs to match at least one of the warnings produced by the expression.
#'
#' @param regexp optional regular expression to match. If not specified, just
#'   asserts that expression gives some warning.
#' @param all if \code{TRUE}, all warnings must match given regular expression;
#'   if \code{FALSE} (the default), then only only warning needs to match
#' @param ... other arguments passed to \code{\link{matches}}
#' @family expectations
#' @export
#' @examples
#' f <- function(x) {
#'   if (x < 0) warning("*x* is already negative")
#'   -x
#' }
#' expect_warning(f(-1))
#' expect_warning(f(-1), "already negative")
#' \dontrun{expect_warning(f(1))}
#'
#' # You can use the arguments of grepl to control the matching
#' expect_warning(f(-1), "*x*", fixed = TRUE)
#' expect_warning(f(-1), "NEGATIVE", ignore.case = TRUE)
gives_warning <- function(regexp = NULL, all = FALSE, ...) {
  function(expr) {
    warnings <- evaluate_promise(expr)$warnings

    if (!is.null(regexp) && length(warnings) > 0) {
      matches(regexp, all = FALSE, ...)(warnings)
    } else {
      expectation(
        length(warnings) > 0,
        "no warnings given",
        paste0(length(warnings), " warnings created")
      )
    }
  }
}
#' @export
#' @rdname gives_warning
#' @inheritParams expect_that
expect_warning <- function(object, regexp = NULL, ..., info = NULL,
                           label = NULL) {
  if (is.null(label)) {
    label <- find_expr("object")
  }
  expect_that(object, gives_warning(regexp, ...), info = info, label = label)
}

#' Expectation: does expression show a message?
#'
#' Needs to match at least one of the messages produced by the expression.
#'
#' @param regexp optional regular expression to match. If not specified, just
#'   asserts that expression shows some message.
#' @param all if \code{TRUE}, all messages must match given regular expression;
#'   if \code{FALSE} (the default), then only only message needs to match
#' @param ... other arguments passed to \code{\link{matches}}
#' @family expectations
#' @export
#' @examples
#' f <- function(x) {
#'   if (x < 0) message("*x* is already negative")
#'   -x
#' }
#' expect_message(f(-1))
#' expect_message(f(-1), "already negative")
#' \dontrun{expect_message(f(1))}
#'
#' # You can use the arguments of grepl to control the matching
#' expect_message(f(-1), "*x*", fixed = TRUE)
#' expect_message(f(-1), "NEGATIVE", ignore.case = TRUE)
shows_message <- function(regexp = NULL, all = FALSE, ...) {
  function(expr) {
    messages <- evaluate_promise(expr)$messages

    if (!is.null(regexp) && length(messages) > 0) {
      matches(regexp, all = all, ...)(messages)
    } else {
      expectation(
        length(messages) > 0,
        "no messages shown",
        paste0(length(messages), " messages shown")
      )
    }
  }
}
#' @export
#' @rdname shows_message
#' @inheritParams expect_that
expect_message <- function(object, regexp = NULL, ..., info = NULL,
                           label = NULL) {
  if (is.null(label)) {
    label <- find_expr("object")
  }
  expect_that(object, shows_message(regexp, ...), info = info, label = label)
}

#' Expectation: does expression take less than a fixed amount of time to run?
#'
#' This is useful for performance regression testing.
#'
#' @family expectations
#' @export
#' @param amount maximum duration in seconds
takes_less_than <- function(amount) {
  function(expr) {
    duration <- system.time(force(expr))["elapsed"]

    expectation(
      duration < amount,
      paste0("took ", duration, " seconds, which is more than ", amount),
      paste0("took ", duration, " seconds, which is less than ", amount)
    )
  }
}

#' Expectation: does object have names?
#'
#' You can either check for the presence of names (leaving \code{expected}
#' blank), specific names (by suppling a vector of names), or absence of
#' names (with \code{NULL}).
#'
#' @param expected Character vector of expected names. Leave missing to
#'   match any names. Use \code{NULL} to check for absence of names.
#' @param ignore.order If \code{TRUE}, sorts names before comparing to
#'   ignore the effect of order.
#' @param ignore.case If \code{TRUE}, lowercases all names to ignore the
#'   effect of case.
#' @param ... Other arguments passed onto \code{has_names}.
#' @family expectations
#' @export
#' @examples
#' x <- c(a = 1, b = 2, c = 3)
#' expect_named(x)
#' expect_named(x, c("a", "b", "c"))
#'
#' # Use options to control sensitivity
#' expect_named(x, c("B", "C", "A"), ignore.order = TRUE, ignore.case = TRUE)
#'
#' # Can also check for the absence of names with NULL
#' z <- 1:4
#' expect_named(z, NULL)
has_names <- function(expected, ignore.order = FALSE, ignore.case = FALSE) {
  if (missing(expected)) {
    function(x) {
      expectation(
        !identical(names(x), NULL),
        paste0("does not have names"),
        paste0("has names")
      )
    }
  } else {
    expected <- normalise_names(expected, ignore.order, ignore.case)

    function(x) {
      x_names <- normalise_names(names(x), ignore.order, ignore.case)

      expectation(
        identical(x_names, expected),
        paste0("names don't match ", paste0(expected, collapse = ", ")),
        paste0("names as expected")
      )
    }
  }
}

#' @rdname has_names
#' @export
#' @inheritParams expect_that
expect_named <- function(object, expected, ..., info = NULL,
                         label = NULL) {
  if (is.null(label)) {
    label <- find_expr("object")
  }
  expect_that(object, has_names(expected, ...), info = info, label = label)
}

normalise_names <- function(x, ignore.order = FALSE, ignore.case = FALSE) {
  if (is.null(x)) return()

  if (ignore.order) x <- sort(x)
  if (ignore.case)  x <- tolower(x)

  x
}

#' Expectation: is returned value less or greater than specified value?
#'
#' This is useful for ensuring returned value is below a ceiling or above
#' a floor.
#'
#' @inheritParams expect_that
#' @param expected Expected value
#' @param label For full form, label of expected object used in error
#'   messages. Useful to override default (deparsed expected expression) when
#'   doing tests in a loop.  For short cut form, object label. When
#'   \code{NULL}, computed from deparsed object.
#' @param expected.label Equivalent of \code{label} for shortcut form.
#' @param ... other values passed to \code{\link{all.equal}}
#' @family expectations
#' @examples
#' a <- 9
#' expect_less_than(a, 10)
#'
#' \dontrun{
#' expect_less_than(11, 10)
#' }
#'
#' a <- 11
#' expect_more_than(a, 10)
#' \dontrun{
#' expect_more_than(9, 10)
#' }
#' @name expect-compare
NULL

#' @rdname expect-compare
#' @export
is_less_than <- function(expected, label = NULL, ...) {
  if (is.null(label)) {
    label <- find_expr("expected")
  } else if (!is.character(label) || length(label) != 1) {
    label <- deparse(label)
  }
  function(actual) {
    diff <- expected - actual

    expectation(
      diff > 0,
      paste0("not less than ", label, ". Difference: ", format(diff)),
      paste0("is less than ", label)
    )
  }
}

#' @export
#' @rdname expect-compare
expect_less_than <- function(object, expected, ..., info = NULL, label = NULL,
                         expected.label = NULL) {
  if (is.null(label)) {
    label <- find_expr("object")
  }
  if (is.null(expected.label)) {
    expected.label <- find_expr("expected")
  }
  expect_that(object, is_less_than(expected, label = expected.label, ...),
              info = info, label = label)
}

#' @rdname expect-compare
#' @export
is_more_than <- function(expected, label = NULL, ...) {
  if (is.null(label)) {
    label <- find_expr("expected")
  } else if (!is.character(label) || length(label) != 1) {
    label <- deparse(label)
  }
  function(actual) {
    diff <- expected - actual

    expectation(
      diff < 0,
      paste0("not more than ", label, ". Difference: ", format(diff)),
      paste0("is more than ", label)
    )
  }
}
#' @export
#' @rdname expect-compare
expect_more_than <- function(object, expected, ..., info = NULL, label = NULL,
                             expected.label = NULL) {
  if (is.null(label)) {
    label <- find_expr("object")
  }
  if (is.null(expected.label)) {
    expected.label <- find_expr("expected")
  }
  expect_that(object, is_more_than(expected, label = expected.label, ...),
              info = info, label = label)
}
