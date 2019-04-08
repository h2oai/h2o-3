setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


test_get_types <- function() {
    h2o_test_frame <- h2o.uploadFile(locate("smalldata/junit/cars.csv"))
    h2o_test_frame["name"] <- h2o.asfactor(h2o_test_frame["name"])
    types <- h2o.getTypes(h2o_test_frame)
    expect_false(all(is.na(types)))
}

doTest("Test h2o.getTypes returns always types not NULL", test_get_types)
