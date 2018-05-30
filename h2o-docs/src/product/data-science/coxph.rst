Cox Proportional Hazards
------------------------

Introduction
~~~~~~~~~~~~

Cox Proportional Hazards (CoxPH) is a survival analysis method that analyzes variables to assess risk factors. Specifically, CoxPH relates risk factors (or exposures), considered simultaneously, to survival time. In a CoxPH regression model, the measure of effect is the hazard rate, which is the risk of failure (i.e., the risk or probability of suffering the event of interest), given that the participant has survived up to a specific time. A probability must lie in the range 0 to 1. However, the hazard represents the expected number of events per one unit of time. As a result, the hazard in a group can exceed 1. 

Defining a CoxPH Model
~~~~~~~~~~~~~~~~~~~~~~

-  `model_id <algo-params/model_id.html>`__: (Optional) Specify a custom name for the model to use as a reference. By default, H2O automatically generates a destination key.

-  `training_frame <algo-params/training_frame.html>`__: (Required) Specify the dataset used to build the model. **NOTE**: In Flow, if you click the **Build a model** button from the ``Parse`` cell, the training frame is entered automatically.

-  **start_column**: The first time column. 

-  **stop_column**: The last time column.

-  `y <algo-params/y.html>`__: (Required) Specify the column to use as the dependent variable. The data can be numeric or categorical.

-  `ignored_columns <algo-params/ignored_columns.html>`__: (Optional, Python and Flow only) Specify the column or columns to be excluded from the model. In Flow, click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons.

-  `weights_column <algo-params/weights_column.html>`__: Specify a column to use for the observation weights, which are used for bias correction. The specified  ``weights_column`` must be included in the specified ``training_frame``. 
   
    *Python only*: To use a weights column when passing an H2OFrame to ``x`` instead of a list of column names, the specified ``training_frame`` must contain the specified ``weights_column``. 
   
    **Note**: Weights are per-row observation weights and do not increase the size of the data frame. This is typically the number of times a row is repeated, but non-integer values are supported as well. During training, rows with higher weights matter more, due to the larger loss function pre-factor.

-  `offset_column <algo-params/offset_column.html>`__: Specify a column to use as the offset.
   
	 **Note**: Offsets are per-row "bias values" that are used during model training. For Gaussian distributions, they can be seen as simple corrections to the response (y) column. Instead of learning to predict the response (y-row), the model learns to predict the (row) offset of the response column. For other distributions, the offset corrections are applied in the linearized space before applying the inverse link function to get the actual response values. For more information, refer to the following `link <http://www.idg.pl/mirrors/CRAN/web/packages/gbm/vignettes/gbm.pdf>`__. 

-  **stratify_by**: A list of columns to use for stratification.

-  **ties**: Specify the method for Handling Ties. Valid values are **efron** (default) and **breslow**. 

-  **init**: The coefficient starting value. This option defaults to 0.

-  **lre_min**: Specify the minimum log-relative error. This option defaults to 9.

-  **inter_max**: Specify the maximum number of iterations. This option defaults to 20.

-  `interactions <algo-params/interactions.html>`__: Specify a list of predictor column indices to interact. All pairwise combinations will be computed for this list. 

-  `interaction_pairs <algo-params/interaction_pairs.html>`__: (Internal only.) When defining interactions, use this option to specify a list of pairwise column interactions (interactions between two variables). Note that this is different than ``interactions``, which will compute all pairwise combinations of specified columns. This option is disabled by default.


Interpreting a CoxPH Model
~~~~~~~~~~~~~~~~~~~~~~~~~~

The output for CoxPH includes the following:

-  Model parameters (hidden)
-  Output (including model category, r-squared, max r-squared, lre, iterations, total events, formula, ties)
-  Column names
-  Domains
-  Scoring history in tabular format
-  Coefficients
-  Variable coefficients


CoxPH Algorithm
~~~~~~~~~~~~~~~



References
~~~~~~~~~~



