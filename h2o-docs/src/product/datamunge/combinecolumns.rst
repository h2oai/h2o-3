Combining Columns from Two Datasets
-----------------------------------

The ``cbind`` function allows you to combine datasets by adding columns from one dataset into another. Note that when using ``cbind``, the two datasets must have the same number of rows. In addition, if the datasets contain common column names, H2O will append the joined column with ``0``. 

.. example-code::
   .. code-block:: r
	
	> library(h2o)
	> h2o.init(nthreads=-1)
	
	# Create two simple, two-column R data frames by inputting values, ensuring that both have a common column (in this case, "fruit").
	> left <- data.frame(fruit = c('apple','orange','banana','lemon','strawberry','blueberry'), color = c('red','orange','yellow','yellow','red','blue'))
	> right <- data.frame(fruit = c('apple','orange','banana','lemon','strawberry','watermelon'), citrus = c(FALSE, TRUE, FALSE, TRUE, FALSE, FALSE))
	
	# Create the H2O data frames from the inputted data.
	> l.hex <- as.h2o(left)
	> print(l.hex)
	        fruit  color
	 1      apple    red
	 2     orange orange
	 3     banana yellow
	 4      lemon yellow
	 5 strawberry    red
	 6  blueberry   blue
	
	[6 rows x 2 columns]
	
	> r.hex <- as.h2o(right)
	> print(r.hex)
	        fruit  color
	 1      apple  FALSE
	 2     orange   TRUE
	 3     banana  FALSE
	 4      lemon   TRUE
	 5 strawberry  FALSE
	 6 watermelon  FALSE

	[6 rows x 2 columns]

	# Combine the l.hex and r.hex datasets into a single dataset. 
	#The columns from r.hex will be appended to the right side of the final dataset. In addition, because both datasets include a "fruit" column, H2O will append the second "fruit" column name with "0". 
	#Note that this is different than ``merge``, which combines data from two commonly named columns in two datasets. 
	
	> columns.hex <- h2o.cbind(l.hex, r.hex)
	> print(columns.hex)
	       fruit  color     fruit0 citrus
	1      apple    red      apple  FALSE
	2     orange orange     orange   TRUE
	3     banana yellow     banana  FALSE
	4      lemon yellow      lemon   TRUE
	5 strawberry    red strawberry  FALSE
	6  blueberry   blue watermelon  FALSE
	
	[6 rows x 4 columns]

		
   .. code-block:: python
   
	>>> import h2o
	>>> h2o.init()
	>>> import numpy as np
	
	# Generate a random dataset with 10 rows 4 columns. Label the columns A, B, C, and D.
	>>> cols1_df = h2o.H2OFrame.from_python(np.random.randn(10,4).tolist(), column_names=list('ABCD'))
	>>> cols1_df.describe
	         A           B           C           D
	----------  ----------  ----------  ----------
	nan         nan         nan         nan
	 -0.372305   -0.744047   -1.89198    -0.66457
	  0.18704     0.176037    0.38628    -1.55655
	 -1.19211     0.579382    1.99508     1.13262
	  0.144151    1.39129    -1.01831    -0.678329
	  0.660908   -0.276543    0.366156    0.861158
	 -0.373436    0.280039   -0.312323    1.59981
	  0.257874    3.93677    -0.681923    0.335323
	  0.193658   -1.20955    -1.57454    -0.825441
	  0.961897    0.194851    0.807101   -1.56672
	
	[11 rows x 4 columns]
	
	# Generate a second random dataset with 10 rows and 1 column. Label the columns, Y and D.
	>>> cols2_df = h2o.H2OFrame.from_python(np.random.randn(10,4).tolist(), column_names=list('YZ'))
	>>> cols2_df.describe
         	  Y            Z
	------------  -----------
	nan           nan
	  0.00313617   -0.171366
	 -1.14186       0.932378
	  0.251192     -0.384113
	  0.603271     -0.275116
	 -0.435936     -0.284039
	 -1.13324      -0.163877
	 -0.0475909    -2.65027
	  1.49039      -0.0887757
	  0.906927     -1.12668
	
	[11 rows x 2 columns]

	# Add the columns from the second dataset into the first. H2O will append these as the right-most columns.
	>>> colsCombine_df = cols1_df.cbind(cols2_df)
	>>> colsCombine_df.describe
         	A           B           C           D             Y            Z
	----------  ----------  ----------  ----------  ------------  -----------
	nan         nan         nan         nan         nan           nan
	 -0.372305   -0.744047   -1.89198    -0.66457     0.00313617   -0.171366
	  0.18704     0.176037    0.38628    -1.55655    -1.14186       0.932378
	 -1.19211     0.579382    1.99508     1.13262     0.251192     -0.384113
	  0.144151    1.39129    -1.01831    -0.678329    0.603271     -0.275116
	  0.660908   -0.276543    0.366156    0.861158   -0.435936     -0.284039
	 -0.373436    0.280039   -0.312323    1.59981    -1.13324      -0.163877
	  0.257874    3.93677    -0.681923    0.335323   -0.0475909    -2.65027
	  0.193658   -1.20955    -1.57454    -0.825441    1.49039      -0.0887757
	  0.961897    0.194851    0.807101   -1.56672     0.906927     -1.12668
	
