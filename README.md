# Logify

Smart Android logging assistant for IntelliJ IDEA and Android Studio.

## Features

### Live Templates
Type these shortcuts in any Java or Kotlin file and press Tab:

| Template | Output |
|----------|--------|
| `logd` | `Log.d("ClassName#methodName", "")` |
| `loge` | `Log.e("ClassName#methodName", "")` |
| `logi` | `Log.i("ClassName#methodName", "")` |
| `logw` | `Log.w("ClassName#methodName", "")` |
| `logt` | `val TAG = "ClassName"` |
| `logvar` | `Log.d(TAG, "varName: $varName")` |

Tags are auto-filled with the current class and method name.

### Intention: Normalize Log Tag
Place your cursor on any `Log.d/e/i/w/v(...)` call with a bad tag (e.g. `"TAG"`, `"DEBUG"`, `"test"`) and press **Alt+Enter** → *Normalize log tag* to replace it with the correct `"ClassName#methodName"` tag.

### Action: Normalize All Log Tags in File
Right-click in the editor → **Normalize All Log Tags in File** to fix every `Log` call in the current file at once.

### Inspection: Unguarded Log Call
Any `Log.*` call not inside `if (BuildConfig.DEBUG)` is highlighted with a warning. Press **Alt+Enter** → *Wrap with BuildConfig.DEBUG check* to fix it instantly.

### Action: Wrap All Unguarded Logs in Project
**Tools → Wrap All Unguarded Logs in Project** — scans every `.kt` and `.java` file in the project and wraps all unguarded `Log.*` calls in one shot. Shows a summary when done.

### Code Generators (Right-click → Logify Generators)
- **Insert Kotlin Log Wrapper** — adds `Any.logd/e/i/w` extension functions at the top of the file
- **Insert Java Log Wrapper (L.java)** — creates an `L.java` utility class in the current package

## Installation

Install from the [JetBrains Marketplace](https://plugins.jetbrains.com/) by searching for **Logify**.

Or manually:
1. Download the `.zip` from [Releases](https://github.com/logify/Logify/releases)
2. In your IDE: **Settings → Plugins → ⚙ → Install Plugin from Disk**

## Requirements

- IntelliJ IDEA 2025.1+ or Android Studio Narwhal+
- Java and Kotlin plugins (bundled)

## License

MIT
