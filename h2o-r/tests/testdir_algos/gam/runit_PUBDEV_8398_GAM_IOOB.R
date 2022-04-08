setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# When this test was run, it will run into array index out of bound error with -1.  This is due to GAM trying to update
# gradient calculation for coefficients that are not active.  I have since fixed this error in the Java backend.  Thank you 
# Marco for brining this up to me.
test.model.gam.IOOB <- function() {
    mtcars_h2o <- as.h2o(mtcars)
    att_model <- h2o.gam(y = "mpg",
                         gam_columns = c("disp", "hp", "drat", "wt"),
                         bs=c(0,2,0,2),
                         family = "gamma",
                         link = "log",
                         training_frame = mtcars_h2o,
                         nfold = 3,
                         standardize = TRUE,
                         alpha = .5,
                         lambda_search = TRUE,
                         model_id = "GAM_Model")
    print("coefficient length is ")
    print(length(att_model@model$coefficients))
    expect_true(length(att_model@model$coefficients) == 27)
}

doTest("General Additive Model test from Marco to test no IOOB with CS, IS", test.model.gam.IOOB)
