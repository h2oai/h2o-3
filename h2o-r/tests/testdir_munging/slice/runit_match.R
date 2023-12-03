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
}

doTest("test match", test.match)

