setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


.calculate_confusion_matrix <-
  function(predicted, actual, favorable_class = "1") {
    t <- predicted == actual
    f <- predicted != actual

    p <- predicted == favorable_class
    n <- predicted != favorable_class

    tp <- sum(t & p)
    fp <- sum(f & p)
    tn <- sum(t & n)
    fn <- sum(f & n)

    total <- tp + fp + tn + fn

    return(
      list(
        tp = tp,
        fp = fp,
        tn = tn,
        fn = fn,
        Accuracy = (tp + tn) / total,
        Precision = tp / (tp + fp),
        tpr = tp / (tp + fn),
        tnr = tn / (fp + tn),
        F1 = (2 * tp) / (2 * tp + fp + fn),
        fpr = fp / (fp + tn),
        fnr = fn / (fn + tp),
        Selected = tp + fp,
        SelectedRatio = (tp + fp) / total,
        Total = total
      )
    )
  }

.get_group_mask <- function(group, newdata) {
  mask <- rep_len(TRUE, nrow(newdata))

  for (col in names(group)) {
    if (!is.na(group[[col]])) {
      mask <- mask & (newdata[[col]] == group[[col]])
    }
  }

  return(mask)
}


.calculate_disparate_measures <-
  function(model,
           newdata,
           sensitive_features,
           reference_groups = list(),
           criterion = "AUTO",
           favorable_class = "0",
           thresholds = list(air = c(0.8, 1.25),
                             p.value = 0.05,
                             smd = c(-0.8, 0.8)),
           minimum_reference_group_factor = 0.8,
           normalize_air = FALSE) {
    if (class(model) == "H2OAutoML") {
      y <- model@leader@allparameters$y
    } else {
      y <- model@allparameters$y
    }
    is_classification <- is.factor(newdata[[y]])
    if (missing(criterion) || criterion == "AUTO") {
      if (is_classification) {
        criterion <- "SelectedRatio"
      } else {
        criterion <- "meanPrediction"
      }
    }

    newdata_df <- as.data.frame(newdata)
    predictions <- as.data.frame(predict(model, newdata)$predict)
    results <- expand.grid(lapply(sensitive_features, function(col)
      unlist(c(
        NA, levels(newdata_df[[col]])
      ))),
                           stringsAsFactors = FALSE)
    names(results) <- sensitive_features

    results <- tail(results, n = -1)
    row.names(results) <- seq_len(nrow(results))

    results$reference <- NA
    mask_cache <- list()

    for (idx in seq_len(nrow(results))) {
      mask <- rep_len(1, nrow(predictions))
      name <- NULL
      for (col in seq_len(ncol(results[idx,]))) {
        if (!is.na(results[idx, col])) {
          col <- names(results)[col]
          val <- results[idx, col][[1]]
          name <- c(name, paste0(col, "=", val))
          cname <- paste0(name, collapse = ",")
          if (!cname %in% names(mask_cache)) {
            mask_cache[[cname]] <- mask & (newdata_df[[col]] == val)
          }
          mask <- mask_cache[[cname]]
        }
      }
      if (is_classification) {
        cm <-
          .calculate_confusion_matrix(predictions[mask, "predict"], newdata_df[mask, y],
                                      favorable_class = favorable_class)
        results[idx, "tp"] <- cm$tp
        results[idx, "fp"] <- cm$fp
        results[idx, "tn"] <- cm$tn
        results[idx, "fn"] <- cm$fn

        results[idx, "Accuracy"] <- cm$Accuracy
        results[idx, "Precision"] <- cm$Precision
        results[idx, "tpr"] <- cm$tpr
        results[idx, "tnr"] <- cm$tnr
        results[idx, "F1"] <- cm$F1
        results[idx, "Total"] <- cm$Total
        results[idx, "Selected"] <- cm$Selected
        results[idx, "SelectedRatio"] <- cm$SelectedRatio
      } else {
        results[idx, "MSE"] <-
          mean((predictions[mask, "predict"] - newdata_df[mask, y])**2)
        results[idx, "RMSE"] <- sqrt(results[idx, "MSE"])
        results[idx, "MAE"] <-
          mean(abs(predictions[mask, "predict"] - newdata_df[mask, y]))
        results[idx, "meanPrediction"] <-
          mean(predictions[mask, "predict"])
      }
    }
    groups <-
      combn(c(rep_len(NA, length(sensitive_features) - 1), sensitive_features), length(sensitive_features))

    # Get reference
    for (gid in seq_len(ncol(groups))) {
      cols <- sensitive_features[sensitive_features %in% groups[, gid]]
      fix_to_na <- (sensitive_features[!sensitive_features %in% groups[, gid]])

      group_mask <- Reduce("&", Map(function(col) {
        if (col %in% fix_to_na) {
          is.na(results[col])
        } else {
          !is.na(results[col])
        }
      }, sensitive_features))

      references_mask <-
        group_mask & Reduce("&", Map(function(col) {
          if (is.null(reference_groups[[col]])) {
            TRUE
          } else {
            results[col] == reference_groups[[col]]
          }
        }, cols))

      refs <- results[references_mask,]

      if (!is.na(minimum_reference_group_factor)) {
        refs <- refs[refs$Total > sum(refs$Total) / nrow(refs) * minimum_reference_group_factor,]
      }

      results[group_mask, "reference"] <-
        row.names(refs[which.max(refs[[criterion]]),])
    }
    if (is_classification) {
      # Calculate air & me
      for (row_id in row.names(results)) {
        ref <- results[row_id, "reference"]
        air <- results[row_id, criterion] / results[ref, criterion]
        if (normalize_air && isTRUE(air > 1)) air <- 1 / air
        results[row_id, "air"] <- air
        results[row_id, "me"] <- results[ref, criterion] - results[row_id, criterion]

        m <-
          matrix(
            c(results[row_id, "Selected"], results[row_id, "Total"] - results[row_id, "Selected"],
              results[ref, "Selected"], results[ref, "Total"] - results[ref, "Selected"]),
            nrow = 2,
            dimnames = list(c("Selected", "Not Selected"), c("Protected", "Reference"))
          )
        ft <- fisher.test(m, alternative = "t")
        results[row_id, "p.value"] <- ft$p.value
      }

      results[["adverse_impact"]] <- FALSE

      if (!is.null(thresholds$air))
        results[["adverse_impact"]] <-
          results[["adverse_impact"]] |
            results[["air"]] < thresholds$air[[1]] |
            results[["air"]] > thresholds$air[[2]]
      if (!is.null(thresholds$me))
        results[["adverse_impact"]] <-
          results[["adverse_impact"]] |
            results[["me"]] < thresholds$me[[1]] |
            results[["me"]] > thresholds$me[[2]]
      if (!is.null(thresholds$p.value))
        results[["adverse_impact"]] <-
          results[["adverse_impact"]] &
            results[["p.value"]] < thresholds$p.value[[1]]
    } else {
      # Calculate smd
      sd_prediction <- sd(predictions$predict)
      for (row_id in row.names(results)) {
        ref <- results[row_id, "reference"]
        results[row_id, "smd"] <-
          (results[row_id, criterion] - results[ref, criterion]) / sd_prediction

        current_group_mask <-
          .get_group_mask(results[row_id, sensitive_features], newdata_df)
        reference_mask <-
          .get_group_mask(results[ref, sensitive_features], newdata_df)

        results[row_id, "p.value"] <-
          wilcox.test(predictions$predict[current_group_mask],
                      predictions$predict[reference_mask])$p.value
      }

      results[["adverse_impact"]] <- FALSE

      if (!is.null(thresholds$smd))
        results[["adverse_impact"]] <-
          results[["smd"]] < thresholds$smd[[1]] |
            results[["smd"]] > thresholds$smd[[2]]
      if (!is.null(thresholds$p.value))
        results[["adverse_impact"]] <-
          results[["adverse_impact"]] &
            results[["p.value"]] > thresholds$p.value[[1]]
    }
    return(results)
  }


.get_data <- function() {
  train <- h2o.importFile("https://raw.githubusercontent.com/h2oai/article-information-2019/master/data/output/hmda_train.csv")
  test <- h2o.importFile("https://raw.githubusercontent.com/h2oai/article-information-2019/master/data/output/hmda_test.csv")

  train <- train[!is.na(train$black) &
                   !is.na(train$hispanic) &
                   !is.na(train$male) &
                   !is.na(train$agegte62),]
  test <- test[!is.na(test$black) &
                 !is.na(test$hispanic) &
                 !is.na(test$male) &
                 !is.na(test$agegte62),]

  train$high_priced <- as.factor(train$high_priced)
  test$high_priced <- as.factor(test$high_priced)

  train$ethnic <- "NA"
  train[train$black == 1, "ethnic"] <- "black"
  train[train$asian == 1, "ethnic"] <- "asian"
  train[train$white == 1, "ethnic"] <- "white"
  train[train$amind == 1, "ethnic"] <- "amind"
  train[train$hipac == 1, "ethnic"] <- "hipac"
  train[train$hispanic == 1, "ethnic"] <- "hispanic"
  train$sex <- "NA"
  train[train$female, "sex"] <- "F"
  train[train$male, "sex"] <- "M"
  train$ethnic <- as.factor(train$ethnic)
  train$sex <- as.factor(train$sex)

  test$ethnic <- "NA"
  test[test$black == 1, "ethnic"] <- "black"
  test[test$asian == 1, "ethnic"] <- "asian"
  test[test$white == 1, "ethnic"] <- "white"
  test[test$amind == 1, "ethnic"] <- "amind"
  test[test$hipac == 1, "ethnic"] <- "hipac"
  test[test$hispanic == 1, "ethnic"] <- "hispanic"
  test$sex <- "NA"
  test[test$female, "sex"] <- "F"
  test[test$male, "sex"] <- "M"
  test$ethnic <- as.factor(test$ethnic)
  test$sex <- as.factor(test$sex)

  x <- c("loan_amount", "loan_to_value_ratio", "no_intro_rate_period", "intro_rate_period", "property_value", "income", "debt_to_income_ratio", "term_360", "conforming")
  y <- "high_priced"

  list(
    train=train,
    test=test,
    x=x,
    y=y,
    protected_cols=c("ethnic", "sex"),
    reference=c(ethnic = "white", sex = "M")
  )
}


.get_data_taiwan <- function() {
  data <- h2o.importFile(locate("smalldata/admissibleml_test/taiwan_credit_card_uci.csv"))
  x <- c('LIMIT_BAL', 'AGE', 'PAY_0', 'PAY_2',  'PAY_4',  'PAY_6', 'BILL_AMT1', 'BILL_AMT2',
         'BILL_AMT4',  'BILL_AMT6', 'PAY_AMT1', 'PAY_AMT2', 'PAY_AMT4', 'PAY_AMT6')
  y <- "default payment next month"
  protected_classes <- tolower(c('SEX', 'EDUCATION'))
  y <- make.names(y)
  x <- tolower(make.names(x))
  names(data) <- make.names(tolower(names(data)))

  for (col in c(y, protected_classes)) {
    print(col)
    data[[col]] <- as.factor(data[[col]])
  }

  splits <- h2o.splitFrame(data, 0.8)
  train <- splits[[1]]
  test <- splits[[2]]
  reference <- c(sex="1", education="2")  # university educated man
  favorable_class <- "0" # no default next month

  list(
    train=train,
    test=test,
    x=x,
    y=y,
    protected_cols=protected_classes,
    reference=reference,
    favorable_class=favorable_class
  )
}


fairness_metrics_are_correct_test <- function() {
  attach(.get_data_taiwan())
  aml <- h2o.automl(x, y, training_frame = train, max_models = 2)
  ref <- reference
  for (pcols in list('sex', 'education', protected_cols)) {
    for (model_id in as.character(as.list(aml@leaderboard$model_id))) {
      m <- h2o.getModel(model_id)
      for (fav_class in c("0", "1")) {
        java_metrics <- h2o.calculate_fairness_metrics(m, test, protected_columns = pcols, reference = ref[pcols], favorable_class = fav_class)$overview
        R_metrics <- .calculate_disparate_measures(m, test, sensitive_features = pcols, favorable_class = fav_class, reference_groups = ref[pcols])
        names(java_metrics) <- tolower(names(java_metrics))
        java_metrics$air <- java_metrics$air_selectedratio
        names(R_metrics) <- tolower(names(R_metrics))
        cols <- intersect(names(java_metrics), names(R_metrics))
        merged <- merge(java_metrics, R_metrics, by = pcols, suffixes = c("_java", "_r"))
        for (col in cols) {
          print(col)
          if (col %in% pcols) next;
          expect_equal(merged[, paste0(col, "_java")], merged[, paste0(col, "_r")])
        }
      }
    }
  }
}

infogram_train_subset_models_works_test <- function() {
  attach(.get_data())
  ig <- h2o.infogram(x = x, y = y, training_frame = train, protected_columns = protected_cols)

  # GBM
  cat("GBM\n")
  da <- h2o.infogram_train_subset_models(ig, h2o.gbm, training_frame = train, test_frame = test, y = y, protected_columns = protected_cols, reference = reference, favorable_class = "0")
  expect_equal(nrow(da), 9)
  expect_true(all(da$air_min > 0.7))
  expect_true(all(da$air_max < 1.2))
  expect_true(any((da$air_min > 0.8) & (da$air_max < 1.25))) # four-fifths rule

  # AutoML
  cat("AutoML\n")
  da <- h2o.infogram_train_subset_models(ig, h2o.automl, training_frame = train, test_frame = test, y = y, protected_columns = protected_cols, reference = reference, favorable_class = "0", max_models = 2)
  expect_equal(nrow(da), 2 * 9 + 9)  # models + SEs
  # some SEs tend to be more unfair than base models, so I relaxed the condition here
  expect_true(any((da$air_min > 0.8) & (da$air_max < 1.25))) # four-fifths rule

  # GRID
  cat("Grid\n")
  da <- h2o.infogram_train_subset_models(ig, h2o.grid, training_frame = train, test_frame = test, y = y, protected_columns = protected_cols, reference = reference,
                          favorable_class = "0", algorithm = "gbm", hyper_params = list(ntrees = c(1, 3)))
  expect_equal(nrow(da), 2 * 9)
  expect_true(all(da$air_min > 0.7))
  expect_true(all(da$air_max < 1.2))
  expect_true(any((da$air_min > 0.8) & (da$air_max < 1.25))) # four-fifths rule
}


infogram_train_subset_models_works_taiwan_test <- function() {
  attach(.get_data_taiwan())
  ig <- h2o.infogram(x = x, y = y, training_frame = train, protected_columns = protected_cols)

  # GBM
  cat("GBM\n")
  da <- h2o.infogram_train_subset_models(ig, h2o.gbm, training_frame = train, test_frame = test, y = y, protected_columns = protected_cols, reference = reference, favorable_class = favorable_class)
  expect_equal(nrow(da), length(x))
  expect_true(any((da$cair > 0.8) & (da$cair < 1.25))) # four-fifths rule

  # AutoML
  cat("AutoML\n")
  da <- h2o.infogram_train_subset_models(ig, h2o.automl, training_frame = train, test_frame = test, y = y, protected_columns = protected_cols, reference = reference, favorable_class = favorable_class, max_models = 2)
  expect_equal(nrow(da), 2 * length(x) + length(x))  # models + SEs
  # some SEs tend to be more unfair than base models, so I relaxed the condition here
  expect_true(any((da$cair > 0.8) & (da$cair < 1.25))) # four-fifths rule

  # GRID
  cat("Grid\n")
  da <- h2o.infogram_train_subset_models(ig, h2o.grid, training_frame = train, test_frame = test, y = y, protected_columns = protected_cols, reference = reference,
                          favorable_class = favorable_class, algorithm = "gbm", hyper_params = list(ntrees = c(1, 3)))
  expect_equal(nrow(da), 2 * length(x))
  expect_true(any((da$cair > 0.8) & (da$cair < 1.25))) # four-fifths rule
}


infogram_train_subset_models_works_with_multiple_metrics_test <- function () {
  attach(.get_data_taiwan())
  ig_fair <- h2o.infogram(x = x, y = y, training_frame = train, protected_columns = protected_cols)
  ig_core <- h2o.infogram(x = x, y = y, training_frame = train)

  # Basic fair
  da <- h2o.infogram_train_subset_models(ig_fair, h2o.gbm, training_frame = train, test_frame = test, y = y, protected_columns = protected_cols, reference = reference, favorable_class = "0")
  expect_equal(nrow(da), length(x))

  # Fair with relevance
  da <- h2o.infogram_train_subset_models(ig_fair, h2o.gbm, training_frame = train, test_frame = test, y = y, protected_columns = protected_cols, reference = reference, favorable_class = "0",
                                         feature_selection_metrics = "relevance_index")
  expect_equal(nrow(da), length(x))

  # Fair with relevance and safety
  da <- h2o.infogram_train_subset_models(ig_fair, h2o.gbm, training_frame = train, test_frame = test, y = y, protected_columns = protected_cols, reference = reference, favorable_class = "0",
                                         feature_selection_metrics = c("safety_index", "relevance_index"))
  expect_equal(nrow(da), length(x))

  # Fair with relevance and safety and cmi_raw
  da <- h2o.infogram_train_subset_models(ig_fair, h2o.gbm, training_frame = train, test_frame = test, y = y, protected_columns = protected_cols, reference = reference, favorable_class = "0",
                                         feature_selection_metrics = c("safety_index", "relevance_index", "cmi_raw"))
  expect_equal(nrow(da), length(x))

  # Fair with relevance and manhattan distance
  da <- h2o.infogram_train_subset_models(ig_fair, h2o.gbm, training_frame = train, test_frame = test, y = y, protected_columns = protected_cols, reference = reference, favorable_class = "0",
                                         feature_selection_metrics = "relevance_index", metric = "manhattan")
  expect_equal(nrow(da), length(x))

  # Fair with relevance and safety and manhattan distance
  da <- h2o.infogram_train_subset_models(ig_fair, h2o.gbm, training_frame = train, test_frame = test, y = y, protected_columns = protected_cols, reference = reference, favorable_class = "0",
                                         feature_selection_metrics = c("safety_index", "relevance_index"), metric = "manhattan")
  expect_equal(nrow(da), length(x))

  # Fair with relevance and maximum distance
  da <- h2o.infogram_train_subset_models(ig_fair, h2o.gbm, training_frame = train, test_frame = test, y = y, protected_columns = protected_cols, reference = reference, favorable_class = "0",
                                         feature_selection_metrics = "relevance_index", metric = "maximum")
  expect_equal(nrow(da), length(x))

  # Fair with relevance and safety and maximum distance
  da <- h2o.infogram_train_subset_models(ig_fair, h2o.gbm, training_frame = train, test_frame = test, y = y, protected_columns = protected_cols, reference = reference, favorable_class = "0",
                                         feature_selection_metrics = c("safety_index", "relevance_index"), metric = "maximum")
  expect_equal(nrow(da), length(x))

  # Basic core
  da <- h2o.infogram_train_subset_models(ig_core, h2o.gbm, training_frame = train, test_frame = test, y = y)
  expect_equal(nrow(da), length(x))

  # core with total information
  da <- h2o.infogram_train_subset_models(ig_core, h2o.gbm, training_frame = train, test_frame = test, y = y,
                                         feature_selection_metrics = "total_information")
  expect_equal(nrow(da), length(x))

  # Core with total and net information
  da <- h2o.infogram_train_subset_models(ig_core, h2o.gbm, training_frame = train, test_frame = test, y = y,
                                         feature_selection_metrics = c("total_information", "net_information"))
  expect_equal(nrow(da), length(x))

  # Core with total and net information and cmi_raw
  da <- h2o.infogram_train_subset_models(ig_core, h2o.gbm, training_frame = train, test_frame = test, y = y,
                                         feature_selection_metrics = c("total_information", "net_information", "cmi_raw"))
  expect_equal(nrow(da), length(x))

  # Core with total and manhattan distance
  da <- h2o.infogram_train_subset_models(ig_core, h2o.gbm, training_frame = train, test_frame = test, y = y,
                                         feature_selection_metrics = "total_information", metric = "manhattan")
  expect_equal(nrow(da), length(x))

  # Core with total and net information and manhattan distance
  da <- h2o.infogram_train_subset_models(ig_core, h2o.gbm, training_frame = train, test_frame = test, y = y,
                                         feature_selection_metrics = c("total_information", "net_information"), metric = "manhattan")
  expect_equal(nrow(da), length(x))
}

doSuite("Fairness tests", makeSuite(
  fairness_metrics_are_correct_test,
  #infogram_train_subset_models_works_test,  # uses data outside of smalldata; useful for debugging
  infogram_train_subset_models_works_taiwan_test,
  infogram_train_subset_models_works_with_multiple_metrics_test
))
