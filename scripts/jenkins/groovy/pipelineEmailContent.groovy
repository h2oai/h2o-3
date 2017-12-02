import hudson.tasks.test.AbstractTestResultAction
import groovy.text.markup.MarkupTemplateEngine
import groovy.text.Template
import groovy.text.markup.MarkupTemplateEngine

def call(final String result, helpers) {

    def build = currentBuild.rawBuild

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
    def stagesContent = ''
    stages.eachWithIndex { stage, index ->
        stagesContent += """
            tr('style':'${index % 2 == 0 ? helpers.TR_EVEN_STYLE : helpers.TR_ODD_STYLE}') {
                td('style':'${helpers.TD_TH_STYLE}${stage['result'] == helpers.RESULT_SUCCESS ? helpers.RESULT_INDICATOR_SUCCESS_STYLE : helpers.RESULT_INDICATOR_FAILURE_STYLE}') 
                td('style':'${helpers.TD_TH_STYLE}', '${stage['name']}') 
                td('style':'${helpers.TD_TH_STYLE}', '${stage['result'].capitalize()}') 
            }"""
    }

    MarkupTemplateEngine engine = helpers.getDefaultTemplateEngine()

    String bodyContent = """
${helpers.headerDiv(build, result)}
div('style':'border: 1px solid gray;padding: 1em 1em 1em 1em;') {
    ${helpers.commonSections(build)}
    p('style':'${helpers.SECTION_HEADER_STYLE}', 'Stages Overview')
    div('style':'${helpers.SECTION_CONTENT_STYLE}') {
        table('style':'width: 80%;border-collapse: collapse;') {
            thead {
                tr {
                    th('style':'${helpers.TD_TH_STYLE}')
                    th('style':'${helpers.TD_TH_STYLE}', 'Stage')
                    th('style':'${helpers.TD_TH_STYLE}', 'Result')
                }
            }
            tbody {
                ${stagesContent}
            }
        }
    }
    p('style':'${helpers.SECTION_HEADER_STYLE}', 'Tests Overview')
    div('style':'${helpers.SECTION_CONTENT_STYLE}') {
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