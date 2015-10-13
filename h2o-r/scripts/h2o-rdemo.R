.origEchoValue <- getOption("echo")
options(echo=FALSE)

H2O.IP      <<- "127.0.0.1"
H2O.PORT    <<- 54321
DEMO        <<- FALSE
RESULTS.DIR <<- NULL

usage<-
function() {
  print("")
  print("Usage for:  R -f r_demo_runner.R --args [...options...]")
  print("")
  print("    --usecloud        connect to h2o on specified ip and port, where ip and port are specified as follows:")
  print("                      IP:PORT")
  print("")
  print("    --demo            the path to the h2o R demo.")
  print("")
  print("    --resultsDir      the results directory.")
  print("")
  q("no",1,FALSE)
}

unknownArg<-
function(arg) {
  print("")
  print(paste0("ERROR: Unknown argument: ",arg))
  print("")
  usage()
}

parseArgs<-
function(args) {
  i <- 1
  while (i <= length(args)) {
      s <- args[i]
      if (s == "--usecloud") {
        i <- i + 1
        if (i > length(args)) usage()
        argsplit <- strsplit(args[i], ":")[[1]]
        H2O.IP   <<- as.character(argsplit[1])
        H2O.PORT <<- as.numeric(argsplit[2])
      } else if (s == "--demo") {
        i <- i + 1
        if (i > length(args)) usage()
        DEMO <<- as.character(args[i])
      } else if (s == "--resultsDir") {
        i <- i + 1
        if (i > length(args)) usage()
        RESULTS.DIR <<- as.character(args[i])
      } else {
        unknownArg(s)
      }
      i <- i + 1
  }
}

removeH2OInit<-
function() {
    hackedDemo <- normalizePath(paste(RESULTS.DIR, .Platform$file.sep, DEMO, sep = ""))
    lines <- readLines(DEMO)
    lines <- lines[-which(sapply(lines, function(l) grepl("^h2o.init",l)))]
    writeLines(lines, hackedDemo)
    if (!file.exists(hackedDemo)) stop(paste0("Could not create file with h2o.init calls removed. Stopping."))
    return(hackedDemo)
}

runH2ORDemo <-
function() {
    parseArgs(commandArgs(trailingOnly=TRUE)) # provided by --args

    # verify h2o package is installed
    if (!"h2o" %in% rownames(installed.packages())) stop("The H2O package has not been installed on this system. Cannot execute the H2O R demo without it!")
    require(h2o)

    print(paste0("Connect to h2o on IP: ",H2O.IP,", PORT: ",H2O.PORT))
    h2o.init(ip=H2O.IP, port=H2O.PORT)

    h2o.startLogging(paste(RESULTS.DIR, "/rest.log", sep = ""))
    print(paste0("Started rest logging in ",RESULTS.DIR,"/rest.log."))

    # hack the demo script. remove instances of h2o.init(). save the hacked demo script in the results dir
    hackedDemo <- removeH2OInit()

    h2o.logAndEcho("------------------------------------------------------------")
    h2o.logAndEcho("")
    h2o.logAndEcho(paste("STARTING TEST: ", DEMO))
    h2o.logAndEcho("")
    h2o.logAndEcho("------------------------------------------------------------")

    # execute the demo
    source(hackedDemo)
}

runH2ORDemo()
options(echo=.origEchoValue)
