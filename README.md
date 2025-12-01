# FxmlKit

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**FxmlKit = Automatic FXML Loading + Optional Dependency Injection + Simplified JavaFX Development**

```java
// Zero configuration - automatic FXML loading
new MainView();

// With dependency injection
new MainView(diAdapter);
```

A modern JavaFX FXML framework that eliminates boilerplate code and provides optional, progressive dependency injection support.

[中文文档](README_CN.md) | [Sample Projects](fxmlkit-samples)

---

## Table of Contents

- [Why FxmlKit](#why-fxmlkit)
- [Core Features](#core-features)
- [Acknowledgments](#acknowledgments)
- [Quick Start](#quick-start)
- [Usage](#usage)
- [Core Concepts](#core-concepts)
- [Annotations](#annotations)
- [FAQ](#faq)
- [Sample Projects](#sample-projects)

---

## Why FxmlKit

### Pain Point 1: Native FXML Loading Requires Massive Boilerplate

Every view requires repetitive code: getting URL, configuring FXMLLoader, handling exceptions, loading stylesheets...

**FxmlKit Solution:**
```java
// ✅ One line of code, everything handled automatically
public class LoginView extends FxmlView<LoginController> {
}
```

Automatically handles FXML parsing, stylesheet attachment, controller creation, and exception handling.

---

### Pain Point 2: FXML Custom Components Cannot Receive Dependency Injection

In traditional approaches, custom components declared in FXML are directly instantiated by FXMLLoader and cannot access the DI container.

**FxmlKit Solution:**

Controller auto-injection:
```java
import javax.inject.Inject;  // Standard JSR-330 annotation

public class LoginController {
    @Inject private UserService userService;
    
    @PostInject  // Automatically invoked
    private void afterInject() {
        // Dependencies are ready
    }
}
```

FXML nodes can also receive auto-injection:
```java
@FxmlObject  // One annotation to enable injection
public class StatusCard extends VBox {
    @Inject private StatusService statusService;
    
    @PostInject
    private void afterInject() {
        updateStatus();
    }
}
```

FXML usage:
```xml
<VBox>
    <StatusCard />  <!-- Automatic dependency injection -->
</VBox>
```

**Key point: Dependency injection is optional!** If you don't need DI, FxmlKit still eliminates FXML loading boilerplate.

---

## Core Features

- **Zero Configuration** — Works out of the box, no setup required
- **Convention over Configuration** — Automatically discovers FXML and stylesheet files
- **Optional Dependency Injection** — Works without DI frameworks, add them when needed
- **Automatic Stylesheets** — Auto-attaches `.bss` and `.css` files
- **Nested FXML** — Full support for `<fx:include>` hierarchies
- **JPro Ready** — Supports multi-user web application data isolation (independent DI container per user session for data security)
- **High Performance** — Intelligent caching and performance optimization

**Comparison with Native JavaFX:**

| Feature | Native JavaFX | FxmlKit |
|---------|---------------|---------|
| Automatic FXML Loading | ❌ Manual loading code | ✅ Zero-config auto-loading |
| Automatic Stylesheet Attachment | ❌ Manual code required | ✅ Auto-attach (including nested FXML) |
| Controller Dependency Injection | ⚠️ Manual factory setup | ✅ Automatic injection |
| **FXML Node Injection** | ❌ **Nearly impossible** | ✅ **@FxmlObject - one line** |
| Multi-layer fx:include Support | ⚠️ Partial support | ✅ Full support (with injection & styles) |
| @PostInject Lifecycle | ❌ None | ✅ Supported |
| JPro Multi-user Isolation | ❌ Manual implementation | ✅ Native support |

---

## Acknowledgments

FxmlKit's convention-over-configuration approach (auto-resolving FXML/CSS by class name) was influenced by [afterburner.fx](https://github.com/AdamBien/afterburner.fx). We've extended this foundation with FXML node injection, multi-layer nesting support, and JPro multi-user isolation.

---

## Quick Start

### Installation

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

**If using Guice:**
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

**If using other DI frameworks:** Continue using the core `fxmlkit` module and implement the `DiAdapter` interface or extend the `BaseDiAdapter` class. Even with Guice, you can choose not to depend on the `fxmlkit-guice` module and implement a `GuiceDiAdapter` yourself (refer to `fxmlkit-guice` source code - the implementation is very simple).

---

### Create Your First View

**1. Create FXML File**

`src/main/resources/com/example/LoginView.fxml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.layout.*?>
<?import javafx.scene.control.*?>

<VBox xmlns:fx="http://javafx.com/fxml" spacing="10">
    <TextField fx:id="usernameField" promptText="Username"/>
    <PasswordField fx:id="passwordField" promptText="Password"/>
    <Button text="Login" onAction="#handleLogin"/>
</VBox>
```

**2. Create Controller**

```java
package com.example;

public class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    
    @FXML
    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        // Handle login logic
    }
}
```

**3. Create View**

```java
package com.example;

import com.dlsc.fxmlkit.FxmlView;

public class LoginView extends FxmlView<LoginController> {
    // That's it! FXML is automatically loaded
}
```

**4. Use View**

```java
public class App extends Application {
    @Override
    public void start(Stage stage) {
        LoginView loginView = new LoginView();
        Scene scene = new Scene(loginView);
        stage.setScene(scene);
        stage.show();
    }
}
```

---

## Usage

FxmlKit supports three usage patterns - choose based on your needs:

### Method 1: Zero Configuration (Recommended for Beginners)

No DI framework required, automatic FXML loading and stylesheet attachment.

```java
public class LoginView extends FxmlView<LoginController> {
    // FXML automatically loaded and bound to controller
}

// Usage
LoginView view = new LoginView();
Scene scene = new Scene(view);
```

**Features:**
- ✅ Automatic FXML loading
- ✅ Automatic stylesheet attachment
- ✅ Automatic controller creation
- ❌ No dependency injection

---

### Method 2: Optional Dependency Injection (Recommended for Enterprise)

Set up a global DI adapter once, and all views automatically support dependency injection.

```java
// Application startup - set global DI adapter
Injector injector = Guice.createInjector(new AbstractModule() {
    @Override
    protected void configure() {
        bind(UserService.class).toInstance(new UserService());
        bind(ConfigService.class).toInstance(new ConfigService());
    }
});
FxmlKit.setDiAdapter(new GuiceDiAdapter(injector));

// Create view - controller and nodes automatically receive injection
public class LoginView extends FxmlView<LoginController> {
}

// Usage - same as zero configuration mode
LoginView view = new LoginView();
```

**Features:**
- ✅ Global DI configuration - set once, use everywhere
- ✅ Automatic controller injection
- ✅ Automatic FXML node injection (with `@FxmlObject`)
- ✅ Supports multiple DI frameworks (Guice, Spring, CDI, etc.)

---

### Method 3: Independent DI Container (Recommended for JPro Multi-User)

Each view uses an independent DI container, suitable for scenarios requiring data isolation (e.g., JPro multi-user web applications).

```java
// Create independent DI container for each user session
Injector userInjector = Guice.createInjector(new AbstractModule() {
    @Override
    protected void configure() {
        bind(UserSession.class).toInstance(new UserSession(userId));
        bind(UserService.class).toInstance(new UserService());
    }
});

// Pass DI adapter when creating view
LoginView view = new LoginView(new GuiceDiAdapter(userInjector));
```

**Use Cases:**
- JPro web applications (one DI container per user session)
- Desktop applications (one DI container per Tab/Window)
- Scenarios requiring strict data isolation

---

## Core Concepts

### Automatic File Resolution

FxmlKit uses convention over configuration to automatically find FXML and stylesheet files:

```
src/main/resources/com/example/
├── UserView.fxml          ← UserView.java auto-matches
├── UserView.css           ← Auto-attached
└── UserView.bss           ← Binary stylesheet (priority)
```

**Convention:** FXML file has the same name as Java class and is in the same resource directory.

For detailed resolution rules, priorities, and custom path configuration, see [Wiki - File Resolution](https://github.com/dlsc-software-consulting-gmbh/FxmlKit/wiki).

---

### FxmlView vs FxmlViewProvider

**FxmlView:** Inherit for simple views, FXML is the view root node

```java
public class LoginView extends FxmlView<LoginController> {
}

// Usage
LoginView view = new LoginView();  // view itself is VBox (FXML root)
scene.setRoot(view);
```

**FxmlViewProvider:** Use for complex layouts where FXML is embedded as a child node

```java
public class MainViewProvider extends FxmlViewProvider<MainController> {
    public MainViewProvider() {
        BorderPane root = new BorderPane();
        root.setCenter(getView());  // FXML content
        root.setTop(createToolbar());
        setRoot(root);
    }
}
```

---

### Injecting Custom Components

Mark custom components with `@FxmlObject` to enable dependency injection:

```java
@FxmlObject
public class StatusCard extends VBox {
    @Inject private StatusService statusService;
    
    @PostInject
    private void afterInject() {
        // statusService is ready
        updateStatus();
    }
}
```

Use in FXML:
```xml
<VBox>
    <StatusCard />  <!-- Automatic injection -->
</VBox>
```

---

### Injection Strategy

FxmlKit defaults to `EXPLICIT_ONLY` strategy (only injects objects marked with `@FxmlObject`).

For other injection strategies (AUTO, DISABLED) and detailed configuration, see [Wiki - Injection Strategy](https://github.com/dlsc-software-consulting-gmbh/FxmlKit/wiki).

---

## Annotations

### @FxmlPath - Custom FXML File Path

**Purpose:** Specify FXML file location, overriding default auto-resolution rules.

**Use Case:** Typically not needed, as FxmlKit auto-resolves. Only use when FXML file is not in default location.

```java
@FxmlPath("/shared/Common.fxml")
public class LoginView extends FxmlView<LoginController> {}
```

For detailed usage and path resolution rules, see [Wiki - FXML Path Configuration](https://github.com/dlsc-software-consulting-gmbh/FxmlKit/wiki).

---

### @FxmlObject - Enable FXML Object Injection

**Purpose:** Mark a class to enable dependency injection when created in FXML.

**Supported Object Types:**
- Custom JavaFX controls (Button, TextField subclasses, etc.)
- Layout containers (Pane, HBox, VBox subclasses, etc.)
- Non-visual objects (MenuItem, ContextMenu, etc.)
- Any custom class declared in FXML

**Example - Custom Control:**
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

**Example - Non-Visual Object:**
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

FXML usage:
```xml
<MenuBar>
    <Menu text="Actions">
        <CustomMenuItem text="Execute"/>  <!-- Also receives injection -->
    </Menu>
</MenuBar>
```

**Note:**
- Custom objects without `@FxmlObject` won't receive dependency injection (unless injection strategy is set to `AUTO`)
- If using `AUTO` strategy but want to exclude certain types, use `FxmlKit.excludeNodeType()` or add `@SkipInjection` annotation on the class

---

### @PostInject - Post-Injection Callback

**Purpose:** Mark a method to be automatically invoked after all `@Inject` field injections are complete.

**Use Case:** Initialize components, load data, or set up listeners after dependencies are ready.

**Execution Timing:** Immediately after all `@Inject` field injections are complete.

**Example:**
```java
public class UserProfileController {
    @Inject private UserService userService;
    @Inject private ConfigService configService;
    
    @PostInject
    private void afterInject() {
        // All @Inject fields are now ready
        userService.loadUserData();
        configService.applySettings();
    }
}
```

**Method Requirements:**
- Must be no-arg
- Can have any access modifier (private, protected, public)
- Supports inheritance (parent class `@PostInject` methods execute first)

---

## FAQ

### Q: Must FxmlKit use a dependency injection framework?

**A:** No! FxmlKit works perfectly without DI frameworks. You can use just `FxmlView` for automatic FXML loading and stylesheet attachment. Add DI only when you need it.

---

### Q: When and how to use the built-in LiteDiAdapter?

**A:** If you need simple dependency injection but don't want to introduce Guice or other complete DI frameworks, the built-in LiteDiAdapter is perfect for you.

**Suitable for:**
- Small projects or prototyping
- Applications with simple dependencies
- Scenarios where you don't want additional dependencies

**Limitations:** LiteDiAdapter only supports instance binding and doesn't support constructor injection, scope management, or other advanced features. If you need these, use Guice or other mature DI frameworks.

**Usage:**
```java
LiteDiAdapter di = new LiteDiAdapter();
di.bindInstance(UserService.class, new UserService());
di.bindInstance(ConfigService.class, new ConfigService());
FxmlKit.setDiAdapter(di);
```

---

### Q: Why are @Inject fields null?

**A:** Common causes:

1. **No DI adapter set** - Call `FxmlKit.setDiAdapter()`
2. **Object not in DI container** - Ensure the object is bound in your DI configuration
3. **Custom node not marked with @FxmlObject** - FXML nodes need `@FxmlObject` annotation (unless using AUTO strategy)

---

### Q: When does @PostInject method execute?

**A:** Immediately after all `@Inject` field injections are complete.

```java
public class LoginController {
    @Inject private UserService userService;  // ① Injected
    
    @PostInject
    private void afterInject() {
        // ② Executes here - userService is ready
        userService.loadData();
    }
    
    @FXML
    private void initialize() {
        // ③ Then JavaFX's initialize()
    }
}
```

---

### Q: Stylesheet not working?

**A:** Check these:

1. **File naming:** Must match class name - `LoginView.java` → `LoginView.css`
2. **File location:** Must be in same package resource directory
3. **Auto-attach enabled:** `FxmlKit.setAutoAttachStyles(true)` (default is true)
4. **CSS priority:** `.bss` files have higher priority than `.css`

---

### Q: How to use with JPro?

**A:** FxmlKit is JPro-ready. Create an independent DI container for each user session:

```java
public class JProApp extends Application {
    @Override
    public void start(Stage stage) {
        // Create independent DI container per user
        Injector userInjector = createUserInjector();
        
        // Pass DI adapter when creating view
        MainView view = new MainView(new GuiceDiAdapter(userInjector));
        
        Scene scene = new Scene(view);
        stage.setScene(scene);
    }
}
```

See `fxmlkit-samples` module's `tier3.multiuser` package for a complete implementation simulating JPro multi-user scenarios (using TabPane to simulate multi-user sessions).

---

## Sample Projects

Complete sample code is in the `fxmlkit-samples` module, organized into three tiers by complexity:

### Tier 1 - Zero Configuration Mode

```
tier1/
├── hello/          # Simplest Hello World
├── provider/       # FxmlViewProvider usage examples
└── viewpath/       # Custom FXML path
```

### Tier 2 - Optional Dependency Injection

```
tier2/
├── fxmlobject/     # @FxmlObject node injection examples
├── guice/          # Guice integration examples
└── login/          # Complete login application example
```

### Tier 3 - JPro Multi-User Isolation

```
tier3.multiuser/    # Simulates JPro multi-user scenario (using TabPane)
```
