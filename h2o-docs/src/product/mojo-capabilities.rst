.. _mojo-capabilities:

MOJO Capabilities
-----------------

This section describes the basics of working with the MOJO model in H2O-3.

About H2O Generated MOJO Models
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

For information about quick starting and building MOJOs, look `here <productionizing.html>`__. For information on how to use the ``h2o-genmodel``, look `here <http://docs.h2o.ai/h2o/latest-stable/h2o-genmodel/javadoc/index.html>`__. 

The ``h2o-genmodel`` API contains:

- **hex.genmodel.algos**

All algorithms that support the MOJO model can be found here. These models can be loaded and directly used to score or perform other model-specific actions. For further documentation of the methods, refer to the javadoc of the `GenModel class <http://docs.h2o.ai/h2o/latest-stable/h2o-genmodel/javadoc/hex/genmodel/GenModel.html>`__.

- **hex.genmodel.easy.EasyPredictModelWrapper**

`This wrapper <http://docs.h2o.ai/h2o/latest-stable/h2o-genmodel/javadoc/hex/genmodel/easy/EasyPredictModelWrapper.html>`__ gives MOJO models an easy, readable interface for scoring and other model-specific actions.

- **hex.genmodel.easy.prediction**

This gives the predictions that can be called from the ``EasyPredictModelWrapper.predict()`` command. For more information, refer to the `javadoc of the prediction classes <http://docs.h2o.ai/h2o/latest-stable/h2o-genmodel/javadoc/hex/genmodel/easy/prediction/AbstractPrediction.html>`__.

- **hex.genmodel.easy.CategoricalEncoder**

Classes from `this interface <http://docs.h2o.ai/h2o/latest-stable/h2o-genmodel/javadoc/hex/genmodel/easy/CategoricalEncoder.html>`__ can be used to preprocess raw data values to the proper categorical values expected by the model.

- **hex.genmodel.attributes.metrics**

`This package <http://docs.h2o.ai/h2o/latest-stable/h2o-genmodel/javadoc/hex/genmodel/attributes/metrics/package-summary.html>`__ provides the different metrics for model-specific needs.

- **hex.genmodel.tools**

These java command line `tools <http://docs.h2o.ai/h2o/latest-stable/h2o-genmodel/javadoc/hex/genmodel/tools/package-summary.html>`__ can be used for various types of applications: printing decision trees, reading a CSV file and making predictions, reading a CSV file and munging it, etc.

Predicting Values with MOJO
~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. tabs::
   .. code-tab:: java Raw MOJO

        MojoModel mojoModel = MojoModel.load("isolation_forest.zip");
        double [] predictions = new double[]{Double.NaN, Double.NaN};
        mojoModel.score0(new double[]{100, 100}, predictions);
        System.out.println(Arrays.toString(predictions));

   .. code-tab:: java Mojo Wrapper

        IsolationForestMojoModel mojoModel = (IsolationForestMojoModel) MojoModel.load("isolation_forest.zip");

        EasyPredictModelWrapper.Config config = new EasyPredictModelWrapper.Config()
                .setModel(mojoModel);
        EasyPredictModelWrapper model = new EasyPredictModelWrapper(config);

        RowData row = new RowData();
        row.put("x", "100");
        row.put("y", "100");

        AnomalyDetectionPrediction p = (AnomalyDetectionPrediction) model.predict(row);
        System.out.println("[" + p.normalizedScore + ", " + p.score + "]");

Metadata Contained in the MOJO Model
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

All h2o-3 models contains some metadata. To access this, type the following respective commands:

.. tabs::
    .. code-tab:: r R

        Type the <model variable name> within the console.

    .. code-tab:: python

        # All metadata
        model._model_json

        # Useful metadata
        model._model_json['output']

        # Description of available keys
        model._model_json['output']['help']

    .. code-tab:: Java Java MOJO Model

        // Must call with metadata flag set to True
        MojoModel model = MojoModel.load("GBM_model.zip", true);
        ModelAttributes attributes = model._modelAttributes;

All MOJO models contain the `following attributes <http://docs.h2o.ai/h2o/latest-stable/h2o-genmodel/javadoc/hex/genmodel/attributes/ModelAttributes.html>`__:

    .. code:: java

        // Correspond to model._model_json['output']['model_summary'] (Number of trees, Size of model,..)
        attributes.getModelSummary();

        // Correspond to model._model_json['output']['scoring_history']
        attributes.getScoringHistory();

        // Correspond to model._model_json['output']['training_metrics']
        // but only some values are available (MSE, RMSE,...)
        // and for example confusion Matrix and other is omitted.
        attributes.getTrainingMetrics();

        // Correspond to model._model_json['output']['validation_metrics']
        // but only some values are available (MSE, RMSE,...)
        // and for example confusion Matrix and other is omitted.
        attributes.getValidationMetrics();

        // Correspond to model._model_json['output']['cross_validation_metrics']
        // but only some values are available (MSE, RMSE,...)
        // and for example confusion Matrix and other is omitted.
        attributes.getCrossValidationMetrics();

        // Correspond to model._model_json['output']['cross_validation_metrics_summary']
        attributes.getCrossValidationMetricsSummary();

        // Model parameters setting when the model was built
        attributes.getModelParameters();

Accessing Model Trees
'''''''''''''''''''''

The following example shows a way to access the number of trees from the model:

.. tabs::
   .. code-tab:: r R

      # Build and train your model
      model <- h2o.randomForest(...)

      # Print the number of trees
      print(paste("Number of Trees: ", model@allparameters$ntrees))

   .. code-tab:: python

      # Build and train your model
      model = H2ORandomForestEstimator(...)
      model.train(...)

      # Print the number of trees
      print("Number of Trees: {}".format(model._model_json["output"]["model_summary"]["number_of_trees"]))

   .. code-tab:: Java Java MOJO Model

      // Load the MOJO model
      MojoModel model = MojoModel.load("rf_model.zip", true);

      // Retrieve the model attributes
      ModelAttributes attributes = model._modelAttributes;
      System.out.print(attributes.getModelSummary().getColHeaders()[1] + ": ");
      System.out.println(attributes.getModelSummary().getCell(1,0));

ModelAttributes Subclasses
''''''''''''''''''''''''''

Subclasses of `ModelAttributes <http://docs.h2o.ai/h2o/latest-stable/h2o-genmodel/javadoc/hex/genmodel/attributes/ModelAttributes.html>`__ are used to handle model-specific attributes (e.g. Variable Importance).

.. tabs::
   .. code-tab:: java Raw MOJO

        // Must call with metadata flag set to True
        MojoModel model = MojoModel.load("GBM_model.zip", true);
        SharedTreeModelAttributes attributes = ((SharedTreeModelAttributes) model._modelAttributes);
        String[] variables = attributes.getVariableImportances()._variables;
        double[] importances = attributes.getVariableImportances()._importances;
        System.out.print(variables[0] + ": ");
        System.out.println(importances[0]);

   .. code-tab:: java Mojo Wrapper

        MojoModel modelMojo = MojoModel.load("GBM_model.zip", true);
        EasyPredictModelWrapper.Config config = new EasyPredictModelWrapper.Config().setModel(modelMojo);
        EasyPredictModelWrapper model = new EasyPredictModelWrapper(config);
        KeyValue[] importances = model.varimp();
        System.out.print(importances[0].getKey() + ": ");
        System.out.println(importances[0].getValue());









