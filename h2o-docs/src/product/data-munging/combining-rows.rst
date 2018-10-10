Combining Rows from Two Datasets
--------------------------------

You can use the ``rbind`` function to combine two similar datasets into a single large dataset. This can be used, for example, to create a larger dataset by combining data from a validation dataset with its training or testing dataset.

Note that when using ``rbind``, the two datasets must have the same set of columns.

.. example-code::
   .. code-block:: r
   
	library(h2o)
	h2o.init()
	
	# Import an existing training dataset
	ecg1Path <- "http://h2o-public-test-data.s3.amazonaws.com/smalldata/anomaly/ecg_discord_train.csv"
	ecg1.hex <- h2o.importFile(path=ecg1Path, destination_frame="ecg1.hex")
	print(dim(ecg1.hex))
	[1] 20 210 

	# Import an existing testing dataset
	ecg2Path <- "http://h2o-public-test-data.s3.amazonaws.com/smalldata/anomaly/ecg_discord_test.csv"
	ecg2.hex <- h2o.importFile(path=ecg2Path, destination_frame="ecg2.hex")
	print(dim(ecg2.hex))
	[1] 23 210

	# Combine the two datasets into a single, larger dataset
	ecgCombine.hex <- h2o.rbind(ecg1.hex, ecg2.hex)
	print(dim(ecgCombine.hex))
	[1] 43 210


   .. code-block:: python

	import h2o
	import numpy as np
	h2o.init()
	
	# Generate a random dataset with 100 rows 4 columns. 
	# Label the columns A, B, C, and D.
	df1 = h2o.H2OFrame.from_python(np.random.randn(100,4).tolist(), column_names=list('ABCD'))
	df1.describe
	        A           B          C           D
	---------  ----------  ---------  ----------
	 0.412228  -0.991376   -1.44374   -0.276455
	 0.348039  -0.193704   -0.370882   0.162211
	 0.125303  -1.24546    -0.916738   1.08088
	 0.293062   0.516151    0.739798  -0.430679
	-0.363344   0.0558051  -1.43888    1.13882
	-1.17492   -0.332647   -1.18689    0.533313
	 0.154774   1.46559     0.373058  -0.915895
	 0.555835  -0.0891554  -1.19151    0.623667
	-1.13092    0.843549   -0.532341  -0.0739869
	 0.752855  -0.168504   -0.750161  -2.46084

	[100 rows x 4 columns]
	
	# Generate a second random dataset with 100 rows and 4 columns. 
	# Again, label the columns, A, B, C, and D.
	df2 = h2o.H2OFrame.from_python(np.random.randn(100,4).tolist(), column_names=list('ABCD'))
	df2.describe
	          A          B          C          D
	-----------  ---------  ---------  ---------
	 0.00118227  -0.835817   1.06634    1.81794
	-0.542678    -0.494483   0.109813   0.714271
	-0.365611    -0.679095   0.891982  -1.93362
	-0.0533568    0.86035   -2.28902   -1.287
	-0.572775     1.30954    0.27412   -0.287373
	 0.310976    -0.594283  -0.566955   0.221888
	 1.34778     -1.02348    0.243686   0.319585
	 0.383136    -0.113979  -0.901779  -0.383478
	-0.968212    -0.606603  -0.828677   0.699539
	 0.491119    -0.629774  -0.632143   0.2898

	[100 rows x 4 columns]
	
	# Bind the rows from the second dataset into the first dataset.
	df1.rbind(df2)
	        A           B          C           D
	---------  ----------  ---------  ----------
	 0.412228  -0.991376   -1.44374   -0.276455
	 0.348039  -0.193704   -0.370882   0.162211
	 0.125303  -1.24546    -0.916738   1.08088
	 0.293062   0.516151    0.739798  -0.430679
	-0.363344   0.0558051  -1.43888    1.13882
	-1.17492   -0.332647   -1.18689    0.533313
	 0.154774   1.46559     0.373058  -0.915895
	 0.555835  -0.0891554  -1.19151    0.623667
	-1.13092    0.843549   -0.532341  -0.0739869
	 0.752855  -0.168504   -0.750161  -2.46084

	[200 rows x 4 columns]

