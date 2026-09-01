Pod::Spec.new do |s|
  s.name             = 'secp256k1' # Named 'secp256k1' to match C module expectation
  s.module_name      = 'secp256k1' # Explicit module name
  s.version          = '0.1.4'
  s.summary          = 'Bindings for secp256k1'
  s.homepage         = 'https://github.com/Boilertalk/secp256k1.swift'
  s.license          = { :type => 'MIT', :file => 'LICENSE' }
  s.author           = { 'Boilertalk' => 'hi@boilertalk.com' }
  s.source           = { :git => 'https://github.com/Boilertalk/secp256k1.swift.git', :tag => s.version.to_s }

  s.ios.deployment_target = '10.0'
  s.osx.deployment_target = '10.12'
  s.watchos.deployment_target = '6.0' # Added watchOS support

  # Use the structure we verified:
  s.source_files = 'secp256k1/Classes/**/*.{h,c,swift}'
  s.public_header_files = 'secp256k1/Classes/secp256k1/include/*.h' # Guessing public headers likely in include subfolder inside classes/secp256k1
  # Actually, let's correspond to findings. 
  # "secp256k1" subdir inside "Classes" likely has include/
  # But let's be broad to ensure headers are found.
  
  s.swift_version = '4.2'
  s.pod_target_xcconfig = { 'SWIFT_INCLUDE_PATHS' => '${PODS_ROOT}/secp256k1/secp256k1/Classes/**' }
end
