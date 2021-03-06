package org.gradle.sdkjar

import com.android.SdkConstants
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.LibraryVariant
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.tasks.bundling.Jar
import proguard.gradle.ProGuardTask


class MakeJarPlugin implements Plugin<Project> {
    public static final String EXTENSION_NAME = "JarSetting";
    private def proguardTask;
    private def proguardFileExist;//是否生成了proguard-android.txt文件
    private def isOldGradleVersion;//旧版本的默认文件在sdk/tools下

    @Override
    public void apply(Project project) {
        DefaultDomainObjectSet<LibraryVariant> variants
        initProguardTask(project)

        if (project.getPlugins().hasPlugin(LibraryPlugin)) {
            variants = project.android.libraryVariants;
            project.extensions.create(EXTENSION_NAME, SdkJarExtension);
            applyTask(project, variants);
        }
    }
    /**
     * 根据Gradle版本初始化proguard任务
     * @param project
     */
    private void initProguardTask(Project project) {
        //buildscript classpath 'com.android.tools.build:gradle:2.2.3'
        def gradleBuildVersion = 0
        project.rootProject.buildscript.configurations.each {
            it.getDependencies().each { Dependency d ->
                if (d.getGroup().equals('com.android.tools.build') &&
                        d.name.equals('gradle')) {
                    gradleBuildVersion = d.version
                }
            }
        }
        LogUtils.debug('initProguardTask gradleVersion = ' + gradleBuildVersion)
        //新版本
        if (SdkJarUtils.isGradleHighVersion(gradleBuildVersion)) {
            LogUtils.debug('gradle build version >= 2.2.0')
            project.rootProject.subprojects.each {
                Project p ->
                    if (p.getPlugins().hasPlugin(AppPlugin)) {
                        LogUtils.debug('extractProguardFiles task exist')
                        proguardTask = project.tasks.findByName('extractProguardFiles')
                        if (proguardTask != null) {
                            proguardTask.doLast {
                                proguardFileExist = true;
                            }
                        }
                    }
            }
        } else {
            LogUtils.debug('gradle build version is old <2.2.0')
            isOldGradleVersion = true
        }
    }

    private void applyTask(Project project, variants) {
        LogUtils.debug('begin proguard  please wait...')
        project.afterEvaluate {
            SdkJarExtension jarExtension = SdkJarExtension.getConfig(project);
            //需要输出jar的包名列表,当此参数为空时，则默认全项目输出
            def includePackage = jarExtension.includePackage
            //不需要输出jar的类名列表
            def excludeClass = jarExtension.excludeClass
            //不需要输出jar的包名列表
            def excludePackage = jarExtension.excludePackage
            //不需要输出jar的jar包列表
            def excludeJar = jarExtension.excludeJar

            variants.all { variant ->
                if (variant.name.capitalize() == "Debug") {

                    def dexTask = project.tasks.findByName(SdkJarUtils.getDexTaskName(variant))
                    if (dexTask != null) {
                        def buildJarBeforeDex = "buildJarBeforeDex${variant.name.capitalize()}"
                        def buildJar = project.tasks.create("buildJar", Jar)

                        buildJar.setDescription("构建jar包")
                        Closure buildJarClosure = {
                            //过滤R文件和BuildConfig文件
                            buildJar.exclude("**/BuildConfig.class")
                            buildJar.exclude("**/BuildConfig\$*.class")
                            buildJar.exclude("**/R.class")
                            buildJar.exclude("**/R\$*.class")
                            //输出原始jar包名
                            buildJar.archiveName = jarExtension.outputFileName
                            //输出目录
                            buildJar.destinationDir = project.file(jarExtension.outputFileDir)
                            //不需要输出jar的类名列表
                            if (excludeClass != null && excludeClass.size() > 0) {
                                excludeClass.each {
                                    //排除指定class
                                    buildJar.exclude(it)
                                }

                            }
                            //不需要输出jar的包名列表
                            if (excludePackage != null && excludePackage.size() > 0) {
                                excludePackage.each {
                                    //过滤指定包名下class
                                    buildJar.exclude("${it}/**/*.class")
                                }

                            }
                            //需要输出jar的包名列表
                            if (includePackage != null && includePackage.size() > 0) {
                                includePackage.each {
                                    //仅仅打包指定包名下class
                                    buildJar.include("${it}/**/*.class")
                                }
                            } else {
                                //默认全项目构建jar
                                buildJar.include("**/*.class")

                            }
                        }
                        //创建buildJarBeforeDexDebug task
                        project.task(buildJarBeforeDex) << {
                            Set<File> inputFiles = SdkJarUtils.getDexTaskInputFiles(project, variant, dexTask)

                            inputFiles.each {
                                inputFile ->
                                    def path = inputFile.absolutePath
                                    if (path.endsWith(SdkConstants.DOT_JAR) && !SdkJarUtils.isExcludedJar(path, excludeJar)) {
                                        buildJar.from(project.zipTree(path))
                                    } else if (inputFile.isDirectory()) {
                                        //intermediates/classes/debug
                                        buildJar.from(inputFile)
                                    }
                            }
                        }

                        def proGuardTask = project.tasks.create("makeJar", ProGuardTask);
                        proGuardTask.setDescription("混淆jar包")
                        proGuardTask.dependsOn buildJar
                        //设置不删除未引用的资源(类，方法等)
                        proGuardTask.dontshrink();
                        //忽略警告
                        proGuardTask.ignorewarnings()
                        //需要被混淆的jar包
                        proGuardTask.injars(jarExtension.outputFileDir + "/" + jarExtension.outputFileName)
                        //混淆后输出的jar包
                        proGuardTask.outjars(jarExtension.outputFileDir + "/" + jarExtension.outputProguardFileName)

                        //libraryjars表示引用到的jar包不被混淆
                        // ANDROID PLATFORM
                        proGuardTask.libraryjars(project.android.getSdkDirectory().toString() + "/platforms/" + "${project.android.compileSdkVersion}" + "/android.jar")
                        // JAVA HOME
                        def javaBase = System.properties["java.home"]
                        def javaRt = "/lib/rt.jar"
                        if (System.properties["os.name"].toString().toLowerCase().contains("mac")) {
                            if (!new File(javaBase + javaRt).exists()) {
                                javaRt = "/../Classes/classes.jar"
                            }
                        }
                        proGuardTask.libraryjars(javaBase + "/" + javaRt)
                        //混淆配置文件
                        proGuardTask.configuration(jarExtension.proguardConfigFile)
                        //APK混淆 需要
//                        def aapt_rules = project.rootDir.absolutePath + "/intermediates/proguard-rules/release/aapt_rules.txt"
//                        LogUtils.debug('aapt_rules = ' + aapt_rules)
//                        proGuardTask.configuration(aapt_rules)

                        if (proguardFileExist || isOldGradleVersion) {
                            File proguardFile = project.file(project.android.getDefaultProguardFile('proguard-android.txt'))
                            if (proguardFile.exists()) {
                                proGuardTask.configuration(proguardFile)
                            }
                        }
                        //输出mapping文件
                        proGuardTask.printmapping(jarExtension.outputFileDir + "/" + "mapping.txt")

                        def buildJarBeforeDexTask = project.tasks[buildJarBeforeDex]
                        buildJar.dependsOn buildJarBeforeDexTask
                        if (proguardTask != null) {
                            buildJar.dependsOn proguardTask
                        }
                        buildJarBeforeDexTask.dependsOn project.tasks.findByName('build')
                        buildJar.doFirst(buildJarClosure)
                    }
                }

            }
        }
    }


}
