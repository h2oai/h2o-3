H2O-3 Blogs
=========================

This page houses the most recent major release blog and focused content blogs for H2O.

Major Release Blogs
-------------------

H2O Release 3.44
~~~~~~~~~~~~~~~~

.. image:: /images/blog/rel-3-44.png

We are excited to announce the release of H2O-3 3.44.0.1! We have added and improved many items. A few of our highlights are the implementation of AdaBoost, Shapley values support, Python 3.10 and 3.11 support, and added custom metric support for Deep Learning, Uplift Distributed Random Forest (DRF), Stacked Ensemble, and AutoML. Please read on for more details.

AdaBoost (Adam Valenta)
'''''''''''''''''''''''

We are proud to introduce `AdaBoost <https://docs.h2o.ai/h2o/latest-stable/h2o-docs/data-science/adaboost.html>`__, an algorithm known for its effectiveness in improving model performance. AdaBoost is particularly notable for its approach in constructing an ensemble of weak learners (typically decision trees) and sequentially refining them to enhance predictive accuracy. It achieves this by assigning higher weights to misclassified data points in each iteration. This emphasizes the correction of errors and ultimately leads to a more precise and robust predictive model. This adaptability and refinement makes AdaBoost a valuable tool in various domains, allowing it to aid in better predictions and informed decision-making.

.. tabs::
    .. code-tab:: r R

        library(h2o)
        h2o.init()

        # Import the prostate dataset into H2O:
        prostate <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")
        predictors <- c("AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON")
        response <- "CAPSULE"
        prostate[response] <- as.factor(prostate[response])

        # Build and train the model:
        adaboost_model <- h2o.adaBoost(nlearners=50,
                                       learn_rate = 0.5,
                                       weak_learner = "DRF",
                                       x = predictors,
                                       y = response,
                                       training_frame = prostate)

        # Generate predictions:
        h2o.predict(adaboost_model, prostate)

    .. code-tab:: python

        import h2o
        from h2o.estimators import H2OAdaBoostEstimator
        h2o.init()

        # Import the prostate dataset into H2O:
        prostate = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/prostate/prostate.csv")
        prostate["CAPSULE"] = prostate["CAPSULE"].asfactor()

        # Build and train the model:
        adaboost_model = H2OAdaBoostEstimator(nlearners=50,
                                              learn_rate = 0.8,
                                              weak_learner = "DRF",
                                              seed=0xBEEF)
        adaboost_model.train(y = "CAPSULE", training_frame = prostate)

        # Generate predictions:
        pred = adaboost_model.predict(prostate)
        pred

Shapley support for ensemble models (Tomáš Frýda)
'''''''''''''''''''''''''''''''''''''''''''''''''

Stacked Ensembles now supports SHapley Additive exPlanations (SHAP) estimation using the Generalized-DeepSHAP method. This is only supported for base models and metalearner models that support SHAP estimation with a background frame. Support for SHAP with a background frame was added for:

Deep Learning (`DeepSHAP <https://arxiv.org/abs/1705.07874>`__),
DRF/ Extremely Randomized Trees (XRT) (`Independent TreeSHAP <https://arxiv.org/abs/1905.04610>`__),
Gradient Boosting Machine (GBM) (`Independent TreeSHAP <https://arxiv.org/abs/1905.04610>`__),
Generalized Linear Models (GLM), and
XGBoost (`Independent TreeSHAP <https://arxiv.org/abs/1905.04610>`__).

There are two variants of the newly implemented SHAP: baseline SHAP and marginal SHAP (default when calling predict_contributions with a background dataset). Baseline SHAP returns contributions for each point from the background dataset. Marginal SHAP returns the average contribution across the whole background dataset. The calculation of both of these SHAP methods can have big memory requirements because the result of the baseline has number of rows equal to :math:`nrows(frame) \times nrow(background\_frame)`. For marginal SHAP contributions in Stacked Ensembles, we optimized the calculation by going through the whole process (baseline SHAP —> average) several times, so the memory usage is small than :math:`(number of base models + 1) \times nrow(frame) \times nrow(background\_frame)` (unless the frame is very small).

The new SHAP implementation requires you to choose your references, or background dataset. This can be used for getting new insights as seen in Figure 3 of `Explaining a series of models by propagating Shapley values <https://www.nature.com/articles/s41467-022-31384-3>`__. It can also be used to comply with some regulations that require explanations with regards to some reference.

For example, according to the `Consumer Financial Protection Bureau <https://www.consumerfinance.gov/rules-policy/regulations/1002/interp-9/#9-b-2-Interp-5>`__, for credit denials in the US, the regulatory commentary suggests to “identify the factors for which the applicant’s score fell furthest below the average score for each of those factors achieved by applicants whose total score was at or slightly above the minimum passing score.” This process can be done by using the applicants just above the cutoff to receive the credit product as the background dataset according to Hall et al. in their book Machine Learning for High-Risk Applications.

.. tabs::
    .. code-tab:: r R

        # Import the prostate dataset:
        pros <- h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")

        # Set the factors:
        pros[, 2] <- as.factor(pros[, 2])
        pros[, 4] <- as.factor(pros[, 4])
        pros[, 5] <- as.factor(pros[, 5])
        pros[, 6] <- as.factor(pros[, 6])
        pros[, 9] <- as.factor(pros[, 9])

        # Split the data into training and validation sets:
        pros_splits <- h2o.splitFrame(data = pros, ratio = 0.8, seed = 1234)
        train <- pros_splits[[1]]
        test <- pros_splits[[2]]

        # Build a GBM model:
        model <- h2o.gbm(y = "CAPSULE",
                         x = 3:9,
                         training_frame = train,
                         distribution = "bernoulli",
                         ntrees = 100,
                         max_depth = 4,
                         learn_rate = 0.1,
                         seed = 1234)

        # Plot the SHAP summary plot:
        h2o.shap_summary_plot(model,
                              prostate_test,
                              background_frame=prostate_train[prostate_train$AGE > 70, ])

    .. code-tab:: python

        from h2o.estimators.gbm import H2OGradientBoostingEstimator

        # Import the prostate dataset:
        pros = h2o.import_file("https://raw.github.com/h2oai/h2o/master/smalldata/logreg/prostate.csv")

        # Set the factors:
        pros["CAPSULE"] = pros["CAPSULE"].asfactor()

        # Split the data into training and validation sets:
        train, test = pros.split_frame(ratios = [.75], seed = 1234)

        # Build a GBM model:
        model = H2OGradientBoostingEstimator(distribution = "bernoulli",
                                             ntrees = 100,
                                             max_depth = 4,
                                             learn_rate = 0.1,
                                             seed = 1234)
        model.train(y = "CAPSULE",
                    x = ["AGE", "RACE", "PSA", "GLEASON"],
                    training_frame = train)

        # Plot the SHAP summary plot:
        model.shap_summary_plot(test, background_frame=train[train["AGE"] > 70, :])

Fixed H2O-3 Vulnerabilities (Marek Novotný)
'''''''''''''''''''''''''''''''''''''''''''

This release contains fixes for more than 30 `CVE <https://www.cve.org/>`__ vulnerabilities in the standalone h2o.jar, Python package, R package, and the `docker image <https://hub.docker.com/r/h2oai/h2o-open-source-k8s>`__ for Kubernetes. These deployment artifacts don’t contain any critical or high CVE vulnerabilities at the time of writing this article.

Categorical feature support for Single Decision Tree (Yuliia Syzon)
'''''''''''''''''''''''''''''''''''''''''''''''''''''''''''''''''''

We added support for categorical columns into the Single Decision Tree. You can now build a binary Single Decision Tree classifier with both numerical and categorical columns!

Categorical values are treated as non-sortable values. When splitting the dataset into nodes, a categorical binning approach is utilized. It's important for you to note that the number of categories shouldn't be excessively large. Ideally, up to 10 categories is optimal for this implementation.

Uplift DRF enhancements (Veronika Maurerova)
''''''''''''''''''''''''''''''''''''''''''''

There have been several enhancements to the Uplift DRF algorithm.

New treatment effect metrics
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

`Treatment effect metrics <https://docs.h2o.ai/h2o/latest-stable/h2o-docs/data-science/upliftdrf.html#treatment-effect-metrics-ate-att-atc>`__ show how the uplift predictions look across the whole dataset (population). Scored data are used to calculate these metrics (``uplift_predict`` column = individual treatment effect).

- Average Treatment Effect (ATE): the average expected uplift prediction (treatment effect) over all records in the dataset.
- Average Treatment Effect on the Treated (ATT): the average expected uplift prediction (treatment effect) of all records in the dataset belonging to the treatment group.
- Average Treatment Effect on the Control (ATC): the average expected uplift prediction (treatment effect) of all records in the dataset belonging to the control group.

Custom metric functionality enabled
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

You can now specify your custom metric if you need a special metric calculation.

.. tabs::
    .. code-tab:: python

        import h2o
        from h2o.estimators import H2OUpliftRandomForestEstimator
        h2o.init()

        # Import the cars dataset into H2O:
        data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv")

        # Set the predictors, response, and treatment column:
        predictors = ["f1", "f2", "f3", "f4", "f5", "f6","f7", "f8"]
        # set the response as a factor
        response = "conversion"
        data[response] = data[response].asfactor()
        # set the treatment as a factor
        treatment_column = "treatment"
        data[treatment_column] = data[treatment_column].asfactor()

        # Split the dataset into a train and valid set:
        train, valid = data.split_frame(ratios=[.8], seed=1234)

        # Define custom metric function
        # ``pred`` is prediction array of length 3, where:
        #   - pred[0]  = ``uplift_predict``: result uplift prediction score, which is calculated as ``p_y1_ct1 - p_y1_ct0``
        #   - pred[1] = ``p_y1_ct1``: probability the response is 1 if the row is from the treatment group
        #   - pred[2] = ``p_y1_ct0``: probability the response is 1 if the row is from the control group
        # ``act`` is array with original data where
        #   - act[0] = target variable
        #   - act[1] = if the record belongs to the treatment or control group
        # ``w`` (weight) and ``o`` (offset) are nor supported in Uplift DRF yet

        class CustomAteFunc:
            def map(self, pred, act, w, o, model):
                return [pred[0], 1]

            def reduce(self, l, r):
                return [l[0] + r[0], l[1] + r[1]]

            def metric(self, l):
                return l[0] / l[1]

        custom_metric = h2o.upload_custom_metric(CustomAteFunc, func_name="ate", func_file="mm_ate.py")

        # Build and train the model:
        uplift_model = H2OUpliftRandomForestEstimator(ntrees=10,
                                                      max_depth=5,
                                                      treatment_column=treatment_column,
                                                      uplift_metric="KL",
                                                      min_rows=10,
                                                      seed=1234,
                                                      auuc_type="qini"
                                                      custom_metric_func=custom_metric)
        uplift_model.train(x=predictors,
                           y=response,
                           training_frame=train,
                           validation_frame=valid)

        # Eval performance:
        perf = uplift_model.model_performance()
        custom_att = perf._metric_json["training_custom"]
        print(custom_att)
        att = perf.att(train=True)
        print(att)

MOJO support introduced
^^^^^^^^^^^^^^^^^^^^^^^

You can import the Uplift DRF model as a MOJO and deploy it to your environment.

Prediction Table renamed
^^^^^^^^^^^^^^^^^^^^^^^^

Due to your feedback, we’ve chosen to rename the prediction table column names to be more precise. We changed ``p_y1_ct1`` to ``p_y1_without_treatment`` and ``p_y1_ct0`` to ``p_y1_with_treatment``.

Make metrics from a new dataset with custom AUUC thresholds
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

This new feature enables custom AUUC thresholds to calculate the AUUC metric using the make_metrics method. If you don’t specify custom thresholds, the default ones will be used.

Deep Learning with custom metric
''''''''''''''''''''''''''''''''

We have implemented custom metric support for the Deep Learning model. This option is not available for AutoEncoder Deep Learning models.

Prior Release Blogs
~~~~~~~~~~~~~~~~~~~

You can find all prior release blogs `here <https://h2o.ai/blog/category/h2o-release/>`__.

General Blogs
-------------

A Look at the UniformRobust method for ``histogram_type``
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Tree-based algorithms, especially Gradient Boosting Machines (GBM's), are one of the most popular algorithms used. They often out-perform linear models and neural networks for tabular data since they used a boosted approach where each tree built works to fix the error of the previous tree. As the model trains, it is continuously self-correcting. 

`H2O-3's GBM <data-science/gbm.html>`__ is able to train on real-world data out of the box: categoricals and missing values are automatically handled by the algorithm in a fully-distributed way. This means you can train the model on all your data without having to worry about sampling.

In this post, we talk about an improvement to how our GBM handles numeric columns. Traditionally, a GBM model would split numeric columns using uniform range-based splitting. Suppose you had a column that had values ranging from 0 to 100. It would split the column into bins 0-10, 11-20, 21-30, ... 91-100. Each bin would be evaluated to determine the best way to split the data. The best split would be the one that most successfully splits your target column. For example, if you are trying to predict whether or not an employee will quit, the best split would be the one that could separate churn vs not-churn employees the most successfully.

However, when you're handling data that has a column with outliers, this isn't the most effective way to handle your data. Suppose you're analyzing yearly income for a neighborhood: the vast majority of the people you're looking at are making somewhere between $20-$80k. However, there are a few outliers in the neighborhood who make well-over $1 million. When splitting the column up for binning, it will still make uniform splits regardless of the distribution of data. Because the column splitting gets skewed with large outliers, all the outlier observations are classified into a single bin while the rest of the observations end up in another single bin. This, unfortunately, leaves most of the bins unused.  

.. image:: /images/blog/empty-binning.png
    :alt: An example of a histogram about income showing how outliers cause cause issues with binning resulting in many bins being unused. 
    :align: center

This can also drastically slow your prediction calculation since you're iterating through so many empty bins. You also sacrifice accuracy because your data loses its diversity in this uneven binning method. Uniform splitting on data with outliers is full of issues.

The introduction of the `UniformRobust method for histogram <data-science/algo-params/histogram_type.html>`__ (``histogram_type="UniformRobust"``) mitigates these issues! By learning from histograms from the previous layer, we are able to fine-tune the split points for the current layer.

The UniformRobust method isn't impeded by outliers. It starts out using uniform range based binning. Then, it checks the distribution of the data in each bin. Many empty bins will indicate this range based binning is suboptimal, so it will iterate through all the bins and redefine them. If a bin contains no data, it's deleted. If a bin contains too much data, then it's split uniformly.

So, in the case that UniformRobust splitting fails (i.e. the distribution of values is still significantly skewed), the next iteration of finding splits attempts to correct the issue by repeating the procedure with new bins. This allows us to refine the promising bins recursively as we get deeper into the tree.

Let's return to that income example. Using the UniformRobust method, we still begin with uniform splitting and see that very uneven distribution. However, what this method does next is to eliminate all those empty bins and split all the bins containing too much data. 
So, that bin that contained all the $0-100k yearly incomes is uniformly split. Then, with each iteration and each subsequent split, we will begin to see a much more even distribution of the data.

.. image:: /images/blog/nonempty-split.png
    :alt: An example of a histogram about income showing a better distribution of bins despite outlier values.
    :align: center

This method of splitting has the best available runtime performance and accuracy on datasets with outliers. We're looking forward to you trying it out!

Example
'''''''

In the following example, you can compare the performance of the UniformRobust method against the UniformAdaptive method on the Swedish motor insurance dataset. This dataset has slightly larger outliers in its Claims column.

.. tabs::
    .. code-tab:: r R

        library(h2o)
        h2o.init()

        # Import the Swedish motor insurance dataset. This dataset has larger outlier
        # values in the "Claims" column:
        motor <- h2o.importFile("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/Motor_insurance_sweden.txt")

        # Set the predictors and response:
        predictors <- c("Payment", "Insured", "Kilometres", "Zone", "Bonus", "Make")
        response <- "Claims"

        # Build and train the UniformRobust model:
        motor_robust <- h2o.gbm(histogram_type = "UniformRobust", seed = 1234, x = predictors, y = response, training_frame = motor)

        # Build and train the UniformAdaptive model (we will use this model to
        # compare with the UniformRobust model):
        motor_adaptive <- h2o.gbm(histogram_type = "UniformAdaptive", seed = 1234, x = predictors, y = response, training_frame = motor)

        # Compare the RMSE of the two models to see which model performed better:
        print(c(h2o.rmse(motor_robust), h2o.rmse(motor_adaptive)))
        [1] 36.03102 36.69582

        # The RMSE is slightly lower in the UniformRobust model, showing that it performed better
        # that UniformAdaptive on a dataset with outlier values!

    .. code-tab:: python

        import h2o
        from h2o.estimators import H2OGradientBoostingEstimator
        h2o.init()

        # Import the Swedish motor insurance dataset. This dataset has larger outlier
        # values in the "Claims" column:
        motor = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/Motor_insurance_sweden.txt")

        # Set the predictors and response:
        predictors = ["Payment", "Insured", "Kilometres", "Zone", "Bonus", "Make"]
        response = "Claims"

        # Build and train the UniformRobust model:
        motor_robust = H2OGradientBoostingEstimator(histogram_type="UniformRobust", seed=1234)
        motor_robust.train(x=predictors, y=response, training_frame=motor)

        # Build and train the UniformAdaptive model (we will use this model to
        # compare with the UniformRobust model):
        motor_adaptive = H2OGradientBoostingEstimator(histogram_type="UniformAdaptive", seed=1234)
        motor_adaptive.train(x=predictors, y=response, training_frame=motor)

        # Compare the RMSE of the two models to see which model performed better:
        print(motor_robust.rmse(), motor_adaptive.rmse())
        36.03102136406947 36.69581743660738

        # The RMSE is slightly lower in the UniformRobust model, showing that it performed better
        # that UniformAdaptive on a dataset with outlier values!

