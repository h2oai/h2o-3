import sys
sys.path.insert(1, "../../../")
import h2o

#def fiftycatGBM(ip,port):
#  # Connect to h2o
#  h2o.init(ip,port)

h2o.init()

# Training set has 100 categories from cat001 to cat100
# Categories cat001, cat003, ... are perfect predictors of y = 1
# Categories cat002, cat004, ... are perfect predictors of y = 0

#Log.info("Importing bigcat_5000x2.csv data...\n")
bigcat = h2o.import_frame(path=h2o.locate("smalldata/gbm_test/bigcat_5000x2.csv"))
bigcat["y"] = bigcat["y"].asfactor()

#Log.info("Summary of bigcat_5000x2.csv from H2O:\n")
#bigcat.summary()

# Train H2O DRF Model:
#Log.info("H2O DRF (Naive Split) with parameters:\nclassification = TRUE, ntree = 1, depth = 1, nbins = 100\n")
model = h2o.random_forest(x=bigcat["X"], y=bigcat["y"], ntrees=1, max_depth=1, nbins=100)
model.show()

## Check AUC and overall prediction error at least as good with group split than without
#Log.info("Expect DRF with Group Split to give Perfect Prediction in Single Iteration")
#expect_true(drfmodel.grpsplit@model$auc == 1)
#expect_true(drfmodel.grpsplit@model$confusion[3,3] == 0)
#expect_true(drfmodel.grpsplit@model$auc >= drfmodel.nogrp@model$auc)
#expect_true(drfmodel.grpsplit@model$confusion[3,3] <= drfmodel.nogrp@model$confusion[3,3])

#if __name__ == "__main__":
#  h2o.run_test(sys.argv, bigcatGBM)
