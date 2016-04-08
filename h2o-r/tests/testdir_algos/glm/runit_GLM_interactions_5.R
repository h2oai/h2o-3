setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.glm.interactions5 <- function() {

  df <- as.h2o(iris[,c(5,1,2,3,4)])

  interactions <- model.matrix(Petal.Width ~ Species*Sepal.Length, data=iris)[,5:6]
  dzz <- data.frame(Species=iris[,5],
                   Species_Sepal.Length.versicolor = interactions[,1],
                   Species_Sepal.Length.virginica = interactions[,2],
                   Sepal.Length=iris[,"Sepal.Length"],
                   Sepal.Width=iris[,"Sepal.Width"],
                   Petal.Length=iris[,"Petal.Length"],
                   Petal.Width=iris[,"Petal.Width"])

  df2 <- as.h2o(dzz)


  r <- h2o.runif(df)
  train <- df[r<=0.8,]
  valid <- df[r>0.8,]

  print(train)
  print(valid)

  train2 <- df2[r<=0.8,]
  valid2 <- df2[r>0.8,]

  print(train2)
  print(valid2)

  m1 <- h2o.glm(x=1:4,y=5,training_frame=train, interactions=c(1,2), lambda=0,standardize=FALSE)
  m2 <- h2o.glm(x=1:6,y=7,training_frame=train2, lambda=0, standardize=FALSE)


  preds1 <- predict(m1,valid)
  preds2 <- predict(m2,valid2)

  expect_true(sum(preds1==preds2)==nrow(valid2))


  m1 <- h2o.glm(x=1:4,y=5,training_frame=train, validation_frame=valid, interactions=c(1,2), lambda=0, standardize=FALSE)
  m2 <- h2o.glm(x=1:6,y=7,training_frame=train2,validation_frame=valid2, lambda=0, standardize=FALSE)



  fold_column <- h2o.kfold_column(df,nfolds=3)

  m1 <- h2o.glm(x=1:4,y=5,training_frame=h2o.cbind(df,fold_column), interactions=c(1,2), lambda=0,standardize=TRUE, fold_column="C1")
  m2 <- h2o.glm(x=1:6,y=7,training_frame=h2o.cbind(df2,fold_column), lambda=0, standardize=TRUE, fold_column="C1")

}

doTest("Testing model accessors for GLM", test.glm.interactions5)
