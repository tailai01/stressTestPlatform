**项目说明** 
- 本项目基于renren-fast Java开发平台开发，内核基于Jmeter-Api和Jmeter脚本实现在线性能压测。

**插播广告：**

如果有需要，请帮忙支持一下：https://juejin.im/post/5e2159215188254e1c43a585
互帮互助，感谢。

**平台特点** 
- 友好的代码结构及注释，便于阅读及二次开发
- 实现前后端分离，通过token进行数据交互，前端再也不用关注后端技术
- 灵活的权限控制，可控制到页面或按钮，满足绝大部分的权限需求
- 页面交互使用Vue2.x，极大的提高了开发效率
- 完善的代码生成机制，可在线生成entity、xml、dao、service、html、js、sql代码，减少70%以上的开发任务
- 引入quartz定时任务，可动态完成任务的添加、修改、删除、暂停、恢复及日志查看等功能
- 引入API模板，根据token作为登录令牌，极大的方便了APP接口开发
- 引入Hibernate Validator校验框架，轻松实现后端校验
- 引入云存储服务，已支持：七牛云、阿里云、腾讯云等
- 引入swagger文档支持，方便编写API接口文档
- 引入路由机制，刷新页面会停留在当前页
- 引入最新版本Jmeter-Api，支持分布式压测，测试报告生成及在线查看下载。
- 引入Echarts，支持在线观测性能压测结果。

<br> 

**项目结构** 
```
renren-fast
├─doc  项目SQL语句
│
│─lib  项目引用jar包
│
├─common 公共模块
│  ├─aspect 系统日志
│  ├─exception 异常处理
│  ├─validator 后台校验
│  └─xss XSS过滤
│ 
├─config 配置信息
│ 
├─modules 功能模块
│  ├─api API接口模块(APP调用)
│  ├─job 定时任务模块
│  ├─oss 文件服务模块
│  ├─sys 权限模块
│  └─test 测试模块
│ 
├─RenrenApplication 项目启动类
│  
├──resources 
│  ├─mapper SQL对应的XML文件
│  ├─static 第三方库、插件等静态资源
│  └─views  项目静态页面

```


**技术选型：** 
- 核心框架：Spring Boot 1.5
- 安全框架：Apache Shiro 1.3
- 视图框架：Spring MVC 4.3
- 持久层框架：MyBatis 3.3
- 定时器：Quartz 2.3
- 数据库连接池：Druid 1.0
- 日志管理：SLF4J 1.7、Log4j
- 页面交互：Vue2.x 
- 前端监控：ECharts 3.8
- 压测内核：Apache JMeter 4.0
- 脚本调用内核：Apache Commons Exec 1.3
- 远程执行命令：Ganymed build210
- 批量上传组件：bootstrap-fileinput v4.5.2
- JVM内部缓存：Guava 18.0
<br> 


 **本地部署**
- 通过git下载源码
- 创建数据库renren_fast，数据库编码为UTF-8
- 执行doc/db.sql文件，初始化数据
- 修改application-dev.yml，更新MySQL账号和密码
- 修改MySQL中sys_config表中Jmeter专属配置项，更新为本地地址
- Eclipse、IDEA运行RenrenApplication.java，则可启动项目
- 项目访问路径：http://localhost:8080/renren-fast/index.html
- 账号密码：admin/admin
- Swagger路径：http://localhost:8080/renren-fast/swagger/index.html

 **tomcat部署**
- 修改application.yml，修改profiles，指定执行环境如线下环境pro
- 修改application-pro.yml，更新线下环境的MySQL账号和密码
- 修改MySQL中sys_config表中Jmeter专属配置项，更新为本地地址
- 通过maven命令打包war包：mvn clean package -f pom-war.xml
- 将target目录下，打包好的war包保存到tomcat的webapps目录下
- 通过tomcat的bin目录下的startup命令，启动tomcat
- 访问tomcat路径，如：http://线下环境ip:8080/renren-fast/index.html
- 账号密码：admin/admin

