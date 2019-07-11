``inflection_point``
--------------------

- Available in Data Preparation for Target Encoding


Description
~~~~~~~~~~~

The inflection point value is used for blending and to calculate ``lambda``. This determines half of the minimal sample size for which we completely trust the estimate based on the sample in the particular level of the categorical variable. This value defaults value to 10.

Related Parameters
~~~~~~~~~~~~~~~~~~
- `blended_avg <blended_avg.html>`__
- `noise <noise.html>`__
- `smoothing <smoothing.html>`__
- `holdout_type <holdout_type.html>`__

Example
~~~~~~~

Refer to the `Target Encoding <../../data-munging/target-encoding.html>`__ data munging topic to view a detailed example.