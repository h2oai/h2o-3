\name{h2o.stack}
\alias{h2o.stack}
\title{
H2O Stack
}
\description{
This function creates a "Super Learner" (stacking) ensemble using a list of existing H2O base models specified by the user.
}
\usage{
h2o.stack(models, 
  metalearner = "h2o.glm.wrapper", 
  response_frame,
  cvpreds_frame = NULL,
  seed = 1,
  keep_levelone_data = TRUE)
}
\arguments{
  \item{models}{
A list of H2O models.  These models must all have been cross-validated using identical folds with \code{keep_cross_validation_predictions = TRUE}.
}
  \item{response_frame}{
An H2OFrame object containing the response variable used in training the base models.  Must be a single column.
}
  \item{cvpreds_frame}{
(Optional & Experimental) An H2OFrame object containing the cross-validation holdout predictions for each of the base learners.
}
  \item{metalearner}{
A string specifying the prediction algorithm used to learn the optimal combination of the base learners.  Supports both h2o and SuperLearner wrapper functions.
}
  \item{seed}{
A random seed to be set (integer); defaults to 1. If \code{NULL}, then a random seed will not be set.  The seed is set prior to creating the CV folds and prior to model training for base learning and metalearning.
}
 \item{keep_levelone_data}{
  Logical, defaults to \code{TRUE}.  Keep the \code{levelone} H2OFrame of cross-validated predicted values (Z matrix) and original response vector, y (cbind to Z).
}
}

\value{

\item{x}{
A vector containing the names of the predictors in the model.
}
\item{y}{
The name of the response variable in the model.
}
\item{family}{
Returns the \code{family} argument from above.  
}
\item{cvControl}{
Returns the \code{cvControl} argument from above.
}
\item{folds}{
A vector of fold ids for each observation, ordered by row index.  The number of unique fold ids is specified in \code{cvControl$V}.   
}
\item{ylim}{
Returns range of \code{y}.
}
\item{seed}{
An integer. Returns \code{seed} argument from above.
}
\item{parallel}{
An character vector. Returns \code{character} argument from above.
}
\item{basefits}{
A list of H2O models, each of which are trained using the \code{data} object.  The length of this list is equal to the number of base learners in the \code{learner} argument.
}
\item{metafit}{
The predictive model which is learned by regressing \code{y} on \code{Z} (see description of \code{Z} below).  The type of model is specified using the \code{metalearner} argument.
}
\item{levelone}{
An H2OFrame object.  The \code{levelone} H2OFrame includes the "Z matrix" (the cross-validated predicted values for each base learner) and original response vector, y.  In the stacking ensemble literature, the Z matrix is the design matrix used to train the metalearner.
}
\item{runtime}{
A list of runtimes for various steps of the algorithm.  The list contains \code{cv}, \code{metalearning}, \code{baselearning} and \code{total} elements.  The \code{cv} element is the time it takes to create the \code{Z} matrix (see above).  The \code{metalearning} element is the training time for the metalearning step.  The \code{baselearning} element is a list of training times for each of the models in the ensemble.  The time to run the entire \code{h2o.ensemble} function is given in \code{total}.
}
\item{h2o_version}{
The version of the h2o R package.
}
\item{h2oEnsemble_version}{
The version of the h2oEnsemble R package.
}
}
\references{
\href{https://github.com/h2oai/h2o-3/tree/master/h2o-r/ensemble}{H2O Ensemble Homepage}	\cr
\href{https://github.com/h2oai/h2o-tutorials/blob/master/tutorials/ensembles-stacking/h2oEnsemble_tutorial.md}{H2O Ensemble Tutorial}	\cr
\cr	
LeDell, E. (2015) Scalable Ensemble Learning and Computationally Efficient Variance Estimation (Doctoral Dissertation).  University of California, Berkeley, USA.\cr
\url{http://www.stat.berkeley.edu/~ledell/papers/ledell-phd-thesis.pdf}\cr
\cr
van der Laan, M. J., Polley, E. C. and Hubbard, A. E. (2007) Super Learner, Statistical Applications of Genetics and Molecular Biology, 6, article 25. \cr
\url{http://dx.doi.org/10.2202/1544-6115.1309}\cr
\url{http://biostats.bepress.com/ucbbiostat/paper222}\cr
\cr
Breiman, L. (1996) Stacked Regressions, Machine Learning, 24:49-64.\cr
\url{http://dx.doi.org/10.1007/BF00117832}\cr
\url{http://statistics.berkeley.edu/sites/default/files/tech-reports/367.pdf}
}
\author{
Erin LeDell \email{erin@h2o.ai}
}


\seealso{
\code{\link[h2oEnsemble:h2o.ensemble]{h2o.ensemble}}, \code{\link[h2oEnsemble:h2o.ensemble_performance]{h2o.ensemble_performance}}
}
\examples{
\dontrun{
    
# An example of binary classification on a local machine using h2o.stack

library(h2oEnsemble)
h2o.init(nthreads = -1) # Start H2O cluster using all available CPU threads


# Import a sample binary outcome train/test set into R
train <- h2o.importFile("https://s3.amazonaws.com/erin-data/higgs/higgs_train_5k.csv")
test <- h2o.importFile("https://s3.amazonaws.com/erin-data/higgs/higgs_test_5k.csv")
y <- "response"
x <- setdiff(names(train), y)
family <- "binomial"

#For binary classification, response should be a factor
train[,y] <- as.factor(train[,y])
test[,y] <- as.factor(test[,y])


# The h2o.stack function is an alternative to the h2o.ensemble function, which
# allows the user to specify H2O models individually and then stack them together
# at a later time.  Saved models, re-loaded from disk, can also be stacked.

# The base models must use identical cv folds; this can be achieved in two ways:
# 1. they be specified explicitly by using the fold_column argument, or
# 2. use same value for `nfolds` and set `fold_assignment = "Modulo"`

nfolds <- 5  

glm1 <- h2o.glm(x = x, y = y, family = family, 
                training_frame = train,
                nfolds = nfolds,
                fold_assignment = "Modulo",
                keep_cross_validation_predictions = TRUE)

gbm1 <- h2o.gbm(x = x, y = y, distribution = "bernoulli",
                training_frame = train,
                seed = 1,
                nfolds = nfolds,
                fold_assignment = "Modulo",
                keep_cross_validation_predictions = TRUE)

rf1 <- h2o.randomForest(x = x, y = y, # distribution not used for RF
                        training_frame = train,
                        seed = 1,
                        nfolds = nfolds,
                        fold_assignment = "Modulo",
                        keep_cross_validation_predictions = TRUE)

dl1 <- h2o.deeplearning(x = x, y = y, distribution = "bernoulli",
                        training_frame = train,
                        nfolds = nfolds,
                        fold_assignment = "Modulo",
                        keep_cross_validation_predictions = TRUE)

models <- list(glm1, gbm1, rf1, dl1)
metalearner <- "h2o.glm.wrapper"

stack <- h2o.stack(models = models,
                   response_frame = train[,y],
                   metalearner = metalearner, 
                   seed = 1,
                   keep_levelone_data = TRUE)


# Compute test set performance:
perf <- h2o.ensemble_performance(stack, newdata = test)

print(perf)
# Base learner performance, sorted by specified metric:
#                                    learner       AUC
# 1          GLM_model_R_1480128759162_16643 0.6822933
# 4 DeepLearning_model_R_1480128759162_18909 0.7016809
# 3          DRF_model_R_1480128759162_17790 0.7546005
# 2          GBM_model_R_1480128759162_16661 0.7780807
# 
# 
# H2O Ensemble Performance on <newdata>:
# ----------------
# Family: binomial
# 
# Ensemble performance (AUC): 0.781241759877087


}
}
