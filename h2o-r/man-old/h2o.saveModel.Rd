\name{h2o.saveModel}
\alias{h2o.saveModel}
\title{Save a H2OModel object to disk.}
\description{Save a H2OModel object to a disk and can be loaded back into H2O using h2o.loadModel.}
\usage{h2o.saveModel(object, dir="", name="", save_cv = TRUE, force=FALSE)}

\arguments{
  \item{object}{ An \code{\linkS4class{H2OModel}} object.}
  \item{dir}{ Directory the model file will be written to.}
  \item{name}{ Name of the file being saved.}
  \item{save_cv}{ (Optional) If \code{save_cv = TRUE} all associated cross validation will be saved in the same base directory as the main model. If you don't save cross validation models, there will be warnings when loading the model.}
  \item{force}{ (Optional) If \code{force = TRUE} any existing file will be overwritten. Otherwise if the file already exists the operation will fail.}
 }
 
\value{
Returns path of model object saved.
}
\seealso{
\code{\link{h2o.saveAll}, \link{h2o.loadModel}, \link{h2o.loadAll}, \linkS4class{H2OModel}}
}
\examples{
\dontrun{
library(h2o)
localH2O = h2o.init()
prostate.hex = h2o.importFile(localH2O, path = paste("https://raw.github.com", 
  "0xdata/h2o/master/smalldata/logreg/prostate.csv", sep = "/"), key = "prostate.hex")
prostate.glm = h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), 
  data = prostate.hex, family = "binomial", nfolds = 10, alpha = 0.5)
h2o.saveModel(object = prostate.glm, dir = "/Users/UserName/Desktop", save_cv = TRUE, force = TRUE)
}
}
