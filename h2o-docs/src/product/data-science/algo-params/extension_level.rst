``extension_level``
-------------------

- Available in: Extended Isolation Forest
- Hyperparameter: yes

Description
~~~~~~~~~~~

For Extended Isolation Forest the ``extension_level`` hyperparameter allows you to leverage the generalization of Isolation Forest. 
The number 0 corresponds to Isolation Forest's behavior because the split points are not randomized with slope. 
For the dataset with :math:`P` features, the maximum ``extension_level`` is :math:`P-1` and means full-extension. 
As the ``extension_level`` is increased, the bias of the standard Isolation Forest is reduced. A lower extension is suitable for
a domain where the range of the minimum and maximum for each feature highly differs (for example, when one feature is 
measured in millimeters, and the second one in meters). The following paragraphs deliver a more detailed explanation.

The branching criteria in Extended Isolation Forest for the data
splitting for a given data point :math:`x` is as follows:

.. math::
    (x - p) * n < 0

where:
 - :math:`x`, :math:`p`, and :math:`n` are vectors with :math:`P` features
 - :math:`p` is random  intercept generated from the uniform distribution with bounds coming from the sub-sample of data to be split.
 - :math:`n` is random slope for the branching cut generated from :math:`\mathcal{N(0,1)}` distribution.

The function of ``extension_level``
is to force random items of :math:`n` to be zero. The ``extension_level`` hyperparameter value is between :math:`0` and :math:`P-1`.
A value of 0 means that all slopes will be parallel with all of the axes, which corresponds to Isolation Forest's behavior.
A higher number of extension level indicates that the split will be parallel with ``extension_level``-number of axes.
The full-extension means ``extension_level`` is equal to :math:`P - 1`. This indicates that the slope of the branching point will
always be randomized. 

For a full insight into the ``extension_level`` hyperparameter, please read the section High Dimensional Data and Extension
Levels from the original `paper <http://dx.doi.org/10.1109/TKDE.2019.2947676>`__.

Example
~~~~~~~

.. tabs::
   .. code-tab:: r R

        library(h2o)
        h2o.init()

        # Import the prostate dataset
        prostate <- h2o.importFile(path = "https://raw.github.com/h2oai/h2o/master/smalldata/logreg/prostate.csv")

        # Simulate Isolation Forest behavior with Extended Isolation Forest algorithm
        model_if <- h2o.extendedIsolationForest(training_frame = prostate,
                                                model_id = "eif_if.hex",
                                                ntrees = 100,
                                                extension_level = 0)

        # Use full-extension
        model_eif <- h2o.extendedIsolationForest(training_frame = prostate,
                                                model_id = "eif.hex",
                                                ntrees = 100,
                                                extension_level = 8)

        # Calculate score
        score_if <- h2o.predict(model_if, prostate)
        anomaly_score_if <- score_if$anomaly_score
        score_eif <- h2o.predict(model_eif, prostate)
        anomaly_score_eif <- score_eif$anomaly_score


   .. code-tab:: python

        import h2o
        from h2o.estimators import H2OExtendedIsolationForestEstimator
        
        # Import the prostate dataset
        h2o_df = h2o.importFile("https://raw.github.com/h2oai/h2o/master/smalldata/logreg/prostate.csv")
        
        # Simulate Isolation Forest behavior with Extended Isolation Forest algorithm
        eif_if = H2OExtendedIsolationForestEstimator(model_id = "eif_if.hex",
                                                     ntrees = 100,
                                                     extension_level = 0)

        # Use full-extension
        eif_full = H2OExtendedIsolationForestEstimator(model_id = "eif_full.hex",
                                                       ntrees = 100,
                                                       extension_level = 8)

        eif_if.train(training_frame = hf)
        eif_full.train(training_frame = hf)

        # Calculate score
        eif_if_result = eif_if.predict(h2o_df)
        eif_full_result = eif_full.predict(h2o_df)
        eif_if_result["anomaly_score"]
        eif_full_result["anomaly_score"]
