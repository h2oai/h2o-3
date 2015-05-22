#Running Tests

H2O provides tests in Java, R, and Python for our algorithms. Each unit test is designed to test different capabilities in H2O using different datasets and parameters and to verify that the expected results are produced. 

##Java


- [Instructions](https://github.com/h2oai/h2o-dev/blob/master/h2o-core/testMultiNode.sh): Instructions on running Java tests. 

- [Deep Learning](https://github.com/h2oai/h2o-dev/tree/master/h2o-algos/src/test/java/hex/deeplearning): Java Library containing multiple Deep Learning tests. 

- [GLM](https://github.com/h2oai/h2o-dev/blob/master/h2o-algos/src/test/java/hex/glm/GLMBasicTest.java): Runs GLM on Prostate dataset and scores results. 

- [K-means](https://github.com/h2oai/h2o-dev/blob/master/h2o-algos/src/test/java/hex/kmeans/KMeansTest.java): Runs K-means on Iris dataset with a seed, checks all clusters are non-zero, and scores results. 

- [Naïve Bayes](https://github.com/h2oai/h2o-dev/blob/master/h2o-algos/src/test/java/hex/naivebayes/NaiveBayesTest.java): Runs Naïve Bayes on Iris dataset, Prostate dataset, and Covtype dataset and scores results. 

- [Split frame](https://github.com/h2oai/h2o-dev/blob/master/h2o-algos/src/test/java/hex/splitframe/ShuffleSplitFrameTest.java): Tests shuffle split frame, splits the frame in half and compares the values. 

- [DRF](https://github.com/h2oai/h2o-dev/blob/master/h2o-algos/src/test/java/hex/tree/drf/DRFTest.java): Runs DRF on the Iris dataset, builds a POJO, and validates the results. 

- [GBM](https://github.com/h2oai/h2o-dev/blob/master/h2o-algos/src/test/java/hex/tree/gbm/GBMTest.java): Builds a GBM model using the Airlines dataset using Bernoulli classification. 

##R


- [Instructions](https://github.com/h2oai/h2o-dev/tree/master/h2o-r): Instructions on running R tests. 

- [Deep Learning](https://github.com/h2oai/h2o-dev/tree/master/h2o-r/tests/testdir_algos/deeplearning): Library of Deep Learning R tests. 

- [GBM](https://github.com/h2oai/h2o-dev/tree/master/h2o-r/tests/testdir_algos/gbm): Library of GBM R tests. 

- [GLM](https://github.com/h2oai/h2o-dev/tree/master/h2o-r/tests/testdir_algos/glm): Library of GLM R tests. 

- [K-means](https://github.com/h2oai/h2o-dev/tree/master/h2o-r/tests/testdir_algos/kmeans): Library of K-means R tests. 

- [Naïve Bayes](https://github.com/h2oai/h2o-dev/tree/master/h2o-r/tests/testdir_algos/naivebayes): Library of Naïve Bayes R tests. 

- [DRF](https://github.com/h2oai/h2o-dev/tree/master/h2o-r/tests/testdir_algos/randomforest): Library of DRF R tests. 

##Python


- [Instructions](https://github.com/h2oai/h2o-dev/tree/master/h2o-py): Instructions for running Python tests. 

- [Deep Learning](https://github.com/h2oai/h2o-dev/tree/master/h2o-py/tests/testdir_algos/deeplearning): Library of Deep Learning Python tests. 

- [GBM](https://github.com/h2oai/h2o-dev/tree/master/h2o-py/tests/testdir_algos/gbm): Library of GBM Python tests. 

- [GLM](https://github.com/h2oai/h2o-dev/tree/master/h2o-py/tests/testdir_algos/glm): Library of GLM Python tests. 

- [K-means](https://github.com/h2oai/h2o-dev/tree/master/h2o-py/tests/testdir_algos/kmeans): Library of K-means Python tests. 

- [DRF](https://github.com/h2oai/h2o-dev/tree/master/h2o-py/tests/testdir_algos/rf): Library of DRF Python tests. 