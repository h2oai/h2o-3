setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../../scripts/h2o-r-test-setup.R")

library(tweedie)


generate_data <- function(tweedie_p, phi, offset) {
  set.seed(12345)
  molsp <- 1000
  x <- seq(1, 10, 1)
  yd <- exp(1 + 1.015 * x)
  simData <- matrix(0, nrow = molsp * 10, ncol = 5)
  colnames(simData) <- c('xt', 'yt', 'yr', 'weight', 'offset_col')
  for (i in 1:length(x)) {
    simData[((i - 1) * molsp + 1):(i * molsp), 1] <- x[i]
    simData[((i - 1) * molsp + 1):(i * molsp), 2] <- yd[i]
    simData[((i - 1) * molsp + 1):(i * molsp), 3] <-
      rtweedie(molsp,
               xi = tweedie_p,
               mu = yd[i],
               phi = phi)
    simData[((i - 1) * molsp + 1):(i * molsp), 4] <- 1
    simData[((i - 1) * molsp + 1):(i * molsp), 5] <- offset
  }
  as.data.frame(simData)
}

nll <- function(simData, mu, phi, p) {
  -sum(log(
    simData$weight * dtweedie(
      y = simData$yr,
      mu = mu,
      phi = phi,
      power = p
    )
  ))
}

train_models <- function(simData, tweedie_p, phi) {
  simDataH2O <- as.h2o(simData)
  simData <- as.data.frame(simData)
  offset <- simData[1, "offset_col"]
  hfit <- h2o.glm(
    training_frame = simDataH2O,
    x = 'xt',
    y = 'yr',
    weights_column = 'weight',
    offset_column = "offset_col",
    model_id = paste0("TweedieDispersionMLE_p", tweedie_p,"_phi", phi, "_offset", offset),
    family = "tweedie",
    link = "tweedie",
    tweedie_link_power = 0,
    tweedie_variance_power = tweedie_p,
    standardize = T,
    intercept = T,
    lambda = 0,
    compute_p_values = T,
    solver = "IRLSM",
    calc_like = T,
    dispersion_epsilon = 1e-5,
    seed = 12345
  )
  set.seed(12345)
  rfit <-
    glm(
      yr ~ xt,
      family = tweedie(var.power = tweedie_p, link.power = 0),
      data = simData,
      weights = weight,
      offset = offset_col
    )
  cat("\n\nRfit:\n")
  print(coef(rfit))
  
  cat("\nH2Ofit:\n")
  print(hfit@model$coefficients_table)
  cat("\n\n")
  rdispersion <- summary(rfit)$dispersion # not a MLE
  
  if (tweedie_p > 1.4 && tweedie_p < 1.75 && !(phi == 1000 && tweedie_p == 1.7)) {  # R's implementation can take very long time to finish for some other values
    tp <- tweedie.profile( yr ~ xt,
                           p.vec= tweedie_p,
                           link.power = 0,
                           data = simData,
                           weights = weight,
                           offset = offset_col,
                           phi.method = "mle",
                           do.smooth = FALSE,
                           verbose = 0
    )
    rdispersion <- tp$phi.max
  }
  
  list(
    rfit = rfit,
    hfit = hfit,
    hmu = as.data.frame(predict(hfit, simDataH2O))$predict,
    rmu = predict.glm(rfit, simData, type = "response"),
    hdispersion = hfit@model$dispersion,
    rdispersion = rdispersion
  )
}



test_helper <- function(p, phi, offset) {
  eps <- 1e-3

  simData <- generate_data(p, phi, offset)
  
  attach(train_models(simData, p, phi))
  
  # negative likelihood from H2O is roughly the same as calculated from predictions
  expect_true(abs(
    nll(
      simData,
      mu = hmu,
      p = p,
      phi = hdispersion
    ) - h2o.loglikelihood(hfit)
  ) < eps)
  
  cat("Difference in negative log-likelihood calculation between R and H2O: ", 
      abs(nll(simData, mu = hmu, p = p, phi = hdispersion) - h2o.loglikelihood(hfit)), "\n", sep="")
  
  # are we better than R's implementation or at least the same? smaller the negative likelihood the better
  hnll <- nll(
                simData,
                mu = hmu,
                p = p,
                phi = hdispersion
              )
 rnll <- nll(
               simData,
               mu = rmu,
               p = p,
               phi = rdispersion
             )
  expect_true(hnll <= rnll || abs(hnll - rnll) < eps)
  cat("H2O negative log-likelihood: ", hnll, "\n", "R negative log-likelihood: ", rnll, "\n",
      "H2O is better: ", hnll < rnll, "\n",
      "H2O and R are roughly similar: ", abs(hnll - rnll) < eps, "\n", sep=""
  )
  
  # check dispersion
  allowed_dispersion_difference <- 1.01*abs(phi - rdispersion)
  cat("Dispersion tolerance: ", allowed_dispersion_difference, "\n", sep="")
  cat("H2O Dispersion Estimation: ", hdispersion, "\nR Dispersion Estimation: ", rdispersion, "\n",
      "H2O is as close as R or closer to the true dispersion: ", abs(phi - hdispersion) < allowed_dispersion_difference,
       "\n", sep=""
  )
  expect_true(abs(phi - hdispersion) < allowed_dispersion_difference)
}


test_p1.6_phi100_no_offset <- function() {
  test_helper(1.6, 100, 0)
}

test_p1.6_phi100_with_offset <- function() {
  test_helper(1.6, 100, 1)
}

test_p1.6_phi10_no_offset <- function() {
  test_helper(1.6, 10, 0)
}

test_p1.6_phi10_with_offset <- function() {
  test_helper(1.6, 10, 1)
}

test_p1.6_phi1000_no_offset <- function() {
  test_helper(1.6, 1000, 0)
}

test_p1.6_phi1000_with_offset <- function() {
  test_helper(1.6, 1000, 1)
}

test_p1.5_phi100_no_offset <- function() {
  test_helper(1.5, 100, 0)
}

test_p1.5_phi100_with_offset <- function() {
  test_helper(1.5, 100, 1)
}

test_p1.5_phi10_no_offset <- function() {
  test_helper(1.5, 10, 0)
}

test_p1.5_phi10_with_offset <- function() {
  test_helper(1.5, 10, 1)
}

test_p1.5_phi1000_no_offset <- function() {
  test_helper(1.5, 1000, 0)
}

test_p1.5_phi1000_with_offset <- function() {
  test_helper(1.5, 1000, 1)
}

test_p1.7_phi100_no_offset <- function() {
  test_helper(1.7, 100, 0)
}

test_p1.7_phi100_with_offset <- function() {
  test_helper(1.7, 100, 1)
}

test_p1.7_phi10_no_offset <- function() {
  test_helper(1.7, 10, 0)
}

test_p1.7_phi10_with_offset <- function() {
  test_helper(1.7, 10, 1)
}

test_p1.7_phi1000_no_offset <- function() {
  test_helper(1.7, 1000, 0)
}

test_p1.7_phi1000_with_offset <- function() {
  test_helper(1.7, 1000, 1)
}


doSuite(
  "Tweedie Dispersion Estimation tests",
  makeSuite(
    test_p1.6_phi100_no_offset,
    test_p1.6_phi100_with_offset,
    test_p1.6_phi10_no_offset,
    test_p1.6_phi10_with_offset,
    test_p1.6_phi1000_no_offset,
    test_p1.6_phi1000_with_offset,
    test_p1.5_phi100_no_offset,
    test_p1.5_phi100_with_offset,
    test_p1.5_phi10_no_offset,
    test_p1.5_phi10_with_offset,
    test_p1.5_phi1000_no_offset,
    test_p1.5_phi1000_with_offset,
    test_p1.7_phi100_no_offset,
    test_p1.7_phi100_with_offset,
    test_p1.7_phi10_no_offset,
    test_p1.7_phi10_with_offset,
    test_p1.7_phi1000_no_offset,
    test_p1.7_phi1000_with_offset,
  )
)
