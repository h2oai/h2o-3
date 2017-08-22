About POJOs and MOJOs
=====================

H2O allows you to convert the models you have built to either a `Plain Old Java Object <https://en.wikipedia.org/wiki/Plain_Old_Java_Object>`__ (POJO) or a Model ObJect, Optimized (MOJO). 

H2O-generated MOJO and POJO models are intended to be easily embeddable in any Java environment. The only compilation and runtime dependency for a generated model is the ``h2o-genmodel.jar`` file produced as the build output of these packages. This file is a library that supports scoring. For POJOs, it contains the base classes from which the POJO is derived from. (You can see "extends GenModel" in a pojo class. The GenModel class is part of this library.) For MOJOs, it also contains the required readers and interpreters. The ``h2o-genmodel.jar`` file is required when POJO/MOJO models are deployed to production.

Users can refer to the following Quick Start files for more information about generating POJOs and MOJOs:

- `POJO Quick Start <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/howto/POJO_QuickStart.md>`__
- `MOJO Quick Start <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/howto/MOJO_QuickStart.md>`__

**Notes**: 

- MOJOs are supported for DRF, GBM, GLM, GLRM, K-Means, Word2vec, and XGBoost models only.
- POJOs are not supported for XGBoost.

Developers can refer to the the `POJO and MOJO Model Javadoc <http://docs.h2o.ai/h2o/latest-stable/h2o-genmodel/javadoc/index.html>`__.