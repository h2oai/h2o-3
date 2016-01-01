setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
################################################################################
##
## Verifying that R can define features as categorical or continuous
##
################################################################################



test.continuous.or.categorical <- function() {
  aa <- data.frame(
    h1 = c( 1, 8, 4, 3, 6),
    h2 = c('fish', 'cat', 'fish', 'dog', 'bird'),
    h3 = c( 0, 1, 0, 0, 1)
    )

  df.hex <- as.h2o(aa)

  print(df.hex)
  print(summary(df.hex))

  expect_false(is.factor(df.hex$h1))
  expect_true(is.factor(df.hex$h2))
  expect_false(is.factor(df.hex$h3))

  h2oTest.logInfo("Converting to categorical")
  df.hex$h1 <- as.factor(df.hex$h1)
  df.hex$h2 <- as.factor(df.hex$h2)
  df.hex$h3 <- as.factor(df.hex$h3)

  print(df.hex)
  print(summary(df.hex))

  expect_true(is.factor(df.hex$h1))
  expect_true(is.factor(df.hex$h2))
  expect_true(is.factor(df.hex$h3))

  h2oTest.logInfo("Converting to continuous")
  df.hex$h1 <- as.numeric(df.hex$h1)
  df.hex$h2 <- as.numeric(df.hex$h2)
  df.hex$h3 <- as.numeric(df.hex$h3)

  expect_false(is.factor(df.hex$h1))
  expect_false(is.factor(df.hex$h2))
  expect_false(is.factor(df.hex$h3))

  print(df.hex)
  print(summary(df.hex))

  
}

h2oTest.doTest("Testing Conversions to Categorical and Continuous Values", test.continuous.or.categorical)
