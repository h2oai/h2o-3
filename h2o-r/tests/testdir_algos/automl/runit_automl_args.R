setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

automl.args.test <- function() {

    # This test checks the following (for binomial classification):
    #
    # 1) That h2o.automl executes w/o errors
    # 2) That the arguments are working properly

    max_models <- 5

    import_dataset <- function() {
        y <- "CAPSULE"
        y.idx <- 1

        keys <- h2o.ls()$key
        if ("rtest_automl_args_train" %in% keys) {
            print("using existing dataset")
            train <- h2o.getFrame("rtest_automl_args_train")
            valid <- h2o.getFrame("rtest_automl_args_valid")
            test <- h2o.getFrame("rtest_automl_args_test")
        } else {
            print("uploading dataset")
            train <- h2o.importFile(locate("smalldata/testng/prostate_train.csv"), destination_frame = "prostate_full_train")
            train[,y] <- as.factor(train[,y])
            train <- as.h2o(train, "rtest_automl_args_train")
            test <- h2o.importFile(locate("smalldata/testng/prostate_test.csv"), destination_frame = "prostate_full_test")
            test[,y] <- as.factor(test[,y])
            ss <- h2o.splitFrame(test, destination_frames=c("rtest_automl_args_valid", "rtest_automl_args_test"), seed = 1)
            valid <- ss[[1]]
            test <- ss[[2]]
        }

        x <- setdiff(names(train), y)
        return(list(x=x, y=y, y.idx=y.idx, train=train, valid=valid, test=test))
    }

    get_partitioned_models <- function(aml) {
        model_ids <- as.character(as.data.frame(aml@leaderboard[,"model_id"])[,1])
        ensemble_model_ids <- grep("StackedEnsemble", model_ids, value = TRUE, invert = FALSE)
        non_ensemble_model_ids <- model_ids[!(model_ids %in% ensemble_model_ids)]
        return(list(all=model_ids, se=ensemble_model_ids, non_se=non_ensemble_model_ids))
    }


    print("Check arguments to H2OAutoML class")

    test_invalid_project_name <- function() {
      print("Try a project name starting with a number")
      ds <- import_dataset()
      expect_error(h2o.automl(y = ds$y,
                              training_frame = ds$train,
                              max_models = max_models,
                              project_name = "1nvalid_name"),
                   "1nvalid_name")
    }

    test_without_y <- function() {
        print("Try without a y")
        ds <- import_dataset()
        expect_error(h2o.automl(training_frame = ds$train,
                                max_models = max_models,
                                project_name = "aml0"))
    }

    test_without_x <- function() {
        print("Try without an x")
        ds <- import_dataset()
        h2o.automl(y = ds$y,
                    training_frame = ds$train,
                    max_models = max_models,
                    project_name = "aml1")
    }

    test_y_as_index_x_as_name <- function() {
        print("Try with y as a column index, x as colnames")
        ds <- import_dataset()
        h2o.automl(x = ds$x, y = ds$y.idx,
                    training_frame = ds$train,
                    max_models = max_models,
                    project_name = "aml2",
        )
    }

    test_single_training_frame <- function() {
        print("Single training frame; x and y both specified")
        ds <- import_dataset()
        h2o.automl(x = ds$x, y = ds$y,
                    training_frame = ds$train,
                    max_models = max_models,
                    project_name = "aml3",
        )
    }

    test_training_with_validation_frame <- function() {
        print("Training & validation frame")
        ds <- import_dataset()
        h2o.automl(x = ds$x, y = ds$y,
                    training_frame = ds$train,
                    validation_frame = ds$valid,
                    max_models = max_models,
                    project_name = "aml4",
        )
    }

    test_training_with_leaderboard_frame <- function() {
        print("Training & leaderboard frame")
        ds <- import_dataset()
        h2o.automl(x = ds$x, y = ds$y,
                    training_frame = ds$train,
                    leaderboard_frame = ds$test,
                    max_models = max_models,
                    project_name = "aml5",
        )
    }

    test_training_with_validation_and_leaderboard_frame <- function() {
        print("Training, validation & leaderboard frame")
        ds <- import_dataset()
        h2o.automl(x = ds$x, y = ds$y,
                    training_frame = ds$train,
                    validation_frame = ds$valid,
                    leaderboard_frame = ds$test,
                    max_models = max_models,
                    project_name = "aml6",
        )
    }

    test_early_stopping_defaults <- function () {
        print("Early stopping defaults")
        ds <- import_dataset()
        aml <- h2o.automl(x = ds$x, y = ds$y,
                   training_frame = ds$train,
                   max_models = max_models,
                   project_name = "aml_early_stopping_defaults",
        )
        json <- attr(aml, "_build_resp")
        stopping_criteria <- json$build_control$stopping_criteria
        auto_stopping_tolerance <- (function(fr) min(0.05, max(0.001, 1/sqrt((1 - sum(h2o.nacnt(fr)) / (ncol(fr) * nrow(fr))) * nrow(fr)))))(ds$train)
        expect_equal(stopping_criteria$stopping_rounds, 3)
        expect_equal(stopping_criteria$stopping_tolerance, auto_stopping_tolerance)
        expect_equal(stopping_criteria$stopping_metric, 'AUTO')
        expect_equal(stopping_criteria$max_models, max_models)
        expect_equal(stopping_criteria$max_runtime_secs, 0)
        expect_equal(stopping_criteria$max_runtime_secs_per_model, 0)
    }

    test_early_stopping <- function() {
        print("Early stopping args")
        ds <- import_dataset()
        aml <- h2o.automl(x = ds$x, y = ds$y,
            training_frame = ds$train,
            validation_frame = ds$valid,
            leaderboard_frame = ds$test,
            max_models = max_models,
            max_runtime_secs = 1200,
            max_runtime_secs_per_model = 60,
            stopping_metric = "AUC",
            stopping_tolerance = 0.001,
            stopping_rounds = 2,
            sort_metric = "RMSE",
            project_name = "aml7",
        )
        json <- attr(aml, "_build_resp")
        stopping_criteria <- json$build_control$stopping_criteria
        expect_equal(stopping_criteria$stopping_rounds, 2)
        expect_equal(stopping_criteria$stopping_tolerance,0.001)
        expect_equal(stopping_criteria$stopping_metric, 'AUC')
        expect_equal(stopping_criteria$max_models, max_models)
        expect_equal(stopping_criteria$max_runtime_secs, 1200)
        expect_equal(stopping_criteria$max_runtime_secs_per_model, 60)
    }

    test_metrics_case_insensitive <- function() {
        print("Metrics params case insensitive")
        ds <- import_dataset()
        h2o.automl(x = ds$x, y = ds$y,
                    training_frame = ds$train,
                    validation_frame = ds$valid,
                    leaderboard_frame = ds$test,
                    max_models = max_models,
                    stopping_metric = "auc",
                    sort_metric = "rmse",
                    project_name = "aml7b",
        )
    }

    test_leaderboard_growth <- function() {
        print("Check max_models = 1")
        ds <- import_dataset()
        aml <- h2o.automl(x = ds$x, y = ds$y,
                            training_frame = ds$train,
                            max_models = 1,
                            project_name = "aml8",
        )
        nrow_lb <- nrow(aml@leaderboard)
        expect_equal(nrow_lb, 1)

        print("Check max_models > 1; leaderboard continuity/growth")
        aml <- h2o.automl(x = ds$x, y = ds$y,
                            training_frame = ds$train,
                            max_models = max_models,
                            project_name = "aml8",
        )
        expect_equal(nrow(aml@leaderboard) > nrow_lb, TRUE)
    }

    test_fold_column <- function() {
        ds <- import_dataset()
        fold_column <- "fold_id"
        ds$train[,fold_column] <- as.h2o(data.frame(rep(seq(1:3), 2000)[1:nrow(ds$train)]))

        print("Check fold_column")
        aml <- h2o.automl(x = ds$x, y = ds$y,
                            training_frame = ds$train,
                            fold_column = fold_column,
                            max_models = max_models,
                            keep_cross_validation_models = TRUE,
                            project_name = "aml9",
        )
        models <- get_partitioned_models(aml)
        base_model <- h2o.getModel(models$non_se[1])
        base_model_fold_column <- base_model@parameters$fold_column$column_name
        expect_equal(base_model_fold_column, fold_column)
        ensemble <- h2o.getModel(models$se[1])
        ensemble_fold_column <- ensemble@parameters$metalearner_fold_column$column_name
        expect_equal(ensemble_fold_column, fold_column)
        ensemble_meta <- h2o.getModel(ensemble@model$metalearner$name)
        expect_equal(length(ensemble_meta@model$cross_validation_models), 3)
    }


    test_weights_column <- function() {
        print("Check weights_column")
        ds <- import_dataset()
        weights_column <- "weight"
        ds$train[,weights_column] <- as.h2o(data.frame(runif(n = nrow(ds$train), min = 0, max = 5)))

        aml <- h2o.automl(x = ds$x, y = ds$y,
                            training_frame = ds$train,
                            weights_column = weights_column,
                            max_models = max_models,
                            project_name = "aml10"
        )
        base_model <- h2o.getModel(get_partitioned_models(aml)$non_se[1])
        base_model_weights_column <- base_model@parameters$weights_column$column_name
        expect_equal(base_model_weights_column, weights_column)
    }

    test_fold_column_with_weights_column <- function() {
        print("Check fold_colum and weights_column")
        ds <- import_dataset()
        fold_column <- "fold_id"
        ds$train[,fold_column] <- as.h2o(data.frame(rep(seq(1:3), 2000)[1:nrow(ds$train)]))
        weights_column <- "weight"
        ds$train[,weights_column] <- as.h2o(data.frame(runif(n = nrow(ds$train), min = 0, max = 5)))

        aml <- h2o.automl(x = ds$x, y = ds$y,
                            training_frame = ds$train,
                            fold_column = fold_column,
                            weights_column = weights_column,
                            max_models = max_models,
                            project_name = "aml11",
        )
        base_model <- h2o.getModel(get_partitioned_models(aml)$non_se[1])
        base_model_fold_column <- base_model@parameters$fold_column$column_name
        expect_equal(base_model_fold_column, fold_column)
        base_model_weights_column <- base_model@parameters$weights_column$column_name
        expect_equal(base_model_weights_column, weights_column)
    }

    test_nfolds_set_to_base_model <- function() {
        print("Check that nfolds is piped through properly to base models (nfolds > 1)")
        ds <- import_dataset()
        aml <- h2o.automl(x = ds$x, y = ds$y,
                            training_frame = ds$train,
                            nfolds = 3,
                            max_models = max_models,
                            project_name = "aml12"
        )
        base_model <- h2o.getModel(get_partitioned_models(aml)$non_se[1])
        expect_equal(base_model@parameters$nfolds, 3)
    }

    test_nfolds_eq_0 <- function() {
        print("Check that nfolds = 0 works properly")  #will need to change after xval leaderboard is implemented
        ds <- import_dataset()
        aml <- h2o.automl(x = ds$x, y = ds$y,
                            training_frame = ds$train,
                            nfolds = 0,
                            max_models = max_models,
                            project_name = "aml13",
        )
        base_model <- h2o.getModel(get_partitioned_models(aml)$non_se[1])
        expect_equal(base_model@allparameters$nfolds, 0)
    }

    test_stacked_ensembles_trained <- function() {
        print("Check that only two Stacked Ensembles are trained in reproducible mode")
        ds <- import_dataset()
        aml <- h2o.automl(x = ds$x, y = ds$y,
                            training_frame = ds$train,
                            nfolds = 3,
                            max_models = max_models,
                            project_name = "aml14",
        )
        # Check that leaderboard contains exactly two SEs: all model ensemble & top model ensemble
        expect_equal(length(get_partitioned_models(aml)$se), 2)
    }

    test_stacked_ensembles_trained_with_blending_frame_and_nfolds_eq_0 <- function() {
        print("Check that Stacked Ensembles are trained using blending frame, even if cross-validation is disabled.")
        ds <- import_dataset()
        aml <- h2o.automl(x = ds$x, y = ds$y,
                            training_frame = ds$train,
                            blending_frame = ds$valid,
                            leaderboard_frame = ds$test,
                            nfolds = 0,
                            max_models = max_models,
                            project_name = "aml_blending",
        )
        se <- get_partitioned_models(aml)$se
        expect_equal(length(se), 2)
        for (m in se) {
            model <- h2o.getModel(m)
            expect_equal(model@model$stacking_strategy, "blending")
        }
    }

    test_balance_classes <- function() {
        print("Check that balance_classes is working")
        ds <- import_dataset()
        aml <- h2o.automl(x = ds$x, y = ds$y,
                            training_frame = ds$train,
                            nfolds = 3,
                            max_models = max_models,
                            exclude_algos = c('XGBoost'),  # XGB doesn't support balance_classes
                            balance_classes = TRUE,
                            max_after_balance_size = 3.0,
                            class_sampling_factors = c(0.2, 1.4),
                            project_name = "aml15",
        )
        base_model <- h2o.getModel(get_partitioned_models(aml)$non_se[1])
        expect_equal(base_model@parameters$balance_classes, TRUE)
        expect_equal(base_model@parameters$max_after_balance_size, 3.0)
        expect_equal(base_model@parameters$class_sampling_factors, c(0.2, 1.4))
    }

    test_keep_cv_models_and_keep_cv_predictions <- function() {
        print("Check that cv preds/models are deleted")
        ds <- import_dataset()
        nfolds <- 3
        aml <- h2o.automl(x = ds$x, y = ds$y,
                            training_frame = ds$train,
                            nfolds = nfolds,
                            max_models = max_models,
                            project_name = "aml15",
                            keep_cross_validation_models = FALSE,
                            keep_cross_validation_predictions = FALSE,
        )

        model_ids <- as.character(as.data.frame(aml@leaderboard[,"model_id"])[,1])
        model_ids <- setdiff(model_ids, grep("StackedEnsemble", model_ids, value = TRUE))
        cv_model_ids <- list(NULL)
        for(i in 1:nfolds) {
            cv_model_ids[[i]] <- paste0(model_ids, "_cv_", i)
        }
        cv_model_ids <- unlist(cv_model_ids)
        expect_equal(sum(sapply(cv_model_ids, function(i) grepl(i, h2o.ls()))), 0)
    }

    test_keep_cv_fold_assignment_defaults_with_nfolds_gt_0 <- function() {
        print("Check that fold assignments were skipped by default and nfolds > 1")
        ds <- import_dataset()
        aml <- h2o.automl(x = ds$x, y = ds$y,
                            training_frame = ds$train,
                            nfolds = 3,
                            max_models = max_models,
                            project_name = "aml16",
        )
        some_base_model <- h2o.getModel(get_partitioned_models(aml)$non_se[1])
        expect_equal(some_base_model@parameters$keep_cross_validation_fold_assignment, NULL)
        expect_equal(some_base_model@model$cross_validation_fold_assignment_frame_id, NULL)
    }

    test_keep_cv_fold_assignment_TRUE_with_nfolds_gt_0 <- function() {
        print("Check that fold assignments were kept when `keep_cross_validation_fold_assignment`= TRUE and nfolds > 1")
        ds <- import_dataset()
        aml <- h2o.automl(x = ds$x, y = ds$y,
                            training_frame = ds$train,
                            nfolds = 3,
                            max_models = max_models,
                            project_name = "aml17",
                            keep_cross_validation_fold_assignment = TRUE,
        )
        base_model <- h2o.getModel(get_partitioned_models(aml)$non_se[1])
        expect_equal(base_model@parameters$keep_cross_validation_fold_assignment, TRUE)
        expect_equal(length(base_model@model$cross_validation_fold_assignment_frame_id), 4)
    }

    test_keep_cv_fold_assignment_TRUE_with_nfolds_eq_0 <- function() {
        print("Check that fold assignments were skipped when `keep_cross_validation_fold_assignment`= TRUE and nfolds = 0")
        ds <- import_dataset()
        aml <- h2o.automl(x = ds$x, y = ds$y,
                            training_frame = ds$train,
                            nfolds = 0,
                            max_models = max_models,
                            project_name = "aml18",
                            keep_cross_validation_fold_assignment = TRUE,
        )
        base_model <- h2o.getModel(get_partitioned_models(aml)$non_se[1])
        expect_equal(base_model@parameters$keep_cross_validation_fold_assignment, NULL)
        expect_equal(base_model@model$cross_validation_fold_assignment_frame_id, NULL)
    }

    test_max_models <- function() {
        print("Check that automl get interrupted after `max_models`")
        ds <- import_dataset()
        aml <- h2o.automl(x=ds$x, y=ds$y,
                            training_frame=ds$train,
                            project_name="aml_max_models",
                            max_models=max_models,
        )
        models <- get_partitioned_models(aml)
        expect_equal(length(models$non_se), max_models)
        expect_equal(length(models$se), 2)
    }

    test_frames_can_be_passed_as_keys <- function() {
      print("Check that all AutoML frames can be passed as keys.")
      ds <- import_dataset()

      frames <- list(
        list(training_frame='dummy'),
        list(training_frame=ds$train, validation_frame='dummy'),
        list(training_frame=ds$train, blending_frame='dummy'),
        list(training_frame=ds$train, leaderboard_frame='dummy')
      )
      for (fr in frames) {
        tryCatch({
            h2o.automl(x=ds$x, y=ds$y,
                        training_frame=fr$training_frame,
                        validation_frame=fr$validation_frame,
                        blending_frame=fr$blending_frame,
                        leaderboard_frame=fr$leaderboard_frame,
                        project_name="aml_frames_as_keys",
                        max_models=max_models,
                        nfolds=0)
            stop("should have raised error due to wrong frame key")
          },
          error=function(err) {
            dummy = names(fr[match("dummy", fr)])
            expect_equal(conditionMessage(err), paste0("argument '",dummy,"' must be a valid H2OFrame or key"))
          }
        )
      }

      aml <- h2o.automl(x=ds$x, y=ds$y,
                        training_frame=h2o.getId(ds$train),
                        validation_frame=h2o.getId(ds$valid),
                        blending_frame=h2o.getId(ds$valid),
                        leaderboard_frame=h2o.getId(ds$test),
                        project_name="aml_frames_as_keys",
                        max_models=max_models,
                        nfolds=0)

      expect_gt(length(aml@leaderboard), 0)
    }


    makeSuite(
        test_invalid_project_name,
        test_without_y,
        test_without_x,
        test_y_as_index_x_as_name,
        test_single_training_frame,
        test_training_with_validation_frame,
        test_training_with_leaderboard_frame,
        test_training_with_validation_and_leaderboard_frame,
        test_early_stopping_defaults,
        test_early_stopping,
        test_metrics_case_insensitive,
        test_leaderboard_growth,
        test_fold_column,
        test_weights_column,
        test_fold_column_with_weights_column,
        test_nfolds_set_to_base_model,
        test_nfolds_eq_0,
        test_stacked_ensembles_trained,
        test_stacked_ensembles_trained_with_blending_frame_and_nfolds_eq_0,
        test_balance_classes,
        test_keep_cv_models_and_keep_cv_predictions,
        test_keep_cv_fold_assignment_defaults_with_nfolds_gt_0,
        test_keep_cv_fold_assignment_TRUE_with_nfolds_gt_0,
        test_keep_cv_fold_assignment_TRUE_with_nfolds_eq_0,
        test_max_models,
        test_frames_can_be_passed_as_keys
    )
}

doSuite("AutoML Args Test", automl.args.test(), time_monitor=TRUE)
