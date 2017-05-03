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

import org.binave.util.CharUtil;
import org.junit.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动类
 * 基于 pom 启动
 * For IntelliJ IDEA only
 *
 * @author bin jin on 2017/3/18.
 * @since 1.8
 */
public class BootstrapTest {

    private List<Unit> getJars() {

        List<Unit> tagList = new ArrayList<>();

        // 从 pom 中引用的项目依赖，会出现在 classpath 中，凭此找到对应的 pom 文件，并解析
        for (String path : System.getProperty("java.class.path").split("" + File.pathSeparatorChar)) {
            if (!path.endsWith("jar") && !path.contains("simple-module-matcher")) {

                String pomPath = path.replace("target\\classes", "pom.xml"); // 获得 pom 路径

                String pomText = CharUtil.readText(pomPath); // 获得 pom 正文
                String tagText = CharUtil.getLabelText(pomText, "Module-Tag"); // 获得 Unit-Tag 标签内容

                if (tagText == null) continue; // 未配置 Unit-Tag

                String nameText = CharUtil.getLabelText(pomText, "artifactId");
                String levelText = CharUtil.getLabelText(pomText, "Module-Level");

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

                System.out.println("load module: " + nameText + ", count: " + tagTexts.length + ", init level: " + (levelText == null ? 5 : levelText));
            }
        }

        return tagList;
    }

    @Test
    public void main() {

        ModuleAssembler moduleAssembler = new ModuleAssembler();
        // 分拣配置，可以多次调用
        moduleAssembler.sorting(getJars());
        // 装配和运行
        moduleAssembler.docking();
    }
}
