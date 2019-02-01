Reproducibility
^^^^^^^^^^^^^^^

- **Is it possible to guarantee reproducibility in single node cluster?**

 The following criteria must be met to guarantee reproducibility in a single node cluster:
 
 - Same training data
 
   - **Note**: If you have H2O import a whole directory with multiple files instead of a single file, we do not guarantee reproducibility because the data may be shuffled during import.
   
 - Same parameters used to train the model
 - Same seed used if sampling is done
   
   - Parameters that perform sampling: 
       - ``sample_rate``
       - ``sample_rate_per_class``
       - ``col_sample_rate``
       - ``col_sample_rate_change_per_level``
       - ``col_sample_rate_per_tree``

 - No early stopping or early stopping with ``score_tree_interval`` explicitly specified and same validation data
   
- **Is it possible to guarantee reproducibility in multi-node clusters?**

 The following criteria must be met to guarantee reproducibility in a multi-node cluster:

 - Reproducible requirements for single node cluster are met. (See above.)
 - The cluster configuration is the same.
    
    - Clusters must have the same number of nodes.
    - Modes must have the same number of CPU cores available (or same restriction on number of threads).

 - The model training is triggered from the leader node of the cluster. (See note below.)

  **Note**: When H2O is running on Hadoop, the leader node is automatically returned by the h2odriver as the node that the user should connect to. In multi-node deployments of Standalone H2O, the leader node has to be manually identified by the user. Flow users can easily check whether they are connected to the leader node by opening Cluster Status (from the Admin menu) and checking that the first node has the same IP address as they see in their browser's address bar.
 
 .. figure:: ../../images/GBMReproducibility_LeaderNode.png
    :alt: Leader node in cluster status

 **Note**: In H2O versions prior to 3.18.0.1, the node automatically returned by the h2odriver while running on Hadoop was not guaranteed to be the leader node.  If you are using a version prior to 3.18.0.1 and you are running H2O on Hadoop, the leader node has to be manually identified by the user for reproducibility.

- **How deterministic is GBM?**

 As long as criteria for reproducibility is met (see above), GBM is deterministic up to floating point rounding errors (out-of-order atomic addition of multiple threads during histogram building). 

- **What are the best practices to ensure reproducibility?**

 When building a model the user should perform the following steps to make sure the model can be reproduced in the future:

 1. Save any script used to pre-process the data before model training.
   
   - In the event that you will need to reproduce your model, it is important to save the steps used to create the training data. 
   - This should include how the data is imported into the H2O cluster and whether any data types have been modified (i.e., was a numeric column converted to categorical).

 2. Save information about the H2O cluster.
   
   - What are the number of nodes in the cluster?
   - What are the number of CPU cores available?

 3. Save the H2O binary model. 
   
   - Refer to :ref:`save_and_load_model` for more information.
   - The binary model will contain the H2O version and the parameters used to train the model.

 4. Train the GBM model using the same:
	
   - training data (information about the training data saved in Step 1)
   - cluster configuration (information about the cluster configuration saved in Step 2)
   - same parameters (information about the parameters saved in Step 3)

   You do not need to explicitly set a seed to make a GBM model reproducible.  If no seed is set, then H2O-3 will randomly choose a seed.  This seed is saved in the binary model.  To reproduce your model, you can extract the seed from the binary model and re-train the GBM with the seed set.
   
   .. example-code::
      .. code-block:: r

      # Train GBM without Seed
      gbm_v1 <- h2o.gbm(y = "Species", training_frame = df, sample_rate = 0.7)

      # Reproduce GBM
      seed <- gbm_v1@allparameters$seed
      gbm_v2 <- h2o.gbm(y = "Species", training_frame = df, sample_rate = 0.7, seed = seed)

      .. code-block:: python

      # Train GBM without Seed
      from h2o.estimators import H2OGradientBoostingEstimator
      gbm_v1 = H2OGradientBoostingEstimator(sample_rate = 0.7)
      gbm_v1.train(y = "Species", training_frame = df)

      # Reproduce GBM
      seed = gbm_v1.params.get('seed').get('actual')
      gbm_v2 = H2OGradientBoostingEstimator(sample_rate = 0.7, seed = seed)
      gbm_v2.train(y = "Species", training_frame = df)
