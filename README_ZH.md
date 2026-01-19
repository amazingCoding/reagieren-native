# reagieren-native

[English](README.md) | [中文](README_ZH.md)

React Native 应用程序处理 Reagieren OTA 更新的原生模块。

## 安装

```bash
npm install reagieren-native
# 或
yarn add reagieren-native
```

### iOS 设置
```bash
cd ios && pod install
```

## 使用方法

```javascript
import Reagieren from 'reagieren-native';

// 在你的应用入口点或更新检查逻辑中
const checkForUpdates = async () => {
  try {
    const result = await Reagieren.update('YOUR_API_KEY', 'YOUR_PROJECT_ID');
    console.log('Update downloaded to:', result.path);
    
    // 重启应用以立即应用更新
    Reagieren.restart();
  } catch (error) {
    console.error('Update failed:', error);
  }
};
```

## 本地开发调试

如果你想在本地项目中调试这个库，可以按照以下步骤操作：

1. **安装依赖**
   在你的宿主 App 项目中：
   ```bash
   yarn add file:../path/to/reagieren-native
   # 或
   npm install ../path/to/reagieren-native
   ```

2. **配置 Metro (metro.config.js)**
   为了让 Metro Bundler 正确解析本地符号链接的模块，你需要修改 `metro.config.js`：

   ```javascript
   const path = require('path');
   const { getDefaultConfig } = require('expo/metro-config'); // 如果使用 Expo
   // 或 const { getDefaultConfig } = require('@react-native/metro-config');

   const config = getDefaultConfig(__dirname);

   // 指向 reagieren-native 的本地路径
   const moduleRoot = path.resolve(__dirname, '../path/to/reagieren-native');

   config.watchFolders = [moduleRoot];
   config.resolver.nodeModulesPaths = [
     path.resolve(__dirname, 'node_modules'),
     path.resolve(moduleRoot, 'node_modules'),
   ];
   // 避免 react 多重实例问题
   config.resolver.extraNodeModules = {
     react: path.resolve(__dirname, 'node_modules/react'),
     'react-native': path.resolve(__dirname, 'node_modules/react-native'),
   };

   module.exports = config;
   ```

3. **重新安装 Pods (iOS)**
   ```bash
   cd ios && pod install
   ```

## 原生集成

要实际加载下载的 JS bundle，你需要修改原生应用的初始化代码以检查下载的 bundle 路径。

### iOS (`AppDelegate.swift`)

修改你的 `sourceURL(for bridge:)` 逻辑以检查更新文件。

```swift
// Swift
func sourceURL(for bridge: RCTBridge) -> URL? {
#if DEBUG
    return RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "index")
#else
    // 1. 检查已下载的更新
    if let latestBundle = Reagieren.getLatestBundleURL() {
        return latestBundle
    }
    // 2. 回退到打包的资源
    return Bundle.main.url(forResource: "main", withExtension: "jsbundle")
#endif
}
```

### Android (`MainApplication.java`)

在你的 `ReactNativeHost` 中重写 `getJSBundleFile`。

```java
import com.reagieren.ReagierenModule; // 引入模块
import java.io.File;

// ... 在你的 ReactNativeHost 定义中
@Override
protected String getJSBundleFile() {
    // 1. 检查已下载的更新
    File latestBundle = ReagierenModule.getLatestBundle(getApplicationContext());
    if (latestBundle != null) {
        return latestBundle.getAbsolutePath();
    }
    
    // 2. 回退到 asset
    return super.getJSBundleFile(); // 通常默认为 "assets://index.android.bundle"
}
```
