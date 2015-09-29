options(echo=FALSE)
#'
#' Check that the required packages are installed and it's the correct version
#'

H2O.S3.R.PACKAGE.REPO <- "https://s3.amazonaws.com/h2o-r"
JENKINS.R.PKG.VER.REQS <- "https://s3.amazonaws.com/h2o-r/package_version_requirements"
JENKINS.R.VERSION.MAJOR <- "3"
JENKINS.R.VERSION.MINOR <- "2.2"

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
        if (!no_pkg) wrong_ver <- !req_ver == packageVersion(req_pkg)
        if (no_pkg || wrong_ver) {
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
    doUpdate    <- !doCheckOnly
    interactiveUpdate <- length(args) == 1
    if (doCheckOnly) {
        write("",stdout())
        write(paste0("INFO: R package/version check only. Please run `./gradlew syncRPackages` if you want to update instead"),stdout())
    } else {
        write("",stdout())
        write(paste0("INFO: R package/version s3 sync procedure"),stdout())
        if (interactiveUpdate) {
            write("",stdout())
            write(paste0("INFO: Interactive mode enabled by default. You will be prompted prior to installing any R package. Use `-PnoAskRPkgSync=true` option to disable"),stdout()) }}

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

    # read the package_version_requirements file
    require(RCurl,quietly=TRUE)
    reqs <- read.csv(textConnection(getURL(JENKINS.R.PKG.VER.REQS)), header=FALSE)
    write("",stdout())
    write("INFO: Jenkins' (package,version) list:",stdout())
    write("",stdout())
    invisible(lapply(1:nrow(reqs),function(x) write(paste0("(",as.character(reqs[x,1]),", ",as.character(reqs[x,2]),")"),stdout())))

    if (doCheckOnly) { # do package and version checks.
        get_packages <- doCheck(rownames(installed.packages()),reqs)
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

        if (interactiveUpdate) {
            f <- file("stdin")
            open(f)
            pn <- 1
            name <- as.character(reqs[pn,3])
            num_packages <- nrow(reqs)
            write("",stdout())
            write(paste0("Press 'y' to install ",name,". Press any other key to skip."),stdout())
            while((length(line <- readLines(f,n=1)) > 0) && pn <= num_packages) {
              if (line == "y") {
                write("",stdout())
                install.packages(paste0(H2O.S3.R.PACKAGE.REPO,"/",name,repos=NULL,type="binary")) }
              pn <- pn + 1
              name <- as.character(reqs[pn,3])
              if (pn <= num_packages) {
                write("",stdout())
                write(paste0("Press 'y' to install ",name,". Press any other key to skip."),stdout())
              } else { break } }
        } else {
            for (p in reqs[3]) {
                name <- as.character(p)
                write("",stdout())
                write(paste0("Installing package ",name,"..."),stdout())
                install.packages(paste0(H2O.S3.R.PACKAGE.REPO,"/",name),repos=NULL,type="binary") } }

        # follow-on check
        write("",stdout())
        write("INFO: R package sync complete. Conducting follow-on R package/version checks...",stdout())
        get_packages <- doCheck(rownames(installed.packages()),reqs)

        if (length(get_packages) > 0) {
            write("",stdout())
            write("INFO: If the above list of missing/incorrect R packages was unexpected, try manually installing",stdout())
        } else {
            write("",stdout())
            write("INFO: R package sync successful",stdout())
            write("",stdout()) }}
}

packageVersionCheckUpdate(args=commandArgs(trailingOnly = TRUE))

