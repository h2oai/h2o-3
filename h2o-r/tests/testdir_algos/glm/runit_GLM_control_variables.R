setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.controlVariables <- function() {

    Log.info("Read in prostate data.")
    h2o.data <- h2o.uploadFile(locate("smalldata/prostate/prostate_complete.csv.zip"), destination_frame="h2o.data")

    myY <- "CAPSULE"
    myX <- c("AGE","RACE","DCAPS","PSA","VOL","DPROS","GLEASON")

    Log.info("Build the model")
    model <- h2o.glm(x=myX,
                     y=myY,
                     training_frame=h2o.data,
                     family="binomial",
                     link="logit")

    #print(model)
    res.dev <- model@model$training_metrics@metrics$residual_deviance
    print(res.dev)
    betas <- model@model$coefficients_table$coefficients
    print(betas)

    Log.info("Build the model with control variables")
    model.cont <- h2o.glm(x=myX,
                          y=myY,
                          training_frame=h2o.data,
                          family="binomial",
                          link="logit",
                          control_variables = c("PSA", "AGE"))
    
    #print(model.cont)
    res.dev.cont <- model.cont@model$training_metrics@metrics$residual_deviance
    print(res.dev.cont)
    betas.cont <- model.cont@model$coefficients_table$coefficients
    print(betas.cont)

    expect_equal(res.dev, res.dev.cont)
}

doTest("Comparison of H2O GLM without and with control variables", test.controlVariables)


