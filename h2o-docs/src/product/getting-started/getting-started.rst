Getting started
===============

Here are some helpful links to help you get started learning H2O-3.

Downloads page
--------------

To begin, download a copy of H2O-3 from the `Downloads page <https://h2o.ai/resources/download/>`__.

1. Click H2O Open Source Platform or scroll down to the H2O section. Here you have access to the different ways to download H2O-3:

- Latest stable: this version is the most recentl alpha release version of H2O-3.
- Nightly bleeding edge: this version contains all the latest changes to H2O-3 that haven't been released officially yet.
- Prior releases: this houses all previously released versions of H2O-3.

For first-time users, we recomment downloading the latest alpha release and the default standalone option (the Download and Run tab) as the installation method. Make sure to install Java if it is not already installed.

.. note::
	By default, this setup is open. Follow `security guidelines <../security.html>`__ if you want to secure your installation.

Using Flow - H2O-3's web UI
---------------------------

`This section describes our web interface, Flow <https://docs.h2o.ai/h2o/latest-stable/h2o-docs/flow.html#using-flow>`__. Flow is similar to IPython notebooks and allows you to create a visual workflow to share with others.

Algorithm support
~~~~~~~~~~~~~~~~~

H2O Flow supports the following H2O-3 algorithms:

- `Aggregator <../data-science/aggregator.html>`__
- `ANOVA GLM <../data-science/anova_glm.html>`__
- `AutoML <../automl.html>`__
- `Cox Proportional Hazards (CoxPH) <../data-science/coxph.html>`__
- `Deep Learning <../data-science/deep-learning.html>`__
- `Distributed Random Forest (DRF) <../data-science/drf.html>`__
- `Distributed Uplift Random Forest (Uplift DRF) <../data-science/upliftdrf.html>`__
- `Extended Isolation Forest <../data-science/eif.html>`__
- `Generalized Linear Model (GLM) <../data-science/glm.html>`__
- `Generalized Low Rank Models (GLRM) <../data-science/glrm.html>`__
- `Gradient Boosting Machine (GBM) <../data-science/gbm.html>`__
- `Information Diagram (Infogram) <../admissible.html>`__
- `Isolation Forest <../data-science/if.html>`__
- `K-Means Clustering <../data-science/k-means.html>`__
- `ModelSelection <../data-science/model_selection.html>`__
- `Naïve Bayes Classifier <../data-science/naive-bayes.html>`__
- `Principal Component Analysis (PCA) <../data-science/pca.html>`__
- `RuleFit <../data-science/rulefit.html>`__
- `Stacked Ensemble <../data-science/stacked-ensembles.html>`__
- `Target Encoding <../data-science/target-encoding.html>`__
- `Word2Vec <../data-science/word2vec.html>`__
- `XGBoost <../data-science/xgboost.html>`__

Tutorials of Flow
~~~~~~~~~~~~~~~~~

The following examples use H2O Flow. To see a step-by-step example of one of our algorithms in action, select a model type from the following list:

- `Deep Learning <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/tutorials/dl/dl.md>`__
- `Distributed Random Forest (DRF) <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/tutorials/rf/rf.md>`__
- `Generalized Linear Model (GLM) <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/tutorials/glm/glm.md>`__
- `Gradient Boosting Machine (GBM) <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/tutorials/gbm/gbm.md>`__
- `K-Means <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/tutorials/kmeans/kmeans.md>`__

Launch from the command line
----------------------------

You can configure H2O-3 when you launch it from the command line. For example, you can specify a different directory for saved Flow data, you could allocate more memory, or you could use a flatfile for a quick configuration of your cluster. See more details about `configuring the additional options when you launch H2O-3 <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/howto/H2O-DevCmdLine.md>`__.


Algorithms
----------

`This section describes the science behind our algorithms <../data-science.html#data-science>`__ and provides a detailed, per-algorithm view of each model type.

Use cases
---------

H2O-3 can handle a wide variety of practical use cases due to its robust catalogue of supported algorithms, wrappers, and machine learning tools. The following are some example problems H2O-3 can handle:

- Determining outliers in housing prices based on number of bedrooms, number of bathrooms, access to waterfront, etc. through `anomaly detection <https://github.com/h2oai/h2o-tutorials/tree/master/best-practices/anomaly-detection>`__.
- Revealing natural customer `segments <https://github.com/h2oai/h2o-tutorials/tree/master/best-practices/segmentation>`__ in retail data to determine which groups are purchasing which products.
- Linking multiple records to the same person with `probabilistic matching <https://github.com/h2oai/h2o-tutorials/tree/master/best-practices/probabilistic-matching-engine>`__.
- Unsampling the minority class for credit card fraud data to handle `imbalanced data <https://github.com/h2oai/h2o-tutorials/tree/master/best-practices/imbalanced-data>`__. 
- `Detecting drift <https://github.com/h2oai/h2o-tutorials/tree/master/best-practices/drift-detection>`__ on avocado sales pre-2018 and 2018+ to determine if a model is still relevant for new data.

See our `best practice tutorials <https://github.com/h2oai/h2o-tutorials/tree/master/best-practices>`__ to further explore the capabilities of H2O-3.

New user quickstart
-------------------

You can follow these steps to quickly get up and running with H2O-3 directly from the `H2O-3 repository <https://github.com/h2oai/h2o-3>`__. These steps will guide you through cloning the repository, starting H2O-3, and importing a dataset. Once you're up and running, you'll be better able to follow examples included within this user guide.

1. In a terminal window, create a folder for the H2O-3 repository:

.. code-block:: bash

   user$ mkdir ~/Desktop/repos

2. Change directories to that new folder, and then clone the repository. Notice that the prompt changes when you change directories:

.. code-block:: bash

    user$ cd ~/Desktop/repos
    repos user$ git clone https://github.com/h2oai/h2o-3.git

3. After the repository is cloned, change directories to the ``h2o-3`` folder:

.. code-block:: bash

    repos user$ cd h2o-3
    h2o-3 user$

4. Run the following command to retrieve sample datasets. These datasets are used throughout the user guide and within the `booklets <../additional-resources.html#algorithms>`__.

.. code-block:: bash

   h2o-3 user$ ./gradlew syncSmalldata

At this point, choose whether you want to complete this quickstart in Python or R. Then, run the following corresponding commands from either the Python or R tab:

.. tabs::
    .. code-tab:: python

        # By default, this setup is open. 
        # Follow our security guidelines (https://docs.h2o.ai/h2o/latest-stable/h2o-docs/security.html) 
        # if you want to secure your installation.

        # Before starting Python, run the following commands to install dependencies.
        # Prepend these commands with `sudo` only if necessary:
        # h2o-3 user$ [sudo] pip install -U requests
        # h2o-3 user$ [sudo] pip install -U tabulate

        # Start python:
        # h2o-3 user$ python

        # Run the following commands to import the H2O module:
        >>> import h2o

        # Run the following command to initialize H2O on your local machine (single-node cluster):
        >>> h2o.init()

        # If desired, run the GLM, GBM, or Deep Learning demo(s):
        >>> h2o.demo("glm")
        >>> h2o.demo("gbm")
        >>> h2o.demo("deeplearning")

        # Import the Iris (with headers) dataset:
        >>> path = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris_wheader.csv"
        >>> iris = h2o.import_file(path=path)

        # View a summary of the imported dataset:
        >>> iris.summary
        # sepal_len    sepal_wid    petal_len    petal_wid    class
        # 5.1          3.5          1.4          0.2          Iris-setosa
        # 4.9          3            1.4          0.2          Iris-setosa
        # 4.7          3.2          1.3          0.2          Iris-setosa
        # 4.6          3.1          1.5          0.2          Iris-setosa
        # 5            3.6          1.4          0.2          Iris-setosa
        # 5.4          3.9          1.7          0.4          Iris-setosa
        # 4.6          3.4          1.4          0.3          Iris-setosa
        # 5            3.4          1.5          0.2          Iris-setosa
        # 4.4          2.9          1.4          0.2          Iris-setosa
        # 4.9          3.1          1.5          0.1          Iris-setosa
        #
        # [150 rows x 5 columns]
        # <bound method H2OFrame.summary of >

    .. code-tab:: r R

        # Download and install R:
        # 1. Go to http://cran.r-project.org/mirrors.html.
        # 2. Select your closest local mirror.
        # 3. Select your operating system (Linux, OS X, or Windows).
        # 4. Depending on your OS, download the appropriate file, along with any required packages.
        # 5. When the download is complete, unzip the file and install.

        # Start R
        h2o-3 user$ r
        ...
        Type 'demo()' for some demos, 'help()' for on-line help, or
        'help.start()' for an HTML browser interface to help.
        Type 'q()' to quit R.
        >

        # By default, this setup is open. 
        # Follow our security guidelines (https://docs.h2o.ai/h2o/latest-stable/h2o-docs/security.html) 
        # if you want to secure your installation.

        # Copy and paste the following commands in R to download dependency packages.
        > pkgs <- c("methods", "statmod", "stats", "graphics", "RCurl", "jsonlite", "tools", "utils")
        > for (pkg in pkgs) {if (! (pkg %in% rownames(installed.packages()))) { install.packages(pkg) }}

        # Run the following command to load the H2O:
        > library(h2o)

        # Run the following command to initialize H2O on your local machine (single-node cluster) using all available CPUs.
        > h2o.init()
     
        # Import the Iris (with headers) dataset.
        > path <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris_wheader.csv"
        > iris <- h2o.importFile(path)

        # View a summary of the imported dataset.
        > print(iris)

          sepal_len    sepal_wid    petal_len    petal_wid        class
        -----------  -----------  -----------  -----------  -----------
                5.1          3.5          1.4          0.2  Iris-setosa
                4.9          3            1.4          0.2  Iris-setosa
                4.7          3.2          1.3          0.2  Iris-setosa
                4.6          3.1          1.5          0.2  Iris-setosa
                5            3.6          1.4          0.2  Iris-setosa
                5.4          3.9          1.7          0.4  Iris-setosa
                4.6          3.4          1.4          0.3  Iris-setosa
                5            3.4          1.5          0.2  Iris-setosa
                4.4          2.9          1.4          0.2  Iris-setosa
                4.9          3.1          1.5          0.1  Iris-setosa
        [150 rows x 5 columns]
        >
