// Row line-by-line input data
val rawAirData = sc.textFile(SparkFiles.get("allyears_tiny.csv"), /* # partitions */ 3)
// Produce RDD[Flight], Flight is POJO
val air /*:RDD[Flight] */ = rawAirData
                    .map(_.split(","))
                    .map(row => AirParser(row))
                    .filter(!_.isWrongRow()) // <- optional step

// Note: this is not correct, since MLLib algos expects 
// ---> RDD[LabeledPoint], RDD[Vector], SchemaRDD <: RDD[Row]
//

val ( airTrain, airValid, airTest) = air.randomSplit(Array(0.8,0.1,0.1),
  seed=42)

val treeStrategy = new Strategy(
                    algo = Classification, 
                    impurity = Variance, 
                    ntree = 10,
                    maxDepth = 3,
                    numClassesForClassification = 2, 
                    categoricalFeaturesInfo = Map.empty,
                    subsamplingRate = subsamplingRate)

val boostingStrategy = new BoostingStrategy(treeStrategy, LogLoss, numIterations, learningRate)

val gbt = GradientBoostedTrees.train(airTrain, boostingStrategy)
//
// Note: Manual validation
//
//
TODO??? make prediction on validation data and compute error

// Predict
val prediction:RDD[Double] = model.predict(airTest)

//
// Help datastructure
//
case class Flight (val Year              :Option[Int],
                val Month             :Option[Int],
                val DayofMonth        :Option[Int],
                val DayOfWeek         :Option[Int],
                val DepTime           :Option[Int],
                val CRSDepTime        :Option[Int],
                val ArrTime           :Option[Int],
                val CRSArrTime        :Option[Int],
                val UniqueCarrier     :Option[String],
                val FlightNum         :Option[Int],
                val TailNum           :Option[Int],
                val ActualElapsedTime :Option[Int],
                val CRSElapsedTime    :Option[Int],
                val AirTime           :Option[Int],
                val ArrDelay          :Option[Int],
                val DepDelay          :Option[Int],
                val Origin            :Option[String],
                val Dest              :Option[String],
                val Distance          :Option[Int],
                val TaxiIn            :Option[Int],
                val TaxiOut           :Option[Int],
                val Cancelled         :Option[Int],
                val CancellationCode  :Option[Int],
                val Diverted          :Option[Int],
                val CarrierDelay      :Option[Int],
                val WeatherDelay      :Option[Int],
                val NASDelay          :Option[Int],
                val SecurityDelay     :Option[Int],
                val LateAircraftDelay :Option[Int],
                val IsArrDelayed      :Option[Boolean],
                val IsDepDelayed      :Option[Boolean]) 
