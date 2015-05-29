#Running Demos and Tests

H2O provides demos and tests in R, Python, Flow, Scala and Java for our algorithms. 

Demos contain example workflows showing how typical end users make use of H2O's capabilities.  They are specifically written to be human-readable starting points for your own exploration and use of H2O.  They cover most, but not all functionality.

Tests exercise every capability of H2O in detail using appropriate datasets and parameters and automatically verify that the expected results are produced.  

##Demos

###R

- [Kaggle](https://github.com/h2oai/h2o-3/tree/master/h2o-r/demos/kaggle): Contains Kaggle demos, including "Beating the Benchmark" and "Will It Rain?"

- [Supervised Demo](https://github.com/h2oai/h2o-3/blob/master/h2o-r/demos/large/supervised.R): Runs four algorithms on categorical or continuous response datasets and reports performance. 

###Python

- [Python Demos](https://github.com/h2oai/h2o-3/tree/master/h2o-py/demos): Contains a library of Python demos and instructions on how to run the demos. 

###Flow

- [Flow Demos](https://github.com/h2oai/h2o-3/tree/master/h2o-docs/src/product/flow/packs/examples): Contains a library of demos that can be run in H2O's web UI, Flow. These demos can also be accessed within Flow by clicking the "Help" sidebar, then clicking "Browse installed packs...", then clicking the "Examples" folder and selecting the demo flow. 

###Scala

- [Scala Demos](https://github.com/h2oai/sparkling-water/tree/master/examples/scripts): Contains Scala demos used at meetups to demonstrate Sparkling Water. 


###Java

>Need location


##Tests

###R

- [Instructions](https://github.com/h2oai/h2o-3/tree/master/h2o-r): Instructions on running R tests. 

- [Deep Learning](https://github.com/h2oai/h2o-3/tree/master/h2o-r/tests/testdir_algos/deeplearning): Library of Deep Learning R tests. 

- [GBM](https://github.com/h2oai/h2o-3/tree/master/h2o-r/tests/testdir_algos/gbm): Library of GBM R tests. 

- [GLM](https://github.com/h2oai/h2o-3/tree/master/h2o-r/tests/testdir_algos/glm): Library of GLM R tests. 

- [K-means](https://github.com/h2oai/h2o-3/tree/master/h2o-r/tests/testdir_algos/kmeans): Library of K-means R tests. 

- [Naïve Bayes](https://github.com/h2oai/h2o-3/tree/master/h2o-r/tests/testdir_algos/naivebayes): Library of Naïve Bayes R tests. 

- [DRF](https://github.com/h2oai/h2o-3/tree/master/h2o-r/tests/testdir_algos/randomforest): Library of DRF R tests. 

- [Demos](https://github.com/h2oai/h2o-3/tree/master/h2o-r/tests/testdir_demos): Library of R tests for our demos, including Citibike, Chicago Crime, and Airlines. 

- [Documentation](https://github.com/h2oai/h2o-3/tree/master/h2o-r/tests/testdir_docexamples): Library of R tests for the examples in the R documentation. 

- [Golden tests](https://github.com/h2oai/h2o-3/tree/master/h2o-r/tests/testdir_golden): Library of "golden" R tests that test each algorithm extensively. 

- [HDFS](https://github.com/h2oai/h2o-3/tree/master/h2o-r/tests/testdir_hdfs): Library of R tests that pull data from HDFS instead of locally. 

- [Java Predict](https://github.com/h2oai/h2o-3/tree/master/h2o-r/tests/testdir_javapredict): Library of R tests that demonstrate how to use POJO output. 

- [Jira](https://github.com/h2oai/h2o-3/tree/master/h2o-r/tests/testdir_jira): Library of R tests for filed Jira tickets; view the corresponding ticket on our [Jira page](http://0xdata.atlassian.net)

- [Misc](https://github.com/h2oai/h2o-3/tree/master/h2o-r/tests/testdir_misc) and [Munging](https://github.com/h2oai/h2o-3/tree/master/h2o-r/tests/testdir_munging): These libraries contain R tests for data munging functions, including splicing, filtering, histograms, and column transformation. 



###Python

- [Instructions](https://github.com/h2oai/h2o-3/tree/master/h2o-py): Instructions for running Python tests. 

- [Deep Learning](https://github.com/h2oai/h2o-3/tree/master/h2o-py/tests/testdir_algos/deeplearning): Library of Deep Learning Python tests. 

- [GBM](https://github.com/h2oai/h2o-3/tree/master/h2o-py/tests/testdir_algos/gbm): Library of GBM Python tests. 

- [GLM](https://github.com/h2oai/h2o-3/tree/master/h2o-py/tests/testdir_algos/glm): Library of GLM Python tests. 

- [K-means](https://github.com/h2oai/h2o-3/tree/master/h2o-py/tests/testdir_algos/kmeans): Library of K-means Python tests. 

- [DRF](https://github.com/h2oai/h2o-3/tree/master/h2o-py/tests/testdir_algos/rf): Library of DRF Python tests. 

###Java

- [Instructions](https://github.com/h2oai/h2o-3/blob/master/h2o-core/testMultiNode.sh): Instructions on running Java tests. 

- [Deep Learning](https://github.com/h2oai/h2o-3/tree/master/h2o-algos/src/test/java/hex/deeplearning): Java Library containing multiple Deep Learning tests. 

- [GLM](https://github.com/h2oai/h2o-3/blob/master/h2o-algos/src/test/java/hex/glm/GLMBasicTest.java): Runs GLM on Prostate dataset and scores results. 

- [K-means](https://github.com/h2oai/h2o-3/blob/master/h2o-algos/src/test/java/hex/kmeans/KMeansTest.java): Runs K-means on Iris dataset with a seed, checks all clusters are non-zero, and scores results. 

- [Naïve Bayes](https://github.com/h2oai/h2o-3/blob/master/h2o-algos/src/test/java/hex/naivebayes/NaiveBayesTest.java): Runs Naïve Bayes on Iris dataset, Prostate dataset, and Covtype dataset and scores results. 

- [Split frame](https://github.com/h2oai/h2o-3/blob/master/h2o-algos/src/test/java/hex/splitframe/ShuffleSplitFrameTest.java): Tests shuffle split frame, splits the frame in half and compares the values. 

- [DRF](https://github.com/h2oai/h2o-3/blob/master/h2o-algos/src/test/java/hex/tree/drf/DRFTest.java): Runs DRF on the Iris dataset, builds a POJO, and validates the results. 

- [GBM](https://github.com/h2oai/h2o-3/blob/master/h2o-algos/src/test/java/hex/tree/gbm/GBMTest.java): Builds a GBM model using the Airlines dataset using Bernoulli classification. 

