setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.fillna <- function(){
    df = h2o.createFrame(categorical_fraction=0.0,missing_fraction=0.7,rows=6,cols=2,seed=123)
    df2 = h2o.fillna(df,method="forward",axis=2,maxlen=1L)
    expect_equal(h2o.nacnt(df2),c(1,2))
}

doTest("Test fillna", test.fillna)
