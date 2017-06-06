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

/**
 * @author by bin jin on 2017/6/2.
 * @since 1.8
 */
public interface SimpleLog {

    int ERROR = 1;
    int WARN = 2;
    int INFO = 3;
    int DEBUG = 4;

    /**
     * 设置日志级别
     * @return old level
     */
    int setLevel(int level);

    boolean isDebugEnabled();

    String debug(String format, Object... arguments);

    boolean isInfoEnabled();

    String info(String format, Object... arguments);

    boolean isWarnEnabled();

    String warn(String format, Object... arguments);

    String error(String format, Object... arguments);

    String error(Throwable t, String format, Object... arguments);

}
