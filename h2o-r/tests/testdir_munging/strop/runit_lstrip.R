setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.lstrip <- function() {
    # load iris
    iris.hex <- as.h2o(iris)

    # test categorical char strip, should be no change
    iris.hex$Species <- h2o.lstrip(iris.hex$Species)
    expect_that(h2o.levels(iris.hex$Species), equals(levels(iris$Species)))

    # test categorical char strip w/ set
    iris.hex$Species <- h2o.lstrip(iris.hex$Species, "esvi")
    expect_that(h2o.levels(iris.hex$Species), equals(c("tosa", "rsicolor", "rginica")))

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

    # FIXME: These tests work but are failing on Jenkins b/c workspace issues...
    # non ASCII
    #nonASCIIdata <- c('    ¢¢¢¢    ', '  ££some£££words£££', '©2016', 'mystring¡', '¤¥§cray tweet¤¥§')
    #nonASCIIdf <- as.data.frame(nonASCIIdata)
    #colnames(nonASCIIdf) <- c('C1')
    #nonASCIIdf.hex <- as.h2o(nonASCIIdf)
    #nonASCIIdf.hex$C1 <- as.character(nonASCIIdf.hex$C1)

    # R gets funky here w/ nonASCII and adds 'Â' before each nonASCII char when displayed
    # The actual data does not contain 'Â' character...
    #nonASCIIdf.hex$C1 <- h2o.lstrip(nonASCIIdf.hex$C1, " £")
    #expect_that(nonASCIIdf.hex$C1[1,1], equals("Â¢Â¢Â¢Â¢    "))
    #expect_that(nonASCIIdf.hex$C1[2,1], equals("someÂ£Â£Â£wordsÂ£Â£Â£"))
    #expect_that(nonASCIIdf.hex$C1[3,1], equals("Â©2016"))
    #expect_that(nonASCIIdf.hex$C1[4,1], equals("mystringÂ¡"))
    #expect_that(nonASCIIdf.hex$C1[5,1], equals("Â¤Â¥Â§cray tweetÂ¤Â¥Â§"))
}

doTest("Test lstrip", test.lstrip)