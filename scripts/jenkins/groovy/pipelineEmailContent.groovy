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

    MarkupTemplateEngine engine = helpers.getDefaultTemplateEngine()

    String bodyContent = """
${helpers.headerDiv(build, result)}
div('style':'border: 1px solid gray;padding: 1em 1em 1em 1em;') {
    ${helpers.commonSections(build)}
    ${helpers.stagesSection(build)}
    p('style':'${helpers.SECTION_HEADER_STYLE}', 'Tests Overview')
    div('style':'${helpers.SECTION_CONTENT_STYLE}') {
        ${testsContent}
    }
}"""
    String templateString = helpers.getTemplateTextForBody(bodyContent)

    Template template = engine.createTemplate(templateString)
    return template.make().toString()
}

return this