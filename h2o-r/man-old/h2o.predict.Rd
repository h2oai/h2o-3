\name{h2o.predict}
\alias{h2o.predict}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
H2O Model Predictions
}
\description{
Obtains predictions from various fitted H2O model objects.
}
\usage{
h2o.predict(object, newdata, \dots)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{object}{
  A fitted \code{\linkS4class{H2OModel}} object for which prediction is desired.
  }
  \item{newdata}{
(Optional) A \code{\linkS4class{H2OParsedData}} object in which to look for variables with which to predict. If omitted, the data used to fit the model \code{object@data} are used.}
  \item{\dots}{
  Additional arguments to pass to h2o.predict. In particular variable num_pc for predicting on \code{\linkS4class{H2OPCAModel}} object is implemented.
  }
}

\details{
This method dispatches on the type of H2O model to select the correct prediction/scoring algorithm. 
}
\value{
A \code{\linkS4class{H2OParsedData}} object containing the predictions.
}
\seealso{
%% ~~objects to See Also as \code{\link{help}}, ~~~
\code{\link{h2o.glm}, \link{h2o.kmeans}, \link{h2o.randomForest}, \link{h2o.prcomp}, \link{h2o.gbm}, \link{h2o.deeplearning}}
}
\examples{
library(h2o)
localH2O = h2o.init()
# Run GLM of CAPSULE ~ AGE + RACE + PSA + DCAPS
prostatePath = system.file("extdata", "prostate.csv", package = "h2o")
prostate.hex = h2o.importFile(localH2O, path = prostatePath, key = "prostate.hex")
prostate.glm = h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), data = prostate.hex, 
   family = "binomial", nfolds = 0, alpha = 0.5)
# Get fitted values of prostate dataset
prostate.fit = h2o.predict(object = prostate.glm, newdata = prostate.hex)
summary(prostate.fit)
}
