setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
library(testthat)

# tests that by.x and by.y gets passed through rapids correctly (e.g. 0-base)
# and that returned columns have parity with base R order of columns.

test.by = function() {
  set.seed(1)
  X = as.h2o(Xdf <- data.frame( A=sample(10,100,replace=TRUE),
                         B=sample(10,100,replace=TRUE),
                         C=sample(10,100,replace=TRUE),
                         V=runif(10) ))
  
  Y = as.h2o(Ydf <- data.frame( A=sample(10,100,replace=TRUE),
                         B=sample(10,100,replace=TRUE),
                         C=sample(10,100,replace=TRUE),
                         V=runif(10) ))
  
  nrows = integer()
  checkEqual = function(df1, df2) {
    df1 = df1[ do.call("order", df1), ]
    df2 = df2[ do.call("order", df2), ]
    names(df1) = names(df2)   # TODO name suffix parity
    row.names(df1) = NULL
    row.names(df2) = NULL
    expect_equal(df1, df2)
    expect_true(nrow(df1) > 0)
    nrows <<- c(nrows, nrow(df1))
  }
  
  by = list( c("B"), c("B","C"), c("C","A"), c("C","A","B"), c("A","B","C") )
  for (b in by) {
    ans1 = as.data.frame(h2o.merge(X, Y, by=b, method="radix"))
    ans2 = merge(Xdf, Ydf, by=b, sort=TRUE)
    checkEqual(ans1, ans2)
    cat(nrow(ans1),"\n")
  }
  
  # now test joining differently named columns
  ans1 = as.data.frame(h2o.merge(X, Y, by.x="B", by.y="C", method="radix"))
  ans2 = suppressWarnings(merge(Xdf, Ydf, by.x="B", by.y="C", sort=TRUE)) # warning re duplicated result name
  checkEqual(ans1, ans2)
  
  ans1 = as.data.frame(h2o.merge(X, Y, by.x=c("C","A"), by.y=c("B","C"), method="radix"))
  ans2 = suppressWarnings(merge(Xdf, Ydf, by.x=c("C","A"), by.y=c("B","C"), sort=TRUE)) # warning re duplicated result name
  checkEqual(ans1, ans2)
  
  ans1 = as.data.frame(h2o.merge(X, Y, by.x=c(1,3), by.y=c(2,1), method="radix"))
  ans2 = suppressWarnings(merge(Xdf, Ydf, by.x=c(1,3), by.y=c(2,1), sort=TRUE)) # warning re duplicated result name
  checkEqual(ans1, ans2)
  
  # Test the joins produced expected non-zero and varying result sets, not just that
  # h2o matched base R (which could be an agreement on empty for some reason)
  expect_equal(nrows, c(984, 106, 86, 5, 5, 981, 96, 108))
  
}

doTest("Test merging by", test.by)

