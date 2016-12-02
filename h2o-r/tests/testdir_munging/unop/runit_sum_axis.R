setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# Test out the h2o.sum() functionality
##


test.sum <- function(){
    #Set up H2OFrame
    fr <- h2o.createFrame(rows = 100, cols = 10, categorical_fraction = 0,
    factors = 0, integer_fraction = 1.0, integer_range = 100,
    binary_fraction = 0, time_fraction = 0, string_fraction = 0,
    has_response = FALSE,seed = 123456789)

    ################################################################
    #Testing h2o.sum and have a frame returned
    sum_return_frame <- h2o.sum(fr,return_frame = TRUE)

    #Testing h2o.sum and have a list returned
    sum_return_list <- h2o.sum(fr)

    #Check types returned are correct
    expect_equal(class(sum_return_frame),"H2OFrame")
    expect_equal(class(sum_return_list),"numeric")

    ################################################################
    #Check axis arguments set to 1 (row sum) and compare to R
    sum_frame_axis <- h2o.sum(fr,na.rm=TRUE,axis=1,return_frame=TRUE)
    fr_df <- as.data.frame(fr)
    sum_list_axis <- rowSums(fr_df,na.rm=TRUE)

    #Convert previous H2O Frame to data frames for comparison
    sum_frame_axis_df <- as.data.frame(sum_frame_axis)
    sum_list_axis_df <- as.data.frame(sum_list_axis)
    colnames(sum_list_axis_df) <- c("sum")
    cat("Check axis arguments set to 1 (row sum) and compare to R\n")
    print(sum_frame_axis_df)
    print(sum_list_axis_df)
    expect_equal(sum_frame_axis_df,sum_list_axis_df,tol=1e-6)
    ################################################################
    #Check axis arguments set to 0 (col sum) and compare to R
    sum_frame_axis <- h2o.sum(fr,na.rm=TRUE,axis=0,return_frame=TRUE)
    fr_df <- as.data.frame(fr)
    sum_list_axis <- colSums(fr_df,na.rm=TRUE)

    #Convert previous H2O Frame to data frames for comparison
    sum_frame_axis_df <- as.data.frame(t(sum_frame_axis))
    sum_list_axis_df <- as.data.frame(sum_list_axis)
    colnames(sum_list_axis_df) <- c("sum")
    colnames(sum_frame_axis_df) <- c("sum")
    rownames(sum_frame_axis_df) <- c()
    rownames(sum_list_axis_df) <- c()
    cat("Check axis arguments set to 0 (col sum) and compare to R\n")
    print(sum_frame_axis_df)
    print(sum_list_axis_df)
    expect_equal(sum_frame_axis_df,sum_list_axis_df,tol=1e-6)
    ######################################################################################################################

    #Check previous with na.rm=FALSE and add a couple NA's to columns
    fr[1,1] <- NA
    fr[2,2] <- NA
    ################################################################
    #Check axis arguments set to 1 (row sum) and compare to R
    sum_frame_axis <- h2o.sum(fr,na.rm=FALSE,axis=1,return_frame=TRUE)
    fr_df <- as.data.frame(fr)
    sum_list_axis <- rowSums(fr_df,na.rm=FALSE)

    #Convert previous H2O Frame to data frames for comparison
    sum_frame_axis_df <- as.data.frame(sum_frame_axis)
    sum_list_axis_df <- as.data.frame(sum_list_axis)
    colnames(sum_list_axis_df) <- c("sum")
    cat("Check axis arguments set to 1 (row sum) & na.rm = FALSE and compare to R\n")
    print(sum_frame_axis_df)
    print(sum_list_axis_df)
    expect_equal(sum_frame_axis_df,sum_list_axis_df,tol=1e-6)
    ################################################################
    #Check axis arguments set to 0 (col sum) and compare to R
    sum_frame_axis <- h2o.sum(fr,na.rm=FALSE,axis=0,return_frame=TRUE)
    fr_df = as.data.frame(fr)
    sum_list_axis <- colSums(fr_df,na.rm=FALSE)

    #Convert previous H2O Frame to data frames for comparison
    sum_frame_axis_df <- as.data.frame(t(sum_frame_axis))
    sum_list_axis_df <- as.data.frame(sum_list_axis)
    colnames(sum_list_axis_df) <- c("sum")
    colnames(sum_frame_axis_df) <- c("sum")
    rownames(sum_frame_axis_df) <- c()
    rownames(sum_list_axis_df) <- c()
    cat("Check axis arguments set to 0 (col sum) & na.rm = FALSE and compare to R\n")
    print(sum_frame_axis_df)
    print(sum_list_axis_df)
    expect_equal(sum_frame_axis_df,sum_list_axis_df)

    ################################################################
    #Check if axis is ignored when return_frame=FALSE
    sum_list <- h2o.sum(fr,na.rm=FALSE)
    sum_list_ignore_row <- h2o.sum(fr,na.rm=FALSE,axis=1)
    sum_list_ignore_col <- h2o.sum(fr,na.rm=FALSE,axis=0)
    cat("Check if axis is ignored when return_frame=FALSE\n")
    print(sum_list_ignore_row)
    print(sum_list_ignore_col)
    expect_equal(sum_list,sum_list_ignore_row)
    expect_equal(sum_list,sum_list_ignore_col)
    expect_equal(sum_list_ignore_col,sum_list_ignore_row)
}

doTest("Test out the h2o.sum() functionality", test.sum)