


test.glm.nonspdmatrix.exception <- function()
{
  cars.hex <- h2o.uploadFile(locate("smalldata/junit/cars_20mpg.csv"))
  cars.hex[,9] <- as.factor(cars.hex[,9])
  c.sid <- h2o.runif(cars.hex)
  cars.train <- h2o.assign(cars.hex[c.sid > .2, ], "cars.train")
  cars.test <- h2o.assign(cars.hex[c.sid <= .2, ], "cars.test")

  my.glm <- h2o.glm(x = 3:8, y = 9, training_frame = cars.train, family = "binomial", prior = 0.5, lambda_search = TRUE)

}

doTest("Testing GLM NonSPDMatrix Exception", test.glm.nonspdmatrix.exception)
