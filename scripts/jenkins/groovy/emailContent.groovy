import hudson.tasks.test.AbstractTestResultAction
import groovy.text.markup.MarkupTemplateEngine
import groovy.text.markup.TemplateConfiguration
import groovy.text.Template

def call(final String result) {
    def LOGO_URL = 'https://pbs.twimg.com/profile_images/501572396810129408/DTgFCs-n.png'
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
        stages << ['name': it.getStartNode().getDisplayName(), 'result': boolToSuccess(it.getError() == null).toLowerCase()]
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

    TemplateConfiguration config = new TemplateConfiguration()
    config.setAutoNewLine(true)
    config.setAutoIndent(true)
    MarkupTemplateEngine engine = new MarkupTemplateEngine(config)
    String templateString = """
html {
    body {
        div('style':'box-shadow: 0px 5px 5px #aaaaaa;background-color: #424242;color: white;') {
            div('style':'display: table;overflow: hidden;height: 150px;') {
                div('style':'display: table-cell;vertical-align: middle;padding-left: 1em;') {
                    div {
                        img('width':'80', 'height':'80', 'alt':'H2O.ai', 'title':'H2O.ai', 'style':'vertical-align:middle;', 'src':'${LOGO_URL}')
                        a('style':'vertical-align:middle;color: white;font-size: 20pt;font-weight: bolder;margin-left: 20px;', 'href':'${build.getAbsoluteUrl()}', "${URLDecoder.decode(env.JOB_NAME, "UTF-8")} #${build.number} - ${result}")
                    }
                }
            }
            div('style':'height: 15px; ${result.toLowerCase() == 'success' ? 'background-color: #84e03e;': 'color: white;background-color: #f8433c;'}') {
            }
        }
        div('style':'border: 1px solid gray;padding: 1em 1em 1em 1em;') {
            p('style':'${SECTION_HEADER_STYLE}', 'Details')
            div('style':'${SECTION_CONTENT_STYLE}') {
                ul {
                    li('Duration: Started at <strong>${new Date(build.getStartTimeInMillis())}</strong>')
                    li('Branch: <strong>${env.BRANCH_NAME}</strong>')
                    li('SHA: <strong>${env.GIT_SHA}</strong>')
                }
            }
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
        }
    }
}"""
    Map<String, Object> model = new HashMap<>([:])
    model['failedTests'] = failedTests
    model['stages'] = stages

    Template template = engine.createTemplate(templateString)
    return template.make(model).toString()
}

def boolToSuccess(final boolean value) {
    if (value) {
        return 'SUCCESS'
    }
    return 'FAILURE'
}

def boolToColor(final boolean value) {
    if (value) {
        return ''
    }
    return ''
}

return this