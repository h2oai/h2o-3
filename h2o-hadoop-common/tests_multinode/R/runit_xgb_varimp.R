setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../h2o-r/scripts/h2o-r-test-setup.R")
#----------------------------------------------------------------------
# Purpose:  This tests the stability of XGBoost models trained on
#           frames with shuffled columns
#----------------------------------------------------------------------

clean_data <- function(df) {
    # Make all variables numeric
    types <- h2o.describe(df)
    cat_cols <- setdiff(as.character(types[types$Type == "enum", ]$Label), "y")
    for(i in cat_cols){
        df[[i]] <- as.numeric(df[[i]])
    }

    # Add binary response column
    df$Response <- h2o.ifelse(df$Response >= 6, "Yes", "No")
    colnames(df) <- c(paste0("A", c(1:(ncol(df) - 1))), "y")
    df$y <- as.factor(df$y)

    return(df)
}

compare_varimp <- function(model, model_shuffled){
    varimp <- as.data.frame(h2o.varimp(model))[c("variable")]
    varimp$rank <- c(1:nrow(varimp))
    varimp_shuffled <- as.data.frame(h2o.varimp(model_shuffled))[c("variable")]
    varimp_shuffled$shuffled_rank <- c(1:nrow(varimp_shuffled))
    colnames(varimp_shuffled) <- c("variable", "shuffled_rank")
    varimp <- merge(varimp, varimp_shuffled, by = "variable", all.x = T, all.y = T)
    varimp <- varimp[order(varimp$rank), ]
    varimp$diff <- abs(varimp$rank - varimp$shuffled_rank)

    return(varimp)
}


check.xgb_varimp_stability <- function() {

    train_data_path <- "/datasets/insurance/train.csv"
    url <- sprintf("hdfs://%s%s", HADOOP.NAMENODE, train_data_path)
    train_data <- h2o.importFile(url)

    df <- clean_data(train_data)

    # Make Monotonicity Constraints based on correlation with the response column
    mc <- as.list(sign(h2o.cor(df, df$y)$y))
    names(mc) <- colnames(df)
    mc <- mc[!is.na(mc) & names(mc) != "y"]

    xgb <- h2o.xgboost(y = "y",
                       training_frame = df,
                       seed = 1234,
                       monotone_constraints = mc
    )
    xgb_shuffled <- h2o.xgboost(y = "y",
                                training_frame = df[rev(colnames(df))],
                                seed = 1234,
                                monotone_constraints = mc
    )

    varimp_comparison <- compare_varimp(xgb, xgb_shuffled)
    mean_diff <- mean(varimp_comparison[1:20, ]$diff)
    print(mean_diff)
    expect_true(mean_diff < 1.5)
}

doTest("Test stability of XGBoost models when columns are shuffled", check.xgb_varimp_stability)
