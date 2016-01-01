setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
### This tests offset in glm on real data ######




test <- function(h) {
    h2oTest.logInfo("Importing lung.csv data...\n")
    lung.hex <- h2o.uploadFile(h2oTest.locate("smalldata/glm_test/lung.csv"))
    lung.hex$log_pop <- log(lung.hex$pop)

    lung.r <- read.csv(h2oTest.locate("smalldata/glm_test/lung.csv"), header = TRUE)
    lung.r <- na.omit(lung.r)

    h2oTest.logInfo(cat("H2O GLM (poisson)"))
    lung.glm.h2o <- h2o.glm(y=4,
                            x=1:2,
                            training_frame=lung.hex,
                            family="poisson",
                            lambda=0,
                            offset="log_pop")

    h2oTest.logInfo(cat("{stats} glm (poisson)"))
    lung.glm.r <- glm(cases ~ city + age + offset(log(pop)),
                      family = "poisson",
                      data = lung.r)

    h2o.rd <- lung.glm.h2o@model$training_metrics@metrics$residual_deviance
    r.rd <- lung.glm.r$deviance
    h2oTest.logInfo(paste("H2O residual deviance: ", h2o.rd, ", and R residual deviance: ", r.rd))
    expect_equal(h2o.rd, r.rd, tolerance = 1e-4)

	
}

h2oTest.doTest("GLM poisson offset comparision2: ", test)
