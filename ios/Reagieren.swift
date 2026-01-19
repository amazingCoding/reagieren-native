import Foundation
import React
import SSZipArchive

@objc(Reagieren)
class Reagieren: NSObject {
    
    private let kDeviceIdKey = "reagieren_device_id"
    private let kVersionsDirName = "reagieren_versions"
    
    @objc(update:projectId:resolve:reject:)
    func update(_ apiKey: String, projectId: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let deviceId = getDeviceId()
        guard let nativeVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String else {
            reject("VERSION_ERROR", "Could not get native version", nil)
            return
        }
        
        let urlString = "https://api.reagieren.com/api/projects/\(projectId)/versions/latest?platform=ios&min_native_version=\(nativeVersion)"
        guard let url = URL(string: urlString) else {
            reject("URL_ERROR", "Invalid URL", nil)
            return
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue(apiKey, forHTTPHeaderField: "X-API-Key")
        
        let task = URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            if let error = error {
                reject("NETWORK_ERROR", error.localizedDescription, error)
                return
            }
            
            guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
                reject("API_ERROR", "Invalid response from server", nil)
                return
            }
            
            guard let data = data else {
                reject("NO_DATA", "No data received", nil)
                return
            }
            
            do {
                if let json = try JSONSerialization.jsonObject(with: data, options: []) as? [String: Any],
                   let downloadUrlString = json["download_url"] as? String {
                    
                    self?.downloadAndUnzip(downloadUrl: downloadUrlString, deviceId: deviceId, resolve: resolve, reject: reject)
                    
                } else {
                    reject("PARSE_ERROR", "Invalid JSON structure", nil)
                }
            } catch {
                reject("JSON_ERROR", error.localizedDescription, error)
            }
        }
        task.resume()
    }
    
    private func getDeviceId() -> String {
        if let existingId = UserDefaults.standard.string(forKey: kDeviceIdKey) {
            return existingId
        }
        let newId = UUID().uuidString
        UserDefaults.standard.set(newId, forKey: kDeviceIdKey)
        return newId
    }
    
    private func downloadAndUnzip(downloadUrl: String, deviceId: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        // Append deviceId to update usage stats/history as per readme
        // Assuming downloadUrl needs deviceId and platform appended if not present
        var urlComp = URLComponents(string: downloadUrl)
        var queryItems = urlComp?.queryItems ?? []
        
        // Add deviceId if not present (assuming the provided download_url is the base or partial)
        // If download_url from server is complete signed url, user might not need to add these.
        // But README says GET /api/download needs these params.
        // If downloadUrl matches /api/download, we append.
        
        if downloadUrl.contains("/api/download") {
            if !queryItems.contains(where: { $0.name == "deviceId" }) {
                queryItems.append(URLQueryItem(name: "deviceId", value: deviceId))
            }
             if !queryItems.contains(where: { $0.name == "platform" }) {
                queryItems.append(URLQueryItem(name: "platform", value: "ios"))
            }
            urlComp?.queryItems = queryItems
        }
        
        guard let finalUrl = urlComp?.url else {
            reject("URL_ERROR", "Invalid download URL", nil)
            return
        }

        let task = URLSession.shared.downloadTask(with: finalUrl) { [weak self] localURL, response, error in
            if let error = error {
                reject("DOWNLOAD_ERROR", error.localizedDescription, error)
                return
            }
            
            guard let localURL = localURL else {
                reject("DOWNLOAD_ERROR", "File not saved", nil)
               return
            }
            
            self?.processDownloadedFile(sourceUrl: localURL, resolve: resolve, reject: reject)
        }
        task.resume()
    }
    
    private func processDownloadedFile(sourceUrl: URL, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        let fileManager = FileManager.default
        guard let documentsDir = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first else {
            reject("FS_ERROR", "Could not find documents directory", nil)
            return
        }
        
        let versionsDir = documentsDir.appendingPathComponent(kVersionsDirName)
        if !fileManager.fileExists(atPath: versionsDir.path) {
            try? fileManager.createDirectory(at: versionsDir, withIntermediateDirectories: true)
        }
        
        // Generate a unique version folder name (e.g. timestamp)
        let timestamp = Int(Date().timeIntervalSince1970)
        let destinationDir = versionsDir.appendingPathComponent("\(timestamp)")
        
        // Unzip
        let success = SSZipArchive.unzipFile(atPath: sourceUrl.path, toDestination: destinationDir.path)
        
        if success {
            // Clean up old versions
            cleanupOldVersions(versionsDir: versionsDir)
            
            resolve(["path": destinationDir.path])
        } else {
            reject("UNZIP_ERROR", "Failed to unzip file", nil)
        }
    }
    
    private func cleanupOldVersions(versionsDir: URL) {
        let fileManager = FileManager.default
        do {
            let files = try fileManager.contentsOfDirectory(at: versionsDir, includingPropertiesForKeys: [.creationDateKey], options: [])
            
            if files.count > 3 {
                let sortedFiles = files.sorted {
                    let d1 = (try? $0.resourceValues(forKeys: [.creationDateKey]))?.creationDate ?? Date.distantPast
                    let d2 = (try? $1.resourceValues(forKeys: [.creationDateKey]))?.creationDate ?? Date.distantPast
                    return d1 < d2 // Oldest first
                }
                
                // Remove oldest until we have 3
                let filesToRemove = sortedFiles.dropLast(3)
                for file in filesToRemove {
                    try? fileManager.removeItem(at: file)
                }
            }
        } catch {
            print("Cleanup error: \(error)")
        }
    }

    @objc(restart)
    func restart() {
        DispatchQueue.main.async {
            NotificationCenter.default.post(name: NSNotification.Name("RCTReloadNotification"), object: nil)
        }
    }

    @objc
    public static func getLatestBundleURL() -> URL? {
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
        if let newestFolder = sorted.first {
            return newestFolder.appendingPathComponent("main.jsbundle") 
        }
        
        return nil
    }
}
