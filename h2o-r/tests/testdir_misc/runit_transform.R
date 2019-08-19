setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.model.transform <- function() {
    data <- h2o.importFile(path = locate('smalldata/testng/airlines_train.csv'))
    cols <- c("Origin", "Distance")
      
    # GBM
    model <- h2o.gbm(x=cols, y = "IsDepDelayed", training_frame = data, ntrees = 1)
    transformation <- h2o.transform(model, data);
    prediction <- h2o.predict(model, data)
    expect_equal(transformation, prediction)
    
    # GLM
    model <- h2o.glm(x=cols, y = "IsDepDelayed", training_frame = data, family = "binomial")
    transformation <- h2o.transform(model, data);
    prediction <- h2o.predict(model, data)
    expect_equal(transformation, prediction)
    
    # XGBoost
    model <- h2o.xgboost(x=cols, y = "IsDepDelayed", training_frame = data, ntrees = 1)
    transformation <- h2o.transform(model, data);
    prediction <- h2o.predict(model, data)
    expect_equal(transformation, prediction)
    
    # Isolation Forest
    model <- h2o.isolationForest(x=cols, training_frame = data)
    transformation <- h2o.transform(model, data);
    prediction <- h2o.predict(model, data)
    expect_equal(transformation, prediction)
        
    # Isolation Forest
    model <- h2o.randomForest(x=cols, y = "IsDepDelayed", training_frame = data)
    transformation <- h2o.transform(model, data);
    prediction <- h2o.predict(model, data)
    expect_equal(transformation, prediction)
    
}

doTest("Target Encoder Model test", test.model.transform )
