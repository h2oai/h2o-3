#'
#' PUB-685: Throw error when MARGIN = c(1,2)
#'
#'
#' Ensure that an error about the margin is thrown when passing in c(1,2) for the MARGIN in apply



test.pub_685_margin.err.throw <- function() {
  hex <- as.h2o(iris[,1:4])

  expect_error(print(apply(hex, c(1,2), sum)))

  tryCatch(print(apply(hex, c(1,2), sum)), error = function(e) {
    msg <- e$message
    msgs <- strsplit(msg, '\n')
    print(e)
    print(msgs[[1]][2])
  })
  
}

doTest("Ensure that an error about the margin is thrown when passing in c(1,2) for the MARGIN in apply",test.pub_685_margin.err.throw )

