setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

# PUBDEV-5761 adding missing values and weights to partial plots
# In this test, I will build a GBM regression model and 
# 1. generate partialplots without weight column and without NAs
# 2. generate partialplots with constant weight column and with NAs
# 3. generate partialplots with varying weight and with NAs.
# 
# partial plots from 1 and 2 should agree except at the NA values
# partial plots from 3 will be compared to correct results generated from python.

testPartialPlots <- function() {
  # correct weighted stats
  wMeanR = c(6.284782127552298, 6.379657412536037, 6.350187212051545)
  wStdR = c(0.6598691787704484, 0.6102004461984243, 0.6295779588841749)
  wStdErrR = c(0.03385057463133443, 0.03130262241161426, 0.032296667838256035)
  
  wMeanA = c(6.318083655321063, 6.318083655321063, 6.318083655321063, 6.318083655321063, 6.318083655321063, 
             6.318083655321063, 6.318083655321063, 6.393722888541923, 6.370576180579088, 6.338771267389174, 
             6.296612317836929, 6.237557057296288, 6.391660063003342, 6.381704885610073, 6.37289303225485, 
             6.4426985498720954, 6.446254406033446, 6.454700662770381, 6.463561821396387, 6.463561821396387, 
             6.484410511202)
  wStdErrA = c(0.03561658248609287, 0.03561658248609287, 0.03561658248609287, 0.03561658248609287, 0.03561658248609287, 
            0.03561658248609287, 0.03561658248609287, 0.03390476614201277, 0.033923132835289325, 0.032434165191685335, 
            0.030514530790556476, 0.03316910473302995, 0.029939750752982972, 0.028667039531838457, 0.02943257198534912, 
            0.026939001615026055, 0.027137118164253776, 0.026853123580389788, 0.026705534803575043, 0.026705534803575043,
            0.030458109513491528)
  wStdA = c(0.6942950095137439, 0.6942950095137439, 0.6942950095137439, 0.6942950095137439, 0.6942950095137439,
            0.6942950095137439, 0.6942950095137439, 0.660925565790081, 0.6612835985544024, 0.6322582757378367,
            0.5948377122877897, 0.646584884868545, 0.5836331866483286, 0.5588234775826761, 0.573746452359967,
            0.5251378171922709, 0.5289998197155226, 0.5234637461075988, 0.5205867111971684, 0.5205867111971684,
            0.5937378591193426)
  ## Import prostate dataset
  prostate_hex = h2o.uploadFile(locate("smalldata/prostate/prostate_NA_weights.csv")) # constant weight C0, vary C10

  ## Change CAPSULE to Enum
  prostate_hex[, "CAPSULE"] = as.factor(prostate_hex[, "CAPSULE"]) # should be enum by default
  ## build GBM model
  prostate_gbm = h2o.gbm(x = c('CAPSULE', 'AGE', 'RACE', 'DPROS', 'DCAPS', 'PSA', 'VOL'), y = "GLEASON", 
                         training_frame = prostate_hex, ntrees = 50, learn_rate=0.05, seed = 12345)
  
  ## Calculate partial dependence using h2o.partialPlot for columns "AGE" and "RACE"
  # build pdp without weight or NA
  h2o_pp = h2o.partialPlot(object = prostate_gbm, data = prostate_hex, cols = c("AGE", "RACE"), plot = F)
  h2o_pp_weight_NA = h2o.partialPlot(object = prostate_gbm, data = prostate_hex, cols = c("AGE", "RACE"), plot = F, weight_column="constWeight", include_na=TRUE)
  h2o_pp_vweight_NA = h2o.partialPlot(object = prostate_gbm, data = prostate_hex, cols = c("AGE", "RACE"), plot = F, weight_column="variWeight", include_na=TRUE)
  
  assert_twoDTable_equal(h2o_pp[[1]], h2o_pp_weight_NA[[1]]) # compare RACE pdp
  assert_twoDTable_equal(h2o_pp[[2]], h2o_pp_weight_NA[[2]]) # compare AGE pdp
  # compare pdp with varying weight with correct answers
  assert_twoDTable_array_equal(h2o_pp_vweight_NA[[2]], wMeanR, wStdR, wStdErrR)
  assert_twoDTable_array_equal(h2o_pp_vweight_NA[[1]], wMeanA, wStdA, wStdErrA)
}

assert_twoDTable_array_equal <- function(table1, arraymean, arraystd, arraystderr) {
  checkEqualsNumeric(table1[, "mean_response"], arraymean)
  checkEqualsNumeric(table1[, "stddev_response"], arraystd)
  checkEqualsNumeric(table1[, "std_error_mean_response"], arraystderr)
  
}
assert_twoDTable_equal <- function(table1, table2) {
  checkEqualsNumeric(table1[, "mean_response"], table2[, "mean_response"][1:length(table1[, "mean_response"])])
  checkEqualsNumeric(table1[, "stddev_response"], table2[, "stddev_response"][1:length(table1[, "mean_response"])])
  checkEqualsNumeric(table1[, "std_error_mean_response"], table2[, "std_error_mean_response"][1:length(table1[, "mean_response"])])
}

doTest("Test Partial Dependence Plots with weights and NAs in H2O: ", testPartialPlots)

