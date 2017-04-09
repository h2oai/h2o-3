.. _Data_Munging:


Data Manipulation
=================

This section provides examples of common tasks performed when preparing data for machine learning. These examples are run on a local cluster.

**Note**: The examples in this section include datasets that are pulled from GitHub and S3. Alternatively, you can run the following command from within the H2O repository on your local machine to retrieve all datasets in the smalldata folder:

 ::
  
  ./gradlew syncSmalldata

.. toctree::
   :maxdepth: 1

   data-munging/uploading-data
   data-munging/importing-data
   data-munging/importing-files
   data-munging/merging-data
   data-munging/groupby
   data-munging/combining-rows
   data-munging/combining-columns
   data-munging/slicing-rows
   data-munging/slicing-columns
   data-munging/replacing-values
   data-munging/splitting-datasets
   data-munging/imputing-data
