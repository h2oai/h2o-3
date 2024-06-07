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
    proxy = http://127.12.34.99.10000"),fileConn)
    #Parse config and check if correct
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,data.frame(init.check_version = as.factor("False"), init.proxy = as.factor("http://127.12.34.99.10000")))

    #Creat tmp config
    writeLines(c("init.check_version = anything!  # rly?
    init.cookies=A
    # more comment"),fileConn)
    #Parse config and check if correct
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,data.frame(init.check_version = as.factor("anything!"), init.cookies = as.factor("A")))

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
     check_version = FALSE"),fileConn)
    #Parse config and check if correct
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,data.frame(init.check_version = as.factor("FALSE")))

    #Creat tmp config
    writeLines(c("[general]
    allow_breaking_changes = True"),fileConn)
    #Parse config and check if correct
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,data.frame(general.allow_breaking_changes = as.factor("True")))

    #Creat tmp config
    writeLines(c("[GEnEraL]
    allow_breaking_changes = True"),fileConn)
    #Parse config and check if correct
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,data.frame(general.allow_breaking_changes = as.factor("True")))

    #Create tmp config
    writeLines(c("[init]
    username = name
    password = password"),fileConn)
    #Parse config and check if correct
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,data.frame(init.username = "name" ,init.password = "password", stringsAsFactors = TRUE))

    #Create tmp config
    writeLines(c("[general]
    allow_breaking_changes = True
    [init]
    username = name
    [init]
    password = password"),fileConn)
    #Parse config and check if correct
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,data.frame(general.allow_breaking_changes = as.factor("True"),init.username = "name" ,init.password = "password", stringsAsFactors = TRUE))

    #Create tmp config
    writeLines(c("
    general.allow_breaking_changes = True
    init.username = name
    init.password = password"),fileConn)
    #Parse config and check if correct
    config = .parse.h2oconfig(h2oconfig_filename)
    expect_equal(config,data.frame(general.allow_breaking_changes = as.factor("True"),init.username = "name" ,init.password = "password", stringsAsFactors = TRUE))

    #Delete tmp directory
    on.exit(unlink(dir,recursive=TRUE))
}

doTest("Test h2o config parsing", test.config)
