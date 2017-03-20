setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.pivot <- function(){
    print("step1")
    df = h2o.createFrame(rows = 1000,
                         cols=3,
                         factors=10,
                         integer_fraction=1.0/3,
                         categorical_fraction=1.0/3,
                         missing_fraction=0.0,
                         seed=123)
    df$C3 = h2o.abs(df$C3)
    df2 = h2o.pivot(df,index="C3",column="C2",value="C1")
    expect_equal(nrow(df2),101)
    expect_equal(ncol(df2),11)
}

doTest("Test pivot", test.pivot)
