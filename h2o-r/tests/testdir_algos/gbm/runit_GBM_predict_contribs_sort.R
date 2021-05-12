setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.GBM.contribs_sorted <- function() {
    prostate_hex <- h2o.importFile(locate("smalldata/prostate/prostate.csv"))
    first_row <- prostate_hex[1,]

    gbm_model <- h2o.gbm(training_frame = prostate_hex[2:9], y = "CAPSULE", nfolds=10, ntrees=10, keep_cross_validation_models=TRUE, seed=1234)

    contributions <- as.data.frame(h2o.predict_contributions(gbm_model, prostate_hex, top_n=0, top_bottom_n=0, abs=FALSE))
    expect_equal(dim(contributions), c(380,8), info="Output frame has wrong dimmension")

    contributions <- as.data.frame(h2o.predict_contributions(gbm_model, first_row, top_n=2, top_bottom_n=0, abs=FALSE))
    expect_equal("VOL", as.character(contributions[1,1]), info="Not sorted correctly")
    expect_equal("RACE", as.character(contributions[1,3]), info="Not sorted correctly")

    contributions <- as.data.frame(h2o.predict_contributions(gbm_model, first_row, top_n=0, top_bottom_n=2, abs=FALSE))
    expect_equal("GLEASON", as.character(contributions[1,1]), info="Not sorted correctly")
    expect_equal("PSA", as.character(contributions[1,3]), info="Not sorted correctly")

    contributions <- as.data.frame(h2o.predict_contributions(gbm_model, first_row, top_n=2, top_bottom_n=2, abs=FALSE))
    expect_equal("VOL", as.character(contributions[1,1]), info="Not sorted correctly")
    expect_equal("RACE", as.character(contributions[1,3]), info="Not sorted correctly")
    expect_equal("GLEASON", as.character(contributions[1,5]), info="Not sorted correctly")
    expect_equal("PSA", as.character(contributions[1,7]), info="Not sorted correctly")

    contributions <- as.data.frame(h2o.predict_contributions(gbm_model, first_row, top_n=-1, top_bottom_n=0, abs=FALSE))
    checkSortedCorrectly(contributions)

    contributions <- as.data.frame(h2o.predict_contributions(gbm_model, first_row, top_n=-1, top_bottom_n=-1, abs=FALSE))
    checkSortedCorrectly(contributions)

    contributions <- as.data.frame(h2o.predict_contributions(gbm_model, first_row, top_n=0, top_bottom_n=-1, abs=FALSE))
    checkSortedCorrectlyReverse(contributions)

    contributions <- as.data.frame(h2o.predict_contributions(gbm_model, first_row, top_n=50, top_bottom_n=-1, abs=FALSE))
    checkSortedCorrectly(contributions)

    contributions <- as.data.frame(h2o.predict_contributions(gbm_model, first_row, top_n=-1, top_bottom_n=50, abs=FALSE))
    checkSortedCorrectly(contributions)

    contributions <- as.data.frame(h2o.predict_contributions(gbm_model, first_row, top_n=50, top_bottom_n=50, abs=FALSE))
    checkSortedCorrectly(contributions)

    contributions <- as.data.frame(h2o.predict_contributions(gbm_model, first_row, top_n=4, top_bottom_n=4, abs=FALSE))
    checkSortedCorrectly(contributions)

    contributions <- as.data.frame(h2o.predict_contributions(gbm_model, prostate_hex, top_n=0, top_bottom_n=0, abs=TRUE))
    expect_equal(dim(contributions), c(380,8), info="Output frame has wrong dimmension")

    contributions <- as.data.frame(h2o.predict_contributions(gbm_model, first_row, top_n=2, top_bottom_n=0, abs=TRUE))
    expect_equal("GLEASON", as.character(contributions[1,1]), info="Not sorted correctly")
    expect_equal("PSA", as.character(contributions[1,3]), info="Not sorted correctly")

    contributions <- as.data.frame(h2o.predict_contributions(gbm_model, first_row, top_n=0, top_bottom_n=2, abs=TRUE))
    expect_equal("RACE", as.character(contributions[1,1]), info="Not sorted correctly")
    expect_equal("DCAPS", as.character(contributions[1,3]), info="Not sorted correctly")

    contributions <- as.data.frame(h2o.predict_contributions(gbm_model, first_row, top_n=2, top_bottom_n=2, abs=TRUE))
    expect_equal("GLEASON", as.character(contributions[1,1]), info="Not sorted correctly")
    expect_equal("PSA", as.character(contributions[1,3]), info="Not sorted correctly")
    expect_equal("RACE", as.character(contributions[1,5]), info="Not sorted correctly")
    expect_equal("DCAPS", as.character(contributions[1,7]), info="Not sorted correctly")

    contributions <- as.data.frame(h2o.predict_contributions(gbm_model, first_row, top_n=-1, top_bottom_n=0, abs=TRUE))
    checkSortedCorrectlyAbs(contributions)

    contributions <- as.data.frame(h2o.predict_contributions(gbm_model, first_row, top_n=-1, top_bottom_n=-1, abs=TRUE))
    checkSortedCorrectlyAbs(contributions)

    contributions <- as.data.frame(h2o.predict_contributions(gbm_model, first_row, top_n=0, top_bottom_n=-1, abs=TRUE))
    checkSortedCorrectlyReverseAbs(contributions)

    contributions <- as.data.frame(h2o.predict_contributions(gbm_model, first_row, top_n=50, top_bottom_n=-1, abs=TRUE))
    checkSortedCorrectlyAbs(contributions)

    contributions <- as.data.frame(h2o.predict_contributions(gbm_model, first_row, top_n=-1, top_bottom_n=50, abs=TRUE))
    checkSortedCorrectlyAbs(contributions)

    contributions <- as.data.frame(h2o.predict_contributions(gbm_model, first_row, top_n=50, top_bottom_n=50, abs=TRUE))
    checkSortedCorrectlyAbs(contributions)

    contributions <- as.data.frame(h2o.predict_contributions(gbm_model, first_row, top_n=4, top_bottom_n=4, abs=TRUE))
    checkSortedCorrectlyAbs(contributions)
}

checkSortedCorrectly <- function (contributions) {
    expect_equal(dim(contributions), c(1,15), info="Output frame has wrong dimmension")
    expect_equal("VOL", as.character(contributions[1,1]), info="Not sorted correctly")
    expect_equal("RACE", as.character(contributions[1,3]), info="Not sorted correctly")
    expect_equal("DCAPS", as.character(contributions[1,5]), info="Not sorted correctly")
    expect_equal("DPROS", as.character(contributions[1,7]), info="Not sorted correctly")
    expect_equal("AGE", as.character(contributions[1,9]), info="Not sorted correctly")
    expect_equal("PSA", as.character(contributions[1,11]), info="Not sorted correctly")
    expect_equal("GLEASON", as.character(contributions[1,13]), info="Not sorted correctly")
}

checkSortedCorrectlyReverse <- function (contributions) {
    expect_equal(dim(contributions), c(1,15), info="Output frame has wrong dimmension")
    expect_equal("VOL", as.character(contributions[1,13]), info="Not sorted correctly")
    expect_equal("RACE", as.character(contributions[1,11]), info="Not sorted correctly")
    expect_equal("DCAPS", as.character(contributions[1,9]), info="Not sorted correctly")
    expect_equal("DPROS", as.character(contributions[1,7]), info="Not sorted correctly")
    expect_equal("AGE", as.character(contributions[1,5]), info="Not sorted correctly")
    expect_equal("PSA", as.character(contributions[1,3]), info="Not sorted correctly")
    expect_equal("GLEASON", as.character(contributions[1,1]), info="Not sorted correctly")
}

checkSortedCorrectlyAbs <- function (contributions) {
    expect_equal(dim(contributions), c(1,15), info="Output frame has wrong dimmension")
    expect_equal("GLEASON", as.character(contributions[1,1]), info="Not sorted correctly")
    expect_equal("PSA", as.character(contributions[1,3]), info="Not sorted correctly")
    expect_equal("VOL", as.character(contributions[1,5]), info="Not sorted correctly")
    expect_equal("AGE", as.character(contributions[1,7]), info="Not sorted correctly")
    expect_equal("DPROS", as.character(contributions[1,9]), info="Not sorted correctly")
    expect_equal("DCAPS", as.character(contributions[1,11]), info="Not sorted correctly")
    expect_equal("RACE", as.character(contributions[1,13]), info="Not sorted correctly")
}

checkSortedCorrectlyReverseAbs <- function (contributions) {
    expect_equal(dim(contributions), c(1,15), info="Output frame has wrong dimmension")
    expect_equal("GLEASON", as.character(contributions[1,13]), info="Not sorted correctly")
    expect_equal("PSA", as.character(contributions[1,11]), info="Not sorted correctly")
    expect_equal("VOL", as.character(contributions[1,9]), info="Not sorted correctly")
    expect_equal("AGE", as.character(contributions[1,7]), info="Not sorted correctly")
    expect_equal("DPROS", as.character(contributions[1,5]), info="Not sorted correctly")
    expect_equal("DCAPS", as.character(contributions[1,3]), info="Not sorted correctly")
    expect_equal("RACE", as.character(contributions[1,1]), info="Not sorted correctly")
}

doTest("GBM Test: Make sure GBM contributions are sorted", test.GBM.contribs_sorted)
