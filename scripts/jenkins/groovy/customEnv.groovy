def call() {
  return [
    "JAVA_VERSION=8",
    "BUILD_HADOOP=false",
    "GRADLE_USER_HOME=../gradle-user-home",
    "GRADLE_OPTS=-Dorg.gradle.daemon=false"
  ]
}

return this
