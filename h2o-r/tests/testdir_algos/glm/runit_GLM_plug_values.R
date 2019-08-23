setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.glm.plug_values <- function() {
    cars <- h2o.importFile(locate("smalldata/junit/cars_20mpg.csv"))
    cars$name <- NULL

    glm1 <- h2o.glm(training_frame = cars, y = "cylinders")

    means <- h2o.mean(cars, na.rm = TRUE, return_frame = TRUE)

    glm2 <- h2o.glm(training_frame = cars, y = "cylinders", missing_values_handling="PlugValues", plug_values=means)
    expect_equal(h2o.coef(glm1), h2o.coef(glm2))

    glm3 <- h2o.glm(training_frame = cars, y = "cylinders", missing_values_handling="PlugValues", plug_values=0.1+2*means)
    expect_false(isTRUE(all.equal(h2o.coef(glm2), h2o.coef(glm3))))
}

doTest("Test Plug Values in GLM", test.glm.plug_values)
