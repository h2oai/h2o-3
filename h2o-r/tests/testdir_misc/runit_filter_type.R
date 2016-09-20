setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

# setupRandomSeed(1994831827)

test_filter_type <- function() {
    fr <- h2o.importFile(locate("smalldata/jira/filter_type.csv"), "hex")
    fr$e = as.factor(fr$e)
    
    num_type = h2o.filter_type(fr) #numeric by default
    cat_type = h2o.filter_type(fr,type="categorical")
    str_type = h2o.filter_type(fr,type="string")
    time_type = h2o.filter_type(fr,type="time")
    uuid_type = h2o.filter_type(fr,type="uuid")
    bad_type = h2o.filter_type(fr,type="bad")

    expect_that(num_type, equals(c(1,3)))
    expect_that(cat_type, equals(c(5)))
    expect_that(str_type, equals(c(2)))
    expect_that(time_type, equals(c(4)))
    expect_that(uuid_type, equals(c(6)))
    expect_that(bad_type, equals(c(3)))

}

doTest("Filter frame by col types", test_filter_type)