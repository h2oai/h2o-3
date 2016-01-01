setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.GLM.solvers <- function() {

  training_data <- h2o.importFile(h2oTest.locate("smalldata/junit/cars_20mpg.csv"))
  predictors <- c("displacement","power","weight","acceleration","year")

  for (solver in c("AUTO", "IRLSM", "L_BFGS", "COORDINATE_DESCENT_NAIVE", "COORDINATE_DESCENT")) {
    print(paste0("Solver = ",solver))
    for (family in c("binomial", "gaussian", "poisson", "tweedie", "gamma")) {
        if        (family == 'binomial') { response_col <- "economy_20mpg"
        } else if (family == 'gaussian') { response_col <- "economy"
        } else                           { response_col <- "cylinders" }
        print(paste0("Family = ",family))

        if (family == 'binomial') { training_data[,response_col] <- as.factor(training_data[,response_col])
        } else {                    training_data[,response_col] <- as.numeric(training_data[,response_col]) }

        model <- h2o.glm(x=predictors, y=response_col, training_frame=training_data, family=family, alpha=0,
                         lambda=1e-5, solver=solver)
    }
  }
}

h2oTest.doTest("GLM Solvers", test.GLM.solvers)

