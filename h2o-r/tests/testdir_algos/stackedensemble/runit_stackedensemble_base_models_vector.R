setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

stackedensemble_base_model_vector_test <- function() {

train <- h2o.uploadFile(locate("smalldata/testng/higgs_train_5k.csv"),
destination_frame = "higgs_train_5k")

y <- "response"
x <- setdiff(names(train), y)
train[,y] <- as.factor(train[,y])
nfolds <- 3

# Train & Cross-validate a GBM
my_gbm <- h2o.gbm(x = x[2:10],
                y = y,
                training_frame = train,
                distribution = "bernoulli",
                ntrees = 10,
                nfolds = nfolds,
                fold_assignment = "Modulo",
                keep_cross_validation_predictions = TRUE,
                seed = 1)

# Train & Cross-validate a RF
my_rf <- h2o.randomForest(x = x[14:20],
                y = y,
                training_frame = train,
                ntrees = 10,
                nfolds = nfolds,
                fold_assignment = "Modulo",
                keep_cross_validation_predictions = TRUE,
                seed = 1)

base_models_vec <- c(my_gbm@model_id, my_rf@model_id)
expect_equal(is.vector(base_models_vec), TRUE)

# Train a stacked ensemble using the GBM and RF above
stack1 <- h2o.stackedEnsemble(x = x,
            y = y,
            training_frame = train,
            base_models = base_models_vec)
}

doTest("Stacked Ensemble base_models vector test", stackedensemble_base_model_vector_test)