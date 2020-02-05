``cluster_size_constraints``
----------------------------

- Available in: K-Means
- Hyperparameter: no

Description
~~~~~~~~~~~

This option specifies the minimum number of points that should be in each cluster. The length of the constraints array has to be the same as the number of clusters.

To satisfy the custom minimal cluster size, the calculation of clusters is converted to the Minimal Cost Flow problem. Instead of using the Lloyd iteration algorithm, a graph is constructed based on the distances and constraints. The goal is to go iteratively through the input edges and create an optimal spanning tree that satisfies the constraints.

**Minimum-cost flow problems can be efficiently solved in polynomial time (or in the worst case, in exponential time). The performance of this implementation of the Constrained K-means algorithm is slow due to many repeatable calculations that cannot be parallelized and more optimized at the H2O backend. For large datasets, the calculation can last hours. For example, a dataset with 100,000 rows and five features can run for several hours.**

Expected time with various sized data (OS debian 10.0 (x86-64), processor Intel© Core™ i7-7700HQ CPU @ 2.80GHz × 4, RAM 23.1 GiB):

* 10 000 rows, 5 features  ~ 0h  9m 21s
* 20 000 rows, 5 features  ~ 0h 39m 27s
* 30 000 rows, 5 features  ~ 1h 26m 43s
* 40 000 rows, 5 features  ~ 2h 13m 31s
* 50 000 rows, 5 features  ~ 4h  4m 18s

Related Parameters
~~~~~~~~~~~~~~~~~~

- None


Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()

	# import the iris dataset:
	# this dataset is used to classify the type of iris plant
	# the original dataset can be found at https://archive.ics.uci.edu/ml/datasets/Iris
	iris <-h2o.importFile("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")

	# convert response column to a factor
	iris['class'] <-as.factor(iris['class'])

	# set the predictor names 
	predictors <-colnames(iris)[-length(iris)]

	# try using the `cluster_size_constraints` parameter:
	iris_kmeans <- h2o.kmeans(x = predictors, 
	                          k=3, 
	                          standardize=T, 
	                          cluster_size_constraints=list(2, 5, 8),
	                          training_frame=iris, 
	                          score_each_iteration=T, 
	                          seed=1234)

	# print the model summary to see the number of datapoints are in each cluster
	summary(iris_kmeans)


   .. code-block:: python
   
	import h2o
	from h2o.estimators.gbm import H2OGradientBoostingEstimator
	h2o.init()
	h2o.cluster().show_status()

	# import the iris dataset:
	# this dataset is used to classify the type of iris plant
	# the original dataset can be found at https://archive.ics.uci.edu/ml/datasets/Iris
	iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")

	# try using the `cluster_size_constraints` parameter:
	kmm = H2OKMeansEstimator(k=3, 
	                         standardize=True, 
	                         cluster_size_constraints=[2, 5, 8], 
	                         score_each_iteration=True, 
	                         seed=1234)
	kmm.train(x=list(range(7)), training_frame=iris)

	# print the model summary to see the number of datapoints are in each cluster
	kmm.show()
