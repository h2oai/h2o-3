pkgname <- "h2oEnsemble"
source(file.path(R.home("share"), "R", "examples-header.R"))
options(warn = 1)
library('h2oEnsemble')

base::assign(".oldSearch", base::search(), pos = 'CheckExEnv')
cleanEx()
nameEx("h2o.ensemble")
### * h2o.ensemble

flush(stderr()); flush(stdout())

### Name: h2o.ensemble
### Title: H2O Ensemble
### Aliases: h2o.ensemble

### ** Examples

## Not run: 
##D     
##D # An example of binary classification using h2o.ensemble
##D 
##D library(h2oEnsemble)  # requires version >=0.0.4 of h2oEnsemble
##D library(SuperLearner)  # For metalearner such as "SL.glm"
##D library(cvAUC)  # Used to calculate test set AUC (requires version >=1.0.1 of cvAUC)
##D localH2O <-  h2o.init(ip = "localhost", port = 54321, startH2O = TRUE, nthreads = -1)
##D 
##D 
##D # Import a sample binary outcome train/test set into R
##D train <- read.table("http://www.stat.berkeley.edu/~ledell/data/higgs_5k.csv", sep=",")
##D test <- read.table("http://www.stat.berkeley.edu/~ledell/data/higgs_test_5k.csv", sep=",")
##D 
##D 
##D # Convert R data.frames into H2O parsed data objects
##D training_frame <- as.h2o(localH2O, train)
##D validation_frame <- as.h2o(localH2O, test)
##D y <- "V1"
##D x <- setdiff(names(training_frame), y)
##D family <- "binomial"
##D training_frame[,c(y)] <- as.factor(training_frame[,c(y)])  #Force Binary classification
##D validation_frame[,c(y)] <- as.factor(validation_frame[,c(y)])  # check to validate that this guarantees the same 0/1 mapping?
##D 
##D 
##D 
##D # Create a custom base learner library & specify the metalearner
##D h2o.randomForest.1 <- function(..., ntrees = 1000, nbins = 100, seed = 1) h2o.randomForest.wrapper(..., ntrees = ntrees, nbins = nbins, seed = seed)
##D h2o.deeplearning.1 <- function(..., hidden = c(500,500), activation = "Rectifier", seed = 1)  h2o.deeplearning.wrapper(..., hidden = hidden, activation = activation, seed = seed)
##D h2o.deeplearning.2 <- function(..., hidden = c(200,200,200), activation = "Tanh", seed = 1)  h2o.deeplearning.wrapper(..., hidden = hidden, activation = activation, seed = seed)
##D learner <- c("h2o.randomForest.1", "h2o.deeplearning.1", "h2o.deeplearning.2")
##D metalearner <- "SL.glm"
##D 
##D 
##D 
##D # Train the ensemble using 4-fold CV to generate level-one data
##D # More CV folds will take longer to train, but should increase performance
##D fit <- h2o.ensemble(x = x, y = y, training_frame = training_frame, 
##D                     family = family, 
##D                     learner = learner, metalearner = metalearner,
##D                     cvControl = list(V=4))
##D 
##D 
##D # Generate predictions on the test set
##D pred <- predict.h2o.ensemble(fit, validation_frame)
##D labels <- as.data.frame(validation_frame[,c(y)])[,1]
##D 
##D 
##D # Ensemble test AUC 
##D AUC(predictions=as.data.frame(pred$pred)[,1], labels=labels)
##D # 0.7681649 (h2o-2)
##D # 0.7372054 (h2o-3)
##D # 0.7771959 (h2o-3)
##D 
##D 
##D # Base learner test AUC (for comparison)
##D L <- length(learner)
##D sapply(seq(L), function(l) AUC(predictions = as.data.frame(pred$basepred)[,l], labels = labels)) 
##D # 0.7583084 0.7145333 0.7123253 (h2o-2)
##D # 0.6957427 0.6578448 0.6428909 (h2o-3)
##D # 0.7740217 0.7191073 0.7156636 (h2o-3)
##D 
##D # Note that the ensemble results above are not reproducible since 
##D # h2o.deeplearning is not reproducible when using multiple cores.
##D # For reproducible results, use h2o.init(nthreads = 1)
##D 
## End(Not run)



cleanEx()
nameEx("h2oEnsemble-package")
### * h2oEnsemble-package

flush(stderr()); flush(stdout())

### Name: h2oEnsemble-package
### Title: H2O Ensemble Package
### Aliases: h2oEnsemble-package h2oEnsemble
### Keywords: models

### ** Examples

See h2o.ensemble for examples.



cleanEx()
nameEx("predict.h2o.ensemble")
### * predict.h2o.ensemble

flush(stderr()); flush(stdout())

### Name: predict.h2o.ensemble
### Title: Predict method for an 'h2o.ensemble' object.
### Aliases: predict.h2o.ensemble

### ** Examples

# See h2o.ensemble documentation for an example.




### * <FOOTER>
###
options(digits = 7L)
base::cat("Time elapsed: ", proc.time() - base::get("ptime", pos = 'CheckExEnv'),"\n")
grDevices::dev.off()
###
### Local variables: ***
### mode: outline-minor ***
### outline-regexp: "\\(> \\)?### [*]+" ***
### End: ***
quit('no')
