\name{h2o.load_ensemble}
\alias{h2o.load_ensemble}
\title{
  Load an H2O Ensemble model
}
\description{
  Load an H2O Ensemble model from disk.  This includes a set of cross-validated H2OModels serialized using the \code{h2o.saveModel}, a serialized RData object (the ensemble object) and optionally, the level-one matrix of cross-validated predicted values as a CSV file.
}
\usage{
  h2o.load_ensemble(path = "", import_levelone = FALSE)
}
\arguments{
  \item{path}{
    String indicating the directory the model will be loaded from.
  }
  \item{import_levelone}{
    Logical, defaults to FALSE.  Will load the level-one matrix of cross-validated predicted values from a CSV file if the model was saved with \code{h2o.save_ensemble} using the \code{export_levelone = TRUE}.  The level-one matrix is required for re-training the metalearner using the \code{h2o.metalearn} function.
  }
}

\author{
  Erin LeDell \email{erin@h2o.ai}
}


\seealso{
  \code{\link{h2o.save_ensemble}} for saving an H2O Ensemble to disk.
}
\examples{
\dontrun{
# An example of binary classification on a local machine using h2o.ensemble
  
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
  
  
# Specify the base learner library & the metalearner
learner <- c("h2o.glm.wrapper", "h2o.randomForest.wrapper", 
             "h2o.gbm.wrapper", "h2o.deeplearning.wrapper")
metalearner <- "h2o.deeplearning.wrapper"
  
  
# Train the ensemble using 5-fold CV to generate level-one data
# More CV folds will take longer to train, but should increase performance
fit <- h2o.ensemble(x = x, y = y, 
                    training_frame = train, 
                    family = family, 
                    learner = learner, 
                    metalearner = metalearner,
                    cvControl = list(V = 5, shuffle = TRUE))
  
# Save ensemble model (a collection of H2OModels and an RData object) to disk
h2o.save_ensemble(fit, path = "./h2o-ensemble-model-loadtest")
rm(fit)
  
# Load model from disk
fit <- h2o.load_ensemble(path = "./h2o-ensemble-model-loadtest")

# Generate predictions from ensemble model on a test set
pp <- predict(fit, test)
predictions <- as.data.frame(pp$pred)[,3]  # third column, p1 is P(Y==1)
labels <- as.data.frame(test[,y])[,1]

# Ensemble test AUC using suggested cvAUC package
if (requireNamespace("cvAUC", quietly=TRUE))
  cvAUC::AUC(predictions = predictions, labels = labels) # 0.7888723
}
}
