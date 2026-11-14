# Security policy

## Supported versions

| Version | Supported |
| ------- | --------- |
| 0.1.x   | yes       |
| older   | no        |

Lumi pins one GeckoView version in `gradle/libs.versions.toml` and moves to each stable GeckoView
release, which carries Mozilla's security fixes. A flaw in Gecko itself is fixed upstream; report it
to Mozilla, and Lumi takes the fix with the next GeckoView update.

## Reporting a vulnerability

Do not open a public issue. Report privately through GitHub: open the repository's
**Security** tab and choose **Report a vulnerability**.

Say what you found, the version (Settings → About Lumi), and how to reproduce it. This is a
one-person project: expect an acknowledgement within about a week, and a fix in the next release
when the report holds; you will be told when it is released.
