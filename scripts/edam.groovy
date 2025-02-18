#!/usr/bin/env groovy
/*
 * Copyright 2012 DTO Solutions, Inc. (http://dtosolutions.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
/*
* Author: Greg Schueler <a href="mailto:greg@dtosolutions.com">greg@dtosolutions.com</a>
* Created: 1/16/2012
* edam.groovy - a gouda-like wrapper for pandoc in groovy
* inspired by gouda: http://www.unexpected-vortices.com/sw/gouda/docs/
*/
import static java.util.regex.Pattern.quote
import static java.util.regex.Pattern.compile
import java.nio.file.Paths

flags=[:]
flagDefaults=[pdocextra:[],help:false,doToc:true,doNav:true,tocAsIndex:true,genTocOnly:false,cleanUpAuto:true,verbose:false,recursive:false,preview:false]
flags = new HashMap(flagDefaults)

pagevars=[:]
optionsDefaults=[
    cssRelative: 'true',
    singleIndex:'true',
    chapterNumbers:'true',
    chapterTitle: 'Chapter',
    sectionTitle: 'Section',
    pageFileName:'${name}',
    cssFileName:'styles.css',
    indexFileName:'(index|readme)',
    indexFileOutputName:'index',
    tocFileName:'toc',
    tocTitle:'Table of Contents',
    subTocTitle: 'Sections',
    pandocCmd:"pandoc",
    tokenStart:'${',
    tokenEnd:'}',
    singleOutputFile:'false',
    pageLinkTitle: '${title}',
    subLinkTitle: '${title}',
    recurseDirPattern: '.*',
    recurseDirPatternIgnore: '^\\..*$',
    recurseDepth:'0',
    srcpagelink:'Edit',
    srcbaseurl:'',
    bugpagelink:'Report',
    bugpageurl:'',
    fulltitleseparator:' - ',
    generateSiteJson: "true",
    siteJsonFile:"site.json",
    siteJsonPathSeparator:" / "
    ]
optionDescs=[
    singleIndex: 'true/false, if only a single markdown file, use it as the index HTML file.',
    chapterNumbers:'true/false, use chapter numbers in navigation.',
    chapterTitle:'Localized text to use for a Chapter title.',
    sectionTitle: 'Localized text to use for a Section title',
    pageFileName: 'Template for generated filename for each page, variables: `${index}`,`${title}`,`${name}`.',
    cssFileName:'File name of the css file to link in the HTML header.',
    indexFileName: 'File name base expected as index markdown file.',
    indexFileOutputName: 'File name base used as index HTML file.',
    tocFileName: 'File name expected/generated as table-of-contents file.',
    tocTitle: 'Localized text to use for table of contents title.',
    subTocTitle: 'Localized text to use for subdirectory sections table of contents title.',
    pandocCmd: 'Pandoc command to run.',
    tokenStart:'Variable expansion start token.',
    tokenEnd:'Variable expansion end token.',
    singleOutputFile:'true/false, combine all files into one output file.',
    pageLinkTitle: 'Template for title for links to the page, variables: `${index}`,`${title}`,`${name}`.',
    subLinkTitle: 'Template for title for links to a sub dir section, variables: `${sectionTitle}`, `${title}`, `${index}`, `${name}`',
    recurseDirPattern: 'Regex to match dirs to include in recursive generation.',
    recurseDirPatternIgnore: 'Regex to match dirs to ignore in recursive generation.',
    recurseDepth: 'Number of dirs to recurse into. -1 means no limit.',
    srcpagelink:'Link content to edit page source',
    srcbaseurl:'Base URL to link to edit this page.',
    bugpagelink:'Text for issue report link',
    bugpageurl:'Link to report issue for the page',
    fulltitleseparator:'Separator string for generating full page link titles',
    generateSiteJson:'true/false, generate a site.json with relative links and page titles',
    siteJsonFile:"Filename for site.json, default: site.json",
    siteJsonPathSeparator: "Separator string for site json full path titles"

    ]
    
options = new HashMap(optionsDefaults)

File.metaClass{
    getBasename = { delegate.name.replaceAll(/\.([^.]+)$/,'') }
    getTitle = {
        def title
        if(delegate.isFile()){
            delegate.withReader { reader ->
                def match=reader.readLine()=~/^[%#]\s+(.+)$/
                if(match.matches()){
                    title=match.group(1).trim()
                }
            }

            if(!title){
                throw new RuntimeException("Could not read title from file: ${delegate}")
            }
        } else{
            title = delegate.basename.replaceAll(/[_\.-]/,' ').replaceAll(/^(.)/,{a,m->m.toUpperCase()})
        } 
        return title
    }
}
String.metaClass{
    replaceTokens = {Map params,String tokenStart='${', String tokenEnd='}'->
        delegate.replaceAll('('+quote(tokenStart)+'([a-zA-Z_0-9.-]+?)'+quote(tokenEnd)+')',{all,m1,m2->
                null!=params[2]?params[m2]:m1
        })
    }
}

def titleToIdentifier(String title){
    def str=title.trim()
    str= str.replaceAll(/['":\/<>,;\[\]\{\}\|\\!@#\$%\^&\*\(\)\+~`\?=]/,'')
    str= str.replaceAll(/[\s]/,'-')
    str = str.toLowerCase()
    str = str.replaceAll(/^[^a-z]+/,'')
    return str
}
def addTempFile(File file,boolean auto=false){
    if(!auto || flags.cleanUpAuto){
        file.deleteOnExit()
    }
    return file
}
def writeTempFile(String content){
    def tfile = addTempFile(File.createTempFile("edam","temp.md"))
    tfile.withWriter("UTF-8"){
        it<<content
    }
    return tfile
}
def pageLinkTitle(Map page){
    return replaceParams(options.pageLinkTitle,[chapterTitle:options.chapterTitle,title:page.title,index:page.index,name:page.file.basename])
}
def subLinkTitle(Map page,File dir){
    return replaceParams(options.subLinkTitle,[sectionTitle:options.sectionTitle,title:page.title,index:page.index,name:dir.name])
}
def readIndexFile(File dir){
    def nameRegex="^"+options.indexFileName+'\\.(md|txt)$'
    def file=dir.listFiles().find{it.name=~nameRegex}
    def index
    if(file){
        def title=replaceParams(file.title,pagevars,options.tokenStart,options.tokenEnd)
        index=[title:title,index:-1,outfile:"${options.indexFileOutputName}.html",file:file,srcfile:file]
    }
    return index
}
def replaceParams(String templ,Map params,String tokenStart='${', String tokenEnd='}'){
    def replaced1=templ.replaceAll(quote(tokenStart+'if(')+'([a-zA-Z_0-9.-]+?)'+quote(')'+tokenEnd)+'(.*?)'+quote(tokenStart+'endif'+tokenEnd),{all,match1,match2->
            params[match1]?match2:''
        })
    def replaced=replaced1.replaceAll('('+quote(tokenStart)+'([a-zA-Z_0-9.-]+?)'+quote(tokenEnd)+')',{all,match1,match2->
            null!=params[match2]?params[match2]:match1
        })
    return replaced
}
def expandFile(File file,Map extraVars=[:]){
    filterFile(file,[expandFileVarsFilter(extraVars)])
}
def expandFileVarsFilter(Map extraVars=[:]){
    [
        applies:{file,text->(pagevars+extraVars)},
        apply:{text->
            replaceParams(text,pagevars+extraVars,options.tokenStart,options.tokenEnd)
        }
    ]
}
def filterFile(File file,List filters){
    def text=null
    filters.each{filter->
        if(filter.applies(file,text)){
            text = filter.apply(text!=null?text:file.text)
        }
    }
    text!=null?writeTempFile(text):file
}
def outfileName(title,index,file,toc){
    def filestub=replaceParams(options.pageFileName,[title:titleToIdentifier(title),index:index,name:file.name.replaceAll(/\.(md|txt)$/,'')])
    def name=filestub
    if(!name){
        name=file.name.replaceAll(/\.(md|txt)$/,'')
    }
    def x=1
    while(toc.find{it.outfile==name+".html"}){
        x++
        name=filestub+"-${x}"
    }
    "${name}.html"
}
def readToc(File tocfile){
    def toc=[]
    def dirs=[]
    if(tocfile.exists()){
        tocfile.eachLine("UTF-8"){ line ->
            def match=line=~/^(\d+):([^:]+):(.+)$/
            if(match.matches()){
                def file=new File(tocfile.parentFile,match.group(2))
                if(file.isFile()){
                    def fdef=[index:match.group(1).toInteger(),file:file,title:match.group(3).trim(),srcfile:file]
                    fdef.outfile=outfileName(fdef.title,fdef.index,fdef.file,toc)
                    toc<<fdef
                }else if (file.isDirectory()){
                    dirs<<file
                }
            }
        }
    }
    return [toc:toc,dirs:dirs]
}
def getToc(File dir, File routputdir){
    def nameRegex='^(toc|'+options.indexFileName+')\\.(md|txt)$'
    def mdfiles=dir.listFiles().findAll{(it.name=~/\.(md|txt)$/) && !(it.name=~nameRegex)}
    def tocfile=new File(dir,"toc.conf")
    def readfile=readToc(tocfile)
    def readtoc=readfile.toc
    def readdirs=readfile.dirs
    def toc=[]
    def srcfile=null
    if(!tocfile.exists()){
        addTempFile tocfile,true
        //create toc.conf
        def ndx=1
        tocfile.withWriter("UTF-8") { out ->
            mdfiles.each{file->
                def title=replaceParams(file.title,pagevars,options.tokenStart,options.tokenEnd)
                toc<<[index:ndx,file:file,srcfile:file,title:title,outfile:outfileName(title,ndx,file,toc)]
                out.writeLine("${ndx}:${file.name}:${title}")
                ndx++
            }
        }
    }else{
        //compare read to mdfiles
        def tocfiles=readtoc.collect{it.file}
        def common=tocfiles.intersect(mdfiles)
        if(common.size()!=mdfiles.size() || common.size() !=readtoc.size()){
            tocfiles.removeAll(common)
            mdfiles.removeAll(common)
            throw new RuntimeException( "Warning: some files changed compared to the TOC, please update toc.conf or remove it to regenerate: Missing ${tocfiles.size()} files declared in TOC: ${tocfiles}, Found ${mdfiles.size()} extra files in directory: ${mdfiles}")    
        }
        srcfile=tocfile
        
        toc=readtoc
    }
    return [toc:toc,dirs:readdirs,srcfile:srcfile]
}

def createTocMdFile(File dir,toc,title,content=null, subdirs=null){
    def tocout=new File(dir,"${options.tocFileName}.txt")
    if(!tocout.exists() && !flags.genTocOnly){
        addTempFile tocout,true
    }
    //println "createTocMdFile: ${tocout}"
    tocout.withWriter("UTF-8"){writer->
        if(content || !flags.genTocOnly){
            writer<< (content?:"% ${title}")
            writer<<"\n\n"
        }
        if(toc){
            if(content || flags.genTocOnly){
                writer<<"## ${options.tocTitle}\n\n"
            }
            toc.each{titem->
                writer<<"${titem.index}. [${pageLinkTitle(titem)}](${titem.outfile})\n"
            }
        }
        if(subdirs){
            if(content || flags.genTocOnly){
                if(toc){
                    writer<<"\n"
                }
                writer<<"## ${options.subTocTitle}\n\n"
            }
            def i=1
            subdirs.each{sitem->
                writer<<"${i}. [${subLinkTitle(sitem.index,sitem.dir)}](${sitem.dir.name}/${sitem.index.outfile})\n"
            }
            //xxx: add toc of every item in sitem.toc?
        }
    }
}
defaultTemplates=[
header:'',
before:'''<div id="docbody">
''',
after:'''</div>
''',
footer:'',
html:'''<!DOCTYPE html>
<html>
<head>
  <title>$if(title-prefix)$$title-prefix$ - $endif$$if(pagetitle)$$pagetitle$$endif$</title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
  <meta name="generator" content="pandoc" />
$for(author)$
  <meta name="author" content="$author$" />
$endfor$
$if(date)$
  <meta name="date" content="$date$" />
$endif$
$if(highlighting-css)$
  <style type="text/css">
$highlighting-css$
  </style>
$endif$
$for(css)$
  <link rel="stylesheet" href="$css$" type="text/css" />
$endfor$
$if(math)$
  $math$
$endif$
$for(header-includes)$
  $header-includes$
$endfor$
</head>
<body>
$for(include-before)$
$include-before$
$endfor$
$if(title)$
<h1 class="title">$title$</h1>
$endif$
$if(toc)$
$toc$
$endif$
$body$
$for(include-after)$
$include-after$
$endfor$
<!-- Start of HubSpot Embed Code -->
<script type="text/javascript" id="hs-script-loader" async defer src="//js.hs-scripts.com/2768099.js"></script>
<!-- End of HubSpot Embed Code -->
</body>
</html>''',
navCrumbs:'''$if(crumbs)$
<nav class="breadcrumb $navclass$">
    <ul>$for(crumb)$
        <li>$if(crumblink)$<a href="$crumblink$">$crumbtitle$</a>$endif$$if(crumbname)$$crumbname$$endif$</li>
    $endfor$</ul>
</nav>
$endif(crumbs)$''',
nav:'''
<nav class="page $navclass$">
    <ul>
        <li class="current"><a href="$currentpagelink$">$currentpage$</a></li>
        $if(tocpage)$<li class="toc"><a href="$tocpagelink$">$tocpage$</a></li>$endif$
        $if(prevpage)$<li class="prev"><a href="$prevpagelink$">$prevpage$</a></li>$endif$
        $if(nextpage)$<li class="next"><a href="$nextpagelink$">$nextpage$</a></li>$endif$
    </ul>
</nav>''']

def getTemplates(File tdir){
    def temps=[:]
    defaultTemplates.each{k,v->
        def f=new File(tdir,"${k}.template")
        if(!f.exists()){
            addTempFile f,true
            f.parentFile.mkdirs()
            f.text=v
        }
        temps[k]=f
    }
    temps
}

def runPandoc(List longparams){
    def panargs = [options.pandocCmd]
    panargs.addAll flags.pdocextra
    panargs.addAll longparams
    if(flags.verbose){
        println "Run: ${panargs.join(' ')}"
    }
    return panargs.execute()
}
    endif=quote('$endif$')
    crendif=quote('$endif(crumbs)$')
endfor=quote('$endfor$')
navPats=[
    currentpagelink:quote('$if(currentpage)$'),
    tocpagelink:quote('$if(tocpage)$'),
    nextpagelink:quote('$if(nextpage)$'),
    prevpagelink:quote('$if(prevpage)$'),
    crumbs:quote('$if(crumbs)$'),
    srcpageurl:quote('$if(srcpageurl)$'),
]

def replaceNavContent(Map navs,String navcontent){
    //replace ifclauses
    
    
    navPats.each{k,v->
        navcontent=navcontent.replaceAll('(?s)'+v+'(.*?)'+(k=='crumbs'?crendif:endif),navs[k]?'$1':'')
    }
    def crumbs=navs.remove('crumbs')
    if(crumbs){
        navcontent=navcontent.replaceAll('(?s)'+quote('$for(crumb)$')+'(.*?)'+endfor){all,m1->
            //evaluate crumbs in a loop
            def repl=[]
            def r=crumbs.size() //distance of breadcrumb, starting at top
            def s = new StringBuilder()
            crumbs.each{c->
                //each crumb either has a crumblink to a page, or just a crumbname (placeholder)
                def vars=c.placeholder?[crumbname:c.dir.name]:[crumbtitle:c.title,crumblink: ("../"*r) + c.outfile]
                def tmpls=[:]
                def tval=m1
                [crumblink:quote('$if(crumblink)$'),crumbname:quote('$if(crumbname)$')].each{k,v->
                    //for template (m1) remove invalid template, keep valid one
                    tval=tval.replaceAll(v+'(.*?)'+endif,vars[k]?'$1':'')
                }
                //finally, replace vars in the remaining template for this crumb
                s<<replaceParams(tval,vars,'$','$')
                r--
            }
            return s.toString()
        }
    }
    replaceParams(navcontent,navs,'$','$')
}
def chapLinkTitle(titem){
    titem.index>0 && options.chapterNumbers=='true' ?"${options.chapterTitle} ${titem.index}: ${pageLinkTitle(titem)}":pageLinkTitle(titem)
}
def prepareAll(toc,File dir,File rootdir, File tocsrcfile){
    def ndxtoc=[]
    
    def index=readIndexFile(dir)
    if(index){
        ndxtoc<<index
    }
    if('true'==options.singleOutputFile){
        flags.doToc=false
        flags.doNav=false
    }
    if(1==toc.size() && !index){
        flags.doNav=false
        flags.doToc=false
        if(options.singleIndex=='true'){
            toc[0].outfile="${options.indexFileOutputName}.html"
        }
    }
    def tocdoc
    if(flags.doToc && (toc.size() )>1 || flags.doToc && flags.recursive){
        //&& (toc.size() + subdirs?.size())>0
        tocdoc=[index:0,title:options.tocTitle,file:new File(dir,"${options.tocFileName}.txt"),outfile:'toc.html']
        ndxtoc<<tocdoc
    }
    if(flags.tocAsIndex && tocdoc){
        tocdoc.outfile="${options.indexFileOutputName}.html"
        if(index){
            ndxtoc.remove(0)
            tocdoc.title=index.title
            tocdoc.content=index.file.text
            tocdoc.srcfile=index.file
        }else if(flags.recursive && tocdoc.title==options.tocTitle){
            tocdoc.title=dir.title
        }
        if(!index && tocsrcfile){
            tocdoc.srcfile=tocsrcfile
        }
    }
    
    def allpages=ndxtoc + toc
    
    
    if('true'==options.singleOutputFile){
        //list all markdown files into the multifiles of the first entry
        def multifiles=[]
        allpages.eachWithIndex{titem,x->
            if(x>0){
                multifiles<<expandFile(titem.file)
            }
        }
        
        allpages[0].multifiles=multifiles
        allpages[0].index=0
        if(options.singleIndex=='true'){
            allpages[0].outfile="${options.indexFileOutputName}.html"
        }
        allpages = [allpages[0]]
    }
    allpages.each{
        it.rootdir=rootdir
    }
    
    return allpages
}
java.util.regex.Pattern PageLinkMatch=compile('(?s)\\[page:([^\\]\\s]+)\\]')
/**
 * Generate output files using pandoc. 
 * order of output:
 * -1: index (if it exists)
 * 0: toc (if flags.doToc)
 * (-1 and 0 are merged if flags.tocAsIndex)
 * 1: first page, ...
 */
this.generateAll={context,allpages,toc,templates,File dir, File outdir, crumbs, subdirs->
    def navfileTop=addTempFile(new File(dir,'temp-nav-top.html'))
    def navfileBot=addTempFile(new File(dir,'temp-nav-bot.html'))
    def tocdoc = allpages.find{it.index==0}
    def index = allpages.find{it.index==-1}
    if(tocdoc){
        createTocMdFile(flags.genTocOnly?outdir:dir,toc,tocdoc.title,tocdoc.content,subdirs)
    }
    if(flags.genTocOnly){
        return allpages
    }
    allpages.eachWithIndex{titem,x->
        def xvars=options.subMap(['bugpagelink','bugpageurl'])
        def relPath= '../'*crumbs.size()
        def templatevars=[relPath:relPath]
        if(options.srcbaseurl && titem.srcfile && titem.rootdir){
            def pagesrcpath= titem.rootdir.toPath().relativize(titem.srcfile.toPath())
            templatevars['pagesrcpath']=pagesrcpath
            xvars.srcpagelink=options.srcpagelink?:'Edit This Page'
            xvars.srcpageurl=options.srcbaseurl + pagesrcpath
            xvars = xvars.collectEntries{
                [it.key,replaceParams(it.value,templatevars)]
            }
        }
        def pargs=['-B',expandFile(templates.header,templatevars)]

        //set up nav links
        def navs=[currentpage:chapLinkTitle(titem),currentpagelink:titem.outfile]+xvars
        
        if(x<allpages.size()-1){
            //all but the last page
            navs.nextpage=chapLinkTitle(allpages[x+1])
            navs.nextpagelink=allpages[x+1].outfile
        }
        if(titem.index>1){
            //all content pages but the first
            navs.prevpage=chapLinkTitle(allpages[x-1])
            navs.prevpagelink=allpages[x-1].outfile
        }
        def toctitle=tocdoc?.title
        def toclink=tocdoc?.outfile
        if(tocdoc && titem.index!=tocdoc.index ){
            //all pages but the toc page
            navs.tocpage=tocdoc.title
            navs.tocpagelink=tocdoc.outfile
        }else if(!tocdoc && index && titem.index!=index.index){
            //all pages but the toc page
            navs.tocpage=index.title
            navs.tocpagelink=index.outfile
        }
        if(flags.doNav || flags.recursive){
            def navcontent=templates.nav.text
            def bcrumbnav=templates.navCrumbs.text
            //write nav temp files
            navfileTop.withWriter("UTF-8"){writer->
                def txt=replaceNavContent(navs + [navclass:'top',crumbs:crumbs],bcrumbnav + navcontent)
                writer.write(pagevars?replaceParams(txt,pagevars,options.tokenStart,options.tokenEnd):txt)
            }
            navfileBot.withWriter("UTF-8"){writer->
                def txt=replaceNavContent(navs + [navclass:'bottom',crumbs:crumbs],navcontent + bcrumbnav)
                writer.write(pagevars?replaceParams(txt,pagevars,options.tokenStart,options.tokenEnd):txt)
            }
            //pargs.addAll(['-B',navfileTop,'-A',navfileBot])
            pargs.addAll(['-B',navfileTop])
        }
        
        pargs.addAll(['-B',expandFile(templates.before,templatevars)])
        pargs.addAll(['-A',expandFile(templates.after,templatevars)])
        if(flags.doNav || flags.recursive){
            pargs.addAll(['-A',navfileBot])
        }

        pargs.addAll(['-A',expandFile(templates.footer,templatevars)])
        def htemplate=expandFile(templates.html,templatevars)
        def cssPath= options.cssRelative=='true'? relPath : ''
        def outfile=new File(outdir,titem.outfile)
        pargs.addAll(['-o',outfile.absolutePath,'-s',"--css=${cssPath}${options.cssFileName}","--template=${htemplate.absolutePath}"])
        if(titem.index>0){
            pargs<<"--toc"
        }

        def text=titem.file.text
        def linkset=[]
        def linksdoc=null
        if(context.siteLinks && text.contains('[page:')){
            //add any other content such as generated page: link definitions
            //generate relative links for the rest of the site
            linkset = (text=~PageLinkMatch).collect{it[1]}
            linksdoc=generateRelativeLinks(context, titem, linkset)
            println "linkset: ${linkset}"
            println "links: ${linksdoc.text}"
            
        }
        def filters=[
            expandFileVarsFilter(),
            replacePageLinksFilter(context, titem, linkset)
        ]
        pargs.add(filterFile(titem.file,filters))
        if(titem.multifiles){
            pargs.addAll titem.multifiles
        }
        if(linksdoc){
            pargs<<linksdoc
        }
        if(!flags.preview){
            def proc=runPandoc(pargs)
            proc.consumeProcessOutput(System.out,System.err)
            def result=proc.waitFor()
            if(0!=result){
                println "Error running pandoc, result: ${result}"
                throw new RuntimeException("Pandoc run failed: ${result} with args ${pargs}")
            }
            println "${outfile} <-- ${titem.srcfile?.name}"
        }else{
            print "${outfile} <-- ${titem.file.name}"
            if(titem.index==-1){
                print " (Index)"
            }else if(titem.index==0){
                print " (TOC, ${toc.size()} Chapters, ${subdirs?.size()} Directories)"
            }else if(titem.index>0){
                print " (Chapter ${titem.index})"
            }
            print " - ${titem.title}"
            println ""
        }
    }
    allpages
}
/**
* return [commoncount,backtrack]
* where commoncount is # of path segments in common from root
* backtrack: # of path segments different from common ancestor
*/
def relativeCrumbs(page,crumbs){
    def i=0
    while(i<page.size() && i<crumbs.size() && page[i] == crumbs[i]){
         i++
    }
    i
}
def relativePath(targetPath,srcpath){
    def parts = targetPath.split('/')
    def srcparts=srcpath.split('/').toList()
    srcparts.pop()
    def common=relativeCrumbs(parts,srcparts)
    [common,("../"*(srcparts.size() - common) )+ (parts[common..<parts.size()].join('/'))]
}
def anchorToTitle(anchor){
    anchor.split('-')*.capitalize().join(' ')
}
def collectRelativeLinks(context, page, linkset=[]){
    def pagesrcpath=page.rootdir.toPath().relativize(page.srcfile.toPath())
    //links to files without #anchor
    def srclinkset=linkset?.collect{
        it.indexOf('#')>0?it.substring(0,it.indexOf('#')):it
    }

    //find broken links
    def broken=srclinkset.findAll{
        !context.siteLinks[it]
    }
    if(broken){
        throw new RuntimeException("Document ${pagesrcpath}: These page links were not found: $broken")
    }

    def sublinks = [:]
    linkset.each{
        def parts=it.split('#',2)
        if(parts.length>1){
            if(!sublinks[parts[0]]){
                sublinks[parts[0]]=[parts[1]]
            }else{
                sublinks[parts[0]]<<parts[1]
            }
        }
    }
    srclinkset.collect{ srcpath ->
        def pagedata=context.siteLinks[srcpath]
        def fullpath=pagedata.alltitles? pagedata.alltitles[1..<pagedata.alltitles.size()]:[pagedata.title]
        def fulltitle=fullpath.join(options.fulltitleseparator)
        def targetpage=context.siteLinks[pagesrcpath.toString()]
        def outpath= targetpage.outpath
            
        //determine relative path for target page, based on pagedate.crumbs and crumbs
        // def relpath = Paths.get(outpath).relativize(Paths.get(pagedata.outpath)).toString()
        def (relcommon,relpath) = relativePath(pagedata.outpath, outpath)
        def reltitle=fullpath[relcommon..<fullpath.size()].join(options.fulltitleseparator)
        
        def linkdefinitions=
        [
            [srcpath:srcpath,relpath:relpath,fulltitle:fulltitle,title:pagedata.title,reltitle:reltitle]
        ]
        sublinks[srcpath]?.each{anchor->
            linkdefinitions<<[
                [
                    srcpath:srcpath+'#'+anchor,
                    relpath:relpath+'#'+anchor,
                    fulltitle:fulltitle + options.fulltitleseparator + anchorToTitle(anchor),
                    reltitle:reltitle + options.fulltitleseparator + anchorToTitle(anchor),
                    title:pagedata.title
                ]
            ]
        }
        linkdefinitions
    }.flatten()
}
def generateRelativeLinksContent(context, page,linkset=[]){
    '\n\n' + collectRelativeLinks(context,page,linkset).collect{ link ->
        "[page:${link.srcpath}]: ${link.relpath} (${link.fulltitle})"
    }.findAll{it}.join('\n')
}
def generateRelativeLinks(context, page,linkset){
    writeTempFile(generateRelativeLinksContent(context,page,linkset))
}

def replacePageLinksFilter(context,page,linkset){
    def linksdata=collectRelativeLinks(context,page,linkset)
    def linksmap=linksdata.collectEntries{[it.srcpath,it]}
    def linksregex=java.util.regex.Pattern.compile('(?s)(\\[\\[page:([^\\]\\s]+)\\]\\])')
    [
        applies:{file,text->
            linkset
        },
        apply:{text->
            text.replaceAll(linksregex){match->
                def link=linksmap[match[2]]
                "[${link.reltitle}][page:${link.srcpath}]"
            }
        }
    ]
}

this.scanAll={context,allpages,toc,templates,File dir, File outdir, crumbs, subdirs->
    def tocdoc = allpages.find{it.index==0}
    def index = allpages.find{it.index==-1}
    if(tocdoc){
        //createTocMdFile(flags.genTocOnly?outdir:dir,toc,tocdoc.title,tocdoc.content,subdirs)
    }
    if(flags.genTocOnly){
        return allpages
    }
    def getPageData={titem->
         def srcpath= titem.rootdir.toPath().relativize(titem.srcfile.toPath()).toString()
        def outfile=new File(outdir,titem.outfile)
        def outpath= context.outputdir.toPath().relativize(outfile.toPath()).toString()
        [srcpath:srcpath,title:chapLinkTitle(titem),outpath:outpath]
    }
    allpages.eachWithIndex{titem,x->

        def pagedata=getPageData(titem)
        
        pagedata.crumbs=new ArrayList(crumbs)
        if(tocdoc && titem.index!=tocdoc.index ){
            //all pages but the toc page
            pagedata.crumbs<<tocdoc
            // navs.tocpage=tocdoc.title
            // navs.tocpagelink=tocdoc.outfile
        }else if(!tocdoc && index && titem.index!=index.index){
            //all pages but the toc page
            pagedata.crumbs<<index
            // navs.tocpage=index.title
            // navs.tocpagelink=index.outfile
        }
        if(pagedata.crumbs){
            def titlepaths = pagedata.crumbs.collect{c->
                c.placeholder?c.dir.name:c.title
            }
            pagedata.alltitles=(titlepaths+[pagedata.title])
        }else{
            pagedata.alltitles = [pagedata.title]
        }
        if(!context.siteLinks){
            context.siteLinks=[(pagedata.srcpath):pagedata]
        }else{
            context.siteLinks[pagedata.srcpath]=pagedata
        }
        
    }
    allpages
}
def parseConf(File dir){
    File conf=new File(dir,'edam.conf')
    if(!conf.file){
        conf = new File(dir,'.edam.conf')
    }
    if(!conf.file){
        return
    }
    def newargs=[]
    conf.eachLine{line->
        //format per line:
        //flag [values]
        def lval=line.split(/ /,2)
        if(lval.length>0){
            newargs.addAll(lval as List)
        }
    }
    parseArgs(newargs)
}

def parseArgs(pargs){
    def x=0
    def xtraargs = false
    
    def docsdir
    def tdir
    def outputdir
    while(x<pargs.size()){
        if(!xtraargs){
            switch(pargs[x]){
                case '-h':
                    flags.help=true
                    break
                case '--help':
                    flags.help=true
                    break
                case '-d':
                    docsdir=new File(pargs[x+1])
                    x++
                    break
                case '-t':
                    tdir = new File(pargs[x+1])
                    x++
                    break
                case '-o':
                    outputdir=new File(pargs[x+1])
                    x++
                    break
                case '--no-toc':
                    flags.doToc=false
                    break
                case '--do-toc':
                    flags.doToc=true
                    break
                case '--no-nav':
                    flags.doNav=false
                    break
                case '--no-auto-clean':
                    flags.cleanUpAuto=false
                    break
                case '--verbose':
                    flags.verbose=true
                    break
                case '--separate-toc':
                    flags.tocAsIndex=false
                    break
                case '--gen-toc-only':
                    flags.genTocOnly=true
                    break
                case '--variables':
                    Properties p = new Properties()
                    p.load(new File(pargs[x+1]).newReader())
                    pagevars.putAll(p)
                case '-V':
                    def arr=pargs[x+1].split('=',2)
                    if(arr.length>1){
                        def value=arr[1]
                        if(value.indexOf('${')){
                            //process embedded vars
                            value=replaceParams(value, pagevars)
                        }
                        pagevars[arr[0]]=value
                    }
                    x++
                    break
                case '-O':
                    def arr=pargs[x+1].split('=',2)
                    if(arr.length>1){
                        options[arr[0]]=arr[1]
                    }
                    x++
                    break
                case '-r':
                    options.recurseDepth="-1"
                    break
                case '--preview':
                    flags.preview=true
                    break
                case '-x':
                    xtraargs=true
            }
        }else{
            flags.pdocextra<<pargs[x]
        }
        x++
    }
    [docsdir:docsdir,tdir:tdir,outputdir:outputdir]
}
argDescs=[
'-h/--help'
:    'Show this help text',
'-d <basedir>'
:    'The base directory containing the docs to convert. Defaults to the current directory.',
'-t <templatesdir>'
:   "The directory containing templates. Defaults to basedir/templates.",
'-o <outputdir>'
:   'The directory to write HTML files to. Defaults to the basedir.',
'-r'
:   'Recursively descend to subdirectories and apply Edam',
'--no-toc'
:   'Don\'t include the Table of Contents.',
'--no-nav'
:   "Don't include navigation links on each page.",
'--verbose'
:   "Be verbose about running pandoc.",
'--no-auto-clean'
:   "Automatically clean up any generated files. Otherwise templates/toc.txt will be created.",
'--separate-toc'
:   "Put the Table of Contents on a separate page, instead of at the end of the index page.",
'-O option=value'
:   "Override an option value. Options are shown below.",
'-V var=value'
:   "Define a variable to expand within templates and markdown content.",
'--variables <propertiesfile>'
:   "Load variables from a properties file.",
]
def printHelp(){    
    println '''% Usage\n
    edam.groovy [-h/--help] [-d <basedir>] [-t <templatesdir>] [-o <outputdir>]
        [-r] [--no-toc] [--no-nav] [--verbose] [--clean] [--separate-toc]
        [-O option=value [ -O ... ] ] [-V var=value ...] [ --variables <propertiesfile> ]
        [-x [ extra pandoc args .. ] ]

## Arguments\n\n'''
    argDescs.each{k,v->
        println "`${k}`\n:    ${v}\n"
    }
    println "## Options\n"
    println "Options define the conventional defaults used for generating output.  You can override any value with `-O option=value` on the commandline.\n"
    optionDescs.keySet().each{
        println "`${it}`\n:    ${optionDescs[it]} Default: `${optionsDefaults[it]}`\n"
    }
}
def generateSiteJson(context, File outputdir){
    if(options.generateSiteJson=='true'){
        File siteJson=new File(outputdir,options.siteJsonFile?:'site.json')
        siteJson.withWriter { writer ->
            writer<<'['
            def comma=""
            context.siteLinks.each{srcpath,pagedata->
                if(pagedata.outpath=='index.html'){
                    return
                }
                writer<<"""${comma}
                {
                    "srcpath": "${pagedata.srcpath}",
                    "title": "${pagedata.title}",
                    "outpath": "${pagedata.outpath}",
"""
                if(pagedata.alltitles.size()>1){
                    writer<<"""
                        "alltitles": "${pagedata.alltitles[1..-1].join(options.siteJsonPathSeparator)}"
                    }
                    """
                }else{
                    writer<<"""
                    "alltitles": "${pagedata.alltitles.join(options.siteJsonPathSeparator)}"
                    }
                    """
                }
                println "${pagedata.srcpath} > ${pagedata.title}(${pagedata.outpath}) = ${pagedata.alltitles}"
                comma=','
            }
            writer<<']'
        }
    }
}
/**
 * Run edam on a directory. 
 * rdepth = remaining dir depth to follow, -1 for indefinite
 * crumbs = breadcrumbs from upper directories, in order
 */
def run(File rootdir, File docsdir, File tdir, File outputdir, rdepth, crumbs){
    def context=[outputdir:outputdir]
    def result=recurse_dirs(context,rootdir,docsdir,tdir,outputdir,rdepth,crumbs,scanAll)
    
    generateSiteJson(context,outputdir)
    
    recurse_dirs(context,rootdir,docsdir,tdir,outputdir,rdepth,crumbs,generateAll)
}
def recurse_dirs(Map context,File rootdir, File docsdir, File tdir, File outputdir, rdepth, crumbs, Closure action){
    def rtdir=tdir
    if(!tdir){
        rtdir=new File(docsdir,'templates')   
    }
    def routputdir=outputdir
    if(!outputdir){
        routputdir=docsdir
    }
    //todo: parse edam.conf
    parseConf(docsdir)
    def readtoc= getToc(docsdir,routputdir)
    def toc=readtoc.toc
    def dirs=readtoc.dirs
    def pages=prepareAll(toc,docsdir,rootdir,readtoc.srcfile)
    def subdirs=[]
    if(rdepth!=0){
        //stash flags, pagevars, options
        def stash=[flags:new HashMap(flags),pagevars:new HashMap(pagevars),options:new HashMap(options)]
        def scrumb=new ArrayList(crumbs)
        if(pages){
            scrumb<<pages[0]
        }else if(scrumb){
            scrumb<<[dir:docsdir,placeholder:true]
        }
        def dirclos={dir->
           if(dir.name!='templates' && dir!=routputdir && !(dir.name=~options.recurseDirPatternIgnore)){
                flags=new HashMap(stash.flags)
                pagevars=new HashMap(stash.pagevars)
                options=new HashMap(stash.options)
                def newoutputdir=new File(routputdir,dir.name)
                newoutputdir.mkdirs()
                def result=recurse_dirs(context,rootdir,dir,rtdir,newoutputdir,rdepth-1,scrumb,action)
                //add index doc to subdirs list for this toc
                if(result?.toc){
                    subdirs<<[dir:dir,index:result.toc[0],toc:result.toc]
                }
           }
        }
        if(!dirs){
            docsdir.eachDirMatch(compile(options.recurseDirPattern),dirclos)
        }else{
            dirs.each(dirclos)
        }
        //unstash
        flags=stash.flags
        pagevars=stash.pagevars
        options=stash.options
    }

    def templates=getTemplates(rtdir)
    action(context,pages,toc,templates,docsdir,routputdir,crumbs,subdirs)
    
    [docsdir:docsdir,tdir:rtdir,outputdir:routputdir,toc:pages]
}

def dirs=parseArgs(args)

if(!dirs.docsdir){
    dirs.docsdir = new File(System.getProperty("user.dir"))
}
if(flags.help){
    printHelp()
    return 1
}
if(options.recurseDepth!='0'){
    flags.recursive=true
}

run(dirs.docsdir,dirs.docsdir,dirs.tdir,dirs.outputdir,options.recurseDepth.toInteger(),[])
