setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test.GBM.interaction.constraints <- function() {
  prostate.hex <- h2o.importFile(locate("smalldata/logreg/prostate_train.csv"), destination_frame="prostate.hex")
  prostate.hex$CAPSULE <- as.factor(prostate.hex$CAPSULE)
  constraints <- list(list("AGE", "DPROS"), list("RACE", "PSA", "VOL"))
  ntrees <- 10
  prostate.h2o <- h2o.gbm(seed=1234, x=2:9, y="CAPSULE", training_frame=prostate.hex, distribution="bernoulli", 
                          ntrees=ntrees, max_depth=5, min_rows=10, learn_rate=0.1, 
                          interaction_constraints=constraints)
  importance <- as.data.frame(h2o.varimp(prostate.h2o))           
  print(importance)
  
  # GLEASON and DCAPS column should have importance == 0 
  expect_equal(importance[importance$variable == "DCAPS", "relative_importance"], 0)       
  expect_equal(importance[importance$variable == "GLEASON", "relative_importance"], 0)       

  # Check trees features
  for (i in seq(ntrees)) {
    tree <- h2o.getModelTree(model=prostate.h2o, tree_number=i)
    tree.features <- tree@features
    tree.features<-tree.features[!is.na(tree.features)]
    print(tree.features)
    expect_true(all(tree.features %in% c("AGE", "DPROS")) || all(tree.features %in% c("RACE", "PSA", "VOL")))
  }
}

doTest("GBM Test: interaction constraints", test.GBM.interaction.constraints)
