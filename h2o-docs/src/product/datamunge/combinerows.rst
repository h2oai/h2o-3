Combining Rows from Two Datasets
--------------------------------

You can use the ``rbind`` function to combine two similar datasets into a single large dataset. This can be used, for example, to create a larger dataset by combining data from a validation dataset with its training or testing dataset.

Note that when using ``rbind``, the two datasets must have the same set of columns.

.. example-code::
   .. code-block:: r
   
	> library(h2o)
	> h2o.init(nthreads=-1)
	
	# Import exsiting training and testing datasets
	> ecg1Path = "../../../smalldata/anomaly/ecg_discord_train.csv"
	> ecg1.hex = h2o.importFile(path=ecg1Path, destination_frame="ecg1.hex")
	> ecg2Path = "../../../smalldata/anomaly/ecg_discord_test.csv"
	> ecg2.hex = h2o.importFile(path=ecg2Path, destination_frame="ecg2.hex")

	# Combine the two datasets into a single, larger dataset
	> ecgCombine.hex <- h2o.rbind(ecg1.hex, ecg2.hex)

   .. code-block:: python

	>>> import h2o
	>>> import numpy as np
	>>> h2o.init()
	
	# Generate a random dataset with 100 rows 4 columns. Label the columns A, B, C, and D.
	>>> df1 = h2o.H2OFrame.from_python(np.random.randn(100,4).tolist(), column_names=list('ABCD'))
	>>> df1.describe
	          A           B            C            D
	-----------  ----------  -----------  -----------
	nan          nan         nan          nan
	 -0.148045     0.516651   -0.218871    -2.11336
	  0.818191    -1.07749    -0.303827     0.0234708
	 -0.894042    -1.83727     1.69621     -0.306524
	 -1.90056      0.528147   -0.745829     0.325673
	 -1.14653      0.146565   -1.12463     -1.39162
	  0.81608      0.21313    -0.122169     1.47247
	  0.419028     1.14975     0.913349     0.975779
	  0.419134    -1.63199     0.633799     0.482761
	  0.0366856   -1.09199    -0.0831492    2.17306
	
	[101 rows x 4 columns]
	
	# Generate a second random dataset with 100 rows and 4 columns. Again, label the columns, A, B, C, and D.
	>>> df2 = h2o.H2OFrame.from_python(np.random.randn(100,4).tolist(), column_names=list('ABCD'))
	>>> df2.describe
	          A            B           C           D
	-----------  -----------  ----------  ----------
	nan          nan          nan         nan
	  0.626459    -1.80634     -1.08245     1.29828
	  1.31526     -0.223264     0.172243   -0.76666
	  1.70095     -0.666482    -0.486086   -1.16518
	 -0.241271    -1.08439      1.75451     1.37618
	 -0.151067    -0.830386     0.7113     -0.979204
	 -2.18042     -1.85949     -0.466211    0.707786
	 -0.0657297   -0.0092001    1.3721     -0.570298
	  1.59816     -0.149408    -0.874023   -0.883033
	 -0.367047    -0.586965    -0.98553    -1.33043
	
	[101 rows x 4 columns]
	
	# Bind the rows from the second dataset into the first dataset.
	>>> df1.rbind(df2)
	>>> df1.describe
          	A           B            C            D
	-----------  ----------  -----------  -----------
	nan          nan         nan          nan
	 -0.148045     0.516651   -0.218871    -2.11336
	  0.818191    -1.07749    -0.303827     0.0234708
	 -0.894042    -1.83727     1.69621     -0.306524
	 -1.90056      0.528147   -0.745829     0.325673
	 -1.14653      0.146565   -1.12463     -1.39162
	  0.81608      0.21313    -0.122169     1.47247
	  0.419028     1.14975     0.913349     0.975779
	  0.419134    -1.63199     0.633799     0.482761
	  0.0366856   -1.09199    -0.0831492    2.17306
	
	[202 rows x 4 columns]
