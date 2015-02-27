H2O Module
==========

:mod:`h2o.h2o` Module
-------------------------

.. automodule:: h2o.h2o
    :members:
    :undoc-members:
    :show-inheritance:

Example
-------

Here is a small example (H2O on Hadoop) :

.. code-block:: python

	import h2o
	h2o.init(ip="192.168.1.10", port=54321)
	--------------------------  ------------------------------------
	H2O cluster uptime:         2 minutes 1 seconds 966 milliseconds
	H2O cluster version:        0.1.27.1064
	H2O cluster name:           H2O_96762
	H2O cluster total nodes:    4
	H2O cluster total memory:   38.34 GB
	H2O cluster total cores:    16
	H2O cluster allowed cores:  80
	H2O cluster healthy:        True
	--------------------------  ------------------------------------
	pathDataTrain = ["hdfs://192.168.1.10/user/data/data_train.csv"]
	pathDataTest = ["hdfs://192.168.1.10/user/data/data_test.csv"]
	trainFrame = h2o.import_frame(path=pathDataTrain)
	testFrame = h2o.import_frame(path=pathDataTest)

	#Parse Progress: [##################################################] 100%
	#Imported [hdfs://192.168.1.10/user/data/data_train.csv'] into cluster with 60000 rows and 500 cols

	#Parse Progress: [##################################################] 100%
	#Imported ['hdfs://192.168.1.10/user/data/data_test.csv'] into cluster with 10000 rows and 500 cols

	trainFrame[499]._name = "label"
	testFrame[499]._name = "label"

	model = h2o.gbm(x=trainFrame.drop("label"),
              y=trainFrame["label"],
              validation_x=testFrame.drop("label"),
              validation_y=testFrame["label"],
              ntrees=100,
              max_depth=10
              )

	#gbm Model Build Progress: [##################################################] 100%

	predictFrame = model.predict(testFrame)
	model.model_performance(testFrame)