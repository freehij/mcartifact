package io.github.freehij.mcartifact

import org.gradle.api.Plugin
import org.gradle.api.Project
import groovy.json.JsonSlurper
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class McArtifactExtension {
    String version
    String environment
    boolean fetchLibraries = true
}

class McArtifactPlugin implements Plugin<Project> {
    void apply(Project project) {
        def ext = project.extensions.create('mcartifact', McArtifactExtension)
        project.afterEvaluate {
            def key = "${ext.version}_${ext.environment}"
            def url = "https://raw.githubusercontent.com/freehij/resources/refs/heads/main/versions.json"
            def json = new JsonSlurper().parse(new URL(url))
            if (!json[key]) throw new RuntimeException("Key $key not found in versions.json")
            def downloadUrl = json[key]
            def fileName = downloadUrl.substring(downloadUrl.lastIndexOf('/') + 1)
            def cacheDir = new File(project.gradle.gradleUserHomeDir, ".gradle/mcartifacts/$key")
            def artifactFile = new File(cacheDir, fileName)
            if (!artifactFile.exists()) {
                artifactFile.parentFile.mkdirs()
                artifactFile << new URL(downloadUrl).openStream()
            }
            def dependencyFile = artifactFile
            if (ext.environment == "server") {
                def mergedFile = new File(cacheDir, "merged-${fileName}")
                if (!mergedFile.exists()) {
                    def innerPath = "META-INF/versions/${ext.version}/server-${ext.version}.jar"
                    def tempInner = File.createTempFile("inner-server-${ext.version}", ".jar")
                    tempInner.deleteOnExit()
                    new ZipFile(artifactFile).withCloseable { outer ->
                        def innerEntry = outer.getEntry(innerPath)
                        if (!innerEntry) throw new RuntimeException("Inner jar not found at $innerPath")
                        tempInner.withOutputStream { os ->
                            os << outer.getInputStream(innerEntry)
                        }
                    }
                    mergedFile.parentFile.mkdirs()
                    new ZipOutputStream(new FileOutputStream(mergedFile)).withCloseable { zos ->
                        new ZipFile(tempInner).withCloseable { innerZip ->
                            innerZip.entries().each { entry ->
                                if (!entry.directory) {
                                    zos.putNextEntry(new ZipEntry(entry.name))
                                    zos << innerZip.getInputStream(entry)
                                    zos.closeEntry()
                                }
                            }
                        }
                        new ZipFile(artifactFile).withCloseable { outerZip ->
                            outerZip.entries().each { entry ->
                                if (!entry.directory && entry.name.startsWith("net/minecraft/bundler/")) {
                                    zos.putNextEntry(new ZipEntry(entry.name))
                                    zos << outerZip.getInputStream(entry)
                                    zos.closeEntry()
                                }
                            }
                        }
                    }
                    tempInner.delete()
                }
                dependencyFile = mergedFile
            }
            project.dependencies.add('compileOnly', project.files(dependencyFile))

            if (ext.fetchLibraries) {
                def libsUrl = "https://raw.githubusercontent.com/freehij/resources/refs/heads/main/libraries.json"
                def libsJson = new JsonSlurper().parse(new URL(libsUrl))
                def libs = libsJson[ext.version]
                if (libs) {
                    def libsCacheDir = new File(cacheDir, "libraries")
                    def libFiles = []
                    libs.each { coord, libUrl ->
                        def parts = coord.split(':')
                        if (parts.length < 3) return
                        def group = parts[0]
                        def artifact = parts[1]
                        def libVersion = parts[2]
                        def groupPath = group.replace('.', '/')
                        def libDir = new File(libsCacheDir, "${groupPath}/${artifact}/${libVersion}")
                        def libFileName = libUrl.substring(libUrl.lastIndexOf('/') + 1)
                        def libFile = new File(libDir, libFileName)
                        if (!libFile.exists()) {
                            libDir.mkdirs()
                            libFile << new URL(libUrl).openStream()
                        }
                        libFiles.add(libFile)
                    }
                    if (!libFiles.isEmpty()) {
                        project.dependencies.add('compileOnly', project.files(libFiles))
                    }
                }
            }

            project.tasks.register('downloadMcArtifact') {
                doLast {
                    println "Artifact available at: ${dependencyFile.absolutePath}"
                    if (ext.fetchLibraries) {
                        println "Libraries cached under: ${new File(cacheDir, 'libraries').absolutePath}"
                    }
                }
            }
        }
    }
}
