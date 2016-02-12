setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

gbm.quantile.test = function(){
  set.seed(1)
  
  # create some data
  N <- 1000
  X1 <- runif(N)
  X2 <- 2*runif(N)
  X3 <- factor(sample(letters[1:4],N,replace=T))
  X4 <- factor(sample(letters[1:6],N,replace=T))
  X5 <- factor(sample(letters[1:3],N,replace=T))
  X6 <- 3*runif(N)
  mu <- c(-1,0,1,2)[as.numeric(X3)]
  
  SNR <- 10 # signal-to-noise ratio
  Y <- X1**1.5 + 2 * (X2**.5) + mu
  sigma <- sqrt(var(Y)/SNR)
  Y <- Y + rnorm(N,0,sigma)
  
  # create a bunch of missing values
  X1[sample(1:N,size=100)] <- NA
  X3[sample(1:N,size=300)] <- NA
  
  # bind all fields together to make a dataframe
  data <- data.frame(Y=Y,X1=X1,X2=X2,X3=X3,X4=X4,X5=X5,X6=X6)
  
  # call on h2o, build h2o frame, assign proper predictor and outcome columns
  data.hex = as.h2o(data)
  featureList=2:6
  resp=1
  N=1
  M=1
  Q=0.2
  
  # run gbm with 1 tree and max_depth 1
  m<-h2o.gbm(x=featureList,y=resp,
             training_frame = data.hex,
             quantile_alpha = Q,distribution = "quantile",
             model_id = "m",ntrees=N, learn_rate = 1, max_depth = M)
  
  #download pojo and stroe splits
  cat("POJO output from Quantile GBM with ntree = 1, max_depth = 1, and quantile_alpha = 0.2")
  h2o.download_pojo(m)
  
  #Below comment is for reference of POJO:
  #class m_Tree_0_class_0 {
  #  static final double score0(double[] data) {
  #    double pred =  (data[1 /* X2 */] <0.61050755f ? -0.9194646f : 0.45379564f);
  #    return pred;
  #  }
  #}
  
  CUT = 0.61050755
  DIM = 3 
  LEFT_POJO = -0.9194646
  RIGHT_POJO = 0.45379564
  
  # cut frame by CUT (left and right), which is from POJO
  data.hex.test.left = data.hex[data.hex[,DIM] < CUT,]
  data.hex.test.right = data.hex[data.hex[,DIM] >= CUT,]
  
  #a test to look at h2o.quantile() - init_F is equal to POJO splits for one node.
  left_q = h2o.quantile(data.hex.test.left[,resp],Q) 
  left_q_test = left_q - m@model$init_f
  left_q_test
  LEFT_POJO
  expect_true(round(left_q_test,7) == round(LEFT_POJO,7), "Quantile GBM left split from POJO does not agree with h2o.quantile()! Please check previous POJO output.")
  
  right_q = h2o.quantile(data.hex.test.right[,resp],Q) 
  right_q_test = right_q - m@model$init_f
  right_q_test
  RIGHT_POJO
  expect_true(round(right_q_test,7) == round(RIGHT_POJO,7), "Quantile GBM right split from POJO does not agree with h2o.quantile()! Please check previous POJO output.")
  
  ## Make weighted version of left/right splits and test h2o.quantile() - init_F is equal to POJO splits for one node
  data.hex.test.left.weighted = h2o.cbind(data.hex,ifelse(data.hex[,DIM] < CUT,1,0))
  data.hex.test.right.weighted = h2o.cbind(data.hex,ifelse(data.hex[,DIM] >= CUT,1,0))
  left_q_weight = h2o.quantile(data.hex.test.left.weighted,Q,weights_column = "C1")[resp] 
  left_q_test_weight = left_q - m@model$init_f
  left_q_test_weight
  LEFT_POJO
  expect_true(round(left_q_test_weight,7) == round(LEFT_POJO,7), "Quantile GBM weighted left split from POJO does not agree with h2o.quantile()! Please check previous POJO output.")
  
  right_q_weight = h2o.quantile(data.hex.test.right.weighted,Q,weights_column = "C1")[resp] 
  right_q_test_weight = right_q_weight - m@model$init_f
  right_q_test_weight
  RIGHT_POJO
  expect_true(round(right_q_test_weight,7) == round(RIGHT_POJO,7), "Quantile GBM weighted right split from POJO does not agree with h2o.quantile()! Please check previous POJO output.")
  
}

doTest("GBM Quantile Test: Synthetic Data", gbm.quantile.test)
