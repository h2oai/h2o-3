setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test.match <- function() {

    iris <- as.h2o(iris)

    # compare h2o and base %in%
    h2o_in <- 'setosa' %in% iris$Species
    base_in <- base::`%in%`("setosa", as.vector(iris$Species))
    expect_equal(h2o_in, base_in)

    sub_h2o_in <- iris$Species %in% c("setosa", "versicolor")
    hh_in <- iris[sub_h2o_in,]
    expect_equal(dim(hh_in), c(100, 5))

    sub_base_in <- base::`%in%`(as.vector(iris$Species), c("setosa", "versicolor"))
    hh_in_base <- iris[as.h2o(sub_base_in),]
    expect_equal(dim(hh_in_base), c(100, 5))

    expect_equal(hh_in, hh_in_base)
    
    sub_h2o_match <- h2o.match(hex$Species, c("setosa", "versicolor"))
    sub_h2o_match <- as.vector(sub_h2o_match)
    expect_equal(sub_h2o_match[1], 1)
    expect_equal(sub_h2o_match[51], 2)
    expect_equal(sub_h2o_match[101], NA_integer_)
    
    sub_base_match <- base::match(as.vector(hex$Species), c("setosa", "versicolor"))
    expect_equal(sub_base_match[1], 1)
    expect_equal(sub_base_match[51], 2)
    expect_equal(sub_base_match[101], NA_integer_)

    # compare h2o and base
    expect_equal(sub_h2o_match, sub_base_match)

    sub_h2o_match <- h2o.match(hex$Species, c("setosa", "versicolor"), nomatch=0)
    sub_h2o_match <- as.vector(sub_h2o_match)
    expect_equal(sub_h2o_match[1], 1)
    expect_equal(sub_h2o_match[51], 2)
    expect_equal(sub_h2o_match[101], 0)

    sub_h2o_match <- h2o.match(hex$Species, c("setosa", "versicolor"), start_index=0)
    sub_h2o_match <- as.vector(sub_h2o_match)
    expect_equal(sub_h2o_match[1], 0)
    expect_equal(sub_h2o_match[51], 1)
    expect_equal(sub_h2o_match[101], NA_integer_)
    
    sub_h2o_in <- hex$Sepal.Length %in% c(5.1)
    hh_in <- hex[sub_h2o_in,]
    expect_equal(dim(hh_in), c(9,5))
    
    sub_h2o_match <- h2o.match(hex$Sepal.Length, c(5.1))
    sub_h2o_match <- as.vector(sub_h2o_match)
    expect_equal(sub_h2o_match[1], 1)
    expect_equal(sub_h2o_match[18], 1)
    expect_equal(sub_h2o_match[20], 1)
    expect_equal(sub_h2o_match[2], NA_integer_)
}

doTest("test match", test.match)

