``smoothing``
-------------

- Available in Data Preparation for Target Encoding

Description
~~~~~~~~~~~

The smoothing value is used for blending and to calculate ``lambda``. Smoothing controls the rate of transition between the particular level's posterior probability and the prior probability. For smoothing values approaching infinity, it becomes a hard threshold between the posterior and the prior probability. This value defaults to 20.

Related Parameters
~~~~~~~~~~~~~~~~~~
- `blended_avg <blended_avg.html>`__
- `noise <noise.html>`__
- `holdout_type <holdout_type.html>`__
- `inflection_point <inflection_point.html>`__

Example
~~~~~~~

Refer to the `Target Encoding <../../data-munging/target-encoding.html>`__ data munging topic to view a detailed example.