setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


expect_ggplot <- function(gg) {
  p <- force(gg)
  expect_true("gg" %in% class(p))
  file <- tempfile(fileext = ".png")
  # try to actually plot it - otherwise ggplot is not evaluated
  tryCatch({ggplot2::ggsave(file, plot = p)}, finally = unlink(file))
}

test_original_values <- function() {
  train <- h2o.uploadFile(locate("smalldata/titanic/titanic_expanded.csv"))
  y <- "fare"

  gbm1 <- h2o.gbm(y = y,
  training_frame = train,
  seed = 1234,
  model_id = "my_awesome_model1")

  col_types <- setNames(unlist(h2o.getTypes(train)), names(train))
  col_types <- col_types[names(col_types) != y]
  cols_to_test <- names(col_types[!duplicated(col_types)])

  for (col in cols_to_test) {
    if (col != "name")
      expect_ggplot(h2o.ice_plot(gbm1, train, col))
  }
  # for (col in cols_to_test) {
  #   if (col != "name") # name is string colum -> not supported
  #     p = h2o.ice_plot(gbm1, train, col)#, show_logodds=TRUE)
  #     file = paste("Rplot", "titanic", col, ".png", sep = "")
  #     print(file)
  #     ggplot2::ggsave(file, plot = p, path = getwd())
  # }

  train <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  y <- "CAPSULE"
  train[, y] <- as.factor(train[, y])

  col_types <- setNames(unlist(h2o.getTypes(train)), names(train))
  col_types <- col_types[names(col_types) != y]
  cols_to_test <- names(col_types[!duplicated(col_types)])

  gbm2 <- h2o.gbm(y = y,
  training_frame = train,
  seed = 1234,
  model_id = "my_awesome_model2")

  for (col in cols_to_test) {
    expect_ggplot(h2o.ice_plot(gbm2, train, col))
  }
  # for (col in cols_to_test) {
  #   p = h2o.ice_plot(gbm2, train, col)#, show_logodds=TRUE)
  #   file = paste("Rplot", "prostate", col, ".png", sep = "")
  #   print(file)
  #   ggplot2::ggsave(file, plot = p, path = getwd())
  # }

  train <- h2o.uploadFile(locate("smalldata/iris/iris2.csv"))
  y <- "response"
  train[, y] <- as.factor(train[, y])

  col_types <- setNames(unlist(h2o.getTypes(train)), names(train))
  col_types <- col_types[names(col_types) != y]
  cols_to_test <- names(col_types[!duplicated(col_types)])

  gbm3 <- h2o.gbm(y = y,
  training_frame = train,
  seed = 1234,
  model_id = "my_awesome_model3")

  for (col in cols_to_test) {
     expect_ggplot(h2o.ice_plot(gbm3, train, col, target = "setosa"))
  }
  # for (col in cols_to_test) {
  #   p = h2o.ice_plot(gbm3, train, col, target = "setosa")
  #   file = paste("Rplot", "iris", col, ".png", sep = "")
  #   print(file)
  #   ggplot2::ggsave(file, plot = p, path = getwd())
  # }
}

ice_plot_display_mode <- function() {
    train <- h2o.uploadFile(locate("smalldata/titanic/titanic_expanded.csv"))
    y <- "fare"

    col_types <- setNames(unlist(h2o.getTypes(train)), names(train))
    col_types <- col_types[names(col_types) != y]
    cols_to_test <- names(col_types[!duplicated(col_types)])

    gbm <- h2o.gbm(y = y,
    training_frame = train,
    seed = 1234,
    model_id = "my_awesome_model")

    for (col in cols_to_test) {
        if (col != "name") { # a string column
            expect_ggplot(h2o.ice_plot(gbm, train, col))
            expect_ggplot(h2o.ice_plot(gbm, train, col, show_pdp=FALSE))
            expect_ggplot(h2o.ice_plot(gbm, train, col, show_pdp=TRUE))
        }
    }
}

ice_plot_test_binary_response_scale <- function() {
    train <- h2o.uploadFile(locate("smalldata/titanic/titanic_expanded.csv"))
    y <- "survived"

    col_types <- setNames(unlist(h2o.getTypes(train)), names(train))
    col_types <- col_types[names(col_types) != y]
    cols_to_test <- names(col_types[!duplicated(col_types)])

    gbm <- h2o.gbm(y = y,
    training_frame = train,
    seed = 1234,
    model_id = "my_awesome_model")

    for (col in cols_to_test) {
        if (col != "name") { # a string column
            expect_ggplot(h2o.ice_plot(gbm, train, col, binary_response_scale="logodds"))
            expect_ggplot(h2o.ice_plot(gbm, train, col, binary_response_scale="response" ))
            expect_ggplot(h2o.ice_plot(gbm, train, col))
        }
    }

    expect_error(h2o.ice_plot(gbm, train, col, binary_response_scale="invalid_value"))

    y <- "fare"
    gbm <- h2o.gbm(y = y,
    training_frame = train,
    seed = 1234,
    model_id = "my_awesome_model")
    expect_error(h2o.ice_plot(gbm, train, col, binary_response_scale="logodds"), "binary_response_scale cannot be set to 'logodds' value for non-binomial models!")

}


ice_plot_display_mode <- function() {
    train <- h2o.uploadFile(locate("smalldata/titanic/titanic_expanded.csv"))
    y <- "fare"

    col_types <- setNames(unlist(h2o.getTypes(train)), names(train))
    col_types <- col_types[names(col_types) != y]
    cols_to_test <- names(col_types[!duplicated(col_types)])

    gbm <- h2o.gbm(y = y,
    training_frame = train,
    seed = 1234,
    model_id = "my_awesome_model")

    for (col in cols_to_test) {
        if (col != "name") { # a string column
            expect_ggplot(h2o.ice_plot(gbm, train, col))
            expect_ggplot(h2o.ice_plot(gbm, train, col, show_pdp=FALSE))
            expect_ggplot(h2o.ice_plot(gbm, train, col, show_pdp=TRUE))
        }
    }
}


doSuite("Explanation Tests", makeSuite(
    test_original_values,
    ice_plot_display_mode,
    ice_plot_test_binary_response_scale,
    ice_plot_display_mode
))
