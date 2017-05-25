# simple-unit-matcher

用于启动模块项目，方便进行模块的 debug 调试。


Licensing
=========
Simple-unit-matcher is licensed under the Apache License, Version 2.0. See
[LICENSE](https://github.com/binave/simple-unit-matcher/blob/master/LICENSE) for the full
license text.

![image](http://www.plantuml.com/plantuml/proxy?idx=0&src=https://raw.githubusercontent.com/binave/simple-module-matcher/master/README.uml)

|标识|名称|作用
|:---:|:---:|---
|container|容器（节点）|管理 loader 生命周期。<br/>管理 loader 关系（注1）。<br/>隐藏通信实现。<br/>隐藏负载均衡。
|loader|加载器|加载 module。<br/>隔离 module 间引用。
|module|模块|普通 jar 包
|regedit|注册|通过接口向 loader 注册自己的实现
|service|服务|通过接口向 loader 获得服务实现

![image](http://www.plantuml.com/plantuml/proxy?idx=1&src=https://raw.githubusercontent.com/binave/simple-module-matcher/master/README.uml)

* 启动顺序
    * 扫描 jar 配置
    * 实例化接口实现类
    * 将接口实现类实例注入调用属性（循环引用检测）
        * 支持注入实现类集合。（暂时只支持 Set 接口）
    * 保存调用关系，并在下次启动后读取
    * 按照优先级执行实现类的 init() 方法
    * 执行启动方法

* 注入支持情形
    * 对接口的静态引用
    * 对接口的引用者是其他接口的实现类
    * 对接口的引用者，被其他接口实现类引用
        * 如果代码运行中重新给桥梁类实例化，则会导致空指针错误。

注：引用在此处仅指作为类的属性。

* 模块规范 (jar 文件形式)
    * MANIFEST.MF 中包含 Module-Tag:
    * Module-Tag 的内容与与 pom 中的相同
    * 可以使用 maven-jar-plugin 将 pom 配置转换为 MANIFEST.MF 配置。
    * Module-Level 标记模块级别的实例化优先级，不标记的则放到最后加载。
    * 将模块 jar 文件放入本工程 jar 所在 lib 文件夹下。
    * 启动方法: org.binave.match.Bootstrap#main 位于 src/test/java 目录

* 模块规范 (测试方式)
    * 支持且仅支持 maven 项目。
    * 将提供或使用接口的模块，以依赖的形式添加到本项目的 pom 中
    * 模块项目的 pom.xml 中含有 Module-Tag 标签
        * 类全名：代表对接口的实现，对应 OSGI 的 export
        * 类全名#属性名：代表对其他模块 export 的接口的引用。对应 OSGI 的 import
        * 类全名#方法名：启动方法，一个项目全局唯一。用于启动工程
    * 启动方法：org.binave.match.BootstrapTest#main 位于 src/test/java 目录

在工程中使用示例注解标记代码。
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

for /r %1 /d %%a in (*src) do call :project "%%a"

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
        set \\\tmp=!\\\tmp:abstract=@!
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
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.0.0</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                        <configuration>
                            <transformers>
                                <transformer
                                        implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <manifestEntries>
                                        <Module-GroupId>${project.groupId}</Module-GroupId>
                                        <Module-ArtifactId>${project.artifactId}</Module-ArtifactId>
                                        <Module-Version>${project.version}</Module-Version>
                                        <Module-Level>0</Module-Level>
                                        <Module-Tag>
                                            org.name.some.oneImpl,org.name.some.tow#oneif,org.name.some.other#startfunc()
                                        </Module-Tag>
                                    </manifestEntries>
                                </transformer>
                            </transformers>
                            <createDependencyReducedPom>false</createDependencyReducedPom>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>2.20</version>
                <configuration>
                    <skipTests>true</skipTests>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-release-plugin</artifactId>
                <version>2.5.3</version>
            </plugin>
        </plugins>
    </build>

```

#### 打包脚本

```batch
@echo off
setlocal
set c=0
for %%a in (
    %*
) do set /a c+=1& call :main %%a

echo %c% finish.
endlocal
call lib igui && pause>nul
goto :eof

:main
    pushd %cd%
        if not exist "%~1" goto end
        cd /d %1
        if not exist .\pom.xml goto end
        call mvn clean && call mvn package assembly:single || goto end
    popd

    echo.

    setlocal
        set i=
        set r=%random%%random%
        for /r %1 %%a in (
            *-with-dependencies.jar
        ) do copy "%%~a" "%~dp0%r%" && set "i=%%~nxa"
        rename "%~dp0%r%" "%i:-jar-with-dependencies=%"
    endlocal

    echo.
    goto :eof

:end
    popd
    echo.Error
    goto :eof
```
