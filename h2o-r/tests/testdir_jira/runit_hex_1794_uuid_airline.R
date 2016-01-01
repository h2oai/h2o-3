setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Testing parsing, splitting, modelling, and computation on data with UUID column
##






test <- function() {
	print("Reading in data (tiny airline with UUIDs).")
		airline.hex = h2o.importFile(h2oTest.locate("smalldata/airlines/uuid_airline.csv"), destination_frame="airline.hex", header=TRUE)
		#print("Summary of airline data: ") ; summary(airline.hex)
		print("Head of airline data: ") ; print(head(airline.hex))

	print("Take subset of rows where UUID is present.")
		airline.uuid = airline.hex[!is.na(airline.hex$uuid),]
		print("Dimension of new set: ") ; print(dim(airline.uuid))
		print("Head of new set: ") ; print(head(airline.uuid))
		
	print("Take a random uniform test train split (30:70).")
		airline.uuid$split <- ifelse(h2o.runif(airline.uuid)>0.3, yes=1, no=0)
  print("Take a look at the head of the data with split col")
    print(head(airline.uuid))
		airline.train.hex <- h2o.assign(airline.uuid[airline.uuid$split==1,1:32],key="airline.train.hex")
		airline.test.hex <- h2o.assign(airline.uuid[airline.uuid$split==0,1:32],key="airline.test.hex")
		print("Dimension of training set: ") ; print(dim(airline.train.hex))
		print("Dimension of test set: ") ; print(dim(airline.test.hex))
		print("Head of training set: ") ; print(head(airline.train.hex))
		print("Head of test set: ") ; print(head(airline.test.hex))

	print("Define variables for x and y.")
	print(colnames(airline.hex))
	x = c("Year","Month","DayofMonth","DayOfWeek","UniqueCarrier","FlightNum","Origin","Dest","Distance")
	y = "IsArrDelayed" 

	#print("Run glm model on train set.")
	#	airline.glm <- h2o.glm(x=x, y=y, data=airline.train.hex,family="binomial")
	#	airline.glm
	#	
	#print("Extract UUIDs from test set.")
	#	test.uuid <- h2o.assign(airline.test.hex$uuid,key="test.uuid")
	#	print("Dimension of UUIDs from test set: ") ; dim(test.uuid)
	#	print("Head of UUIDs from test set: ") ; head(test.uuid)
	#	
	#print("Run GLM prediction on test set.")
	#	airline.predict.uuid <- h2o.predict(object=airline.glm, newdata=airline.test.hex)
	#	print("Head of prediction on test set: ") ; head(airline.predict.uuid)
	#	
	#print("Splice UUIDs back to predictions with cbind()")
	#	air.results <- h2o.assign(cbind(airline.predict.uuid, test.uuid), key="air.results")
	#	print("Head of predictions with UUIDs: ") ; head(air.results)
	#	print("Tail of predictions with UUIDs: ") ; tail(air.results)
	#	print("Summary of predictions with UUIDs: ") ; summary(air.results) 

	#print("Show distribution of predictions with quantile.")
	#	quantile.H2OH2OFrame(air.results$NO)

	#print("Extract strongest predictions.")
	#	top.air <- h2o.assign(air.results[air.results$NO > 0.75],key="top.air")
	#	print("Dimension of strongest predictions: ") ; dim(top.air)
	#	print("Head of strongest predictions: ") ; head(top.air)

  
}

h2oTest.doTest("Test parsing, splitting, modelling, and computation on data with UUID column", test)
