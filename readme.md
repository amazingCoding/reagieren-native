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
    if let latestBundle = ReagierenBundleLoader.getLatestBundleURL() {
        return latestBundle
    }
    // 2. Fallback to bundled resource
    return Bundle.main.url(forResource: "main", withExtension: "jsbundle")
#endif
}

// Helper class to find the latest bundle
class ReagierenBundleLoader {
    static func getLatestBundleURL() -> URL? {
        let fileManager = FileManager.default
        guard let documentsDir = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first else { return nil }
        let versionsDir = documentsDir.appendingPathComponent("reagieren_versions")
        
        guard let contents = try? fileManager.contentsOfDirectory(at: versionsDir, includingPropertiesForKeys: [.creationDateKey], options: .skipsHiddenFiles) else { return nil }
        
        // Sort by creation date (newest first)
        let sorted = contents.sorted {
            let d1 = (try? $0.resourceValues(forKeys: [.creationDateKey]))?.creationDate ?? Date.distantPast
            let d2 = (try? $1.resourceValues(forKeys: [.creationDateKey]))?.creationDate ?? Date.distantPast
            return d1 > d2 
        }
        
        // Return main.jsbundle inside the newest folder
        // Note: Adjust "main.jsbundle" if your unzipped structure is different
        if let newestFolder = sorted.first {
            return newestFolder.appendingPathComponent("main.jsbundle") 
        }
        
        return nil
    }
}
```

### Android (`MainApplication.java`)

Override `getJSBundleFile` in your `ReactNativeHost`.

```java
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

// ... inside your ReactNativeHost definition
@Override
protected String getJSBundleFile() {
    // 1. Check for downloaded updates
    File latestBundle = ReagierenBundleLoader.getLatestBundle(getApplicationContext());
    if (latestBundle != null) {
        return latestBundle.getAbsolutePath();
    }
    
    // 2. Fallback to asset
    return super.getJSBundleFile(); // which defaults to "assets://index.android.bundle" usually
}

// Helper class
class ReagierenBundleLoader {
    public static File getLatestBundle(Context context) {
        File versionsDir = new File(context.getFilesDir(), "reagieren_versions");
        if (!versionsDir.exists() || !versionsDir.isDirectory()) return null;

        File[] files = versionsDir.listFiles(File::isDirectory);
        if (files == null || files.length == 0) return null;

        // Sort by modification time (newest first)
        Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        
        File newestFolder = files[0];
        // Note: Adjust "index.android.bundle" if your unzipped structure is different
        File bundleFile = new File(newestFolder, "index.android.bundle");
        
        return bundleFile.exists() ? bundleFile : null;
    }
}
```
