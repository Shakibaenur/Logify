# Logify

Smart Android logging assistant for IntelliJ IDEA and Android Studio.

---

## Features

### Live Templates
Type these shortcuts in any Java or Kotlin file and press **Tab**:

| Template | Output |
|----------|--------|
| `logd` | `Log.d("ClassName#methodName", "")` |
| `loge` | `Log.e("ClassName#methodName", "")` |
| `logi` | `Log.i("ClassName#methodName", "")` |
| `logw` | `Log.w("ClassName#methodName", "")` |
| `logt` | `val TAG = "ClassName"` |
| `logvar` | `Log.d(TAG, "varName: $varName")` |

Tags are auto-filled with the current class and method name.

---

### Intention: Normalize Log Tag
Place your cursor on any `Log.d/e/i/w/v(...)` call with a bad tag (e.g. `"TAG"`, `"DEBUG"`, `"test"`) and press **Alt+Enter** → *Normalize log tag* to replace it with the correct `"ClassName#methodName"` tag.

---

### Intention: Log This
Place your cursor on any variable or expression, press **Alt+Enter** → *Log This* to instantly wrap it in a log call with the correct tag and value.

---

### Inspection: Unguarded Log Call
Any `Log.*` call not inside `if (BuildConfig.DEBUG)` is highlighted with a warning.
Press **Alt+Enter** → *Wrap with BuildConfig.DEBUG check* to fix it instantly.
Works in both Java and Kotlin files.

---

### Inspection: Sensitive Data in Log Call
Detects log calls that may expose sensitive information such as passwords, tokens, or secrets in their arguments.
Highlighted as a warning in both Java and Kotlin files.

---

### Inspection: Log Call Should Use Timber
An opt-in warning that flags any direct `android.util.Log` call and suggests migrating to Timber.
Disabled by default — enable it in **Settings → Editor → Inspections → Logify**.

---

### Action: Normalize All Log Tags in File
Right-click in the editor → **Normalize All Log Tags in File** — fixes every `Log` call tag in the current file at once.

---

### Action: Wrap All Unguarded Logs in Project
**Tools → Wrap All Unguarded Logs in Project** — scans every `.kt` and `.java` file in the project and wraps all unguarded `Log.*` calls with `if (BuildConfig.DEBUG)` in one shot.

---

### Action: Check Release Readiness
**Tools → Check Release Readiness** — scans the entire project and produces a report covering:
- Debug log calls (`d`, `i`, `v`) that will appear in release builds
- Log calls not wrapped in `BuildConfig.DEBUG`
- Log calls that may expose sensitive data

---

### Action: Convert All Log Calls to Timber
**Tools → Convert All Log Calls to Timber** — replaces every `Log.d/e/i/w/v` call across the whole project with the equivalent `Timber.d/e/i/w/v` call.

---

### Action: Remove Debug Log Calls
**Tools → Remove Debug Log Calls** — deletes all `Log.d/i/v` and `Timber.d/i/v` calls project-wide. Keeps `Log.e` and `Log.w` intact.

---

### Code Generators
Right-click in the editor → **Logify Generators**:
- **Insert Kotlin Log Wrapper** — adds `Any.logd/e/i/w` extension functions at the top of the file
- **Insert Java Log Wrapper (L.java)** — creates an `L.java` utility class in the current package

---

## Installation

Install from the [JetBrains Marketplace](https://plugins.jetbrains.com/) by searching for **Logify**.

Or manually:
1. Download the `.zip` from [Releases](https://github.com/Shakibaenur/Logify/releases)
2. In your IDE: **Settings → Plugins → ⚙ → Install Plugin from Disk**

---

## Requirements

- IntelliJ IDEA 2025.1+ or Android Studio Narwhal+
- Java and Kotlin plugins (bundled)

---

## License

MIT
