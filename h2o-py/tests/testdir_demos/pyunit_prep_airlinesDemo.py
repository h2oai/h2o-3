#----------------------------------------------------------------------
# Purpose:  Condition an Airline dataset by filtering out NAs where the
#           departure delay in the input dataset is unknown.
#
#           Then treat anything longer than minutesOfDelayWeTolerate
#           as delayed.
#----------------------------------------------------------------------

import sys
sys.path.insert(1, "../../")
import h2o

def prep_airlines(ip,port):
    # Connect to a pre-existing cluster
    h2o.init(ip,port)

    air = h2o.import_frame(h2o.locate("smalldata/airlines/allyears2k_headers.zip"))

    numRows, numCols = air.dim()

    x_cols = ["Month", "DayofMonth", "DayOfWeek", "CRSDepTime", "CRSArrTime", "UniqueCarrier", "CRSElapsedTime", "Origin", "Dest", "Distance"]
    y_col = "SynthDepDelayed"

    noDepDelayedNAs = air[air["DepDelay"].isna() == 0]
    print "Dimensions of new dataset: {0}".format(noDepDelayedNAs.dim())

    minutesOfDelayWeTolerate = 15
    noDepDelayedNAs.cbind(noDepDelayedNAs["DepDelay"] > minutesOfDelayWeTolerate)
    noDepDelayedNAs[numCols] = noDepDelayedNAs[numCols].asfactor()
    noDepDelayedNAs._vecs[numCols].setName(y_col)

    gbm = h2o.gbm(x=noDepDelayedNAs[x_cols], y=noDepDelayedNAs[y_col], loss="bernoulli")
    gbm.show()

if __name__ == "__main__":
    h2o.run_test(sys.argv, prep_airlines)
