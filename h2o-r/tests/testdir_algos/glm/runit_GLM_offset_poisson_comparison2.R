### This tests offset in glm on real data ######

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test <- function(h) {
    Log.info("Importing lung.csv data...\n")
    lung.hex <- h2o.uploadFile(locate("smalldata/glm_test/lung.csv"))
    lung.hex$log_pop <- log(lung.hex$pop)

    lung.r <- read.csv(locate("smalldata/glm_test/lung.csv"), header = TRUE)
    lung.r <- na.omit(lung.r)

    Log.info(cat("H2O GLM (poisson)"))
    lung.glm.h2o <- h2o.glm(y=4,
                            x=1:2,
                            training_frame=lung.hex,
                            family="poisson",
                            lambda=0,
                            offset="log_pop")

    Log.info(cat("{stats} glm (poisson)"))
    lung.glm.r <- glm(cases ~ city + age + offset(log(pop)),
                      family = "poisson",
                      data = lung.r)

    h2o.rd <- lung.glm.h2o@model$training_metrics@metrics$residual_deviance
    r.rd <- lung.glm.r$deviance
    Log.info(paste("H2O residual deviance: ", h2o.rd, ", and R residual deviance: ", r.rd))
    expect_equal(h2o.rd, r.rd, tolerance = 1e-4)

	testEnd()
}

doTest("GLM poisson offset comparision2: ", test)