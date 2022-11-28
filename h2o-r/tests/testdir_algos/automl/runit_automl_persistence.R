setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

automl_save_load_test <- function() {
    # Load data and split into train, valid and test sets
    train <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), destination_frame = "training_frame")
    y <- "CAPSULE"
    train[,y] <- as.factor(train[,y])
    max_models <- 3
    filename <- tempfile()
    on.exit(unlink(filename))

    aml <- h2o.automl(y = y,
                        training_frame = train,
                        max_models = max_models)
    leaderboard <- as.data.frame(aml@leaderboard)
    h2o.save_automl(aml, path=dirname(filename), filename = basename(filename))
    extended_leaderboard <- as.data.frame(h2o.get_leaderboard(aml, "ALL"))
    extended_leaderboard_made <- as.data.frame(h2o.make_leaderboard(aml, train, extra_columns = "ALL"))

    h2o.removeAll()

    loaded_aml <- h2o.load_automl(filename)
    loaded_leaderboard <- as.data.frame(loaded_aml@leaderboard)
    expect_equal(leaderboard, loaded_leaderboard)

    loaded_extended_leaderboard <- as.data.frame(h2o.get_leaderboard(loaded_aml, "ALL"))

    # predict_time_per_row_ms is filled with NAs in the loaded extended leaderboard since there is no training frame to compute it on
    pt_idx <- which(names(loaded_extended_leaderboard) == "predict_time_per_row_ms")
    expect_equal(extended_leaderboard[, -pt_idx], loaded_extended_leaderboard[, -pt_idx])

    train <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), destination_frame = "training_frame")
    loaded_extended_leaderboard_made <- as.data.frame(h2o.make_leaderboard(aml, train, extra_columns = "ALL"))

    # Basically checking if it is a value or NA
    extended_leaderboard_made$predict_time_per_row_ms <- is.finite(extended_leaderboard_made$predict_time_per_row_ms)
    loaded_extended_leaderboard_made$predict_time_per_row_ms <- is.finite(loaded_extended_leaderboard_made$predict_time_per_row_ms)

    expect_equal(extended_leaderboard_made, loaded_extended_leaderboard_made)
}


automl_download_upload_test <- function() {
    # Load data and split into train, valid and test sets
    train <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), destination_frame = "training_frame")
    y <- "CAPSULE"
    train[,y] <- as.factor(train[,y])
    max_models <- 3
    filename <- tempfile()
    on.exit(unlink(filename))

    aml <- h2o.automl(y = y,
                      training_frame = train,
                      max_models = max_models)
    leaderboard <- as.data.frame(aml@leaderboard)
    h2o.download_automl(aml, path=dirname(filename), filename = basename(filename))
    extended_leaderboard <- as.data.frame(h2o.get_leaderboard(aml, "ALL"))
    extended_leaderboard_made <- as.data.frame(h2o.make_leaderboard(aml, train, extra_columns = "ALL"))

    h2o.removeAll()

    loaded_aml <- h2o.upload_automl(filename)
    loaded_leaderboard <- as.data.frame(loaded_aml@leaderboard)
    expect_equal(leaderboard, loaded_leaderboard)

    loaded_extended_leaderboard <- as.data.frame(h2o.get_leaderboard(loaded_aml, "ALL"))

    # predict_time_per_row_ms is filled with NAs in the loaded extended leaderboard since there is no training frame to compute it on
    pt_idx <- which(names(loaded_extended_leaderboard) == "predict_time_per_row_ms")
    expect_equal(extended_leaderboard[, -pt_idx], loaded_extended_leaderboard[, -pt_idx])

    train <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), destination_frame = "training_frame")
    loaded_extended_leaderboard_made <- as.data.frame(h2o.make_leaderboard(aml, train, extra_columns = "ALL"))

    # Basically checking if it is a value or NA
    extended_leaderboard_made$predict_time_per_row_ms <- is.finite(extended_leaderboard_made$predict_time_per_row_ms)
    loaded_extended_leaderboard_made$predict_time_per_row_ms <- is.finite(loaded_extended_leaderboard_made$predict_time_per_row_ms)

    expect_equal(extended_leaderboard_made, loaded_extended_leaderboard_made)
}

automl_download_load_test <- function() {
    # Load data and split into train, valid and test sets
    train <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), destination_frame = "training_frame")
    y <- "CAPSULE"
    train[,y] <- as.factor(train[,y])
    max_models <- 3
    filename <- tempfile()
    on.exit(unlink(filename))

    aml <- h2o.automl(y = y,
                      training_frame = train,
                      max_models = max_models)
    leaderboard <- as.data.frame(aml@leaderboard)
    h2o.download_automl(aml, path=dirname(filename), filename = basename(filename))
    extended_leaderboard <- as.data.frame(h2o.get_leaderboard(aml, "ALL"))
    extended_leaderboard_made <- as.data.frame(h2o.make_leaderboard(aml, train, extra_columns = "ALL"))

    h2o.removeAll()

    loaded_aml <- h2o.load_automl(filename)
    loaded_leaderboard <- as.data.frame(loaded_aml@leaderboard)
    expect_equal(leaderboard, loaded_leaderboard)

    loaded_extended_leaderboard <- as.data.frame(h2o.get_leaderboard(loaded_aml, "ALL"))

    # predict_time_per_row_ms is filled with NAs in the loaded extended leaderboard since there is no training frame to compute it on
    pt_idx <- which(names(loaded_extended_leaderboard) == "predict_time_per_row_ms")
    expect_equal(extended_leaderboard[, -pt_idx], loaded_extended_leaderboard[, -pt_idx])

    train <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), destination_frame = "training_frame")
    loaded_extended_leaderboard_made <- as.data.frame(h2o.make_leaderboard(aml, train, extra_columns = "ALL"))

    # Basically checking if it is a value or NA
    extended_leaderboard_made$predict_time_per_row_ms <- is.finite(extended_leaderboard_made$predict_time_per_row_ms)
    loaded_extended_leaderboard_made$predict_time_per_row_ms <- is.finite(loaded_extended_leaderboard_made$predict_time_per_row_ms)

    expect_equal(extended_leaderboard_made, loaded_extended_leaderboard_made)
}

doSuite("AutoML export Test", makeSuite(
  automl_save_load_test,
  automl_download_upload_test,
  automl_download_load_test
))