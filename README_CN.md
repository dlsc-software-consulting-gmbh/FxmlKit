# FxmlKit

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**FxmlKit = 自动 FXML 加载 + 热更新 + 可选依赖注入**

```java
// 开发时启用 FXML/CSS 热更新
FxmlKit.enableDevelopmentMode();

// 零配置 - 自动加载 FXML
new MainView();

// 带依赖注入
new MainView(diAdapter);
```

现代化的 JavaFX FXML 框架，消除样板代码，提供 FXML/CSS 热更新，以及可选的渐进式依赖注入支持。

[English](README.md) | [示例项目](fxmlkit-samples)

---

## 目录

- [为什么选择 FxmlKit](#为什么选择-fxmlkit)
- [核心特性](#核心特性)
- [致谢（Acknowledgments）](#致谢acknowledgments)
- [快速开始](#快速开始)
- [使用方式](#使用方式)
- [热更新](#热更新)
- [核心概念](#核心概念)
- [注解](#注解)
- [常见问题](#常见问题)
- [示例项目](#示例项目)

---

## 为什么选择 FxmlKit

### 痛点一：UI 开发时没有热更新

传统 JavaFX 开发中，每次修改 FXML 或 CSS 文件都需要重启应用才能看到效果。这大大降低了 UI 开发效率。

**FxmlKit 方案：**

```java
public class MyApp extends Application {
    @Override
    public void start(Stage stage) {
        FxmlKit.enableDevelopmentMode();  // ✅ 一行代码启用热更新

        stage.setScene(new Scene(new MainView()));
        stage.show();
    }
}
```

现在编辑 `.fxml` 或 `.css` 文件，保存后即刻生效 — 无需重启！

---

### 痛点二：原生 FXML 加载需要大量样板代码

每个视图都要重复：获取 URL、配置 FXMLLoader、处理异常、加载样式表……

**FxmlKit 方案：**
```java
// ✅ 一行代码，自动处理所有事情
public class LoginView extends FxmlView<LoginController> {
}
```

自动完成 FXML 解析、样式表附加、控制器创建和异常处理。

---

### 痛点三：FXML 自定义组件几乎无法接收依赖注入

传统方式下，FXML 中的自定义组件由 FXMLLoader 直接实例化，无法访问 DI 容器。

**FxmlKit 方案：**

控制器自动注入：
```java
public class LoginController {
    @Inject private UserService userService;

    @PostInject  // 自动调用
    private void afterInject() {
        // 依赖已就绪
    }
}
```

FXML 节点也能自动注入：
```java
@FxmlObject  // 一个注解搞定
public class StatusCard extends VBox {
    @Inject private StatusService statusService;

    @PostInject
    private void afterInject() {
        updateStatus();
    }
}
```

在 FXML 中直接使用：
```xml
<VBox>
    <StatusCard/>  <!-- ✅ 自动注入依赖 -->
</VBox>
```

**重点：依赖注入是可选的！** 如果你不需要 DI，FxmlKit 仍然能消除 FXML 加载的样板代码。

---

## 核心特性

- **零配置** — 开箱即用，无需任何设置
- **约定优于配置** — 自动发现 FXML 和样式表文件
- **热更新** — 开发时 FXML 和 CSS 修改即刻生效
- **fx:include 支持** — 完整的嵌套 FXML 热更新（包括动态添加/删除的 include）
- **可选依赖注入** — 不需要 DI 框架也能使用，需要时可以添加
- **自动样式表** — 自动附加 `.bss` 和 `.css` 文件
- **JPro 就绪** — 支持多用户 Web 应用的数据隔离（每个用户会话独立 DI 容器，确保数据安全）
- **高性能** — 智能缓存和性能优化

**与原生 JavaFX 对比：**

| 功能 | JavaFX 原生 | FxmlKit |
|------|------------|---------|
| 热更新（FXML + CSS） | ❌ 需要重启应用 | ✅ 即刻刷新 |
| fx:include 热更新 | ❌ 无 | ✅ 完整支持（子文件变化时父视图自动刷新） |
| User Agent Stylesheet 热更新 | ❌ 无 | ✅ 全部级别（Application/Scene/SubScene/自定义控件） |
| FXML 自动加载 | ❌ 手动编写加载代码 | ✅ 零配置自动加载 |
| 样式表自动附加 | ❌ 手动代码附加 | ✅ FxmlView 自动附加 |
| 控制器依赖注入 | ⚠️ 需手动配置工厂 | ✅ 自动注入 |
| **FXML 节点注入** | ❌ **几乎不可能** | ✅ **@FxmlObject 一行搞定** |
| 多层 fx:include 支持 | ⚠️ 部分支持 | ✅ 完整支持（含注入、样式） |
| @PostInject 生命周期 | ❌ 无 | ✅ 支持 |
| JPro 多用户隔离 | ❌ 需手动实现 | ✅ 原生支持 |

---

## 致谢（Acknowledgments）

- **[afterburner.fx](https://github.com/AdamBien/afterburner.fx)** — 启发了我们的约定优于配置理念（按类名自动解析 FXML/CSS）。我们在此基础上扩展了 FXML 节点注入、多层嵌套支持和 JPro 多用户隔离。
- **[CSSFX](https://github.com/McFoggy/cssfx)** — 启发了我们的 CSS 热重载方案（file:// URI 替换）。我们的实现采用了共享 WatchService、防抖刷新和基于 WeakReference 的惰性清理。

---

## 快速开始

### 依赖要求

- **Java:** 11 或更高版本
- **JavaFX:** 11 或更高版本（推荐 17+）

#### 热更新性能说明

FxmlKit 使用 Java 内置的 `WatchService` 实现 FXML/CSS 热更新。该服务在不同操作系统上的性能表现存在差异：

| 操作系统 | Java 版本 | 文件变更检测延迟 | 说明 |
|---------|----------|----------------|------|
| Windows | 11+ | 几乎瞬间 | 原生文件系统事件 |
| Linux | 11+ | 几乎瞬间 | 原生文件系统事件 |
| macOS | 11+ | 约 2 秒 | FxmlKit 已优化 |

### 安装

**Maven:**
```xml
<dependency>
    <groupId>com.dlsc.fxmlkit</groupId>
    <artifactId>fxmlkit</artifactId>
    <version>1.4.0</version>
</dependency>
```

**Gradle:**
```gradle
implementation 'com.dlsc.fxmlkit:fxmlkit:1.4.0'
```

**如果需要使用 Guice 来进行依赖注入：** 直接依赖 `fxmlkit-guice` 即可（已包含核心模块）

**Maven:**
```xml
<dependency>
    <groupId>com.dlsc.fxmlkit</groupId>
    <artifactId>fxmlkit-guice</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
    <groupId>com.google.inject</groupId>
    <artifactId>guice</artifactId>
    <version>7.0.0</version>
</dependency>
```

**Gradle:**
```gradle
implementation 'com.dlsc.fxmlkit:fxmlkit-guice:1.0.0'
implementation 'com.google.inject:guice:7.0.0'
```

**如果需要使用其他 DI 框架：** 可以继续使用 `fxmlkit` 核心模块，然后实现 `DiAdapter` 接口或继承 `BaseDiAdapter` 类来适配你的 DI 框架。同样地，即使使用 Guice，你也可以选择不依赖 `fxmlkit-guice` 模块，而是自己实现一个 `GuiceDiAdapter`（参考 `fxmlkit-guice` 的源码，实现非常简单）。

---

### 创建你的第一个视图

**1. 创建 FXML 文件**

`src/main/resources/com/example/HelloView.fxml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.layout.*?>
<?import javafx.scene.control.*?>

<VBox xmlns:fx="http://javafx.com/fxml" 
      fx:controller="com.example.HelloController"
      spacing="10" alignment="CENTER">
    <Label fx:id="messageLabel" text="Hello, FxmlKit!"/>
    <Button text="点我" onAction="#handleClick"/>
</VBox>
```

**2. 创建控制器**

`src/main/java/com/example/HelloController.java`:
```java
package com.example;

import javafx.fxml.FXML;
import javafx.scene.control.*;

public class HelloController {
    @FXML private Label messageLabel;
    
    @FXML
    private void handleClick() {
        messageLabel.setText("你好，来自 FxmlKit！");
    }
}
```

**3. 创建视图**

`src/main/java/com/example/HelloView.java`:
```java
package com.example;

import com.dlsc.fxmlkit.fxml.FxmlView;

public class HelloView extends FxmlView<HelloController> {
    // 就这么简单！
}
```

**4. 使用视图**

```java
public class HelloApp extends Application {
    @Override
    public void start(Stage stage) {
        stage.setScene(new Scene(new HelloView()));
        stage.setTitle("FxmlKit 演示");
        stage.show();
    }
}
```

**可选：添加样式表**

创建 `src/main/resources/com/example/HelloView.css`，FxmlKit 会自动附加它！

---

## 使用方式

FxmlKit 支持三种使用方式，根据你的需求选择：

### 方式一：零配置

**适用场景：** 学习 JavaFX、快速原型、简单应用

```java
public class MainView extends FxmlView<MainController> {
}

// 使用
stage.setScene(new Scene(new MainView()));
```

**特点：**
- ✅ 自动加载 FXML
- ✅ 自动附加样式表
- ✅ 控制器自动创建
- ❌ 不支持依赖注入

---

### 方式二：可选依赖注入

**适用场景：** 需要依赖注入的桌面应用

```java
// 应用启动时 - 设置全局 DI 适配器
Injector injector = Guice.createInjector(new AbstractModule() {
    @Override
    protected void configure() {
        bind(UserService.class).toInstance(new UserService());
        bind(ConfigService.class).toInstance(new ConfigService());
    }
});
FxmlKit.setDiAdapter(new GuiceDiAdapter(injector));

// 创建视图 - 控制器和节点自动接收注入
public class LoginView extends FxmlView<LoginController> {
}

// 使用 - 与零配置模式相同
LoginView view = new LoginView();
```

**特点：**
- ✅ 全局 DI 配置 - 设置一次，随处使用
- ✅ 控制器自动注入
- ✅ FXML 节点自动注入（使用 `@FxmlObject`）
- ✅ 支持多种 DI 框架（Guice、Spring、CDI 等）

---

### 方式三：独立 DI 容器

**适用场景：** JPro Web 应用，每个用户需要独立数据

```java
// 为每个用户会话创建独立的 DI 容器
Injector userInjector = Guice.createInjector(new AbstractModule() {
    @Override
    protected void configure() {
        bind(UserSession.class).toInstance(new UserSession(userId));
        bind(UserService.class).toInstance(new UserService());
    }
});

// 创建视图时传递 DI 适配器
LoginView view = new LoginView(new GuiceDiAdapter(userInjector));
```

**适用场景：**
- JPro Web 应用（每个用户会话一个 DI 容器）
- 桌面应用（每个 Tab/窗口一个 DI 容器）
- 需要严格数据隔离的场景

---

### 响应式 Controller API

FxmlKit 将控制器和视图暴露为 JavaFX 属性，支持响应式编程模式。这在热更新时特别有用，可以自动更新 UI 以响应控制器变化。

**使用 FxmlView（立即加载）：**
```java
MainView view = new MainView();

// 直接访问控制器（仅当 FXML 中没有 fx:controller 时才返回 null）
MainController controller = view.getController();

// 响应控制器变化（例如热更新时）
view.controllerProperty().addListener((obs, oldController, newController) -> {
    if (newController != null) {
        newController.refreshData();  // 热更新时重新初始化
    }
});
```

**使用 FxmlViewProvider（懒加载）：**
```java
MainViewProvider provider = new MainViewProvider();

// 在调用 getView() 之前，controller 为 null
MainController controller = provider.getController();  // null - 尚未加载

// 响应控制器变化
provider.controllerProperty().addListener((obs, oldCtrl, newCtrl) -> {
    if (newCtrl != null) {
        newCtrl.loadData();
    }
});

// 触发懒加载
Parent view = provider.getView();  // 现在 FXML 已加载
controller = provider.getController();  // 现在返回控制器
```

**优势：**
- ✅ 自动响应热更新事件
- ✅ 与 JavaFX 属性绑定无缝集成
- ✅ 相比手动处理重载更简洁

**注意：** 使用 `FxmlView` 时，FXML 在构造函数中立即加载，因此 `getController()` 总是返回控制器（或在 FXML 没有 `fx:controller` 属性时返回 null）。使用 `FxmlViewProvider` 时，FXML 在首次调用 `getView()` 时才懒加载，因此在此之前 `getController()` 返回 null。

---

## 热更新

FxmlKit 内置热更新功能，加速 UI 开发。编辑 FXML 或 CSS 文件后，无需重启即可看到效果。

### 快速开始

```java
public class MyApp extends Application {
    @Override
    public void start(Stage stage) {
        // 启用热更新（必须在创建视图之前调用）
        FxmlKit.enableDevelopmentMode();
        
        stage.setScene(new Scene(new MainView()));
        stage.show();
    }
}
```

**重要：** `enableDevelopmentMode()` 必须在创建任何视图**之前**调用。启用前创建的视图不会被监控。

### 工作原理

| 文件类型 | 行为 | 运行时状态 | fx:include |
|----------|------|------------|------------|
| `.fxml` | 完整视图重载 | 丢失（用户输入、滚动位置） | ✅ 子文件变化时父视图自动刷新 |
| `.css` / `.bss` | 仅刷新样式表 | **保留** | ✅ 完整支持 |

**fx:include 热更新：**
- 编辑子 FXML → 父视图自动刷新
- 动态添加/删除 `<fx:include>` → 无缝支持
- 嵌套 include → 所有层级均被监控

**监控的样式表类型：**
- 普通样式表（`scene.getStylesheets()`、`parent.getStylesheets()`）
- User Agent Stylesheet（Application、Scene、SubScene 三级）
- 自定义控件样式表（`Region.getUserAgentStylesheet()` 重写）
- 自动附加的样式表（约定命名的 `.css`/`.bss` 文件）

### User Agent Stylesheet 支持

对于 **Scene** 和 **SubScene** 级别的 User Agent Stylesheet，使用原生 JavaFX API 即可自动监控：

```java
scene.setUserAgentStylesheet("/styles/theme.css");  // 自动监控
```

对于 **Application** 级别，需使用 FxmlKit 的桥接属性以启用热更新：

```java
// ✅ 支持热更新 + 属性绑定
FxmlKit.setApplicationUserAgentStylesheet("/styles/dark-theme.css");

// 或绑定到主题选择器
FxmlKit.applicationUserAgentStylesheetProperty()
    .bind(themeComboBox.valueProperty());
```

注意：直接使用 `Application.setUserAgentStylesheet()` 仍然可以工作，但不会触发热更新。


### 自定义控件 User Agent Stylesheet

FxmlKit 支持重写了 `getUserAgentStylesheet()` 的自定义控件的热更新，但**此功能需要显式启用**，因为涉及 CSS 优先级变更：
```java
public class VersionLabel extends Label {
    
    @Override
    public String getUserAgentStylesheet() {
        return VersionLabel.class.getResource("version-label.css").toExternalForm();
    }
}
```

**启用自定义控件 UA 热更新：**
```java
// 方式一：启用开发模式（FXML + CSS 热更新）
FxmlKit.enableDevelopmentMode();
FxmlKit.setControlUAHotReloadEnabled(true);

// 方式二：仅启用 CSS 热更新
FxmlKit.setCssHotReloadEnabled(true);
FxmlKit.setControlUAHotReloadEnabled(true);
```

**⚠️ 前置条件：**
- 控件 UA 热更新需要 CSS 监控处于活动状态
- 请先通过 `enableDevelopmentMode()` 或 `setCssHotReloadEnabled(true)` 启用 CSS 热更新

**实现原理：** 启用后，FxmlKit 自动检测重写了 `getUserAgentStylesheet()` 的自定义控件，并将样式表提升到 `getStylesheets().add(0, ...)` 以进行监控。样式表添加在索引 0（作者样式表中最低优先级）以近似原有的低优先级行为。

**⚠️ 为什么需要显式启用？** 这种提升会改变 CSS 级联语义 —— 样式表从"用户代理样式表"变成了"作者样式表"。在某些情况下，这可能导致自定义控件样式意外覆盖用户定义的或场景级别的样式表。

**建议：**
- 仅在需要编辑自定义控件样式表时才在开发环境启用
- 生产环境禁用：`FxmlKit.setControlUAHotReloadEnabled(false)`
- 如遇到意外的样式冲突，请测试您的样式

### 精细控制

```java
// 仅启用 FXML 热更新
FxmlKit.setFxmlHotReloadEnabled(true);

// 仅启用 CSS 热更新
FxmlKit.setCssHotReloadEnabled(true);

// 启用控件 UA 样式表热更新（需要显式启用，依赖 CSS 热更新）
FxmlKit.setControlUAHotReloadEnabled(true);

// 同时启用（等同于 enableDevelopmentMode()）
FxmlKit.setFxmlHotReloadEnabled(true);
FxmlKit.setCssHotReloadEnabled(true);

// 全部禁用
FxmlKit.disableDevelopmentMode();
```

### 与 CSSFX 配合使用

如果你更喜欢用 [CSSFX](https://github.com/McFoggy/cssfx) 做 CSS 热更新，可以禁用 FxmlKit 的内置 CSS 监控：

```java
// 仅使用 FxmlKit 的 FXML 热更新
FxmlKit.setFxmlHotReloadEnabled(true);
FxmlKit.setCssHotReloadEnabled(false);

// 使用 CSSFX 处理 CSS
CSSFX.start();
```

### 生产环境警告

**热更新仅用于开发环境。** 不要在生产环境启用。

**方式一：发布前直接注释掉**

```java
public class MyApp extends Application {
    @Override
    public void start(Stage stage) {
        // FxmlKit.enableDevelopmentMode();  // 生产环境注释掉这行
        
        stage.setScene(new Scene(new MainView()));
        stage.show();
    }
}
```

**方式二：使用 JVM 参数自动切换**

```java
public class MyApp extends Application {
    // 通过 JVM 参数设置：-Ddev.mode=true
    private static final boolean DEV_MODE = Boolean.getBoolean("dev.mode");
    
    @Override
    public void start(Stage stage) {
        if (DEV_MODE) {
            FxmlKit.enableDevelopmentMode();
        }
        
        stage.setScene(new Scene(new MainView()));
        stage.show();
    }
}
```

开发环境运行：`java -Ddev.mode=true -jar myapp.jar`

生产环境运行：`java -jar myapp.jar`

---

## 核心概念

### 自动文件解析

FxmlKit 使用约定优于配置，自动查找 FXML 和样式表文件：

```
src/main/resources/com/example/
├── UserView.fxml          ← UserView.java 自动匹配
├── UserView.css           ← 自动附加
└── UserView.bss           ← 二进制样式表（优先）
```

**约定：** FXML 文件与 Java 类同名，放在同一资源目录下。

### FxmlView vs FxmlViewProvider

| 特性 | FxmlView | FxmlViewProvider |
|------|----------|------------------|
| **类型** | IS-A Node (继承 StackPane) | HAS-A Node (持有 Parent) |
| **加载** | 立即加载（构造时） | 惰性加载（首次调用 `getView()`） |
| **使用** | 直接作为 Node 使用 | 需要调用 `getView()` 获取 Node |
| **适用场景** | 直接作为节点使用 | 延迟加载，节省资源 |

**FxmlView 示例：**
```java
public class LoginView extends FxmlView<LoginController> {
}

// 使用 - 直接作为 Node
LoginView view = new LoginView();  // FXML 立即加载
stage.setScene(new Scene(view));   // view 本身是 StackPane
```

**FxmlViewProvider 示例：**
```java
public class MainViewProvider extends FxmlViewProvider<MainController> {
}

// 使用 - 需要调用 getView()
MainViewProvider provider = new MainViewProvider();  // FXML 尚未加载
Parent view = provider.getView();  // 这里才加载 FXML
stage.setScene(new Scene(view));
```

### 注入自定义组件

使用 `@FxmlObject` 向自定义 JavaFX 组件注入依赖：

```java
@FxmlObject
public class StatusCard extends VBox {
    @Inject private StatusService statusService;
    
    @PostInject
    private void afterInject() {
        // statusService 已就绪
        updateStatus();
    }
}
```

**FXML 中使用：**
```xml
<VBox>
    <StatusCard/>  <!-- 自动接收依赖注入 -->
</VBox>
```

### 注入策略

FxmlKit 默认使用 `EXPLICIT_ONLY` 策略（仅注入标记 `@FxmlObject` 的对象）。

---

## 注解

### @FxmlPath - 自定义 FXML 文件路径

**作用：** 指定 FXML 文件的位置，覆盖默认的自动解析规则。

**使用场景：** 通常不需要此注解，FxmlKit 会自动解析。仅在 FXML 文件不在默认位置时使用。

```java
@FxmlPath("/shared/Common.fxml")
public class LoginView extends FxmlView<LoginController> {}
```

---

### @FxmlObject - 启用 FXML 对象注入

**作用：** 标记一个类，使其在 FXML 中创建时能够接收依赖注入。

**支持的对象类型：**
- 自定义 JavaFX 控件（Button、TextField 等子类）
- 布局容器（Pane、HBox、VBox 等子类）
- 非可视对象（如 MenuItem、ContextMenu 等）
- 任何在 FXML 中声明的自定义类

**示例 - 自定义控件：**
```java
@FxmlObject
public class UserAvatar extends Circle {
    @Inject private UserService userService;
    
    @PostInject
    private void afterInject() {
        loadUserImage();
    }
}
```

**示例 - 非可视对象：**
```java
@FxmlObject
public class CustomMenuItem extends MenuItem {
    @Inject private ActionService actionService;
    
    @PostInject
    private void afterInject() {
        setOnAction(e -> actionService.execute());
    }
}
```

**FXML 中使用：**
```xml
<MenuBar>
    <Menu text="操作">
        <CustomMenuItem text="执行"/>  <!-- 也能接收注入 -->
    </Menu>
</MenuBar>
```

**注意：**
- 不使用 `@FxmlObject` 的自定义对象将不会接收依赖注入（除非注入策略设置为 `AUTO`）
- 如果使用 `AUTO` 策略但想排除某些类型，可以使用 `FxmlKit.excludeNodeType()` 或在类上添加 `@SkipInjection` 注解

---

### @PostInject - 注入后回调

**作用：** 标记一个方法在所有 `@Inject` 字段注入完成后立即执行。

**使用场景：** 需要在依赖注入完成后进行初始化操作时使用（如加载数据、设置监听器等）。

**执行时机：** 所有 `@Inject` 字段注入完成后立即执行。

**示例：**
```java
public class UserProfileController {
    @Inject private UserService userService;
    @Inject private ConfigService configService;
    
    @PostInject
    private void afterInject() {
        // 所有 @Inject 字段已就绪
        userService.loadUserData();
        configService.applySettings();
    }
}
```

**方法要求：**
- 必须是无参方法
- 可以是任何访问级别（private、protected、public）
- 支持继承（父类的 @PostInject 方法会先执行）

---

## 常见问题

### Q: FxmlKit 必须使用依赖注入框架吗？

**A:** 不需要！FxmlKit 的核心功能（FXML 加载、样式表附加）无需任何 DI 框架。依赖注入是**完全可选**的，只有当你的应用需要时才使用。

---

### Q: 何时使用内置的 LiteDiAdapter？

**A:** LiteDiAdapter 是一个简单的 DI 容器，适合小型项目和学习场景。

**依赖要求：**
```xml
<dependency>
    <groupId>javax.inject</groupId>
    <artifactId>javax.inject</artifactId>
    <version>1</version>
</dependency>
```

**使用示例：**
```java
import javax.inject.Inject;

LiteDiAdapter di = new LiteDiAdapter();
di.bindInstance(UserService.class, new UserService());
FxmlKit.setDiAdapter(di);
```

**何时使用：** 小型项目、学习、原型开发  
**何时升级：** 项目变大或需要高级特性时，建议使用 Guice 或其他成熟 DI 框架

---

### Q: 为什么 @Inject 字段是 null？

**原因：** 零配置模式（方式一）不支持依赖注入。

**解决：** 配置 DiAdapter：
```java
FxmlKit.setDiAdapter(diAdapter);
```

---

### Q: @PostInject 方法何时执行？

**A:** 在依赖注入完成后执行。Controllers 和节点的执行时机不同：

#### 对于 Controllers

**执行顺序：** `Constructor → @Inject → @FXML → initialize() → @PostInject`

通常**不需要** - 直接使用 `initialize()` 即可：
```java
public class LoginController implements Initializable {
    @Inject private UserService userService;  // ① 注入
    @FXML private Button loginButton;          // ② JavaFX 注入
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // ③ @Inject 和 @FXML 字段都已可用
        loginButton.setOnAction(e -> userService.login());
    }
    
    @PostInject
    private void afterInject() {
        // ④ 在 initialize() 之后调用 - 对于 controllers 通常不需要
        userService.loadSettings();  // 示例：如果确实需要
    }
}
```

#### 对于 @FxmlObject 节点

**执行顺序：** `Constructor → @Inject → @PostInject`

如果需要使用注入的依赖，则**必须使用**：
```java
@FxmlObject
public class StatusLabel extends Label {
    @Inject private StatusService statusService;
    
    public StatusLabel() {
        // ① 构造函数 - statusService 此时为 null ❌
    }
    
    @PostInject
    private void afterInject() {
        // ② 已注入 - statusService 现在可用 ✅
        setText(statusService.getStatus());
    }
}
```

**规则：** 对于 `@FxmlObject` 节点，如果初始化需要使用 `@Inject` 依赖，必须使用 `@PostInject` 方法。

---

### Q: 样式表没有生效？

**A:** 检查以下几点：

1. **文件命名：** 必须与类名匹配 - `LoginView.java` → `LoginView.css`
2. **文件位置：** 必须在相同的包资源目录下
3. **自动附加已启用：** `FxmlKit.setAutoAttachStyles(true)`（默认为 true）
4. **CSS 优先级：** `.bss` 文件优先级高于 `.css`

---

### Q: 热更新不生效？

**A:** 检查以下几点：

1. **调用顺序：** `enableDevelopmentMode()` 必须在创建视图**之前**调用
2. **文件位置：** 源文件必须在 `src/main/resources`（Maven/Gradle 标准目录）
3. **IDE 自动构建：** 在 IDE 中启用自动构建，实现无缝热更新
4. **调试日志：** 设置 `FxmlKit.setLogLevel(Level.FINE)` 查看热更新日志

---

### Q: fx:include 热更新不生效？

**A:** FxmlKit 自动监控 fx:include 依赖。请检查以下几点：

1. **FXML 热更新已启用：** 父 FxmlView 必须在启用 FXML 热更新之后创建（`enableDevelopmentMode()` 或 `setFxmlHotReloadEnabled(true)`）
2. **文件存在：** 被 include 的 FXML 必须存在于源目录中

---

### Q: 如何在 JPro 中使用？

**A:** FxmlKit 已为 JPro 做好准备。为每个用户会话创建独立的 DI 容器：

```java
public class JProApp extends Application {
    @Override
    public void start(Stage stage) {
        // 为每个用户创建独立的 DI 容器
        Injector userInjector = createUserInjector();
        
        // 创建视图时传递 DI 适配器
        MainView view = new MainView(new GuiceDiAdapter(userInjector));
        
        Scene scene = new Scene(view);
        stage.setScene(scene);
    }
}
```

参考 `fxmlkit-samples` 模块中的 `tier3.multiuser` 包，包含模拟 JPro 多用户场景的完整实现（使用 TabPane 模拟多用户会话）。

---

## 示例项目

`fxmlkit-samples` 模块包含完整的示例代码，按复杂度分为三个层级：

### Tier 1 - 零配置模式

```
tier1/
├── hello/          # 最简单的 Hello World
├── i18n/           # 国际化示例
├── provider/       # FxmlViewProvider 使用示例
└── viewpath/       # 自定义 FXML 路径
└── theme/          # User Agent Stylesheet 热更新（Application 级别 + 自定义控件）
```

### Tier 2 - 可选依赖注入

```
tier2/
├── fxmlobject/     # @FxmlObject 节点注入示例
├── guice/          # Guice 集成示例
└── login/          # 完整的登录应用示例
```

### Tier 3 - JPro 多用户隔离

```
tier3.multiuser/    # 模拟 JPro 多用户场景（使用 TabPane）
```
