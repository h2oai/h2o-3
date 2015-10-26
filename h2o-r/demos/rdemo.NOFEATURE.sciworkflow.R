library(h2o)
h2o.init()

print("Reading in prostate dataset")
pros.hex <- h2o.importFile(normalizePath(h2o:::.h2o.locate("smalldata/logreg/prostate.csv")), destination_frame="pros.hex")
print ("Run summary")
summary(pros.hex)
print("Summary of a column")
print(summary(pros.hex$CAPSULE))
print("Convert a column to factor")
pros.hex$CAPSULE <- as.factor(pros.hex$CAPSULE)
print("print the summary again")
print(summary(pros.hex$CAPSULE))
print("Print quantile of a column")
print(quantile(pros.hex$AGE,probs=seq(0,1,.1)))

print("split frame into test train")
#  a <- h2o.splitFrame(pros.hex,ratios=c(.2),shuffle=T)
# print("print dimension and assign to test and train")
# print(dim(a[[1]]))
# print(dim(a[[2]]))
# pros.train <- a[[2]]
# pros.test <- a[[1]]
sid <- h2o.runif(pros.hex)
pros.train <- pros.hex[sid > 0.2, ]
pros.test <- pros.hex[sid <= 0.2, ]

myX <- c("AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON")
myY <- "CAPSULE"

#GLM
print("Build GLM model")
my.glm <- h2o.glm(x=myX, y=myY, training_frame=pros.train, family="binomial",standardize=T,
  lambda_search=T) #TODO: something return_all_lambdas = T?
print(my.glm)

print("This is the best model")
best_model <- my.glm@best_model
print(best_model)

print("predict on best lambda model")
pred <- predict(my.glm@models[[best_model]],pros.test)
print(head(pred))

print("print performance and AUC")
perf <- h2o.performance(pred$'1',pros.test$CAPSULE )
print(perf)
print(perf@model$AUC)
plot(perf,type="roc")

result_frame <- data.frame(id = 0,auc = 0 , key = 0)

print("print performance for all models on test set")
for(i in 1:100){
  pred <- predict(my.glm@models[[i]],pros.test)
  perf <- h2o.performance(pred$'1',pros.test$CAPSULE )
  print ( paste ("  model number:", i, "  AUC on test set: ", round(perf@model$AUC, digits=4),  sep=''), quote=F)
  result_frame <- rbind(result_frame, c(i,round(perf@model$AUC, digits=4),my.glm@models[[i]]@key))
}

result_frame <- result_frame[-1,]
result_frame
print("order the results by AUC on test set")
ordered_results <- result_frame[order(result_frame$AUC,decreasing=T),]
ordered_results
print("get the model that gives the best prediction using the AUC score")
glm_best_model <- h2o.getModel(model_id= ordered_results[1,"key"])
print(glm_best_model)

#GBM
print("Grid search gbm")
pros.gbm <- h2o.gbm(x = myX, y = myY, loss = "bernoulli", data = pros.train, n.trees = c(50,100),n.minobsinnode=1,
                    interaction.depth = c(2,3), shrinkage = c(0.01,.001), n.bins = c(20), importance = F)
pros.rf <- h2o.randomForest(x=myX,y=myY,data=pros.train,classification=T,ntree=c(5,10),depth=10,mtries=c(2,5),importance=F, type = "BigData")
print(pros.gbm)
pros.gbm@sumtable
print("number of models built")
num_models <- length(pros.gbm@sumtable)
print(num_models)

print("Scoring")
for ( i in 1:num_models ) {
  #i=1
  model <- pros.gbm@model[[i]]
  pred <- predict( model, pros.test )
  perf <- h2o.performance ( pred$'1', pros.test$CAPSULE, measure="F1" )

  print ( paste ( pros.gbm@sumtable[[i]]$model_key, " trees:", pros.gbm@sumtable[[i]]$n.trees,
                  " depth:", pros.gbm@sumtable[[i]]$interaction.depth,
                  " shrinkage:", pros.gbm@sumtable[[i]]$shrinkage,
                  " min row: ", pros.gbm@sumtable[[i]]$n.minobsinnode,
                  " bins:", pros.gbm@sumtable[[i]]$nbins,
                  " AUC:", round(perf@model$AUC, digits=4), sep=''), quote=F)
}

print(" Performance measure on a test set ")
model <- pros.gbm@model[[1]] #  my.glm@models[[80]], pros.rf@model[[1]]
pred <- predict ( model, pros.test )
perf <- h2o.performance ( pred$'1', pros.test$CAPSULE, measure="F1" )
print(perf)