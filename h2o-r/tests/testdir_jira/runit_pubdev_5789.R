setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.jira.pubdev5789 <- function() {

  ######  Versions that must fail  ######

  # oracle jdk 6
  expect_equal('Your java is not supported: java version "1.6.0_45"', .h2o.check_java_version(c(
    'java version "1.6.0_45"',
    'Java(TM) SE Runtime Environment (build 1.6.0_45-b06)',
    'Java HotSpot(TM) 64-Bit Server VM (build 20.45-b01, mixed mode)'
  )))

  # docker run -it --rm xaas/jrockit java -version
  expect_equal('Your java is not supported: java version "1.6.0_45"', .h2o.check_java_version(c(
    'java version "1.6.0_45"',
    'Java(TM) SE Runtime Environment (build 1.6.0_45-b06)',
    'Oracle JRockit(R) (build R28.2.7-7-155314-1.6.0_45-20130329-0641-linux-x86_64, compiled mode)'
  )))


  # docker run -it --rm java:6-jdk java -version
  expect_equal('Your java is not supported: java version "1.6.0_38"', .h2o.check_java_version(c(
    'java version "1.6.0_38"',
    'OpenJDK Runtime Environment (IcedTea6 1.13.10) (6b38-1.13.10-1~deb7u1)',
    'OpenJDK 64-Bit Server VM (build 23.25-b01, mixed mode)'
  )))

  # docker run -it --rm java:7-jdk-alpine java -version
  expect_equal('Your java is not supported: java version "1.7.0_121"', .h2o.check_java_version(c(
    'java version "1.7.0_121"',
    'OpenJDK Runtime Environment (IcedTea 2.6.8) (Alpine 7.121.2.6.8-r0)',
    'OpenJDK 64-Bit Server VM (build 24.121-b00, mixed mode)'
  )))

  # docker run -it --rm java:7-jdk java -version
  expect_equal('Your java is not supported: java version "1.7.0_111"', .h2o.check_java_version(c(
    'java version "1.7.0_111"',
    'OpenJDK Runtime Environment (IcedTea 2.6.7) (7u111-2.6.7-2~deb8u1)',
    'OpenJDK 64-Bit Server VM (build 24.111-b01, mixed mode)'
  )))

  # docker run -it --rm java:openjdk-7-jdk-alpine java -version
  expect_equal('Your java is not supported: java version "1.7.0_121"', .h2o.check_java_version(c(
    'java version "1.7.0_121"',
    'OpenJDK Runtime Environment (IcedTea 2.6.8) (Alpine 7.121.2.6.8-r0)',
    'OpenJDK 64-Bit Server VM (build 24.121-b00, mixed mode)'
  )))

  ###### Versions that must pass  ######

  # oracle jdk 8
  expect_null(.h2o.check_java_version(c(
    'java version "1.8.0_181"',
    'Java(TM) SE Runtime Environment (build 1.8.0_181-b13)',
    'Java HotSpot(TM) 64-Bit Server VM (build 25.181-b13, mixed mode)'
  )))

  # oracle jdk 10
  expect_null(.h2o.check_java_version(c(
    'java version "10.0.2" 2018-07-17',
    'Java(TM) SE Runtime Environment 18.3 (build 10.0.2+13)',
    'Java HotSpot(TM) 64-Bit Server VM 18.3 (build 10.0.2+13, mixed mode)'
  )))

  # docker run -it --rm openjdk:8-jre-slim java -version
  expect_null(.h2o.check_java_version(c(
    'openjdk version "1.8.0_181"',
    'OpenJDK Runtime Environment (build 1.8.0_181-8u181-b13-1~deb9u1-b13)',
    'OpenJDK 64-Bit Server VM (build 25.181-b13, mixed mode)'
  )))

  # docker run -it --rm openjdk:9-jre-slim java -version
  expect_null(.h2o.check_java_version(c(
    'openjdk version "9.0.4"',
    'OpenJDK Runtime Environment (build 9.0.4+12-Debian-4)',
    'OpenJDK 64-Bit Server VM (build 9.0.4+12-Debian-4, mixed mode)'
  )))

  # docker run -it --rm openjdk:10-jre-slim java -version
  expect_null(.h2o.check_java_version(c(
    'openjdk version "10.0.2" 2018-07-17',
    'OpenJDK Runtime Environment (build 10.0.2+13-Debian-1)',
    'OpenJDK 64-Bit Server VM (build 10.0.2+13-Debian-1, mixed mode)'
  )))

  # docker run -it --rm openjdk:11-jre-slim java -version
  expect_null(.h2o.check_java_version(c(
    'openjdk version "11" 2018-09-25',
    'OpenJDK Runtime Environment (build 11+24-Debian-1)',
    'OpenJDK 64-Bit Server VM (build 11+24-Debian-1, mixed mode, sharing)'
  )))

}

doTest("PUBDEV-5789 check java version", test.jira.pubdev5789)
