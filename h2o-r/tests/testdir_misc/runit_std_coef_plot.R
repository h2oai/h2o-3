setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


#analogous to pyunit_empty_strings.py
test.std_coef_plot <- function() {
    # Importing prostate.csv data
    prostate.hex <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), destination_frame="prostate.hex")
    # Converting CAPSULE and RACE columns to factors
    prostate.hex$CAPSULE <- as.factor(prostate.hex$CAPSULE)
    prostate.hex$RACE <- as.factor(prostate.hex$RACE)

    # Train H2O GLM Model:
    prostate.glm <- h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), training_frame = prostate.hex,
                            family = "binomial", nfolds = 0, alpha = 0.5, lambda_search = FALSE)

    # plot variable importance for all and two features
    h2o.std_coef_plot(prostate.glm)
    h2o.std_coef_plot(prostate.glm, 2)

}

doTest("Plot Variable Importance", test.std_coef_plot)