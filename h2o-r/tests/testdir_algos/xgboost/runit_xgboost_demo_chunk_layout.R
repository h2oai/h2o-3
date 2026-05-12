setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


# To demonstrate effect of chunking on AUC: https://github.com/h2oai/h2o-3/issues/8189
demo.xgboost_chunk_layout <- function() {
    orig_df <- h2o.importFile(locate("smalldata/demos/bank-additional-full.csv"))

    random_df <- h2o.createFrame(rows = nrow(orig_df), cols = 100, seed = 1234, seed_for_column_types = 1234)

    appended_df <- h2o.cbind(random_df[c(1:20)], orig_df[c(1:10)], random_df[c(21:100)], orig_df[c(11:21)])
    copy_df <- appended_df[, colnames(orig_df)]

    # We now have 3 versions of the original dataframe:
    # 1. orig - original data
    # 2. appended - original data with some random columns appended with cbind - this changes layout of the Frame in H2O
    # 3. copy - copy of the original data with a different chunk layout (the same layout as for "appended" Frame)

    # Sanity check: make sure the orig and copy frames are actually identical (ignores the chunk layout)
    expect_equal(as.data.frame(orig_df), as.data.frame(copy_df))

    # 1. Train XGBoost on the original data, calculate approximate and precise AUC
    xgboost_orig <- h2o.xgboost(y = "y", x = colnames(orig_df), training_frame = orig_df,
                                model_id = "orig.hex", grow_policy="lossguide", tree_method = "hist",
                                col_sample_rate = 0.3, seed = 1234)
    auc_orig <- h2o.auc(xgboost_orig)
    preds_orig <- h2o.predict(xgboost_orig, orig_df)
    perfect_auc_orig <- .h2o.perfect_auc(preds_orig[, "yes"], orig_df[, "y"])

    # 2. Train XGBoost on the copy of the data with a different chunk layout, calculate approximate and precise AUC
    xgboost_copy <- h2o.xgboost(y = "y", x = colnames(orig_df), training_frame = copy_df,
                                model_id = "copy.hex", grow_policy="lossguide", tree_method = "hist",
                                col_sample_rate = 0.3, seed = 1234)
    auc_copy <- h2o.auc(xgboost_copy)
    preds_copy <- h2o.predict(xgboost_copy, copy_df)
    perfect_auc_copy <- .h2o.perfect_auc(preds_copy[, "yes"], copy_df[, "y"])

    # 2. Train XGBoost on data with random columns appended, calculate approximate and precise AUC
    xgboost_appended <- h2o.xgboost(y = "y", x = colnames(orig_df), training_frame = appended_df, 
                                    model_id = "appended.hex", grow_policy="lossguide", tree_method = "hist",
                                    col_sample_rate = 0.3, seed = 1234)
    auc_appended <- h2o.auc(xgboost_appended)
    preds_appended <- h2o.predict(xgboost_appended, appended_df)
    perfect_auc_appended <- .h2o.perfect_auc(preds_appended[, "yes"], appended_df[, "y"])

    # Expectations
    # - precise AUC will be the exact same regardless of the appended columns for 2 frames that have the same chunk layout 
    expect_identical(perfect_auc_copy, perfect_auc_appended)
    # - if cluster has just a single node AND the algo is XGBoost, precise AUC will be identical also for 2 frames with a different chunk layout
    if (nrow(h2o.clusterStatus()) == 1) {
        expect_identical(perfect_auc_orig, perfect_auc_copy)
    }
    # - approximated AUC will be identical regardless of the appended columns for 2 frames that have the same chunk layout
    expect_identical(auc_copy, auc_appended)
    # - approximated AUC might (and in this case will) be different for 2 identical frames that have a different chunk layout
    expect_true(!identical(auc_orig, auc_copy))
}

doTest("XGBoost Demo: Show how chunk layout can XGBoost model", demo.xgboost_chunk_layout)
