#----------------------------------------------------------------------
# Purpose:  This tests convergance of k-means on a large dataset.
#----------------------------------------------------------------------

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------
# Check if we are running inside the 0xdata network by seeing if we can touch
# the HDP2.1 namenode. Update if using other clusters.
# Note this should fail on home networks, since 186 is not likely to exist.
running_inside_hexdata = url.exists("http://mr-0xd6:50070", timeout=1)

if (running_inside_hexdata) {
    # cdh3 cluster
    hdfs_name_node = "mr-0xd6"
    hdfs_cross_file = "/datasets/BigCross.data"
} else {
    stop("Not running on 0xdata internal network. No access to HDFS.")
}

#----------------------------------------------------------------------

heading("BEGIN TEST")
conn <- h2o.init(ip=myIP, port=myPort)

#----------------------------------------------------------------------
# Single file cases.
#----------------------------------------------------------------------

heading("Import BigCross.data from HDFS")
url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_cross_file)
cross.hex <- h2o.importFile(conn, url)
n <- nrow(cross.hex)
print(paste("Imported n =", n, "rows"))
ncent <- 3
miters <- 10

heading(paste("Run k-means with k =", ncent, "and max_iterations =", miters))
cross1.km <- h2o.kmeans(training_frame = cross.hex, k = ncent, max_iterations = miters)
print(cross1.km)

heading("Run k-means with init = final cluster centers and max_iterations = 1")
cross2.km <- h2o.kmeans(training_frame = cross.hex, init = cross1.km@model$centers, max_iterations = 1)
print(cross2.km)

heading("Check k-means converged or maximum iterations reached")
avg_change <- sum((cross1.km@model$centers - cross2@model$centers)^2)/ncent
expect_true(avg_change < 1e-6 || cross1.km@model$iterations > miters)

PASS_BANNER()
