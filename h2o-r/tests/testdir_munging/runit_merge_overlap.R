setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
library(testthat)

# TODO:  vary the overlap in many different ways.  This is just a small range completely within a large

test.merge = function() {
  X = h2o.createFrame(rows=1e3, cols=2, integer_range=1e4, integer_fraction=1,
                      missing_fraction=0, categorical_fraction=0, binary_fraction=0, seed=1234)
  Y = h2o.createFrame(rows=1e7, cols=2, integer_range=1e9, integer_fraction=1, 
                      missing_fraction=0, categorical_fraction=0, binary_fraction=0, seed=1234)
  colnames(X) = c("KEY","X2")
  colnames(Y) = c("KEY","Y2")
  ans1 = h2o.merge(X, Y, method="radix")
  ans2 = h2o.merge(Y, X, method="radix")
  expect_identical(as.vector(ans1$KEY), as.integer(c(-7501,-7501,-1749,376,5963,7765,9500)))
  expect_identical(as.data.frame(ans1), as.data.frame(ans2[,colnames(ans1)]))
  ans3 = h2o.merge(X, Y, method="hash")
  expect_true(nrow(ans1)==7 && nrow(ans3)==6)
  expect_identical(sort(as.vector(ans3$KEY)), unique(as.vector(ans1$KEY)))
  # TODO PUBDEV-3044: hash method doesn't handle this dup. When fixed the test above can have the unique() removed.
  ans4 = h2o.merge(Y, X, method="hash")
  expect_identical(as.data.frame(ans3), as.data.frame(ans4))  # the columns are the same order regardless, when method='hash'
  
  X$KEY = abs(X$KEY)
  Y$KEY = abs(Y$KEY)
  ans1 <- h2o.merge(X, Y, method="radix")
  ans2 <- h2o.merge(Y, X, method="radix")
  expect_identical(as.vector(ans1$KEY), as.integer(c(376,1749,1749,2457,2720,5963,6979,7501,7501,7765,9500)))
  expect_identical(as.data.frame(ans1), as.data.frame(ans2[,colnames(ans1)]))
  ans3 <- h2o.merge(X, Y, method="hash")
  expect_true(nrow(ans1)==11 && nrow(ans3)==9)  # TODO: two dups not handled by method='hash', see comment above
  expect_identical(sort(as.vector(ans3$KEY)), unique(as.vector(ans1$KEY)))
  ans4 = h2o.merge(Y, X, method="hash")
  expect_identical(as.data.frame(ans3), as.data.frame(ans4))
}

doTest("Test merging overlapping ranges", test.merge)


