# Contributing to kotlin-crypto-pure

Thank you for considering contributing to kotlin-crypto-pure! 🎉

## How to Contribute

### Reporting Bugs

1. Check existing [Issues](https://github.com/ImL1s/kotlin-crypto-pure/issues)
2. Create a new issue with:
   - Clear title and description
   - Steps to reproduce
   - Expected vs actual behavior
   - Platform/version information

### Feature Requests

1. Open an issue with the `enhancement` label
2. Describe the use case and benefits
3. Include example code if possible

### Pull Requests

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass (`./gradlew testDebugUnitTest`)
6. Commit with clear messages
7. Push and create a Pull Request

## Development Setup

### Prerequisites

- JDK 17+
- Android Studio or IntelliJ IDEA
- Xcode (for iOS/watchOS development)

### Building

```bash
# Android tests
./gradlew testDebugUnitTest

# iOS build
./gradlew compileKotlinIosArm64

# watchOS build
./gradlew compileKotlinWatchosArm64
```

## Code Style

- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add KDoc for public APIs
- Keep functions focused and small

## Security

⚠️ **Security is critical for cryptographic libraries!**

- Never commit private keys or secrets
- Follow secure coding practices
- Report security vulnerabilities privately

## License

By contributing, you agree that your contributions will be licensed under the Apache 2.0 License.
