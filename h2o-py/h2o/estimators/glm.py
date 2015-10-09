from .estimator_base import H2OEstimator


class H2OGLMEstimator(H2OEstimator):
  def __init__(self, model_id=None, max_iterations=None, beta_epsilon=None, solver=None,
               standardize=None, family=None, link=None, tweedie_variance_power=None,
               tweedie_link_power=None, alpha=None, prior=None, lambda_search=None,
               nlambdas=None, lambda_min_ratio=None, beta_constraints=None, nfolds=None,
               fold_assignment=None, keep_cross_validation_predictions=None,
               intercept=None, Lambda=None, max_active_predictors=None, checkpoint=None):
    """
    Build a Generalized Linear Model
    Fit a generalized linear model, specified by a response variable, a set of predictors, and a description of the error
    distribution.

    Parameters
    ----------
    model_id : str, optional
      The unique id assigned to the resulting model. If none is given, an id will
      automatically be generated.
    max_iterations : int
      A non-negative integer specifying the maximum number of iterations.
    beta_epsilon : int
      A non-negative number specifying the magnitude of the maximum difference between
      the coefficient estimates from successive iterations. Defines the convergence
      criterion.
    solver : str
      A character string specifying the solver used: IRLSM (supports more features),
      L_BFGS (scales better for datasets with many columns)
    standardize : bool
      Indicates whether the numeric predictors should be standardized to have a mean of
      0 and a variance of 1 prior to training the models.
    family : str
      A character string specifying the distribution of the model:
     gaussian, binomial, poisson, gamma, tweedie.
    link : str
      A character string specifying the link function.
      The default is the canonical link for the family.
      The supported links for each of the family specifications are:
          "gaussian": "identity", "log", "inverse"
          "binomial": "logit", "log"
          "poisson": "log", "identity"
          "gamma": "inverse", "log", "identity"
          "tweedie": "tweedie"

    tweedie_variance_power : int
      numeric specifying the power for the variance function when family = "tweedie".
    tweedie_link_power : int
      A numeric specifying the power for the link function when family = "tweedie".
    alpha : float
      A numeric in [0, 1] specifying the elastic-net mixing parameter.

      The elastic-net penalty is defined to be:
      eqn{P(\alpha,\beta) = (1-\alpha)/2||\beta||_2^2 + \alpha||\beta||_1 = \sum_j [(1-\alpha)/2 \beta_j^2 + \alpha|\beta_j|],
      making alpha = 1 the lasso penalty and alpha = 0 the ridge penalty.

    Lambda : float
      A non-negative shrinkage parameter for the elastic-net, which multiplies \eqn{P(\alpha,\beta) in the objective function.
      When Lambda = 0, no elastic-net penalty is applied and ordinary generalized linear models are fit.
    prior : float, optional
      A numeric specifying the prior probability of class 1 in the response when family = "binomial". The default prior is the observational frequency of class 1.
    lambda_search : bool
      A logical value indicating whether to conduct a search over the space of lambda values starting from the lambda max, given lambda is interpreted as lambda minself.
    nlambdas : int
      The number of lambda values to use when lambda_search = TRUE.
    lambda_min_ratio : float
      Smallest value for lambda as a fraction of lambda.max. By default if the number of observations is greater than the the number of
      variables then lambda_min_ratio = 0.0001; if the number of observations is less than the number of variables then lambda_min_ratio = 0.01.
    beta_constraints : H2OFrame
      A data.frame or H2OParsedData object with the columns ["names", "lower_bounds", "upper_bounds", "beta_given"], where each row corresponds to a predictor
      in the GLM. "names" contains the predictor names, "lower"/"upper_bounds", are the lower and upper bounds of beta, and "beta_given" is some supplied starting
      values for the
    nfolds : int, optional
      Number of folds for cross-validation. If nfolds >= 2, then validation must remain empty.
    fold_assignment : str
      Cross-validation fold assignment scheme, if fold_column is not specified Must be "AUTO", "Random" or "Modulo"
    keep_cross_validation_predictions : bool
      Whether to keep the predictions of the cross-validation models
    intercept : bool
      Logical, include constant term (intercept) in the model
    max_active_predictors : int, optional
      Convergence criteria for number of predictors when using L1 penalty.

    Returns
    -------
      A subclass of ModelBase is returned. The specific subclass depends on the machine learning task at hand (if
      it's binomial classification, then an H2OBinomialModel is returned, if it's regression then a H2ORegressionModel is
      returned). The default print-out of the models is shown, but further GLM-specifc information can be queried out of
      the object.
      Upon completion of the GLM, the resulting object has coefficients, normalized coefficients, residual/null deviance,
      aic, and a host of model metrics including MSE, AUC (for logistic regression), degrees of freedom, and confusion
      matrices.
    """
    super(H2OGLMEstimator, self).__init__()
    self.parms = locals()
    self.parms = {k:v for k,v in self.parms.iteritems() if k!="self"}
    self.parms["algo"] = "glm"