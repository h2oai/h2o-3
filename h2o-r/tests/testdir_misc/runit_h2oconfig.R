setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.config <- function() {
    if(!dir.exists("../results")){
        dir.create("../results")
        dir = "../results"
    }else{
        dir = "../results"
    }

    #Creat tmp config
    fileConn<-file(paste0(dir,"/.h2oconfig"))
    writeLines(c("# key = value\n\n"),fileConn)

    #Parse config and check if correct
    h2oconfig_filename <- paste0(dir,"/.h2oconfig")
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,NULL)

    #Creat tmp config
    fileConn<-file(paste0(dir,"/.h2oconfig"))
    writeLines(c("# key = value\n[init]\n"),fileConn)

    #Parse config and check if correct
    h2oconfig_filename <- paste0(dir,"/.h2oconfig")
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,NULL)

    #Creat tmp config
    fileConn<-file(paste0(dir,"/.h2oconfig"))
    writeLines(c("[init]
    check_version = False
    proxy = http://127.12.34.99.10000
    cluster_id = 3"),fileConn)

    #Parse config and check if correct
    h2oconfig_filename <- paste0(dir,"/.h2oconfig")
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,data.frame(init.check_version = as.factor("False"), init.proxy = as.factor("http://127.12.34.99.10000"), init.cluster_id = as.factor(3)))

    #Creat tmp config
    fileConn<-file(paste0(dir,"/.h2oconfig"))
    writeLines(c("init.check_version = anything!  # rly?
    init.cookies=A
    py:init.cluster_id=7
    # more comment"),fileConn)

    #Parse config and check if correct
    h2oconfig_filename <- paste0(dir,"/.h2oconfig")
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,data.frame(init.check_version = as.factor("anything!"), init.cookies = as.factor("A")))

    #Creat tmp config
    fileConn<-file(paste0(dir,"/.h2oconfig"))
    writeLines(c("r:init.cluster_id=asf"),fileConn)

    #Parse config and check if correct
    h2oconfig_filename <- paste0(dir,"/.h2oconfig")
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,data.frame(init.cluster_id = as.factor("asf")))

    #Creat tmp config
    fileConn<-file(paste0(dir,"/.h2oconfig"))
    writeLines(c("init.checkversion = True
    init.clusterid = 7
    proxy = None"),fileConn)

    #Parse config and check if correct
    h2oconfig_filename <- paste0(dir,"/.h2oconfig")
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,NULL)

    #Creat tmp config
    fileConn<-file(paste0(dir,"/.h2oconfig"))
    writeLines(c("[something]
    init.check_version = True"),fileConn)

    #Parse config and check if correct
    h2oconfig_filename <- paste0(dir,"/.h2oconfig")
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,NULL)

    #Creat tmp config
    fileConn<-file(paste0(dir,"/.h2oconfig"))
    writeLines(c("init.check_version = True
    init.check_version = False
    init.check_version = Ambivolent"),fileConn)

    #Parse config and check if correct
    h2oconfig_filename <- paste0(dir,"/.h2oconfig")
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,data.frame(init.check_version = as.factor("Ambivolent")))

    #Delete tmp directory
    unlink(dir,recursive=TRUE)
}

doTest("Test h2o config parsing", test.config)