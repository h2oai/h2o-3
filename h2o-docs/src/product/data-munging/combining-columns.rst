Combining Columns from Two Datasets
-----------------------------------

The ``cbind`` function allows you to combine datasets by adding columns from one dataset into another. Note that when using ``cbind``, the two datasets must have the same number of rows. In addition, if the datasets contain common column names, H2O will append the joined column with ``0``. 

.. example-code::
   .. code-block:: r
	
	library(h2o)
	h2o.init()
	
	# Create two simple, two-column R data frames by inputting values, 
	# ensuring that both have a common column (in this case, "fruit").
	left <- data.frame(fruit = c('apple','orange','banana','lemon','strawberry','blueberry'), 
	     color = c('red','orange','yellow','yellow','red','blue'))
	right <- data.frame(fruit = c('apple','orange','banana','lemon','strawberry','watermelon'), 
	    citrus = c(FALSE, TRUE, FALSE, TRUE, FALSE, FALSE))
	
	# Create the H2O data frames from the inputted data.
	l.hex <- as.h2o(left)
	print(l.hex)
	        fruit  color
	 1      apple    red
	 2     orange orange
	 3     banana yellow
	 4      lemon yellow
	 5 strawberry    red
	 6  blueberry   blue
	
	[6 rows x 2 columns]
	
	r.hex <- as.h2o(right)
	print(r.hex)
	        fruit  citrus
	 1      apple  FALSE
	 2     orange   TRUE
	 3     banana  FALSE
	 4      lemon   TRUE
	 5 strawberry  FALSE
	 6 watermelon  FALSE

	[6 rows x 2 columns]

	# Combine the l.hex and r.hex datasets into a single dataset. 
	# The columns from r.hex will be appended to the right side of the final dataset. 
	# In addition, because both datasets include a "fruit" column, H2O will append the 
	# second "fruit" column name with "0". Note that this is different than ``merge``, 
	# which combines data from two commonly named columns in two datasets. 
	
	columns.hex <- h2o.cbind(l.hex, r.hex)
	print(columns.hex)
	       fruit  color     fruit0 citrus
	1      apple    red      apple  FALSE
	2     orange orange     orange   TRUE
	3     banana yellow     banana  FALSE
	4      lemon yellow      lemon   TRUE
	5 strawberry    red strawberry  FALSE
	6  blueberry   blue watermelon  FALSE
	
	[6 rows x 4 columns]

		
   .. code-block:: python
   
	import h2o
	h2o.init()
	import numpy as np
	
	# Generate a random dataset with 10 rows 4 columns. 
	# Label the columns A, B, C, and D.
	cols1_df = h2o.H2OFrame.from_python(np.random.randn(10,4).tolist(), column_names=list('ABCD'))
	cols1_df.describe
	        A          B          C           D
	---------  ---------  ---------  ---------- 
	 0.660737  -1.11679    0.278233  -0.0326621
	-0.124613  -0.668794   0.558957   1.11402
	 0.944408  -1.6397     0.616223   0.137581
	 0.739501   0.671192   0.715497  -0.361146
	 1.52177    0.232701   0.196153   0.499426
	-1.48407    0.222175   2.45155   -0.470239
	 0.880962   0.906569  -0.767418   1.38261
	 0.509212   0.602155   1.41956    1.96045
	 1.11071    0.779309   1.77455   -0.400746
	-0.881062  -0.897391   0.980548  -0.266982

	[10 rows x 4 columns]
	
	# Generate a second random dataset with 10 rows and 1 column. 
	# Label the columns, Y and D.
	cols2_df = h2o.H2OFrame.from_python(np.random.randn(10,2).tolist(), column_names=list('YZ'))
	cols2_df.describe
	         Y           Z
	----------  ----------
	 0.54945     0.0283338
	 1.27367    -1.46298
	 0.875547    0.317876
	 2.12603     0.371443
	 0.662796    1.0291
	-0.267864    0.86477
	-1.51065     0.71466
	 0.0676983  -0.844925
	 0.311779    0.0397941
	 0.363517    0.465146

	[10 rows x 2 columns]

	# Add the columns from the second dataset into the first. 
	# H2O will append these as the right-most columns.
	colsCombine_df = cols1_df.cbind(cols2_df)
	colsCombine_df.describe
	        A          B          C           D           Y           Z
	---------  ---------  ---------  ----------  ----------  ----------
	 0.660737  -1.11679    0.278233  -0.0326621   0.54945     0.0283338
	-0.124613  -0.668794   0.558957   1.11402     1.27367    -1.46298
	 0.944408  -1.6397     0.616223   0.137581    0.875547    0.317876
	 0.739501   0.671192   0.715497  -0.361146    2.12603     0.371443
	 1.52177    0.232701   0.196153   0.499426    0.662796    1.0291
	-1.48407    0.222175   2.45155   -0.470239   -0.267864    0.86477
	 0.880962   0.906569  -0.767418   1.38261    -1.51065     0.71466
	 0.509212   0.602155   1.41956    1.96045     0.0676983  -0.844925
	 1.11071    0.779309   1.77455   -0.400746    0.311779    0.0397941
	-0.881062  -0.897391   0.980548  -0.266982    0.363517    0.465146

	[10 rows x 6 columns]
	
