# Security Policy

## Reporting a Vulnerability

We take the security of MobileSentinel seriously. If you believe you have found a security vulnerability in this project, please report it to us by following these steps:

1. **Do not disclose the vulnerability publicly** until we have had a chance to investigate and address it.
2. Send a detailed report of the vulnerability to the maintainer's contact email (found on their GitHub profile).
3. Include as much information as possible, including steps to reproduce, the impact of the vulnerability, and any potential fixes.

## Gemini API Key Safety

MobileSentinel is designed to use an API key for Gemini AI explanations. 

- **WARNING**: Never commit your real API key to this repository.
- The project is configured to read the key from `local.properties`, which is ignored by Git.
- If you accidentally expose your key, revoke it immediately in the Google AI Studio console.

## Limitations

MobileSentinel is a security *assessment* tool. It analyzes permissions, configurations, and network indicators to estimate risk. It may produce false positives or miss sophisticated threats. Use it as part of a broader security strategy.
