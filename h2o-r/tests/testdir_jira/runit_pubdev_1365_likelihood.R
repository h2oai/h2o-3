setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
####################################################################################################
## Test for pubdev-1365
## Likelihood reaches infinity and fails
####################################################################################################




test.likelihood.infinity <- function() {
  cars.hex <- h2o.uploadFile(h2oTest.locate("smalldata/junit/cars.csv"))
  cars.hex[,3] <- as.factor(cars.hex[,3])
  c.sid <- h2o.runif(cars.hex)
  cars.train <- h2o.assign(cars.hex[c.sid > .2, ], "cars.train")
  cars.test <- h2o.assign(cars.hex[c.sid <= .2, ], "cars.test")

  bc <- data.frame(
    names = c('cylinders.4', 'cylinders.5', 'cylinders.6', 'cylinders.8', 'displacement (cc)', 'power (hp)', 'weight (lb)', '0-60 mph (s)'),
    lower_bounds = c(0.7534199, -0.7151317, -0.3926009, 0.1918593, 0.3669187, -0.4763336, 0.4140609, -0.1862967),
    upper_bounds = c(1.4351102,  0.2168169,  0.2448618, 0.5457334, 0.7613185, -0.2467378, 0.8391007, -0.1172999)
    )


  myglm <- h2o.glm(x = 3:7, y = 2, training_frame = cars.train, family = 'gaussian', link = 'log', beta_constraints = bc)

  
}

h2oTest.doTest("Likelihood Is Infinity and Fails in Comparison", test.likelihood.infinity)
