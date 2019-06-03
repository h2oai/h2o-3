setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Testing target encoding  (h2o.target_encode_fit and h2o.target_encode_transform)
##

doTestAndContinue <- function(testDesc, test) {
    tryCatch(test_that(testDesc, withWarnings(test())), warning = function(w) WARN(w), error =function(e) FAIL(e))
}

getTitanicData <- function() {
    dataPath <- locate("smalldata/gbm_test/titanic.csv")
    print("Importing titanic data into H2O")
    data <- h2o.importFile(path = dataPath, destination_frame = "data")
    data$survived <- as.factor(data$survived)
    return(data)
}

test <- function() {
    data <- getTitanicData()

    te_cols <- list("embarked")
    encoding_map <- h2o.target_encode_fit(data, te_cols, "survived")

    frameKeys <- attr(encoding_map, "frames")
    emFrameKeys <- lapply(frameKeys, function(x) x$key$name )
    encodingMapFrame <- h2o.getFrame(emFrameKeys[[1]])

    # Adjust number due to NA level that is not being counted by h2o.levels
    nlevelsOriginally <- length(h2o.levels(data$embarked))
    embarked_na_count <- h2o.nacnt(data$embarked)
    if(embarked_na_count > 0) {
        nlevelsOriginally <- nlevelsOriginally + 1
    }

    Log.info("Expect that number of rows of mapping match number of unique levels in original file")
    expect_that(nrow(encodingMapFrame), equals(nlevelsOriginally))

    Log.info("Expect that numerator matches sum of y = 1 of levels in original file")
    titanic_as_frame <- as.data.frame(data)
    numerator_expected <- aggregate(embarked ~ survived, data = titanic_as_frame[titanic_as_frame$survived == 1, ], length)

    sum_of_numerators_in_encoding_map <- h2o.sum(encodingMapFrame$numerator)

    # need to deduct na_count because we treat NA as another category
    expect_that(sum_of_numerators_in_encoding_map - embarked_na_count, equals(numerator_expected$embarked))

}

testTEColumnAsString <- function() {
    data <- getTitanicData()
    column <- "embarked"
    te_cols <- list(column)
    h2o.target_encode_fit(data, te_cols, "survived")
    h2o.target_encode_fit(data, column, "survived")
    Log.info("Expect that both of the calls to `h2o.target_encode_fit` should not cause error")
}

testHoldoutTypeValidation <- function() {
    data <- getTitanicData()
    column <- "embarked"
    te_cols <- list(column)
    encoding_map <- h2o.target_encode_fit(data, te_cols, "survived")
    Log.info("Expect that holdout_type `UnknownType` will not be accepted and error will be throwned")
    expect_error(h2o.target_encode_transform(data, te_cols, "survived", encoding_map, blended_avg=FALSE,  holdout_type = "UnknownType"))
}


testIndexesToNames <- function() {
    data <- getTitanicData()

    Log.info("Expect that if we pass fold_column as INDEX to the target_encode_fit method we will get encoding map with folds.")
    te_cols <- list("embarked")
    data$fold <- h2o.kfold_column(data, nfolds = 5, seed = 1234)
    encoding_map <- h2o.target_encode_fit(data, te_cols, "survived", 15)
    frameKeys <- attr(encoding_map, "frames")
    emFrameKeys <- lapply(frameKeys, function(x) x$key$name )
    encodingMapFrame <- h2o.getFrame(emFrameKeys[[1]])
    expect_that(TRUE, equals("fold" %in% colnames(encodingMapFrame)))

    Log.info("Expect that we can pass te columns as INDEXES to the target_encode_fit method")
    te_cols <- list(10, 11) # cabin, embarked
    encoding_map <- h2o.target_encode_fit(data, te_cols, "survived")
    mapKeys <- attr(encoding_map, "map_keys")
    emKeys <- mapKeys$string
    expect_that(TRUE, equals("embarked" %in% emKeys && "cabin" %in% emKeys))

    Log.info("Expect that we can pass response column as INDEX to the target_encode_fit method")
    te_cols <- list( 11) # embarked
    response_col <- 2 # "survived"
    encoding_map <- h2o.target_encode_fit(data, te_cols, response_col)
    frameKeys <- attr(encoding_map, "frames")
    emFrameKeys <- lapply(frameKeys, function(x) x$key$name )
    encodingMapFrame <- h2o.getFrame(emFrameKeys[[1]])
    expect_that(270, equals(encodingMapFrame$denominator[1,1]))

    Log.info("Expect that we can pass INDEXES to the `target_encode_fit` and `target_encode_transform` methods")
    te_cols <- list( 11) # embarked
    response_col <- 2 # "survived"
    encoding_map <- h2o.target_encode_fit(data, te_cols, response_col)
    transformed <- h2o.target_encode_transform(data, te_cols, response_col, encoding_map, blended_avg=FALSE,  holdout_type = "loo")

}

testNoiseParameter <- function() {
    .check_vector_is_within_range <- function(vectorFrame, range) {
        for(i in length(vectorFrame)){
            expect_lt(vectorFrame[i,1], range)
        }
    }
    data <- getTitanicData()
    te_cols <- list("embarked")

    encoding_map <- h2o.target_encode_fit(data, te_cols, "survived")

    Log.info("Expect that noise will be added if we are using noise parameter. Default noise is 0.01")
    transformed_without_noise <- h2o.target_encode_transform(data, te_cols, "survived", encoding_map, blended_avg=FALSE,
    holdout_type = "loo", noise = 0, seed = 1234)

    transformed_with_default_noise <- h2o.target_encode_transform(data, te_cols, "survived", encoding_map, blended_avg=FALSE,
    holdout_type = "loo", seed = 1234)

    transformed_with_noise <- h2o.target_encode_transform(data, te_cols, "survived", encoding_map, blended_avg=FALSE,
    holdout_type = "loo", noise = 0.015, seed = 1234)
    diff <- transformed_with_noise$embarked_te - transformed_without_noise$embarked_te

    .check_vector_is_within_range(diff$embarked_te, 0.01)

    diffWithDefaultNoise <- transformed_with_default_noise$embarked_te - transformed_without_noise$embarked_te
    .check_vector_is_within_range(diffWithDefaultNoise$embarked_te, 0.01)

}

testThatWarningWillBeShownIfWeAddNoiseForNoneStrategy <- function() {

    data <- getTitanicData()
    te_cols <- list("embarked")

    data$fold <- h2o.kfold_column(data, nfolds = 5, seed = 1234)

    encoding_map <- h2o.target_encode_fit(data, te_cols, "survived")

    # Expect no warning
    h2o.target_encode_transform(data, te_cols, "survived", encoding_map, blended_avg=FALSE, holdout_type = "none", noise = 0, seed = 1234)

    Log.info("Expect that warning will be shown")
    expect_warning(h2o.target_encode_transform(data, te_cols, "survived", encoding_map, blended_avg=FALSE, holdout_type = "none", noise = 0.1, seed = 1234))

}

testThatErrorWillBeThrownIfUserHasNotUsedFoldColumn <- function() {

    data <- getTitanicData()
    te_cols <- list("embarked")

    data$fold <- h2o.kfold_column(data, nfolds = 5, seed = 1234)

    encoding_map <- h2o.target_encode_fit(data, te_cols, "survived")

    Log.info("Expect that error will be thrown as encoding map was not created with `fold_column` but there is an attempt to use `holdout_type` = 'kfold'")
    expect_error(h2o.target_encode_transform(data, te_cols, "survived", encoding_map, blended_avg=FALSE, holdout_type = "kfold", fold_column="fold", noise = 0, seed = 1234))

}

testKFoldColumnNameIsSpecifiedWhenHoldoutTypeIsSetToKFold <- function() {

    data <- getTitanicData()
    te_cols <- list("embarked")

    data$fold <- h2o.kfold_column(data, nfolds = 5, seed = 1234)

    encoding_map <- h2o.target_encode_fit(data, te_cols, "survived", fold_column = "fold")

    # No exception expected
    h2o.target_encode_transform(data, te_cols, "survived", encoding_map, blended_avg=FALSE, holdout_type = "kfold", fold_column="fold", noise = 0, seed = 1234)
    
    Log.info("Expect that error will be thrown when kfold column name is not provided but holdout_type = `kfold` ")
    expect_error(transformed_without_noise <- h2o.target_encode_transform(data, te_cols, "survived", encoding_map, blended_avg=TRUE,
    holdout_type = "kfold", noise = 0, seed = 1234))
}

testBlendingParamsAreWithinValidRange <- function() {

    data <- getTitanicData()
    te_cols <- list("embarked")

    encoding_map <- h2o.target_encode_fit(data, te_cols, "survived")

    # No exception expected
    h2o.target_encode_transform(data, te_cols, "survived", encoding_map, blended_avg=TRUE, holdout_type = "loo", inflection_point = 1, smoothin = 1, noise = 0, seed = 1234)
    
    Log.info("Expect that error will be thrown when `inflection_point` is not withing valid range")
    expect_error(transformed_without_noise <- h2o.target_encode_transform(data, te_cols, "survived", encoding_map, blended_avg=TRUE, inflection_point = 0, smoothin = 1,
    holdout_type = "loo", noise = 0, seed = 1234))
    Log.info("Expect that error will be thrown when `smoothin` is not withing valid range")
    expect_error(transformed_without_noise <- h2o.target_encode_transform(data, te_cols, "survived", encoding_map, blended_avg=TRUE, inflection_point = 1, smoothin = 0,
    holdout_type = "loo", noise = 0, seed = 1234))
}

testDefaultParamsWillNotCauseErrorToBeThrown <- function() {

    data <- getTitanicData()
    te_cols <- list("embarked")

    encoding_map <- h2o.target_encode_fit(data, te_cols, "survived")

    # No exception expected
    h2o.target_encode_transform(data, te_cols, "survived", encoding_map, holdout_type = "none")
}

doTestAndContinue("Test target encoding exposed from Java", test)
doTestAndContinue("Test that target_encode_fit is also accepting te column as a string(not array with single element", testTEColumnAsString)
doTestAndContinue("Test holdout_type validation", testHoldoutTypeValidation)
doTestAndContinue("Test indexes to names conversion", testIndexesToNames)
doTestAndContinue("Test noise parameter", testNoiseParameter)
doTestAndContinue("Test warning will be shown when noise is used with `none` strategy", testThatWarningWillBeShownIfWeAddNoiseForNoneStrategy)
doTestAndContinue("Test error is being thrown when encoding map does not contain fold column but there is an attempt to apply holdout_type = 'kfold' strategy", testThatErrorWillBeThrownIfUserHasNotUsedFoldColumn)
doTestAndContinue("Test kfold column name is provided for holdout_type=`kfold`", testKFoldColumnNameIsSpecifiedWhenHoldoutTypeIsSetToKFold)
doTestAndContinue("Test that setting blending parameters to values outside of the valid range will throw errors", testBlendingParamsAreWithinValidRange)
doTestAndContinue("Test that using default values of optional parameters does not lead to errors", testDefaultParamsWillNotCauseErrorToBeThrown)
PASS()

