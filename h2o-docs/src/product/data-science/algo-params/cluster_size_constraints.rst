``cluster_size_constraints``
----------------------------

- Available in: K-Means
- Hyperparameter: yes

Description
~~~~~~~~~~~

This option specifies how many points should be in each cluster. This makes K-Means less prone to local minima and helps avoid local solutions with empty clusters. If enabled, it explicitly adds :math:`k` constraints to the underlying clustering optomization problem requiring that cluster :math:`h` have at least :math:`x` points.

**Note:** The length of the constraints array has to be same as the number of clusters.

For more information, refer to the following `link <https://pdfs.semanticscholar.org/ecad/eb93378d7911c2f7b9bd83a8af55d7fa9e06.pdf>`__.

Related Parameters
~~~~~~~~~~~~~~~~~~

- ?

Example
~~~~~~~

.. tabs::
   .. code-tab:: r R

		library(h2o)
		h2o.init



   .. code-tab:: python

		import h2o
		from h2o.estimators import H2OKMeansEstimator
		h2o.init()

**References**

Bradley, P.S., Bennett, K.P, & Demiriz, A. "Constrained K-Means Clustering." (2000).