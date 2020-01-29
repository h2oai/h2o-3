def call(final pipelineContext, final stageConfig, final benchmarkFolderConfig) {
    // use scripts/benchmark_time_analysis.R to refresh these values
    def EXPECTED_VALUES = [
        'gbm': [
            'paribas': [
                50: [
                    'train_time_min':  7,
                    'train_time_max': 11
                ],
                200: [
                    'train_time_min': 31,
                    'train_time_max': 36
                ]
            ],
            'homesite': [
                50: [
                    'train_time_min': 9,
                    'train_time_max': 13
                ],
                200: [
                    'train_time_min': 41,
                    'train_time_max': 50
                ]
            ],
            'redhat': [
                50: [
                    'train_time_min': 29,
                    'train_time_max': 33
                ],
                200: [
                    'train_time_min': 139,
                    'train_time_max': 157
                ]
            ],
            'springleaf': [
                50: [
                    'train_time_min': 60,
                    'train_time_max': 66
                ],
                200: [
                    'train_time_min': 461,
                    'train_time_max': 540
                ]
            ],
            'higgs': [
                50: [
                    'train_time_min': 87,
                    'train_time_max': 96
                ],
                200: [
                    'train_time_min': 519,
                    'train_time_max': 546
                ]
            ]
        ],
        'glm': [
            'paribas': [
                COORDINATE_DESCENT: [
                    'train_time_min': 3,
                    'train_time_max': 6
                ],
                IRLSM: [
                    'train_time_min': 3,
                    'train_time_max': 6
                ]
            ],
            'homesite': [
                COORDINATE_DESCENT: [
                    'train_time_min': 36,
                    'train_time_max': 43
                ],
                IRLSM: [
                    'train_time_min': 68,
                    'train_time_max': 76
                ]
            ],
            'redhat': [
                COORDINATE_DESCENT: [
                    'train_time_min': 34,
                    'train_time_max': 40
                ],
                IRLSM: [
                    'train_time_min': 36,
                    'train_time_max': 42
                ]
            ],
            'springleaf': [
                COORDINATE_DESCENT: [
                    'train_time_min': 142,
                    'train_time_max': 157
                ],
                IRLSM: [
                    'train_time_min': 259,
                    'train_time_max': 282
                ]
            ],
            'higgs': [
                COORDINATE_DESCENT: [
                    'train_time_min': 43,
                    'train_time_max': 50
                ],
                IRLSM: [
                    'train_time_min': 60,
                    'train_time_max': 66
                ]
            ]
        ],
        'gbm-client': [
            'paribas': [
                50: [
                    'train_time_min': 7,
                    'train_time_max': 11
                ],
                200: [
                    'train_time_min': 31,
                    'train_time_max': 36
                ]
            ],
            'homesite': [
                50: [
                    'train_time_min': 9,
                    'train_time_max': 13
                ],
                200: [
                    'train_time_min': 41,
                    'train_time_max': 50
                ]
            ],
            'redhat': [
                50: [
                    'train_time_min': 29,
                    'train_time_max': 33
                ],
                200: [
                    'train_time_min': 139,
                    'train_time_max': 157
                ]
            ],
            'springleaf': [
                50: [
                    'train_time_min': 60,
                    'train_time_max': 66
                ],
                200: [
                    'train_time_min': 493,
                    'train_time_max': 510
                ]
            ],
            'higgs': [
                50: [
                    'train_time_min': 87,
                    'train_time_max': 96
                ],
                200: [
                    'train_time_min': 519,
                    'train_time_max': 546
                ]
            ]
        ],
        'xgb': [
            'airlines-1m': [
                [100, "cpu"]: [
                    'train_time_min': 26,
                    'train_time_max': 45
                ],
                [100, "gpu"]: [
                    'train_time_min': 11,
                    'train_time_max': 17
                ]
            ],
            'airlines-10m': [
                [100, "cpu"]: [
                    'train_time_min': 114,
                    'train_time_max': 156
                ],
                [100, "gpu"]: [
                    'train_time_min': 30,
                    'train_time_max': 49
                ]
            ],
            'higgs': [
                [100, "cpu"]: [
                    'train_time_min': 167,
                    'train_time_max': 213
                ],
                [100, "gpu"]: [
                    'train_time_min': 43,
                    'train_time_max': 49
                ]
            ],
            'cox2': [
                [10, "cpu"]: [
                    'train_time_min': 1439,
                    'train_time_max': 2038
                ]
            ],
            'cox2-20m': [
                [10, "cpu"]: [
                    'train_time_min': 322,
                    'train_time_max': 350
                ]
            ]
        ],
        'xgb-vanilla': [
            'airlines-1m': [
                100: [
                    'train_time_min': 21,
                    'train_time_max': 31
                ]
            ],
            'airlines-10m': [
                100: [
                    'train_time_min': 99,
                    'train_time_max': 141
                ]
            ],
            'higgs': [
                100: [
                    'train_time_min': 140,
                    'train_time_max': 175
                ]
            ]
        ],
        'xgb-dmlc': [
            'airlines-1m': [
                100: [
                    'train_time_min': 14,
                    'train_time_max': 23
                ]
            ],
            'airlines-10m': [
                100: [
                    'train_time_min': 76,
                    'train_time_max': 103
                ]
            ],
            'higgs': [
                100: [
                    'train_time_min': 153,
                    'train_time_max': 176
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
                    def interval
                    def testCaseKey
                    def testCaseValue
                    if (line.backend) {
                        interval = datasetValues[[Integer.parseInt(line.ntrees), line.backend]]
                        testCaseKey = "ntrees-${line.backend}"
                        testCaseValue = line.ntrees
                    } else if (line.ntrees) {
                        interval = datasetValues[Integer.parseInt(line.ntrees)]
                        testCaseKey = 'ntrees'
                        testCaseValue = line.ntrees
                    } else if (line.solver) {
                        interval = datasetValues[line.solver]
                        testCaseKey = 'solver'
                        testCaseValue = line.solver
                    } else {
                        error "Cannot find usable key to get expected interval. Supported keys are ntrees and solver"
                    }
                    if (interval) {
                        def minValue = interval["${column}_min"]
                        if (minValue == null) {
                            error("Minimum for ${column} for ${line.dataset} cannot be found")
                        }
                        def maxValue = interval["${column}_max"]
                        if (maxValue == null) {
                            error("Maximum for ${column} for ${line.dataset} cannot be found")
                        }
                        def lineValue = Double.parseDouble(line[column])
                        echo "Checking ${column} for ${line.dataset} with ${testCaseKey} = ${testCaseValue}"
                        if ((lineValue < minValue) || (lineValue > maxValue)) {
                            echo "Check failed. Expected interval is ${minValue}..${maxValue}. Actual value ${lineValue}"
                            failures += [
                                    algorithm: line.algorithm,
                                    dataset: line.dataset,
                                    testCaseKey: testCaseKey,
                                    testCaseValue: testCaseValue,
                                    column: column,
                                    min: minValue,
                                    max: maxValue,
                                    value: lineValue.round(4)
                            ]
                        } else {
                            echo "Check OK!"
                        }
                    } else {
                        error "Cannot find EXPECTED_VALUES for ${line.dataset} with ${testCaseKey} = ${testCaseValue}"
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
        result += "Check of ${failure.column} for ${failure.dataset} with ${failure.testCaseKey} = ${failure.testCaseValue}. Expected interval is ${failure.min}..${failure.max}. Actual value is ${failure.value}"
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
                <td style="${benchmarksSummary.TD_STYLE}">${failure.dataset.capitalize()} ${failure.testCaseKey} = ${failure.testCaseValue}</td>
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
