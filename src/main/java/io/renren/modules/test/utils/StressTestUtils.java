package io.renren.modules.test.utils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.renren.common.exception.RRException;
import io.renren.common.utils.SpringContextUtils;
import io.renren.modules.sys.service.SysConfigService;
import io.renren.modules.test.jmeter.JmeterRunEntity;
import io.renren.modules.test.jmeter.calculator.LocalSamplingStatCalculator;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.jmeter.util.JMeterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * 性能测试的工具类，同时用于读取配置文件。
 * 配置文件在数据库中配置
 */
//@ConfigurationProperties(prefix = "test.stress")
@Component
public class StressTestUtils {

    Logger logger = LoggerFactory.getLogger(getClass());

    private static SysConfigService sysConfigService = (SysConfigService) SpringContextUtils.getBean("sysConfigService");
    public static String xslFilePath = "classpath:config/jmeter.results.zyanycall.xsl";

    private static final String OS_NAME = System.getProperty("os.name");
    public static final String OS_NAME_LC = OS_NAME.toLowerCase(java.util.Locale.ENGLISH);

    //0：初始状态  1：正在运行  2：成功执行  3：运行出现异常
    public static final Integer INITIAL = 0;
    public static final Integer RUNNING = 1;
    public static final Integer RUN_SUCCESS = 2;
    public static final Integer RUN_ERROR = 3;
    public static final Integer NO_FILE = 4;

    /**
     * 是否需要测试报告的状态标识
     */
    //0：保存测试报告原始文件  1：不需要测试报告
    public static final Integer NEED_REPORT = 0;
    public static final Integer NO_NEED_REPORT = 1;

    /**
     * 是否需要前端Chart监控的状态标识
     */
    //0：需要前端监控  1：不需要前端监控
    public static final Integer NEED_WEB_CHART = 0;
    public static final Integer NO_NEED_WEB_CHART = 1;

    /**
     * 是否开启调试的状态标识
     */
    //0：默认关闭调试  1：开启调试
    public static final Integer NO_NEED_DEBUG = 0;
    public static final Integer NEED_DEBUG = 1;

    //0：禁用  1：启用  2：进行中
    public static final Integer DISABLE = 0;
    public static final Integer ENABLE = 1;
    public static final Integer PROGRESSING = 2;

    /**
     * 针对每一个fileId，存储一份
     * 用于存储每一个用例的计算结果集合。
     * 使用google的缓存技术，让这部分全局数据多一份时间控制保障。
     */
    public static Cache<Long, Map<String, LocalSamplingStatCalculator>> samplingStatCalculator4File =
//            new HashMap<>();
            CacheBuilder.newBuilder()
                    .maximumSize(5000) // 设置缓存的最大容量
                    .expireAfterAccess(30, TimeUnit.MINUTES) // 设置缓存在写入一分钟后失效
                    .concurrencyLevel(10) // 设置并发级别
//            .recordStats() // 开启缓存统计
                    .build();

    /**
     * 针对每一个fileId，存储一份Jmeter的Engines，用于指定的用例启动和停止。
     * 如果不使用分布式节点，则Engines仅包含master主节点。
     * 默认是使用分布式的，则Engines会包含所有有效的分布式节点的Engine。
     */
    public static Map<Long, JmeterRunEntity> jMeterEntity4file = new HashMap();

    /**
     * 主进程Master内保存的一些状态，主要用于分布式的压测操作服务。
     */
    public static Cache<String, String> jMeterStatuses = CacheBuilder.newBuilder()
            .maximumSize(5000) // 设置缓存的最大容量
            .expireAfterAccess(30, TimeUnit.MINUTES) // 设置缓存在写入一分钟后失效
            .concurrencyLevel(10) // 设置并发级别
//            .recordStats() // 开启缓存统计
            .build();

    /**
     * Jmeter在Master节点的绝对路径
     */
    public final static String MASTER_JMETER_HOME_KEY = "MASTER_JMETER_HOME_KEY";

    /**
     * Jmeter在Master节点存储用例信息的绝对路径
     * 存放用例的总目录，里面会细分文件存放用例及用例文件
     * Jmeter节点机需要在/etc/bashrc中配置JAVA_HOME，同时source /etc/bashrc生效
     */
    public final static String MASTER_JMETER_CASES_HOME_KEY = "MASTER_JMETER_CASES_HOME_KEY";

    /**
     * 如果配置了Jmeter脚本启动，则额外开启Jmeter进程运行测试用例脚本及分布式程序。
     * 分布式程序可以取消ssl校验。
     * 同时仅支持Jmeter+InfluxDB+Grafana的实时监控。
     * 如果没有配置Jmeter脚本启动，则使用web本身自带的Jmeter功能。
     * 支持自带的ECharts实时监控。
     * 默认是false，即使用web程序进程来启动Jmeter-master程序。
     */
    public final static String MASTER_JMETER_USE_SCRIPT_KEY = "MASTER_JMETER_USE_SCRIPT_KEY";

    /**
     * 如果配置了本地生成测试报告（不包括调试报告），则使用web程序进程生成测试报告。
     * 默认是true，即配置为本地web程序进程，好处是可以多线程并发生成测试报告。
     * 对应的，如果为false则使用Jmeter_home的脚本生成测试报告，无法同时生成多个测试报告。
     */
    public final static String MASTER_JMETER_GENERATE_REPORT_KEY = "MASTER_JMETER_GENERATE_REPORT_KEY";

    /**
     * 上传文件时，遇到同名文件是替换还是报错，默认是替换为true
     */
    public final static String MASTER_JMETER_REPLACE_FILE_KEY = "MASTER_JMETER_REPLACE_FILE_KEY";

    /**
     * 脚本的默认最长定时执行时间，是否开启，默认是为true，开启。
     * 具体的执行时间，由脚本文件字段来配置。单位是秒，对应的是Jmeter脚本的duration字段。
     * 该功能添加的原因是应对脚本执行，但是忘记了关闭的情况，这样会导致浪费系统资源，尤其是线上操作尤其危险。
     */
    public final static String SCRIPT_SCHEDULER_DURATION_KEY = "SCRIPT_SCHEDULER_DURATION_KEY";

    public static String getJmeterHome() {
        return sysConfigService.getValue(MASTER_JMETER_HOME_KEY);
    }

    public String getCasePath() {
        return sysConfigService.getValue(MASTER_JMETER_CASES_HOME_KEY);
    }

    public boolean isUseJmeterScript() {
        return Boolean.parseBoolean(sysConfigService.getValue(MASTER_JMETER_USE_SCRIPT_KEY));
    }

    public boolean isReplaceFile() {
        return Boolean.parseBoolean(sysConfigService.getValue(MASTER_JMETER_REPLACE_FILE_KEY));
    }

    public boolean isMasterGenerateReport() {
        return Boolean.parseBoolean(sysConfigService.getValue(MASTER_JMETER_GENERATE_REPORT_KEY));
    }

    public boolean isScriptSchedulerDurationEffect() {
        int duration = getScriptSchedulerDuration();
        if (duration > 0) {
            return true;
        }
        return false;
    }

    public static Integer getScriptSchedulerDuration() {
        try {
            if (StringUtils.isBlank(sysConfigService.getValue(SCRIPT_SCHEDULER_DURATION_KEY))) {
                return 0;
            }
            // 对遗留的false也做一下处理
            if ("false".equalsIgnoreCase(sysConfigService.getValue(SCRIPT_SCHEDULER_DURATION_KEY))) {
                return 0;
            }
            return Integer.parseInt(sysConfigService.getValue(SCRIPT_SCHEDULER_DURATION_KEY));
        } catch (Exception e) {
            return 3600;
        }
    }

    public static String getSuffix4() {
        String currentTimeStr = System.currentTimeMillis() + "";
        return currentTimeStr.substring(currentTimeStr.length() - 4);
    }

    /**
     * 获取Jmeter的bin目录
     */
    public String getJmeterHomeBin() {
        return getJmeterHome() + File.separator + "bin";
    }

    /**
     * 根据操作系统信息获取可以执行的jmeter主程序
     */
    public String getJmeterExc() {
        String jmeterExc = "jmeter";
        if (OS_NAME_LC.startsWith("windows")) {
            jmeterExc = "jmeter.bat";
        }
        return jmeterExc;
    }

    /**
     * 根据操作系统信息获取可以停止的jmeter主程序
     */
    public String getJmeterStopExc() {
        String jmeterExc = "shutdown.sh";
        if (OS_NAME_LC.startsWith("windows")) {
            jmeterExc = "shutdown.cmd";
        }
        return jmeterExc;
    }

    /**
     * 为前台的排序和数据之间做适配
     */
    public static Map<String, Object> filterParms(Map<String, Object> params) {
        if (params.containsKey("sidx") && params.get("sidx") != null) {
            String sidxValue = params.get("sidx").toString();

            if ("caseid".equalsIgnoreCase(sidxValue)) {
                params.put("sidx", "case_id");
            } else if ("addTime".equalsIgnoreCase(sidxValue)) {
                params.put("sidx", "add_time");
            } else if ("updateTime".equalsIgnoreCase(sidxValue)) {
                params.put("sidx", "update_time");
            } else if ("fileId".equalsIgnoreCase(sidxValue)) {
                params.put("sidx", "file_id");
            } else if ("reportId".equalsIgnoreCase(sidxValue)) {
                params.put("sidx", "report_id");
            } else if ("slaveId".equalsIgnoreCase(sidxValue)) {
                params.put("sidx", "slave_id");
            } else if ("fileSize".equalsIgnoreCase(sidxValue)) {
                params.put("sidx", "file_size");
            }
        }
        return params;
    }

    /**
     * 获取上传文件的md5
     */
    public String getMd5(MultipartFile file) throws IOException {
        return DigestUtils.md5Hex(file.getBytes());
    }

    /**
     * 获取文件的MD5值，远程节点机也是通过MD5值来判断文件是否重复及存在，所以就不使用其他算法了。
     */
    public String getMd5ByFile(String filePath) throws IOException {
        FileInputStream fis = new FileInputStream(filePath);
        return DigestUtils.md5Hex(IOUtils.toByteArray(fis));
    }

    /**
     * 保存文件
     */
    public void saveFile(MultipartFile multipartFile, String filePath) {
        try {
            File file = new File(filePath);
            FileUtils.forceMkdirParent(file);
            multipartFile.transferTo(file);
        } catch (IOException e) {
            throw new RRException("保存文件异常失败", e);
        }
    }

    /**
     * 判断当前是否存在正在执行的脚本
     */
    public static boolean checkExistRunningScript() {
        for (JmeterRunEntity jmeterRunEntity : jMeterEntity4file.values()) {
            if (jmeterRunEntity.getRunStatus().equals(RUNNING)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 设置Jmeter运行环境相关的配置，如配置文件的加载，当地语言环境等。
     */
    public void setJmeterProperties() {
        String jmeterHomeBin = getJmeterHomeBin();
        JMeterUtils.loadJMeterProperties(jmeterHomeBin + File.separator + "jmeter.properties");
        JMeterUtils.setJMeterHome(getJmeterHome());
        JMeterUtils.initLocale();

        Properties jmeterProps = JMeterUtils.getJMeterProperties();

        // Add local JMeter properties, if the file is found
        String userProp = JMeterUtils.getPropDefault("user.properties", ""); //$NON-NLS-1$
        if (userProp.length() > 0) { //$NON-NLS-1$
            File file = JMeterUtils.findFile(userProp);
            if (file.canRead()) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    Properties tmp = new Properties();
                    tmp.load(fis);
                    jmeterProps.putAll(tmp);
                } catch (IOException e) {
                }
            }
        }

        // Add local system properties, if the file is found
        String sysProp = JMeterUtils.getPropDefault("system.properties", ""); //$NON-NLS-1$
        if (sysProp.length() > 0) {
            File file = JMeterUtils.findFile(sysProp);
            if (file.canRead()) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    System.getProperties().load(fis);
                } catch (IOException e) {
                }
            }
        }

        jmeterProps.put("jmeter.version", JMeterUtils.getJMeterVersion());
    }

    /**
     * 为调试模式动态设置Jmeter的结果文件格式，让jtl包含必要的调试信息。
     * 这些信息会显著影响压力机性能，所以仅供调试使用。
     * 同时这些配置仅对当前进程即master节点生效。
     */
    public void setJmeterOutputFormat() {
        Properties jmeterProps = JMeterUtils.getJMeterProperties();
        jmeterProps.put("jmeter.save.saveservice.label", "true");
        jmeterProps.put("jmeter.save.saveservice.response_data", "true");
        jmeterProps.put("jmeter.save.saveservice.response_data.on_error", "true");
        jmeterProps.put("jmeter.save.saveservice.response_message", "true");
        jmeterProps.put("jmeter.save.saveservice.successful", "true");
        jmeterProps.put("jmeter.save.saveservice.thread_name", "true");
        jmeterProps.put("jmeter.save.saveservice.time", "true");
        jmeterProps.put("jmeter.save.saveservice.subresults", "true");
        jmeterProps.put("jmeter.save.saveservice.assertions", "true");
        jmeterProps.put("jmeter.save.saveservice.latency", "true");
        jmeterProps.put("jmeter.save.saveservice.connect_time", "true");
        jmeterProps.put("jmeter.save.saveservice.samplerData", "true");
        jmeterProps.put("jmeter.save.saveservice.responseHeaders", "true");
        jmeterProps.put("jmeter.save.saveservice.requestHeaders", "true");
        jmeterProps.put("jmeter.save.saveservice.encoding", "true");
        jmeterProps.put("jmeter.save.saveservice.bytes", "true");
        jmeterProps.put("jmeter.save.saveservice.url", "true");
        jmeterProps.put("jmeter.save.saveservice.filename", "true");
        jmeterProps.put("jmeter.save.saveservice.hostname", "true");
        jmeterProps.put("jmeter.save.saveservice.thread_counts", "true");
        jmeterProps.put("jmeter.save.saveservice.sample_count", "true");
        jmeterProps.put("jmeter.save.saveservice.idle_time", "true");
    }

    /**
     * 为测试报告和调试报告提供的删除jmx的生成目录方法。
     * 如果删除的测试报告是测试脚本唯一的测试报告，则将目录也一并删除。
     */
    public void deleteJmxDir(String reportPath) {
        String jmxDir = reportPath.substring(0, reportPath.lastIndexOf(File.separator));
        File jmxDirFile = new File(jmxDir);
        if (jmxDirFile.exists() && FileUtils.sizeOf(jmxDirFile) == 0L) {
            FileUtils.deleteQuietly(jmxDirFile);
        }
    }

    public void pause(long ms) {
        try {
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 毫秒转化时分秒毫秒
     */
    public static String formatTime(Long ms) {
        Integer ss = 1000;
        Integer mi = ss * 60;
        Integer hh = mi * 60;
        Integer dd = hh * 24;

        Long day = ms / dd;
        Long hour = (ms - day * dd) / hh;
        Long minute = (ms - day * dd - hour * hh) / mi;
        Long second = (ms - day * dd - hour * hh - minute * mi) / ss;
        Long milliSecond = ms - day * dd - hour * hh - minute * mi - second * ss;

        StringBuffer sb = new StringBuffer();
        if (day > 0) {
            sb.append(day + "天");
        }
        if (hour > 0) {
            sb.append(hour + "小时");
        }
        if (minute > 0) {
            sb.append(minute + "分钟");
        }
        if (second > 0) {
            sb.append(second + "秒");
        }
        if (milliSecond > 0) {
            sb.append(milliSecond + "毫秒");
        }
        return sb.toString();
    }

    /**
     * 创建shell文件
     * @param path
     * @param strs
     */
    public void createShell(String path, String... strs) {

        if (strs == null) {
            logger.error("strs is null");
            return;
        }

        File sh = new File(path);
        if (sh.exists()) {
            sh.delete();
        }
        BufferedWriter bf = null;
        try {
            sh.createNewFile();
            sh.setExecutable(true);
            FileWriter fw = new FileWriter(sh);
            bf = new BufferedWriter(fw);

            for (int i = 0; i < strs.length; i++) {
                bf.write(strs[i]);

                if (i < strs.length - 1) {
                    bf.newLine();
                }
            }
        } catch (IOException e) {
            logger.error("createShell 遇到了问题！", e);
        } finally {
            try {
                bf.flush();
                bf.close();
            } catch (IOException e) {
                logger.error("createShell 遇到了流关闭问题！", e);
            }
        }

    }

    /**
     * 创建shell文件
     *
     * @param strs
     * @return
     */
    public File createShell(String... strs) {
        if (strs == null) {
            logger.error("strs is null");
            return null;
        }

        File sh = null;
        try {
            sh = File.createTempFile("temp-sh", ".sh");
            String path = sh.getAbsolutePath();
            createShell(path, strs);
        } catch (IOException e) {
            logger.error("createShell 创建临时文件遇到了问题！", e);
        }
        return sh;
    }

    /**
     * 执行shell
     *
     * @param shellPath
     * @return
     * @throws Exception
     */
    public String runShell(String shellPath) throws Exception {

        if (shellPath == null || shellPath.equals("")) {
            return "shell path is empty";
        }
        Process ps = Runtime.getRuntime().exec(shellPath);
        ps.waitFor();

        BufferedReader br = new BufferedReader(new InputStreamReader(ps.getInputStream()));
        StringBuffer sb = new StringBuffer();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line).append("\n");
        }
        String result = sb.toString();
        br.close();
        return result;
    }
}