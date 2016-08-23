setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
library(testthat)

# Since the radix merge takes advantage of range and overlap of the left and right ordered index,
# we need to test lots of overlap permutations. We wouldn't need to do this with relatively simpler
# but slower hash approach: less code and branches independent of range.
# Essentially, here we aim to test :
#         -----     
#     ++       
#       ++++        
#         +++++
#          +++
#            ++++
#                ++ 
# where - is right range and + are various left ranges and vice versa. Also narrow to wide ranges.
# We want some keys to match where they should and some not where they shouldn't, otherwise
# if the result is an empty table correctly, that isn't testing much.
# The relevant range for code path testing is after the minimum is subtracted. So the approach
# taken is to first generate a distribution, then sample subsets of ranges from that to generate
# the various tables.

test.many = function() {
  ranges = 10^c(2,3,12)   # test small and large ranges to test high bits and scalings
  low = c(0.0, 0.25, 0.20, 0.25, 0.30, 0.48, 0.9)
  upp = c(0.1, 0.50, 0.70, 0.75, 0.80, 0.52, 1.0)
  N = 1e6
  M = 1e4
  L = list()
  i = 1
  set.seed(1)
  for (r in ranges) {
    x = sort(sample(r, min(r,N), replace=FALSE))
    for (j in seq_along(low)) {
      ss = seq(1+length(x)*low[j], length(x)*upp[j])
      m = min(length(ss),M)
      L[[i]] = as.h2o(data.frame(
        KEY = sample(x[ss], m, replace=FALSE),
        V = sample(1e7, m)
      ))
      i = i+1
    }
  }
  cat("Created",length(L),"random tables\n")
  if (any(sapply(L, nrow)==0)) stop("Some random tables were empty")
  nonZeroCount = 0
  numRows = 0
  for (i in seq_along(L)) {
  for (j in seq_along(L)[-i]) { 
    cat("Checking i=",i, " j=",j, "...", sep="")
    ans1 = h2o.merge(L[[i]], L[[j]], by="KEY", method="radix")
    ans2 = merge(as.data.frame(L[[i]]), as.data.frame(L[[j]]), by="KEY", sort=TRUE)
    names(ans1) = names(ans2)   # TODO name suffix parity
    if (nrow(ans1) == 0 && nrow(ans2) == 0) {
      cat("\n")
      next
    } else {
      expect_identical(as.data.frame(ans1), ans2)
      nonZeroCount = nonZeroCount + 1
      numRows = numRows + nrow(ans1)
      cat(nonZeroCount, numRows, "\n")
    }
    
    # Check that sorting the inputs first also works (PUBDEV-3236).
    # Already sorted inputs are edge case that could break distributing and batching.
    # Test here just left sorted, just right and both.
    # In all cases should return the same result above.
    x_sorted = h2o.arrange(L[[i]], KEY)
    y_sorted = h2o.arrange(L[[j]], KEY)
    ans3 = h2o.merge(x_sorted, L[[j]], by="KEY", method="radix")
    names(ans3) = names(ans2)
    expect_identical(as.data.frame(ans3), ans2)
    
    ans4 = h2o.merge(L[[i]], y_sorted, by="KEY", method="radix")
    names(ans4) = names(ans2)
    expect_identical(as.data.frame(ans4), ans2)
    
    ans5 = h2o.merge(x_sorted, y_sorted, by="KEY", method="radix")
    names(ans5) = names(ans2)
    expect_identical(as.data.frame(ans5), ans2)    
  }}
  # Check that all tables didn't just return nothing all of a sudden for some reason.
  # These count checks depend on set.seed(1) above.
  expect_equal(nonZeroCount, 74)  # the number of joins tested that returned some matches
  expect_equal(numRows, 8758)     # the total number of rows returned by all those joins
}


test.fixed = function() {
  # This previous test kept for the rule of 'never remove tests'

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

doTest("Test merging overlapping ranges", test.many)
doTest("Test merging fixed overlap", test.fixed)


