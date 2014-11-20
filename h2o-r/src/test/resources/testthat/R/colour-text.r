
testthat_colours <- list(
  passed = crayon::green,
  skipped = crayon::yellow,
  error = crayon::red
)

colourise <- function(text, as = c("passed", "skipped", "error")) {
  colour_config <- getOption("testthat.use_colours", TRUE)
  if (!isTRUE(colour_config)) return(text)
  as <- match.arg(as)
  testthat_colours[[as]](text)
}
