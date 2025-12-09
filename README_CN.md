# FxmlKit

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**FxmlKit = 自动 FXML 加载 + 可选依赖注入 + 简化 JavaFX 开发**

```java
// 零配置 - 自动加载 FXML
new MainView();

// 带依赖注入
new MainView(diAdapter);
```

现代化的 JavaFX FXML 框架，消除样板代码，提供可选的渐进式依赖注入支持。

[English](README.md) | [示例项目](fxmlkit-samples)

---

## 目录

- [为什么选择 FxmlKit](#为什么选择-fxmlkit)
- [核心特性](#核心特性)
- [致谢（Acknowledgments）](#致谢acknowledgments)
- [快速开始](#快速开始)
- [使用方式](#使用方式)
- [核心概念](#核心概念)
- [注解](#注解)
- [常见问题](#常见问题)
- [示例项目](#示例项目)

---

## 为什么选择 FxmlKit

### 痛点一：原生 FXML 加载需要大量样板代码

每个视图都要重复：获取 URL、配置 FXMLLoader、处理异常、加载样式表……

**FxmlKit 方案：**
```java
// ✅ 一行代码，自动处理所有事情
public class LoginView extends FxmlView<LoginController> {
}
```

自动完成 FXML 解析、样式表附加、控制器创建和异常处理。

---

### 痛点二：FXML 自定义组件几乎无法接收依赖注入

传统方式下，FXML 中的自定义组件由 FXMLLoader 直接实例化，无法访问 DI 容器。

**FxmlKit 方案：**

控制器自动注入：
```java
import javax.inject.Inject;  // 标准 JSR-330 注解

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
    @Inject private StatusService statusService;  // 自动注入！
    
    private final Label statusLabel;
    
    public StatusCard() {
        statusLabel = new Label();
        getChildren().add(statusLabel);
    }
    
    @PostInject
    private void afterInject() {
        updateStatus();  // 直接使用注入的服务
    }
}
```

在 FXML 中直接使用：
```xml
<VBox>
    <StatusCard/>  <!-- ✅ 自动注入 statusService -->
    <StatusCard/>  <!-- ✅ 每个实例都自动注入 -->
</VBox>
```

**重点：依赖注入是可选的！** 如果你不需要 DI，FxmlKit 仍然能消除 FXML 加载的样板代码。

---

## 核心特性

- **零配置** — 开箱即用，无需任何设置
- **约定优于配置** — 自动发现 FXML 和样式表文件
- **可选依赖注入** — 不需要 DI 框架也能使用，需要时可以添加
- **自动样式表** — 自动附加 `.bss` 和 `.css` 文件
- **嵌套 FXML** — 完整支持 `<fx:include>` 层级结构
- **JPro 就绪** — 支持多用户 Web 应用的数据隔离（每个用户会话独立 DI 容器，确保数据安全）
- **高性能** — 智能缓存和性能优化

**与原生 JavaFX 对比：**

| 功能 | JavaFX 原生 | FxmlKit |
|------|------------|---------|
| FXML 自动加载 | ❌ 手动编写加载代码 | ✅ 零配置自动加载 |
| 样式表自动附加 | ❌ 手动代码附加 | ✅ 自动附加（含嵌套 FXML） |
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

### 安装

**Maven:**
```xml
<dependency>
    <groupId>com.dlsc.fxmlkit</groupId>
    <artifactId>fxmlkit</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Gradle:**
```gradle
implementation 'com.dlsc.fxmlkit:fxmlkit:1.0.0'
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
// 不需要任何配置
public class MainView extends FxmlView<MainController> {
}

// 直接使用
stage.setScene(new Scene(new MainView()));
```

**特点：**
- ✅ 无需任何设置
- ✅ 自动加载 FXML
- ✅ 自动附加样式表
- ✅ 控制器自动创建
- ❌ 不支持依赖注入

---

### 方式二：可选依赖注入

**适用场景：** 需要依赖注入的桌面应用

**一次性配置：**
```java
public class MyApp extends Application {
    @Override
    public void start(Stage stage) {
        // 在启动时配置一次
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(UserService.class).toInstance(new UserService());
                bind(ConfigService.class).toInstance(new ConfigService());
            }
        });
        
        FxmlKit.setDiAdapter(new GuiceDiAdapter(injector));
        
        // 然后正常使用
        stage.setScene(new Scene(new MainView()));
        stage.show();
    }
}
```

**控制器支持注入：**
```java
public class MainController {
    @Inject private UserService userService;
    @Inject private ConfigService configService;
    
    @PostInject
    private void afterInject() {
        // 注入完成后调用
        System.out.println("当前用户: " + userService.getCurrentUser());
    }
}
```

**特点：**
- ✅ 方式一的所有特性
- ✅ 支持 `@Inject` 字段注入
- ✅ 支持 `@PostInject` 生命周期
- ✅ 可选 - 不用也能工作

**支持多种 DI 框架：** 内置的 `LiteDiAdapter`（轻量级）、Google Guice、Jakarta CDI，以及任何实现 `DiAdapter` 接口的框架。

---

### 方式三：独立 DI 容器

**适用场景：** JPro Web 应用，每个用户需要独立数据

**核心思想：** 每个用户会话创建独立的 DI 容器，通过构造函数注入到视图。

```java
// 用户视图：接收独立的 DI 容器
public class UserDashboardView extends FxmlView<DashboardController> {
    @Inject
    public UserDashboardView(DiAdapter diAdapter) {
        super(diAdapter);  // 传递用户专属的 DI 容器
    }
}
```

```java
// 为每个用户创建独立的 DI 容器
Injector userInjector = Guice.createInjector(new UserModule(currentUser));
UserDashboardView view = userInjector.getInstance(UserDashboardView.class);
```

**特点：**
- ✅ 方式二的所有特性
- ✅ 完全的用户数据隔离
- ✅ 无交叉污染风险
- ✅ 线程安全

> 💡 **完整示例：** 请参考 `fxmlkit-samples` 模块中的 `tier3.multiuser` 包，包含模拟 JPro 多用户场景的完整实现（使用 TabPane 模拟多用户会话）。

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
| **适用场景** | 直接作为节点使用	 | 延迟加载，节省资源 |

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

**使用场景：** 当你在 FXML 中使用自定义组件或对象，并且需要注入服务时使用。

**类级别注解：**
```java
// 自定义控件
@FxmlObject
public class StatusCard extends VBox {
    @Inject private StatusService statusService;
    
    @PostInject
    private void afterInject() {
        updateStatus();
    }
}

// 非可视对象
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
<VBox>
    <StatusCard/>  <!-- 自动接收依赖注入 -->
    
    <MenuBar>
        <Menu text="操作">
            <CustomMenuItem text="执行"/>  <!-- 也能接收注入 -->
        </Menu>
    </MenuBar>
</VBox>
```

**注意：** 
- 不使用 `@FxmlObject` 的自定义对象将不会接收依赖注入（除非注入策略设置为 `AUTO`）
- 如果使用 `AUTO` 策略但想排除某些类型，可以使用 `FxmlKit.excludeNodeType()` 或在类上添加 `@SkipInjection` 注解

---

### @PostInject - 注入后回调

**作用：** 标记一个方法在所有依赖注入完成后立即执行。

**使用场景：** 需要在依赖注入完成后进行初始化操作时使用（如加载数据、设置监听器等）。

**方法级别注解：**
```java
public class UserController {
    @Inject private UserService userService;
    @Inject private ConfigService configService;
    
    @PostInject
    private void afterInject() {
        // ✅ 所有 @Inject 字段已就绪
        User user = userService.getCurrentUser();
        Config config = configService.loadConfig();
        initialize(user, config);
    }
}
```

**方法要求：**
- 必须是无参方法
- 可以是任何访问级别（private、protected、public）
- 可以有返回值（但会被忽略）
- 支持继承（父类的 @PostInject 方法会先执行）

---

## 常见问题

### Q: FxmlKit 必须使用依赖注入框架吗？

**A: 不需要！** FxmlKit 的核心功能（FXML 加载、样式表附加）无需任何 DI 框架。依赖注入是**完全可选**的，只有当你的应用需要时才使用。

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

### 问：@PostInject 方法何时执行？

**答：** 在依赖注入完成后执行。Controllers 和节点的执行时机不同：

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

**检查：**
1. 样式表是否与 FXML 同名？`LoginView.fxml` → `LoginView.css`
2. 样式表是否在同目录？
3. 是否禁用了自动附加？检查 `FxmlKit.isAutoAttachStyles()`

---

### Q: 如何在 JPro 中使用？

使用方式三（独立 DI 容器），每个用户会话创建独立的 Injector：

```java
// 每个用户
Injector userInjector = Guice.createInjector(new UserModule(user));
UserView view = userInjector.getInstance(UserView.class);
```

---

## 示例项目

`fxmlkit-samples` 模块包含完整的示例代码，展示了各种使用场景：

### Tier 1 - 零配置模式

```
tier1/
├── hello/          # 最简单的 Hello World
├── i18n/           # 国际化示例
├── provider/       # FxmlViewProvider 使用示例
└── viewpath/       # 自定义 FXML 路径（@FxmlPath）
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