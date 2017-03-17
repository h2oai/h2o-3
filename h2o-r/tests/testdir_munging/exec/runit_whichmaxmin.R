setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.whichmaxmin <- function(){
    #Create H2O Frame
    hf <- h2o.createFrame(rows = 10000, cols = 100, categorical_fraction = 0, missing_fraction = 0,seed=1234)

    #Cast as R Data Frame for comparison
    df <- as.data.frame(hf)

    #Col wise max
    which_max_col <- h2o.which_max(hf,axis=0)
    which_max_col_app <- apply(hf,2,which.max)

    #Apply and original which_max should match
    expect_equal(sum(which_max_col_app == which_max_col),100)
    expect_equal(which_max_col_app,which_max_col)

    #H2O which_max should match R
    which_max_col_app_r <- apply(df,2,which.max)

    expect_equal(which_max_col, as.h2o(which_max_col_app_r))
    expect_equal(which_max_col_app, as.h2o(which_max_col_app_r))

    #Col wise min
    which_min_col <- h2o.which_min(hf,axis=0)
    which_min_col_app <- apply(hf,2,which.min)

    #Apply and original which_max should match
    expect_equal(sum(which_min_col_app == which_min_col),100)
    expect_equal(which_min_col_app,which_min_col)

    #H2O which_max should match R
    which_min_col_app_r <- apply(df,2,which.min)

    expect_equal(which_min_col, as.h2o(which_min_col_app_r))
    expect_equal(which_min_col_app, as.h2o(which_min_col_app_r))

    #############################################################
    #Row wise max
    which_max_row <- h2o.which_max(hf,axis=1)
    which_max_row_app <- apply(hf,1,which.max)

    #Apply and original which_max should match
    expect_equal(sum(which_max_row_app == which_max_row),10000)
    expect_equal(which_max_row_app,which_max_col)

    #H2O which_max should match R
    which_max_row_app_r <- apply(df,1,which.max)

    expect_equal(which_max_row, as.h2o(which_max_row_app_r))
    expect_equal(which_max_row_app, as.h2o(which_max_row_app_r))

    #Row wise min
    which_min_row <- h2o.which_min(hf,axis=1)
    which_min_row_app <- apply(hf,1,which.min)

    #Apply and original which_max should match
    expect_equal(sum(which_min_row_app == which_min_row),10000)
    expect_equal(which_min_row_app,which_min_row)

    #H2O which_max should match R
    which_min_row_app_r <- apply(df,1,which.min)

    expect_equal(which_min_row, as.h2o(which_min_row_app_r))
    expect_equal(which_min_row_app, as.h2o(which_min_row_app_r))

}

doTest("Check which_max/min ", test.whichmaxmin)