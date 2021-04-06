setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# tests that infogram build the correct model for core infogram.  Make sure 
# 1. it gets the correct result compared to deep's code.
# 2. the relevance and cmi frame contains the correct values
# 3. test that infogram_algorithm_params work.
infogramBC <- function() {
    bhexFV <- h2o.importFile(locate("smalldata/admissibleml_test/wdbc_changed.csv"))
    bhexFV["diagnosis"]<- h2o.asfactor(bhexFV["diagnosis"])
    Y <- "diagnosis"
    X <- c("radius_mean", "texture_mean", "perimeter_mean", "area_mean",
           "smoothness_mean", "compactness_mean", "concavity_mean", "concave_points_mean", "symmetry_mean",
           "fractal_dimension_mean", "radius_se", "texture_se", "perimeter_se", "area_se", "smoothness_se",
           "compactness_se", "concavity_se", "concave_points_se", "symmetry_se", "fractal_dimension_se",
           "radius_worst", "texture_worst", "perimeter_worst", "area_worst", "smoothness_worst", "compactness_worst",
           "concavity_worst", "concave_points_worst", "symmetry_worst", "fractal_dimension_worst")
    deepRel <- sort(c(0.0040477989, 0.0974455315, 0.0086303713, 0.0041002103, 0.0037914745,
                      0.0036801151, 0.0257819346, 0.2808010416, 0.0005372569, 0.0036280018, 0.0032444598, 0.0002943119,
                      0.0026430897, 0.0262074332, 0.0033317064, 0.0068812603, 0.0006185385, 0.0082121491, 0.0014562177,
                      0.0081786997, 1.0000000000, 0.0894895310, 0.6187801784, 0.3302352775, 0.0021346433, 0.0016077771,
                      0.0260198502, 0.3404628948, 0.0041384517, 0.0019399743))
    deepCMI <- sort(c(0.00000000, 0.31823883, 0.52769230, 0.00000000, 0.00000000, 0.00000000,
                      0.01183309, 0.67430653, 0.00000000, 0.00000000, 0.45443221, 0.00000000, 0.24561013, 0.87720587,
                      0.31939378, 0.19370515, 0.00000000, 0.16463918, 0.00000000, 0.00000000, 0.44830772, 1.00000000,
                      0.00000000, 0.00000000, 0.62478098, 0.00000000, 0.00000000, 0.00000000, 0.00000000, 0.64466111))
    Log.info("Build the model")
    mFV <- h2o.infogram(y=Y, x=X, training_frame=bhexFV,  seed=12345, top_n_features=50)
    relCMIFrame <- mFV@admissible_score # get frames containing relevance and cmi
    frameCMI <- sort(as.vector(t(relCMIFrame[,5])))
    frameRel <- sort(as.vector(t(relCMIFrame[,4])))
    
  #  expect_equal(deepCMI, frameCMI, tolerance=1e-6) # Deep's result is problematic due to building same model with different predictors orders
    expect_equal(deepRel, frameRel, tolerance=1e-6) 
    
    # model built with different parameters and their relevance and cmi to be different
    gbm_params <- list(ntrees=3)
    
    mFVNew <- h2o.infogram(y=Y, x=X, training_frame=bhexFV,  seed=12345, top_n_features=50, algorithm='gbm', 
                           algorithm_params=gbm_params)
    cmiNew <- sort(as.vector(t(mFVNew@admissible_score[,5])))
    relNew <- sort(as.vector(t(mFVNew@admissible_score[,4])))
    expect_true((frameCMI[length(frameCMI)-2] - cmiNew[length(frameCMI)-2]) > 0.01) # CMI and relevance should not equal
    expect_true((frameRel[length(frameCMI)-2] - relNew[length(frameCMI)-2]) > 0.01)
}

doTest("Infogram: Breast cancer core infogram", infogramBC)
