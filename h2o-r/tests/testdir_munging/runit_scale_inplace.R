setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


# This test shows h2o.scale will only scale the numeric values (and not corrupt the categoricals)
test.scale.inplace <- function() {
    prostate <- h2o.importFile(locate('smalldata/extdata/prostate.csv'))
    prostate$ID <- NULL

    # Save a local copy that H2O cannot touch
    prostate_local <- as.data.frame(prostate)

    expected <- as.data.frame(scale(prostate_local))

    prostate_scaled <- h2o.scale(prostate)
    
    # Check the input dataset was not modified
    expect_equal(prostate_local, as.data.frame(prostate))

    print(head(expected))
    print(head(as.data.frame(prostate_scaled)))

    # Check the output is properly scaled
    expect_equal(expected, as.data.frame(prostate_scaled))

    # Scaling in-place
    h2o.scale(prostate, inplace=TRUE)
    expect_equal(expected, as.data.frame(prostate))
}

doTest("Test h2o.scale - in-place scaling", test.scale.inplace)
