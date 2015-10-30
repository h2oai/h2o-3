library(h2o)
h2o.init()

print("Reading in data (tiny airline with UUIDs).")
airline.hex <- h2o.uploadFile(h2o:::.h2o.locate("smalldata/airlines/uuid_airline.csv"), destination_frame="airline.hex", header=TRUE)
print("Summary of airline data: ")
print(summary(airline.hex))
print("Head of airline data: ")
print(head(airline.hex))

print("Take subset of rows where UUID is present.")
airline.uuid <- airline.hex[!is.na(airline.hex$uuid),]
print("Dimension of new set: ")
print(dim(airline.uuid))
print("Head of new set: ")
print(head(airline.uuid))

print("Take a random uniform test train split (30:70).")

# fails with some seeds/bad splits. use fixed seed.
airline.uuid$split <- ifelse(h2o.runif(airline.uuid, seed=12345)>0.3, yes=1, no=0)
airline.train.hex <- h2o.assign(airline.uuid[airline.uuid$split==1,1:32],key="airline.train.hex")
airline.test.hex <- h2o.assign(airline.uuid[airline.uuid$split==0,1:32],key="airline.test.hex")
print("Dimension of training set: ")
dim(airline.train.hex)
print("Dimension of test set: ")
dim(airline.test.hex)
print("Head of training set: ")
head(airline.train.hex)
print("Head of test set: ")
head(airline.test.hex)

print("Define variables for x and y.")
colnames(airline.hex)
x <- c("Year","Month","DayofMonth","DayOfWeek","UniqueCarrier","FlightNum","Origin","Dest","Distance")
y <- "IsArrDelayed"

print("Run glm model on train set.")
airline.glm <- h2o.glm(x=x, y=y, training_frame=airline.train.hex,family="binomial")
airline.glm

print("Extract UUIDs from test set.")
test.uuid <- h2o.assign(airline.test.hex$uuid,key="test.uuid")
print("Dimension of UUIDs from test set: ")
dim(test.uuid)
print("Head of UUIDs from test set: ")
head(test.uuid)

print("Run GLM prediction on test set")
airline.predict.uuid <- predict(object=airline.glm, newdata=airline.test.hex)
print("Head of prediction on test set: ")
head(airline.predict.uuid)

print("Splice UUIDs back to predictions with h2o.cbind()")
air.results <- h2o.assign(h2o.cbind(airline.predict.uuid, test.uuid), key="air.results")
print("Head of predictions with UUIDs: ")
head(air.results)
print("Tail of predictions with UUIDs: ")
tail(air.results)
print("Summary of predictions with UUIDs: ")
summary(air.results)

print("Check performce and AUC")
perf <- h2o.performance(airline.glm,airline.test.hex)
print(perf)
perf@metrics$AUC

print("Show distribution of predictions with quantile.")
print(quant <- quantile(air.results$'YES'))
print("Extract strongest predictions.")
top.air <- h2o.assign(air.results[air.results$'YES' > quant['75%'], ],key="top.air")
print("Dimension of strongest predictions: ")
dim(top.air)
print("Head of strongest predictions: ")
head(top.air)