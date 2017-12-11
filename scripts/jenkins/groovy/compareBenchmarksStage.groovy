def call(buildConfig, stageConfig, benchmarkFolderConfig) {

    def EXPECTED_VALUES = [
            'paribas': [
                    50: [
                            'train_time_min': 9.2,
                            'train_time_max': 11.7
                    ],
                    200: [
                            'train_time_min': 31.1,
                            'train_time_max': 35.1
                    ]
            ],
            'homesite': [
                    50: [
                            'train_time_min': 11.4,
                            'train_time_max': 13.3
                    ],
                    200: [
                            'train_time_min': 41.2,
                            'train_time_max': 46.0
                    ]
            ],
            'redhat': [
                    50: [
                            'train_time_min': 28.5,
                            'train_time_max': 33.5
                    ],
                    200: [
                            'train_time_min': 132.5,
                            'train_time_max': 139.5
                    ]
            ],
            'springleaf': [
                    50: [
                            'train_time_min': 55.0,
                            'train_time_max': 63.5
                    ],
                    200: [
                            'train_time_min': 464.0,
                            'train_time_max': 497.0
                    ]
            ],
            'higgs': [
                    50: [
                            'train_time_min': 92.0,
                            'train_time_max': 100.0
                    ],
                    200: [
                            'train_time_min': 510.0,
                            'train_time_max': 559.0
                    ]
            ]
    ]

    def TESTED_COLUMNS = ['train_time']

    def insideDocker = load('h2o-3/scripts/jenkins/groovy/insideDocker.groovy')
    def customEnv = load('h2o-3/scripts/jenkins/groovy/customEnv.groovy')
    def stageNameToDirName = load('h2o-3/scripts/jenkins/groovy/stageNameToDirName.groovy')

    insideDocker(customEnv(), stageConfig.image, buildConfig.DOCKER_REGISTRY, 5, 'MINUTES') {
        String csvFilePath = "${stageNameToDirName(stageConfig.stageName)}/${benchmarkFolderConfig.getCSVPath()}"
        def csvData = parseCsvFile(csvFilePath)

        List failures = []
        for (column in TESTED_COLUMNS) {
            for (line in csvData) {
                def datasetValues = EXPECTED_VALUES[line.dataset]
                if (datasetValues) {
                    def ntreesValues = datasetValues[Integer.parseInt(line.ntrees)]
                    if (ntreesValues) {
                        def minValue = ntreesValues["${column}_min"]
                        if (minValue == null) {
                            error("Minimum for ${column} for ${line.dataset} with ${line.ntrees} trees cannot be found")
                        }
                        def maxValue = ntreesValues["${column}_max"]
                        if (maxValue == null) {
                            error("Maximum for ${column} for ${line.dataset} with ${line.ntrees} trees cannot be found")
                        }
                        def lineValue = Double.parseDouble(line[column])
                        echo "Checking ${column} for ${line.dataset} with ${line.ntrees} trees"
                        if ((lineValue < minValue) || (lineValue > maxValue)) {
                            echo "Check failed. Expected interval is ${minValue}..${maxValue}. Actual value ${lineValue}"
                            failures += [
                                    dataset: line.dataset,
                                    ntrees: line.ntrees,
                                    column: column,
                                    min: minValue,
                                    max: maxValue,
                                    value: lineValue.round(4)
                            ]
                        } else {
                            echo "Check OK!"
                        }
                    } else {
                        error "Cannot find EXPECTED_VALUES for ${line.dataset} with ${line.ntrees} trees"
                    }
                } else {
                    error "Cannot find EXPECTED_VALUES for ${line.dataset}"
                }
            }
        }
        if (!failures.isEmpty()) {
            echo failuresToText(failures)
            sendBenchmarksWarningMail(failures)
            error "One or more checks failed"

        } else {
            echo "All checks passed!"
        }
    }
}

def parseCsvFile(final String filePath, final String separator=',') {
    final String text = readFile(sh(script: "ls ${filePath}", returnStdout: true).trim())
    if (text == null) {
        return null
    }

    def result = []

    List lines = text.split('\n')
    if (lines.size() > 0) {
        List colNames = lines[0].split(separator).collect{
            trimQuotes(it)
        }
        Map colIndices = [:]
        colNames.eachWithIndex{ e, i ->
            colIndices[e] = i
        }

        for (line in lines[1..-1]) {
            values = line.split(separator)
            data = [:]
            for (colName in colNames) {
                data[colName] = trimQuotes(values[colIndices[colName]])
            }
            result += data
        }
    }
    return result
}

def trimQuotes(final String text) {
    def result = text
    if (result.startsWith('"')) {
        result = result.substring(1)
    }
    if (result.endsWith('"')) {
        result = result.substring(0, result.length() - 1)
    }
    return result
}

def failuresToText(final failures, final String joinStr='\n') {
    result = []
    for (failure in failures) {
        result += "Check of ${failure.column} for ${failure.dataset} with ${failure.ntrees}. Expected interval is ${failure.min}..${failure.max}. Actual value is ${failure.value}"
    }
    return result.join(joinStr)
}

def sendBenchmarksWarningMail(final failures) {
    def sendEmail = load('h2o-3/scripts/jenkins/groovy/sendEmail.groovy')
    def emailContentHelpers = load('h2o-3/scripts/jenkins/groovy/emailContentHelpers.groovy')
    def benchmarksEmailContent = load('h2o-3/scripts/jenkins/groovy/benchmarksEmailContent.groovy')

    def emailBody = benchmarksEmailContent(failures, emailContentHelpers)

    sendEmail('warning', emailBody, emailContentHelpers.getRelevantPipelineRecipients(result))
}

return this