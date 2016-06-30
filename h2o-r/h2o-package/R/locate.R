#
# THIS IS FOR DEBUG PURPOSES WITHIN H2O.ai TESTING INFRASTRUCTURE
# DO NOT EXPORT ANY OF THESE FUNCTIONS FOR END USER CONSUMPTION
#

.H2O.LOCATE.PROJECT.ROOT <- "h2o-dev"
.H2O.LOCATE.SUBPROJECT.ROOT <- "h2o-r"

#'
#' Locate a file given the pattern <bucket>/<path/to/file>
#' e.g. h2o:::.h2o.locate("smalldata/iris/iris22.csv") returns the absolute path to iris22.csv
#'
.h2o.locate<-
  function(pathStub, root.parent = NULL) {
    pathStub <- .h2o.locate.clean(pathStub)
    bucket <- pathStub[1]
    offset <- pathStub[-1]
    cur.dir <- getwd()
    #recursively ascend until `bucket` is found
    bucket.abspath <- .h2o.locate.path.compute(cur.dir, bucket, root.parent)
    if (length(offset) != 0) return(paste(c(bucket.abspath, offset), collapse = "/", sep = "/"))
    else return(bucket.abspath)
  }

#
# Clean a path up: change \ -> /; remove starting './'; split
.h2o.locate.clean<-
  function(p) {
    p <- gsub("[\\]", "/", p)
    p <- unlist(strsplit(p, '/'))
    p
  }

#
# Compute a path distance.
#
# We are looking for a directory `root`. Recursively ascend the directory structure until the root is found.
# If not found, produce an error.
#
# @param cur.dir: the current directory
# @param root: the directory that is being searched for
# @param root.parent: if not null, then the `root` must have `root.parent` as immediate parent
# @return: Return the absolute path to the root.
.h2o.locate.path.compute<-
  function(cur.dir, root, root.parent = NULL) {
    
    parent.dir  <- dirname(cur.dir)
    parent.name <- basename(parent.dir)
    
    # root.parent is null
    if (is.null(root.parent)) {
      
      # first check if cur.dir is root
      if (basename(cur.dir) == root) return(normalizePath(cur.dir))
      
      # next check if root is in cur.dir somewhere
      if (root %in% dir(cur.dir)) return(normalizePath(paste(cur.dir, "/", root, sep = "")))
      
      # the root is the parent
      if (parent.name == root) return(normalizePath(paste(parent.dir, "/", root, sep = "")))
      
      # the root is h2o-dev, check the children here (and fail if `root` not found)
      if (parent.name == .H2O.LOCATE.PROJECT.ROOT) {
        if (root %in% dir(parent.dir)) return(normalizePath(paste(parent.dir, "/", root, sep = "")))
        else stop(paste("Could not find the dataset bucket: ", root, sep = "" ))
      }
      
      # root.parent is not null
    } else {
      
      # first check if cur.dir is root
      if (basename(cur.dir) == root && parent.name == root.parent) return(normalizePath(cur.dir))
      
      # next check if root is in cur.dir somewhere (if so, then cur.dir is the parent!)
      if (root %in% dir(cur.dir) && root.parent == basename(cur.dir)) return(normalizePath(paste(cur.dir, "/", root, sep = "")))
      
      # the root is the parent
      if (parent.name == root && basename(dirname(parent.dir)) == root.parent) return(.h2o.locate.path.compute(parent.dir, root, root.parent)) #return(normalizePath(paste(parent.dir, "/", root, sep = "")))
      
      # fail if reach h2o-dev
      if (parent.name == .H2O.LOCATE.PROJECT.ROOT) stop("Reached the root h2o-dev. Didn't find the bucket with the root.parent")
    }
    if (cur.dir == parent.dir) {
      # Reached root: / or C:
      # Stop now otherwise infinite recursive call
      stop("Could not find the dataset bucket '", root,"'. Please setwd() to a H2O directory and/or syncSmalldata or syncBigdataLaptop.")
    }
    return(.h2o.locate.path.compute(parent.dir, root, root.parent))
  }
  
  
