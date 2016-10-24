setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

check.deeplearning_multi_image_inception <- function() {
  if (!h2o.deepwater.available()) return()

  Log.info("Test checks if Deep Water works fine with a multiclass image dataset")
  
  df <- h2o.uploadFile(locate("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))
  print(head(df))
  path = 1 ## must be the first column
  response = 2

  hh <- h2o.deepwater(x=path, y=response, training_frame=df, epochs=50, learning_rate=1e-3, network="inception_bn")
  print(hh)

  ll = h2o.logloss(hh)
  checkTrue(ll <= 0.02, "Logloss is too high!")
}

doTest("Deep Water MultiClass Image Test", check.deeplearning_multi_image_inception)
