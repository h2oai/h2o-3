setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.config <- function() {

    #Set up testing framework
    dir <- sandbox()
    h2oconfig_filename <- paste0(dir,"/.h2oconfig")
    fileConn <- file(h2oconfig_filename)

    #Creat tmp config
    writeLines(c("# key = value\n\n"),fileConn)
    #Parse config and check if correct
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,NULL)

    #Creat tmp config
    writeLines(c("# key = value\n[init]\n"),fileConn)
    #Parse config and check if correct
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,NULL)

    #Creat tmp config
    writeLines(c("[init]
    check_version = False
    proxy = http://127.12.34.99.10000
    cluster_id = 3"),fileConn)
    #Parse config and check if correct
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,data.frame(init.check_version = as.factor("False"), init.proxy = as.factor("http://127.12.34.99.10000"), init.cluster_id = as.factor(3)))

    #Creat tmp config
    writeLines(c("init.check_version = anything!  # rly?
    init.cookies=A
    py:init.cluster_id=7
    # more comment"),fileConn)
    #Parse config and check if correct
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,data.frame(init.check_version = as.factor("anything!"), init.cookies = as.factor("A")))

    #Creat tmp config
    writeLines(c("r:init.cluster_id=asf"),fileConn)
    #Parse config and check if correct
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,data.frame(init.cluster_id = as.factor("asf")))

    #Creat tmp config
    writeLines(c("init.checkversion = True
    init.clusterid = 7
    proxy = None"),fileConn)
    #Parse config and check if correct
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,NULL)

    #Creat tmp config
    writeLines(c("[something]
    init.check_version = True"),fileConn)
    #Parse config and check if correct
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,NULL)

    #Creat tmp config
    writeLines(c("init.check_version = True
    init.check_version = False
    init.check_version = Ambivolent"),fileConn)
    #Parse config and check if correct
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,data.frame(init.check_version = as.factor("Ambivolent")))

    #Creat tmp config
    writeLines(c("[something]
    check_version = True
    [init]
    cluster_id = 3"),fileConn)
    #Parse config and check if correct
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,data.frame(init.cluster_id = as.factor("3")))

    #Creat tmp config
    writeLines(c("[general]
    allow_breaking_changes = True
    [init]
    cluster_id = 3"),fileConn)
    #Parse config and check if correct
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,data.frame(general.allow_breaking_changes = as.factor("True"),init.cluster_id = as.factor("3")))

    #Creat tmp config
    writeLines(c("[GEnEraL]
    allow_breaking_changes = True
    [INiT]
    cluster_id = 3"),fileConn)
    #Parse config and check if correct
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,data.frame(general.allow_breaking_changes = as.factor("True"),init.cluster_id = as.factor("3")))

    #Delete tmp directory
    on.exit(unlink(dir,recursive=TRUE))
}

doTest("Test h2o config parsing", test.config)