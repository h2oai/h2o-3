setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
######################################################################
# Test for PUB-826
# Check the nfold CM to see if there's clumping of the responses
######################################################################


options(echo=TRUE)


test.pub.826 <- function() {
  h2oTest.logInfo('Importing the airlines data from smalldata.')
  flights <- h2o.importFile(normalizePath(h2oTest.locate('smalldata/airlines/allyears2k_headers.zip')), 'air')

  h2oTest.logInfo('Print head of dataset')
  h2oTest.logInfo(head(flights))

  vars <- colnames(flights)

  # Suggested Explanatory Variables:
  FlightDate     <- vars[1:4]        # "Year", "Month", "DayofMonth", "DayOfWeek"
  ScheduledTimes <- vars[c(6,8,13)]  # "CRSDepTime", "CRSArrTime", "CRSElapsedTime"
  FlightInfo     <- vars[c(9,18,19)] # "UniqueCarrier", "Dest", "Distance"

  # Response
  Delayed        <- vars[31]         # "IsDepDelayed"

  m <- h2o.randomForest(x = c(FlightDate, ScheduledTimes, FlightInfo), y = Delayed, training_frame = flights, nfold = 3)

  show(m)
  
}

h2oTest.doTest("PUB-826: nfold cross validation doesn't work correctly", test.pub.826)
