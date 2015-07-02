#
# date parsing and field extraction tests
#


setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')



datetest <- function(conn){
  Log.info('uploading date testing dataset')
  hdf <- h2o.importFile(conn, normalizePath(locate('smalldata/jira/v-11.csv')))
  # df should be 5 columns: ds1:5

  Log.info('data as loaded into h2o:')
  Log.info(head(hdf))

  # NB: columns 1,5 are currently unsupported as date types
  # that is, h2o cannot understand:
  # 1 integer days since epoch (or since any other date);
  # 2 dates formatted as %d/%m/%y (in strptime format strings)
  summary(hdf)

  Log.info('adding date columns')
  # NB: h2o automagically recognizes and if it doesn't recognize, you're out of luck
  hdf$ds5 <- as.Date(hdf$ds5, "%d/%m/%y %H:%M")
  hdf$ds6 <- as.Date(hdf$ds6, "%d/%m/%Y %H:%M:%S")
  hdf$ds7 <- as.Date(hdf$ds7, "%m/%d/%y")
  hdf$ds8 <- as.Date(hdf$ds8, "%m/%d/%Y")
  hdf$ds9 <- as.Date(as.factor(hdf$ds9), "%Y%m%d")
  hdf$ds10 <- as.Date(hdf$ds10, "%Y_%m_%d")

  Log.info('extracting year and month from posix date objects')
  hdf$year2 <- year(hdf$ds2)
  hdf$year3 <- year(hdf$ds3)
  hdf$year4 <- year(hdf$ds4)
  hdf$year5 <- year(hdf$ds5)
  hdf$year6 <- year(hdf$ds6)
  hdf$year7 <- year(hdf$ds7)
  hdf$year8 <- year(hdf$ds8)
  hdf$year9 <- year(hdf$ds9)
  hdf$year10 <- year(hdf$ds10)
  hdf$mon2 <- month(hdf$ds2)
  hdf$mon3 <- month(hdf$ds3)
  hdf$mon4 <- month(hdf$ds4)
  hdf$mon5 <- month(hdf$ds5)
  hdf$mon6 <- month(hdf$ds6)
  hdf$mon7 <- month(hdf$ds7)
  hdf$mon8 <- month(hdf$ds8)
  hdf$mon9 <- month(hdf$ds9)
  hdf$mon10 <- month(hdf$ds10)
  hdf$idx2 <- year(hdf$ds2) * 12 + month(hdf$ds2)
  hdf$idx3 <- year(hdf$ds3) * 12 + month(hdf$ds3)
  hdf$idx4 <- year(hdf$ds4) * 12 + month(hdf$ds4)
  hdf$idx5 <- year(hdf$ds5) * 12 + month(hdf$ds5)
  hdf$idx6 <- year(hdf$ds6) * 12 + month(hdf$ds6)
  hdf$idx7 <- year(hdf$ds7) * 12 + month(hdf$ds7)
  hdf$idx8 <- year(hdf$ds8) * 12 + month(hdf$ds8)
  hdf$idx9 <- year(hdf$ds9) * 12 + month(hdf$ds9)
  hdf$idx10 <- year(hdf$ds10) * 12 + month(hdf$ds10)

  cc <- colnames(hdf)
  nn <- c( paste('year', 2:10, sep=''), paste('month', 2:10, sep=''), paste('idx', 2:10, sep='') )
  cc[ (length(cc) - length(nn) + 1):length(cc) ] <- nn
  colnames(hdf) <- cc

  Log.info('pulling year/month indices local')
  ldf <- as.data.frame( hdf )

  # build the truth using R internal date fns
  rdf <- read.csv(locate('smalldata/jira/v-11.csv'))
  rdf$days1 <- as.Date(rdf$ds1, origin='1970-01-01')
  rdf$days2 <- as.Date(rdf$ds2, format='%Y-%m-%d')
  rdf$days3 <- as.Date(rdf$ds3, format='%d-%b-%y')
  rdf$days4 <- as.Date(rdf$ds4, format='%d-%B-%Y')
  rdf$days5 <- as.Date(rdf$ds5, format='%d/%m/%y %H:%M')
  rdf$days6 <- as.Date(rdf$ds6, format='%d/%m/%Y %H:%M:%S')
  rdf$days7 <- as.Date(rdf$ds7, format='%m/%d/%y')
  rdf$days8 <- as.Date(rdf$ds8, format='%m/%d/%Y')
  rdf$days9 <- as.Date(as.factor(rdf$ds9), format='%Y%m%d')
  rdf$days10 <- as.Date(rdf$ds10, format='%Y_%m_%d')

  months <- data.frame(lapply(rdf[,11:20], function(x) as.POSIXlt(x)$mon+1))
  years <- data.frame(lapply(rdf[,11:20], function(x) as.POSIXlt(x)$year))
  idx <- 12*years + months

  Log.info('testing correctness')
  expect_that( ldf$year2, equals(years[,2]) )
  expect_that( ldf$year3, equals(years[,3]) )
  expect_that( ldf$year4, equals(years[,4]) )
  expect_that( ldf$year5, equals(years[,5]) )
  expect_that( ldf$year6, equals(years[,6]) )
  expect_that( ldf$year7, equals(years[,7]) )
  expect_that( ldf$year8, equals(years[,8]) )
  expect_that( ldf$year9, equals(years[,9]) )
  expect_that( ldf$year10, equals(years[,10]) )

  expect_that( ldf$month2, equals(months[,2]) )
  expect_that( ldf$month3, equals(months[,3]) )
  expect_that( ldf$month4, equals(months[,4]) )
  expect_that( ldf$month5, equals(months[,5]) )
  expect_that( ldf$month6, equals(months[,6]) )
  expect_that( ldf$month7, equals(months[,7]) )
  expect_that( ldf$month8, equals(months[,8]) )
  expect_that( ldf$month9, equals(months[,9]) )
  expect_that( ldf$month10, equals(months[,10]) )

  expect_that( ldf$idx2, equals(idx[,2]) )
  expect_that( ldf$idx3, equals(idx[,3]) )
  expect_that( ldf$idx4, equals(idx[,4]) )
  expect_that( ldf$idx5, equals(idx[,5]) )
  expect_that( ldf$idx6, equals(idx[,6]) )
  expect_that( ldf$idx7, equals(idx[,7]) )
  expect_that( ldf$idx8, equals(idx[,8]) )
  expect_that( ldf$idx9, equals(idx[,9]) )
  expect_that( ldf$idx10, equals(idx[,10]) )

  
  ## Col 1-10 test all different parse options, rows test some corner cases
  ## Row 1/2 test 1969/2068 inference
  formats = c("%c %z", "%a %d %m %y %H:%M:%S %z", "%A %m %d %Y %k", "%b %d %C %y %I %p", "%e %B, %Y %l %p", "%h-%e, %y %r", "%D %H_%M", "%F %H", "%H:%M %j %Y", "%d_%m_%y %T", "%d%m%y %R")
  c1 = c("Fri Jan 10 00:00:00 1969 -0800", "Tue Jan 10 04:00:00 2068 -0800", "Mon Dec 30 01:00:00 2002 -0800", "Wed Jan 1 12:00:00 2003 -0800")
  c1dt = strptime(c1, formats[1], tz="America/Los_Angeles")
  c2 = strftime(c1dt, formats[2], tz="America/Los_Angeles")
  c3 = strftime(c1dt, formats[3], tz="America/Los_Angeles")
  c4 = strftime(c1dt, formats[4], tz="America/Los_Angeles")
  c5 = strftime(c1dt, formats[5], tz="America/Los_Angeles")
  c6 = strftime(c1dt, formats[6], tz="America/Los_Angeles")
  c7 = strftime(c1dt, formats[7], tz="America/Los_Angeles")
  c8 = strftime(c1dt, formats[8], tz="America/Los_Angeles")
  c9 = strftime(c1dt, formats[9], tz="America/Los_Angeles")
  c10 = strftime(c1dt, formats[10], tz="America/Los_Angeles")
  c11 = strftime(c1dt, formats[11], tz="America/Los_Angeles")
  
  ldf = data.frame(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10, c11)
  hdf = as.h2o(conn, ldf, "hdf")
  hdf$c1 = as.Date(hdf$c1, formats[1])
  hdf$c2 = as.Date(hdf$c2, formats[2])
  hdf$c3 = as.Date(hdf$c3, formats[3])
  hdf$c4 = as.Date(hdf$c4, formats[4])
  hdf$c5 = as.Date(hdf$c5, formats[5])
  hdf$c6 = as.Date(hdf$c6, formats[6])
  hdf$c7 = as.Date(hdf$c7, formats[7])
  hdf$c8 = as.Date(hdf$c8, formats[8])
  hdf$c9 = as.Date(hdf$c9, formats[9])
  hdf$c10 = as.Date(hdf$c10, formats[10])
  hdf$c11 = as.Date(hdf$c11, formats[11])
                   
  lmillis = data.frame(as.vector(unclass(as.POSIXct(c1dt, formats[1], tz="America/Los_Angeles")) * 1000))
  res = as.data.frame(hdf)
  expect_that(lmillis[,1], equals(res[,1]))
  expect_that(lmillis[,1], equals(res[,2]))
  expect_that(lmillis[,1], equals(res[,3]))
  expect_that(lmillis[,1], equals(res[,4]))
  expect_that(lmillis[,1], equals(res[,5]))
  expect_that(lmillis[,1], equals(res[,6]))
  expect_that(lmillis[,1], equals(res[,7]))
  expect_that(lmillis[,1], equals(res[,8]))
  expect_that(lmillis[,1], equals(res[,9]))
  expect_that(lmillis[,1], equals(res[,10]))
  expect_that(lmillis[,1], equals(res[,11]))
  
  testEnd()
}


doTest('date testing', datetest)
