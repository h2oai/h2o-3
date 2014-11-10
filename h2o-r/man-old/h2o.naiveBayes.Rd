\name{h2o.naiveBayes}
\alias{h2o.naiveBayes}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
H2O: Naive Bayes Classifier
}
%%  ~~function to do ... ~~

\description{Builds gradient boosted classification trees, and gradient boosted regression trees on a parsed data set.
}
\usage{
h2o.naiveBayes(x, y, data, key = "", laplace = 0, dropNACols = FALSE)
}
\arguments{
  \item{x}{
A vector containing the names of the predictors in the model.
}
  \item{y}{
The name of the response variable in the model.
}
  \item{data}{
An \code{\linkS4class{H2OParsedData}} (\code{version = 2}) object containing the variables in the model.
}
  \item{key}{
(Optional) The unique hex key assigned to the resulting model. If none is given, a key will automatically be generated.
}
  \item{laplace}{
(Optional) A positive number controlling Laplace smoothing. The default (0) disables Laplace smoothing.
}
  \item{dropNACols}{
(Optional) A logical value indicating whether to drop predictor columns with >= 20\% NAs.
  }
}
\details{
The naive Bayes classifier assumes independence between predictor variables conditional on the response, and a Gaussian distribution of numeric predictors with mean and standard deviation computed from the training dataset. 

When building a naive Bayes classifier, every row in the training dataset that contains at least one NA will be skipped completely. If the test dataset has missing values, then those predictors are omitted in the probability calculation during prediction.
}
\value{
An object of class \code{\linkS4class{H2ONBModel}} with slots key, data, and model, where the last is a list of the following components:
  \item{laplace }{A positive number controlling Laplace smoothing. The default (0) disables Laplace smoothing.}
  \item{levels }{Categorical levels of the dependent variable.}
  \item{apriori }{Total occurrences of each level of the dependent variable.}
  \item{apriori_prob }{A-priori class distribution for the dependent variable.}
  \item{tables }{A list of tables, one for each predictor variable. For categorical predictors, the table displays, for each attribute level, the conditional probabilities given the target class. For numeric predictors, the table gives, for each target class, the mean and standard deviation of the variable.}
}

\seealso{
For more information see: http://docs.0xdata.com
}
\examples{
library(h2o)
localH2O = h2o.init()

# Build naive Bayes classifier with categorical predictors
votesPath = system.file("extdata", "housevotes.csv", package="h2o")
votes.hex = h2o.importFile(localH2O, path = votesPath, header = TRUE)
summary(votes.hex)
h2o.naiveBayes(y = 1, x = 2:17, data = votes.hex, laplace = 3)

# Build naive Bayes classifier with numeric predictors
irisPath = system.file("extdata", "iris.csv", package="h2o")
iris.hex = h2o.importFile(localH2O, path = irisPath)
h2o.naiveBayes(y = 5, x = 1:4, data = iris.hex)
}
