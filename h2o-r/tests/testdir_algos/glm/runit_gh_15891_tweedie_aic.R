setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test_tweedie_aic <- function() {
    require(statmod)
    require(tweedie)


    #h2o.init()
    set.seed(100)
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

    fitBase <- glm(yr~xt, family=tweedie(var.power=1.6, link.power=0), data=simData, weights = weight, offset=offset_col)
    print("Fitting with R")
    print(summary(fitBase)$AIC)
    print(summary(fitBase)$dispersion)
    AICtweedie(fitBase)
    
    ############################### fit R base glm ########################################
    fitBase <- glm(yr~xt, family=tweedie(var.power=1.6, link.power=0), data=simData, weights = weight, offset=offset_col)
    print("Fitting with R")
    print(summary(fitBase)$AIC)
    print(summary(fitBase)$dispersion)
    aic_tweedie = AICtweedie(fitBase)
    print(aic_tweedie)


    fitH2Otest2 <- h2o.glm(
        remove_collinear_columns=TRUE,
        training_frame = simDataH2O,
        x = 'xt',
        y = 'yr',
        family = "tweedie",
        link = "tweedie",
        tweedie_variance_power = tweedie_p,
        lambda = 0,
        compute_p_values = T,
        dispersion_parameter_method = "ml",
        calc_like=TRUE,
        fix_tweedie_variance_power = F,
        tweedie_link_power = 0
    )
    
    print("loglikelihood from function call")
    print(h2o.loglikelihood(fitH2Otest2, train=TRUE))
    print("AIC from function call")
    aic_h2o = h2o.aic(fitH2Otest2, train=TRUE)
    print(aic_h2o)

    # 139033.7, 139007.6
    expect_equal(aic_tweedie, aic_h2o, tolerance = aic_h2o * 1e-3)
    expect_true(aic_h2o > 0)
    print(aic_h2o * 1e-3)
   
}

doTest("GLM: Tweedie AIC test",
       test_tweedie_aic)
