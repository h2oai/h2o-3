import groovy.text.markup.MarkupTemplateEngine
import groovy.text.markup.TemplateConfiguration
import groovy.text.markup.MarkupTemplateEngine

GString headerDiv(build, result) {
    def LOGO_URL = 'https://pbs.twimg.com/profile_images/501572396810129408/DTgFCs-n.png'

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
    div('style':'height: 15px; ${result.toLowerCase() == 'success' ? 'background-color: #84e03e;': 'color: white;background-color: #f8433c;'}') {
    }
}
"""
}

GString detailsSection(headerStyle, contentStyle, build) {
    return """
p('style':'${headerStyle}', 'Details')
div('style':'${contentStyle}') {
    ul {
        li('Duration: Started at <strong>${new Date(build.getStartTimeInMillis())}</strong>')
        li('Branch: <strong>${env.BRANCH_NAME}</strong>')
        li('SHA: <strong>${env.GIT_SHA}</strong>')
    }
}
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

def boolToSuccess(final boolean value) {
    if (value) {
        return 'SUCCESS'
    }
    return 'FAILURE'
}


return this