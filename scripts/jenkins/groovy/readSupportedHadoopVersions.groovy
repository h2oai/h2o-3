import groovy.json.JsonSlurper

def call(final String buildinfoPath) {
    // FIXME
    def final DOCKERIZED_DISTRIBUTIONS = ['cdh'] // ['cdh', 'hdp']

    sh "sed -i 's/SUBST_BUILD_TIME_MILLIS/\"SUBST_BUILD_TIME_MILLIS\"/g' ${buildinfoPath}"
    sh "sed -i 's/SUBST_BUILD_NUMBER/\"SUBST_BUILD_NUMBER\"/g' ${buildinfoPath}"
    def jsonStr = readFile(buildinfoPath)
    def buildinfo = new JsonSlurper().parseText(jsonStr)

    def distributionsToBuild = []

    for (distSpec in buildinfo.hadoop_distributions) {
        def distributionStr = distSpec.distribution.toLowerCase()
        for (dockerizedDist in DOCKERIZED_DISTRIBUTIONS) {
            if (distributionStr.startsWith(dockerizedDist)) {
                def distributionName = dockerizedDist
                def distributionVersion = distributionStr.replaceFirst(dockerizedDist, '')
                distributionsToBuild += [
                        name: distributionName,
                        version: distributionVersion
                ]
                echo "Supported dist found: dist: ${distributionName}, ver: ${distributionVersion}"
            }
        }
    }

    return distributionsToBuild
}

return this