#/bin/csh
rm -rf /tmp/h2o-cliffc sandbox 
mkdir sandbox 
nice java -ea -cp "h2o-core/build/classes;lib/commons-collections-3.0.jar;lib/commons-logging-1.1.1.jar;lib/guava-16.0.1.jar;lib/hadoop-common-2.3.0.jar;lib/hamcrest-core-1.3.jar;lib/javassist-3.15.0-GA.jar;lib/joda-time-2.3.jar;lib/junit-4.11.jar;lib/log4j-1.2.15.jar;lib/testng-6.8.jar" water.H2O 1> sandbox/out.1 2>&1 & 
nice java -ea -cp "h2o-core/build/classes;lib/commons-collections-3.0.jar;lib/commons-logging-1.1.1.jar;lib/guava-16.0.1.jar;lib/hadoop-common-2.3.0.jar;lib/hamcrest-core-1.3.jar;lib/javassist-3.15.0-GA.jar;lib/joda-time-2.3.jar;lib/junit-4.11.jar;lib/log4j-1.2.15.jar;lib/testng-6.8.jar" water.H2O 1> sandbox/out.2 2>&1 & 
nice java -ea -cp "h2o-core/build/classes;lib/commons-collections-3.0.jar;lib/commons-logging-1.1.1.jar;lib/guava-16.0.1.jar;lib/hadoop-common-2.3.0.jar;lib/hamcrest-core-1.3.jar;lib/javassist-3.15.0-GA.jar;lib/joda-time-2.3.jar;lib/junit-4.11.jar;lib/log4j-1.2.15.jar;lib/testng-6.8.jar" water.H2O 1> sandbox/out.3 2>&1 & 
nice java -ea -cp "h2o-core/build/classes;lib/commons-collections-3.0.jar;lib/commons-logging-1.1.1.jar;lib/guava-16.0.1.jar;lib/hadoop-common-2.3.0.jar;lib/hamcrest-core-1.3.jar;lib/javassist-3.15.0-GA.jar;lib/joda-time-2.3.jar;lib/junit-4.11.jar;lib/log4j-1.2.15.jar;lib/testng-6.8.jar" water.H2O 1> sandbox/out.4 2>&1 & 
sleep 2
( 
  nice java -ea -cp "h2o-core/build/classes;lib/commons-collections-3.0.jar;lib/commons-logging-1.1.1.jar;lib/guava-16.0.1.jar;lib/hadoop-common-2.3.0.jar;lib/hamcrest-core-1.3.jar;lib/javassist-3.15.0-GA.jar;lib/joda-time-2.3.jar;lib/junit-4.11.jar;lib/log4j-1.2.15.jar;lib/testng-6.8.jar" water.TestUtil water.fvec.WordCountTest 2>&1 &
  echo $? > sandbox/status.0 ) | tee sandbox/out.0
pkill java
