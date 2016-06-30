setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.na.omit = function(){
    a = c(2, 3, 5)
    b = c(NA, 2, 3)
    c = c(2, 1, 2)
    df = data.frame(a,b,c)

    df.hex = as.h2o(df)
    cat("Frame before na.omit():\n")
    print(df.hex)

    cat("Frame after na.omit():\n")
    df2.hex = na.omit(df.hex)
    print(df2.hex)

    print(paste0("Number of rows after na.omit(): ",nrow(df2.hex)))
    expect_true(nrow(df2.hex) == 2)

    print(paste0("Number of NA's after na.omit(): ",sum(is.na((df2.hex)))))
    expect_true(sum(is.na(df2.hex)) == 0)
}

doTest("Test na.omit on frame", test.na.omit)