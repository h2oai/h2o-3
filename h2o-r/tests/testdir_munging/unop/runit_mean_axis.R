setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# Test out the h2o.mean() functionality
##


test.mean <- function(){
  #Set up H2OFrame
  fr <- h2o.createFrame(rows = 100, cols = 10, categorical_fraction = 0,
                    factors = 0, integer_fraction = 1.0, integer_range = 100,
                    binary_fraction = 0, time_fraction = 0, string_fraction = 0,
                    has_response = FALSE,seed = 123456789)

  ################################################################
  #Testing h2o.mean and have a frame returned
  mean_return_frame <- h2o.mean(fr,return_frame = TRUE)

  #Testing h2o.mean and have a list returned
  mean_return_list <- h2o.mean(fr)

  #Check types returned are correct
  expect_equal(class(mean_return_frame),"H2OFrame")
  expect_equal(class(mean_return_list),"numeric")

  ################################################################
  #Check axis arguments set to 1 (row mean) and compare to R
  mean_frame_axis <- h2o.mean(fr,na.rm=TRUE,axis=1,return_frame=TRUE)
  fr_df <- as.data.frame(fr)
  mean_list_axis <- rowMeans(fr_df,na.rm=TRUE)

  #Convert previous H2O Frame to data frames for comparison
  mean_frame_axis_df <- as.data.frame(mean_frame_axis)
  mean_list_axis_df <- as.data.frame(mean_list_axis)
  colnames(mean_list_axis_df) <- c("mean")
  cat("Check axis arguments set to 1 (row mean) and compare to R\n")
  print(mean_frame_axis_df)
  print(mean_list_axis_df)
  expect_equal(mean_frame_axis_df,mean_list_axis_df,tol=1e-6)
  ################################################################
  #Check axis arguments set to 0 (col mean) and compare to R
  mean_frame_axis <- h2o.mean(fr,na.rm=TRUE,axis=0,return_frame=TRUE)
  fr_df <- as.data.frame(fr)
  mean_list_axis <- colMeans(fr_df,na.rm=TRUE)

  #Convert previous H2O Frame to data frames for comparison
  mean_frame_axis_df <- as.data.frame(t(mean_frame_axis))
  mean_list_axis_df <- as.data.frame(mean_list_axis)
  colnames(mean_list_axis_df) <- c("mean")
  colnames(mean_frame_axis_df) <- c("mean")
  rownames(mean_frame_axis_df) <- c()
  rownames(mean_list_axis_df) <- c()
  cat("Check axis arguments set to 0 (col mean) and compare to R\n")
  print(mean_frame_axis_df)
  print(mean_list_axis_df)
  expect_equal(mean_frame_axis_df,mean_list_axis_df,tol=1e-6)
  ######################################################################################################################

  #Check previous with na.rm=FALSE and add a couple NA's to columns
  fr[1,1] <- NA
  fr[2,2] <- NA
  ################################################################
  #Check axis arguments set to 1 (row mean) and compare to R
  mean_frame_axis <- h2o.mean(fr,na.rm=FALSE,axis=1,return_frame=TRUE)
  fr_df <- as.data.frame(fr)
  mean_list_axis <- rowMeans(fr_df,na.rm=FALSE)

  #Convert previous H2O Frame to data frames for comparison
  mean_frame_axis_df <- as.data.frame(mean_frame_axis)
  mean_list_axis_df <- as.data.frame(mean_list_axis)
  colnames(mean_list_axis_df) <- c("mean")
  cat("Check axis arguments set to 1 (row mean) & na.rm = FALSE and compare to R\n")
  print(mean_frame_axis_df)
  print(mean_list_axis_df)
  expect_equal(mean_frame_axis_df,mean_list_axis_df,tol=1e-6)
  ################################################################
  #Check axis arguments set to 0 (col mean) and compare to R
  mean_frame_axis <- h2o.mean(fr,na.rm=FALSE,axis=0,return_frame=TRUE)
  fr_df = as.data.frame(fr)
  mean_list_axis <- colMeans(fr_df,na.rm=FALSE)

  #Convert previous H2O Frame to data frames for comparison
  mean_frame_axis_df <- as.data.frame(t(mean_frame_axis))
  mean_list_axis_df <- as.data.frame(mean_list_axis)
  colnames(mean_list_axis_df) <- c("mean")
  colnames(mean_frame_axis_df) <- c("mean")
  rownames(mean_frame_axis_df) <- c()
  rownames(mean_list_axis_df) <- c()
  cat("Check axis arguments set to 0 (col mean) & na.rm = FALSE and compare to R\n")
  print(mean_frame_axis_df)
  print(mean_list_axis_df)
  expect_equal(mean_frame_axis_df,mean_list_axis_df)

  ################################################################
  #Check if axis is ignored when return_frame=FALSE
  mean_list <- h2o.mean(fr,na.rm=FALSE)
  mean_list_ignore_row <- h2o.mean(fr,na.rm=FALSE,axis=1)
  mean_list_ignore_col <- h2o.mean(fr,na.rm=FALSE,axis=0)
  cat("Check if axis is ignored when return_frame=FALSE\n")
  print(mean_list_ignore_row)
  print(mean_list_ignore_col)
  expect_equal(mean_list,mean_list_ignore_row)
  expect_equal(mean_list,mean_list_ignore_col)
  expect_equal(mean_list_ignore_col,mean_list_ignore_row)
}

doTest("Test out the h2o.mean() functionality", test.mean)