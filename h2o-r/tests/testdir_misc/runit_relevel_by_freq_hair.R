setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
library(data.table)

test.relevel_by_freq <- function() {
    
    hair_dt <- as.data.table(HairEyeColor)
    # expand back out the frequency table such that N records for each combination
    N <- 10
    hair_dt <- hair_dt[rep(seq(.N), N),][, N := NULL]
    hair_hf <- as.h2o(hair_dt)
    hair_hf <- h2o.asfactor(hair_hf)
    print(hair_hf)
    col <- "Hair"

    print("Original data")
    print(h2o.group_by(data=hair_hf, by=col, nrow(1)))
    expect_equal(c("Black", "Blond", "Brown", "Red"), h2o.levels(hair_hf[col]))

    # reorder using frequencies 
    hair_releveled_hf <- h2o.relevel_by_frequency(x=hair_hf[col])
    print("Relevel by frequency")
    print(h2o.group_by(data=hair_releveled_hf, by=col, nrow(1)))
    expect_equal(c("Brown", "Blond", "Black", "Red"), h2o.levels(hair_releveled_hf[col]))

    # move only the most frequent level
    hair_top2_hf <- h2o.relevel_by_frequency(x=hair_hf[col], top_n=1)
    print("Relevel by frequency top_n=1")
    print(h2o.group_by(data=hair_top2_hf, by=col, nrow(1)))
    expect_equal(c("Brown", "Black", "Blond", "Red"), h2o.levels(hair_top2_hf[col]))

    # move only the most frequent level
    hair_top2_hf <- h2o.relevel_by_frequency(x=hair_hf[col], top_n=2)
    print("Relevel by frequency top_n=2")
    print(h2o.group_by(data=hair_top2_hf, by=col, nrow(1)))
    expect_equal(c("Brown", "Blond", "Black", "Red"), h2o.levels(hair_top2_hf[col]))
}

doTest("Test h2o.relevel_by_freq", test.relevel_by_freq)

