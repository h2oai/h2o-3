setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.partialDomains <- function() {
    df = h2o.importFile(locate("smalldata/airlines/allyears2k_headers.zip"))

    ## row-slicing reduces the number of existing factors, the rest should be treated as NA
    df <- df[300:360,]
    model1 = h2o.gbm(model_id = "model1",
        training_frame = df,
        x = c("Origin"),
        y = "IsDepDelayed",
        max_depth = 5,
        seed=1234,
        min_rows = 1,
        ntrees = 3)

    ## relevel by re-parsing the dataset
    df <- as.h2o(as.data.frame(df))
    model2 = h2o.gbm(model_id = "model22",
        training_frame = df,
        x = c("Origin"),
        y = "IsDepDelayed",
        max_depth = 5,
        seed=1234,
        min_rows = 1,
        ntrees = 3)

    ## compare models on the sliced frame
    cat("Comparison 1")
    for (s in c("SAN", "LAX", "OAK")) { ## The only factors in the sliced dataset
      leaf_assign1 <- h2o.predict_leaf_node_assignment(model1,df[df$Origin==s,])
      pred1 <- h2o.predict(model1,df[df$Origin==s,])
      leaf_assign2 <- h2o.predict_leaf_node_assignment(model2,df[df$Origin==s,])
      pred2 <- h2o.predict(model2,df[df$Origin==s,])
      expect_that(all(leaf_assign1 == leaf_assign2), is_true())
      expect_that(all(pred1 == pred2), is_true())
    }

    ## compare models on the full frame
    cat("Comparison 2")
    df = h2o.importFile(locate("smalldata/airlines/allyears2k_headers.zip"))
    for (s in c("SAN", "LAX", "OAK", "SFO", "ABQ", "IAH", "BOS", "LIH")) {
      leaf_assign1 <- h2o.predict_leaf_node_assignment(model1,df[df$Origin==s,])
      pred1 <- h2o.predict(model1,df[df$Origin==s,])
      leaf_assign2 <- h2o.predict_leaf_node_assignment(model2,df[df$Origin==s,])
      pred2 <- h2o.predict(model2,df[df$Origin==s,])
      expect_that(all(leaf_assign1 == leaf_assign2), is_true())
      expect_that(all(pred1 == pred2), is_true())
    }
}

doTest("Test GBM partial domains", test.partialDomains)
