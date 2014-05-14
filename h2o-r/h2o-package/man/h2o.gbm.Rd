\name{h2o.gbm}
\alias{h2o.gbm}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
H2O: Gradient Boosted Machines
}
%%  ~~function to do ... ~~

\description{Builds gradient boosted classification trees, and gradient boosed regression trees
	 on a parsed data set.
}
\usage{
h2o.gbm(x, y, distribution = "multinomial", data, n.trees = 10, interaction.depth = 5, 
  n.minobsinnode = 10, shrinkage = 0.1, n.bins = 100, importance = FALSE, validation, balance.classes = FALSE, max.after.balance.size = 5)
}
\arguments{
  \item{x}{
A vector containing the names or indices of the predictor variables to use in building the GBM model.
}
  \item{y}{
The name or index of the response variable. If the data does not contain a header, this is the column index number starting at 0, and increasing from left to right. (The response must be either an integer or a categorical variable).
}
  \item{distribution}{
The type of GBM model to be produced: classification is "multinomial" (default), "gaussian" is used for regression.
}
  \item{data}{
An \code{\linkS4class{H2OParsedData}} object containing the variables in the model.
}
  \item{n.trees}{
(Optional) Number of trees to grow. Must be a nonnegative integer.
}
  \item{interaction.depth}{
(Optional) Maximum depth to grow the tree.
}
  \item{n.minobsinnode}{
(Optional) Minimum number of rows to assign to teminal nodes.
}
  \item{shrinkage}{
(Optional) A learning-rate parameter defining step size reduction.
}
  \item{n.bins}{
(Optional) Number of bins to use in building histogram.
}
  \item{importance}{
(Optional) A logical value indicating whether variable importance should be calculated. This will increase the amount of time for the algorithm to complete.
  }
  \item{validation}{
  (Optional) An \code{\linkS4class{H2OParsedData}} object indicating the validation dataset used to construct confusion matrix. If left blank, this defaults to the training data.}
  \item{balance.classes}{(Optional) Balance training data class counts via over/under-sampling (for imbalanced data)}
  \item{max.after.balance.size}{Maximum relative size of the training data after balancing class counts (can be less than 1.0)}
  }
\value{
An object of class \code{\linkS4class{H2OGBMModel}} with slots key, data, valid (the validation dataset) and model, where the last is a list of the following components:
\item{type }{The type of the tree.}
\item{n.trees }{Number of trees grown.}
\item{oob_err }{Out of bag error rate.}
\item{forest }{A matrix giving the minimum, mean, and maximum of the tree depth and number of leaves.}
\item{confusion }{Confusion matrix of the prediction when classification model is specified.}
}
\references{

1. Elith, Jane, John R Leathwick, and Trevor Hastie. "A Working Guide to
Boosted Regression Trees." Journal of Animal Ecology 77.4 (2008): 802-813

2. Friedman, Jerome, Trevor Hastie, Saharon Rosset, Robert Tibshirani,
and Ji Zhu. "Discussion of Boosting Papers." Ann. Statist 32 (2004): 
102-107

3. Hastie, Trevor, Robert Tibshirani, and J Jerome H Friedman. The
Elements of Statistical Learning.
Vol.1. N.p.: Springer New York, 2001. 
http://www.stanford.edu/~hastie/local.ftp/Springer/OLD//ESLII_print4.pdf
}

\seealso{
For more information see: http://docs.0xdata.com
}
\examples{
library(h2o)
localH2O = h2o.init(ip = "localhost", port = 54321, startH2O = TRUE)

# Run regression GBM on australia.hex data 
ausPath = system.file("extdata", "australia.csv", package="h2o")
australia.hex = h2o.importFile(localH2O, path = ausPath)
independent <- c("premax", "salmax","minairtemp", "maxairtemp", "maxsst", 
  "maxsoilmoist", "Max_czcs")
dependent <- "runoffnew"
h2o.gbm(y = dependent, x = independent, data = australia.hex, n.trees = 10, interaction.depth = 3, 
  n.minobsinnode = 2, shrinkage = 0.2, distribution= "gaussian")

# Run multinomial classification GBM on australia data 
h2o.gbm(y = dependent, x = independent, data = australia.hex, n.trees = 15, interaction.depth = 5, 
  n.minobsinnode = 2, shrinkage = 0.01, distribution= "multinomial")
h2o.shutdown(localH2O)
}
