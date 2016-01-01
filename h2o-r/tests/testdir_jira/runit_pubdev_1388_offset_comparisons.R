setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.offset.comparison <- function() {
  h2oTest.logInfo("Loading datasets...")
  pros.dat <- read.csv(h2oTest.locate("smalldata/prostate/prostate.csv"))
  pros.dat[,2] <- as.factor(pros.dat[,2])
  pros.dat[,4] <- as.factor(pros.dat[,4])
  pros.dat[,5] <- as.factor(pros.dat[,5])
  pros.dat[,6] <- as.factor(pros.dat[,6])
  pros.dat[,9] <- as.factor(pros.dat[,9])
  pros.hex <- as.h2o(pros.dat)

  cars.dat <- read.csv(h2oTest.locate("smalldata/junit/cars.csv"))
  cars.dat[,1] <- as.factor(cars.dat[,1])
  cars.dat[,3] <- as.factor(cars.dat[,3])
  cars.hex <- as.h2o(cars.dat)

  family_type = c("gaussian", "poisson")
  solver = c("IRLSM", "L_BFGS")

  h2oTest.logInfo("Running Binomial Comparison...")
  glm.bin.R <- glm(CAPSULE ~ . -ID -AGE, family = "binomial", data = pros.dat,
                   offset = pros.dat$AGE)
  glm.bin.h2o <- h2o.glm(x = 4:9, y = 2, training_frame = pros.hex, family = "binomial",
                         standardize = F, offset_column = "AGE", lambda = 0, max_iterations = 100)
  print("binomial")
  print("R:")
  print(paste("deviance       ", glm.bin.R$deviance))
  print(paste("null deviance: ", glm.bin.R$null.deviance))
  print(paste("aic:           ", glm.bin.R$aic))
  print("H2O:")
  print(paste("deviance       ", h2o.residual_deviance(glm.bin.h2o)))
  print(paste("null deviance: ", h2o.null_deviance(glm.bin.h2o)))
  print(paste("aic:           ", h2o.aic(glm.bin.h2o)))
  expect_equal(glm.bin.R$deviance, h2o.residual_deviance(glm.bin.h2o), tolerance = 0.1)
  expect_equal(glm.bin.R$null.deviance, h2o.null_deviance(glm.bin.h2o), tolerance = 0.1)
  if (glm.bin.R$aic != Inf)
    expect_equal(glm.bin.R$aic, h2o.aic(glm.bin.h2o), tolerance = 0.1)

  h2oTest.logInfo("Running Regression Comparisons...")
  for(fam in family_type) {
    glm.R <- glm(economy..mpg. ~ . - name - year, family = fam, data = cars.dat, offset = cars.dat$year)
    glm.h2o <- h2o.glm(x = 3:7, y = 2, training_frame = cars.hex, family = fam,
                       standardize = F, offset_column = "year", lambda = 0, max_iterations = 100)
    print(fam)
    print("R:")
    print(paste("deviance:       ",glm.R$deviance))
    print(paste("null deviance: ", glm.R$null.deviance))
    print(paste("aic:           ", glm.R$aic))
    print("H2O:")
    print(paste("deviance:       ",h2o.residual_deviance(glm.h2o)))
    print(paste("null deviance: ", h2o.null_deviance(glm.h2o)))
    print(paste("aic:           ", h2o.aic(glm.h2o)))
    expect_equal(glm.R$deviance, h2o.residual_deviance(glm.h2o), label = paste(fam, "H2O deviance"),
                 expected.label = paste(fam, "R deviance"), tolerance = 0.1)
    expect_equal(glm.R$null.deviance, h2o.null_deviance(glm.h2o),
                 label = paste(fam, "H2O null deviance"),
                 expected.label = paste(fam, "R null deviance"), tolerance = 0.1)
    if (glm.R$aic != Inf)
      expect_equal(glm.R$aic, h2o.aic(glm.h2o),
                   label = paste(fam, "H2O aic"),
                   expected.label = paste(fam, "R aic"), tolerance = 0.1)
  }

  
}

h2oTest.doTest("Testing Offsets in GLM", test.offset.comparison)
