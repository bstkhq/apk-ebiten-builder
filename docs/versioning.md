# Android versioning

`VERSION` is the Android `versionName`. `Include.mk` derives the numeric
`VERSION_CODE` automatically, so a consuming project normally sets only
`VERSION`.

## Derivation

The builder extracts the first four integers in `VERSION` and packs them as:

```
VERSION_CODE = major * 1_000_000 + minor * 10_000 + patch * 100 + extra
```

Missing components are `0`. For example:

| `VERSION` | Derived `VERSION_CODE` |
| --- | --- |
| `v2.3.1` | `2030100` |
| `v2.3.1.4` | `2030104` |
| `v0.8.9` | `80900` |

The value is emitted through decimal arithmetic rather than as a zero-padded
literal. Gradle therefore receives `80900` for `v0.8.9`, never an
octal-looking `0080900`.

Set `VERSION` in the consuming project's `Makefile` or on the command line.
The root [configuration table](../README.md#configuration-variables) lists
the default and related build settings.
