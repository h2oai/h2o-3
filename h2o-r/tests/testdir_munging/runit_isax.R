setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.isax <- function(){
    df = h2o.createFrame(rows = 1, cols = 256, randomize = TRUE,
                         value = 0, real_range = 100, categorical_fraction = 0, factors = 0,
                         integer_fraction = 0, integer_range = 100, binary_fraction = 0,
                         binary_ones_fraction = 0, time_fraction = 0, string_fraction = 0,
                         missing_fraction = 0,has_response = FALSE, seed = 123)
    df2 = h2o.cumsum(df,axis=1)
    res = h2o.isax(df2,num_words=10,max_cardinality=10)
    print(res)
    answer = "0^10_0^10_0^10_0^10_5^10_7^10_8^10_9^10_9^10_8^10"
    print(answer)
    print(res[1,1])
    expect_equal(res[1,1],answer)
}

doTest("Test isax", test.isax)