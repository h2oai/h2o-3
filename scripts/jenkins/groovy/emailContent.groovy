import hudson.tasks.test.AbstractTestResultAction
import groovy.text.markup.MarkupTemplateEngine
import groovy.text.markup.TemplateConfiguration
import groovy.text.Template

def call() {
    def LOGO_URL = 'https://h2o2016.wpengine.com/wp-content/themes/h2o2016/images/H2O_logo_yellow.svg'
    def REPO_URL = 'https://github.com/h2oai/h2o-3'

    def result = currentBuild.currentResult
    def build = currentBuild.rawBuild

    def changesContent = ''
    build.getChangeSets().each { changeSetList ->
        if (changeSetList.getBrowser().getRepoUrl() == REPO_URL) {
            changeSetList.each { changeSet ->
                changesContent += "li('<a href=\"${REPO_URL}/commit/${changeSet.getRevision()}\"><strong>${changeSet.getRevision().substring(0, 8)}</strong></a> by <strong>${changeSet.getAuthorEmail()}</strong> - ${changeSet.getMsg()}')"
            }
        }
    }

    testResultsAction = build.getAction(AbstractTestResultAction.class)
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
    stages.each { stage ->
        stagesContent += """
            tr('class':'row') {
                td('class':'result-indicator ${stage['result']}') 
                td('${stage['name']}') 
                td('${stage['result'].capitalize()}') 
            }"""
    }

    TemplateConfiguration config = new TemplateConfiguration()
    config.setAutoNewLine(true)
    config.setAutoIndent(true)
    MarkupTemplateEngine engine = new MarkupTemplateEngine(config)
    String templateString = """
html {
    head {
        style('type':'text/css', '''
              .summary {
                  box-shadow: 0px 5px 5px #aaaaaa;
                  background-color: #424242;
                  color: white;
                }
            
                .summary-header {
                  display: table;
                  overflow: hidden;
                  height: 150px;
                }
            
                .summary-header-content {
                  display: table-cell;
                  vertical-align: middle;
                  padding-left: 1em;
                }
            
                .summary-result.failure {
                  color: white;
                  background-color: #f8433c;
                }
            
                .summary-result.success {
                  background-color: #84e03e;
                }
            
                .summary-header a {
                  color: white;
                  text-decoration: underline;
                  display: inline-block;
                  font-size: 20pt;
                  font-weight: bolder;
                  line-height: 82px;
                  vertical-align: middle;
                  background-image: url(${LOGO_URL});
                  background-repeat: no-repeat;
                  padding-left: 92px;
                }
            
                .summary-result {
                  height: 50px;
                }
            
                .content {
                  border: 1px solid gray;
                  padding: 1em 1em 1em 1em;
                }
            
                .section-header {
                  font-weight: bold;
                  font-size: 16pt;
                }
            
                .section-content {
                  margin-left: 15px;
                  padding-bottom: 15px;
                }
            
                tr:nth-child(even) {
                  background: #CCC
                }
            
                tr:nth-child(odd) {
                  background: #FFF
                }
            
                table {
                  width: 80%;
                  border-collapse: collapse;
                }
            
                td, th {
                  border: 1px solid #aaa;
                  padding: 0.5em;
                  text-align: left;
                }
            
                .result-indicator {
                  width: 10px;
                }
            
                .result-indicator.failure {
                  background-color: rgb(255, 0, 0);
                }
            
                .result-indicator.success {
                  background-color: rgb(0, 255, 0);
            ''')
    }
        
    body {
        div('class':'summary') {
            div('class':'summary-header') {
                div('class':'summary-header-content') {
                    a('class':'heading', 'href':'${build.getAbsoluteUrl()}', "Build #${build.number} - ${result}")
                }
            }
            div('class':'summary-result ${result.toLowerCase()}') {
            }
        }
        div('class':'content') {
            p('class':'section-header', 'Duration')
            div('class':'section-content') {
                p('Started at <strong>${new Date(build.getStartTimeInMillis())}</strong>')
            }
            p('class':'section-header', 'Changes')
            div('class':'section-content') {
                ul {
                    ${changesContent}
                }
            }
            p(class:'section-header', 'Stages Overview')
            div('class':'section-content') {
                table {
                    thead {
                        tr {
                            th('class':'result-indicator')
                            th('Stage')
                            th('Result')
                        }
                    }
                    tbody {
                        ${stagesContent}
                    }
                }
            }
            p(class:'section-header', 'Tests Overview')
            div('class':'section-content') {
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

return this