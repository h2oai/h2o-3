setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

# setupRandomSeed(1994831827)

test_columns_by_type <- function() {

    #Positive case. All coltypes available
    fr <- h2o.importFile(locate("smalldata/jira/filter_type.csv"), "hex")
    fr$e = as.factor(fr$e)

    #Negative case frame. All coltypes are ints
    frame = h2o.createFrame(rows = 10, cols = 10, randomize = TRUE, value = 0,
              real_range = 100, categorical_fraction = 0, factors = 0,
              integer_fraction = 1, integer_range = 100, binary_fraction = 0,
              binary_ones_fraction = 0, time_fraction = 0, string_fraction = 0,
              missing_fraction = 0,has_response = FALSE)

    #Positive case. Look for all coltypes
    num_type = h2o.columns_by_type(fr) #numeric by default
    cat_type = h2o.columns_by_type(fr,coltype="categorical")
    str_type = h2o.columns_by_type(fr,coltype="string")
    time_type = h2o.columns_by_type(fr,coltype="time")
    uuid_type = h2o.columns_by_type(fr,coltype="uuid")
    bad_type = h2o.columns_by_type(fr,coltype="bad")

    #Negative case. Look for categoricals,strings,times,uuid, and bad when there are none. Should return an empty list.
    neg_cat = h2o.columns_by_type(frame,coltype="categorical")
    neg_string = h2o.columns_by_type(frame,coltype="string")
    neg_time = h2o.columns_by_type(frame,coltype="time")
    neg_uuid = h2o.columns_by_type(frame,coltype="uuid")
    neg_bad = h2o.columns_by_type(frame,coltype="bad")

    #Positive Test
    expect_that(num_type, equals(c(1,3)))
    expect_that(cat_type, equals(c(5)))
    expect_that(str_type, equals(c(2)))
    expect_that(time_type, equals(c(4)))
    expect_that(uuid_type, equals(c(6)))
    expect_that(bad_type, equals(c(3)))

    #Negative Test
    expect_that(neg_cat, equals(numeric(0)))
    expect_that(neg_string, equals(numeric(0)))
    expect_that(neg_time, equals(numeric(0)))
    expect_that(neg_uuid, equals(numeric(0)))
    expect_that(neg_bad, equals(numeric(0)))

}

doTest("Filter frame by col types", test_columns_by_type)