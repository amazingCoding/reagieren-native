# reagieren-native

[English](README.md) | [中文](README_ZH.md)

Native module for handling Reagieren OTA updates in React Native apps.

## Installation

```bash
npm install reagieren-native
# or
yarn add reagieren-native
```

### iOS Setup
```bash
cd ios && pod install
```

## Usage

```javascript
import Reagieren from 'reagieren-native';

// In your app's entry point or update check logic
const checkForUpdates = async () => {
  try {
    const result = await Reagieren.update('YOUR_API_KEY', 'YOUR_PROJECT_ID');
    console.log('Update downloaded to:', result.path);
    
    // Restart the app to apply the update immediately
    Reagieren.restart();
  } catch (error) {
    console.error('Update failed:', error);
  }
};
```

## Local Development

If you want to use this library locally for debugging:

1. **Install Dependency**
   In your host app:
   ```bash
   yarn add file:../path/to/reagieren-native
   # or
   npm install ../path/to/reagieren-native
   ```

2. **Configure Metro (metro.config.js)**
   To ensure Metro resolves the symlinked module correctly:

   ```javascript
   const path = require('path');
   const { getDefaultConfig } = require('@react-native/metro-config');

   const config = getDefaultConfig(__dirname);
   const moduleRoot = path.resolve(__dirname, '../path/to/reagieren-native');

   config.watchFolders = [moduleRoot];
   config.resolver.nodeModulesPaths = [
     path.resolve(__dirname, 'node_modules'),
     path.resolve(moduleRoot, 'node_modules'),
   ];
   config.resolver.extraNodeModules = {
     react: path.resolve(__dirname, 'node_modules/react'),
     'react-native': path.resolve(__dirname, 'node_modules/react-native'),
   };

   module.exports = config;
   ```

3. **Pod Install (iOS)**
   ```bash
   cd ios && pod install
   ```

## Native Integration

To actually load the downloaded JS bundle, you need to modify your native app initialization code to check for the downloaded bundle path.

### iOS (`AppDelegate.swift`)

Modify your `sourceURL(for bridge:)` logic to check for the update file.

```swift
func sourceURL(for bridge: RCTBridge) -> URL? {
#if DEBUG
    return RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "index")
#else
    // 1. Check for downloaded updates
    if let latestBundle = Reagieren.getLatestBundleURL() {
        return latestBundle
    }
    // 2. Fallback to bundled resource
    return Bundle.main.url(forResource: "main", withExtension: "jsbundle")
#endif
}
```

### Android (`MainApplication.java`)

Override `getJSBundleFile` in your `ReactNativeHost`.

```java
import com.reagieren.ReagierenModule; // Import module
import java.io.File;

// ... inside your ReactNativeHost definition
@Override
protected String getJSBundleFile() {
    // 1. Check for downloaded updates
    File latestBundle = ReagierenModule.getLatestBundle(getApplicationContext());
    if (latestBundle != null) {
        return latestBundle.getAbsolutePath();
    }
    
    // 2. Fallback to asset
    return super.getJSBundleFile(); // which defaults to "assets://index.android.bundle" usually
}
```
