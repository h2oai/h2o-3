from .estimator_base import H2OEstimator
from h2o.connection import H2OConnection

class H2OGeneralizedLinearEstimator(H2OEstimator):
  """Build a Generalized Linear Model
    Fit a generalized linear model, specified by a response variable, a set of predictors,
    and a description of the error distribution.

    Parameters
    ----------
      model_id : str, optional
        The unique id assigned to the resulting model. If none is given, an id will
        automatically be generated.

      ignore_const_cols : bool
        Ignore constant columns (no information can be gained anyway)

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
        gaussian, binomial, multinomial, poisson, gamma, tweedie.

      link : str
        A character string specifying the link function. The default is the canonical
        link for the family. The supported links for each of the family specifications are
        "gaussian" - "identity", "log", "inverse"
        "binomial" - "logit", "log"
        "multinomial" - "multinomial"
        "poisson" - "log", "identity"
        "gamma" - "inverse", "log", "identity"
        "tweedie" - "tweedie"

      tweedie_variance_power : int
        numeric specifying the power for the variance function when family = "tweedie". Default is 0.

      tweedie_link_power : int
        A numeric specifying the power for the link function when family = "tweedie". Default is 1.

      alpha : float
        A numeric in [0, 1] specifying the elastic-net mixing parameter.
        The elastic-net penalty is defined to be
        eqn{P(\alpha,\beta) = (1-\alpha)/2||\beta||_2^2 +
        \alpha||\beta||_1 = \sum_j [(1-\alpha)/2 \beta_j^2 + \alpha|\beta_j|],
        making alpha = 1 the lasso penalty and alpha = 0 the ridge penalty.

      Lambda : float
        A non-negative shrinkage parameter for the elastic-net, which multiplies
        \eqn{P(\alpha,\beta) in the objective function.
        When Lambda = 0, no elastic-net penalty is applied and ordinary generalized linear
        models are fit.

      prior : float, optional
        A numeric specifying the prior probability of class 1 in the response when
        family = "binomial". The default prior is the observational frequency of class 1.
        Must be from (0,1) exclusive range or None (no prior).

      lambda_search : bool
        A logical value indicating whether to conduct a search over the space of lambda
        values starting from the lambda max, given lambda is interpreted as lambda minself.

      early_stopping : bool
        A logical value indicating whether to stop early when doing lambda search.
        H2O will stop the computation at the moment when the likelihood stops changing or gets  (on the validation data).

      nlambdas : int
        The number of lambda values to use when lambda_search = TRUE.

      lambda_min_ratio : float
        Smallest value for lambda as a fraction of lambda.max. By default if the number of
        observations is greater than the the number of variables then
        lambda_min_ratio = 0.0001; if the number of observations is less than the number
        of variables then lambda_min_ratio = 0.01.

      beta_constraints : H2OFrame
        A data.frame or H2OParsedData object with the columns
        ["names", "lower_bounds", "upper_bounds", "beta_given"],
        where each row corresponds to a predictor in the GLM.
        "names" contains the predictor names, "lower"/"upper_bounds",
        are the lower and upper bounds of beta, and "beta_given" is some supplied starting
        values.

      nfolds : int, optional
        Number of folds for cross-validation. If nfolds >= 2, then validation must
        remain empty.

      seed : int, optional
        Specify the random number generator (RNG) seed for cross-validation folds.

      fold_assignment : str
        Cross-validation fold assignment scheme, if fold_column is not
        specified, must be "AUTO", "Random",  "Modulo", or "Stratified". 
        The Stratified option will stratify the folds based on the response 
        variable, for classification problems.

      keep_cross_validation_predictions : bool
        Whether to keep the predictions of the cross-validation models

      keep_cross_validation_fold_assignment : bool
        Whether to keep the cross-validation fold assignment.

      intercept : bool
        Logical, include constant term (intercept) in the model

      max_active_predictors : int, optional
        Convergence criteria for number of predictors when using L1 penalty.

      missing_values_handling : str
        A character string specifying how to handle missing value:
        "MeanImputation","Skip".

      interactions : list, optional
        A list of column names to interact. All pairwise combinations of columns will be
        interacted.
      max_runtime_secs: int, optional
        Maximum allowed runtime, model will stop running after reaching the limit and return whatever result it has at the moment.

    Returns
    -------
      A subclass of ModelBase is returned. The specific subclass depends on the machine
      learning task at hand (if it's binomial classification, then an H2OBinomialModel
      is returned, if it's regression then a H2ORegressionModel is returned). The default
      print-out of the models is shown, but further GLM-specifc information can be
      queried out of the object. Upon completion of the GLM, the resulting object has
      coefficients, normalized coefficients, residual/null deviance, aic, and a host of
      model metrics including MSE, AUC (for logistic regression), degrees of freedom, and
      confusion matrices.
    """
  def __init__(self, model_id=None,ignore_const_cols=None, max_iterations=None, beta_epsilon=None, solver=None,
               standardize=None, family=None, link=None, tweedie_variance_power=None,
               tweedie_link_power=None, alpha=None, prior=None, lambda_search=None, early_stopping=True,
               nlambdas=None, lambda_min_ratio=None, beta_constraints=None, nfolds=None,
               seed = None, fold_assignment=None, keep_cross_validation_predictions=None,
               keep_cross_validation_fold_assignment=None,
               intercept=None, Lambda=None, max_active_predictors=None, checkpoint=None,
               objective_epsilon=None, gradient_epsilon=None, non_negative=False,
               compute_p_values=False, remove_collinear_columns=False,
               missing_values_handling=None, interactions=None, max_runtime_secs = 0):
    super(H2OGeneralizedLinearEstimator, self).__init__()
    self._parms = locals()
    self._parms = {k: v for k, v in self._parms.items() if k != "self"}
    self._parms["lambda"] = self._parms.pop("Lambda")

  @property
  def max_iterations(self):
    return self._parms["max_iterations"]

  @max_iterations.setter
  def max_iterations(self, value):
    self._parms["max_iterations"] = value

  @property
  def beta_epsilon(self):
    return self._parms["beta_epsilon"]

  @beta_epsilon.setter
  def beta_epsilon(self, value):
    self._parms["beta_epsilon"] = value

  @property
  def solver(self):
    return self._parms["solver"]

  @solver.setter
  def solver(self, value):
    self._parms["solver"] = value

  @property
  def standardize(self):
    return self._parms["standardize"]

  @standardize.setter
  def standardize(self, value):
    self._parms["standardize"] = value

  @property
  def family(self):
    return self._parms["family"]

  @family.setter
  def family(self, value):
    self._parms["family"] = value

  @property
  def link(self):
    return self._parms["link"]

  @link.setter
  def link(self, value):
    self._parms["link"] = value

  @property
  def tweedie_variance_power(self):
    return self._parms["tweedie_variance_power"]

  @tweedie_variance_power.setter
  def tweedie_variance_power(self, value):
    self._parms["tweedie_variance_power"] = value

  @property
  def tweedie_link_power(self):
    return self._parms["tweedie_link_power"]

  @tweedie_link_power.setter
  def tweedie_link_power(self, value):
    self._parms["tweedie_link_power"] = value

  @property
  def alpha(self):
    return self._parms["alpha"]

  @alpha.setter
  def alpha(self, value):
    self._parms["alpha"] = value

  @property
  def prior(self):
    return self._parms["prior"]

  @prior.setter
  def prior(self, value):
    self._parms["prior"] = value

  @property
  def lambda_search(self):
    return self._parms["lambda_search"]

  @lambda_search.setter
  def lambda_search(self, value):
    self._parms["lambda_search"] = value

  @property
  def early_stopping(self):
    return self._parms["early_stopping"]

  @early_stopping.setter
  def early_stopping(self, value):
    self._parms["early_stopping"] = value

  @property
  def nlambdas(self):
    return self._parms["nlambdas"]

  @nlambdas.setter
  def nlambdas(self, value):
    self._parms["nlambdas"] = value

  @property
  def lambda_min_ratio(self):
    return self._parms["lambda_min_ratio"]

  @lambda_min_ratio.setter
  def lambda_min_ratio(self, value):
    self._parms["lambda_min_ratio"] = value

  @property
  def beta_constraints(self):
    return self._parms["beta_constraints"]

  @beta_constraints.setter
  def beta_constraints(self, value):
    self._parms["beta_constraints"] = value

  @property
  def nfolds(self):
    return self._parms["nfolds"]

  @nfolds.setter
  def nfolds(self, value):
    self._parms["nfolds"] = value

  @property
  def fold_assignment(self):
    return self._parms["fold_assignment"]

  @fold_assignment.setter
  def fold_assignment(self, value):
    self._parms["fold_assignment"] = value

  @property
  def keep_cross_validation_predictions(self):
    return self._parms["keep_cross_validation_predictions"]

  @keep_cross_validation_predictions.setter
  def keep_cross_validation_predictions(self, value):
    self._parms["keep_cross_validation_predictions"] = value

  @property
  def keep_cross_validation_fold_assignment(self):
    return self._parms["keep_cross_validation_fold_assignment"]

  @keep_cross_validation_fold_assignment.setter
  def keep_cross_validation_fold_assignment(self, value):
    self._parms["keep_cross_validation_fold_assignment"] = value

  @property
  def intercept(self):
    return self._parms["intercept"]

  @intercept.setter
  def intercept(self, value):
    self._parms["intercept"] = value

  @property
  def Lambda(self):
    return self._parms["Lambda"]

  @Lambda.setter
  def Lambda(self, value):
    self._parms["Lambda"] = value

  @property
  def max_active_predictors(self):
    return self._parms["max_active_predictors"]

  @max_active_predictors.setter
  def max_active_predictors(self, value):
    self._parms["max_active_predictors"] = value

  @property
  def checkpoint(self):
    return self._parms["checkpoint"]

  @checkpoint.setter
  def checkpoint(self, value):
    self._parms["checkpoint"] = value

  @property
  def objective_epsilon(self):
    return self._parms["objective_epsilon"]

  @objective_epsilon.setter
  def objective_epsilon(self, value):
    self._parms["objective_epsilon"] = value

  @property
  def gradient_epsilon(self):
    return self._parms["gradient_epsilon"]  
    
  @gradient_epsilon.setter
  def gradient_epsilon(self, value):
    self._parms["gradient_epsilon"] = value

  @property
  def non_negative(self):
    return self._parms["non_negative"]

  @non_negative.setter
  def non_negative(self, value):
    self._parms["non_negative"] = value

  @property
  def compute_p_values(self):
    return self._parms["compute_p_values"]

  @compute_p_values.setter
  def compute_p_values(self, value):
    self._parms["compute_p_values"] = value

  @property
  def remove_collinear_columns(self):
    return self._parms["remove_collinear_columns"]

  @remove_collinear_columns.setter
  def remove_collinear_columns(self, value):
    self._parms["remove_collinear_columns"] = value

  @property
  def missing_values_handling(self):
    return self._parms["missing_values_handling"]

  @missing_values_handling.setter
  def missing_values_handling(self, value):
    self._parms["missing_values_handling"] = value

  @property
  def ignore_const_cols(self):
    return self._parms["ignore_const_cols"]

  @ignore_const_cols.setter
  def ignore_const_cols(self, value):
    self._parms["ignore_const_cols"] = value

  @property
  def seed(self):
    return self._parms["seed"]

  @seed.setter
  def seed(self, value):
    self._parms["seed"] = value

  """
  Extract full regularization path explored during lambda search from glm model.

  Parameters:

  model - source lambda search model

  """
  @staticmethod
  def getGLMRegularizationPath(model):
    x = H2OConnection.get_json("GetGLMRegPath",model=model._model_json['model_id']['name'])
    ns = x.pop('coefficient_names')
    res = {'lambdas':x['lambdas'],'explained_deviance_train':x['explained_deviance_train'],'explained_deviance_valid':x['explained_deviance_valid']}
    res['coefficients'] = [dict(zip(ns,y)) for y in x['coefficients']]
    if 'coefficients_std' in x:
      res['coefficients_std'] = [dict(zip(ns,y)) for y in x['coefficients_std']]
    return res

  """
  Create a custom GLM model using the given coefficients.
  Needs to be passed source model trained on the dataset to extract the dataset information from.

  Parameters:
    model - source model, used for extracting dataset information
    coefs - dictionary containing model coefficients
    threshold - (optional, only for binomial) decision threshold used for classification

  """
  @staticmethod
  def makeGLMModel(model, coefs, threshold=.5):
    model_json = H2OConnection.post_json("MakeGLMModel",model=model._model_json['model_id']['name'], names=list(coefs.keys()), beta = list(coefs.values()), threshold = threshold)
    m = H2OGeneralizedLinearEstimator()
    m._resolve_model(model_json['model_id']['name'], model_json)
    return m

