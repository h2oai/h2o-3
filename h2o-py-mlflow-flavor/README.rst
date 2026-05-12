H2O-3 MLFlow Flavor
===================

A tiny library containing a `MLFlow <https://mlflow.org/>`_ flavor for working with H2O-3 MOJO and POJO models.

Logging Models to MLFlow Registry
---------------------------------

The model that was trained with H2O-3 runtime can be exported to MLFlow registry with `log_model` function.:

.. code-block:: Python

    import mlflow
    import h2o_mlflow_flavor

    mlflow.set_tracking_uri("http://127.0.0.1:8080")
    
    h2o_model = ... training phase ...
    
    with mlflow.start_run(run_name="myrun") as run:
	h2o_mlflow_flavor.log_model(h2o_model=h2o_model,
                                    artifact_path="folder",
                                    model_type="MOJO",
                                    extra_prediction_args=["--predictCalibrated"])


Compared to `log_model` functions of the other flavors being a part of MLFlow, this function has two extra arguments:
	
* ``model_type`` - It indicates whether the model should be exported as `MOJO <https://docs.h2o.ai/h2o/latest-stable/h2o-docs/mojo-quickstart.html#what-is-a-mojo>`_ or `POJO <https://docs.h2o.ai/h2o/latest-stable/h2o-docs/pojo-quickstart.html#what-is-a-pojo>`_. The default value is `MOJO`.

* ``extra_prediction_args`` - A list of extra arguments for java scoring process. Possible values:

  * ``--setConvertInvalidNum`` - The scoring process will convert invalid numbers to NA.

  * ``--predictContributions`` - The scoring process will Return also Shapley values a long with the predictions. Model must support that Shapley values, otherwise scoring process will throw an error.

  * ``--predictCalibrated`` - The scoring process will also return calibrated prediction values.
   
The `save_model` function that persists h2o binary model to MOJO or POJO has the same signature as the `log_model` function.

Extracting Information about Model
----------------------------------

The flavor offers several functions to extract information about the model.

* ``get_metrics(h2o_model, metric_type=None)`` - Extracts metrics from the trained H2O binary model. It returns dictionary and takes following parameters:

  * ``h2o_model`` - An H2O binary model.

  * ``metric_type`` - The type of metrics. Possible values are "training", "validation", "cross_validation". If parameter is not specified, metrics for all types are returned.

* ``get_params(h2o_model)`` - Extracts training parameters for the H2O binary model. It returns dictionary and expects one parameter:

  * ``h2o_model`` - An H2O binary model.

* ``get_input_example(h2o_model, number_of_records=5, relevant_columns_only=True)`` - Creates an example Pandas dataset from the training dataset of H2O binary model. It takes following parameters:

  * ``h2o_model`` - An H2O binary model.

  * ``number_of_records`` - A number of records that will be extracted from the training dataset.

  * ``relevant_columns_only`` - A flag indicating whether the output dataset should contain only columns required by the model. Defaults to ``True``.
  
The functions can be utilized as follows:

.. code-block:: Python

    import mlflow
    import h2o_mlflow_flavor
    
    mlflow.set_tracking_uri("http://127.0.0.1:8080")

    h2o_model = ... training phase ...

    with mlflow.start_run(run_name="myrun") as run:
	    mlflow.log_params(h2o_mlflow_flavor.get_params(h2o_model))
	    mlflow.log_metrics(h2o_mlflow_flavor.get_metrics(h2o_model))
	    input_example = h2o_mlflow_flavor.get_input_example(h2o_model)
	    h2o_mlflow_flavor.log_model(h2o_model=h2o_model,
                                        input_example=input_example,
                                        artifact_path="folder",
                                        model_type="MOJO",
                                        extra_prediction_args=["--predictCalibrated"])


Model Scoring
-------------

After a model obtained from the model registry, the model doesn't require h2o runtime for ability to score. The only thing
that model requires is a ``h2o-gemodel.jar`` which was persisted with the model during saving procedure.
The model could be loaded by the function ``load_model(model_uri, dst_path=None)``. It returns an objecting making
predictions on Pandas dataframe and takes the following parameters:

* ``model_uri`` - An unique identifier of the model within MLFlow registry.

* ``dst_path`` - (Optional) A local filesystem path for downloading the persisted form of the model. 

The object for scoring could be obtained also via the `pyfunc` flavor as follows:

.. code-block:: Python

    import mlflow
    mlflow.set_tracking_uri("http://127.0.0.1:8080")

    logged_model = 'runs:/9a42265cf0ef484c905b02afb8fe6246/iris'
    loaded_model = mlflow.pyfunc.load_model(logged_model)

    import pandas as pd
    data = pd.read_csv("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
    loaded_model.predict(data)
