setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


seed <- 9803190

`%or%` <- function(x, y) {
  if (is.null(x)) y else x
}


.make_data <- function(nrows) {
  set.seed(seed)
  a <- sample(rep_len(c(0, 0.7333), nrows))
  b <- 2 * a**.01 + 3 + 8 * runif(nrows)
  d <- rep_len(1:8, nrows)
  c <- 3 * d - 2 + runif(nrows)
  e <- rep_len(c("class 0", "class A"), nrows)
  f <- rep_len(c("class 0", "class A", "class alpha", "class aleph"), nrows)

  as.h2o(data.frame(
    quasibinomial = a,
    gaussian = b,
    noise = c,
    ordinal = d,
    binomial = as.factor(e),
    multinomial = as.factor(f),
    ordinal_factors = as.factor(d)
  ))
}


Distribution.binomial <- 'bernoulli'
Distribution.multinomial <- 'multinomial'
Distribution.gaussian <- 'gaussian'
Distribution.poisson <- 'poisson'
Distribution.gamma <- 'gamma'
Distribution.laplace <- 'laplace'
Distribution.quantile <- function(alpha) list(distribution = "quantile", quantile_alpha = alpha)
Distribution.huber <- function(alpha) list(distribution = "huber", huber_alpha = alpha)
Distribution.tweedie <- function(power) list(distribution = "tweedie", tweedie_power = power)
Distribution.ordinal <- 'ordinal'


automl.distributions.tests <- function() {
  scenarios <- list(
    list(response = "binomial", distribution = Distribution.binomial,
         algos = c('DRF', 'DeepLearning', 'GBM', 'GLM', 'StackedEnsemble', 'XGBoost'), max_models = 12),
    list(response = "quasibinomial", distribution = "quasibinomial", algos = c('GBM', 'GLM', 'StackedEnsemble'),
         max_models = 17, fail = TRUE),  # needed to be able to build SE
    list(response = "quasibinomial", distribution = "fractionalbinomial", algos = c('GLM'), fail = TRUE),
    list(response = "multinomial", distribution = Distribution.multinomial,
         algos = c('DRF', 'DeepLearning', 'GBM', 'GLM', 'StackedEnsemble', 'XGBoost'), max_models = 12),
    list(response = "gaussian", distribution = Distribution.gaussian,
         algos = c('DRF', 'DeepLearning', 'GBM', 'GLM', 'StackedEnsemble', 'XGBoost'), max_models = 12),
    list(response = "ordinal", distribution = Distribution.poisson,
         algos = c('DeepLearning', 'GBM', 'GLM', 'StackedEnsemble', 'XGBoost'), nrows = 400),
    list(response = "gaussian", distribution = Distribution.gamma,
         algos = c('DeepLearning', 'GBM', 'GLM', 'StackedEnsemble', 'XGBoost'), nrows = 400),
    list(response = "gaussian", distribution = Distribution.laplace, algos = c('DeepLearning', 'GBM')),
    list(response = "gaussian", distribution = Distribution.quantile(0.25), algos = c('DeepLearning', 'GBM')),
    list(response = "gaussian", distribution = Distribution.huber(.3),
         algos = c('DeepLearning', 'GBM'), max_models = 12),
    list(response = "gaussian", distribution = Distribution.tweedie(1.5),
         algos = c('DeepLearning', 'GBM', 'GLM', 'StackedEnsemble', 'XGBoost')),
    list(response = "ordinal_factors", distribution = Distribution.ordinal, algos = c(), fail = TRUE)
    # list(response = "gaussian", distribution = "custom", algos = c("GBM")),
    # list(response = "gaussian", distribution = "custom2", algos = c("GBM"))
  )

  all_algos <- c('DeepLearning', "DRF", 'GBM', 'GLM', 'StackedEnsemble', 'XGBoost')

  do.call(makeSuite, lapply(scenarios, function(scenario) {
    distribution <- scenario$distribution
    if (is.list(distribution)) distribution <- distribution$distribution
    assign(paste0("test_", distribution), function() {

      cat("\n", distribution, "\n", rep_len("=", nchar(distribution)), "\n", sep = "")
      h2o.removeAll()
      train <- .make_data(scenario$nrows %or% 264)
      aml <- tryCatch(h2o.automl(y = scenario$response,
                                 distribution = scenario$distribution,
                                 seed = seed,
                                 max_runtime_secs_per_model = 10,
                                 max_models = scenario$max_models %or% 12,
                                 training_frame = train,
                                 verbosity = NULL
      ),
                      error = function(e) e
      )
      if ("error" %in% class(aml)) {
        expect_true(scenario$fail)
        return()
      }

      # All algos should be used but only supported ones use the specified distribution
      expect_true(length(setdiff(tolower(all_algos), tolower(unlist(as.list(h2o.get_leaderboard(aml, "algo")$algo))))) == 0)

      for (model_id in unlist(as.list(aml@leaderboard$model_id))) {
        model <- h2o.getModel(model_id)

        if (model@algorithm == "stackedensemble") {
          model <- model@model$metalearner_model
        }
        distr <- model@parameters$distribution %or% model@parameters$family

        if (distr == "binomial") {
          distr <- "bernoulli"
        }
        if (model@algorithm %in% tolower(scenario$algos)) {
          expect_equal(distr, distribution)
        } else {
          expect_true(distr != distribution)
        }
      }
    }, envir = parent.frame(2))
    as.name(paste0("test_", distribution))
  }))
}


doSuite("AutoML distributions Test", automl.distributions.tests())
