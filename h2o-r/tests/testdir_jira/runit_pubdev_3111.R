setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.pubdev_3111 = function(){
    # Create some random data
    myframe = h2o.createFrame(rows = 20, cols = 5,
                              seed = -12301283, randomize = TRUE, value = 0,
                              categorical_fraction = 0.8, factors = 10, real_range = 1,
                              integer_fraction = 0.2, integer_range = 10,
                              binary_fraction = 0, binary_ones_fraction = 0.5,
                              missing_fraction = 0.2,
                              response_factors = 1)
     # Turn integer column into a categorical
     myframe[,5] <- as.factor(myframe[,5])
     head(myframe, 20)

     # Create pairwise interactions
     pairwise <- h2o.interaction(myframe, destination_frame = 'pairwise',
                                 factors = list(c(1,2),c("C2","C3","C4")),
                                 pairwise=TRUE, max_factors = 10, min_occurrence = 1)

    h2o_ls = lapply(h2o.ls(), as.character)$key
    expect_true("pairwise" %in% h2o_ls)

}

doTest("R API's h2o.interaction() does not use destination_frame argument", test.pubdev_3111)