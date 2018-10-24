setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# Note: this test uses a Credit Card Fraud dataset licensed under ODbL v1.0
# full ODvL: https://opendatacommons.org/licenses/odbl/1.0/
# additional dataset details: https://www.kaggle.com/mlg-ulb/creditcardfraud/home

test.IsolationForest.creditcardfraud <- function() {
    p <- 0.95
    ccf_path <- locate("bigdata/laptop/creditcardfraud/creditcardfraud.csv")

    ## In H2O

    creditcardfraud <- h2o.importFile(ccf_path)

    h2o_isofor <- h2o.isolationForest(creditcardfraud, x = colnames(creditcardfraud)[1:30], ntrees = 100, seed = 1234)

    h2o_anomaly_score <- h2o.predict(h2o_isofor, creditcardfraud)

    h2o_anomaly_score$Class <- as.factor(creditcardfraud$Class)
    h2o_anomaly_score_local <- as.data.frame(h2o_anomaly_score)

    h2o_cm <- table(
        h2oForest = h2o_anomaly_score_local$predict > quantile(h2o_anomaly_score_local$predict, p),
        Actual = h2o_anomaly_score_local$Class == 1
    )
    print(h2o_cm)

    ## With isofor

    creditcardfraud_local <- read.csv(ccf_path)

    isofor_model <- isofor::iForest(creditcardfraud_local[1:30], seed = 1234)

    isofor_anomaly_score <- predict(isofor_model, creditcardfraud_local[1:30])

    isofor_cm <- table(
        iForest = isofor_anomaly_score > quantile(isofor_anomaly_score, p),
        Actual = creditcardfraud_local$Class == 1
    )
    print(isofor_cm)

    expect_equal(
        h2o_cm[2,2] / (h2o_cm[1,2] + h2o_cm[2,2]),
        isofor_cm[2,2] / (isofor_cm[1,2] + isofor_cm[2,2]),
        tolerance = 0.05, scale = 1
    )
}

doTest("IsolationForest: Compares Isolation Forest to isofor package in R", test.IsolationForest.creditcardfraud)
