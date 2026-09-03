package com.cbstudio.wearwallet.core.multichain.monero

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * Monero Native Library 載入器
 * 從 JAR 提取並載入 native libraries
 */
object MoneroNativeLoader {
    
    private var isLoaded = false
    
    /**
     * 載入 Monero native libraries
     */
    fun loadLibraries(context: Context): Boolean {
        if (isLoaded) {
            println("✅ Monero native libraries 已經載入")
            return true
        }
        
        return try {
            println("🔧 開始載入 Monero native libraries...")
            
            // 優先嘗試載入 Monerujo 原生庫
            if (tryLoadMonerujo()) {
                return true
            }
            
            // 回退到原有的 monero-java 實作
            println("🔄 回退到 monero-java 實作...")
            
            // 取得 CPU 架構
            val cpuAbi = android.os.Build.SUPPORTED_ABIS?.firstOrNull() 
                ?: android.os.Build.CPU_ABI
            
            val arch = when (cpuAbi) {
                "arm64-v8a" -> "linux-arm64"
                "x86_64" -> "linux-x86_64"
                "armeabi-v7a" -> "linux-arm64" // 嘗試使用 arm64 版本
                else -> {
                    println("❌ 不支援的 CPU 架構: $cpuAbi")
                    println("   支援的 ABIs: ${android.os.Build.SUPPORTED_ABIS?.joinToString()}")
                    return false
                }
            }
            
            println("📱 CPU 架構: $cpuAbi -> $arch")
            
            // 提取並載入 native libraries
            val libsToLoad = listOf("libmonero-cpp.so", "libmonero-java.so")
            
            for (libName in libsToLoad) {
                if (!extractAndLoadLibrary(context, "$arch/$libName", libName)) {
                    println("❌ 無法載入 $libName")
                    return false
                }
            }
            
            isLoaded = true
            println("✅ 所有 Monero native libraries 載入成功!")
            true
            
        } catch (e: Exception) {
            println("❌ Native library 載入失敗: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 嘗試載入 Monerujo 原生庫
     */
    private fun tryLoadMonerujo(): Boolean {
        return try {
            println("🚀 嘗試載入 Monerujo 原生庫...")
            System.loadLibrary("monerujo")
            isLoaded = true
            println("✅ libmonerujo.so 載入成功！使用 Monerujo 實作")
            true
        } catch (e: UnsatisfiedLinkError) {
            println("⚠️ 無法載入 libmonerujo.so: ${e.message}")
            false
        }
    }
    
    /**
     * 從 JAR 提取並載入單個 library
     */
    private fun extractAndLoadLibrary(context: Context, jarPath: String, libName: String): Boolean {
        return try {
            println("📦 提取 $libName from $jarPath...")
            
            // 取得 native library 目錄
            val libDir = File(context.applicationInfo.nativeLibraryDir)
            if (!libDir.exists()) {
                libDir.mkdirs()
            }
            
            // 目標文件
            val targetFile = File(libDir, libName)
            
            // 如果文件已存在且大小不為 0，嘗試直接載入
            if (targetFile.exists() && targetFile.length() > 0) {
                println("📁 文件已存在: ${targetFile.absolutePath}")
                System.load(targetFile.absolutePath)
                println("✅ 成功載入 $libName (從已存在文件)")
                return true
            }
            
            // 從 JAR 提取
            val classLoader = MoneroNativeLoader::class.java.classLoader
            
            // 列出 JAR 中的資源（除錯用）
            try {
                val resources = classLoader?.getResources("")
                while (resources?.hasMoreElements() == true) {
                    val url = resources.nextElement()
                    println("📦 Resource URL: $url")
                }
            } catch (e: Exception) {
                println("⚠️ 無法列出資源: ${e.message}")
            }
            
            val inputStream = classLoader?.getResourceAsStream(jarPath)
            
            if (inputStream == null) {
                println("⚠️ 無法從 JAR 找到 $jarPath")
                
                // 嘗試其他可能的路徑
                val alternativePaths = listOf(
                    "lib/$jarPath",
                    "native/$jarPath",
                    jarPath.replace("linux-", ""),
                    // monero-java JAR 的實際路徑結構
                    jarPath.replace("libmonero-", "monero-"),
                    "linux/$libName",
                    "arm64/$libName",
                    libName
                )
                
                println("🔍 嘗試替代路徑...")
                for (altPath in alternativePaths) {
                    println("   嘗試: $altPath")
                    val altStream = classLoader?.getResourceAsStream(altPath)
                    if (altStream != null) {
                        println("✅ 找到替代路徑: $altPath")
                        extractToFile(altStream, targetFile)
                        System.load(targetFile.absolutePath)
                        println("✅ 成功載入 $libName")
                        return true
                    }
                }
                
                // 最後嘗試：檢查 JAR 中的所有 .so 文件
                println("🔍 搜尋 JAR 中的所有 .so 文件...")
                try {
                    val jarUrl = classLoader?.getResource("META-INF/MANIFEST.MF")
                    println("   JAR URL: $jarUrl")
                } catch (e: Exception) {
                    println("   無法取得 JAR URL: ${e.message}")
                }
                
                return false
            }
            
            // 提取到文件
            extractToFile(inputStream, targetFile)
            
            // 載入 library
            System.load(targetFile.absolutePath)
            println("✅ 成功載入 $libName")
            true
            
        } catch (e: Exception) {
            println("❌ 載入 $libName 失敗: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 提取流到文件
     */
    private fun extractToFile(inputStream: java.io.InputStream, targetFile: File) {
        FileOutputStream(targetFile).use { output ->
            inputStream.use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
        }
        
        // 設置執行權限（僅限擁有者，避免 world readable 警告）
        targetFile.setExecutable(true, true)
        targetFile.setReadable(true, true)
        
        println("📄 提取完成: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
    }
    
    /**
     * 嘗試直接載入（用於測試）
     */
    fun tryDirectLoad(): Boolean {
        return try {
            System.loadLibrary("monero-cpp")
            System.loadLibrary("monero-java")
            isLoaded = true
            println("✅ 直接載入成功")
            true
        } catch (e: UnsatisfiedLinkError) {
            println("⚠️ 直接載入失敗: ${e.message}")
            false
        }
    }
}