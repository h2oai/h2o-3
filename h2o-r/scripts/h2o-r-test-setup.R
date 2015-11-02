#' Do not echo any loading of this file
.origEchoValue <- getOption("echo")
options(echo=FALSE)
options(scipen=999)

#'#####################################################
#'
#'
#' h2o r test (rdemo, runit, rbooklet) setup procedure
#'
#'
#'#####################################################

h2oTestSetup <-
function() {
    h2oRDir <- normalizePath(paste(dirname(R.utils::commandArgs(asValues=TRUE)$"f"),"..",sep=.Platform$file.sep))
    h2oDocsDir <- normalizePath(paste(dirname(R.utils::commandArgs(asValues=TRUE)$"f"),"..","..","h2o-docs",
                                      sep=.Platform$file.sep))

    source(paste(h2oRDir,"scripts","h2o-r-test-setup-utils.R",sep=.Platform$file.sep))

    parseArgs(commandArgs(trailingOnly=TRUE)) # provided by --args

    if (test.is.rdemo()) {
        if (!"h2o" %in% rownames(installed.packages())) {
            stop("The H2O package has not been installed on this system. Cannot execute the H2O R demo without it!")
        }
        require(h2o)

        # source h2o-r/demos/rdemoUtils
        to_src <- c("utilsR.R")
        src_path <- paste(h2oRDir,"demos","rdemoUtils",sep=.Platform$file.sep)
        invisible(lapply(to_src,function(x){source(paste(src_path, x, sep = .Platform$file.sep))}))

        TEST.NAME <<- removeH2OInit(test.name())
    } else if (test.is.runit()) {
        # source h2o-r/h2o-package/R. overrides h2o package load
        to_src <- c("classes.R", "connection.R", "constants.R", "logging.R", "communication.R", "kvstore.R",
                    "frame.R", "astfun.R", "import.R", "parse.R", "export.R", "models.R", "edicts.R", "gbm.R",
                    "glm.R", "glrm.R", "kmeans.R", "deeplearning.R", "randomforest.R", "naivebayes.R", "pca.R",
                    "svd.R", "locate.R","grid.R")
        src_path <- paste(h2oRDir,"h2o-package","R",sep=.Platform$file.sep)
        invisible(lapply(to_src,function(x){source(paste(src_path, x, sep = .Platform$file.sep))}))

        # source h2o-r/tests/runitUtils
        to_src <- c("utilsR.R", "pcaR.R", "deeplearningR.R", "glmR.R", "glrmR.R", "gbmR.R", "kmeansR.R",
                    "naivebayesR.R", "gridR.R", "shared_javapredict.R")
        src_path <- paste(h2oRDir,"tests","runitUtils",sep=.Platform$file.sep)
        invisible(lapply(to_src,function(x){source(paste(src_path, x, sep = .Platform$file.sep))}))

        default.packages()
        Log.info("Loaded default packages. Additional required packages must be loaded explicitly.")

        sb <- sandbox(create=TRUE)
        Log.info(paste0("Created sandbox for runit test ",test.name()," in directory ",sb,".\n"))

        master_seed_dir <- getwd() 
        ms <- paste(master_seed_dir, "/master_seed", sep = "")
        seed <- NULL
        if (file.exists(ms)) seed <- read.table(ms)[[1]]
        setupRandomSeed(seed)
        h2o.logIt("[SEED] :", get.test.seed())
    } else if (test.is.rbooklet()) {
        if (!"h2o" %in% rownames(installed.packages())) {
            stop("The H2O package has not been installed on this system. Cannot execute the H2O R booklet without it!")
        }
        require(h2o)

        # source h2o-r/demos/rbookletUtils
        to_src <- c("utilsR.R")
        src_path <- paste(h2oDocsDir,"src","booklets","v2_2015","source","rbookletUtils",sep=.Platform$file.sep)
        invisible(lapply(to_src,function(x){source(paste(src_path, x, sep = .Platform$file.sep))}))
    } else {
        stop(paste0("Unrecognized test type. Must be of type rdemo, runit, or rbooklet, but got: ", test.name()))
    }

    test.ip <- get.test.ip()
    test.port <- get.test.port()
    cat(sprintf("[%s] %s\n", Sys.time(), paste0("Connect to h2o on IP: ",test.ip,", PORT: ",test.port)))
    h2o.init(ip = test.ip, port = test.port, startH2O = FALSE)

    h2o.startLogging(paste(results.dir(), "/rest.log", sep = ""))
    cat(sprintf("[%s] %s\n", Sys.time(),paste0("Started rest logging in: ",results.dir(),"/rest.log.")))

    h2o.logAndEcho("------------------------------------------------------------")
    h2o.logAndEcho("")
    h2o.logAndEcho(paste("STARTING TEST: ", test.name()))
    h2o.logAndEcho("")
    h2o.logAndEcho("------------------------------------------------------------")

    # execute test
    if (test.is.rdemo()) {
        h2o.removeAll()
        conn <- h2o.getConnection()
        conn@mutable$session_id <- h2o:::.init.session_id()
    }
    source(test.name())
}

h2oTestSetup()
options(echo=.origEchoValue)
