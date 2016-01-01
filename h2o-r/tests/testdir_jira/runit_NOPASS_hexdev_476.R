setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.hexdev.476 <- function() {

    cars.hex <- h2o.uploadFile(h2oTest.locate("smalldata/junit/cars.csv"))
    cars.hex[,3] <- as.factor(cars.hex[,3])
    c.sid <- h2o.runif(cars.hex)
    cars.train <- h2o.assign(cars.hex[c.sid > .2, ], "cars.train")
    cars.test <- h2o.assign(cars.hex[c.sid <= .2, ], "cars.test")

    x <- c(3,4,5,6,7)
    y <- 2
    family <- "gaussian"
    solver <- "L_BFGS"
    link <- "inverse"
    alpha <- 0.647752951597795
    lambda_search <- TRUE
    beta_constraints <- data.frame(names=c("cylinders.3","cylinders.4","cylinders.5","cylinders.6","cylinders.8",
                                           "displacement (cc)","power (hp)","weight (lb)","0-60 mph (s)"),
                                   lower_bounds=c(-0.6187974,0.4776914,0.1440038,-0.3016808,0.1122200,-0.4713539,
                                                  -0.2321098,0.7914278,-0.5622925),
                                   upper_bounds=c(-0.082615558,0.569782338,1.032216270,0.211087204,1.068418983,
                                                  0.373266821,-0.011390360,0.827313250,-0.003660066))

    h2o.glm(x=x, y=y, training_frame=cars.train, family=family, solver=solver, link=link, alpha=alpha,
            lambda_search=lambda_search, beta_constraints=beta_constraints)

}

h2oTest.doTest("HEXDEV-476", test.hexdev.476)
