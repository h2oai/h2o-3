setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.RF.contribs_sorted_smoke <- function() {
    prostate_hex <- h2o.importFile(locate("smalldata/prostate/prostate.csv"))
    first_row <- prostate_hex[1,]

    rf_model <- h2o.randomForest(training_frame = prostate_hex[2:9], y = "CAPSULE", max_depth=100, ntrees=50, keep_cross_validation_models=TRUE, seed=1234)

    contributions <- as.data.frame(h2o.predict_contributions(rf_model, prostate_hex, top_n=0, top_bottom_n=0, abs=FALSE))
    expect_equal(dim(contributions), c(380,8), info="Output frame has wrong dimmension")

    contributions <- as.data.frame(h2o.predict_contributions(rf_model, first_row, top_n=2, top_bottom_n=0, abs=FALSE))
    expect_equal("VOL", as.character(contributions[1,1]), info="Not sorted correctly")
    expect_equal("RACE", as.character(contributions[1,3]), info="Not sorted correctly")

    contributions <- as.data.frame(h2o.predict_contributions(rf_model, first_row, top_n=0, top_bottom_n=2, abs=FALSE))
    expect_equal("PSA", as.character(contributions[1,1]), info="Not sorted correctly")
    expect_equal("GLEASON", as.character(contributions[1,3]), info="Not sorted correctly")

    contributions <- as.data.frame(h2o.predict_contributions(rf_model, first_row, top_n=2, top_bottom_n=2, abs=FALSE))
    expect_equal("VOL", as.character(contributions[1,1]), info="Not sorted correctly")
    expect_equal("RACE", as.character(contributions[1,3]), info="Not sorted correctly")
    expect_equal("PSA", as.character(contributions[1,5]), info="Not sorted correctly")
    expect_equal("GLEASON", as.character(contributions[1,7]), info="Not sorted correctly")

    contributions <- as.data.frame(h2o.predict_contributions(rf_model, prostate_hex, top_n=0, top_bottom_n=0, abs=TRUE))
    expect_equal(dim(contributions), c(380,8), info="Output frame has wrong dimmension")

    contributions <- as.data.frame(h2o.predict_contributions(rf_model, first_row, top_n=2, top_bottom_n=0, abs=TRUE))
    expect_equal("PSA", as.character(contributions[1,1]), info="Not sorted correctly")
    expect_equal("GLEASON", as.character(contributions[1,3]), info="Not sorted correctly")

    contributions <- as.data.frame(h2o.predict_contributions(rf_model, first_row, top_n=0, top_bottom_n=2, abs=TRUE))
    expect_equal("DCAPS", as.character(contributions[1,1]), info="Not sorted correctly")
    expect_equal("RACE", as.character(contributions[1,3]), info="Not sorted correctly")

    contributions <- as.data.frame(h2o.predict_contributions(rf_model, first_row, top_n=2, top_bottom_n=2, abs=TRUE))
    expect_equal("PSA", as.character(contributions[1,1]), info="Not sorted correctly")
    expect_equal("GLEASON", as.character(contributions[1,3]), info="Not sorted correctly")
    expect_equal("DCAPS", as.character(contributions[1,5]), info="Not sorted correctly")
    expect_equal("RACE", as.character(contributions[1,7]), info="Not sorted correctly")
}

doTest("RF Test: Make sure RF contributions are sorted", test.RF.contribs_sorted_smoke)
