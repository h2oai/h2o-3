setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("/Users/nickkarpov/ws/h2o-3/h2o-r/scripts/h2o-r-test-setup.R")

test.lstrip <- function() {
    # load iris
    iris.hex <- as.h2o(iris)

    # test categorical char strip, should be no change
    iris.hex$Species <- h2o.lstrip(iris.hex$Species)
    expect_that(h2o.levels(iris.hex$Species)[1], equals("setosa"))
    expect_that(h2o.levels(iris.hex$Species)[2], equals("versicolor"))
    expect_that(h2o.levels(iris.hex$Species)[3], equals("virginica"))

    # test categorical char strip w/ set
    iris.hex$Species <- h2o.lstrip(iris.hex$Species, "esvi")
    expect_that(h2o.levels(iris.hex$Species)[1], equals("tosa"))
    expect_that(h2o.levels(iris.hex$Species)[2], equals("rsicolor"))
    expect_that(h2o.levels(iris.hex$Species)[3], equals("rginica"))

    # create test data
    data <- c('   empty left', 'empty right   ', 'some string', 'mystring', 'cray tweet')
    df <- as.data.frame(data)
    colnames(df) <- c('C1')
    df.hex <- as.h2o(df)
    df.hex$C1 <- as.character(df.hex$C1)

    # test string char strip
    df.hex$C1 <- h2o.lstrip(df.hex$C1)
    expect_that(df.hex$C1[1,1], equals("empty left"))  # only this should change b/c left white space
    expect_that(df.hex$C1[2,1], equals("empty right   "))
    expect_that(df.hex$C1[3,1], equals("some string"))
    expect_that(df.hex$C1[4,1], equals("mystring"))
    expect_that(df.hex$C1[5,1], equals("cray tweet"))

    # test string char strip
    df.hex$C1 <- h2o.lstrip(df.hex$C1, "me")
    expect_that(df.hex$C1[1,1], equals("pty left"))  # matches
    expect_that(df.hex$C1[2,1], equals("pty right   ")) # matches
    expect_that(df.hex$C1[3,1], equals("some string"))
    expect_that(df.hex$C1[4,1], equals("ystring")) # matches
    expect_that(df.hex$C1[5,1], equals("cray tweet"))
}

doTest("Test lstrip", test.lstrip)
