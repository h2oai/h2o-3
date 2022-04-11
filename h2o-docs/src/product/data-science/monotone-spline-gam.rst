GAM Monotone Splines
--------------------

We have implemented I-splines which are used as monotone splines.

B-splines: :math:`Q_{i,k}(t)`
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This implementation begins with the B-spline. Let :math:`Q_{i,k}(t)` denote a B-spline of order :math:`k` at knot :math:`i` where it is non-zero over the duration :math:`t_0 \leq t < t_k` The recursive formula [:ref:`2<ref2>`] used to generate a B-spline of a higher order from a B-spline of a lower order is:

.. math::
	
	Q_{i,k}(t) = {\frac{(t-t_{i})}{(t_{i+k}-t_{i})}} Q_{i,k-1}(t) + {\frac{(t_{i+k}-t)}{(t_{i+k}-t_{i})}} Q_{i+1,k-1}(t) {\text{ }}{\text { Equation 1}}

B-spline of order 1
'''''''''''''''''''

Using knots :math:`t_o,t_1,\dots ,t_N` over the range of inputs of interest from :math:`t_0` to :math:`t_N`, an order 1 B-spline is defined as [:ref:`2<ref2>`]:

.. math::

	Q_{i,1}(t) = \begin{cases} {\frac {1}{(t_{i+1}-t_t)}}, t_i \leq t < t_{i+1} \\ 0, t < t_i \text{ or } t \geq t_{i+1} \\\end{cases} {\text{ }}{\text{ Equation 2}}

If there are :math:`N+1` knots over the input interval from :math:`t_0` to :math:`t_N`, there will be :math:`N` basis function of order 1.

B-spline of order 2
'''''''''''''''''''

Using knots :math:`t_0,t_1,\dots ,t_N` over the range of inputs of interest, the new range of knots will be :math:`t_0,t_0,t_1,t_2,\dots ,t_{N-1},t_N,t_N` in order to preserve the continuity of derivatives of the splines over the knots [:ref:`3<ref3>`]. This is just adding the duplication of the knots at the beginning and the end. Using the recursive formula from *Equation 1* for B-splines and :math:`Q_{i,1}(t)` *Equation 2*, an order 2 B-spline can be derived as:

.. math::

	Q_{i,2}(t) = \begin{cases} {\frac{(t-t_1)}{(t_{i+2}-t_i)(t_{i+1}-t_i)}}, t_i \leq t < t_{t+1} \\ {\frac{(t_{t+2}-2)}{(t_{i+2}-t_{i+1})(t_{i+2}-t_i)}}, t_{i+1} \leq t < t_{i+2} \\ 0,t<t_i \text{ or } t \geq t_{i+2} \\\end{cases}{\text{ }}{\text{ Equation 3}}

There will be :math:`N+1` basis functions of order 2 defined over the input range of :math:`t_0` to :math:`t_N`.

B-spline of order 3
'''''''''''''''''''

The new knots to define the order 3 B-splines are :math:`t_0,t_0,t_0,t_1,t_2,\dots ,t_{N-1},t_N,t_N,t_N`. Again, using the recursive formula of *Equation 1* and :math:`Q_{i,2}(t)` *Equation 3*, an order 3 B-spline can be derived as:

.. math::

	Q_{i,3}(t) = \begin{cases}{\frac{(t-t_i)^2}{(t_{i+3}-t_i)(t_{i+2}-t_i)(t_{i+1}-t_i)}}, t_i \leq t < t_i+1 \\
	{\frac{(t-t_i)(t_{i+2}-t)}{(t_{i+2}-t_{i+1})(t_{i+2}-t_i)(t_{i+3}-t_i)}} + {\frac{(t_{i+3}-t)(t-t_{i+1})}{(t_{i+3}-t_{i+1})(t_{i+2}-t_{i+1})(t_{i+3}-t_i)}}, t_{i+1} \leq t < t_{i+2} \\
	{\frac{(t_{i+3}-t)^2}{t_{i+3}-t_i)(t_{i+3}-t_{i+2})(t_{i+3}-t_{i+1})}}, t_{i+2} \leq t < t_{i+3} \\
	0,t>t \text{ or } t \geq t_{i+3} \\\end{cases}

There will be :math:`N+2` basis functions of order 3 defined over the input range of :math:t_0` to :math:`t_N`.

B-spline of order *k*
'''''''''''''''''''''

To generate an order *k* B-spline, you must extend the original knots :math:`t_0,t_1,\dots ,t_N` over the range of inputs of interest. Add :math:`k-1` knots of value :math:`t_0` to the front of the knots and :math:`k-1` knots of value :math:`t_N` to the end of knots. The knots with dupplication will look like:

.. math::
	
	t_0,t_0,\dots ,t_0,t_1,t_2,\dots ,t_{N-1},t_N,t_N,t_N.

where:

- :math:`t_0,t_0,\dots ,t_0` and :math:`t_N,t_N,t_N` are the :math:`k` duplicates

The B-spline of order :math:`k` is basically a polynomial of order :math:`k-1`.

NBSplineType1: :math:`M_{i,k}(t)`
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

If you normalize the basic B-spline function to have an integration of 1 over the interest range where it is non-zero, you can denote it as :math:`M_{i,k}(t)`. This is the normalized B-spline type I, and it is defined as:

.. math::
	
	M_{i,k}(t) = {\frac{k}{k-1}}\bigg( {\frac{t-t_i)}{t_{i+k}-t_i)}}M_{i,k-1}(t)-{\frac{(t_{i+k}-t)}{(t_{i+k}-t_i)}}M_{i+1,k-1}(t)\bigg) {\text{ }}{\text{ Equation 4}}

Note that :math:`M_{i,k}(t)` is defined over the same knot sequence as the original B-spline and the number of :math:`M_{i,k}(t)` splines is the same as the number of B-splines over the same known sequence.

M-spline of order 1
'''''''''''''''''''

Using knotes :math:`t_0,t_1,\dots ,t_N` over the range of inputs of interest from :math:`t_0` to :math:`t_N`, an order 1 B-spline is defined as [:ref:`2<ref2>`]:

.. math::
	
	














References
~~~~~~~~~~

.. _ref1:

Lecture 7 Divided Difference Interpolation Polynomial by Professor R.Usha, Department of Mathematics, IITM, https://www.youtube.com/watch?v=4m5AKnseSyI .

.. _ref2:

Carl De Boor et. al., ON CALCULATING WITH B-SPLINES II. INTEGRATION, ResearchGate Article, January 1976.

.. _ref3:

J. O. Ramsay, “Monotone Regression Splines in Action”, Statistical Science, 1988, Vol. 3, No. 4, 425-461.