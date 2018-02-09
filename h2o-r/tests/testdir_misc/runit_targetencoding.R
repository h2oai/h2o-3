setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Testing target encoding map (h2o.targetencoding_map) and target encoding frame (h2o.targetencoding_frame)
##


# setupRandomSeed(1994831827)

test <- function() {
  Log.info("Upload iris dataset into H2O...")
  iris.hex = as.h2o(iris)
  
  Log.info("Calculating Target Encoding Mapping for numeric column")
  map_length <- h2o.targetencoding_map(iris.hex$Species, iris.hex$Sepal.Length)
  
  Log.info("Expect that number of rows of mapping match number of unique levels in original file")
  expect_that(nrow(map_length), equals(length(levels(iris$Species))))
  
  Log.info("Expect that numerator matches sum of Sepal.Length of levels in original file")
  map_length_df <- as.data.frame(map_length)
  numerator_expected <- aggregate(Sepal.Length ~ Species, data = iris, sum)
  expect_that(map_length_df$numerator, equals(numerator_expected$Sepal.Length))
  
  Log.info("Expect that denominator matches frequency of levels in original file")
  denominator_expected <- aggregate(Sepal.Length ~ Species, data = iris, length)
  expect_that(map_length_df$denominator, equals(denominator_expected$Sepal.Length))
  
  Log.info("Calculating Target Encoding Mapping for binary column with NA's")
  iris$y <- as.factor(ifelse(iris$Sepal.Length < 5, NA, ifelse(iris$Sepal.Length < 6, "yes", "no")))
  iris.hex <- as.h2o(iris)
  map_y <- h2o.targetencoding_map(iris.hex$Species, iris.hex$y)
  
  Log.info("Expect that numerator matches sum of y = yes of levels in original file")
  map_y_df <- as.data.frame(map_y)
  numerator_expected <- aggregate(y ~ Species, data = iris[iris$y == "yes", ], length)
  expect_that(map_y_df$numerator, equals(numerator_expected$y))
  
  Log.info("Expect that denominator matches frequency of levels in original file")
  denominator_expected <- aggregate(y ~ Species, data = iris, length)
  expect_that(map_y_df$denominator, equals(denominator_expected$y))
  
  
  Log.info("Calculating Target Encoding Frame for train = TRUE")
  frame_y_train <- h2o.targetencoding_frame(iris.hex$Species, iris.hex$y, map_y, train = TRUE)
  
  Log.info("Expect that number of rows of frame match number of rows of original file")
  expect_that(nrow(frame_y_train), equals(nrow(iris.hex)))
  
  Log.info("Calculating Target Encoding Frame for train = FALSE")
  frame_y_test <- h2o.targetencoding_frame(iris.hex$Species, iris.hex$y, map_y, train = FALSE)
  
  Log.info("Expect that number of rows of frame match number of rows of original file")
  expect_that(nrow(frame_y_test), equals(nrow(iris.hex)))
  
}

doTest("Test target encoding", test)

