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
h2o.predict(object, newdata)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{object}{
A fitted \code{\linkS4class{H2OModel}} or \code{\linkS4class{H2OModelVA}} object for which prediction is desired.
}
  \item{newdata}{
(Optional) A \code{\linkS4class{H2OParsedData}} or \code{\linkS4class{H2OParsedDataVA}} object in which to look for variables with which to predict. If omitted, the data used to fit the model \code{object@data} are used.
}
}
\details{
This method dispatches on the type of H2O model to select the correct prediction/scoring algorithm. 
}
\value{
A \code{\linkS4class{H2OParsedData}} or \code{\linkS4class{H2OParsedDataVA}} object containing the predictions.
}
\seealso{
%% ~~objects to See Also as \code{\link{help}}, ~~~
\code{\link{h2o.glm}, \link{h2o.kmeans}, \link{h2o.randomForest}, \link{h2o.prcomp}, \link{h2o.gbm}, \link{h2o.deeplearning}}
}
\examples{
library(h2o)
localH2O = h2o.init(ip = "localhost", port = 54321, startH2O = TRUE)
# Run GLM of CAPSULE ~ AGE + RACE + PSA + DCAPS
prostate.hex = h2o.importURL.VA(localH2O, path = paste("https://raw.github.com", 
  "0xdata/h2o/master/smalldata/logreg/prostate.csv", sep = "/"), key = "prostate.hex")
prostate.glm = h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), data = prostate.hex, 
   family = "binomial", nfolds = 10, alpha = 0.5)
# Get fitted values of prostate dataset
prostate.fit = h2o.predict(object = prostate.glm, newdata = prostate.hex)
summary(prostate.fit)
h2o.shutdown(localH2O)
}
% Add one or more standard keywords, see file 'KEYWORDS' in the
% R documentation directory.
\keyword{ ~kwd1 }
\keyword{ ~kwd2 }% __ONLY ONE__ keyword per line
