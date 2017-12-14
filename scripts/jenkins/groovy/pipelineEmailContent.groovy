import hudson.tasks.test.AbstractTestResultAction
import groovy.text.markup.MarkupTemplateEngine
import groovy.text.Template
import groovy.text.markup.MarkupTemplateEngine

def call(final String result, helpers) {
    def REPO_URL = 'https://github.com/h2oai/h2o-3'
    
    def TR_EVEN_STYLE = 'background: #fff'
    def TR_ODD_STYLE = 'background: #ccc'
    def TD_TH_STYLE = 'border: 1px solid #aaa; padding: 0.5em; text-align: left;'
    def RESULT_INDICATOR_SUCCESS_STYLE = 'width: 10px; background-color: rgb(0, 255, 0);'
    def RESULT_INDICATOR_FAILURE_STYLE = 'width: 10px; background-color: rgb(255, 0, 0);'
    
    def SECTION_HEADER_STYLE = 'font-weight:bold; font-size:16pt;'
    def SECTION_CONTENT_STYLE = 'margin-left: 15px; padding-bottom: 15px;'

    def build = currentBuild.rawBuild

    def changesContent = ''
    build.getChangeSets().each { changeSetList ->
        if (changeSetList.getBrowser().getRepoUrl() == REPO_URL) {
            changeSetList.each { changeSet ->
                changesContent += "li('<a href=\"${REPO_URL}/commit/${changeSet.getRevision()}\"><strong>${changeSet.getRevision().substring(0, 8)}</strong></a> by <strong>${changeSet.getAuthorEmail()}</strong> - ${changeSet.getMsg()}')"
            }
        }
    }
    def changesSection = ''
    if (changesContent != '') {
        changesSection = """p('style':'${SECTION_HEADER_STYLE}', 'Changes')
        div('style':'${SECTION_CONTENT_STYLE}') {
            ul {
                ${changesContent}
            }
        }"""
    }

    def testResultsAction = build.getAction(AbstractTestResultAction.class)
    def testsContent = "p('No tests were run.')"
    def failedTests = null
    if (testResultsAction != null) {
        failedTests = testResultsAction.getFailedTests()
        if (failedTests.isEmpty()) {
            testsContent = "p('All tests passed!')"
        } else {
            testsContent = '''ul {
      failedTests.each {
        fragment("li(failedTest)", failedTest:it.getFullDisplayName())
      }
    }'''
        }
    }

    def stages = []
    def STAGE_START_TYPE_DISPLAY_NAME = 'Stage : Body : End'
    def buildNodes = build.getAction(org.jenkinsci.plugins.workflow.job.views.FlowGraphAction.class).getNodes().findAll {
        it.getTypeDisplayName() == STAGE_START_TYPE_DISPLAY_NAME
    }
    buildNodes.each {
        stages << ['name': it.getStartNode().getDisplayName(), 'result': helpers.boolToSuccess(it.getError() == null).toLowerCase()]
    }
    stagesContent = ''
    stages.eachWithIndex { stage, index ->
        stagesContent += """
            tr('style':'${index % 2 == 0 ? TR_EVEN_STYLE : TR_ODD_STYLE}') {
                td('style':'${TD_TH_STYLE}${stage['result'] == 'success' ? RESULT_INDICATOR_SUCCESS_STYLE : RESULT_INDICATOR_FAILURE_STYLE}') 
                td('style':'${TD_TH_STYLE}', '${stage['name']}') 
                td('style':'${TD_TH_STYLE}', '${stage['result'].capitalize()}') 
            }"""
    }

    MarkupTemplateEngine engine = helpers.getDefaultTemplateEngine()

    String bodyContent = """
${helpers.headerDiv(build, result)}
div('style':'border: 1px solid gray;padding: 1em 1em 1em 1em;') {
    ${helpers.detailsSection(SECTION_HEADER_STYLE, SECTION_CONTENT_STYLE, build)}
    ${changesSection}
    p('style':'${SECTION_HEADER_STYLE}', 'Stages Overview')
    div('style':'${SECTION_CONTENT_STYLE}') {
        table('style':'width: 80%;border-collapse: collapse;') {
            thead {
                tr {
                    th('style':'${TD_TH_STYLE}')
                    th('style':'${TD_TH_STYLE}', 'Stage')
                    th('style':'${TD_TH_STYLE}', 'Result')
                }
            }
            tbody {
                ${stagesContent}
            }
        }
    }
    p('style':'${SECTION_HEADER_STYLE}', 'Tests Overview')
    div('style':'${SECTION_CONTENT_STYLE}') {
        ${testsContent}
    }
}"""
    String templateString = helpers.getTemplateTextForBody(bodyContent)
    Map<String, Object> model = new HashMap<>([:])
    model['failedTests'] = failedTests
    model['stages'] = stages

    Template template = engine.createTemplate(templateString)
    return template.make(model).toString()
}

return this