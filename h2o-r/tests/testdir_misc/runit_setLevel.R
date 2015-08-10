setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.setLevel <- function() {
  library(MASS)
 
  # ---------------------------------------------------------------------------
  # 1. generate data
 
  set.seed(1)
  N <- 5000
  x1 <- runif(N)
  x2 <- runif(N)
  sigma <- matrix(c(1, 0.95, 0.95, 1), 2, 2)
  x34 <- mvrnorm(N, c(0, 0), sigma)
  x3 <- x34[, 1]
  x4 <- x34[, 2]
  x5 <- factor(sample(letters[1:3], N, replace = TRUE))
  x6 <- factor(sample(letters[1:3], N, replace = TRUE))
  x7 <- factor(sample(letters[1:10], N, replace = TRUE))
  expit <- function(x) 1 / (1 + exp(-x))
  p <- expit(-1 + 2 * x1 ^ 0.5 + 0.5 * log(1 + x2) - x3 ^ 2 + 
               c(-1, 0.5, 0)[as.numeric(x5)])
  y <- rbinom(N, 1, p)
  data <- data.frame(y = y, x1 = x1, x2 = x2, x3 = x3, x4 = x4, x5 = x5, 
                   x6 = x6, x7 = x7) 
  data.hex <- as.h2o(data)
  head(data.hex)
  str(data.hex)


  data.hex[,1] <- as.factor(data.hex[,1])

  # ---------------------------------------------------------------------------
  # 2. fit a gbm model
  fit.gbm <- h2o.gbm(y = 1, x = 2:8, distribution= "bernoulli", ntrees = 100,
                     training_frame= data.hex, max_depth=4, learn_rate= 0.03
                     )
  
  p1=predict(fit.gbm, data.hex)
  head(p1)
  # predict        X0        X1
  # 1       0 0.5448750 0.4551250
  # 2       0 0.7948446 0.2051553
  # 3       0 0.6927410 0.3072590
  # 4       0 0.5632815 0.4367184
  # 5       0 0.8724055 0.1275945
  # 6       0 0.5093251 0.4906749
  # ---------------------------------------------------------------------------
  
  
  # ---------------------------------------------------------------------------
  # 3. fix x5 column at "a"
  x5_original <- data.hex$x5
  data.hex$x5 <- h2o.setLevel(data.hex$x5, "a")
  head(data.hex)
  p2=predict(fit.gbm, data.hex)
  head(p2)
  # predict        X0        X1
  # 1       0 0.8122386 0.1877614
  # 2       0 0.7948446 0.2051553
  # 3       0 0.9031955 0.0968045
  # 4       0 0.6892335 0.3107665
  # 5       0 0.8724055 0.1275945
  # 6       0 0.6813020 0.3186980
  
  # 4. fix x5 at "b"
  # data.hex$x5 <- x5_original
  data.hex$x5 <- h2o.setLevel(data.hex$x5, "b")
  head(data.hex)
  p2=predict(fit.gbm, data.hex)
  head(p2)
  # predict        X0        X1
  # 1       0 0.5662285 0.4337715
  # 2       1 0.4209059 0.5790941
  # 3       0 0.6822745 0.3177256
  # 4       1 0.4773917 0.5226083
  # 5       0 0.6659734 0.3340266
  # 6       1 0.4713104 0.5286896
  data.hex$x5 <- h2o.setLevel(data.hex$x5, "c")
  head(data.hex)
  p2=predict(fit.gbm, data.hex)
  head(p2)
  # predict        X0        X1
  # 1       0 0.6266587 0.3733413
  # 2       0 0.5623179 0.4376821
  # 3       0 0.8490875 0.1509124
  # 4       0 0.5839345 0.4160654
  # 5       0 0.8178309 0.1821690
  # 6       0 0.5456495 0.4543505
  
  testEnd()
}

doTest("Import a dataset with a header H2OParsedData Object", test.setLevel)
