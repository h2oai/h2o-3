setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test_tweedie_dispersion_parameters <- function() {
  require(statmod)
  require(tweedie)
  
  
  #h2o.init()
  #set.seed(89)
  # simulating data with log link and Tweedie distribution with known dispersion parameter and tweedie power
  tweedie_p <- 1.6
  molsp <- 1000
  x <- seq(1, 10, 1)
  yd <- exp(1 + 1.015 * x)
  phi = 2
  simData <- matrix(0, nrow = molsp * 10, ncol = 5)
  colnames(simData) <-
    c('xt', 'yt', 'yr', 'weight', 'offset_col')
  for (i in 1:length(x)) {
    simData[((i - 1) * molsp + 1):(i * molsp), 1] <- x[i]
    simData[((i - 1) * molsp + 1):(i * molsp), 2] <- yd[i]
    simData[((i - 1) * molsp + 1):(i * molsp), 3] <-
      tweedie::rtweedie(molsp,
                        xi = tweedie_p,
                        mu = yd[i],
                        phi = phi)
    simData[((i - 1) * molsp + 1):(i * molsp), 4] <- 1
    simData[((i - 1) * molsp + 1):(i * molsp), 5] <- 1
  }
  simDataH2O <- as.h2o(simData)
  simData <- as.data.frame(simData)
  #  simDataH2O <- h2o.importFile("/Users/wendycwong/temp/simDataH2O.csv")
  std_ind <- T
  int_ind <- T
  p_valueAIC = T
  fullLikelihoodAIC = T
  
  ############################### fit R base glm ########################################
   fitBase <- glm(yr~xt, family=tweedie(var.power=1.6, link.power=0), data=simData, weights = weight, offset=offset_col)
  print("Fitting with R")
   print(summary(fitBase)$AIC)
   print(summary(fitBase)$dispersion)
   AICtweedie(fitBase)
  
  
  ###### different dispersion methods using h2o.glm ######
  
  ### deviance ###
  fitH2Otest1 <- h2o.glm(
    remove_collinear_columns=TRUE,
    training_frame = simDataH2O,
    x = 'xt',
    y = 'yr',
    family = "tweedie",
    link = "tweedie",
    tweedie_variance_power = tweedie_p,
    lambda = 0,
    compute_p_values = p_valueAIC,
    calc_like=TRUE,
    dispersion_parameter_method = "deviance"
  ) # options: deviance; ml; pearson
  
   print("deviance dispersion parameter")
  dispersiontest1 <- fitH2Otest1@model$dispersion
  print(dispersiontest1)
  print("loglikelihood from function call")
  print(h2o.loglikelihood(fitH2Otest1, train=TRUE))
  print("AIC from function call")
  print(h2o.aic(fitH2Otest1, train=TRUE))
  
  ### ml ###
  fitH2Otest2 <- h2o.glm(
    remove_collinear_columns=TRUE,
    training_frame = simDataH2O,
    x = 'xt',
    y = 'yr',
    family = "tweedie",
    link = "tweedie",
    tweedie_variance_power = tweedie_p,
    lambda = 0,
    compute_p_values = p_valueAIC,
    dispersion_parameter_method = "ml",
    calc_like=TRUE
  ) # options: deviance; ml; pearson
  
  print("ml with fixed variance power dispersion parameter")
  dispersiontest2 <- fitH2Otest2@model$dispersion
  print(dispersiontest2)
  print("loglikelihood from function call")
  print(h2o.loglikelihood(fitH2Otest2, train=TRUE))
  print("AIC from function call")
  print(h2o.aic(fitH2Otest2, train=TRUE))
  
  ###### different dispersion methods ######
  
  ### pearson ###
  fitH2Otest3 <- h2o.glm(
    remove_collinear_columns=TRUE,
    training_frame = simDataH2O,
    x = 'xt',
    y = 'yr',
    family = "tweedie",
    link = "tweedie",
    tweedie_variance_power = tweedie_p,
    lambda = 0,
    compute_p_values = p_valueAIC,
    dispersion_parameter_method = "pearson",
    calc_like = TRUE
  ) # options: deviance; ml; pearson
  
  print("Pearson dispersion parameter")
  dispersiontest3 <- fitH2Otest3@model$dispersion
  print(dispersiontest3)
  print("loglikelihood from function call")
  print(h2o.loglikelihood(fitH2Otest3, train=TRUE))
  print("AIC from function call")
  print(h2o.aic(fitH2Otest3, train=TRUE))
}

doTest("GLM: Tweedie dispersion parameters test",
       test_tweedie_dispersion_parameters)
