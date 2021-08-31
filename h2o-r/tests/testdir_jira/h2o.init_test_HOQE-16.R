#Currently, our R/python test suite is executed against an established h2o cluster (run.py sets up the cluster). However, we ignore the mode of 
#operation where the h2o cluster is created by the client. Consequently, we may not recognize bugs in h2o.init() for this mode of operation. 
#For this ticket, I think we should create a set of tests that check that h2o.init() is successful for each OS/client interface combination.

#Below is the test that will be implemented:

options(h2o.dev.javacheck.disable = TRUE)

library(h2o)

check_cluster <- function() {
  #Way to check if cluster is up and also get status:
  cluster_up = h2o.clusterIsUp()
  cluster_status = h2o.clusterStatus()
  #Logical test to see if status is healthy or not
  if(cluster_status$healthy == TRUE & cluster_up == TRUE){
    cat("Cluster is up and healthy")
  }else if(cluster_status$healthy != TRUE & cluster_up == TRUE){
    stop("Cluster is up but not healthy")
  }else{
    stop("Cluster is not up and is not healthy")
  }
}

check_cluster_and_shutdown <- function() {
  check_cluster()
  conn <- h2o.getConnection()
  h2o.shutdown(prompt = FALSE)

  while(h2o.clusterIsUp(conn)) {
    print("Cluster is still up")
    Sys.sleep(1)
  }
}

#Sometimes we want to add extra system properties to JVM invocation (eg. allow running on unsupported Java version)
jvm_custom_args = Sys.getenv("ADDITIONAL_TEST_JVM_OPTS")
print(sprintf("ADDITIONAL_TEST_JVM_OPTS: %s", jvm_custom_args))

#Call h2o.init() just in case instance is not running
h2o.init(jvm_custom_args = jvm_custom_args)

#First, we will shutdown any instance of h2o
h2o.shutdown(prompt = FALSE)

#Load up h2o and h2o.init()
h2o.init(jvm_custom_args = jvm_custom_args)

check_cluster_and_shutdown()


#We just need anything (in this case we use the h2o.jar) to add to the classpath to check that init works with extra_classpath as well
extra_classpath <- h2o:::.h2o.downloadJar()

h2o.init(jvm_custom_args = jvm_custom_args, extra_classpath = extra_classpath)

check_cluster()
h2o.shutdown(prompt = FALSE)
