\name{h2o.saveAll}
\alias{h2o.saveAll}
\title{Save all H2OModel objects to disk.}
\description{Save all H2OModel objects to a disk and can be loaded back into H2O using h2o.loadModel or h2o.loadAll.}
\usage{h2o.saveAll(object, dir="", save_cv = TRUE, force=FALSE)}

\arguments{
  \item{object}{ An \code{\linkS4class{H2OClient}} object.}
  \item{dir}{ Directory the model files will be written to.}
  \item{save_cv}{ (Optional) If \code{save_cv = TRUE} all associated cross validation will be saved in the same base directory as the main model. If you don't save cross validation models, there will be warnings when loading the model.}
  \item{force}{ (Optional) If \code{force = TRUE} any existing file will be overwritten. Otherwise if the file already exists the operation will fail.}
 }
 
\value{
Returns paths of model objects saved.
}
\seealso{
\code{\link{h2o.saveModel},\link{h2o.loadAll}, \link{h2o.loadModel}, \linkS4class{H2OModel}}
}
\examples{
\dontrun{
library(h2o)
localH2O = h2o.init()
prostate.hex = h2o.importFile(localH2O, path = paste("https://raw.github.com", 
  "0xdata/h2o/master/smalldata/logreg/prostate.csv", sep = "/"), key = "prostate.hex")
prostate.glm = h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), 
  data = prostate.hex, family = "binomial", nfolds = 10, alpha = 0.5)
prostate.gbm = h2o.gbm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), n.trees=3,
  interaction.depth=1, distribution="multinomial", data = prostate.hex)
h2o.saveModel(object = prostate.glm, dir = "/Users/UserName/Desktop", save_cv = TRUE, force = TRUE)
h2o.saveAll(object = localH2O, dir = "/Users/UserName/Desktop", save_cv = TRUE, force = TRUE)
}
}
