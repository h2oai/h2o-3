# An example of binary classification on a local machine using h2o.stack
# This example uses the technique of generating a random grid of base learners for maximum diversity

library(h2oEnsemble)  # Requires version >=0.1.8 of h2oEnsemble
h2o.init(nthreads = -1)  # Start an H2O cluster with nthreads = num cores on your machine


# Import a sample binary outcome train/test set into R
train <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/higgs/higgs_train_5k.csv")
test <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/higgs/higgs_test_5k.csv")
y <- "response"
x <- setdiff(names(train), y)
family <- "binomial"

#For binary classification, response should be a factor
train[,y] <- as.factor(train[,y])  
test[,y] <- as.factor(test[,y])



# Random Grid Search (e.g. 120 second maximum)
# This is set to run fairly quickly, increase max_runtime_secs 
# or max_models to cover more of the hyperparameter space.
# Also, you can expand the hyperparameter space of each of the 
# algorithms by modifying the hyper param code below.

search_criteria <- list(strategy = "RandomDiscrete", 
                        max_runtime_secs = 120)
nfolds <- 5

# GBM Hyperparamters
learn_rate_opt <- c(0.01, 0.03) 
max_depth_opt <- c(3, 4, 5, 6, 9)
sample_rate_opt <- c(0.7, 0.8, 0.9, 1.0)
col_sample_rate_opt <- c(0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8)
hyper_params <- list(learn_rate = learn_rate_opt,
                     max_depth = max_depth_opt, 
                     sample_rate = sample_rate_opt,
                     col_sample_rate = col_sample_rate_opt)

gbm_grid <- h2o.grid("gbm", x = x, y = y,
                     training_frame = train,
                     ntrees = 100,
                     seed = 1,
                     nfolds = nfolds,
                     fold_assignment = "Modulo",
                     keep_cross_validation_predictions = TRUE,
                     hyper_params = hyper_params,
                     search_criteria = search_criteria)
gbm_models <- lapply(gbm_grid@model_ids, function(model_id) h2o.getModel(model_id))



# RF Hyperparamters
mtries_opt <- 8:20 
max_depth_opt <- c(5, 10, 15, 20, 25)
sample_rate_opt <- c(0.7, 0.8, 0.9, 1.0)
col_sample_rate_per_tree_opt <- c(0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8)
hyper_params <- list(mtries = mtries_opt,
                     max_depth = max_depth_opt,
                     sample_rate = sample_rate_opt,
                     col_sample_rate_per_tree = col_sample_rate_per_tree_opt)

rf_grid <- h2o.grid("randomForest", x = x, y = y,
                    training_frame = train,
                    ntrees = 200,
                    seed = 1,
                    nfolds = nfolds,
                    fold_assignment = "Modulo",
                    keep_cross_validation_predictions = TRUE,                    
                    hyper_params = hyper_params,
                    search_criteria = search_criteria)
rf_models <- lapply(rf_grid@model_ids, function(model_id) h2o.getModel(model_id))



# Deeplearning Hyperparamters
activation_opt <- c("Rectifier", "RectifierWithDropout", 
                    "Maxout", "MaxoutWithDropout") 
hidden_opt <- list(c(10,10), c(20,15), c(50,50,50))
l1_opt <- c(0, 1e-3, 1e-5)
l2_opt <- c(0, 1e-3, 1e-5)
hyper_params <- list(activation = activation_opt,
                     hidden = hidden_opt,
                     l1 = l1_opt,
                     l2 = l2_opt)

dl_grid <- h2o.grid("deeplearning", x = x, y = y,
                    training_frame = train,
                    epochs = 15,
                    seed = 1,
                    nfolds = nfolds,
                    fold_assignment = "Modulo",
                    keep_cross_validation_predictions = TRUE,                    
                    hyper_params = hyper_params,
                    search_criteria = search_criteria)
dl_models <- lapply(dl_grid@model_ids, function(model_id) h2o.getModel(model_id))



# GLM Hyperparamters
alpha_opt <- seq(0,1,0.1)
lambda_opt <- c(0,1e-7,1e-5,1e-3,1e-1)
hyper_params <- list(alpha = alpha_opt,
                     lambda = lambda_opt)

glm_grid <- h2o.grid("glm", x = x, y = y,
                     training_frame = train,
                     family = "binomial",
                     nfolds = nfolds,
                     fold_assignment = "Modulo",
                     keep_cross_validation_predictions = TRUE,                    
                     hyper_params = hyper_params,
                     search_criteria = search_criteria)
glm_models <- lapply(glm_grid@model_ids, function(model_id) h2o.getModel(model_id))


# Create a list of all the base models
models <- c(gbm_models, rf_models, dl_models, glm_models)

# Specify a defalt GLM as the metalearner
metalearner <- "h2o.glm.wrapper"

# Let's stack!
stack <- h2o.stack(models = models, 
                   response_frame = train[,y],
                   metalearner = metalearner)

# Compute test set performance:
perf <- h2o.ensemble_performance(stack, newdata = test)
print(perf)

## Top 3 base models:
#3             Grid_GBM_RTMP_sid_a4c7_49_model_R_1459237975988_7806_model_1 0.7744157
#2             Grid_GBM_RTMP_sid_a4c7_49_model_R_1459237975988_7806_model_2 0.7759220
#1             Grid_GBM_RTMP_sid_a4c7_49_model_R_1459237975988_7806_model_3 0.7776145
#
#
#H2O Ensemble Performance on <newdata>:
#  ----------------
#  Family: binomial
#
#Ensemble performance (AUC): 0.780630492577354



# Now let's refit the metalearner using a DL and GLM-NN
stack2 <- h2o.metalearn(stack, metalearner = "h2o.deeplearning.wrapper")
perf2 <- h2o.ensemble_performance(stack2, newdata = test, score_base_models = FALSE)
print(perf2)

#H2O Ensemble Performance on <newdata>:
#  ----------------
#  Family: binomial
#
#Ensemble performance (AUC): 0.754679585409704

# Ouch, here a default DL did not do a great job -- this is why it's good to check performance against top base learner



# It's always a good idea to try a GLM restricted to non-negative weights as a metalearner
# There have been a lot of empircal studies that show that non-negative weights can lead to better performance
h2o.glm_nn <- function(..., non_negative = TRUE) h2o.glm.wrapper(..., non_negative = non_negative)
stack3 <- h2o.metalearn(stack, metalearner = "h2o.glm_nn")
perf3 <- h2o.ensemble_performance(stack3, newdata = test, score_base_models = FALSE)
print(perf3)

#H2O Ensemble Performance on <newdata>:
#  ----------------
#  Family: binomial
#
#Ensemble performance (AUC): 0.781995245966915

# We have a winner.
