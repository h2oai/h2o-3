setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

automl.leaderboard.suite <- function() {

    # Test each ML task to make sure the leaderboard is working as expected:
    # Leaderboard columns are correct for each ML task
    # Check that excluded algos are not in the leaderboard
    # Since we are not running this a long time, we can't guarantee that DNN and GLM will be in there

    all_algos <- c("GLM", "DeepLearning", "GBM", "DRF", "XGBoost", "StackedEnsemble")

    test.binomial <- function() {
        # Binomial:
        fr <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
        fr["CAPSULE"] <- as.factor(fr["CAPSULE"])
        exclude_algos <- c("GLM", "DeepLearning", "DRF")  #Expect GBM + StackedEnsemble
        aml <- h2o.automl(y = 2, training_frame = fr, max_models = 10,
                           project_name = "r_aml_lb_binomial_test",
                           exclude_algos = exclude_algos)
        aml@leaderboard
        # check that correct leaderboard columns exist
        expect_equal(names(aml@leaderboard), c("model_id", "auc", "logloss", "aucpr", "mean_per_class_error", "rmse", "mse"))
        model_ids <- as.vector(aml@leaderboard$model_id)
        # check that no excluded algos are present in leaderboard
        exclude_algo_count <- sum(sapply(exclude_algos, function(i) sum(grepl(i, model_ids))))
        expect_equal(exclude_algo_count, 0)
        include_algos <- setdiff(all_algos, exclude_algos)
        # check that expected algos are included in leaderboard
        for (a in include_algos) {
          expect_equal(sum(grepl(a, model_ids)) > 0, TRUE)
        }
    }


    test.regression <- function() {
        # Regression:
        fr <- h2o.uploadFile(locate("smalldata/extdata/australia.csv"))
        exclude_algos <- c("GBM", "DeepLearning")  #Expect GLM, DRF (and XRT), XGBoost, StackedEnsemble
        aml <- h2o.automl(y = "runoffnew", training_frame = fr, max_models = 10,
                           project_name = "r_aml_lb_regression_test",
                           exclude_algos = exclude_algos)
        aml@leaderboard
        expect_equal(names(aml@leaderboard), c("model_id", "mean_residual_deviance", "rmse", "mse", "mae", "rmsle"))
        model_ids <- as.vector(aml@leaderboard$model_id)
        # check that no excluded algos are present in leaderboard
        exclude_algo_count <- sum(sapply(exclude_algos, function(i) sum(grepl(i, model_ids))))
        expect_equal(exclude_algo_count, 0)
        include_algos <- c(setdiff(all_algos, exclude_algos), "XRT")
        # check that expected algos are included in leaderboard
        for (a in include_algos) {
          expect_equal(sum(grepl(a, model_ids)) > 0, TRUE)
        }
    }

    test.multinomial <- function() {
        # Multinomial:
        fr <- as.h2o(iris)
        exclude_algos <- c("XGBoost")
        aml <- h2o.automl(y = 5, training_frame = fr, max_models = 10,
                           project_name = "r_aml_lb_multinomial_test",
                           exclude_algos = exclude_algos)
        aml@leaderboard
        expect_equal(names(aml@leaderboard),c("model_id", "mean_per_class_error", "logloss", "rmse", "mse", "auc", "aucpr"))
        model_ids <- as.vector(aml@leaderboard$model_id)
        # check that no excluded algos are present in leaderboard
        exclude_algo_count <- sum(sapply(exclude_algos, function(i) sum(grepl(i, model_ids))))
        expect_equal(exclude_algo_count, 0)
        # check that expected algos are included in leaderboard
        include_algos <- c(setdiff(all_algos, exclude_algos), "XRT")
        for (a in include_algos) {
          expect_equal(sum(grepl(a, model_ids)) > 0, TRUE)
        }
    }

    test.empty_leaderboard <- function() {
        # Exclude all the algorithms, check for empty leaderboard
        fr <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))  #need to reload data (getting error otherwise)
        fr["CAPSULE"] <- as.factor(fr["CAPSULE"])
        aml <- h2o.automl(y = 2, training_frame = fr,
                           project_name = "r_aml_lb_empty_test",
                           include_algos = list())
        aml@leaderboard
        expect_equal(nrow(aml@leaderboard), 0)

        warnings <- aml@event_log[aml@event_log['level'] == 'Warn','message']
        last_warning <- warnings[nrow(warnings), 1]
        expect_true(grepl("Empty leaderboard", last_warning))
    }

    test.all_algos <- function() {
        # Include all algorithms (all should be there, given large enough max_models)
        fr <- as.h2o(iris)
        aml <- h2o.automl(y = 5, training_frame = fr, max_models = 12,
                           project_name = "r_aml_lb_all_algos_test")
        model_ids <- as.vector(aml@leaderboard$model_id)
        include_algos <- c(all_algos, "XRT")
        for (a in include_algos) {
          expect_equal(sum(grepl(a, model_ids)) > 0, TRUE)
        }
    }

    test.custom_leaderboard <- function() {
        fr <- as.h2o(iris)
        aml <- h2o.automl(y = 5, training_frame = fr, max_models = 5,
                          project_name = "r_aml_customlb")
        std_columns <- c("model_id", "mean_per_class_error", "logloss", "rmse", "mse", "auc", "aucpr")
        expect_equal(names(aml@leaderboard), std_columns)
        expect_equal(names(h2o.get_leaderboard(aml)), std_columns)
        expect_equal(names(h2o.get_leaderboard(aml, extra_columns='unknown')), std_columns)
        expect_equal(names(h2o.get_leaderboard(aml, extra_columns='ALL')), c(std_columns, "training_time_ms", "predict_time_per_row_ms", "algo"))
        expect_equal(names(h2o.get_leaderboard(aml, extra_columns="training_time_ms")), c(std_columns, "training_time_ms"))
        expect_equal(names(h2o.get_leaderboard(aml, extra_columns=c("predict_time_per_row_ms","training_time_ms"))), c(std_columns, "predict_time_per_row_ms", "training_time_ms"))
        expect_equal(names(h2o.get_leaderboard(aml, extra_columns=list("unkown","training_time_ms"))), c(std_columns, "training_time_ms"))

        lb_ext <- h2o.get_leaderboard(aml, 'ALL')
        print(lb_ext)
        expect_true(all(sapply(lb_ext[setdiff(names(lb_ext), c("model_id", "algo"))], is.numeric)))
        expect_true(all(sapply(lb_ext["training_time_ms"], function(v) v >= 0)))
        expect_true(all(sapply(lb_ext["predict_time_per_row_ms"], function(v) v > 0)))
        expect_true(all(lb_ext["algo"] %in% c("DeepLearning", "DRF", "GBM", "GLM", "StackedEnsemble", "XGBoost")))
    }


    test.get_best_model_per_family <- function() {
        fr <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
        fr["CAPSULE"] <- as.factor(fr["CAPSULE"])
        aml <- h2o.automl(y = "CAPSULE", training_frame = fr, max_models = 11,
                          project_name = "r_aml_customlb")
        .check_best_model <- function(model_ids, criterion) {
            seen <- character()

            # test case insensitivity in algo specification
            top_model_ids <- sapply(c("DEEPLEARNING", "drf", "GBM", "glm", "stackedensemble", "xgboost"), function(algo) {
                m <- h2o.get_best_model(aml, algo, criterion = criterion)
                if (is.null(m)) NULL else m@model_id
            })
            expect_true(sum(sapply(top_model_ids, is.null)) <= 1 && length(top_model_ids) <= 6)
            for (model_id in model_ids) {
                model_type <- strsplit(model_id, "_")[[1]][[1]]
                if (!model_type %in% seen) {
                    expect_true(model_id %in% top_model_ids)
                    if (model_type %in% c("DRF", "XRT"))
                      seen <- c(seen, c("DRF", "XRT"))
                    else
                      seen <- c(seen, model_type)
                }
            }
        }
        # check it works with default criterion
        .check_best_model(as.character(as.list(aml@leaderboard$model_id)), "auto")
        # check it works with AUC criterion (the higher the better as opposed to loss functions) and test case insensitivity
        .check_best_model(as.character(as.list(h2o.arrange(aml@leaderboard, desc(auc))$model_id)), "AUC")
        # check it works for MSE as a criterion
        .check_best_model(as.character(as.list(h2o.arrange(aml@leaderboard, mse)$model_id)), "mse")

        # Check it works for without specifying a model type
        expect_equal(h2o.get_best_model(aml)@model_id, aml@leaderboard[1, "model_id"])

        # Check it works with just criterion
        expect_equal(h2o.get_best_model(aml, criterion = "mse")@model_id, h2o.arrange(aml@leaderboard, mse)[1, "model_id"])

        # Check it works with extra_cols
        top_model <- h2o.arrange(h2o.get_leaderboard(aml, extra_columns = "training_time_ms"), training_time_ms)[1, "model_id"]
        expect_equal(h2o.get_best_model(aml, criterion = "training_time_ms")@model_id, top_model)

        # Check input validation
        expect_error(h2o.get_best_model(iris))
        expect_error(h2o.get_best_model(aml, algorithm = "GXboost"))
        expect_error(h2o.get_best_model(aml, criterion = "lorem_ipsum_dolor_sit_amet"))
}

    makeSuite(
      test.binomial,
      test.regression,
      test.multinomial,
      test.empty_leaderboard,
      test.all_algos,
      test.custom_leaderboard,
      test.get_best_model_per_family,
    )
}

doSuite("AutoML Leaderboard Test", automl.leaderboard.suite())

