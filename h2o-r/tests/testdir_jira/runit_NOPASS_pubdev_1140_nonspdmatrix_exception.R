setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.glm.nonspdmatrix.exception <- function(conn)
{
  cars.hex <- h2o.uploadFile(locate("smalldata/junit/cars.csv"))
  cars.hex[,3] <- as.factor(cars.hex[,3])
  c.sid <- h2o.runif(cars.hex)
  cars.train <- h2o.assign(cars.hex[c.sid > .2, ], "cars.train")
  cars.test <- h2o.assign(cars.hex[c.sid <= .2, ], "cars.test")

  my.glm1 <- h2o.glm(x = 3:7, y = 2, training_frame = cars.train, family = "gamma", prior = 0.5, lambda_search = TRUE)
  my.glm3 <- h2o.glm(x = 3:7, y = 2, training_frame = cars.train, family = "poisson", prior = 0.5, lambda_search = TRUE)

  testEnd()
}

doTest("Testing GLM NonSPDMatrix Exception", test.glm.nonspdmatrix.exception)
