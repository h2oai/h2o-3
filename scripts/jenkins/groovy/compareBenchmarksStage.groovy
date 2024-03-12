def call(final pipelineContext, final stageConfig, final benchmarkFolderConfig) {
    // use scripts/benchmark_time_analysis.R to refresh these values
    def EXPECTED_VALUES = [
        'gbm': [
            'paribas': [
                50: [
                    'train_time_min':  5,
                    'train_time_max': 10
                ],
                200: [
                    'train_time_min': 26,
                    'train_time_max': 32
                ]
            ],
            'homesite': [
                50: [
                    'train_time_min': 8,
                    'train_time_max': 11
                ],
                200: [
                    'train_time_min': 35,
                    'train_time_max': 40
                ]
            ],
            'redhat': [
                50: [
                    'train_time_min': 21,
                    'train_time_max': 26
                ],
                200: [
                    'train_time_min': 90,
                    'train_time_max': 115
                ]
            ],
            'springleaf': [
                50: [
                    'train_time_min': 52,
                    'train_time_max': 58
                ],
                200: [
                    'train_time_min': 475,
                    'train_time_max': 568
                ]
            ],
            'higgs': [
                50: [
                    'train_time_min': 72,
                    'train_time_max': 77
                ],
                200: [
                    'train_time_min': 345,
                    'train_time_max': 390
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
                    'train_time_min': 37,
                    'train_time_max': 48
                ],
                IRLSM: [
                    'train_time_min': 68,
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
                    'train_time_min': 55,
                    'train_time_max': 65
                ],
                IRLSM: [
                    'train_time_min': 150,
                    'train_time_max': 173
                ]
            ]
        ],
        'xgb': [
            'airlines-1m': [
                [100, "cpu"]: [
                    'train_time_min': 6,
                    'train_time_max': 15
                ],
                [100, "ext"]: [
                    'train_time_min': 10,
                    'train_time_max': 15
                ],
                [100, "gpu"]: [
                    'train_time_min': 4,
                    'train_time_max': 26
                ]
            ],
            'airlines-10m': [
                [100, "cpu"]: [
                    'train_time_min': 54,
                    'train_time_max': 78
                ],
                [100, "ext"]: [
                    'train_time_min': 65,
                    'train_time_max': 80
                ],
                [100, "gpu"]: [
                    'train_time_min': 10,
                    'train_time_max': 40
                ]
            ],
            'higgs': [
                [100, "cpu"]: [
                    'train_time_min': 123,
                    'train_time_max': 143
                ],
                [100, "ext"]: [
                    'train_time_min': 123,
                    'train_time_max': 143
                ],
                [100, "gpu"]: [
                    'train_time_min': 15,
                    'train_time_max': 37
                ]
            ],
            'cox2': [
                [10, "cpu"]: [
                    'train_time_min': 800,
                    'train_time_max': 960
                ]
            ],
            'cox2-20m': [
                [10, "cpu"]: [
                    'train_time_min': 170,
                    'train_time_max': 190
                ],
                [10, "ext"]: [
                    'train_time_min': 176,
                    'train_time_max': 199
                ]
            ]
        ],
        'xgb-vanilla': [
            'airlines-1m': [
                100: [
                    'train_time_min': 1,
                    'train_time_max': 4
                ]
            ],
            'airlines-10m': [
                100: [
                    'train_time_min': 30,
                    'train_time_max': 52
                ]
            ],
            'higgs': [
                100: [
                    'train_time_min': 50,
                    'train_time_max': 65
                ]
            ]
        ],
        'xgb-dmlc': [
            'airlines-1m': [
                100: [
                    'train_time_min': 5,
                    'train_time_max': 9
                ]
            ],
            'airlines-10m': [
                100: [
                    'train_time_min': 61,
                    'train_time_max': 78
                ]
            ],
            'higgs': [
                100: [
                    'train_time_min': 111,
                    'train_time_max': 133
                ]
            ]
        ],
        'merge': [
            'fileSize100millionRows2ColsallxyTF': [
                [100000000, 2]: [
                    'train_time_min': 30,
                    'train_time_max': 37
                ]
            ],
            'fileSize10millionRows2ColsallxyTF': [
                [10000000, 2]: [
                    'train_time_min': 4,
                    'train_time_max': 10
                ]
            ],
            'fileSize100millionRows2ColsallxyFF': [
                [100000000, 2]: [
                    'train_time_min': 25,
                    'train_time_max': 37
                ]
            ],
            'fileSize10millionRows2ColsallxyFF': [
                [10000000, 2]: [
                    'train_time_min': 4,
                    'train_time_max': 10
                ]
            ]
        ],
        'sort': [
            'fileSize100millionRows2Cols': [
                [100000000, 2]: [
                    'train_time_min': 8,
                    'train_time_max': 14
                ]
            ],
            'fileSize10millionRows2Cols': [
                [10000000, 2]: [
                    'train_time_min': 2,
                    'train_time_max': 7
                ]
            ]
        ],
        'rulefit': [
            'redhat': [
                ['RULES_AND_LINEAR', 3, 3]: [
                        'train_time_min': 40,
                        'train_time_max': 50
                ]
            ],
            'homesite': [
                ['RULES_AND_LINEAR', 3, 3]: [
                        'train_time_min': 9,
                        'train_time_max': 15
                ]
            ],
            'springleaf': [
                ['RULES_AND_LINEAR', 3, 3]: [
                        'train_time_min': 90,
                        'train_time_max': 96
                ]
            ],
            'paribas': [
                ['RULES_AND_LINEAR', 3, 3]: [
                        'train_time_min': 11,
                        'train_time_max': 16
                ]
            ],
            'higgs': [
                ['RULES_AND_LINEAR', 3, 3]: [
                        'train_time_min': 22,
                        'train_time_max': 27
                ]
            ]
        ]    
    ]
    // this is a starting point: we need to collect enough data to establish reasonable ranges first
    // for the lower bound we just use 0, for the upper bound we use the upper bound of the regular 'gbm' benchmark
    EXPECTED_VALUES['gbm-noscoring'] = new LinkedHashMap<String, LinkedHashMap<Serializable, LinkedHashMap<String, Integer>>>()
    EXPECTED_VALUES['gbm'].each { dataset, cases ->
        EXPECTED_VALUES['gbm-noscoring'][dataset] = [
                50: [
                        train_time_min: 0,
                        train_time_max: cases[50].train_time_max
                ],
                200: [
                        train_time_min: 0,
                        train_time_max: cases[200].train_time_max
                ]
        ]
    }

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
                            def lineValueFormatted = new java.text.DecimalFormat("#.#").format(lineValue)
                            echo "Check failed. Value ${lineValueFormatted}s not in [${minValue}s..${maxValue}s]. "
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
