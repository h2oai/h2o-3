library(h2o)
h2o.init()

print("Run GBM without Target Encoding as Baseline")

te_cols <- c("cabin", "embarked", "home.dest")
inflection_point <- 3
smoothing <- 1

.split_data <- function ( frame, seed) {
    print(paste0("Split data into training, validation, testing and target encoding holdout with seed = ", seed))
    splits <- h2o.splitFrame(frame, seed = seed, ratios = c(0.7, 0.1, 0.1),
    destination_frames = c("train.hex", "valid.hex", "te_holdout.hex", "test.hex"))

    train <- splits[[1]]
    full_train <- h2o.rbind(train, splits[[3]])
    ds <- list(train = train, valid = splits[[2]], te_holdout = splits[[3]], test = splits[[4]], full_train = full_train)
    
    return(ds)
}

.average_benchmarking_without_te <- function (frame, randomSeeds) {
    # Since we are not creating any target encoding, we will use both the `train`` and `te_holdout`` frames to train our model

    sum_of_aucs <- 0
    for(current_seed in randomSeeds) {
        ds <- .split_data(frame, current_seed)

        predictors <- setdiff(colnames(ds$train), c("survived", "name", "ticket", "boat", "body"))

        print(paste(c("Predictors: ", predictors), collapse=" | "))

        default_gbm <- h2o.gbm(x = predictors, y = "survived",
                           training_frame = ds$full_train, validation_frame = ds$valid,
                           ntrees = 1000, score_tree_interval = 10, model_id = "default_gbm",
                           # Early Stopping
                            stopping_rounds = 5, stopping_metric = "AUC", stopping_tolerance = 0.001, seed = 1)
        
        auc <- h2o.auc(h2o.performance(default_gbm, ds$test))
        print(paste0("auc = ", auc))
        sum_of_aucs <- sum_of_aucs + auc
    }
    return( sum_of_aucs / length(randomSeeds))
}

.average_benchmarking_kfold <- function (frame, randomSeeds) {
    ############################################## KFold ###################################################################
    print("Perform Target Encoding on cabin, embarked, and home.dest with Cross Validation Holdout")

    # For this model we will calculate Target Encoding mapping on the full training data with cross validation holdout
    # There is possible data leakage since we are creating the encoding map on the training and applying it to the training
    # To mitigate the effect of data leakage without creating a holdout data, we remove the existing value of the row (holdout_type = LeaveOneOut)

    sum_of_aucs <- 0
    for(current_seed in randomSeeds) {
        ds <- .split_data(frame, current_seed)

        kfold_train <- ds$full_train
        kfold_valid <- ds$valid
        kfold_test <- ds$test
        kfold_train$fold <- h2o.kfold_column(kfold_train, nfolds = 5, seed = 1234)

        te <- h2o.targetencoder(x = te_cols, y = "survived", training_frame = kfold_train,
                                fold_column = "fold", data_leakage_handling = "KFold",
                                blending = TRUE, inflection_point = inflection_point, smoothing = smoothing,
                                seed = 1234)        

        # Apply Encoding Map on Training, Validation, Testing Data
        kfold_train <- h2o.transform(te, kfold_train, as_training=TRUE)
        kfold_valid <- h2o.transform(te, kfold_valid, noise=0)
        kfold_test <- h2o.transform(te, kfold_test, noise=0)

        print("Run GBM with Cross Calculation Target Encoding")

        predictors <- setdiff(colnames(kfold_test), c(te_cols, "survived", "name", "ticket", "boat", "body"))

        print(paste(c("Predictors: ", predictors), collapse=" | "))

        kfold_te_gbm <- h2o.gbm(x = predictors, y = "survived",
                                training_frame = kfold_train, validation_frame = kfold_valid,
                                ntrees = 1000, score_tree_interval = 10, model_id = "kfold_te_gbm",
                                # Early Stopping
                                stopping_rounds = 5, stopping_metric = "AUC", stopping_tolerance = 0.001,
                                seed = 1)

        auc <- h2o.auc(h2o.performance(kfold_te_gbm, kfold_test))
        print(paste0("auc = ", auc))
        sum_of_aucs <- sum_of_aucs + auc
    }
    return( sum_of_aucs / length(randomSeeds))
}

.average_benchmarking_loo <- function (frame, randomSeeds) {
    ############################################## LeaveOneOut #############################################################
    print("Perform Leave One Out Target Encoding on cabin, embarked, and home.dest")

    # For this model we will calculate LOO Target Encoding on the full train
    # There is possible data leakage since we are creating the encoding map on the training and applying it to the training
    # To mitigate the effect of data leakage without creating a holdout data, we remove the existing value of the row (holdout_type = LeaveOneOut)

    sum_of_aucs <- 0
    for(current_seed in randomSeeds) {
        ds <- .split_data(frame, current_seed)

        
        loo_train <- ds$full_train
        loo_valid <- ds$valid
        loo_test <- ds$test

        te <- h2o.targetencoder(x = te_cols, y = "survived", training_frame = loo_train,
                                data_leakage_handling = "LeaveOneOut",
                                blending = TRUE, inflection_point = inflection_point, smoothing = smoothing,
                                seed = 1234)

        # Apply Encoding Map on Training, Validation, Testing Data
        loo_train <- h2o.transform(te, loo_train, as_training=TRUE)
        loo_valid <- h2o.transform(te, loo_valid, noise=0)
        loo_test <- h2o.transform(te, loo_test, noise=0)

        print("Run GBM with Leave One Out Target Encoding")
        predictors <- setdiff(colnames(loo_test), c(te_cols, "survived", "name", "ticket", "boat", "body"))
        print(paste(c("Predictors: ", predictors), collapse=" | "))

        loo_gbm <- h2o.gbm(x = predictors, y = "survived",
                            training_frame = loo_train, validation_frame = loo_valid,
                            ntrees = 1000, score_tree_interval = 10, model_id = "loo_gbm",
                            # Early Stopping
                            stopping_rounds = 5, stopping_metric = "AUC", stopping_tolerance = 0.001,
                            seed = 1)

        auc <- h2o.auc(h2o.performance(loo_gbm, loo_test))
        print(paste0("auc = ", auc))
        sum_of_aucs <- sum_of_aucs + auc
    }
    return( sum_of_aucs / length(randomSeeds))
}

.average_benchmarking_none <- function (frame, randomSeeds) {
    ############################################## None holdout ############################################################
    print("Perform Target Encoding on cabin, embarked, and home.dest on Separate Holdout Data")

    # For this model we will calculate the Target Encoding mapping on the te_holdout data
    # Since we are creating the encoding map on the te_holdout data and applying it to the training data,
    # we do not need to take data leakage precautions (set `holdout_type = None`)

    sum_of_aucs <- 0
    for(current_seed in randomSeeds) {
        ds <- .split_data(frame, current_seed)
        
        holdout_train <- ds$train
        holdout_valid <- ds$valid
        holdout_test <- ds$test
        te_holdout <- ds$te_holdout

        te <- h2o.targetencoder(x = te_cols, y = "survived", training_frame = holdout_train,
                                data_leakage_handling = "None",
                                blending = TRUE, inflection_point = inflection_point, smoothing = smoothing,
                                noise=0, seed = 1234)

        # Apply Encoding Map on Training, Validation, Testing Data
        holdout_train <- h2o.transform(te, holdout_train, as_training=TRUE)
        holdout_valid <- h2o.transform(te, holdout_valid)
        holdout_test <- h2o.transform(te, holdout_test)

        predictors <- setdiff(colnames(holdout_test), c(te_cols, "survived", "name", "ticket", "boat", "body"))

        print(paste(c("Predictors: ", predictors), collapse=" | "))
        
        print("Run GBM with Target Encoding on Holdout")
        holdout_gbm <- h2o.gbm(x = predictors, y = "survived",
                                training_frame = holdout_train, validation_frame = holdout_valid,
                                ntrees = 1000, score_tree_interval = 10, model_id = "holdout_gbm",
                                # Early Stopping
                                stopping_rounds = 5, stopping_metric = "AUC", stopping_tolerance = 0.001,
                                seed = 1)

        auc <- h2o.auc(h2o.performance(holdout_gbm, holdout_test))
        print(paste0("auc = ", auc))
        sum_of_aucs <- sum_of_aucs + auc
    }
    return( sum_of_aucs / length(randomSeeds))
}

# Beginning of the average_benchmarking
number_of_runs <- 2
randomSeeds <- sample(100:1000, number_of_runs, replace=FALSE)

dataPath <- h2o:::.h2o.locate("smalldata/gbm_test/titanic.csv")
print("Importing titanic data into H2O")
data <- h2o.importFile(path = dataPath, destination_frame = "data")
data$survived <- as.factor(data$survived)

avg_without_te_auc <- .average_benchmarking_without_te(data, randomSeeds)
avg_kfold_auc <- .average_benchmarking_kfold(data, randomSeeds)
avg_loo_auc <- .average_benchmarking_loo(data, randomSeeds)
avg_holdout_none_auc <- .average_benchmarking_none(data, randomSeeds)

round_digits <- 4

print("Compare AUC of GBM with different types of target encoding")

print(paste0("Default AUC without TE: ", round(avg_without_te_auc, round_digits)))
print(paste0("KFold TE GBM AUC: ", round(avg_kfold_auc, round_digits)))
print(paste0("LOO GBM AUC: ", round(avg_loo_auc, round_digits)))
print(paste0("Holdout none GBM AUC: ", round(avg_holdout_none_auc, round_digits)))

