Saving and Loading a Model
==========================

This section describes how to save and load models using Flow, R, and Python. 

In Flow
-------

There are a number of ways you can save your model in Flow. 

- In the web UI, click the **Flow** menu, then click **Save Flow**. Your flow is saved to the *Flows* tab in the **Help** sidebar on the right.
- In the web UI, click the **Flow** menu, then click **Download this Flow...**. Depending on your browser and configuration, your flow is saved to the "Downloads" folder (by default) or to the location you specify in the pop-up **Save As** window if it appears.
- For DRF, GBM, and DL models only: Use model checkpointing to resume training a model. Copy the ``model_id`` number from a built model and paste it into the *checkpoint* field in the ``buildModel`` cell.

 **Note**: When you are running H2O on Hadoop, H2O tries to determine the home HDFS directory so it can use that as the download location. If the default home HDFS directory is not found, manually set the download location from the command line using the ``-flow_dir`` parameter. For example, 

 ::

	hadoop jar h2odriver.jar <...> -flow_dir hdfs:///user/yourname/yourflowdir). 

 You can view the default download directory in the logs by clicking **Admin > View logs...** and looking for the line that begins with ``Flow dir:``.

After a Flow is saved, you can load it by clicking on the **Flows** tab in the right sidebar. Then in the pop-up confirmation window that appears, select **Load Notebook**. Refer to `Loading Flows <flow.html#loading-flows>`__ for more information. 

In Flow, you can also import specific models rather than entire Flows. Refer to `Exporting and Importing Models <flow.html#exporting-and-importing-models>`__ for more information. 

In R and Python
---------------

In R and Python, you can save a model locally or to HDFS using the ``h2o.saveModel`` (R) or ``h2o.save_model`` (Python) function . This function accepts the model object and the file path. If no path is specified, then the model will be saved to the current working directory. After the model is saved, you can load it using the ``h2o.loadModel`` (R) or ``h2o.load_model`` (Python) function.

.. example-code::
   .. code-block:: r

    # build the model
    model <- h2o.deeplearning(params)

    # save the model
    model_path <- h2o.saveModel(object=model, path=getwd(), force=TRUE)

    print(model_path)
    /tmp/mymodel/DeepLearning_model_R_1441838096933

    # load the model
    saved_model <- h2o.loadModel(model_path)

   .. code-block:: python

	# build the model
	model = H2ODeepLearningEstimator(params)
	model.train(params)

	# save the model
	model_path = h2o.save_model(model=model, path="/tmp/mymodel", force=True)

	print model_path
	/tmp/mymodel/DeepLearning_model_python_1441838096933

	# load the model
	saved_model = h2o.load_model(model_path)
 

**Note**: When saving to HDFS, you must prepend the save directory with ``hdfs://``. For example:

.. example-code::
   .. code-block:: r

	# build the model
	model <- h2o.glm(model params)

	# save the model to HDFS
	hdfs_name_node <- "node-1"
	hdfs_tmp_dir <- "/tmp/runit”
	model_path <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_tmp_dir)
	h2o.saveModel(model, dir=model_path, name="mymodel")

   .. code-block:: python

	# build the model
	h2o_glm = H2OGeneralizedLinearEstimator(model params)
	h2o_glm.train(training params)

	# save the model to HDFS
	hdfs_name_node = "node-1"
	hdfs_model_path = sprintf("hdfs://%s%s", hdfs_name_node, hdfs_tmp_dir)
	new_model_path = h2o.save_model(h2o_glm, "hdfs://" + hdfs_name_node + "/" + hdfs_model_path)
