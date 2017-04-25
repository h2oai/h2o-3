setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")




test.klime.titanic <- function() {
  # Load Titanic dataset (with predictions of 'Survived' made by GBM)
  titanic_input = h2o.importFile(path = locate("smalldata/klime_test/titanic_input.csv"),
                                   col.types = c("enum","enum","real","real","real","enum","real","real","real","real"))
  titanic_expected = h2o.importFile(path = locate("smalldata/klime_test/titanic_3_expected.csv"),
                                    col.types = c("real","real","real","real","real","real","real","real","real"))

  # Train a k-LIME model
  klime = h2o.klime(training_frame = titanic_input,
                    x = c("Pclass", "Sex", "Age", "SibSp", "Parch"), y = "p1",
                    max_k = 3, estimate_k = FALSE,
                    seed = 12345)

  # Use as a regular regression model to predict
  titanic_predicted <- h2o.predict(klime, titanic_input)

  # Check predictions
  expect_equal(as.data.frame(titanic_predicted$predict_klime)[1],
               expected = as.data.frame(titanic_expected$predict_klime)[1], tolerance = 0.005)
}

doTest("Test k-LIME on Titanic dataset with k=3", test.klime.titanic)
