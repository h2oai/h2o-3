Scoring
^^^^^^^

- **I want to score multiple models on a huge dataset. Is it possible to score these models in parallel?**

 The best way to score models in parallel is to use the in-H2O binary models. To do this:

  1. Import the binary (non-POJO, previously exported) model into an H2O cluster
  2. Import the datasets into H2O as well. 
  3. Call the predict endpoint either from R, Python, Flow, or the REST API directly. 
  4. Export the predictions to file or download them from the server.

 You can also score models in parallel by downloading a pojo for each model, and then embedding those pojos within a HIVE UDF to score the large dataset stored on Hadoop. A tutorial on this process can be found `here <http://docs.h2o.ai/h2o-tutorials/latest-stable/tutorials/hive_udf_template/index.html>`__.

- **Which parameters are used with or for scoring?**

 - ``score_each_iteration``
 - ``score_tree_interval``