#  WebView 壳

## 一、常用变量集中在头部改
`app/src/main/java/com/cn12315/pda/MainActivity.java` 顶部「常用配置」那一块，以后只改这里：
```java
// ===================== 常用配置：以后只改这一块 =====================
private static final String TARGET_URL = "https://mp.cn12315.com/pda_login";  /** 应用启动时加载的商户登录入口 */
private static final String SH_NO = "ckbs";                                  /** 商户编号，会话过期跳 /pda_login 时要用 */
private static final String CLIENT_TAG = "cn12315pda";                       /** PDA 客户端标识，大小写敏感，勿改 */
private static final String STATUS_BAR_COLOR = "#428bca";                    /** 顶部状态栏颜色 */
private static final String OFFLINE_TIP = "网络连接失败，请检查网络设置后重试"; /** 断网时显示的文字 */
// ===================================================================

改app名字
app/src/main/res/values/strings.xml 第 3 行
<string name="app_name">定义的名字</string>


