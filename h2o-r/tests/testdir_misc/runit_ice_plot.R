setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

expect_ggplot <- function(gg) {
  p <- force(gg)
  expect_true("gg" %in% class(p))
  file <- tempfile(fileext = ".png")
  # try to actually plot it - otherwise ggplot is not evaluated
  tryCatch({ggplot2::ggsave(file, plot = p)}, finally = unlink(file))
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
       expect_ggplot(h2o.ice_plot(gbm, train, col, display_mode="ice"))
       expect_ggplot(h2o.ice_plot(gbm, train, col, display_mode="pdp"))
    }
  }
}




doSuite("Explanation Tests", makeSuite(
    ice_plot_display_mode
))
