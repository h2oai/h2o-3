setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# Test out the h2o.mean() functionality
##


test.mean <- function(){
  #Set up H2OFrame
  fr = h2o.createFrame(rows = 100, cols = 10, categorical_fraction = 0,
                    factors = 0, integer_fraction = 1.0, integer_range = 100,
                    binary_fraction = 0, time_fraction = 0, string_fraction = 0,
                    has_response = FALSE, missing_fraction = 0.10)

  ################################################################
  #Testing h2o.mean and have a frame returned
  mean_return_frame = h2o.mean(fr,return_frame = TRUE)

  #Testing h2o.mean and have a list returned
  mean_return_list = h2o.mean(fr)

  #Check types returned are correct
  expect_equal(class(mean_return_frame),"H2OFrame")
  expect_equal(class(mean_return_list),"numeric")

  ################################################################
  #Check axis arguments set to 1 (row mean) and compare to R
  mean_frame_axis = h2o.mean(fr,na.rm=TRUE,axis=1,return_frame=TRUE)
  mean_list_axis = mean(t(fr),na.rm=TRUE)

  #Convert previous to data frames for comparison
  mean_frame_axis_df = as.data.frame(mean_frame_axis)
  mean_list_axis_df = as.data.frame(mean_list_axis)
  colnames(mean_list_axis_df) = c("mean")

  expect_equal(mean_frame_axis_df,mean_list_axis_df,tol=1e-6)

  ################################################################
  #Check axis arguments set to 0 (col mean) and compare to R
  mean_frame = h2o.mean(fr,na.rm=TRUE,axis=0,return_frame=TRUE)
  mean_list = mean(fr,na.rm=TRUE)

  #Convert previous to data frames for comparison
  mean_frame_df = as.data.frame(mean_frame_axis)
  mean_list_df = as.data.frame(mean_list_axis)
  colnames(mean_list_df) = c("mean")

  expect_equal(mean_frame_df,mean_list_df,tol=1e-6)

  ######################################################################################################################

  #Check previous with na.rm=FALSE
  ################################################################
  #Check axis arguments set to 1 (row mean) and compare to R
  mean_frame_axis = h2o.mean(fr,na.rm=FALSE,axis=1,return_frame=TRUE)
  mean_list_axis = mean(t(fr),na.rm=FALSE)

  #Convert previous to data frames for comparison
  mean_frame_axis_df = as.data.frame(mean_frame_axis)
  mean_list_axis_df = as.data.frame(mean_list_axis)
  colnames(mean_list_axis_df) = c("mean")

  expect_equal(mean_frame_axis_df,mean_list_axis_df,tol=1e-6)

  ################################################################
  #Check axis arguments set to 0 (col mean) and compare to R
  mean_frame = h2o.mean(fr,na.rm=FALSE,axis=0,return_frame=TRUE)
  mean_list = mean(fr,na.rm=FALSE)

  #Convert previous to data frames for comparison
  mean_frame_df = as.data.frame(mean_frame_axis)
  mean_list_df = as.data.frame(mean_list_axis)
  colnames(mean_list_df) = c("mean")

  expect_equal(mean_frame_df,mean_list_df,tol=1e-6)

}

doTest("Test out the h2o.mean() functionality", test.mean)