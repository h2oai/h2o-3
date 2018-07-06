setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test.CoxPH.load_save <- function() {
    hex.fit <- h2o.coxph(x = c("age", "rx", "ecog.ps"), event_column = "fustat", stop_column = "futime",
                         training_frame = as.h2o(ovarian))
    print(hex.fit)

    # Expected Frame id
    fr_var_cumhaz_2.id <- paste0(hex.fit@model_id, "_var_cumhaz_2")

    # Export & Delete
    model.path <- h2o.saveModel(hex.fit, sandbox())
    h2o.rm(hex.fit@model_id)

    # Frame should be deleted
    expect_false(fr_var_cumhaz_2.id %in% h2o.ls()$key)

    h2o.loadModel(model.path)

    # Frame should be re-created
    var_cumhaz_2 = h2o.getFrame(fr_var_cumhaz_2.id)
    expect_equal(c(26, 3), dim(var_cumhaz_2))
}

doTest("CoxPH: Save and Load", test.CoxPH.load_save)