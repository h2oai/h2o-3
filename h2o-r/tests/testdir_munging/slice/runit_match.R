setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test.match <- function() {

    data <- as.h2o(iris)

    # compare h2o and base %in%
    h2o_in <- 'setosa' %in% data$Species
    base_in <- base::`%in%`("setosa", as.vector(data$Species))
    expect_equal(h2o_in, base_in)

    sub_h2o_in <- data$Species %in% c("setosa", "versicolor")
    hh_in <- data[sub_h2o_in,]
    expect_equal(dim(hh_in), c(100, 5))

    sub_base_in <- base::`%in%`(as.vector(data$Species), c("setosa", "versicolor"))
    hh_in_base <- data[as.h2o(sub_base_in),]
    expect_equal(dim(hh_in_base), c(100, 5))

    expect_equal(hh_in, hh_in_base)
    
    # compare h2o and base match
    # string values, default setting
    sub_h2o_match <- h2o.match(data$Species, c("setosa", "versicolor"))
    sub_h2o_match <- as.vector(sub_h2o_match)
    expect_equal(sub_h2o_match[1], 1)
    expect_equal(sub_h2o_match[51], 2)
    expect_equal(sub_h2o_match[101], NA_integer_)
    
    sub_base_match <- base::match(as.vector(data$Species), c("setosa", "versicolor"))
    expect_equal(sub_base_match[1], 1)
    expect_equal(sub_base_match[51], 2)
    expect_equal(sub_base_match[101], NA_integer_)

    expect_equal(sub_h2o_match, sub_base_match)

    # string values, nomatch=0
    sub_h2o_match <- h2o.match(data$Species, c("setosa", "versicolor"), nomatch=0)
    sub_h2o_match <- as.vector(sub_h2o_match)
    expect_equal(sub_h2o_match[1], 1)
    expect_equal(sub_h2o_match[51], 2)
    expect_equal(sub_h2o_match[101], 0)

    # string values, start_index=0
    sub_h2o_match <- h2o.match(data$Species, c("setosa", "versicolor"), start_index=0)
    sub_h2o_match <- as.vector(sub_h2o_match)
    expect_equal(sub_h2o_match[1], 0)
    expect_equal(sub_h2o_match[51], 1)
    expect_equal(sub_h2o_match[101], NA_integer_)
    
    sub_h2o_in <- data$Sepal.Length %in% c(5.1)
    hh_in <- data[sub_h2o_in,]
    expect_equal(dim(hh_in), c(9,5))

    # numeric value, default setting
    sub_h2o_match <- h2o.match(data$Sepal.Length, c(5.1))
    sub_h2o_match <- as.vector(sub_h2o_match)
    expect_equal(sub_h2o_match[1], 1)
    expect_equal(sub_h2o_match[18], 1)
    expect_equal(sub_h2o_match[20], 1)
    expect_equal(sub_h2o_match[2], NA_integer_)

    # string values, duplicates in match values
    sub_h2o_match <- h2o.match(data$Species, c("setosa", "versicolor", "setosa"))
    sub_h2o_match <- as.vector(sub_h2o_match)
    expect_equal(sub_h2o_match[1], 1)
    expect_equal(sub_h2o_match[51], 2)
    expect_equal(sub_h2o_match[101], NA_integer_)

    # test doc example 
    match_col <- h2o.match(data$Species, c("setosa", "versicolor", "setosa"))
    iris_match <- h2o.cbind(data, match_col)
    sample <- h2o.splitFrame(iris_match, ratios=0.05, seed=1)[[1]]
    print(sample)
}

doTest("test match", test.match)

