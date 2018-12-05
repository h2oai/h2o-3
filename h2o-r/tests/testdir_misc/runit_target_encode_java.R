setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Testing target encoding  (h2o.target_encode_fit and h2o.target_encode_transform)
##

doTestAndContinue <-
function(testDesc, test) {
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
    Log.info("Expect that holdout_type will be converted to `loo` and no error will be throwned")
    transformed <- h2o.target_encode_transform(data, te_cols, "survived", encoding_map, blended_avg=FALSE,  holdout_type = "LeaveOneOut",   is_train_or_valid=TRUE)

    # Log.info("Expect that holdout_type will be converted to `kfold` and no error will be throwned")
    # transformed <- h2o.target_encode_transform(data, te_cols, "survived", encoding_map, blended_avg=FALSE,  holdout_type = "KFold",   is_train_or_valid=TRUE)
    #
    # Log.info("Expect that holdout_type will be converted to `None` and no error will be throwned")
    # transformed <- h2o.target_encode_transform(data, te_cols, "survived", encoding_map, blended_avg=FALSE,  holdout_type = "None",   is_train_or_valid=TRUE)

}

doTestAndContinue("Test target encoding exposed from Java", test)
doTestAndContinue("Test that target_encode_fit is also accepting te column as a string(not array with single element", testTEColumnAsString)
doTestAndContinue("Test holdout_type validation", testHoldoutTypeValidation)
PASS()

