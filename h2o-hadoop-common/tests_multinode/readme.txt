Hadoop multi-node tests
=======================

These tests run executed on an external cluster. Before adding 
anything make sure to follow the following rules:

1) make sure an existing tests does not already cover your case

2) do we absolutely need to test this on multi-node?

3) if your tests need any data, prepare the data in /datasets or /user/jenkins/(smalldata|bigdata)

4) does your test need to write anything to hdfs? 
   write only into /user/jenkins/test_output and try to delete everything afterwards.
