setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

automl.model_selection.suite <- function() {
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

  test_exclude_algos <- function() {
    print("AutoML doesn't train models for algos listed in exclude_algos")
    ds <- import_dataset()
    aml <- h2o.automl(x = ds$x, y = ds$y.idx,
      training_frame = ds$train,
      project_name = "aml_exclude_algos",
      max_models = max_models,
      exclude_algos = c('DRF', 'GLM'),
    )
    models <- get_partitioned_models(aml)
    expect_false(any(grepl("DRF", models$all)) || any(grepl("GLM", models$all)))
    expect_equal(length(models$se), 2)
  }

  test_include_algos <- function() {
    print("AutoML trains only models for algos listed in include_algos")
    ds <- import_dataset()
    aml <- h2o.automl(x = ds$x, y = ds$y.idx,
      training_frame = ds$train,
      project_name = "aml_include_algos",
      max_models = max_models,
      include_algos = c('GBM'),
    )
    models <- get_partitioned_models(aml)
    expect_true(all(grepl("GBM", models$all)))
    expect_equal(length(models$se), 0, info="No StackedEnsemble should have been trained if not explicitly included to the existing include_algos")
  }

  test_include_exclude_algos <- function() {
    print("include_algos and exclude_algos parameters are mutually exclusive")
    ds <- import_dataset()
    expect_error(
      h2o.automl(x = ds$x, y = ds$y.idx,
        training_frame = ds$train,
        project_name = "aml_include_exclude_algos",
        max_models = max_models,
        exclude_algos = c('DRF', 'GLM'),
        include_algos = c('XGBoost', 'GBM'),
      ),
      "Use either include_algos or exclude_algos, not both."
    )
  }

  test_bad_modeling_plan <- function() {
    ds <- import_dataset()
    expect_error(
      h2o.automl(x = ds$x, y = ds$y.idx,
        training_frame = ds$train,
        modeling_plan = list("GBM", list("GLM")),
      ),
      "Each steps definition must be a string or a list with a 'name' key and an optional 'alias' or 'steps' key."
    )
    expect_error(
      h2o.automl(x = ds$x, y = ds$y.idx,
        training_frame = ds$train,
        modeling_plan = list("GBM", list(name="GLM", alias='all', steps=c("def_1", "def_2"))),
      ),
      "Each steps definition must be a string or a list with a 'name' key and an optional 'alias' or 'steps' key."
    )
    expect_error(
      h2o.automl(x = ds$x, y = ds$y.idx,
        training_frame = ds$train,
        modeling_plan = list("GBM", list(name="GLM", alias='wrong')),
      ),
      "alias key must be one of 'all', 'defaults', 'grids'."
    )
    expect_error(
      h2o.automl(x = ds$x, y = ds$y.idx,
        training_frame = ds$train,
        modeling_plan = list("GBM", list(name="GLM", steps=list(name='def_1'))),
      ),
      "steps key must be a vector, and each element must be a string or a list with an 'id' key."
    )
  }

  test_modeling_plan_full_syntax <- function() {
    ds <- import_dataset()
    aml <- h2o.automl(x = ds$x, y = ds$y.idx,
      training_frame = ds$train,
      modeling_plan = list(
        list(name="GLM", steps=c(list(id="def_1"))),
        list(name="GBM", alias='grids'),
        list(name='DRF', steps=c(list(id='def_1', weight=333)))  # just testing that it is parsed correctly on backend (no model won't be build due to max_models)
      ),
      project_name = "r_modeling_plan_full_syntax",
      max_models = 2,
      seed = 1,
    )
    models <- get_partitioned_models(aml)
    expect_equal(length(models$non_se), 2)
    expect_equal(length(models$se), 0)
    expect_true(any(grepl("GLM", models$all)))
    expect_true(any(grepl("GBM_grid", models$all)))
  }

  test_modeling_plan_minimal_syntax <- function() {
    ds <- import_dataset()
    aml <- h2o.automl(x = ds$x, y = ds$y.idx,
      training_frame = ds$train,
      modeling_plan = list(
        list(name="DRF", steps=c("XRT", "def_1")),
        "GLM",
        list(name="GBM", alias="grids"),
        "StackedEnsemble"
      ),
      project_name = "r_modeling_plan_minimal_syntax",
      max_models = 5,
      seed = 1,
    )
    models <- get_partitioned_models(aml)
    expect_equal(length(models$non_se), 5)
    expect_equal(length(models$se), 2)
    expect_true(any(grepl("XRT", models$all)))
    expect_true(any(grepl("GLM", models$all)))
    expect_equal(sum(grepl("GBM_grid", models$all)), 2)
    expect_true(any(grepl("BestOfFamily", models$all)))
    expect_true(any(grepl("AllModels", models$all)))
  }

  test_modeling_steps <- function() {
    ds <- import_dataset()
    aml <- h2o.automl(x = ds$x, y = ds$y.idx,
      training_frame = ds$train,
      modeling_plan = list(
        "DRF",
        "GLM",
        list(name="GBM", steps=list(id="grid_1", weight=777)),
        "StackedEnsemble"
      ),
      project_name = "r_modeling_steps",
      max_models = 5,
      seed = 1,
    )
    print(aml@leaderboard)
    expect_equal(aml@modeling_steps, list(
      list(name='DRF', steps=list(list(id='def_1', weight=10), list(id='XRT', weight=10))),
      list(name='GLM', steps=list(list(id='def_1', weight=10))),
      list(name='GBM', steps=list(list(id='grid_1', weight=777))),
      list(name='StackedEnsemble', steps=list(list(id='best', weight=10), list(id='all', weight=10)))
    ))

    new_aml <- h2o.automl(x=ds$x, y=ds$y.idx,
      training_frame=ds$train,
      modeling_plan=aml@modeling_steps,
      project_name="r_reinject_modeling_steps",
      max_models=5
    )
    print(new_aml@leaderboard)
    expect_equal(new_aml@modeling_steps, aml@modeling_steps)
  }

  test_exclude_algos_is_applied_on_top_of_modeling_plan <- function() {
    ds <- import_dataset()
    aml <- h2o.automl(x = ds$x, y = ds$y.idx,
      training_frame = ds$train,
      modeling_plan = list("DRF", "GLM", list(name="GBM", alias='grids'), "StackedEnsemble"),
      exclude_algos = c("GBM", "StackedEnsemble"),
      project_name = "r_modeling_steps",
      max_models = 5,
      seed = 1,
    )
    models <- get_partitioned_models(aml)
    expect_equal(length(models$se), 0)
    expect_equal(length(models$non_se), 3)
  }

  test_monotone_constraints <- function() {
    ds <- import_dataset()
    aml <- h2o.automl(x = ds$x, y = ds$y.idx,
      training_frame = ds$train,
      monotone_constraints = list(AGE=1, VOL=-1),
      project_name="r_monotone_constraints",
      max_models = 6,
      seed = 1
    )
    models <- get_partitioned_models(aml)$all
    models_supporting_monotone_constraints <- models[grepl("^(GBM|XGBoost)", models)]
    expect_lt(length(models_supporting_monotone_constraints), length(models))
    for (m in models_supporting_monotone_constraints) {
      model <- h2o.getModel(m)
      mc <- model@parameters$monotone_constraints
      expect_equal(mc[[1]]$key, "AGE")
      expect_equal(mc[[1]]$value, 1)
      expect_equal(mc[[2]]$key, "VOL")
      expect_equal(mc[[2]]$value, -1)
    }
  }

  test_monotone_constraints_can_be_passed_as_algo_parameter <- function() {
    ds <- import_dataset()
    aml <- h2o.automl(x = ds$x, y = ds$y.idx,
      training_frame = ds$train,
      algo_parameters = list(
        monotone_constraints = list(AGE=1, VOL=-1)
        # ntrees = 10
      ),
      project_name="r_monotone_constraints",
      max_models = 6,
      seed = 1
    )
    models <- get_partitioned_models(aml)$all
    models_supporting_monotone_constraints <- models[grepl("^(GBM|XGBoost)", models)]
    expect_lt(length(models_supporting_monotone_constraints), length(models))
    for (m in models_supporting_monotone_constraints) {
      model <- h2o.getModel(m)
      mc <- model@parameters$monotone_constraints
      expect_equal(mc[[1]]$key, "AGE")
      expect_equal(mc[[1]]$value, 1)
      expect_equal(mc[[2]]$key, "VOL")
      expect_equal(mc[[2]]$value, -1)
    }

    # models_supporting_ntrees <- models[grepl("^(DRF|GBM|XGBoost|XRT)", models)]
    # expect_gt(length(models_supporting_ntrees), 0)
    # for (m in models_supporting_ntrees) {
    #   model <- h2o.getModel(m)
    #   expect_equal(model@parameters$ntrees, 10)
    # }
  }

  test_algo_parameter_can_be_applied_only_to_a_specific_algo <- function() {
    ds <- import_dataset()
    aml <- h2o.automl(x = ds$x, y = ds$y.idx,
      training_frame = ds$train,
      algo_parameters = list(GBM__monotone_constraints = list(AGE=1)),
      project_name="r_specific_algo_param",
      max_models = 6,
      seed = 1
    )
    models <- get_partitioned_models(aml)$all
    models_supporting_monotone_constraints <- models[grepl("^(GBM|XGBoost)", models)]
    expect_lt(length(models_supporting_monotone_constraints), length(models))
    for (m in models_supporting_monotone_constraints) {
      model <- h2o.getModel(m)
      mc <- model@parameters$monotone_constraints
      if (grepl("^GBM", m)) {
        expect_equal(mc[[1]]$key, "AGE")
        expect_equal(mc[[1]]$value, 1)
      } else {
        expect_null(mc[[1]])
      }
    }
  }


  makeSuite(
    test_exclude_algos,
    test_include_algos,
    test_include_exclude_algos,
    test_bad_modeling_plan,
    test_modeling_plan_full_syntax,
    test_modeling_plan_minimal_syntax,
    test_modeling_steps,
    test_exclude_algos_is_applied_on_top_of_modeling_plan,
    test_monotone_constraints,
    test_monotone_constraints_can_be_passed_as_algo_parameter,
    test_algo_parameter_can_be_applied_only_to_a_specific_algo,
  )
}


doSuite("AutoML Models Selection Suite", automl.model_selection.suite())
