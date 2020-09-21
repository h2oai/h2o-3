.. _isoforestextended:

Extended Isolation Forest
-------------------------

Introduction
~~~~~~~~~~~~

The Extended Isolation Forest algorithm generalizes its predecessor algorithm, `Isolation Forest <if.html>`__. The original `Isolation Forest <if.html>`__ algorithm brings a
brand new form of detection, although the algorithm suffers
from bias due to tree branching. Extension of the algorithm
mitigates the bias by adjusting the branching,
and the original algorithm becomes just a special case.

The cause of the bias is that branching is defined by the similarity
to BST. At each branching point the
feature and the value are chosen; this introduces the
bias since the branching point is parallel to one of the axes.
The general case needs to define a random slope for each branching point.
Instead of selecting the feature and value, it selects a random slope :math:`n` for
the branching cut and a random intercept :math:`p`. The slope can
be generated from :math:`\mathcal{N(0,1)}` Gaussian distribution, and the
intercept is generated from the uniform distribution with bounds coming
from the sub-sample of data to be split. The branching criteria for the data
splitting for a given point :math:`x` is as follows:

.. math::
    (x - p) * n < 0

Tutorials and Blogs
~~~~~~~~~~~~~~~~~~~

The following tutorials are available that describe how to use Extended Isolation Forest: 

- `Master's thesis: Anomaly detection using Extended Isolation Forest <https://dspace.cvut.cz/bitstream/handle/10467/87988/F8-DP-2020-Valenta-Adam-thesis.pdf?sequence=-1&isAllowed=y>`__: The thesis deals with anomaly detection algorithms with a focus on the Extended Isolation Forest algorithm and includes the implementation to the H2O-3 open-source Machine Learning platform.
- `Extended Isolation Forest jupyter notebook created by the authors of the algorithm <https://github.com/sahandha/eif/blob/master/Notebooks/EIF.ipynb>`__: Describes how Extended Isolation Forest behaves compared to Isolation Forest.


Defining an Extended Isolation Forest Model
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

-  `model_id <algo-params/model_id.html>`__: (Optional) Specify a custom name for the model to use as a reference. By default, H2O automatically generates a destination key.

-  `training_frame <algo-params/training_frame.html>`__: (Required) Specify the dataset used to build the model. **NOTE**: In Flow, if you click the **Build a model** button from the ``Parse`` cell, the training frame is entered automatically.

-  `ignored_columns <algo-params/ignored_columns.html>`__: (Optional, Python and Flow only) Specify the column or columns to be excluded from the model. In Flow, click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons.

-  `ignore_const_cols <algo-params/ignore_const_cols.html>`__: Specify whether to ignore constant training columns, since no information can be gained from them. This option defaults to true (enabled).

-  `ntrees <algo-params/ntrees.html>`__: (Required) Specify the number of trees (defaults to 50).

-  `max_runtime_secs <algo-params/max_runtime_secs.html>`__: Maximum allowed runtime in seconds for model training. This option is set to 0 (disabled) by default.

-  `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations. This option defaults to -1 (time-based random number).

-  `build_tree_one_node <algo-params/build_tree_one_node.html>`__: Specify whether to run on a single node. This is suitable for small datasets as there is no network overhead but fewer CPUs are used. This value is disabled by default.

-  `sample_size <algo-params/sample_size.html>`__: The number of randomly sampled observations used to train each Extended Isolation Forest tree. This value defaults to 256.

- `categorical_encoding <algo-params/categorical_encoding.html>`__: Specify one of the following encoding schemes for handling categorical features:

  - ``auto`` or ``AUTO``: Allow the algorithm to decide (default). In Isolation Forest, the algorithm will automatically perform ``enum`` encoding.
  - ``enum`` or ``Enum``: 1 column per categorical feature
  - ``enum_limited`` or ``EnumLimited``: Automatically reduce categorical levels to the most prevalent ones during training and only keep the **T** (10) most frequent levels.
  - ``one_hot_explicit`` or ``OneHotExplicit``: N+1 new columns for categorical features with N levels
  - ``binary`` or ``Binary``: No more than 32 columns per categorical feature
  - ``eigen`` or ``Eigen``: *k* columns per categorical feature, keeping projections of one-hot-encoded matrix onto *k*-dim eigen space only
  - ``label_encoder`` or ``LabelEncoder``:  Convert every enum into the integer of its index (for example, level 0 -> 0, level 1 -> 1, etc.)
  - ``sort_by_response`` or ``SortByResponse``: Reorders the levels by the mean response (for example, the level with lowest response -> 0, the level with second-lowest response -> 1, etc.). This is useful in GBM/DRF, for example, when you have more levels than ``nbins_cats``, and where the top level splits now have a chance at separating the data with a split. Note that this requires a specified response column.

- `extension_level <algo-params/extension_level.rst>`__: The number in range :math:`[0, P-1]`; where :math:`P` is the number of features. The minimum value of the hyperparameter is 0 (default), which corresponds to Isolation Forest behavior. The maximum is :math:`P-1` and stands for a full extension. As the ``extension_level`` is increased, the bias of standard Isolation Forest is reduced.


Examples
~~~~~~~~

Below is a simple example showing how to build an Extended Isolation Forest model.

.. tabs::
   .. code-tab:: r R

        library(h2o)
        h2o.init()

        # Import the prostate dataset
        prostate <- h2o.importFile(path = "https://raw.github.com/h2oai/h2o/master/smalldata/logreg/prostate.csv")

        # Build an Extended Isolation forest model
        model <- h2o.extendedIsolationForest(training_frame = prostate,
                                             model_id = "eif.hex",
                                             ntrees = 100,
                                             extension_level = 8)

        # Calculate score
        score <- h2o.predict(model, prostate)
        anomaly_score <- score$anomaly_score

   .. code-tab:: python

        import h2o
        from h2o.estimators import H2OExtendedIsolationForestEstimator
        
        # Import the prostate dataset
        h2o_df = h2o.importFile("https://raw.github.com/h2oai/h2o/master/smalldata/logreg/prostate.csv")
        
        # Define an Extended Isolation forest model
        eif = H2OExtendedIsolationForestEstimator(model_id = "eif.hex",
                                                  ntrees = 100,
                                                  extension_level = 8)

        # Train Extended Isolation Forest
        eif.train(training_frame = hf)

        # Calculate score
        eif_result = eif.predict(h2o_df)
        eif_result["anomaly_score"]


References
~~~~~~~~~~

`S. Hariri, M. Carrasco Kind and R. J. Brunner, "Extended Isolation Forest," in IEEE Transactions on Knowledge and Data Engineering, doi: 10.1109/TKDE.2019.2947676. <http://dx.doi.org/10.1109/TKDE.2019.2947676>`__
