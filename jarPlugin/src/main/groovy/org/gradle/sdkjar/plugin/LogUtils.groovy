package org.gradle.sdkjar.plugin


class LogUtils {

    static debug=true

    static void debug(String msg) {
        if(debug){
            println(msg)
        }
    }

    static void error(String msg) {
        println(msg)
    }
}
