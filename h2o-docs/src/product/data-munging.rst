.. _Data_Munging:


Data manipulation
=================

This section provides examples of common tasks performed when preparing data for machine learning. These examples are run on a local cluster.

.. note::
   
   The examples in this section include datasets that are pulled from GitHub and S3. 

.. toctree::
   :maxdepth: 1

   data-munging/uploading-data
   data-munging/importing-data
   data-munging/importing-files
   data-munging/downloading-data
   data-munging/change-column-type
   data-munging/combining-columns
   data-munging/combining-rows
   data-munging/fillnas
   data-munging/groupby
   data-munging/imputing-data
   data-munging/merging-data
   data-munging/pivot
   data-munging/replacing-values
   data-munging/slicing-columns
   data-munging/slicing-rows
   data-munging/sortcolumn
   data-munging/splitting-datasets
   data-munging/tokenize

Feature engineering
-------------------

H2O-3 also has methods for feature engineering. `Target Encoding <data-science/target-encoding.html>`__ is a categorical encoding technique which replaces a categorical value with the mean of the target variable (this is especially useful for high-cardinality features). `Word2vec <data-science/word2vec.html>`__ is a text processing method which converts a corpus of text into an output of word vectors.
