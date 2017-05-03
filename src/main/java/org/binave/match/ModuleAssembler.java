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


import org.binave.common.util.TypeUtil;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.*;

/**
 * 模块装配
 *
 * @author bin jin
 * @since 1.8
 */
public class ModuleAssembler {

    private ClassLoader loader;

    ModuleAssembler(ClassLoader loader) {
        this.loader = loader;
    }

    ModuleAssembler() {
        this.loader = Thread.currentThread().getContextClassLoader();
    }

    // 如果读取到存档，先初始化此属性

    // <接口类，接口实现类实例> 主接口实现缓存
    private Map<Class, Set<Object>> implObjectByInterface = new HashMap<>();

    // <类，实例> 属性对应的类：除了主接口实现类以外，仅允许成为其他类的属性的类，存在此处。
    private Map<Class, Object> objectByClass = new HashMap<>();

    // <属性所在类，属性名集合> 保存需要进行赋值的属性
    private Map<Class, Set<String>> fieldSetByOwnerClass = new HashMap<>();

    // <属性所在类，<属性名，属性>> 扫描过属性的类
    private Map<Class, Map<String, Field>> fieldNameCacheByOwnerClass = new HashMap<>();

    // 开启方法
    private Method startMethod;
    private Class startClass;

    private List<Unit> units = new ArrayList<>();

    /**
     * 装配运行
     */
    void docking() {
        if (startMethod == null)
            throw new RuntimeException("Start method not init");

        try {
            int fieldInitCount = 0;

            for (Class c : fieldSetByOwnerClass.keySet()) {
                for (String fieldName : fieldSetByOwnerClass.get(c)) {
                    Field field = fieldNameCacheByOwnerClass.get(c).get(fieldName);

                    if (field == null) throw new RuntimeException(
                            new NoSuchFieldException(c.getName() + "#" + fieldName)
                    );

                    // 如果是 final 属性，则报错
                    if (Modifier.isFinal(field.getModifiers()))
                        throw new RuntimeException(c.getName() + "#" + fieldName + " is final");

                    Class fieldType = field.getType();

                    // 必须使用接口进行引用，包括集合在内
                    if (!fieldType.isInterface())
                        throw new RuntimeException(c.getName() + "#" + fieldName + " not interface");

                    // 判断是否是集合类的 todo Collection
                    boolean isSet = Set.class.isAssignableFrom(fieldType);

                    // 如果是集合（不支持自定义集合）
                    fieldType = isSet ? (Class) TypeUtil.getGenericTypes(field)[0] : fieldType;

                    // 测试有没有内容
                    Set<Object> implSet = implObjectByInterface.get(fieldType);

                    if (implSet == null || implSet.isEmpty())
                        throw new RuntimeException("this class not export :" + c.getName() + "#" + fieldName);

                    Object obj;

                    // 如果是静态属性
                    if (Modifier.isStatic(field.getModifiers())) {
                        obj = null;
                    } else {
                        obj = objectByClass.get(c); // 从实现类中，根据类型拿取实例（此时实现类为主接口实现类）
                        if (obj == null) {// 说明属性所在类，不是主接口实现类。以属性所在类为主接口实现类的属性尝试进行处理
                            obj = getMainInterfaceFieldObject(c);
                            if (obj == null) throw new RuntimeException("not implement object or field not static: " +
                                    c.getName() + "#" + fieldName);
                        }
                    }

                    // 被引用的属性，是其他主接口实现类实例
                    field.set(obj, isSet ? implSet : implSet.iterator().next());
                    ++fieldInitCount;
                }
            }

            // 简单判断一下引用是否都已经注入了
            if (fieldInitCount < implObjectByInterface.size() || fieldInitCount < fieldSetByOwnerClass.size())
                throw new RuntimeException("field not inject all");

        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        // 根据初始化级别排序
        Collections.sort(this.units);

        // 按照 level 执行 init 方法
        for (Unit unit : units) {
            Method method = unit.getInit();
            if (method != null) {
                Object obj = objectByClass.get(unit.getImplClass());
                if (obj == null) throw new RuntimeException("obj not found: " +
                        unit.getClassName() + "#" + method.getName());
                try {
                    method.invoke(obj); // init
                } catch (IllegalAccessException | InvocationTargetException e) {
                    unpackRuntimeException(e);
                }
            }
        }

        // 判断，启动类是否已经实例化
        Object startObj = getInstance(startClass);

        // 启动方法
        try {
            startMethod.invoke(startObj);
        } catch (IllegalAccessException | InvocationTargetException e) {
            unpackRuntimeException(e);
        }
    }

    // 异常脱壳
    private void unpackRuntimeException(Throwable e) {
        Throwable throwable = e.getCause();
        if (throwable == null) throwable = e;
        if (throwable instanceof RuntimeException) {
            if (throwable.getCause() == null) {
                throw (RuntimeException) throwable;
            } else unpackRuntimeException(throwable);
        } else throw new RuntimeException(throwable);
    }

    private Object getMainInterfaceFieldObject(Class type) {
        // 拿到缓存的属性所在类（已经是引用类的二级所在类）

        for (Class c : fieldNameCacheByOwnerClass.keySet()) {
            for (Field f : fieldNameCacheByOwnerClass.get(c).values()) {
                // 搜索，现存的类当中的属性匹配问题
                if (type == f.getType()) {
                    // 找到所属类，测试此类是否为接口实现类
                    if (objectByClass.containsKey(c)) {
                        // 进行赋值操作
                        try {
                            Object obj = getInstance(type);
                            f.set(objectByClass.get(c), obj);
                            return obj;
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }
        return null;
    }

    // 读取配置，分拣
    void sorting(List<Unit> units) {

        if (units == null || units.isEmpty())
            throw new RuntimeException("add conf is empty");

        // 线程池
        ExecutorService executor = Executors.newCachedThreadPool();

        List<InitImpl> impls = new ArrayList<>();
        for (Unit unit : units) {
            impls.add(new InitImpl(unit));
        }

        String methodName = null;
        Class methodOwnerClass = null;

        List<Future<MethodUnit>> futureList;

        try {
            // 添加并执行
            futureList = executor.invokeAll(impls);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // 等待全部线程完成
        executor.shutdown();

        for (Future<MethodUnit> future : futureList) {
            MethodUnit unit;
            try {
                unit = future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
            if (unit != null) {
                if (methodName != null)
                    throw new RuntimeException("start method too much: " +
                            methodOwnerClass + "#" + methodName + "()");
                methodName = unit.getMethodName();
                methodOwnerClass = unit.getMethodOwnerClass();
            }
        }

        // 启动方法
        if (methodName != null) {
            if (startMethod == null) {
                // 尝试当作方法获取
                List<Method> methods = TypeUtil.prefixPublicMethods(methodOwnerClass, methodName).get(methodName);

                if (methods == null)
                    throw new IllegalArgumentException("method " + methodName + "not found in " + methodOwnerClass.getName());

                startMethod = methods.get(0);

                if (!objectByClass.containsKey(methodOwnerClass)) {
                    // 如果不是实现的接口类
                    Object obj = getInstance(methodOwnerClass);
                    objectByClass.put(methodOwnerClass, obj);
                }
                // 直接缓存
                startClass = methodOwnerClass;
//                removeFromList(fieldSetByOwnerClass, ownerClass, fieldName);
            } else
                throw new RuntimeException("Start method not single: " + methodOwnerClass.getName() + "." + methodName);
        }

        // 获得等待处理的类迭代器
        for (Class ownerClass : fieldSetByOwnerClass.keySet()) {
            getFieldsMapCache(ownerClass);
        }

        this.units.addAll(units);
    }

    /**
     * 多线程初始化返回值
     */
    private class MethodUnit {

        private String methodName;
        private Class methodOwnerClass;

        MethodUnit(String methodName, Class methodOwnerClass) {
            this.methodName = methodName;
            this.methodOwnerClass = methodOwnerClass;
        }

        String getMethodName() {
            return methodName;
        }

        Class getMethodOwnerClass() {
            return methodOwnerClass;
        }
    }

    /**
     * 异步执行主接口初始化
     */
    private class InitImpl implements Callable<MethodUnit> {

        private Unit unit;

        private InitImpl(Unit unit) {
            this.unit = unit;
        }

        @Override
        public MethodUnit call() throws Exception {

            String fullName = unit.getClassName();

            MethodUnit mUnit = null;

            if (!fullName.contains("#")) {

                // 处理主接口实现
                initMainInterfaceImpl(unit);
            } else {

                // 缓存 属性路径，todo 会添加一些其他处理
                // 初步将接口实现与需要引用的方法分离

                int index = fullName.indexOf("#");
                Class ownerClass = getClassFromCache(fullName.substring(0, index)); // 属性所属类
                String fieldName = fullName.substring(index + 1); // 属性名称

                if (fieldName.contains("(")) {
                    // 启动方法
                    mUnit = new MethodUnit(
                            fieldName.substring(0, fieldName.indexOf("(")),
                            ownerClass
                    );
                } else {

                    // 根据赋值属性所在类名称，进行缓存
                    Set<String> fieldSet = fieldSetByOwnerClass.get(ownerClass);

                    if (fieldSet == null) {
                        fieldSet = new HashSet<>();
                        fieldSetByOwnerClass.put(ownerClass, fieldSet);
                    }

                    fieldSet.add(fieldName);
                }
            }
            return mUnit;
        }
    }

    /**
     * 初始化实现类实例，并缓存
     */
    private void initMainInterfaceImpl(Unit unit) {

        String fullClassName = unit.getClassName();

        if (!fullClassName.endsWith("Impl"))
            throw new RuntimeException("Name Is NOT criterion: " + fullClassName);

        // 获得配置对应接口实现类
        Class impl = getClassFromCache(fullClassName);

        // 放入对象
        unit.setImplClass(impl);

        try {
            // 设置无参 init 方法（如果有）
            unit.setInit(impl.getMethod("init"));
        } catch (NoSuchMethodException ignored) {
        }

        boolean find = false;

        for (Class _interface : TypeUtil.getInterfaces(impl)) {
            // 通过类名称进行匹配，此处需要落实到规范之中。
            if (impl.getSimpleName().contains(_interface.getSimpleName())) {
                find = true;
                Object obj = getInstance(impl);
                synchronized (_interface) {
                    // 获得接口实现对象，此处仅为单例对象
                    Set<Object> implSet = implObjectByInterface.get(_interface);
                    if (implSet == null) {
                        implSet = new HashSet<>();
                        implObjectByInterface.put(_interface, implSet);
                    }
                    implSet.add(obj);
                }
                // 缓存实现类全部属性
                getFieldsMapCache(impl);
                // 将类全名放入 set
            }
        }

        // 没有符合规范的接口
        if (!find)
            throw new RuntimeException("interface not found :" + fullClassName + ", interface:" + Arrays.toString(TypeUtil.getInterfaces(impl)));
    }

    private Map<String, Field> getFieldsMapCache(Class ownerClass) {
        Map<String, Field> fieldMap = fieldNameCacheByOwnerClass.get(ownerClass);
        if (fieldMap == null) {
            // 获得类的全量属性
            fieldMap = TypeUtil.getFieldMap(ownerClass);
            fieldNameCacheByOwnerClass.put(ownerClass, fieldMap);
        }
        return fieldMap;
    }

    // <类全名，类>
    private Map<String, Class> classCache = new HashMap<>();

    private Class getClassFromCache(String classFullName) {
        Class type = classCache.get(classFullName);
        if (type == null) {
            try {
                type = getClass(classFullName);
                classCache.put(classFullName, type);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return type;
    }


    private Class getClass(String fullClassName) throws ClassNotFoundException {

        if (fullClassName.contains("/")) throw new RuntimeException("classname format error: " + fullClassName);

        // like: org.apache.json.Clear
        return loader.loadClass(fullClassName);
    }

//    private <K, O, L extends Collection<O>> void addInList(Map<K, L> map, K key, O obj) {
//        if (!map.containsKey(key)) {
//            L set = (L) new HashSet();
//            set.add(obj);
//            map.put(key, set);
//        } else map.getCaseInsensitive(key).add(obj);
//    }

//    private <K, O, L extends Collection<O>> void removeFromList(Map<K, L> map, K key, O obj) {
//        map.getCaseInsensitive(key).remove(obj);
//        if (map.getCaseInsensitive(key).isEmpty()) map.remove(key);
//    }

    private Object getInstance(Class type) {
        Object obj = objectByClass.get(type);
        if (obj == null) {
            try {
                obj = type.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(type.toString(), e);
            }
            objectByClass.put(type, obj);
        }
        return obj;
    }

}
