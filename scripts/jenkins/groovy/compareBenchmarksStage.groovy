def call(final pipelineContext, final stageConfig, final benchmarkFolderConfig) {

    def EXPECTED_VALUES = [
        'gbm': [
            'paribas': [
                50: [
                    'train_time_min': 9.0,
                    'train_time_max': 11.7
                ],
                200: [
                    'train_time_min': 30,
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
                    'train_time_max': 48.0
                ]
            ],
            'redhat': [
                50: [
                    'train_time_min': 28,
                    'train_time_max': 33.5
                ],
                200: [
                    'train_time_min': 124.0,
                    'train_time_max': 136
                ]
            ],
            'springleaf': [
                50: [
                    'train_time_min': 55.0,
                    'train_time_max': 65
                ],
                200: [
                    'train_time_min': 463.0,
                    'train_time_max': 512.0
                ]
            ],
            'higgs': [
                50: [
                    'train_time_min': 88.0,
                    'train_time_max': 95.0
                ],
                200: [
                    'train_time_min': 502.0,
                    'train_time_max': 549.0
                ]
            ]
        ],
        'gbm-client': [
            'paribas': [
                50: [
                    'train_time_min': 8,
                    'train_time_max': 12
                ],
                200: [
                    'train_time_min': 28,
                    'train_time_max': 40
                ]
            ],
            'homesite': [
                50: [
                    'train_time_min': 9,
                    'train_time_max': 14
                ],
                200: [
                    'train_time_min': 38,
                    'train_time_max': 50
                ]
            ],
            'redhat': [
                50: [
                    'train_time_min': 28,
                    'train_time_max': 32
                ],
                200: [
                    'train_time_min': 125,
                    'train_time_max': 140
                ]
            ],
            'springleaf': [
                50: [
                    'train_time_min': 55,
                    'train_time_max': 68
                ],
                200: [
                    'train_time_min': 460,
                    'train_time_max': 505
                ]
            ],
            'higgs': [
                50: [
                    'train_time_min': 82,
                    'train_time_max': 96
                ],
                200: [
                    'train_time_min': 485,
                    'train_time_max': 530
                ]
            ]
        ],
        'xgb': [
            'airlines-1m': [
                100: [
                    'train_time_min': 38,
                    'train_time_max': 76
                ]
            ],
            'airlines-10m': [
                100: [
                    'train_time_min': 140,
                    'train_time_max': 187
                ]
            ],
            'higgs': [
                100: [
                    'train_time_min': 200,
                    'train_time_max': 218
                ]
            ]
        ],
        'xgb-vanilla': [
            'airlines-1m': [
                100: [
                    'train_time_min': 10,
                    'train_time_max': 27
                ]
            ],
            'airlines-10m': [
                100: [
                    'train_time_min': 90,
                    'train_time_max': 133
                ]
            ],
            'higgs': [
                100: [
                    'train_time_min': 140,
                    'train_time_max': 197
                ]
            ]
        ],
        'xgb-dmlc': [
            'airlines-1m': [
                100: [
                    'train_time_min': 12,
                    'train_time_max': 34
                ]
            ],
            'airlines-10m': [
                100: [
                    'train_time_min': 127,
                    'train_time_max': 173
                ]
            ],
            'higgs': [
                100: [
                    'train_time_min': 210,
                    'train_time_max': 300
                ]
            ]
        ]
    ]

    def TESTED_COLUMNS = ['train_time']

    def insideDocker = load('h2o-3/scripts/jenkins/groovy/insideDocker.groovy')

    insideDocker(pipelineContext.getBuildConfig().getBuildEnv(), stageConfig.image, pipelineContext.getBuildConfig().DOCKER_REGISTRY, pipelineContext.getBuildConfig(), 5, 'MINUTES') {
        String csvFilePath = "${pipelineContext.getUtils().stageNameToDirName(stageConfig.stageName)}/${benchmarkFolderConfig.getCSVPath()}"
        def csvData = parseCsvFile(csvFilePath)

        List failures = []
        for (column in TESTED_COLUMNS) {
            for (line in csvData) {
                if (EXPECTED_VALUES[line.algorithm] == null) {
                    error "Cannot find EXPECTED VALUES for this line: ${line}"
                }
                def datasetValues = EXPECTED_VALUES[line.algorithm][line.dataset]
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
                                    algorithm: line.algorithm,
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
            sendBenchmarksWarningMail(pipelineContext, failures)
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

def sendBenchmarksWarningMail(final pipelineContext, final failures) {
    final def benchmarksSummary = pipelineContext.getBuildSummary().newInstance(false)
    final def buildSummary = pipelineContext.getBuildSummary()

    benchmarksSummary.addSection(this, buildSummary.findSectionOrThrow(buildSummary.DETAILS_SECTION_ID))

    String rowsHTML = ''
    for (failure in failures) {
        rowsHTML += """
            <tr>
                <td style="${benchmarksSummary.TD_STYLE}">${failure.algorithm}</td>
                <td style="${benchmarksSummary.TD_STYLE}">${failure.column}</td>
                <td style="${benchmarksSummary.TD_STYLE}">${failure.dataset.capitalize()} ${failure.ntrees} Trees</td>
                <td style="${benchmarksSummary.TD_STYLE}">${failure.value}</td>
                <td style="${benchmarksSummary.TD_STYLE}">${failure.min}</td>
                <td style="${benchmarksSummary.TD_STYLE}">${failure.max}</td>
            </tr>
        """
    }
    final String warningsTable = """
        <table style="${benchmarksSummary.TABLE_STYLE}">
            <thead>
                <tr>
                    <th style=\"${benchmarksSummary.TH_STYLE}\">Algorithm</th>
                    <th style=\"${benchmarksSummary.TH_STYLE}\">Column</th>
                    <th style=\"${benchmarksSummary.TH_STYLE}\">Test Case</th>
                    <th style=\"${benchmarksSummary.TH_STYLE}\">Value</th>
                    <th style=\"${benchmarksSummary.TH_STYLE}\">Min</th>
                    <th style=\"${benchmarksSummary.TH_STYLE}\">max</th>
                </tr>
            </thead>
            <tbody>
                ${rowsHTML}
            </tbody>
        </table>
    """
    benchmarksSummary.addSection(this, 'warnings', 'Warnings', warningsTable)

    pipelineContext.getEmailer().sendEmail(this, benchmarksSummary.RESULT_WARNING, benchmarksSummary.getSummaryHTML(this))
}

return this
