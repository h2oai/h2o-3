setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


test.hist <- function() {
  df <- as.h2o(iris)

  h <- h2o.hist(df$Petal.Length, breaks="Sturges")
  expect_true(length(h$breaks) == 9)

  h <- h2o.hist(df$Petal.Length, breaks=5)
  expect_true(length(h$breaks) == 5)

  h <- h2o.hist(df$Petal.Length, breaks=3)
  expect_true(length(h$breaks) == 3)

  h <- h2o.hist(df$Petal.Length, breaks=c(0.5,2,4,5))
  expect_true(length(h$breaks) == 4)
}

doTest("Test hist", test.hist)
