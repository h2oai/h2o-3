Merging Two Datasets
--------------------

You can use the `merge` function to combine two datasets that share a common column name. By default, all columns in common are used as the merge key; uncommon will be ignored. Also, if you want to use only a subset of the columns in common, rename the other columns so the columns are unique in the merged result.

Note that in order for a merge to work in multinode clusters, one of the datasets must be small enough to exist in every node.  


.. example-code::
   .. code-block:: r
   
	# Currently, this function only supports `all.x = TRUE`. All other permutations will fail.
	library(h2o)
	h2o.init()
	
	# Create two simple, two-column R data frames by inputting values, ensuring that both have a common column (in this case, "fruit").
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
	        fruit citrus
	 1      apple  FALSE
	 2     orange   TRUE
	 3     banana  FALSE
	 4      lemon   TRUE
	 5 strawberry  FALSE
	 6 watermelon  FALSE

	[6 rows x 2 columns]
	
	# Merge the data frames. The result is a single dataset with three columns.
	left.hex <- h2o.merge(l.hex, r.hex, all.x = TRUE)
	print(left.hex)
	       fruit  color citrus
	1  blueberry   blue   <NA>
	2      apple    red  FALSE
	3     banana yellow  FALSE
	4      lemon yellow   TRUE
	5     orange orange   TRUE
	6 strawberry    red  FALSE
	
	[6 rows x 3 columns] 
   
   .. code-block:: python
   
	import h2o
	h2o.init()
	import numpy as np
	
	# Create a dataset by inputting raw data. 
	df1 = h2o.H2OFrame.from_python({'A':['Hello', 'World', 'Welcome', 'To', 'H2O', 'World'], 
	                                'n': [0,1,2,3,4,5]})
	df1.describe
	A          n
	-------  ---
	Hello      0
	World      1
	Welcome    2
	To         3
	H2O        4
	World      5
	
	[6 rows x 2 columns]
	
	# Generate a random dataset from python. 
	df2 = h2o.H2OFrame.from_python([[x] for x in np.random.randint(0, 10, size=20).tolist()], column_names=['n'])
	df2.describe
	  n
	---
	nan
	  0
	  8
	  6
	  1
	  7
	  8
	  5
	  1
	  3
	  
	[21 rows x 1 column]
	
	# Merge the first dataset into the second dataset. Note that only columns 
	# in common are merged (i.e, values in df2 greater than 5 will not be merged).
	df3 = df2.merge(df1)
	df3.describe
	  n  A
	---  -------
	nan  Hello
	  3  To
	  3  To
	  0  Hello
	  5  World
	  3  To
	  0  Hello
	  5  World
	  1  World
	  2  Welcome
	  
	[14 rows x 2 columns]
	
	# Merge all of df2 into df1. Note that this will result in missing values for 
	# column A, which does not include values greater than 5.
	df4 = df2.merge(df1, all_x=True)
	df4.describe
	  n  A
	---  -----
	nan  Hello
	  0  Hello
	  8
	  6
	  1  World
	  7
	  8
	  5  World
	  1  World
	  3  To
	
	[21 rows x 2 columns]
	
