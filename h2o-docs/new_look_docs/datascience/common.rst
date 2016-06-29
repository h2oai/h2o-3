Commonalities
-------------

Quantiles
~~~~~~~~~

**Note**: The quantile results in Flow are computed lazily on-demand and
cached. It is a fast approximation (max - min / 1024) that is very
accurate for most use cases. If the distribution is skewed, the quantile
results may not be as accurate as the results obtained using
``h2o.quantile`` in R or ``H2OFrame.quantile`` in Python.
