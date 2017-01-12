package org.gradle.sdkjar

import org.gradle.api.Project
import org.gradle.api.tasks.Input

class SdkJarExtension {
    @Input
    HashSet<String> includePackage = [];//需要输出jar的包名列表,当此参数为空时，则默认全项目输出
    @Input
    HashSet<String> excludeJar = [];//不需要输出jar的jar包
    @Input
    HashSet<String> excludeClass = [];//不需要输出jar的类名列表
    @Input
    HashSet<String> excludePackage = [];//不需要输出jar的包名列表
    @Input
    String outputFileDir //输出目录
    @Input
    String outputFileName//输出原始jar包名
    @Input
    String outputProguardFileName //输出混淆jar包名
    @Input
    String proguardConfigFile //混淆配置

    public static SdkJarExtension getConfig(Project project) {
        SdkJarExtension config =
                project.getExtensions().findByType(SdkJarExtension.class);
        if (config == null) {
            config = new SdkJarExtension();
        }
        return config;
    }

}
