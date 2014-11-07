\name{h2o.pcr}
\alias{h2o.pcr}

\title{H2O: Principal Components Regression}

\description{
Runs GLM regression on PCA results, and allows for transformation of test data to match PCA transformations of training data.  
}
\usage{
h2o.pcr(x, y, data, key = "", ncomp, family, nfolds = 10, alpha = 0.5, lambda = 1e-05, 
  epsilon = 1e-05, tweedie.p)
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
  \item{ncomp}{
    A number indicating the number of principal components to use in the regression model.
  }
  \item{family}{
    A description of the error distribution and corresponding link function to be used in the model. Currently, Gaussian, binomial, Poisson, gamma, and Tweedie are supported.
  }
  \item{nfolds}{
    (Optional) Number of folds for cross-validation. The default is 10.
  }
  \item{alpha}{
    (Optional) The elastic-net mixing parameter, which must be in [0,1]. The penalty is defined to be \deqn{P(\alpha,\beta) = (1-\alpha)/2||\beta||_2^2 + \alpha||\beta||_1 = \sum_j [(1-\alpha)/2 \beta_j^2 + \alpha|\beta_j|]} so \code{alpha=1} is the lasso penalty, while \code{alpha=0} is the ridge penalty.
  }
  \item{lambda}{
    (Optional) The shrinkage parameter, which multiples \eqn{P(\alpha,\beta)} in the objective. The larger \code{lambda} is, the more the coefficients are shrunk toward zero (and each other).
  }
  \item{epsilon}{
  (Optional) Number indicating the cutoff for determining if a coefficient is zero.
  }
  \item{tweedie.p}{The index of the power variance function for the tweedie distribution. Only used if \code{family = "tweedie"}
  }
}

\details{
This method standardizes the data, obtains the first \code{ncomp} principal components using PCA (in decreasing order of standard deviation), and then runs GLM with the components as the predictor variables. 
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
\code{\link{h2o.prcomp}, \link{h2o.glm}}
}
\examples{
\dontrun{
library(h2o)
localH2O = h2o.init()

# Run PCR on Prostate Data
prostate.hex = h2o.importURL(localH2O, path = paste("https://raw.github.com", 
  "0xdata/h2o/master/smalldata/logreg/prostate.csv", sep = "/"), key = "prostate.hex")
h2o.pcr(x = c("AGE","RACE","PSA","DCAPS"), y = "CAPSULE", data = prostate.hex, family = "binomial", 
  nfolds = 0, alpha = 0.5, ncomp = 2)
}
}
