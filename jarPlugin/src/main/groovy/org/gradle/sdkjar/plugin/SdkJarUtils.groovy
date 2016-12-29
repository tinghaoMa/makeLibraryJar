package org.gradle.sdkjar.plugin

import com.android.SdkConstants
import com.android.build.gradle.api.BaseVariant
import com.google.common.collect.Sets
import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.gradle.api.Task


public class SdkJarUtils {

    public static boolean isExcludedJar(String path, Set<String> excludeJar) {
        for (String exclude : excludeJar) {
            if (path.endsWith(exclude)) {
                return true;
            }
        }
        return false;
    }

    public
    static Set<File> getDexTaskInputFiles(Project project, BaseVariant variant, Task dexTask) {
        if (dexTask == null) {
            dexTask = project.tasks.findByName(getDexTaskName(variant));
        }
        if (isUseTransformAPI(project)) {
            //extensions = [jar]
            def extensions = [SdkConstants.EXT_JAR] as String[]
            Set<File> files = Sets.newHashSet();
            dexTask.inputs.files.files.each {
                if (it.exists()) {
                    if (it.isDirectory()) {
                        Collection<File> jars = FileUtils.listFiles(it, extensions, true);
                        files.addAll(jars)
                        LogUtils.debug('path = ' + it.absolutePath.toLowerCase())
                        if (it.absolutePath.toLowerCase().endsWith(
                                ("intermediates" + File.separator + "classes" + File.separator +
                                        variant.name.capitalize()).toLowerCase())) {
                            files.add(it)
                        }
                    } else if (it.name.endsWith(SdkConstants.DOT_JAR)) {
                        LogUtils.debug('name = ' + it.name)
                        files.add(it)
                    }
                }
            }
            return files
        } else {
            return dexTask.inputs.files.files;
        }
    }

    /**
     * 获取Dex任务名
     * @param project
     * @param variant
     * @return
     */
    static String getDexTaskName(BaseVariant variant) {
        return "bundle${variant.name.capitalize()}"
    }

    public static boolean isUseTransformAPI(Project project) {
        return compareVersionName(project.gradle.gradleVersion, "1.4.0") >= 0;
    }

    public static boolean isGradleHighVersion(String gradleBuildVersion) {
        return compareVersionName(gradleBuildVersion, "2.2.0") >= 0;
    }

    private static int compareVersionName(String str1, String str2) {
        String[] thisParts = str1.split("-")[0].split("\\.");
        String[] thatParts = str2.split("-")[0].split("\\.");
        int length = Math.max(thisParts.length, thatParts.length);
        for (int i = 0; i < length; i++) {
            int thisPart = i < thisParts.length ?
                    Integer.parseInt(thisParts[i]) : 0;
            int thatPart = i < thatParts.length ?
                    Integer.parseInt(thatParts[i]) : 0;
            if (thisPart < thatPart)
                return -1;
            if (thisPart > thatPart)
                return 1;
        }
        return 0;
    }


}
