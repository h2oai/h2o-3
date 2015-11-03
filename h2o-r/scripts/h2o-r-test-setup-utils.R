#' Do not echo any loading of this file
.origEchoValue <- getOption("echo")
options(echo=FALSE)

#'
#'
#' ----------------- Global variables and accessors -----------------
#'
#'
H2O.IP                      <<- "127.0.0.1"
H2O.PORT                    <<- 54321
ON.HADOOP                   <<- FALSE
HADOOP.NAMENODE             <<- NULL
IS.RDEMO                    <<- FALSE
IS.RUNIT                    <<- FALSE
IS.RBOOKLET                 <<- FALSE
RESULTS.DIR                 <<- NULL
TEST.NAME                   <<- NULL
SEED                        <<- NULL
PROJECT.ROOT                <<- "h2o-3"

get.test.ip       <- function() return(H2O.IP)
get.test.port     <- function() return(H2O.PORT)
test.is.on.hadoop <- function() return(ON.HADOOP)
hadoop.namenode   <- function() return(HADOOP.NAMENODE)
test.is.rdemo     <- function() return(IS.RDEMO)
test.is.runit     <- function() return(IS.RUNIT)
test.is.rbooklet  <- function() return(IS.RBOOKLET)
results.dir       <- function() return(RESULTS.DIR)
test.name         <- function() return(TEST.NAME)
get.test.seed     <- function() return(SEED)
get.project.root  <- function() return(PROJECT.ROOT)

#'
#'
#' ----------------- Arg parsing -----------------
#'
#'
parseArgs<-
function(args) {
  i <- 1
  while (i <= length(args)) {
      s <- args[i]
      if (s == "--usecloud") {
        i <- i + 1
        if (i > length(args)) usage()
        argsplit <- strsplit(args[i], ":")[[1]]
        H2O.IP   <<- argsplit[1]
        H2O.PORT <<- as.numeric(argsplit[2])
      } else if (s == "--hadoopNamenode") {
        i <- i + 1
        if (i > length(args)) usage()
        HADOOP.NAMENODE <<- args[i]
      } else if (s == "--onHadoop") {
        ON.HADOOP <<- TRUE
      } else if (s == "--rDemo") {
        IS.RDEMO <<- TRUE
      } else if (s == "--rUnit") {
        IS.RUNIT <<- TRUE
      } else if (s == "--rBooklet") {
        IS.RBOOKLET <<- TRUE
      } else if (s == "--resultsDir") {
        i <- i + 1
        if (i > length(args)) usage()
        RESULTS.DIR <<- as.character(args[i])
      } else if (s == "--testName") {
        i <- i + 1
        if (i > length(args)) usage()
        TEST.NAME <<- args[i]
      } else {
        unknownArg(s)
      }
      i <- i + 1
  }
  if (sum(c(IS.RDEMO, IS.RUNIT, IS.RBOOKLET)) > 1) {
    print("Only one of the --rDemo, --rUnit, or --rBooklet options can be specified at a time.")
    usage()
  }
}

usage<-
function() {
  print("")
  print("Usage for:  R -f rtest.R --args [...options...]")
  print("")
  print("    --usecloud        connect to h2o on specified ip and port, where ip and port are specified as follows:")
  print("                      IP:PORT")
  print("")
  print("    --onHadoop        Indication that tests will be run on h2o multinode hadoop clusters.")
  print("                      `locate` and `sandbox` runit test utilities use this indication in order to")
  print("                      behave properly. --hadoopNamenode must be specified if --onHadoop option is used.")
  print("    --hadoopNamenode  Specifies that the runit tests have access to this hadoop namenode.")
  print("                      `hadoop.namenode` runit test utility returns this value.")
  print("")
  print("    --rDemo           test is R demo")
  print("")
  print("    --rUnit           test is R unit test")
  print("")
  print("    --rBooklet        test is R booklet")
  print("")
  print("    --resultsDir      the results directory.")
  print("")
  print("    --testName        name of the rdemo, runit, or rbooklet.")
  print("")
  q("no",1,FALSE) #exit with nonzero exit code
}

unknownArg<-
function(arg) {
  print("")
  print(paste0("ERROR: Unknown argument: ",arg))
  print("")
  usage()
}

options(echo=.origEchoValue)