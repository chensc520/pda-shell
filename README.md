# PDA 扫码发货 WebView 壳

改app名字
app/src/main/res/values/strings.xml 第 3 行
<string name="app_name">定义的名字</string>

一个最小的 Android WebView 工程：全屏加载 PDA 专用登录页，登录后在网页内跳转到扫码发货页。
扫码走 PDA 的键盘模拟（HID），光标在输入框里扫码即填入并回车——APK 不处理扫码，只负责把网页跑好。



## 改状态栏颜色也是这个文件里  搜  getWindow().setStatusBarColor(Color.parseColor("#428bca"));
## 改成自己喜欢的颜色
`app/src/main/java/com/cn12315/pda/MainActivity.java` 顶部两个常量：


## 一、改首页地址也是在上面这个地址  
 

```java
 // ===== 应用启动时 加载的商户登录入口 =====
    private static final String TARGET_URL = "https://test.cn12315.com/pda_login?c=ckbs";
```

- `TARGET_URL`：APK 打开就加载的页面（登录页）。上线换正式域名时改这里。
- `SITE_HOST`：本域名在 WebView 内打开，其他域名调系统浏览器。

改完重新打包即可。

## 二、本机打包（Android Studio）

1. Android Studio → File → Open → 选本工程目录 `pda-apk`
2. 首次打开会下载 Gradle 和依赖（已配阿里云镜像，国内能较快拉到），等它 Sync 完成
   > 本工程刻意不放 `gradle-wrapper.jar`——Android Studio 会用自带的 Gradle 构建，无需 wrapper。
3. 顶部菜单：**Build → Build Bundle(s) / APK(s) → Build APK(s)**
4. 编译完成后右下角弹提示，点 **locate** 直达 APK：
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```
5. 把 `app-debug.apk` 拷到 PDA 安装即可

> 真机安装：PDA 设置里开"未知来源安装"，用数据线拷过去点开，或 `adb install app-debug.apk`。

## 三、在线打包（GitHub Actions，本机无环境时用）

1. 把 `pda-apk` 目录初始化为 git 仓库并推到 GitHub：
   ```bash
   cd pda-apk
   git init
   git add .
   git commit -m "init pda shell"
   git remote add origin https://github.com/你的账号/pda-shell.git
   git push -u origin main
   ```
2. 打开仓库页面 → **Actions** 标签 → 等 `Build PDA APK` 跑完（约 3-5 分钟）
3. 点进那次运行 → 页面底部 **Artifacts** → 下载 `pda-apk-debug`（是个 zip，解压出 apk）

改了 `TARGET_URL` 后重新 `git push`，会自动再出一个新包。

## 四、WebView 已做的适配

| 项 | 处理 |
|---|---|
| 全屏 | 去标题栏，保留状态栏（看电量/时间） |
| 屏幕常亮 | `FLAG_KEEP_SCREEN_ON`，仓库作业不用反复点亮 |
| 软键盘遮挡 | `windowSoftInputMode=adjustNothing` + PDA 物理键盘，扫码不弹键盘挡屏 |
| Cookie 登录态 | `CookieManager` 开启，登录后跨页面保持 |
| JS / AJAX | `setJavaScriptEnabled(true)`，页面调接口正常 |
| viewport | 按 320 CSS 像素渲染，页面里的字号直接生效 |
| 返回键 | 网页有历史就后退，不直接退出 App |
| 加载失败 | 显示"点击重试" |
| 真机调试 | 已开 WebContentsDebugging，PC Chrome 访问 `chrome://inspect` 可调试 |

## 五、常见问题

- **Sync 卡住 / Gradle 下载慢**：本工程 `settings.gradle` 已配阿里云镜像。若仍慢，检查 Android Studio 是否走了代理。
- **编译报 SDK 版本错**：Android Studio → SDK Manager → 勾选 Android 14 (API 34) 安装。
- **打开网页空白**：确认 PDA 能访问 `https://test.cn12315.com/pda_login`，且证书有效（自签证书需另行信任）。
- **想换正式站**：只改 `TARGET_URL` 和 `SITE_HOST` 两个常量。
