About POJOs and MOJOs
=====================

H2O allows you to convert the models you have built to either a `Plain Old
Java Object <https://en.wikipedia.org/wiki/Plain_Old_Java_Object>`__
(POJO) or a Model ObJect, Optimized (MOJO). 

H2O-generated MOJO and POJO models are intended to be easily embeddable in any Java environment. The only compilation and runtime dependency for a generated model is the ``h2o-genmodel.jar`` file produced as the build output of these packages. 

Users can refer to the following Quick Start files for more information about generating POJOs and MOJOs:

- `POJO Quick Start <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/howto/POJO_QuickStart.md>`__
- `MOJO Quick Start <https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/howto/MOJO_QuickStart.md>`__

**Note**: MOJOs are supported for GBM, DRF, and GLM models only.

Developers can refer to the the `POJO and MOJO Model Javadoc <http://docs.h2o.ai/h2o/latest-stable/h2o-genmodel/javadoc/index.html>`__.