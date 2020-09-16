def call(final pipelineContext, final stageConfig, final benchmarkFolderConfig) {
    // use scripts/benchmark_time_analysis.R to refresh these values
    def EXPECTED_VALUES = [
        'gbm': [
            'paribas': [
                50: [
                    'train_time_min':  7,
                    'train_time_max': 12
                ],
                200: [
                    'train_time_min': 32,
                    'train_time_max': 36
                ]
            ],
            'homesite': [
                50: [
                    'train_time_min': 9,
                    'train_time_max': 14
                ],
                200: [
                    'train_time_min': 42,
                    'train_time_max': 50
                ]
            ],
            'redhat': [
                50: [
                    'train_time_min': 29,
                    'train_time_max': 36
                ],
                200: [
                    'train_time_min': 139,
                    'train_time_max': 151
                ]
            ],
            'springleaf': [
                50: [
                    'train_time_min': 60,
                    'train_time_max': 69
                ],
                200: [
                    'train_time_min': 498,
                    'train_time_max': 562
                ]
            ],
            'higgs': [
                50: [
                    'train_time_min': 88,
                    'train_time_max': 98
                ],
                200: [
                    'train_time_min': 522,
                    'train_time_max': 554
                ]
            ]
        ],
        'glm': [
            'paribas': [
                COORDINATE_DESCENT: [
                    'train_time_min': 3,
                    'train_time_max': 8
                ],
                IRLSM: [
                    'train_time_min': 4,
                    'train_time_max': 8
                ]
            ],
            'homesite': [
                COORDINATE_DESCENT: [
                    'train_time_min': 38,
                    'train_time_max': 48
                ],
                IRLSM: [
                    'train_time_min': 71,
                    'train_time_max': 84
                ]
            ],
            'redhat': [
                COORDINATE_DESCENT: [
                    'train_time_min': 26,
                    'train_time_max': 34
                ],
                IRLSM: [
                    'train_time_min': 28,
                    'train_time_max': 36
                ]
            ],
            'springleaf': [
                COORDINATE_DESCENT: [
                    'train_time_min': 144,
                    'train_time_max': 154
                ],
                IRLSM: [
                    'train_time_min': 259,
                    'train_time_max': 272
                ]
            ],
            'higgs': [
                COORDINATE_DESCENT: [
                    'train_time_min': 47,
                    'train_time_max': 54
                ],
                IRLSM: [
                    'train_time_min': 65,
                    'train_time_max': 73
                ]
            ]
        ],
        'gam': [
            'paribas': [
                COORDINATE_DESCENT: [
                    'train_time_min': 2,
                    'train_time_max': 6
                ],
                IRLSM: [
                    'train_time_min': 2,
                    'train_time_max': 6
                ]
            ],
            'homesite': [
                COORDINATE_DESCENT: [
                    'train_time_min': 2,
                    'train_time_max': 8
                ],
                IRLSM: [
                    'train_time_min': 2,
                    'train_time_max': 7
                ]
            ],
            'springleaf': [
                COORDINATE_DESCENT: [
                    'train_time_min': 3,
                    'train_time_max': 8
                ],
                IRLSM: [
                    'train_time_min': 17,
                    'train_time_max': 21
                ]
            ],
            'higgs': [
                COORDINATE_DESCENT: [
                    'train_time_min': 90,
                    'train_time_max': 110
                ],
                IRLSM: [
                    'train_time_min': 130,
                    'train_time_max': 145
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
                    'train_time_min': 31,
                    'train_time_max': 39
                ]
            ],
            'homesite': [
                50: [
                    'train_time_min': 10,
                    'train_time_max': 14
                ],
                200: [
                    'train_time_min': 42,
                    'train_time_max': 53
                ]
            ],
            'redhat': [
                50: [
                    'train_time_min': 30,
                    'train_time_max': 37
                ],
                200: [
                    'train_time_min': 141,
                    'train_time_max': 152
                ]
            ],
            'springleaf': [
                50: [
                    'train_time_min': 61,
                    'train_time_max': 69
                ],
                200: [
                    'train_time_min': 497,
                    'train_time_max': 540
                ]
            ],
            'higgs': [
                50: [
                    'train_time_min': 88,
                    'train_time_max': 96
                ],
                200: [
                    'train_time_min': 524,
                    'train_time_max': 544
                ]
            ]
        ],
        'xgb': [
            'airlines-1m': [
                [100, "cpu"]: [
                    'train_time_min': 12,
                    'train_time_max': 35
                ],
                [100, "gpu"]: [
                    'train_time_min': 11,
                    'train_time_max': 19
                ]
            ],
            'airlines-10m': [
                [100, "cpu"]: [
                    'train_time_min': 102,
                    'train_time_max': 175
                ],
                [100, "gpu"]: [
                    'train_time_min': 31,
                    'train_time_max': 49
                ]
            ],
            'higgs': [
                [100, "cpu"]: [
                    'train_time_min': 153,
                    'train_time_max': 178
                ],
                [100, "gpu"]: [
                    'train_time_min': 50,
                    'train_time_max': 59
                ]
            ],
            'cox2': [
                [10, "cpu"]: [
                    'train_time_min': 1301,
                    'train_time_max': 1581
                ]
            ],
            'cox2-20m': [
                [10, "cpu"]: [
                    'train_time_min': 291,
                    'train_time_max': 310
                ]
            ]
        ],
        'xgb-vanilla': [
            'airlines-1m': [
                100: [
                    'train_time_min': 6,
                    'train_time_max': 8
                ]
            ],
            'airlines-10m': [
                100: [
                    'train_time_min': 74,
                    'train_time_max': 89
                ]
            ],
            'higgs': [
                100: [
                    'train_time_min': 110,
                    'train_time_max': 126
                ]
            ]
        ],
        'xgb-dmlc': [
            'airlines-1m': [
                100: [
                    'train_time_min': 6,
                    'train_time_max': 9
                ]
            ],
            'airlines-10m': [
                100: [
                    'train_time_min': 74,
                    'train_time_max': 89
                ]
            ],
            'higgs': [
                100: [
                    'train_time_min': 117,
                    'train_time_max': 132
                ]
            ]
        ],
        'merge': [
            'fileSize100millionRows2ColsallxyTF': [
                [100000000, 2]: [
                    'train_time_min': 33,
                    'train_time_max': 37
                ]
            ],
            'fileSize10millionRows2ColsallxyTF': [
                [10000000, 2]: [
                    'train_time_min': 4,
                    'train_time_max': 9
                ]
            ],
            'fileSize100millionRows2ColsallxyFF': [
                [100000000, 2]: [
                    'train_time_min': 33,
                    'train_time_max': 37
                ]
            ],
            'fileSize10millionRows2ColsallxyFF': [
                [10000000, 2]: [
                    'train_time_min': 4,
                    'train_time_max': 9
                ]
            ]
        ],
        'sort': [
            'fileSize100millionRows2Cols': [
                [100000000, 2]: [
                    'train_time_min': 9,
                    'train_time_max': 14
                ]
            ],
            'fileSize10millionRows2Cols': [
                [10000000, 2]: [
                    'train_time_min': 2,
                    'train_time_max': 5
                ]
            ]
        ],
        'rulefit': [
            'redhat': [
                ['RULES', 3, 3]: [
                        'train_time_min': 3,
                        'train_time_max': 8
                ],
                ['RULES', 1, 10]: [
                        'train_time_min': 3,
                        'train_time_max': 8
                ],
                ['RULES_AND_LINEAR', 3, 3]: [
                        'train_time_min': 3,
                        'train_time_max': 8
                ],
                ['RULES_AND_LINEAR', 1, 10]: [
                        'train_time_min': 3,
                        'train_time_max': 8
                ]
            ],
            'homesite': [
                ['RULES', 3, 3]: [
                        'train_time_min': 3,
                        'train_time_max': 8
                ],
                ['RULES', 1, 10]: [
                        'train_time_min': 3,
                        'train_time_max': 8
                ],
                ['RULES_AND_LINEAR', 3, 3]: [
                        'train_time_min': 3,
                        'train_time_max': 8
                ],
                ['RULES_AND_LINEAR', 1, 10]: [
                        'train_time_min': 3,
                        'train_time_max': 8
                ]
            ],
            'springleaf': [
                ['RULES', 3, 3]: [
                        'train_time_min': 3,
                        'train_time_max': 8
                ],
                ['RULES', 1, 10]: [
                        'train_time_min': 3,
                        'train_time_max': 8
                ],
                ['RULES_AND_LINEAR', 3, 3]: [
                        'train_time_min': 3,
                        'train_time_max': 8
                ],
                ['RULES_AND_LINEAR', 1, 10]: [
                        'train_time_min': 3,
                        'train_time_max': 8
                ]
            ],
            'paribas': [
                ['RULES', 3, 3]: [
                        'train_time_min': 3,
                        'train_time_max': 8
                ],
                ['RULES', 1, 10]: [
                        'train_time_min': 3,
                        'train_time_max': 8
                ],
                ['RULES_AND_LINEAR', 3, 3]: [
                        'train_time_min': 3,
                        'train_time_max': 8
                ],
                ['RULES_AND_LINEAR', 1, 10]: [
                        'train_time_min': 3,
                        'train_time_max': 8
                ]
            ],
            'higgs': [
                ['RULES', 3, 3]: [
                        'train_time_min': 3,
                        'train_time_max': 8
                ],
                ['RULES', 1, 10]: [
                        'train_time_min': 3,
                        'train_time_max': 8
                ],
                ['RULES_AND_LINEAR', 3, 3]: [
                        'train_time_min': 3,
                        'train_time_max': 8
                ],
                ['RULES_AND_LINEAR', 1, 10]: [
                        'train_time_min': 3,
                        'train_time_max': 8
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
                    } else if (line.numRows) {
                        interval = datasetValues[[Integer.parseInt(line.numRows), Integer.parseInt(line.numCols)]]
                        testCaseKey = 'dataset-size'
                        testCaseValue = "${line.numRows}x${line.numCols}"
                    } else if (line.model_type) {
                        interval = datasetValues[[line.model_type, Integer.parseInt(line.min_rule_length), Integer.parseInt(line.max_rule_length)]]
                        testCaseKey = 'rulefit_type-tree_depths'
                        testCaseValue = "${line.model_type}:${line.min_rule_length},${line.max_rule_length}"    
                    } else {
                        error "Cannot find usable key to get expected interval. Supported keys are backend, ntrees, solver, numRows, model_type. Line: ${line}"
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
