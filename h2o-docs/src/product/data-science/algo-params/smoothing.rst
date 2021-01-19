``smoothing``
-------------

- Available in: Target Encoding

Description
~~~~~~~~~~~

The smoothing value is used for blending and to calculate ``lambda``. Smoothing controls the rate of transition between the particular level's posterior probability and the prior probability. For smoothing values approaching infinity, it becomes a hard threshold between the posterior and the prior probability. This value defaults to 20.

Related Parameters
~~~~~~~~~~~~~~~~~~
- `blending <blending.html>`__
- `inflection_point <inflection_point.html>`__
- `data_leakage_handling <data_leakage_handling.html>`__
- `noise <noise.html>`__

Example
~~~~~~~

Refer to the `Target Encoding <../target-encoding.html>`__ topic to view a detailed example.
