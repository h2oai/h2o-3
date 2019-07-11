setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


# This test shows h2o.scale will only scale the numeric values (and not corrupt the categoricals)
test.scale.cat <- function() {
    adapt <- function(f) {
        # remove the ID column
        f$ID <- NULL
        # CAPSULE should be categorical
        f$CAPSULE <- as.factor(f$CAPSULE)
        f
    }

    prostate_cat <- adapt(h2o.importFile(locate('smalldata/glm_test/prostate_cat_train.csv')))
    prostate_cat_copy <- adapt(h2o.importFile(locate('smalldata/glm_test/prostate_cat_train.csv')))
    
    cats <- c("CAPSULE", "RACE", "DPROS", "DCAPS")
    nums <- setdiff(colnames(prostate_cat), cats)

    prostate_cat <- h2o.scale(prostate_cat)
    # now only apply to numeric columns
    prostate_cat_copy[, nums] <- h2o.scale(prostate_cat_copy[, nums])

    # sanity check - make sure the columns were actually centered
    expect_equal(rep(0, length(nums)), h2o.mean(prostate_cat_copy[, nums]))
    
    # check that categorical columns are left intact
    expect_equal(as.data.frame(prostate_cat_copy), as.data.frame(prostate_cat))
}

doTest("Test h2o.scale on a Frame with categorical values", test.scale.cat)
