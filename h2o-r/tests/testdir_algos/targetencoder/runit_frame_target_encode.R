setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# Testing target encoding map (h2o.target_encode_create) and target encoding frame (h2o.target_encode_apply).
# Thoses tests are only for the legacy pure client-based implementation of target encoding in R: 
# this is NOT related with the backend implementation of target encoding.
##


# setupRandomSeed(1994831827)

test <- function() {
  Log.info("Upload iris dataset into H2O...")
  shuffled_iris <- iris[sample(c(1:nrow(iris)), nrow(iris)), ]
  iris.hex = as.h2o(shuffled_iris)
  
  Log.info("Calculating Target Encoding Mapping for numeric column")
  map_length <- h2o.target_encode_create(iris.hex, list("Species"), "Sepal.Length")[["Species"]]
  
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
  map_y <- h2o.target_encode_create(iris.hex, list("Species"), "y")
  
  Log.info("Expect that numerator matches sum of y = yes of levels in original file")
  map_y_df <- as.data.frame(map_y[["Species"]])
  numerator_expected <- aggregate(y ~ Species, data = iris[iris$y == "yes", ], length)
  expect_that(map_y_df$numerator, equals(numerator_expected$y))
  
  Log.info("Expect that denominator matches frequency of levels in original file")
  denominator_expected <- aggregate(y ~ Species, data = iris, length)
  expect_that(map_y_df$denominator, equals(denominator_expected$y))
  
  Log.info("Calculating Target Encoding Mapping with fold_column")
  iris$my_fold <- rep(c(1:3), 50)
  iris.hex <- as.h2o(iris)
  map_fold <- h2o.target_encode_create(iris.hex, list("Species"), "y", fold_column = "my_fold")
  
  Log.info("Expect that numerator matches sum of y = yes by fold and Species in original file")
  numerator_fold_expected <- aggregate(y ~ my_fold + Species, data = iris[(iris$y == "yes"), ], length)
  expect_that(as.matrix(map_fold[["Species"]]$numerator)[, 1], equals(numerator_fold_expected$y))
  
  Log.info("Expect that denominator matches frequency by fold and Species in original file")
  denominator_fold_expected <- aggregate(y ~ my_fold + Species, data = iris, length)
  expect_that(as.matrix(map_fold[["Species"]]$denominator)[, 1], equals(denominator_fold_expected$y))
  
  
  Log.info("Calculating Target Encoding Frame for `holdout_type = None`")
  frame_y_test <- h2o.target_encode_apply(iris.hex, x = "Species", y = "y", map_y, holdout_type = "None", blended_avg = FALSE, noise_level = 0)
  
  Log.info("Expect that number of rows of frame match number of rows of original file")
  expect_that(nrow(frame_y_test), equals(nrow(iris.hex)))
  
  Log.info("Expect that target encoding of test matches the average per group on the original file")
  expected_y_test <- merge(numerator_expected, denominator_expected, by = "Species")
  expected_y_test <- merge(iris, expected_y_test, by = "Species", all.x = T, all.y = F)
  expect_that(as.matrix(frame_y_test$TargetEncode_Species)[, 1], equals(expected_y_test$y.x/expected_y_test$y.y))
  
  Log.info("Calculating Target Encoding Frame for `holdout_type = LeaveOneOut`")
  frame_y_train <- h2o.target_encode_apply(iris.hex, x = list("Species"), y = "y", map_y, holdout_type = "LeaveOneOut", blended_avg = FALSE, noise_level = 0)
  
  Log.info("Expect that number of rows of frame match number of rows of original file")
  expect_that(nrow(frame_y_train), equals(nrow(iris.hex)))
  
  Log.info("Expect that target encoding of train matches the average per group on the original file - minus record value")
  expected_y_train <- ifelse(is.na(expected_y_test$y), 
                             expected_y_test$y.x/expected_y_test$y.y, 
                             (expected_y_test$y.x - ifelse(expected_y_test$y == "yes", 1, 0))/(expected_y_test$y.y - 1))
  expect_that(as.matrix(frame_y_train$TargetEncode_Species)[, 1], equals(expected_y_train))
  
  Log.info("Calculating Target Encoding Frame for `holdout_type = LeaveOneOut` and blended_avg = TRUE")
  frame_y_blended <- h2o.target_encode_apply(iris.hex, x = list("Species"), y = "y", map_y, holdout_type = "LeaveOneOut", blended_avg = TRUE, noise_level = 0)
  
  Log.info("Expect that number of rows of frame match number of rows of original file")
  expect_that(nrow(frame_y_blended), equals(nrow(iris.hex)))
  
  Log.info("Expect that target encoding of train with blending matches blending average of original file")
  expected_y_blended <- NULL
  for(i in unique(iris$Species)){
    
    species <- iris[iris$Species == i, ]
    species$denominator <- nrow(species[!is.na(species$y), ])
    species$denominator <- ifelse(!is.na(species$y), species$denominator - 1,  species$denominator)
    
    species$numerator <- nrow(species[!is.na(species$y) & (species$y == "yes"), ])
    species$numerator <- ifelse((species$y == "yes") & !is.na(species$y), species$numerator - 1,  species$numerator)
    
    lambda <- 1/(1 + exp((-1)* (species$denominator - 20)/10))
    species$C1 <- ((1 - lambda) * nrow(iris[!is.na(iris$y) & (iris$y == "yes"), ])/nrow(iris[!is.na(iris$y), ])) + 
      (lambda * species$numerator/species$denominator)
   
    expected_y_blended <- rbind(expected_y_blended, species) 
  }
  expect_that(as.matrix(frame_y_blended$TargetEncode_Species)[, 1], equals(expected_y_blended$C1))
  
  
  Log.info("Calculating Target Encoding Frame for `holdout_type = LeaveOneOut`, blended_avg = TRUE and noise_level = 0.1")
  frame_y_noise <- h2o.target_encode_apply(iris.hex, x = list("Species"), y = "y", map_y, holdout_type = "LeaveOneOut", blended_avg = TRUE, noise_level = 0.1)
  
  Log.info("Expect that number of rows of frame match number of rows of original file")
  expect_that(nrow(frame_y_noise), equals(nrow(iris.hex)))
  
  Log.info("Calculating Target Encoding Frame for Sepal.Length")
  map_sepallen <- h2o.target_encode_create(iris.hex, list("Species"), "Sepal.Length")
  frame_sepallen <- h2o.target_encode_apply(iris.hex, x = list("Species"), y = "Sepal.Length", map_sepallen, holdout_type = "LeaveOneOut", seed = 1234)
  expect_that(nrow(frame_sepallen), equals(nrow(iris.hex)))
  
  Log.info("Calculating Target Encoding Frame for x and y indices")
  map_indices <- h2o.target_encode_create(iris.hex, x = list(5), y = 1)
  
  Log.info("Expect that mapping with indices matches mapping with column names")
  expect_that(map_indices, equals(map_sepallen))
  
  Log.info("Expect that frame with indices matches frame with column names")
  frame_indices <- h2o.target_encode_apply(iris.hex, x = list(5), y = 1, map_indices, holdout_type = "LeaveOneOut", seed = 1234)
  expect_that(frame_indices, equals(frame_sepallen))
  
  Log.info("Calculating Target Encoding Frame with Fold")
  frame_y_fold <- h2o.target_encode_apply(iris.hex, x = list("Species"), y = "y", map_fold, holdout_type = "KFold", fold_column = "my_fold", noise_level = 0, blended_avg = FALSE)
  expect_that(nrow(frame_y_fold), equals(nrow(iris)))
  
  Log.info("Calculating Target Encoding Frame with String Columns")
  iris.hex$y <- as.character(iris.hex$y)
  frame_strings <- h2o.target_encode_apply(iris.hex, x = list("Species"), y = "Sepal.Length", map_sepallen, holdout_type = "None")
  
  Log.info("Expect that string column dropped from frame")
  expect_that(ncol(frame_strings), equals(ncol(iris.hex)))
  expect_that(colnames(frame_strings), equals(c("Species", "Sepal.Length", "Sepal.Width", "Petal.Length", "Petal.Width", "my_fold", "TargetEncode_Species")))
  
  
  Log.info("Calculating Target Encoding for multiple columns")
  iris.hex$y <- as.factor(iris.hex$y)
  mapping_multiple <- h2o.target_encode_create(iris.hex, list(c("Species"), c("y")), "Sepal.Length")
  frame_multiple <- h2o.target_encode_apply(iris.hex, list(c("Species"), c("y")), "Sepal.Length", mapping_multiple, holdout_type = "None")
  
  Log.info("Expect that there are two target encoding columns")
  expect_that(colnames(frame_multiple), equals(c("y", "Species", "Sepal.Length", "Sepal.Width", "Petal.Length", "Petal.Width", "my_fold", "TargetEncode_Species", "TargetEncode_y")))
  
  Log.info("Calculating Target Encoding for interaction columns")
  mapping_interaction <- h2o.target_encode_create(iris.hex, list(c("Species", "y")), "Sepal.Length")
  frame_interaction <- h2o.target_encode_apply(iris.hex, list(c("Species", "y")), "Sepal.Length", mapping_interaction, holdout_type = "None")
  
  Log.info("Expect that there are two target encoding columns")
  expect_that(colnames(frame_interaction), equals(c("Species", "y", "Sepal.Length", "Sepal.Width", "Petal.Length", "Petal.Width", "my_fold", "TargetEncode_Species:y")))
  
}

doTest("Test target encoding", test)

