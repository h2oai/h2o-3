Model Explainability
====================

H2O Explainability API is a convenient interface to many explainabilty methods and visualizations in H2O.  The main functions, ``h2o.explain()`` and ``h2o.explain_row()`` work for individual models, as well a list of models or an `H2O AutoML object <automl.html>`__.  The ``h2o.explain()`` function generates a list of "explanations" -- individual units of explanation such as a Partial Dependence Plot or a Variable Importance plot.  All the explanations are visual objects that can also be individually created by utility functions.  

The visualization engine used in the R interface is the `ggplot2 <https://ggplot2.tidyverse.org/>`__ package and in Python, we use `matplotlib <https://matplotlib.org/>`__.



Explainability Interface
------------------------

The H2O Explainability Interface is designed to be automatic -- all of the "explanations" are generated with a single function, ``h2o.explain()``.  The input can be any of the following: an H2O model, a list of H2O models, an ``H2OAutoML`` object or an ``H2OAutoML`` Leaderboard slice, and a holdout frame.  If you provide a list of models or an AutoML object, there will be additional plots that do multi-model comparisons.  


.. tabs::
   .. code-tab:: r R

        # Explain a model
        exm <- h2o.explain(model, test)
        exm

        # Explain an AutoML object
        exa <- h2o.explain(aml, test)
        exa


   .. code-tab:: python

        # Explain a model
        exm = model.explain(test)

        # Explain an AutoML object
        exa = aml.explain(test)



Parameters
~~~~~~~~~~

- **object**: (R only) One of the following: an H2O model, a list of models, an H2OAutoML object, or an H2OAutoML leaderboard slice.

- **frame** (Python) / **newdata** (R): An H2OFrame used in Residual Analysis, Shapley contributions.

- **columns**: A vector of column names or an integer. If column names are specified, create plots only with these columns (where applicable).  If an integer, N, is given, then use the top N columns, ranked by variable importance.

- **include_explanations**: If specified, do only the specified explanations. Mutually exclusive with ``exclude_explanations``.

- **exclude_explanations**: Exclude specified explanations.  The available options (explanations) for ``include_explanations`` and ``exclude_explanations`` are:
    
    - ``"leaderboard"``  (AutoML and list of models only)
    - ``"residual_analysis"``  (regression only)
    - ``"confusion_matrix"``   (classification only)
    - ``"variable_importance"``  (not currently available for Stacked Ensembles)
    - ``"variable_importance_heatmap"``
    - ``"model_correlation_heatmap"``
    - ``"shap_summary"``
    - ``"pdp"``
    - ``"ice"``

- **plot_overrides**: Overrides for individual explanations, e.g. ``list(shap_summary_plot = list(top_n_features = 50))``.


Code Examples
-------------

The R and Python code below is the quickest way to get started.  We are working on some longer explainability tutorials that we will link here soon.


Explain Models
~~~~~~~~~~~~~~

Here’s an example showing basic usage of the ``h2o.explain()`` function in *R* and the ``explain()`` method in *Python*.  Keep in mind that this code should be run in an environment that can support plots.  We recommend Jypyter notebooks in Python and RStudio IDE in R (either in the console or in R Markdown file or notebook).  There is also support for the `IRkernel <https://irkernel.github.io/installation/>`__ Jupyter notebook R kernel.


.. tabs::
   .. code-tab:: r R

        library(h2o)
        h2o.init()

        # Import a sample binary outcome train/test set into H2O
        train <- h2o.importFile("https://s3.amazonaws.com/erin-data/higgs/higgs_train_10k.csv")
        test <- h2o.importFile("https://s3.amazonaws.com/erin-data/higgs/higgs_test_5k.csv")

        # Identify predictors and response
        y <- "response"
        x <- setdiff(names(train), y)

        # For binary classification, response should be a factor
        train[, y] <- as.factor(train[, y])
        test[, y] <- as.factor(test[, y])

        # Run AutoML
        aml <- h2o.automl(x = x, y = y, 
                          training_frame = train,
                          max_models = 10,
                          seed = 1)

        # Explain leader model & compare with all AutoML models                  
        exa <- h2o.explain(aml, test)
        exa

        # Explain a single H2O model (e.g. leader model from AutoML)
        exm <- h2o.explain(aml@leader, test)
        exm



   .. code-tab:: python

        import h2o
        from h2o.automl import H2OAutoML
        from h2o.explain import explain, explain_row

        h2o.init()

        # Import a sample binary outcome train/test set into H2O
        train = h2o.import_file("https://s3.amazonaws.com/erin-data/higgs/higgs_train_10k.csv")
        test = h2o.import_file("https://s3.amazonaws.com/erin-data/higgs/higgs_test_5k.csv")

        # Identify predictors and response
        x = train.columns
        y = "response"
        x.remove(y)

        # For binary classification, response should be a factor
        train[y] = train[y].asfactor()
        test[y] = test[y].asfactor()
        
        # Run AutoML
        aml = H2OAutoML(max_models=10, seed=1)
        aml.train(x=x, y=y, training_frame=train)

        # Explain leader model & compare with all AutoML models 
        exa = aml.explain(test)

        # Explain a single H2O model (e.g. leader model from AutoML)
        exm = aml.leader.explain(test)


Notes:
''''''

In R, the ``H2OExplanation`` object will not be printed if you save it to an object.  If you save the output to an object, you can access the plots and associated data for each explanation.  Then you can ``print(exa)`` to print the explaiation.

In Python, the ``H2OExplanation`` will always be printed, even if you save it to an object.  Once you save it to an object, however, if you want to print it again, you ``from IPython.core.display import display`` and ``display(exa)``.



Explain a single row prediction
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The ``h2o.explain_row()`` function provides model explanations for a single row of test data. Using the previous code example, you can evaluate row-level behavior by specifying the ``row_index``:

.. tabs::
   .. code-tab:: r R

        # Explain row 1 with all AutoML models
        h2o.explain_row(aml, test, row_index = 1)

        # Explain row 1 with a single model
        h2o.explain_row(aml@leader, row_index = 1)

   .. code-tab:: python

        # Explain row 1 with all AutoML models
        aml.explain_row(test, row_index=1)

        # Explain row 1 with a single model
        aml.leader.explain_row(test, row_index=1)


Output: Explanations
--------------------

TO DO: Overview of the output object.  Add some plots


When `explain()` is provided a list of models, we get the following explanations:

- Leaderboard
- Confusion Matrix for Leader Model (classification only)
- Residual Analysis for Leader Model (regression only)
- Variable Importance of Top Base Model 
- Variable Importance Heatmap (compare all models)
- Model Correlation Heatmap (compare all models)
- SHAP Summary of Top Tree-based Model (TreeSHAP)
- PD Plots (compare all models)

When `explain()` is provided a single model, we get the following explanations:



- Individual Conditional Expectation (ICE) Plots







Explanation Plotting Functions 
------------------------------

TO DO: Let's put examples of each function and the plot, in the order in which they appear in the ``h2o.explain()`` output.  Let's also show how to customize the plots.



There are a number of individual plotting functions that are used inside the ``explain()`` function.  Some of these functions 

Takes a list of models (including an AutoML object or leaderboard slice) as input:
::

    varimp_heatmap          
    model_correlation_heatmap        
    pdp_multi_plot       


Takes a single model as input:
::
    residual_analysis_plot
    shap_explain_row_plot
    shap_summary_plot
    pd_plot
    ice_plot

R has the same functions, but with the ``h2o.*`` prefix.



Residual Analysis
~~~~~~~~~~~~~~~~~

The Residual Analysis plot function graphs "Fitted vs Residuals". Ideally, residuals should be randomly distributed. Patterns in this plot can indicate potential problems with the model selection, e.g., using simpler model than necessary, not accounting for heteroscedasticity, autocorrelation, etc.

.. tabs::
   .. code-tab:: r R

        # Residual analysis plot for the AutoML leader model
        ra_plot <- h2o.residual_analysis_plot(aml@leader, test)
        ra_plot

   .. code-tab:: python

        # Residual analysis plot for the AutoML leader model
        ra_plot = aml.leader.residual_analysis_plot(test)









Notes
~~~~~

The H2O Explainability interface is newly released and currently experimental.  From the initial release, we may evolve (and potentially break) the API, as we collect collect feedback from users and work to improve and expand the functionality.  We welcome feedback!  If you find bugs, or if you have any feature requests or suggested improvements, please create a ticket on the `H2O JIRA issue tracker <https://0xdata.atlassian.net/projects/PUBDEV>`__.

Our roadmap for improving the the interface is `here <https://0xdata.atlassian.net/jira/software/c/projects/PUBDEV/issues/PUBDEV-7806?filter=allissues>`__.



References
----------

- Insert Residual Analysis reference
- Insert SHAP reference
- Insert PDP reference
- Insert ICE reference

