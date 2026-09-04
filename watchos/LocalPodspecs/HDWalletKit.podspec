Pod::Spec.new do |s|
  s.name             = 'HDWalletKit'
  s.version          = '0.3.6'
  s.summary          = 'HDWalletKit in Swift.'
  s.description      = 'Hierarchical Deterministic (HD) wallet for cryptocurrencies in Swift.'
  s.homepage         = 'https://github.com/essentiaone/HDWallet'
  s.license          = { :type => 'MIT', :file => 'LICENSE' }
  s.author           = { 'EssentiaOne' => 'hi@essentia.one' }
  s.source           = { :git => 'https://github.com/essentiaone/HDWallet.git', :tag => s.version.to_s }

  s.ios.deployment_target = '11.0'
  s.osx.deployment_target = '10.13'
  s.watchos.deployment_target = '7.0' # Added watchOS support

  s.source_files = 'HDWalletKit/**/*'
  s.dependency 'CryptoSwift', '~> 1.0'
  s.swift_version = '5.0'
end
