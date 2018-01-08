import groovy.text.markup.MarkupTemplateEngine
import groovy.text.markup.TemplateConfiguration

RESULT_SUCCESS = 'success'
RESULT_WARNING = 'warning'
RESULT_FAILURE = 'failure'

TR_EVEN_STYLE = 'background: #fff'
TR_ODD_STYLE = 'background: #ccc'
TD_TH_STYLE = 'border: 1px solid #aaa; padding: 0.5em; text-align: left;'

RESULT_INDICATOR_SUCCESS_STYLE = 'width: 10px; background-color: rgb(0, 255, 0);'
RESULT_INDICATOR_FAILURE_STYLE = 'width: 10px; background-color: rgb(255, 0, 0);'
RESULT_INDICATOR_WARNING_STYLE = 'width: 10px; background-color: rgb(244, 146, 66);'

SECTION_HEADER_STYLE = 'font-weight:bold; font-size:16pt;'
SECTION_CONTENT_STYLE = 'margin-left: 15px; padding-bottom: 15px;'

PIPELINE_ALWAYS_RECIPIENTS = ['michalr@h2o.ai']
PIPELINE_FAILURE_RECIPIENTS = ['michalk@h2o.ai'] + PIPELINE_ALWAYS_RECIPIENTS

GString headerDiv(build, result) {
    def LOGO_URL = 'https://pbs.twimg.com/profile_images/501572396810129408/DTgFCs-n.png'
    def DEFAULT_BACKGROUND_COLOR = '#f8433c'
    def BACKGROUND_COLORS = [
            (RESULT_SUCCESS): '#84e03e',
            (RESULT_FAILURE): '#f8433c',
            (RESULT_WARNING): '#f49242'
    ]
    def backgroundColor = BACKGROUND_COLORS[result.toLowerCase()]
    if (backgroundColor == null) {
        backgroundColor = DEFAULT_BACKGROUND_COLOR
    }

    return """
div('style':'box-shadow: 0px 5px 5px #aaaaaa;background-color: #424242;color: white;') {
    div('style':'display: table;overflow: hidden;height: 150px;') {
        div('style':'display: table-cell;vertical-align: middle;padding-left: 1em;') {
            div {
                img('width':'80', 'height':'80', 'alt':'H2O.ai', 'title':'H2O.ai', 'style':'vertical-align:middle;', 'src':'${LOGO_URL}')
                a('style':'vertical-align:middle;color: white;font-size: 20pt;font-weight: bolder;margin-left: 20px;', 'href':'${build.getAbsoluteUrl()}', "${URLDecoder.decode(env.JOB_NAME, "UTF-8")} #${build.number} - ${result}")
            }
        }
    }
    div('style':'height: 15px; background-color:${backgroundColor}') {
    }
}
"""
}

GString createSection(final title, final content) {
    return """
        p('style':'${SECTION_HEADER_STYLE}', '${title}')
        div('style':'${SECTION_CONTENT_STYLE}') {
            ${content}
        }
    """
}

GString detailsSection(build) {
    def content =  """
        ul {
            li('Duration: Started at <strong>${new Date(build.getStartTimeInMillis())}</strong>')
            li('Branch: <strong>${env.BRANCH_NAME}</strong>')
            li('SHA: <strong>${env.GIT_SHA}</strong>')
        }
    """
    return createSection('Details', content)
}

String changesSection(build) {
    def REPO_URL = 'https://github.com/h2oai/h2o-3'

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
        def content = """
            ul {
                ${changesContent}
            }
        """
        changesSection = createSection('Changes', content)
    }
    return changesSection
}

GString stagesSection(build) {
    GString content =  """
        table('style':'width: 80%;border-collapse: collapse;') {
            thead {
                tr {
                    th('style':'${TD_TH_STYLE}')
                    th('style':'${TD_TH_STYLE}', 'Stage')
                    th('style':'${TD_TH_STYLE}', 'Result')
                }
            }
            tbody {
                ${getStagesContent(build)}
            }
        }
    """
    return createSection('Stages Overview', content)
}

GString commonSections(build) {
    return """
${detailsSection(build)}
${changesSection(build)}
"""
}

GString getTemplateTextForBody(final String bodyContent) {
    return """
html {
    body {
        ${bodyContent}
    }
}"""
}

MarkupTemplateEngine getDefaultTemplateEngine() {
    TemplateConfiguration config = new TemplateConfiguration()
    config.setAutoNewLine(true)
    config.setAutoIndent(true)
    return new MarkupTemplateEngine(config)
}

List getRelevantPipelineRecipients(result) {
    if (result.toLowerCase() == RESULT_SUCCESS) {
        return PIPELINE_ALWAYS_RECIPIENTS
    }
    return PIPELINE_FAILURE_RECIPIENTS
}

private String getStagesContent(build) {
    def stages = []
    def STAGE_START_TYPE_DISPLAY_NAME = 'Stage : Body : End'
    def buildNodes = build.getAction(org.jenkinsci.plugins.workflow.job.views.FlowGraphAction.class).getNodes().findAll {
        it.getTypeDisplayName() == STAGE_START_TYPE_DISPLAY_NAME
    }
    buildNodes.each {
        stages << ['name': it.getStartNode().getDisplayName(), 'result': boolToSuccess(it.getError() == null).toLowerCase().capitalize()]
    }
    def stagesContent = ''
    stages.eachWithIndex { stage, index ->
        echo stage.result
        stagesContent += """
            tr('style':'${index % 2 == 0 ? TR_EVEN_STYLE : TR_ODD_STYLE}') {
                td('style':'${TD_TH_STYLE}${stage.result.toLowerCase() == RESULT_SUCCESS ? RESULT_INDICATOR_SUCCESS_STYLE : RESULT_INDICATOR_FAILURE_STYLE}')
                td('style':'${TD_TH_STYLE}', '${stage.name}')
                td('style':'${TD_TH_STYLE}', '${stage.result}')
            }"""
    }
    return stagesContent
}

private def boolToSuccess(final boolean value) {
    if (value) {
        return RESULT_SUCCESS
    }
    return RESULT_FAILURE
}

return this