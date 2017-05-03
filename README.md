# simple-unit-matcher

用于启动模块项目，方便进行模块的 debug 调试。


Licensing
=========
Simple-unit-matcher is licensed under the Apache License, Version 2.0. See
[LICENSE](https://github.com/binave/simple-unit-matcher/blob/master/LICENSE) for the full
license text.

* 启动顺序
    * 扫描 jar 配置
    * 实例化接口实现类
    * 将接口实现类实例注入调用属性（循环引用检测）
        * 支持注入实现类集合。（暂时只支持 Set 接口）
    * 保存调用关系，并在下次启动后读取
    * 启动工程

* 注入支持情形
    * 对接口的静态引用
    * 对接口的引用者是其他接口的实现类
    * 对接口的引用者，被其他接口实现类引用
        * 如果代码运行中重新给桥梁类实例化，则会导致空指针错误。

注：引用在此处仅指作为类的属性。

* 模块规范 (class 文件形式)
    * 支持且仅支持 maven 项目。
    * 将提供或使用接口的模块，以依赖的形式添加到本项目的 pom 中
    * 模块项目的 pom.xml 中含有 Module-Tag 标签
        * 类全名：代表对接口的实现，对应 OSGI 的 export
        * 类全名#属性名：代表对其他模块 export 的接口的引用。对应 OSGI 的 import
        * 类全名#方法名：启动方法，一个项目全局唯一。用于启动工程
    * 启动方法：org.binave.match.BootstrapTest#main 位于 src/test/java 目录

* 模块规范 (jar 文件形式)
    * MANIFEST.MF 中包含 Module-Tag:
    * Module-Tag 的内容与与 pom 中的相同
    * 可以使用 maven-jar-plugin 将 pom 配置转换为 MANIFEST.MF 配置。
    * Module-Level 标记模块级别的实例化优先级，不标记的则放到最后加载。
    * 将 jar 文件放入指定位置（待实现）
    * 启动方法（待实现）

在工程中使用示例注解代码。
然后将以 batch 本放入工作目录并执行，会将目标 Module-Tag 标签逐个写入剪贴板。

#### 用于标注：接口实现、注入、启动方法 的注解。

```java
@Documented
@Target({TYPE, FIELD, METHOD})
@Retention(RetentionPolicy.SOURCE)
public @interface Skenlr {

Tab

Tab
//        inject, implement, bootstrap
//    }skenlr

    /**
     * 实现接口
     */
    @Documented
    @Target(TYPE)
    @Retention(RetentionPolicy.SOURCE)
    @interface implement {
    }

    /**
     * 需要注入的属性
     */
    @Documented
    @Target(FIELD)
    @Retention(RetentionPolicy.SOURCE)
    @interface inject {
    }

    /**
     * 启动方法
     */
    @Documented
    @Target(METHOD)
    @Retention(RetentionPolicy.SOURCE)
    @interface bootstrap {
    }

}

```

#### 扫描项目注解并生成 Module-Tag 标签，写入剪贴板

```batch
@echo off

for /r %~dp0 /d %%a in (*src) do call :project "%%a"

goto :eof

:project
    setlocal enabledelayedexpansion
    echo %~dp1pom.xml
    set \\\clip=
    for /f "usebackq delims=" %%a in (
        `findstr.exe /s /m "@Skenlr" "%~1\*.java"`
    ) do call :file "%%a"
    if not defined \\\clip (echo.|clip.exe) else echo.^^^<Module-Tag^^^>%\\\clip:~0,-1%^^^</Module-Tag^^^>| clip.exe
    if defined \\\clip (echo.== chip ==) else echo.-- none --
    pause>nul
    endlocal
    goto :eof

:file
    set \\\tag=none
    for /f "usebackq delims=" %%a in (
        %1
    ) do (
        set "\\\tmp=%%~a"
        set \\\tmp=!\\\tmp:"= !
        set \\\tmp=!\\\tmp:^<=!
        set \\\tmp=!\\\tmp:^>=!
        set \\\tmp=!\\\tmp:;= {!
        set \\\tmp=!\\\tmp:(= !
        set \\\tmp=!\\\tmp:static=@!
        set \\\tmp=!\\\tmp:final=@!
        set \\\tmp=!\\\tmp:private=@!
        set \\\tmp=!\\\tmp:class=@ class!
        set \\\tmp=!\\\tmp:public=@!
        set \\\tmp=!\\\tmp:  = !
        set \\\tmp=!\\\tmp:  = !
        set \\\tmp=!\\\tmp:  = !
        set \\\tmp=!\\\tmp:@ @ =@ !
        set \\\tmp=!\\\tmp:@ @ =@ !
        set \\\tmp=!\\\tmp:@ @ =@ !
        call :line !\\\tmp! 2>nul
    )
    goto :eof

:line
    call :%\\\tag% %*
    if "%~1"=="package" set \\\package=%~2
    if "%~1%~2"=="@class" set \\\class=%~3
    if "%~1"=="@Skenlr.inject" set \\\tag=inject
    if "%~1"=="@Skenlr.implement" set \\\tag=implement
    if "%~1"=="@Skenlr.bootstrap" set \\\tag=bootstrap
    goto :eof

:none
    goto :eof

:inject
    REM echo %0 %*
    if "%~1"=="@" set \\\clip=!\\\clip!%\\\package%.%\\\class%#%~3,& set \\\tag=none
    goto :eof

:implement
    REM echo %0 %*
    if "%~1%~2"=="@class" set \\\clip=!\\\clip!%\\\package%.%~3,& set \\\tag=none
    goto :eof

:bootstrap
    REM echo %0 %*
    if "%~1"=="@" set \\\clip=!\\\clip!%\\\package%.%\\\class%#%~3(),& set \\\tag=none
    goto :eof

```

#### 生成样例
注意：Module-Tag 标签中的内容用英文半角逗号分割，不允许有换行和空格。

e.g.
```xml

    <properties>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
        <maven.compiler.encoding>UTF-8</maven.compiler.encoding>
        <maven.build.timestamp.format>yyMMdd_hhmmss</maven.build.timestamp.format>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <build>
        <finalName>${project.artifactId}-${maven.build.timestamp}</finalName>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <version>2.20</version>
                    <configuration>
                        <skipTests>true</skipTests>
                    </configuration>
                </plugin>
                <plugin>
                    <!-- mvn package assembly:single -->
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-assembly-plugin</artifactId>
                    <version>3.0.0</version>
                    <configuration>
                        <descriptorRefs>
                            <descriptorRef>jar-with-dependencies</descriptorRef>
                        </descriptorRefs>
                        <archive>
                            <manifestEntries>
                                <Module-GroupId>${project.groupId}</Module-GroupId>
                                <Module-ArtifactId>${project.artifactId}</Module-ArtifactId>
                                <Module-Version>${project.version}</Module-Version>
                                <Module-Level>0</Module-Level>
                                <Module-Tag>
                                    org.name.some.oneImpl,org.name.some.tow#oneif,org.name.some.other#startfunc()
                                </Module-Tag>
                            </manifestEntries>
                        </archive>
                    </configuration>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>

```
