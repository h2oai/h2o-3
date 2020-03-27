setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.model.gam <- function() {
    data <- h2o.importFile(path = locate('smalldata/testng/airlines_train.csv'))
    cols <- c("Distance")
    original_model <- h2o.gam(x=cols, y = "IsDepDelayed", gam_x = cols, training_frame = data, family = "binomial")
    print(original_model)
    expect_false(is.null(original_model))
    
    prostate = h2o.importFile(locate("smalldata/prostate/prostate.csv"))
    prostate$CAPSULE <- as.factor(prostate$CAPSULE)
    h2o.gam(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), gam_x = c("AGE"), training_frame = prostate,
    family = "binomial", alpha = 0.5, lambda_search = FALSE)
}

doTest("General Additive Model test", test.model.gam)
