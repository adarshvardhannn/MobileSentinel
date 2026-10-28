# Contributing to MobileSentinel

We welcome contributions from the community! Whether you are fixing bugs, improving the risk engine, or adding new features, your help is appreciated.

## Getting Started

1. Fork the repository on GitHub.
2. Clone your fork locally.
3. Create a new branch for your feature or bug fix:
   ```bash
   git checkout -b feature/your-feature-name
   ```
4. Set up your local environment (add Gemini API key to `local.properties`).
5. Make your changes and ensure the project still builds.

## Guidelines

- Follow the existing code style and architecture (MVVM, Hilt, Clean Architecture principles).
- Keep detection logic centralized in the `utils` or `domain` layers.
- If adding new risk factors, update the `SecurityRiskEngine`.
- Ensure any UI changes follow Material 3 guidelines.
- Write unit tests for critical security logic.

## Pull Request Process

1. Push your branch to your fork.
2. Open a Pull Request against the main repository's `main` branch.
3. Provide a clear description of your changes and why they are necessary.
4. Wait for review and address any feedback.

Thank you for contributing to mobile security!
