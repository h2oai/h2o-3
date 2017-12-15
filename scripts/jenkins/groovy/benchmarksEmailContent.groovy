import groovy.text.Template
import groovy.text.markup.MarkupTemplateEngine

def call(final failures, final helpers) {
    def build = currentBuild.rawBuild

    def warningsContent = ''
    failures.eachWithIndex { failure, index ->
        warningsContent += """
            tr('style':'${index % 2 == 0 ? helpers.TR_EVEN_STYLE : helpers.TR_ODD_STYLE}') {
                td('style':'${helpers.TD_TH_STYLE}${helpers.RESULT_INDICATOR_WARNING_STYLE}') 
                td('style':'${helpers.TD_TH_STYLE}', '${failure.column}') 
                td('style':'${helpers.TD_TH_STYLE}', '${failure.dataset.capitalize()} ${failure.ntrees} trees') 
                td('style':'${helpers.TD_TH_STYLE}', '${failure.value}') 
                td('style':'${helpers.TD_TH_STYLE}', '${failure.min}') 
                td('style':'${helpers.TD_TH_STYLE}', '${failure.max}') 
            }"""
    }

    MarkupTemplateEngine engine = helpers.getDefaultTemplateEngine()

    String bodyContent = """
${helpers.headerDiv(build, helpers.RESULT_WARNING)}
div('style':'border: 1px solid gray;padding: 1em 1em 1em 1em;') {
    ${helpers.commonSections(build)}
    p('style':'${helpers.SECTION_HEADER_STYLE}', 'Warnings')
    div('style':'${helpers.SECTION_CONTENT_STYLE}') {
        table('style':'width: 80%;border-collapse: collapse;') {
            thead {
                tr {
                    th('style':'${helpers.TD_TH_STYLE}')
                    th('style':'${helpers.TD_TH_STYLE}', 'Column')
                    th('style':'${helpers.TD_TH_STYLE}', 'Test Case')
                    th('style':'${helpers.TD_TH_STYLE}', 'Value')
                    th('style':'${helpers.TD_TH_STYLE}', 'Min')
                    th('style':'${helpers.TD_TH_STYLE}', 'Max')
                }
            }
            tbody {
                ${warningsContent}
            }
        }
    }
}"""
    String templateString = helpers.getTemplateTextForBody(bodyContent)

    Template template = engine.createTemplate(templateString)
    return template.make().toString()
}

return this