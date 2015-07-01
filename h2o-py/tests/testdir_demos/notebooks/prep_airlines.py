#----------------------------------------------------------------------
# Purpose:  Condition an Airline dataset by filtering out NAs where the
#           departure delay in the input dataset is unknown.
#
#           Then treat anything longer than minutesOfDelayWeTolerate
#           as delayed.
#----------------------------------------------------------------------
import h2o
air = h2o.import_frame(h2o.locate("smalldata/airlines/allyears2k_headers.zip"))
numRows, numCols = air.dim()
print "Original dataset rows: {0}, columns: {1}".format(numRows, numCols)

x_cols = ["Month", "DayofMonth", "DayOfWeek", "CRSDepTime", "CRSArrTime", "UniqueCarrier", "CRSElapsedTime", "Origin", "Dest", "Distance"]
y_col = "SynthDepDelayed"

noDepDelayedNAs = air[air["DepDelay"].isna() == 0]
rows, cols = noDepDelayedNAs.dim()
print "New dataset rows: {0}, columns: {1}".format(rows, cols)
minutesOfDelayWeTolerate = 15
noDepDelayedNAs.cbind(noDepDelayedNAs["DepDelay"] > minutesOfDelayWeTolerate)
noDepDelayedNAs[numCols] = noDepDelayedNAs[numCols-1].asfactor()
noDepDelayedNAs.setName(numCols,y_col)
gbm = h2o.gbm(x=noDepDelayedNAs[x_cols], y=noDepDelayedNAs[y_col], distribution="bernoulli")
gbm.show()
