Model Explainability
====================

H2O offers a convenient interface to many explainabilty methods in H2O.  The main functions, `explain()` and `explain_row()` work for individual models, as well a list of models or an `H2O AutoML object <automl.html>`__.  The `explain()` function generates a list of "explanations" -- individual units of explaination such as a Partial Dependence Plot or a Variable Importance plot.  All the explanations are visual objects that can also be individually created by utility functions.  

The visualization engine used in the R interface is the [ggplot2](https://ggplot2.tidyverse.org/) package and in Python, we use [matplotlib](https://matplotlib.org/).



Explainability Interface
------------------------

The H2O explainability interface is designed to be automatic -- all of the explainability feedback is generated with a single function, ``h2o.explain()``, which takes an object (one of the following: an H2O model, a list of models, an H2OAutoML object, an H2OAutoML Leaderboard slice), and a test frame.


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
        exm

        # Explain an AutoML object
        exa = aml.explain(test)
        exa




Parameters
~~~~~~~~~~

TO DO: This was created looking at the R interface, may need some updates for Python.  

- **object**: (R only) One of the following: an H2O model, a list of models, an H2OAutoML object, or an H2OAutoML leaderboard slice.

- **test_frame**: An H2OFrame of test data.

- **columns_of_interest**: A vector of column names. If specified, create plots only with these columns (where applicable).

- **include_explanations**: If specified, do only the specified explanations. Mutually exclusive with exclude_explanations.

- **exclude_explanations**: Exclude specified explanations.

The available options (explainations) for ``include_explanations`` and ``exclude_explainations`` are:
    
    - ``"leaderboard"``
    - ``"confusion_matrix"``
    - ``"residual_analysis"``
    - ``"variable_importance"``
    - ``"variable_importance_heatmap"``
    - ``"model_correlation_heatmap"``
    - ``"shap_summary"``
    - ``"pdp"``
    - ``"ice"``

- **columns**: If ``columns`` is missing, create plots only with the top n columns (where applicable).  Defaults to 5.

- **plot_overrides**: Overrides for individual explanations, e.g. ``list(shap_summary_plot = list(top_n_features = 50))`` in R. 

Notes
~~~~~

Our roadmap for improving the explainability interface is [here](https://0xdata.atlassian.net/jira/software/c/projects/PUBDEV/issues/PUBDEV-7806?filter=allissues).

We are looking for feedback from users.  If you have any feature requests, please file a 



Code Examples
-------------

See below for code examples in R and Python.  We are working on some longer explainability tutorials that we will link here soon.


Explain models
~~~~~~~~~~~~~~

Here’s an example showing basic usage of the ``h2o.explain()`` function in *R* and the ``explain()`` method in *Python*.  Keep in mind that this code should be run in an environment that can support plots.  We recommend Jypyter notebooks in Python and RStudio IDE in R (either in the console or in R Markdown file or notebook).


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

        # Run AutoML for 20 base models
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
        from IPython.core.display import display

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
        
        # Run AutoML for 20 base models
        aml = H2OAutoML(max_models=10, seed=1)
        aml.train(x=x, y=y, training_frame=train)

        # Explain leader model & compare with all AutoML models 
        exa = aml.explain(test)
        display(exa)

        # Explain a single H2O model (e.g. leader model from AutoML)
        exm = aml.leader.explain(test)
        display(exm)




The code above is the quickest way to get started. 

Explain a single row prediction
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The ``h2o.explain_row()`` function provides model explanations for a single row of test data. 

Using the previous code example, you can evaluate row-level behavior by specifying the `row_index`:

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


Output: Explainations
---------------------

Explaination Plotting Functions 
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

TO DO: Let's put examples of each function and the plot, in the order in which they appear in the ``h2o.explain()`` output.  Let's also show how to customize the plots.

.. tabs::
   .. code-tab:: r R

        # Residual analysis plot for an AutoML object
        ra_plot <- h2o.residual_analysis(aml@leader, test)
        ra_plot

   .. code-tab:: python

        # Residual analysis plot for an AutoML object
        ra_plot = aml.leader.residual_analysis(test)
        ra_plot





Resources
---------

- `AutoML Tutorial <https://github.com/h2oai/h2o-tutorials/tree/master/h2o-world-2017/automl>`__ (R and Python notebooks)
- Intro to AutoML + Hands-on Lab `(1 hour video) <https://www.youtube.com/watch?v=42Oo8TOl85I>`__ `(slides) <https://www.slideshare.net/0xdata/intro-to-automl-handson-lab-erin-ledell-machine-learning-scientist-h2oai>`__
- Scalable Automatic Machine Learning in H2O `(1 hour video) <https://www.youtube.com/watch?v=j6rqrEYQNdo>`__ `(slides) <https://www.slideshare.net/0xdata/scalable-automatic-machine-learning-in-h2o-89130971>`__
- `AutoML Roadmap <https://0xdata.atlassian.net/issues/?filter=21603>`__

