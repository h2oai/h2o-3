def call(final buildConfig) {
  return [
    "JAVA_VERSION=8",
    "BUILD_HADOOP=${buildConfig.getBuildHadoop()}",
    // FIXME
    "H2O_TARGET=cdh5.8"
  ]
}

return this
