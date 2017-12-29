import groovy.text.markup.MarkupTemplateEngine
import groovy.text.Template
import groovy.text.markup.MarkupTemplateEngine

def call(final String result, helpers) {

    def build = currentBuild.rawBuild

    MarkupTemplateEngine engine = helpers.getDefaultTemplateEngine()
    GString releaseContent = """
        ul {
            li('Version: <strong>${env.PROJECT_VERSION}</strong>')
            li('Release Node: <strong>${env.BUILT_ON_NODE}</strong>')
            li('Nexus Upload: <strong>${strBoolToYesNo(env.UPLOAD_NEXUS)}</strong>')
            li('PyPI Upload: <strong>${strBoolToYesNo(env.UPLOAD_PYPI)}</strong>')
            li('Conda Upload: <strong>${strBoolToYesNo(env.UPLOAD_CONDA)}</strong>')
        }
    """

    String bodyContent = """
${helpers.headerDiv(build, result)}
div('style':'border: 1px solid gray;padding: 1em 1em 1em 1em;') {
    ${helpers.commonSections(build)}
    ${helpers.createSection('Release', releaseContent)}
    ${helpers.stagesSection(build)}
}"""
    String templateString = helpers.getTemplateTextForBody(bodyContent)

    Template template = engine.createTemplate(templateString)
    return template.make().toString()
}

def strBoolToYesNo(final String value) {
    return value.toLowerCase() == 'true' ? 'Yes' : 'No'
}

return this