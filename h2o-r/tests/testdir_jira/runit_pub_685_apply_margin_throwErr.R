#'
#' PUB-685: Throw error when MARGIN = c(1,2)
#'
#'
#' Ensure that an error about the margin is thrown when passing in c(1,2) for the MARGIN in apply
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../findNSourceUtils.R')

test.pub_685_margin.err.throw <- function(H2Oserver) {
  hex <- as.h2o(H2Oserver, iris[,1:4])

  expect_error(apply(hex, c(1,2), sum))

  tryCatch(apply(hex, c(1,2), sum), error = function(e) {
    msg <- e$message
    msgs <- strsplit(msg, '\n')
    print(e)
    print(msgs[[1]][2])
    expect_true( strsplit(e$message, '\n')[[1]][2] == "   MARGIN limited to 1 (rows) or 2 (cols)")
  })
  testEnd()
}

doTest("Ensure that an error about the margin is thrown when passing in c(1,2) for the MARGIN in apply",test.pub_685_margin.err.throw )

