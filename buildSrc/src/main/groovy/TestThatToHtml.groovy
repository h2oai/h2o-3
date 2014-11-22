package water.gradle.plugins
import org.gradle.api.*
import org.gradle.api.plugins.*
import groovy.xml.MarkupBuilder
import java.util.regex.Matcher

apply plugin: 'convertR2html'

dependencies {
    compile gradleApi()
    compile localGroovy()
}

class convertR2html implements Plugin<Project> {
    def eol = System.getProperty('line.separator')

    def styleDefinitions = {
        def writer = new StringWriter()
        new File("src/test/resources/testHtmlTemplates/style.txt").eachLine{
            writer << it << eol
        }
        writer.toString()
    }

    def scriptDefinitions = {
        def writer = new StringWriter()
        new File("src/test/resources/testHtmlTemplates/script.txt").eachLine{
            writer << it << eol
        }
        writer.toString()
    }

    def testsSummary = {
        def q = { "'" + it + "'"}
        def writer = new StringWriter()
        writer << "Show" << eol
        [0:"Summary", 1:"Failed", 2:"All"].each{k,v->
            def js = "javascript:showCase(${k.toString()})"
            writer << "<a href=${q(js)}>${v}</a>" << eol
        }
        writer.toString()
    }

    def html4test = {fileName ->
        def writer = new StringWriter()
        def xml = new MarkupBuilder(writer)
        def firstLines = [
        """<?xml version="1.0" encoding="UTF-8"?>""",
        """<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">"""]
        xml.'html'('xmlns':'http://www.w3.org/1999/xhtml'){
            head{
                mkp.yieldUnescaped( styleDefinitions() )
            }
            body{
                mkp.yieldUnescaped(scriptDefinitions())

                div(class: 'heading'){
                    h1("Unit test report")
                    //TODO: add strong as in <p class='attribute'><strong>Start Time:</strong> 2014-11-14 12:19:45</p>
                    p(class: 'attribute', "Start Time: " + "2014-11-14 12:19:45" )
                    p(class: 'attribute', "Duration: " + "0:00:00.105188")
                    p(class: 'attribute', "Status: " + "Pass 3")
                    p(class: 'description')
                }

                p( id:'show_detail_line'){
                    a(href:'javascript:showCase(0)', "Summary")
                    a(href:'javascript:showCase(1)', "Failed")
                    a(href:'javascript:showCase(2)', "All")
                }
                table(id:'result_table'){
                    colgroup{
                        col(align:'left')
                        col(align:'right')
                        col(align:'right')
                        col(align:'right')
                        col(align:'right')
                        col(align:'right')
                        col(align:'right')
                    }
                    tr(id:'header_row'){
                        td("Test Group/Test Case")
                        td("Time")
                        td("Count")
                        td("Pass")
                        td("Fail")
                        td("Error")
                        td("View")
                    }
                    tr(class:'passClass'){
                        td("proboscis.case:MethodTest")
                        td("0.00")
                        td("3")
                        td("3")
                        td("0")
                        td("0")
                        td{
                            a(href:"javascript:showClassDetail('c1',3)", "Detail")
                        }
                    }
                    tr(id:'pt1.1', class:'hiddenRow'){
                        td(class:'none'){
                            div(class:'testcase', 'testD')
                        }
                        td("0.00")
                        td(colspan:'5', align:'center', 'pass')
                    }
                    tr(id:'pt1.2', class:'hiddenRow'){
                        td(class:'none'){
                            div(class:'testcase', 'testE')
                        }
                        td("0.00")
                        td(colspan:'5', align:'center', 'pass')
                    }
                    tr(id:'pt1.3', class:'hiddenRow'){
                        td(class:'none'){
                            div(class:'testcase', 'testF')
                        }
                        td("0.00")
                        td(colspan:'5', align:'center', 'pass')
                    }
                    tr(id: 'total_row'){
                        td("Total")
                        td("0.00")
                        td("3")
                        td("3")
                        td("0")
                        td("0")
                        td( mkp.yieldUnescaped("&nbsp;") )
                    }
                }
                div(id: 'ending')
            }
        }
        def out = new File(fileName)
        out.write('')
        firstLines.each{ out << it << eol}
        out << writer.toString() << eol
    }

    void apply(Project project){
        project.task('reportAcceptanceTests', dependsOn: 'prepareAcceptanceHtmlReports') << {
            new File("build/reports/site").mkdirs()

            def folders = project.ext.R_test_folders as List<String>
            def testMarker = project.ext.Regex_test_marker as java.util.regex.Pattern
            logger.info " ** Looking into ${folders.toString()}"
            def pickResults = {from->
                def answer = []
                def getTests = {
                    def what = new File(it).list([accept:{d, f-> f ==~ testMarker }] as FilenameFilter)
                    (what == null)?[]:what.toList()
                }
                from.each{
                    getTests(it).each{item->
                        answer.add(new Tuple<String,String>(it, item))
                    }
                }
                answer
            }

            def testResults = [:]
            /*will end up being something like
            [
            test1:[tests:5, failures:2, errors:1, skipped:2],
            test2:[tests:7, failures:0, errors:0, skipped:0]
            ]
            */

            def processTestResults = {file, into ->
                logger.warn "Processing test results from " + file.getCanonicalPath()
                def lines = file as String[]
                /*  we care here about two kind of lines
                Basic tests : ........
                or
                Basic Tests : ......1....
                and then we look for two lines like:
                1. Failure(@test-colour.r#17): We have colours if we want to -------------------
                c1 isn't true
                */
                def summaryPattern = ~/(\w[^:]+)\s*:\s*(.*)/
                def detailPattern = ~/(.)\.\s*(.+)\(@.+\.r#\d*\):\s*(.+)/
                def exclusions = ["Warning messages"] as Set
                def tempBuffer = []
                def buffer = []
                lines.eachWithIndex{line, i->
                    logger.warn line
                    if ( 0 == line.trim().length() ){
                        if ( tempBuffer.size() > 0 ){
                            buffer << tempBuffer.clone()
                            tempBuffer.clear()
                        }
                    }
                    else{
                        tempBuffer << line.trim()
                        logger.warn "adding to temp: " + tempBuffer.toString()
                    }
//                    Matcher m1 = (line =~ summaryPattern)
//                    Matcher m2 = (line =~ detailPattern)
//                    if ( m1.matches() ){
//                        if ( !exclusions.contains(m1[0][1]) ){
//                            //println " * ${m1[0][1]} : ${m1[0][2]}"
//                        }
//                    }
//                    if ( m2.matches() )println "#" + line
                }
                if ( tempBuffer.size() > 0 ) buffer << tempBuffer
/*
                --
                Basic tests : .......
                Functional tests : ....12.
                Integration tests : .......
                Smoke tests : .......
                --
                1. Failure(@test-functional.r#14): equality holds ------------------------------
                5 not equal to 6
                Mean relative difference: 0.1666667
                --
                2. Failure(@test-functional.r#15): equality holds ------------------------------
                10 is not identical to 11. Differences:
                Mean relative difference: 0.09090909
*/
                //populate 'into' structure

            }

            pickResults(folders).each{tuple->
                //TODO: what's happening with testResults if we digest multiple files?
                processTestResults(
                    new File([tuple.get(0), tuple.get(1)].join(System.getProperty('file.separator'))),
                    testResults
                )
            }

            def reportLocation = new File("build/seeme.html").getCanonicalPath()
            html4test(reportLocation)

            def links = ""
            def details = ""
            def q = { "\"" + it + "\""}
            [tryIt:[tests:5, failures:2, errors:1, skipped:2, resultsHtml: reportLocation]].each{k,v->
                def href = q(v['resultsHtml'])
                def title= q(k)
                links += "<a href=${href} title=${title}>$k</a>\n"
                details +=
                        "<pre>" +
                        "<b>${k}<b/><br />Tests: ${v['tests']}<br />" +
                        "Failures: ${v['failures']}<br />" +
                        "Errors: ${v['errors']}<br />" +
                        "Skipped: ${v['skipped']}" +
                        "</pre>\n"
            }
            //ant copy seems to choke on token substitution if binary files are included. need to go in two passes
            ant.copy(toDir: "build/reports/site", filtering: true, force: true, overwrite: true){
                fileset(dir: "src/test/resources/site"){
                    include(name: "**/*.html")
                }
                filterset(){
                    filter(token: "test_links", value: links)
                    filter(token: "test_results_details", value: details)
                }
            }   //copy with filtering has some issues copying some of the files, so two passes here.
            ant.copy(toDir: "build/reports/site", filtering: false, force: true, overwrite: false){
                fileset(dir: "src/test/resources/site"){
                    exclude(name: "**/*.html")
                }
            }
        }
    }
}

