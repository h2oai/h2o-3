setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

get_symbol <- function(num_classes = 1000) {
  library(mxnet)
  data <- mx.symbol.Variable('data')
  # first conv
  conv1 <- mx.symbol.Convolution(data = data, kernel = c(5, 5), num_filter = 20)

  tanh1 <- mx.symbol.Activation(data = conv1, act_type = "tanh")
  pool1 <- mx.symbol.Pooling(data = tanh1, pool_type = "max", kernel = c(2, 2), stride = c(2, 2))

  # second conv
  conv2 <- mx.symbol.Convolution(data = pool1, kernel = c(5, 5), num_filter = 50)
  tanh2 <- mx.symbol.Activation(data = conv2, act_type = "tanh")
  pool2 <- mx.symbol.Pooling(data = tanh2, pool_type = "max", kernel = c(2, 2), stride = c(2, 2))
  # first fullc
  flatten <- mx.symbol.Flatten(data = pool2)
  fc1 <- mx.symbol.FullyConnected(data = flatten, num_hidden = 500)
  tanh3 <- mx.symbol.Activation(data = fc1, act_type = "tanh")
  # second fullc
  fc2 <- mx.symbol.FullyConnected(data = tanh3, num_hidden = num_classes)
  # loss
  lenet <- mx.symbol.SoftmaxOutput(data = fc2, name = 'softmax')
  return(lenet)
}


check.deeplearning_multi_image_lenet <- function() {
  if (!h2o.deepwater.available()) return()

  Log.info("Test checks if Deep Water works fine with a multiclass image dataset")
  
  df <- h2o.uploadFile(locate("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))
  print(head(df))
  path = 1 ## must be the first column
  response = 2
  nclasses = nlevels(df[,response])

  network <- get_symbol(nclasses)
  cat(network$as.json(), file = "lenet.json", sep = '')

  hh <- h2o.deepwater(x=path, y=response, training_frame=df, epochs=50, learning_rate=1e-3, network_definition_file="lenet.json")
  print(hh)

  ll = h2o.logloss(hh)
  checkTrue(ll <= 0.02, "Logloss is too high!")
}

doTest("Deep Water MultiClass Image Test", check.deeplearning_multi_image_lenet)
