setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")




test.klime.titanic <- function() {
  # Load Titanic dataset (with predictions of 'Survived' made by GBM)
  titanic_input <- h2o.importFile(path = locate("smalldata/klime_test/titanic_input.csv"),
                                  col.types = c("enum","enum","real","real","real","enum","real","real","real","real"))
  titanic_expected <- h2o.importFile(path = locate("smalldata/klime_test/titanic_3_expected.csv"),
                                     col.types = c("real","real","real","real","real","real","real","real","real"))

  titanic_age <- titanic_input$Age
  titanic_input$Age <- NULL
  titanic_input <- h2o.cbind(titanic_age, titanic_input)

  # Train a k-LIME model
  klime <- h2o.klime(training_frame = titanic_input,
                    x = c("Age", "Pclass", "Sex", "SibSp", "Parch"), y = "p1",
                    max_k = 3, estimate_k = FALSE,
                    seed = 12345)

  # Use as a regular regression model to predict
  titanic_predicted <- h2o.predict(klime, titanic_input)

  titanic_predicted_loc <- as.data.frame(titanic_predicted)
  titanic_expected_loc <- as.data.frame(titanic_expected)

  # Check clustering
  expect_equal(titanic_predicted_loc$cluster_klime, expected = titanic_expected_loc$cluster_klime)

  # Check prediction on cluster = 0
  expect_equal(titanic_predicted_loc[titanic_predicted_loc$cluster_klime == 0, "predict_klime"],
               expected = titanic_expected_loc[titanic_expected_loc$cluster_klime == 0, "predict_klime"], tolerance = 0.005)
}

doTest("Test k-LIME on Titanic dataset with k=3", test.klime.titanic)
