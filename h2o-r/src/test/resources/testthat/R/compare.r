compare <- function(x, y, ...) {
  UseMethod("compare", x)
}

#' @export
compare.default <- function(x, y, ...){
  same <- all.equal(x, y, ...)
  list(
    equal = identical(same, TRUE),
    message = paste0(same, collapse = "\n")
  )
}

#' @export
# x <- c("abc", "def", "jih")
# y <- paste0(x, "y")
# compare(x, y)
#
# x <- "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Duis cursus
#  tincidunt auctor. Vestibulum ac metus bibendum, facilisis nisi non, pulvinar
#  dolor. Donec pretium iaculis nulla, ut interdum sapien ultricies a. "
# y <- "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Duis cursus
#  tincidunt auctor. Vestibulum ac metus1 bibendum, facilisis nisi non, pulvinar
#  dolor. Donec pretium iaculis nulla, ut interdum sapien ultricies a. "
# compare(x, y)
# expect_equal(x, y)
compare.character <- function(x, y, ..., max_strings = 5, max_lines = 5,
                              width = getOption("width")) {
  if (identical(x, y)) return(list(equal = TRUE))

  # If they're not the same length, fallback to default method
  if (length(x) != length(y)) return(NextMethod())

  # If vectorwise-equal, fallback to default method
  diff <- xor(is.na(x), is.na(y)) | x != y
  diff[is.na(diff)] <- FALSE

  if (!any(diff)) {
    return(NextMethod())
  }

  width <- width - 6 # allocate space for labels
  n_show <- seq_len(min(length(diff), max_strings))
  show <- diff[n_show]

  encode <- function(x) encodeString(x, quote = '"')
  show_x <- str_chunk(str_trunc(encode(x[show]), max_lines * width), width)
  show_y <- str_chunk(str_trunc(encode(y[show]), max_lines * width), width)

  names <- which(diff)[n_show]

  sidebyside <- Map(function(x, y, name) {
    x <- paste0("x[", name, "]: ", x)
    y <- paste0("y[", name, "]: ", y)

    n <- max(length(x), length(y))
    length(x) <- n
    length(y) <- n

    paste0(as.vector(rbind(x, "\n", y, "\n\n")), collapse = "")
  }, show_x, show_y, names)

  msg <- paste0(sum(diff), " string mismatches:\n",
    paste0(sidebyside, collapse = "\n\n"))
  list(equal = FALSE, message = msg)
}

str_trunc <- function(x, length) {
  too_long <- nchar(x) > length

  x[too_long] <- paste0(substr(x[too_long], 1, length - 3), "...")
  x
}

str_chunk <- function(x, length) {
  lapply(x, str_chunk_1, length)
}
str_chunk_1 <- function(x, length) {
  lines <- ceiling(nchar(x) / length)
  start <- (seq_len(lines) - 1) * length + 1

  substring(x, start, start + length - 1)
}
