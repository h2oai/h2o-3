####################################################################################################
##
## Testing splitFrame with larger datasets
## splitFrame should not take longer than parsing
##
####################################################################################################

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.splitframe <- function() {
  # add datasets here: paste( DIRECTORY, LIST OF FILES, sep = "/")
  datasets <- list(
    milsongs = file.path("milsongs", c("milsongs-test.csv.gz", "milsongs-train.csv.gz")),
    mnist    = file.path("mnist", c("test.csv.gz", "train.csv.gz")),
    KDDcup   = file.path("usecases", c("cup98LRN_z.csv", "cup98VAL_z.csv")),
    citibike = file.path("citibike-nyc", c("2013-07.csv", "2013-08.csv", "2013-09.csv", "2013-10.csv",
                                      "2013-11.csv", "2013-12.csv", "2014-01.csv", "2014-02.csv",
                                      "2014-03.csv", "2014-04.csv", "2014-05.csv", "2014-06.csv",
                                      "2014-07.csv", "2014-08.csv"))
    )

  for(data in names(datasets)) {
    # create list of explicit directory of files
    Log.info(paste("Import and split for", data))
    dirs <- unlist(lapply(file.path("bigdata","laptop", datasets[[data]]), locate))
    parse.time <- system.time(hex <- h2o.importFile(dirs))
    split.time <- system.time(spl <- h2o.splitFrame(hex, 0.75))

    Log.info(paste("Timings for", data))
    print(parse.time)
    print(split.time)

    # Parse time should be the upper bound for split frame
    # TODO: we commented-out this check because "performance" checks should be done in a non resource-contested
    # environment
    #expect_true(parse.time[3] >= split.time[3])

    # Remove unecessary datasets
    h2o.rm(hex)
    h2o.rm(spl[[1]])
    h2o.rm(spl[[2]])
    print("")
  }

  
}

doTest("Splitframe Testing with Timings vs Parsing", test.splitframe)
