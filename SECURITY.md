# Security Policy / 安全政策

We value the security research community and believe that responsible disclosure is essential to protecting our users' privacy and security.

我们重视安全研究社区，并坚信负责任的漏洞披露对于保护用户隐私与安全至关重要。

## Supported Versions / 支持的版本

Please always use the latest version for security updates.

请始终使用最新版本以获得安全更新。

| Version | Supported          |
| ------- | ------------------ |
| latest  | :white_check_mark: |
| < latest| :x:                |

## Reporting a Vulnerability / 报告漏洞

**Contact / 联系方式：** [mail@sunnyhmz.top](mailto:mail@sunnyhmz.top)

If you discover a security issue, please do not publicly disclose the details. Instead, send an email to the address above. We will begin working on it as soon as possible upon receipt, and keep you updated on our progress.

如果发现安全问题，请不要公开披露细节。请发送邮件至上方地址。我们会在收到报告后尽快着手处理，并持续向您同步进展。

## Security Vulnerability Reporting Guidelines / 安全漏洞报告指引

Report code security vulnerabilities, privacy leaks, and other sensitive information to us to enhance the security and reliability of the project. It covers multiple aspects, including code vulnerabilities, dependency vulnerabilities, and security alerts:

- Vulnerability Reports: A vulnerability report refers to potential security issues or vulnerabilities discovered in the code. These vulnerabilities may lead to system compromise or damage, such as SQL injection, cross-site scripting (XSS), and path traversal.
- Dependency Vulnerabilities: Security issues present in the external libraries or software packages that the project depends on, to prevent attackers from exploiting dependency vulnerabilities to execute malicious code.
- Security Alerts: Security risks existing in the code, such as risks arising from coding errors, design flaws, or unauthorized access.

向我们报告代码安全漏洞和隐私泄漏等敏感信息，促进项目的安全性和可靠性。它涵盖了多个方面，包括代码漏洞、依赖关系漏洞、安全警报等：

- 漏洞报告：漏洞报告指的是在代码中发现的潜在安全问题或漏洞。这些漏洞可能导致系统受到攻击或遭受损害，例如 SQL 注入、跨站脚本（XSS）、路径遍历等。
- 依赖漏洞：项目所依赖的外部库或软件包中存在的安全问题，避免攻击者可以利用依赖漏洞来执行恶意代码。
- 安全警报：代码中存在的安全风险，例如编码错误、设计缺陷或未经授权的访问等原因而产生的安全风险等。

### CVE Number / CVE 编号

If a CVE number is assigned to the vulnerability, please provide it; if unclear or not yet available, you may omit it.

如果有漏洞的CVE编号，请提供；如果不清楚或尚未知晓，请忽略。

### Impact / 影响程度

The impact of a security vulnerability usually depends on the type of vulnerability and the attack vector. For example, if the vulnerability is a cross-site scripting (XSS) issue, the affected users may include all users who visit the compromised page. For other types of vulnerabilities, such as authentication bypass or privilege escalation, the scope of impact may be more limited, but it can still have a significant effect on the security of the system.

安全漏洞的影响程度通常取决于漏洞的类型和攻击向量。例如，如果漏洞是跨站脚本（XSS），受影响的用户可能包括访问受感染页面的所有用户。对于其他类型的漏洞，例如身份验证绕过或权限提升，影响范围可能更加有限，但仍然会对系统的安全性产生重大影响。

What type of vulnerability is this? Who is affected?

这是什么类型的漏洞？谁会受到影响？

### Patch / 补丁

If the security vulnerability has been fixed, please provide the relevant patch information to the project members. This may involve guiding project members to upgrade to a specific software version that contains the fix. Typically, the latest version includes the most recent security fixes.

如果已经修复了安全漏洞，请向项目成员提供相关的补丁信息。这可能涉及指导项目成员升级到包含修复程序的特定软件版本。通常情况下，最新版本包含了最新的安全修复。

Has the issue been patched? Which versions should be upgraded to?

问题是否已修补？应升级到哪些版本？

### Workaround / 解决方法

Even when no patch is available, project members can still mitigate the risk of the security vulnerability through temporary measures such as "workarounds" or "interim fixes." These workarounds may include configuring system settings, disabling the affected feature or module, or implementing other security controls to limit exploitation of the vulnerability.

即使没有可用的补丁，项目成员仍然可以通过“解决方案”或“临时修复”等临时措施来减轻安全漏洞的风险，这些解决方法可能包括配置系统设置、禁用受影响的功能或模块，或者实施其他安全控制来限制漏洞的利用。

Is there a way to fix or correct the vulnerability without upgrading?

是否有办法在不升级的情况下修复或纠正漏洞？

### References / 参考资料

To gain a deeper understanding of the vulnerability and the associated fixes and workarounds, please provide additional reference materials. These materials may include links to security vulnerability reports, official patch notes, security advisories, or other relevant documentation. Project members can consult these references to obtain more detailed information about the vulnerability and its impact, as well as the appropriate measures to protect the system.

为了更深入地了解漏洞以及相关的修复和解决方法，请提供更多参考资料。这些资料可能包括安全漏洞报告、官方补丁说明、安全建议或其他相关文档的链接。项目成员可以通过查阅这些参考资料来获取更多关于漏洞及其影响的详细信息，以及如何采取适当的措施来保护系统。

What links can be accessed for more information?

可以访问哪些链接以获取更多信息？

### Common Weakness Enumeration (CWE) / 常见弱点枚举（CWE）

Weaknesses in CWE can cover a variety of security issues, such as buffer overflows, cross-site scripting (XSS), SQL injection, and more. By understanding and identifying potential weaknesses in software, developers can take appropriate measures to improve system security, such as strengthening input validation and employing secure coding practices.

CWE 中的弱点可以涵盖各种安全问题，例如缓冲区溢出、跨站脚本（XSS）、SQL 注入等。通过了解和识别软件中可能存在的弱点，开发人员可以采取相应的措施来提高系统的安全性，例如加强输入验证、使用安全编码实践等。

Please provide the Common Weakness Enumeration (CWE) ID or keyword.

请提供常见弱点枚举（CWE）编号或关键词。

### Severity / 严重程度

The potential degree of impact that a vulnerability or security issue has on system security. Severity is typically expressed using levels such as Unknown, Low, Moderate, High, and Critical to indicate the seriousness of the vulnerability.

漏洞或安全问题对系统安全性的潜在影响程度。通常使用未知(Unknown)、低（Low）、中等（Moderate）、高（High）、严重（Critical）等不同的级别来表示漏洞的严重性。

### Reporter Information / 报告人信息

Please provide your contact details.

请提供你的联系方式。
