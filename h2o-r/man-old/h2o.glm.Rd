\name{h2o.glm}
\alias{h2o.glm}
\title{
H2O: Generalized Linear Models
}
\description{
Fit a generalized linear model, specified by a response variable, a set of predictors, and a description of the error distribution.
}
\usage{
h2o.glm(x, y, data, key = "", family, link, nfolds = 0, alpha = 0.5, nlambda = -1, 
  lambda.min.ratio = -1, lambda = 1e-5, epsilon = 1e-4, standardize = TRUE, 
  prior, variable_importances = FALSE, use_all_factor_levels = FALSE, tweedie.p =
  ifelse(family == 'tweedie', 1.5, as.numeric(NA)), iter.max = 100, 
  higher_accuracy = FALSE, lambda_search = FALSE, return_all_lambda = FALSE, 
  max_predictors = -1, offset, has_intercept = TRUE, non_negative = FALSE)
}
\arguments{
  \item{x}{
A vector containing the names of the predictors in the model.
}
  \item{y}{
The name of the response variable in the model.
}
  \item{data}{
An \code{\linkS4class{H2OParsedData}} object containing the variables in the model.
}
  \item{key}{
(Optional) The unique hex key assigned to the resulting model. If none is given, a key will automatically be generated.
}
  \item{family}{
A description of the error distribution and corresponding link function to be used in the model. Currently, Gaussian, binomial, Poisson, gamma, and Tweedie are supported. When a model is specified as Tweedie, users must also specify the appropriate Tweedie power. 
}
  \item{link}{
(Optional) The link function relates the linear predictor to the distribution function. Default is the canonical link for the specified family. The full list of supported links: \cr
\cr
gaussian: identity, log, inverse \cr
binomial: logit, log \cr
poisson: log, identity \cr
gamma: inverse, log, identity \cr
tweedie: tweedie \cr
}
  \item{nfolds}{
(Optional) Number of folds for cross-validation.
}
  \item{alpha}{
(Optional) The elastic-net mixing parameter, which must be in [0,1]. The penalty is defined to be \deqn{P(\alpha,\beta) = (1-\alpha)/2||\beta||_2^2 + \alpha||\beta||_1 = \sum_j [(1-\alpha)/2 \beta_j^2 + \alpha|\beta_j|]} so \code{alpha=1} is the lasso penalty, while \code{alpha=0} is the ridge penalty.
}
  \item{nlambda}{
  The number of \code{lambda} values when performing a search.
  }
  \item{lambda.min.ratio}{
  Smallest value for \code{lambda} as a fraction of \code{lambda.max}, the entry value, which is the smallest value for which all coefficients in the model are zero. 
  }
  \item{lambda}{The shrinkage parameter, which multiplies \eqn{P(\alpha,\beta)} in the objective. The larger \code{lambda} is, the more the coefficients are shrunk toward zero (and each other).
}
  \item{epsilon}{
(Optional) Number indicating the cutoff for determining if a coefficient is zero.
  }
  \item{standardize}{
(Optional) Logical value indicating whether the data should be standardized (set to mean = 0, variance = 1) before running GLM.
  }
  \item{prior}{
(Optional) Prior probability of class 1. Only used if \code{family = "binomial"}. When omitted, prior will default to the frequency of class 1 in the response column.
  }
  \item{variable_importances}{
  (Optional) A logical value either TRUE or FALSE to indicate whether the variable importances should be computed.  Compute variable importances for input features. NOTE: If use_all_factor_levels is off the importance of the base level will NOT be shown.
  }
  \item{use_all_factor_levels}{
  (Optional) A logical value either TRUE or FALSE to indicate whether all factor levels should be used. By default, first factor level is skipped from the possible set of predictors. Set this flag if you want use all of the levels. Needs sufficient regularization to solve!
  }
  \item{tweedie.p}{
  (Optional) The index of the power variance function for the tweedie distribution. Only used if \code{family = "tweedie"}.
}
  \item{iter.max}{
  (Optional) Maximum number of iterations allowed.
  }
  \item{higher_accuracy}{
  (Optional) A logical value indicating whether to use line search. This will cause the algorithm to run slower, so generally, it should only be set to TRUE if GLM does not converge otherwise.
  }
  \item{lambda_search}{
  (Optional) A logical value indicating whether to conduct a search over the space of lambda values, starting from lambda_max. When this is set to TRUE, \code{lambda} will be interpreted as lambda_min.
  }
  \item{return_all_lambda}{
  (Optional) A logical value indicating whether to return every model built during the lambda search. Only used if \code{lambda_search = TRUE}. If \code{return_all_lambda = FALSE}, then only the model corresponding to the optimal lambda will be returned.
  }
  \item{max_predictors}{
  (Optional) When \code{lambda_search = TRUE}, the algorithm will stop training if the number of predictors exceeds this value. Ignored when \code{lambda_search = FALSE} or \code{max_predictors = -1}.
  }
  \item{offset}{(Optional) Column to be used as an offset, if you have one. }
  \item{has_intercept}{ A logical value indicating whether or not to include the intercept term. If there are factor columns in your model, then the intercept must be included. }
  \item{non_negative}{ A logical value indicating whether or not coefficients will be restricted to only non-negative values.}
}
\value{
An object of class \code{\linkS4class{H2OGLMModel}} with slots key, data, model and xval. The slot model is a list of the following components:
\item{coefficients }{A named vector of the coefficients estimated in the model.}
\item{rank }{The numeric rank of the fitted linear model.}
\item{family }{The family of the error distribution.}
\item{deviance }{The deviance of the fitted model.}
\item{aic }{Akaike's Information Criterion for the final computed model.}
\item{null.deviance }{The deviance for the null model.}
\item{iter }{Number of algorithm iterations to compute the model.}
\item{df.residual }{The residual degrees of freedom.}
\item{df.null }{The residual degrees of freedom for the null model.}
\item{y }{The response variable in the model.}
\item{x }{A vector of the predictor variable(s) in the model.}
\item{auc }{Area under the curve.}
\item{training.err }{Average training error.}
\item{threshold }{Best threshold.}
\item{confusion }{Confusion matrix.}
The slot xval is a list of \code{\linkS4class{H2OGLMModel}} objects representing the cross-validation models. (Each of these objects themselves has xval equal to an empty list).
}
\seealso{
%% ~~objects to See Also as \code{\link{help}}, ~~~
\code{\link{h2o.importFile}, \link{h2o.importFolder}, \link{h2o.importHDFS}, \link{h2o.importURL}, \link{h2o.uploadFile}}
}
\examples{
# -- CRAN examples begin --
library(h2o)
localH2O = h2o.init()

# Run GLM of CAPSULE ~ AGE + RACE + PSA + DCAPS
prostatePath = system.file("extdata", "prostate.csv", package = "h2o")
prostate.hex = h2o.importFile(localH2O, path = prostatePath, key = "prostate.hex")
h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), data = prostate.hex, 
        family = "binomial", nfolds = 0, alpha = 0.5, lambda_search = FALSE, 
        use_all_factor_levels = FALSE, variable_importances = FALSE, higher_accuracy = FALSE)

# Run GLM of VOL ~ CAPSULE + AGE + RACE + PSA + GLEASON
myX = setdiff(colnames(prostate.hex), c("ID", "DPROS", "DCAPS", "VOL"))
h2o.glm(y = "VOL", x = myX, data = prostate.hex, family = "gaussian", nfolds = 0, alpha = 0.1,
        lambda_search = FALSE, use_all_factor_levels = FALSE, variable_importances = FALSE, 
        higher_accuracy = FALSE)
# -- CRAN examples end --

\dontrun{
# GLM variable importance
# Also see:
#   https://github.com/0xdata/h2o/blob/master/R/tests/testdir_demos/runit_demo_VI_all_algos.R
data.hex = h2o.importFile(
  localH2O,
  path = "https://raw.github.com/0xdata/h2o/master/smalldata/bank-additional-full.csv",
  key = "data.hex")
myX = 1:20
myY="y"
my.glm = h2o.glm(x=myX, y=myY, data=data.hex, family="binomial",standardize=T,
                 use_all_factor_levels=T,higher_accuracy=T,lambda_search=T,
                 return_all_lambda=T,variable_importances=T)
best_model = my.glm@best_model
n_coeff = abs(my.glm@models[[best_model]]@model$normalized_coefficients)
VI = abs(n_coeff[-length(n_coeff)])
glm.VI = VI[order(VI,decreasing=T)]
print(glm.VI)
}
}
