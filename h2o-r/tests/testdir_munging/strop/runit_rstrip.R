setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.rstrip <- function() {
    # load iris
    iris.hex <- as.h2o(iris)

    # test categorical char strip, should be no change
    iris.hex$Species <- h2o.rstrip(iris.hex$Species)
    expect_that(h2o.levels(iris.hex$Species), equals(levels(iris$Species)))

    # test categorical char strip w/ set
    iris.hex$Species <- h2o.rstrip(iris.hex$Species, "orac")
    expect_that(h2o.levels(iris.hex$Species), equals(c("setos", "versicol", "virgini")))

    # create test data
    data <- c('   empty left', 'empty right   ', 'some string', 'mystring', 'cray tweet')
    df <- as.data.frame(data)
    colnames(df) <- c('C1')
    df.hex <- as.h2o(df)
    df.hex$C1 <- as.character(df.hex$C1)

    # test string char strip
    df.hex$C1 <- h2o.rstrip(df.hex$C1)
    expect_that(df.hex$C1[1,1], is_equivalent_to("   empty left"))
    expect_that(df.hex$C1[2,1], is_equivalent_to("empty right"))
    expect_that(df.hex$C1[3,1], is_equivalent_to("some string"))
    expect_that(df.hex$C1[4,1], is_equivalent_to("mystring"))
    expect_that(df.hex$C1[5,1], is_equivalent_to("cray tweet"))

    # test string char strip
    df.hex$C1 <- h2o.rstrip(df.hex$C1, "lefthgin")
    expect_that(df.hex$C1[1,1], is_equivalent_to("   empty "))
    expect_that(df.hex$C1[2,1], is_equivalent_to("empty r"))
    expect_that(df.hex$C1[3,1], is_equivalent_to("some str"))
    expect_that(df.hex$C1[4,1], is_equivalent_to("mystr"))
    expect_that(df.hex$C1[5,1], is_equivalent_to("cray tw"))

    # FIXME: These tests work but are failing on Jenkins b/c workspace issues...
    # non ASCII
    #nonASCIIdata <- c('    ¢¢¢¢    ', '  ££some£££words£££', '©2016', 'mystring¡', '¤¥§cray tweet¤¥§')
    #nonASCIIdf <- as.data.frame(nonASCIIdata)
    #colnames(nonASCIIdf) <- c('C1')
    #nonASCIIdf.hex <- as.h2o(nonASCIIdf)
    #nonASCIIdf.hex$C1 <- as.character(nonASCIIdf.hex$C1)

    # R gets funky here w/ nonASCII and adds 'Â' before each nonASCII char when displayed
    # The actual data does not contain 'Â' character...
    #nonASCIIdf.hex$C1 <- h2o.rstrip(nonASCIIdf.hex$C1, " £")
    #expect_that(nonASCIIdf.hex$C1[1,1], equals("    Â¢Â¢Â¢Â¢"))
    #expect_that(nonASCIIdf.hex$C1[2,1], equals("  Â£Â£someÂ£Â£Â£words"))
    #expect_that(nonASCIIdf.hex$C1[3,1], equals("Â©2016"))
    #expect_that(nonASCIIdf.hex$C1[4,1], equals("mystringÂ¡"))
    #expect_that(nonASCIIdf.hex$C1[5,1], equals("Â¤Â¥Â§cray tweetÂ¤Â¥Â§"))
}

doTest("Test rstrip", test.rstrip)