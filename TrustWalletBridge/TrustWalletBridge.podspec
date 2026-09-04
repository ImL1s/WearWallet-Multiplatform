Pod::Spec.new do |s|
  s.name             = 'TrustWalletBridge'
  s.version          = '1.0.0'
  s.summary          = 'Swift bridge for TrustWallet Core Ed25519 functionality'
  s.description      = <<-DESC
  Provides Swift/Objective-C bridge for TrustWallet Core's Ed25519 signing,
  designed to be consumed by Kotlin Multiplatform via cinterop.
                       DESC

  s.homepage         = 'https://github.com/cbstudio/wearwallet'
  s.license          = { :type => 'MIT' }
  s.author           = { 'CBStudio' => 'dev@cbstudio.com' }
  s.source           = { :path => '.' }

  # 支援平台
  s.ios.deployment_target = '13.0'
  # watchOS 不支援 TrustWallet Core

  # 源代碼
  s.source_files = 'Classes/**/*.{h,m,swift}'
  s.public_header_files = 'Classes/**/*.h'

  # 依賴 TrustWallet Core
  s.dependency 'TrustWalletCore', '4.1.17'

  # Swift 版本
  s.swift_version = '5.0'

  # Pod 設置
  s.static_framework = true
  s.module_name = 'TrustWalletBridge'
end
