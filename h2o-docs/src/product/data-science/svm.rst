Support Vector Machine (SVM)
----------------------------

**Note**: This is an alpha version of the documentation.

Introduction
~~~~~~~~~~~~

In machine learning, support vector machines are supervised learning models with associated learning algorithms that analyze data used for classification and regression analysis. Given a set of training examples, each marked as belonging to one or the other of two categories, an SVM training algorithm builds a model that assigns new examples to one category or the other. 

H2O’s implementation of support vector machine follows the `PSVM:Parallelizing Support Vector Machineson Distributed Computers <http://papers.nips.cc/paper/3202-parallelizing-support-vector-machines-on-distributed-computers.pdf>`__ specification and can be used to solve binary classification problems only.

MOJO Support
''''''''''''

SVM currently does not support `MOJOs <../save-and-load-model.html#supported-mojos>`__.

Defining an SVM Model
~~~~~~~~~~~~~~~~~~~~~

Algorithm-specific parameters
'''''''''''''''''''''''''''''

-  **disable_training_metrics**: Disable calculating training metrics (expensive on large datasets). This option defaults to ``True`` (enabled).

-  **fact_threshold**: Specify the convergence threshold of the `Incomplete Cholesky Facorization <https://en.wikipedia.org/wiki/Incomplete_Cholesky_factorization>`__ (ICF). This option defaults to ``1e-05``.

-  **feasible_threshold**: Specify the convergence threshold for primal-dual residuals in the `Interior Point Method <https://en.wikipedia.org/wiki/Interior-point_method>`__ (IPM) iteration. This option defaults to ``0.001``.

-  **gamma**: Specify the coefficient of the kernel (currently RBF gamma for gaussian kernel). This option defaults to ``-1`` which means :math:`\frac{1}{\text{#features}}`.

-  **hyper_param**: Specify the penalty parameter C of the error term. This option defaults to ``1``.

-  **kernel_type**: Specify the type of kernel to use (currently only ``gaussian`` is supported).

-  **mu_factor**: Specify to increase the mean value by this factor. This option defaults to ``10``.

-  **negative_weight**: Specify the weight of the negative (-1) class of observations. This option defaults to ``1``.

-  **positive_weight**: Specify the weight of the positive (+1) class of observations. This option defaults to ``1``.

-  **rank_ratio**: Specify the desired rank of the `Incomplete Cholesky Facorization <https://en.wikipedia.org/wiki/Incomplete_Cholesky_factorization>`__ (ICF) matrix expressed as an ration of number of input rows. This option defaults to ``-1`` which means :math:`\sqrt{\text{#rows}}`.

-  **surrogate_gap_threshold**: Specify the feasibility criterion of the surrogate duality gap (eta). This option defaults to ``0.001``.

-  **sv_threshold**: Specify the threshold for accepting a candidate observation into the set of support vectors. This option defaults to ``0.0001``.

Common parameters
'''''''''''''''''

-  `model_id <algo-params/model_id.html>`__: Specify a custom name for the model to use as a reference. By default, H2O automatically generates a destination key.

-  `ignore_const_cols <algo-params/ignore_const_cols.html>`__: Specify whether to ignore constant training columns, since no information can be gained from them. This option defaults to ``True`` (enabled).

-  `ignored_columns <algo-params/ignored_columns.html>`__: (Python and Flow only) Specify the column or columns to be excluded from the model. In Flow, click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons.

-  `max_iterations <algo-params/max_iterations.html>`__: Specify the maximum allowed number of iterations during model training. This value defaults to ``200``.

-  `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations. This value defaults to ``-1`` (time-based random number).

-  `training_frame <algo-params/training_frame.html>`__: *Required* Specify the dataset used to build the model. 

    **NOTE**: In Flow, if you click the **Build a model** button from the ``Parse`` cell, the training frame is entered automatically.

-  `validation_frame <algo-params/validation_frame.html>`__: Specify the dataset used to evaluate the accuracy of the model.

-  `x <algo-params/x.html>`__: Specify a vector containing the names or indices of the predictor variables to use in building the model. If ``x`` is missing, then all columns except ``y`` are used.

-  `y <algo-params/y.html>`__: *Required* Specify the column to use as the dependent variable. The data can be numeric or categorical.

SVM Algorithm
~~~~~~~~~~~~~

As mentioned previously, H2O's implementation of support vector machine follows the PSVM algorithm specified by Edward Y. Chang and others. This implementation can be used to solve binary classification problems. In this configuration, SVM can be formulated as a quadratic optimization problem:

:math:`\min{\frac{1}{2}\|\mathbf{w}\|^2 + C\sum_{i=1}^n\xi_i}` s.t. :math:`1 - y_i(\mathbf{w}^T\phi(\mathbf{x}_i) + b) \leq \xi_i, \xi_i > 0`


where :math:`C` is a regularization hyperparameter, and :math:`\phi(\cdot)` is a basis function that maps each observation :math:`\mathbf{x}_i` to a Reproducing Kernel Hilbert Space. The solution of the problem is a vector of weights :math:`w` and a threshold :math:`b` that defines a separating hyperplane with the largest separation, or margin, between the two classes in the RKHS space. As a result, the decision function of a SVM classifier is :math:`f(\mathbf{x}) = \mathbf{w}^T\phi(\mathbf{x})+b`.

Due to the difficulty of solving this formulation of the problem, the duality principle is used to formulate a dual problem using the method of Lagrangian multipliers:

:math:`\min{\frac{1}{2}\alpha^T\mathbf{Q}\alpha - \alpha^T\mathbf{1}}` s.t. :math:`\mathbf{0} \leq \alpha \leq \mathbf{C}, \mathbf{y}^T\alpha = 0`

Matrix :math:`\mathbf{Q}` is defined as :math:`\mathbf{Q}_{ij} = y_{i}y_{j}K(\mathbf{x}_i,\mathbf{x}_j)`, where :math:`K` is a Kernel function. In our setting, this problem represents a convex Quadratic Programming problem with linear constraints. This problem can be solved by Interior-Point method (IPM): https://en.wikipedia.org/wiki/Interior-point_method.

The main idea of the SVM algorithm is to approximate the (potentially very large) matrix :math:`\mathbf{Q}` using row-based Incomplete Cholesky Factorization (:math:`\mathbf{Q} \approx \mathbf{H}\mathbf{H}^T`). The row-based nature of the factorization algorithm allows for paralellization that can be implemented in the distributed environment of the H2O platform. The quality of the approximation is measured by :math:`trace(\mathbf{Q} - \mathbf{H}\mathbf{H}^T)`, and the algorithm stops when the value of the trace is within a user provided threshold, or when the configured maximum rank of the ICF matrix is reached.

An approximation of the :math:`\mathbf{Q}` matrix is used in the IPM algorithm in order to speed up the bottleneck of the Newton step and leverage the parallel execution environment.

Examples
~~~~~~~~

This example demonstrates how to build a Support Vector Machine (SVM) model using H2O-3 for classification tasks. The model is trained on the splice dataset, with parameters set for gamma and rank ratio to optimize performance. After training, the model's performance is evaluated, and predictions can be generated on the dataset if needed.

.. tabs::
   .. code-tab:: r R

    library(h2o)
    h2o.init()

    # Import the splice dataset into H2O:
    splice <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/splice/splice.svm")

    # Build and train the model:
    svm_model <- h2o.psvm(gamma = 0.01, 
                          rank_ratio = 0.1, 
                          y = "C1", 
                          training_frame = splice, 
                          disable_training_metrics = FALSE)

    # Eval performance:
    perf <- h2o.performance(svm_model)


   .. code-tab:: python

    import h2o
    from h2o.estimators import H2OSupportVectorMachineEstimator
    h2o.init()

    # Import the splice dataset into H2O:
    splice = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/splice/splice.svm")

    # Build and train the model:
    svm_model = H2OSupportVectorMachineEstimator(gamma=0.01, 
                                                 rank_ratio = 0.1, 
                                                 disable_training_metrics = False)
    svm_model.train(y = "C1", training_frame = splice)

    # Eval performance:
    perf = svm_model.model_performance()

    # Generate predictions (if necessary):
    pred = svm_model.predict(splice)


References
~~~~~~~~~~

 E.Y. Chang, K. Zhu, H. Wang, H. Bai, J. Li, Z. Qiu, H. Cui, Parallelizing support vector machines on distributed computers, in Proceedings of NIPS, 2007 `Google Scholar <http://papers.nips.cc/paper/3202-parallelizing-support-vector-machines-on-distributed-computers.pdf>`__
