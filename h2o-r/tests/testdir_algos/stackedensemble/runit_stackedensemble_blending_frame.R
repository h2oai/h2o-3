setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

stackedensemble.blending_frame.suite <- function() {
    
    seed <- 2019
    
    prepare_data <- function(blending=TRUE, testing=FALSE) {
        train <- h2o.uploadFile(locate("smalldata/iris/iris_train.csv"))
        y <- "species"
        x <- setdiff(names(train), y)
        train[,y] <- as.factor(train[,y])
        test <- NULL
        blend <- NULL
        if(testing) {
            test <- h2o.uploadFile(locate("smalldata/iris/iris_test.csv"))
            test[,y] <- as.factor(test[,y])
        }
        if(blending) {
            splits <- h2o.splitFrame(train, seed=seed)
            train <- splits[[1]]
            blend <- splits[[2]]
        }
        list(x=x, y=y, train=train, test=test, blend=blend)
    }
    
    train_base_models <- function(dataset, ...) {
        dots <- list(...)
        gbm <- do.call('h2o.gbm', c(list(
                       x = dataset$x, y = dataset$y, 
                       training_frame = dataset$train,
                       distribution = "multinomial",
                       ntrees = 10,
                       max_depth = 3,
                       min_rows = 2,
                       learn_rate = 0.2,
                       seed = seed
                       ), dots))
    
        rf <- do.call('h2o.randomForest', c(list(
                      x = dataset$x, y = dataset$y,
                      training_frame = dataset$train,
                      ntrees = 20,
                      seed = seed
                      ), dots))
        
        c(gbm, rf)
    }
    
    train_stackedensemble <- function(dataset, base_models, ...) {
        dots <- list(...)
        do.call('h2o.stackedEnsemble', c(list(
                x = dataset$x, y = dataset$y,
                training_frame = dataset$train,
                blending_frame = dataset$blend,
                base_models = lapply(base_models, function(m) m@model_id),
                seed = seed
                ), dots))
    }
    
    test.passing_blending_frame_triggers_blending_mode <- function() {
        ds <- prepare_data()
        base_models <- train_base_models(ds)
        se <- train_stackedensemble(ds, base_models)
        expect_equal(se@model$stacking_strategy, "blending")
        expect_null(se@model$levelone_frame_id)
    }

    test.level_one_frame_format_in_blending_mode <- function() {
        ds <- prepare_data()
        base_models <- train_base_models(ds)
        se <- train_stackedensemble(ds, base_models, keep_levelone_frame=TRUE)

        expect_false(is.null(se@model$levelone_frame_id))
        expected_cols <- nrow(h2o.unique(ds$blend[ds$y])) * length(base_models) + 1 # = count_predictions_probabilities * count_models + 1 (target)
        expected_rows <- nrow(ds$blend)
        level_one_frame = h2o.getFrame(se@model$levelone_frame_id$name)
        expect_equal(ncol(level_one_frame), expected_cols)
        expect_equal(nrow(level_one_frame), expected_rows) 
    }

    test.base_models_can_be_trained_with_different_training_frames_in_blending_mode <- function() {
        ds <- prepare_data()
        cut <- nrow(ds$train) %/% 2
        ds1 <- ds
        ds1$train <- ds$train[1:cut,]
        ds2 <- ds
        ds2$train <- ds$train[cut:nrow(ds$train),]
        base_models_1 <- train_base_models(ds1)
        base_models_2 <- train_base_models(ds2)
        base_models <- c(base_models_1, base_models_2)
        se <- train_stackedensemble(ds, base_models)
        expect_true(h2o.mse(h2o.performance(se)) == h2o.mse(h2o.performance(se, ds$train)))
    }

    test.training_frame_is_not_required_in_blending_mode <- function() {
        ds <- prepare_data()
        base_models <- train_base_models(ds)
        ds_notrain <- ds
        ds_notrain$train <- NULL
        se = train_stackedensemble(ds_notrain, base_models)
        expect_true(h2o.mse(h2o.performance(se)) == h2o.mse(h2o.performance(se, ds$blend)))
    }

    test.blending_mode_usually_performs_worse_than_CV_stacking_mode <- function() {
        ds_blending <- prepare_data(testing=TRUE)
        se_blending <- train_stackedensemble(ds_blending, train_base_models(ds_blending))
        
        ds_cv <- prepare_data(blending=FALSE, testing=TRUE)
        se_cv <- train_stackedensemble(ds_cv, 
                                       train_base_models(ds_cv, 
                                                         nfolds=3,
                                                         fold_assignment='Modulo',
                                                         keep_cross_validation_predictions=TRUE))
        
        perfs <- list()
        perfs[[se_blending@model$stacking_strategy]] <- h2o.performance(se_blending, newdata=ds_blending$test)
        perfs[[se_cv@model$stacking_strategy]] <- h2o.performance(se_cv, newdata=ds_cv$test)

        expect_gt(h2o.rmse(perfs$blending), h2o.rmse(perfs$cross_validation))   # not guaranteed
    }

    makeSuite(
        test.passing_blending_frame_triggers_blending_mode,
        test.level_one_frame_format_in_blending_mode,
        test.base_models_can_be_trained_with_different_training_frames_in_blending_mode,
        test.training_frame_is_not_required_in_blending_mode,
        test.blending_mode_usually_performs_worse_than_CV_stacking_mode
    )
}

doSuite("Stacked Ensemble Blending Frame Suite", stackedensemble.blending_frame.suite())
    
