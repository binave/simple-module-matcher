/*
 * Copyright (c) 2017 bin jin.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.binave.match;

import sun.net.www.ParseUtil;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;


/**
 * 启动类
 * 基于 jar 启动
 *
 * @author bin jin on 2017/3/18.
 * @since 1.8
 */
public class Bootstrap {

    private static SimpleLog log = SimpleLogFactory.getLog(Bootstrap.class);

    public static void main(String[] args) {

        String loadPath;

        if (args == null || args.length == 0) {
            // 使用 jar 所在路径
            String[] classPath = System.getProperty("java.class.path").
                    split("" + File.pathSeparatorChar);

            // 不是 jar 则跳出
            if (classPath.length != 1) {
                log.error("not a jar");
                System.exit(1);
            }

            loadPath = new File(pwd(classPath[0])).getParentFile().getAbsolutePath();
        } else loadPath = new File(pwd(args[0])).getAbsolutePath(); // 处理成绝对路径

        loadPath += File.separator + "lib"; // 拿到 lib 目录

        File loadDir = new File(loadPath);
        if (!loadDir.isDirectory()) {
            log.error("not found lib directory: {}", loadDir);
            System.exit(1);
        }

        // 获得其中的 jar 文件
        List<URL> urlList = new ArrayList<>();
        List<Unit> tagList = new ArrayList<>();

        urlList.add(getURL(loadDir));

        for (File f : loadDir.listFiles(pathname -> pathname.getPath().endsWith(".jar"))) {

            urlList.add(getURL(f));

            String tagText = null, nameText = null, levelText = null;

            for (Map.Entry entry : getManifest(f).entrySet()) {
                if ("Module-ArtifactId".equals(entry.getKey().toString()))
                    nameText = entry.getValue().toString();
                if ("Module-Level".equals(entry.getKey().toString()))
                    levelText = entry.getValue().toString();
                if ("Module-Tag".equals(entry.getKey().toString()))
                    tagText = entry.getValue().toString();
            }

            if (tagText == null) continue;

            // 引用或实现类的全名
            String[] tagTexts = tagText.replaceAll("[ \n]", "").split("[,;:]");

            for (String tag : tagTexts) {
                Unit unit = new Unit();
                unit.setModuleName(nameText); //放入项目名称
                unit.setClassName(tag); // 类名称 + 属性或方法名称
                // 如果没有，则默认为 5 界别
                unit.setInitLevel(levelText != null ? Integer.valueOf(levelText) : 5);
                tagList.add(unit); // 放入集合
            }

            log.info("load module: {}, count: {}, init level: {}", nameText, tagTexts.length, levelText == null ? 5 : levelText);

        }

        ClassLoader loader = new URLClassLoader(urlList.toArray(new URL[urlList.size()]));

        Thread.currentThread().setContextClassLoader(loader);

        run(tagList, loader);
    }

    private static void run(List<Unit> unitList, ClassLoader loader) {

        ModuleAssembler moduleAssembler = new ModuleAssembler(loader);
        // 分拣配置，可以多次调用
        moduleAssembler.sorting(unitList);
        // 装配和运行
        moduleAssembler.docking();
    }

    private static URL getURL(File file) {

        String path = ParseUtil.encodePath(file.getAbsolutePath());

        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (!path.endsWith("/") && file.isDirectory()) {
            path = path + "/";
        }

        try {
            // jar 包所在路径
            return new URL("file", "", path);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<Object, Object> getManifest(File file) {
        try {
            JarFile jar = new JarFile(file);
            return jar.getManifest().getMainAttributes();
        } catch (IOException e) {
            return new HashMap<>(0);
        }
    }

    // 处理当前路径
    private static String pwd(String path) {
        if (!path.contains(File.separator)) {
            return "." + File.separator + path;
        }
        return path;
    }

}
