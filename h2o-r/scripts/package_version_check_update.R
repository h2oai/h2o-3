options(echo=FALSE)
#'
#' Check that the required packages are installed and it's the correct version
#'

H2O.S3.R.PACKAGE.REPO.OSX <- "https://s3.amazonaws.com/h2o-r/osx"
H2O.S3.R.PACKAGE.REPO.LIN <- "https://s3.amazonaws.com/h2o-r/linux"
H2O.S3.R.PACKAGE.REPO.WIN <- "https://s3.amazonaws.com/h2o-r/windows"
JENKINS.R.PKG.VER.REQS.OSX <- paste0(H2O.S3.R.PACKAGE.REPO.OSX,"/package_version_requirements.osx")
JENKINS.R.PKG.VER.REQS.LIN <- paste0(H2O.S3.R.PACKAGE.REPO.LIN,"/package_version_requirements.linux")
JENKINS.R.PKG.VER.REQS.WIN <- paste0(H2O.S3.R.PACKAGE.REPO.WIN,"/package_version_requirements.windows")
JENKINS.R.VERSION.MAJOR <- "3"
JENKINS.R.VERSION.MINOR <- "2.2"

#'
#' Given a dataframe of required packages, reorder the rows to satisfy package interdependencies
#'
orderByDependencies<-
function(reqs) {
    # create character vector of package names in desired order
    pkgNames <- as.character(reqs[,1])
    pkgNames <- pkgNames[-which(pkgNames %in% "fpc")]
    pkgNames <- append(pkgNames,"fpc",after=which(pkgNames %in% "trimcluster"))
    pkgNames <- pkgNames[-which(pkgNames %in% "bit64")]
    pkgNames <- append(pkgNames,"bit64",after=which(pkgNames %in% "bit"))
    pkgNames <- pkgNames[-which(pkgNames %in% "ggplot2")]
    pkgNames <- append(pkgNames,"ggplot2",after=which(pkgNames %in% "scales"))
    return(reqs[match(pkgNames,reqs[,1]),])
}

#'
#' Given a vector of installed packages, and a data frame of requirements (package,version,repo_name), return
#' a vector of packages that need to be retrieved
#'
doCheck<-
function(installed_packages, reqs) {
    mp <- wv <- gp <- c()
    for (i in 1:nrow(reqs)) {
        req_pkg <- as.character(reqs[i,1])
        req_ver <- as.character(reqs[i,2])
        no_pkg <- !req_pkg %in% installed_packages
        wrong_version <- FALSE
        if (!no_pkg) wrong_version <- !req_ver == packageVersion(req_pkg)
        if (no_pkg || wrong_version) {
            gp <- c(gp, c(as.character(reqs[i,3])))
            if (no_pkg) mp <- c(mp, c(req_pkg))
            else wv <- c(wv, c(paste0("package=", req_pkg,", installed version=", packageVersion(req_pkg), ", required version=", req_ver))) } }

    # missing packages
    num_missing_packages <- length(mp)
    if (num_missing_packages > 0) {
        write("",stdout())
        write("INFO: Missing the following Jenkins-approved R packages: ",stdout())
        write("",stdout())
        write(mp,stdout())
        write("",stdout())
        write("INFO: Please run `./gradlew syncRPackages` to update ",stdout()) }

    # wrong versions
    num_wrong_versions <- length(wv)
    if (num_wrong_versions > 0) {
        write("",stdout())
        write("INFO: This system has R packages that are not Jenkins-approved versions: ",stdout())
        write("",stdout())
        write(wv,stdout())
        write("",stdout())
        write("INFO: Please run `./gradlew syncRPackages` to update",stdout()) }

    gp
}

#'
#' Main
#'
#' @param args args[1] is requirements filename, args[2] is check or update, args[3] is optional and indicates -PnoAskRPkgSync=true
#'
packageVersionCheckUpdate <-
function(args) {
    doCheckOnly <- args[1] == "check"
    if (doCheckOnly) {
        write("",stdout())
        write(paste0("INFO: R package/version check only. Please run `./gradlew syncRPackages` if you want to update instead"),stdout())
    } else {
        write("",stdout())
        write(paste0("INFO: R package/version s3 sync procedure"),stdout()) }

    OSX <- Sys.info()["sysname"] == "Darwin"
    LIN <- Sys.info()["sysname"] == "Linux"

    # check R version
    return_val <- 0
    sysRMajor <- R.version$major
    sysRMinor <- R.version$minor
    wrong_r <- !(sysRMajor == JENKINS.R.VERSION.MAJOR && sysRMinor == JENKINS.R.VERSION.MINOR)
    if (wrong_r) {
        return_val <- 2
        write("",stdout())
        write(paste0("WARNING: Jenkins has R version ",JENKINS.R.VERSION.MAJOR,".",JENKINS.R.VERSION.MINOR,
                     ", but this system's R version is ",sysRMajor,".",sysRMinor),stdout())
        write(paste0("INFO: Manually update your R version to match Jenkins'"),stdout()) }

    installed_packages <- rownames(installed.packages())

    # download and install RCurl
    url <- tryCatch({
        no_rcurl <- !"RCurl" %in% installed_packages
        if (no_rcurl) {
            write("INFO: Installing RCurl...",stdout())
            install.packages("RCurl",repos="http://cran.us.r-project.org") }
    }, error = function(e) {
        write(paste0("ERROR: Unable to install RCurl, which is a requirement to continue proceed: ",e),stdout())
        q("no",1,FALSE)
    })

    # read the package_version_requirements file
    require(RCurl,quietly=TRUE)
    url <- tryCatch({
        if (OSX) { # osx
            getURL(JENKINS.R.PKG.VER.REQS.OSX,.opts=list(ssl.verifypeer = FALSE))
        } else if (LIN) { # linux
            getURL(JENKINS.R.PKG.VER.REQS.LIN,.opts=list(ssl.verifypeer = FALSE))
        } else {
            getURL(JENKINS.R.PKG.VER.REQS.WIN,.opts=list(ssl.verifypeer = FALSE)) }
    }, error = function(e) {
        write(paste0("ERROR: Could not connect to S3 to retrieve R package requirements: ",e),stdout())
        q("no",1,FALSE)
    })
    reqs <- read.csv(textConnection(url), header=FALSE)
    # reorder the rows to satisfy package interdependencies
    reqs <- orderByDependencies(reqs)
    write("",stdout())
    write("INFO: Jenkins' (package,version) list:",stdout())
    write("",stdout())
    invisible(lapply(1:nrow(reqs),function(x) write(paste0("(",as.character(reqs[x,1]),", ",as.character(reqs[x,2]),")"),stdout())))
    num_packages <- nrow(reqs)

    if (doCheckOnly) { # do package and version checks.
        get_packages <- doCheck(installed_packages,reqs)
        num_get_packages <- length(get_packages)
        if (num_get_packages > 0) return_val <- return_val + 1
        write("",stdout())
        if (return_val == 0 || return_val == 2) {
            write("INFO: Check successful. All system R packages/versions are Jenkins-approved",stdout())
        } else {
            write("ERROR: Check unsuccessful",stdout()) }
        q("no",return_val,FALSE)
    } else { # install/upgrade/downgrade packages/versions
        write("",stdout())
        write("INFO: Starting updates...",stdout())

        for (i in 1:num_packages) {
            name <- as.character(reqs[i,1])
            ver  <- as.character(reqs[i,2])
            pkg  <- as.character(reqs[i,3])

            no_pkg <- !name %in% installed_packages
            wrong_version <- FALSE
            if (!no_pkg) wrong_version <- !ver == packageVersion(name)

            if (no_pkg || wrong_version) {
                write("",stdout())
                write(paste0("Installing package ",pkg,"..."),stdout())
                if (OSX) { # osx
                    install.packages(paste0(H2O.S3.R.PACKAGE.REPO.OSX,"/",pkg),repos=NULL,type="mac.binary.mavericks")
                } else if (LIN) { # linux
                    install.packages(paste0(H2O.S3.R.PACKAGE.REPO.LIN,"/",pkg),repos=NULL,method="curl")
                } else {
                    install.packages(paste0(H2O.S3.R.PACKAGE.REPO.WIN,"/",pkg),repos=NULL,type="win.binary",method="curl") }}}

        # follow-on check
        write("",stdout())
        write("INFO: R package sync complete. Conducting follow-on R package/version checks...",stdout())
        installed_packages <- rownames(installed.packages())
        get_packages <- doCheck(installed_packages,reqs)

        if (length(get_packages) > 0) {
            write("",stdout())
            write("INFO: If the above list of missing/incorrect R packages was unexpected, try manually installing",stdout())
        } else {
            write("",stdout())
            write("INFO: R package sync successful",stdout())
            write("",stdout()) }}
}

packageVersionCheckUpdate(args=commandArgs(trailingOnly = TRUE))

