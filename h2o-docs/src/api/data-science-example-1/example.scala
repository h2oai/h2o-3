//
// Load data
//

// Shortcut for local files
val air = new DataFrame(H2OFiles.get("allyears_tiny.csv"))
// Generic form to any datasource in form ''<schema>://location' :
// URI dataSourceURI = new java.net.URI("hdfs://mr-0xd6")
//
// val air = new DataFrame(d)
//
// Note: we should have h2o-specific schema for cluster data


//
// Generate a vector with uniform distribution, length of vector 
// and vector group are derived from given vector/frame
val airS = air ++ ('S, Vec.runif(air)) // Append vector at the end of frame to be usable in M/R tasks

// 
// Filtering and slicing
//
// Note: this is only idea based on Scalding+Shalala+h2o-dev-scala API
// 
//            Frame  Oper ColSelect (and output spec)   FUNC
//              |     |     |                             |
val airTrain = air filter ('S)                 { (s:Double) => s <= 0.8} 
val airValid = air filter ('S)                 { (s:Double) => s > 0.8 && s <= 0.9}
val airTest  = air filter ('S)                 { (s:Double) => s > 0.9 }

// Create Parameters for run
val gbmParams = new GBMParameters()
// Column selector
gbmParams._train = airTrain('Origin, 'Dest, 'Distance, 'UniqueCarrier, 'Month, 'DayofMonth, 'DayOfWeek)
gbmParams._valid = airValid // Do not need to select columns since algo will filter right one
gbmParams._response_column = 'IsDepDelayed
gbmParams._distribution = Distributions.MULTINOMIAL // enum
gbmParams._interaction_depth = 3
gbmParams._shrinkage = 0.01
gbmParams._importance = true

// Create builder
val gbm = new GBM(gbmParams)
// Invoke builder and get a model
val gbmModel = gbm.trainModel.get

// 
// Make a prediction
//  - use API call and select the right column with prediction
val pred = gbmModel.score()('predict)

