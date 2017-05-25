package org.binave.match;

import org.binave.common.util.CharUtil;
import org.binave.common.util.MonitorUtil;

import java.io.PrintStream;

/**
 * @author by bin jin on 2017/6/2.
 * @since 1.8
 */
public class SimpleLogFactory {

    public static SimpleLog getLog(Class type) {
        return new SimpleLogImpl(type.getName());
    }

    private static int globalLevel = SimpleLog.INFO; // 全局默认是 info

    public static int setGlobalLevel(int level) {
        int oldGlobalLevel = globalLevel;
        globalLevel = level;
        return oldGlobalLevel;
    }

    private static class SimpleLogImpl implements SimpleLog {

        private String className;
        private int level;

        SimpleLogImpl(String className) {
            this.className = className;
        }

        @Override
        public int setLevel(int level) {
            // 没有级别
            if (level == 0) return this.level;
            int oldLevel = this.level;
            this.level = level;
            return oldLevel;
        }

        @Override
        public boolean isDebugEnabled() {
            return this.level == 0 ? globalLevel >= DEBUG : this.level >= DEBUG;
        }

        @Override
        public String debug(String format, Object... arguments) {
            return isDebugEnabled() ? log(DEBUG, format, arguments) : null;
        }

        @Override
        public boolean isInfoEnabled() {
            return this.level == 0 ? globalLevel >= INFO : this.level >= INFO;
        }

        @Override
        public String info(String format, Object... arguments) {
            return isInfoEnabled() ? log(INFO, format, arguments) : null;
        }

        @Override
        public boolean isWarnEnabled() {
            return this.level == 0 ? globalLevel >= WARN : this.level >= WARN;
        }

        @Override
        public String warn(String format, Object... arguments) {
            return isWarnEnabled() ? log(WARN, format, arguments) : null;
        }

        @Override
        public String error(String format, Object... arguments) {
            return log(ERROR, format, arguments);
        }

        @Override
        public String error(Throwable t, String format, Object... arguments) {
            t.printStackTrace();
            return CharUtil.format("{} - {}", log(ERROR, format, arguments), t.getMessage());
        }

        private String log(int level, String format, Object... args) {
            PrintStream print = level <= WARN ? System.err : System.out;
            String tag;
            if (level == INFO) {
                tag = "{} [{}] INFO   {} - {}";
            } else if (level == DEBUG) {
                tag = "{} [{}] DEBUG {} - {}";
            } else if (level == ERROR) {
                tag = "{} [{}] ERROR {} - {}";
            } else if (level == WARN) {
                tag = "{} [{}] WARN  {} - {}";
            } else throw new IllegalArgumentException();

            String msg = CharUtil.format(
                    tag,
                    MonitorUtil.getFormatTime("YYYY-MM-dd HH:mm:ss.SSS"),
                    Thread.currentThread().getName(),
                    className,
                    CharUtil.format(format, args)
            );
            print.println(msg);
            print.flush();
            return msg;
        }

    }

}
