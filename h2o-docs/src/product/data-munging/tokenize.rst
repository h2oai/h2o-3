.. _tokenize:

Tokenize strings
================

The ``tokenize`` function is available in H2O-3. This function converts strings into tokens then stores the tokenized text into a single column, therefore making it easier for additional processing. 

Tokenize example
----------------

The following short example shows strings from frames tokenized into a single column. Refer to the following demos for a more extensive demo using tokenized text in Word2Vec:

- `Python tokenizing demo <https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/word2vec_craigslistjobtitles.ipynb>`__
- `R tokenizing demo <https://github.com/h2oai/h2o-3/blob/master/h2o-r/demos/rdemo.word2vec.craigslistjobtitles.R>`__

.. tabs::
   .. code-tab:: python

        import h2o
        h2o.init()

        # Create four simple, single-column Python data frames by inputting values
        df1 = h2o.H2OFrame.from_python({'String':[' this is a string ']})
        df1 = df1.ascharacter()
        df2 = h2o.H2OFrame.from_python({'String':['this is another string']})
        df2 = df2.ascharacter()
        df3 = h2o.H2OFrame.from_python({'String':['this is a longer string']})
        df3 = df3.ascharacter()
        df4 = h2o.H2OFrame.from_python({'String':['this is tall, this is taller']})
        df4 = df4.ascharacter()

        # Combine the datasets into a single dataset. 
        combined = df1.rbind([df2, df3, df4])
        combined
        String
        ----------------------------
        this is a string
        this is another string
        this is a longer string
        this is tall, this is taller

        # Tokenize the dataset.
        # Notice that tokenized sentences are separated by empty rows.
        tokenized = combined.tokenize(" ")
        tokenized.describe
        C1
        -------

        this
        is
        a
        string

        this
        is
        another
        string

        [24 rows x 1 column]

   .. code-tab:: r R
	
    	library(h2o)
    	h2o.init()
    	
    	# Create four simple, single-column R data frames by inputting values.
    	s1 <- as.character(as.h2o(" this is a string "))
    	s2 <- as.character(as.h2o("this is another string"))
    	s3 <- as.character(as.h2o("this is a longer string"))
    	s4 <- as.character(as.h2o("this is tall, this is taller"))

    	# Combine the datasets into a single dataset. 
    	ds <- h2o.rbind(s1, s2, s3, s4)
    	ds
    	                            C1
    	1            this is a string 
    	2       this is another string
    	3      this is a longer string
    	4 this is tall, this is taller

    	# Tokenize the dataset.
    	# Notice that tokenized sentences are separated by <NA>.
    	tokenized <- h2o.tokenize(ds, " ")
    	tokenized
    	      C1
    	1       
    	2   this
    	3     is
    	4      a
    	5 string
    	6   <NA>

    	[24 rows x 1 column]

