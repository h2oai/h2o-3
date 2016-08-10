setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
library(testthat)

# Currently tests h2o.arrange() but will also eventually test HF[order(...)]
# and setkey(HF,...).
# See http://stackoverflow.com/questions/1296646/how-to-sort-a-dataframe-by-columns

test.sort = function() {
  set.seed(1)
  X = as.h2o(Xdf <- data.frame( A=sample(10,100,replace=TRUE),
                         B=sample(10,100,replace=TRUE),
                         C=sample(10,100,replace=TRUE),
                         V=runif(100) ))
  
  checkEqual = function(df1, df2) {
    df1 = as.data.frame(df1)
    df2 = as.data.frame(df2)
    row.names(df1) = NULL
    row.names(df2) = NULL
    expect_equal(df1, df2)
  }
  
  by = c("B", "B,C", "C,A", "C,A,B", "A,B,C")
  check = rep(NA_real_,length(by))
  for (i in seq_along(by)) {
    b = by[i]
    cat("Testing sort by", b, "... ")
    e1 = paste0("h2o.arrange(X,", b, ")")
    e2 = paste0("Xdf[with(Xdf,order(",b,")),]")
    
    # compare to base R which is also stable sort (i.e. preserves original order within ties)
    checkEqual(eval(parse(text=e1)), ans<-eval(parse(text=e2)))
    
    # store row 5 to check afterwards we really tested something
    # different for each variant (row 5 changes value for these)
    check[i] = ans$V[5]
    cat("ok\n")
  }
  
  # Check that we really sorted and got a different answer each time 
  expect_true(!anyDuplicated(check) && !anyNA(check))
}

doTest("Test sort H2OFrame", test.sort)

