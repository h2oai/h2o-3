setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
#
# date parsing and field extraction tests
#





print_diff <- function(r, h2o) {
  if (!isTRUE(all.equal(r,h2o))) {
    h2oTest.logInfo(paste("R :", r))
    h2oTest.logInfo(paste("H2O :" , h2o))
  }
}


#
# This contains two tests.  
# The first reads a file into H2O with 10 columns 
# each with date format.  Some columns automatically parse, others need to
# use the as.Date method with a format string.  Both months and years
# are extracted into their own columns.  The same file is read in
# by R and the results are compared.  This tests that both R and H2O
# agree on dates, and that month and year methods work.
#
# The second test creates its own dataset. It tests all the parse options
# available to the format strings.  It also test corner cases in the time
# conversion/interpretation.
#
# hdf - dataframe on H2O server
# ldf - dataframe copied from H2O server
# rdf - dataframe created using only R native methods
#
datetest <- function(){
  h2oTest.logInfo('Test 1')
  print(h2o.getTimezone())
  h2oTest.logInfo('uploading date testing dataset')
  # Data file is 10 columns of dates, each column in a different format
  hdf <- h2o.importFile(normalizePath(h2oTest.locate('smalldata/jira/v-11.csv')))

  h2oTest.logInfo('data as loaded into h2o:')
  h2oTest.logInfo(head(hdf))

  # Columns 1,5-10 are not automatically interpreted by H2O as dates
  # column 1 is integer days since epoch (or since any other date);
  # column 5-10 are dates formatted as %d/%m/%y (in strptime format strings)
  summary(hdf)

  h2oTest.logInfo('Converting columns 5-10 to date columns')
  # h2o automagically recognizes and if it doesn't recognize,
  # you need to call as.Date to convert the values to dates
  hdf$ds5 <- as.Date(hdf$ds5, "%d/%m/%y %H:%M")
  hdf$ds6 <- as.Date(hdf$ds6, "%d/%m/%Y %H:%M:%S")
  hdf$ds7 <- as.Date(hdf$ds7, "%m/%d/%y")
  hdf$ds8 <- as.Date(hdf$ds8, "%m/%d/%Y")
  hdf$ds9 <- as.Date(as.factor(hdf$ds9), "%Y%m%d")
  hdf$ds10 <- as.Date(hdf$ds10, "%Y_%m_%d")

  h2oTest.logInfo('extracting year and month from posix date objects')
  # extract year from each date and put in its own column (on server)
  for( i in 2:10) {
    hdf[[paste("year",i,sep="")]] <- year(hdf[[paste("ds",i,sep="")]])
  }

  # extract month from each date and put in its own column (on server)
  for( i in 2:10) {
    hdf[[paste("month",i,sep="")]] <- month(hdf[[paste("ds",i,sep="")]])
  }
  
  # extract year & month from each date and put total month count in its own column (on server)
  for( i in 2:10) {
    hdf[[paste("idx",i,sep="")]] <- year(hdf[[paste("ds",i,sep="")]]) * 12 + month(hdf[[paste("ds",i,sep="")]])
  }
  #server dataframe is now 37 columns, 10 dates, 9 years, 9 months, 9 totalmonths
  
  # set the column names for the new columns
  cc <- colnames(hdf)
  nn <- c( paste('year', 2:10, sep=''), paste('month', 2:10, sep=''), paste('idx', 2:10, sep='') )
  cc[ (length(cc) - length(nn) + 1):length(cc) ] <- nn
  colnames(hdf) <- cc

  h2oTest.logInfo('Creating a local dataframe from H2O frame')
  ldf <- as.data.frame( hdf )

  # build the truth using R internal date fns
  rdf <- read.csv(h2oTest.locate('smalldata/jira/v-11.csv'))
  rdf$ds1 <- as.Date(rdf$ds1, origin='1970-01-01')
  rdf$ds2 <- as.Date(rdf$ds2, format='%Y-%m-%d')
  rdf$ds3 <- as.Date(rdf$ds3, format='%d-%b-%y')
  rdf$ds4 <- as.Date(rdf$ds4, format='%d-%B-%Y')
  rdf$ds5 <- as.Date(rdf$ds5, format='%d/%m/%y %H:%M')
  rdf$ds6 <- as.Date(rdf$ds6, format='%d/%m/%Y %H:%M:%S')
  rdf$ds7 <- as.Date(rdf$ds7, format='%m/%d/%y')
  rdf$ds8 <- as.Date(rdf$ds8, format='%m/%d/%Y')
  rdf$ds9 <- as.Date(as.factor(rdf$ds9), format='%Y%m%d')
  rdf$ds10 <- as.Date(rdf$ds10, format='%Y_%m_%d')

  # create year, month, and totalmonth columns for R's version of the data
  years <- data.frame(lapply(rdf, function(x) as.POSIXlt(x)$year))
  colnames(years) <- paste(rep("year",10),c(1:10),sep="")

  # as.POSIX.lt(x).mon returns a 0-11 range, but the R/H2O month(x) method returns 1-12
  months <- data.frame(lapply(rdf, function(x) as.POSIXlt(x)$mon + 1))
  colnames(months) <- paste(rep("month",10),c(1:10),sep="")
  
  idx <- 12*years + months
  colnames(idx) <- paste(rep("idx",10),c(1:10),sep="")
  
  rdf <- cbind(rdf, years, months, idx)

  #Compare the results imported from H2O (ldf) to R's results (rdf)
  h2oTest.logInfo('testing correctness')
  for (i in 2:10) {
    print_diff(rdf[[paste("year",i,sep="")]], ldf[[paste("year",i,sep="")]])
    expect_that(ldf[[paste("year",i,sep="")]], equals(rdf[[paste("year",i,sep="")]]))
    print_diff(rdf[[paste("month",i,sep="")]], ldf[[paste("month",i,sep="")]])
    expect_that(ldf[[paste("month",i,sep="")]], equals(rdf[[paste("month",i,sep="")]]))
    print_diff(rdf[[paste("idx",i,sep="")]], ldf[[paste("idx",i,sep="")]])    
    expect_that(ldf[[paste("idx",i,sep="")]], equals(rdf[[paste("idx",i,sep="")]]))
  }
  
  
  h2oTest.logInfo('Test 2')
  origTZ = h2o.getTimezone()
  #test 1
  h2o.setTimezone("America/Los_Angeles")
  print(h2o.getTimezone())
  ## Col 1-10 test all different parse options, rows test some corner cases
  ## Rows 1,2 test 1969/2068 inference
  formats = c("%c %z", "%a %d %m %y %H:%M:%S %z", "%A %m %d %Y %k", "%b %d %C %y %I %p", "%e %B, %Y %l %p", "%h-%e, %y %r", "%D %H_%M", "%F %H", "%H:%M %j %Y", "%d_%m_%y %T", "%d%m%y %R")
  c1 = c("Fri Jan 10 00:00:00 1969 -0800", "Tue Jan 10 04:00:00 2068 -0800", "Mon Dec 30 01:00:00 2002 -0800", "Wed Jan 1 12:00:00 2003 -0800")
  # create local data frame
  c1dates = strptime(c1, formats[1], tz="America/Los_Angeles")
  # reprint dates into different formats
  ldf = data.frame(c1)
  for (i in 2:11) {
    ldf[[paste("c",i,sep="")]] <- strftime(c1dates, formats[[i]], tz="America/Los_Angeles")
  }
  
  #load data frame into H2O
  hdf = as.h2o(ldf, "hdf")
  #parse strings and enums into dates on H2O
  for (i in 1:11) {
    hdf[[paste("c",i,sep="")]] <- as.Date(hdf[[paste("c",i,sep="")]], formats[[i]])
  }
  
  # convert dates in R into milliseconds
  lmillis <- data.frame(as.vector(unclass(as.POSIXct(c1dates, formats[1], tz="America/Los_Angeles")) * 1000))
  
  #pull results back from H2O
  ldf <- as.data.frame(hdf)
  # compare milliseconds from R with those from H2O
  for (i in 1:11) {
    print_diff(lmillis[,1], ldf[[i]])
    expect_that(lmillis[,1], equals(ldf[[i]]))
  }

  h2o.setTimezone(origTZ)
  print(h2o.getTimezone())
}


h2oTest.doTest('date testing', datetest)
