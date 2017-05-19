Flow
----

**I received the following error message when launching H2O - how do I resolve the error?**

::

    Invalid flow_dir illegal character at index 12...

This error message means that there is a space (or other unsupported character) in your H2O directory. To resolve this error:

-  Create a new folder without unsupported characters to use as the H2O directory (for example, ``C:\h2o``).

  or

-  Specify a different save directory using the ``-flow_dir`` parameter when launching H2O: ``java -jar h2o.jar -flow_dir test``

--------------

**How can I use Flow to export the prediction results with a dataset?**

After obtaining your results, click the **Combine predictions with frame** button, then click the **View Frame** button.

--------------

**How can I call Rapids expressions from Flow?**

You can use the following to write a top level function and call Rapids expression from Flow:

::

	flow.context.requestExec "expr goes here", (err, result) -> print if err then err else result
	#run this once:
	callRapids = (expr) -> flow.context.requestExec expr, (err, result) -> print if err then err else result
	# and then you can run this several times:
	callRapids 'the expr'