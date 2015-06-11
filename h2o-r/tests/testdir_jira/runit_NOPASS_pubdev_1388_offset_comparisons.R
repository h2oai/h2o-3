setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.offset.comparison <- function(conn) {
  Log.info("Loading datasets...")
  pros.dat <- read.csv(locate("smalldata/prostate/prostate.csv"))
  pros.dat[,2] <- as.factor(pros.dat[,2])
  pros.dat[,4] <- as.factor(pros.dat[,4])
  pros.dat[,5] <- as.factor(pros.dat[,5])
  pros.dat[,6] <- as.factor(pros.dat[,6])
  pros.dat[,9] <- as.factor(pros.dat[,9])
  p.sid <- runif(pros.dat)
  pros.train.R <- pros.dat[p.sid > .2, ]
  pros.test.R <- pros.dat[p.sid <= .2, ]
  pros.train.h2o <- as.h2o(pros.train.R, destination_frame = "pros.train")
  pros.test.h2o <- as.h2o(pros.test.R, destination_frame = "pros.test")

  cars.dat <- read.csv(locate("smalldata/junit/cars.csv"))
  cars.dat[,1] <- as.factor(cars.dat[,1])
  cars.dat[,3] <- as.factor(cars.dat[,3])
  c.sid <- runif(cars.dat)
  cars.train.R <- cars.dat[c.sid > .2, ]
  cars.test.R <- cars.dat[c.sid <= .2, ]
  cars.train.h2o <- as.h2o(cars.train.R, destination_frame = "cars.train")
  cars.test.h2o <- as.h2o(cars.test.R, destination_frame = "cars.test")

  family_type = c("gaussian", "poisson")
  solver = c("IRLSM", "L_BFGS")

  Log.info("Running Binomial Comparison...")
  glm.bin.R <- glm(CAPSULE ~ . -ID -AGE, family = "binomial", data = pros.train.R,
                   offset = pros.train.R$AGE)
  glm.bin.h2o <- h2o.glm(x = 4:9, y = 2, training_frame = pros.train.h2o,
                         family = "binomial", standardize = F,
                         offset_column = "AGE")
  print("binomial")
  print("R:")
  print(paste("deviance       ", glm.bin.R$deviance))
  print(paste("null deviance: ", glm.bin.R$null.deviance))
  print(paste("aic:           ", glm.bin.R$aic))
  print("H2O:")
  print(paste("deviance       ", h2o.residual_deviance(glm.bin.h2o)))
  print(paste("null deviance: ", h2o.null_deviance(glm.bin.h2o)))
  print(paste("aic:           ", h2o.aic(glm.bin.h2o)))
  expect_equal(glm.bin.R$deviance, h2o.residual_deviance(glm.bin.h2o))
  expect_equal(glm.bin.R$null.deviance, h2o.null_deviance(glm.bin.h2o))
  expect_equal(glm.bin.R$aic, h2o.aic(glm.bin.h2o))

  Log.info("Running Regression Comparisons...")
  for(fam in family_type) {
    glm.R <- glm(economy..mpg. ~ ., family = fam, data = cars.train.R,
                 offset = cars.train.R$year)
    glm.h2o <- h2o.glm(x = c(1, 3:8), y = 2, training_frame = cars.train.h2o,
                       family = fam, standardize = F, offset_column = "year")
    print(fam)
    print("R:")
    print(paste("deviance:       ",glm.R$deviance))
    print(paste("null deviance: ", glm.R$null.deviance))
    print(paste("aic:           ", glm.R$aic))
    print("H2O:")
    print(paste("deviance:       ",h2o.residual_deviance(glm.h2o)))
    print(paste("null deviance: ", h2o.null_deviance(glm.h2o)))
    print(paste("aic:           ", h2o.aic(glm.h2o)))
    expect_equal(glm.R$deviance, h2o.residual_deviance(glm.h2o),
                 label = paste(fam, "H2O deviance"),
                 expected.label = paste(fam, "R deviance"))
    expect_equal(glm.R$null.deviance, h2o.null_deviance(glm.h2o),
                 label = paste(fam, "H2O null deviance"),
                 expected.label = paste(fam, "R null deviance"))
    expect_equal(glm.R$aic, h2o.aic(glm.h2o),
                 label = paste(fam, "H2O aic"),
                 expected.label = paste(fam, "R aic"))
  }

  testEnd()
}

doTest("Testing Offsets in GLM", test.offset.comparison)