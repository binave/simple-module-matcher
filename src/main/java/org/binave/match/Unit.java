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

import lombok.*;

import java.lang.reflect.Method;

/**
 * 与每个类、方法、属性一一对应
 * 处理有序初始化（暂定）
 *
 * @author bin jin on 2017/4/13.
 * @since 1.8
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Unit implements Comparable<Unit> {

    /**
     * 模块名称（项目名称）
     */
    private String moduleName;

    /**
     * 初始化级别
     */
    private int initLevel;

    /**
     * 类名称
     */
    private String className;

    /**
     * 接口实现 class
     */
    private Class implClass;

    /**
     * init() 方法
     */
    private Method init;

    /**
     * 排序
     */
    @Override
    public int compareTo(Unit o) {
        // 两者的顺序决定了 Collections.sort 方法输出的顺序
        return this.getInitLevel() - o.getInitLevel();
    }
}
