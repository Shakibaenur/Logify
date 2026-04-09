# Logify

Smart Android logging assistant for IntelliJ IDEA and Android Studio.

---

## Table of Contents
- [Live Templates](#live-templates)
- [Intention: Normalize Log Tag](#intention-normalize-log-tag)
- [Intention: Log This](#intention-log-this)
- [Inspection: Unguarded Log Call](#inspection-unguarded-log-call)
- [Inspection: Sensitive Data in Log Call](#inspection-sensitive-data-in-log-call)
- [Inspection: Log Call Should Use Timber](#inspection-log-call-should-use-timber)
- [Action: Normalize All Log Tags in File](#action-normalize-all-log-tags-in-file)
- [Action: Wrap All Unguarded Logs in Project](#action-wrap-all-unguarded-logs-in-project)
- [Action: Check Release Readiness](#action-check-release-readiness)
- [Action: Convert All Log Calls to Timber](#action-convert-all-log-calls-to-timber)
- [Action: Remove Debug Log Calls](#action-remove-debug-log-calls)
- [Code Generators](#code-generators)
- [Installation](#installation)
- [Requirements](#requirements)

---

## Live Templates

Type any shortcut inside a function body and press **Tab** to expand.

### Kotlin

| Template | Expands To |
|----------|-----------|
| `logd` | `Log.d("ClassName#methodName", "")` |
| `loge` | `Log.e("ClassName#methodName", "")` |
| `logi` | `Log.i("ClassName#methodName", "")` |
| `logw` | `Log.w("ClassName#methodName", "")` |
| `logt` | `private val TAG = "ClassName"` |
| `logvar` | `Log.d(TAG, "varName=$varName")` |
| `logok` | `Log.d(TAG, "✅ ")` |
| `logerr` | `Log.e(TAG, "❌ error: ")` |
| `logtime` | Timing block with start/end log |
| `logtry` | Try-catch block with error log |
| `logflow` | `Log.d(TAG, "🌊 flow thread=...")` |
| `lognavto` | `Log.d(TAG, "🧭 navigating to: ")` |
| `loglogin` | `Log.d(TAG, "🔐 login user=")` |
| `logquery` | `Log.d(TAG, "🗄️ QUERY: ")` |
| `td` | `Timber.d("")` |
| `te` | `Timber.e("")` |
| `ti` | `Timber.i("")` |
| `tw` | `Timber.w("")` |
| `tvar` | `Timber.d("varName=$varName")` |
| `ttry` | Try-catch block with Timber error log |

### Java

Same abbreviations as Kotlin — all templates include a semicolon and use Java string concatenation where needed.

| Template | Expands To |
|----------|-----------|
| `logd` | `Log.d("ClassName#methodName", "");` |
| `loge` | `Log.e("ClassName#methodName", "");` |
| `logi` | `Log.i("ClassName#methodName", "");` |
| `logw` | `Log.w("ClassName#methodName", "");` |
| `logt` | `private static final String TAG = "ClassName";` |
| `logvar` | `Log.d(TAG, "varName=" + varName);` |
| `td` | `Timber.d("");` |
| `tvar` | `Timber.d("varName=%s", varName);` |

> **Note:** Templates only activate **inside a function/method body**. The tag is auto-filled with `ClassName#methodName` from your current position.

---

## Intention: Normalize Log Tag

Fixes a single bad log tag to the correct `"ClassName#methodName"` format.

**How to use:**
1. Write a log call with a bad tag, e.g. `Log.d("TAG", "msg")`
2. Place your cursor on the log call
3. Press **Alt+Enter**
4. Select **Normalize log tag**

**Before:**
```kotlin
Log.d("TAG", "loading data")
```
**After:**
```kotlin
Log.d("MainActivity#onCreate", "loading data")
```

Works in both Java and Kotlin files.

---

## Intention: Log This

Instantly wraps any variable or expression in a log call.

**How to use:**
1. Place your cursor on any variable or expression
2. Press **Alt+Enter**
3. Select **Log This**

**Before:**
```kotlin
val userName = "John"
```
**After:**
```kotlin
val userName = "John"
Log.d("MainActivity#onCreate", "userName=$userName")
```

---

## Inspection: Unguarded Log Call

Warns on any `Log.*` call not wrapped in `if (BuildConfig.DEBUG)`. These calls will output to logcat in release builds.

**How to use:**
- The warning appears automatically as a yellow underline
- Press **Alt+Enter** → **Wrap with BuildConfig.DEBUG check** to fix instantly

**Before:**
```kotlin
Log.d("TAG", "debug info")
```
**After:**
```kotlin
if (BuildConfig.DEBUG) {
    Log.d("TAG", "debug info")
}
```

Works in both Java and Kotlin files.

---

## Inspection: Sensitive Data in Log Call

Detects log calls that may expose sensitive information — passwords, tokens, secrets, emails, phone numbers — in their arguments.

**How to use:**
- Warning appears automatically as a yellow underline on the log call
- Remove or mask the sensitive argument manually

**Example flagged call:**
```kotlin
Log.d("TAG", "user password=$password")  // ⚠️ flagged
Log.d("TAG", "token=$authToken")          // ⚠️ flagged
```

Works in both Java and Kotlin files.

---

## Inspection: Log Call Should Use Timber

An opt-in inspection that flags any direct `android.util.Log` call and suggests using Timber instead.

**How to enable:**
1. Go to **Settings → Editor → Inspections → Logify**
2. Enable **Log call should use Timber**

**Flagged example:**
```kotlin
Log.d("TAG", "msg")  // ⚠️ suggests Timber.d("msg")
```

Disabled by default so it doesn't interfere with projects not using Timber.

---

## Action: Normalize All Log Tags in File

Fixes every log tag in the current file at once — no need to go call by call.

**How to use:**
1. Open any `.kt` or `.java` file
2. Right-click anywhere in the editor
3. Select **Normalize All Log Tags in File**

**Before:**
```kotlin
Log.d("TAG", "onCreate")
Log.d("DEBUG", "loading")
Log.e("test", "error occurred")
```
**After:**
```kotlin
Log.d("MainActivity#onCreate", "onCreate")
Log.d("MainActivity#loadData", "loading")
Log.e("MainActivity#onError", "error occurred")
```

---

## Action: Wrap All Unguarded Logs in Project

Wraps every unguarded `Log.*` call across the **entire project** with `if (BuildConfig.DEBUG)` in one shot.

**How to use:**
1. Go to **Tools → Wrap All Unguarded Logs in Project**
2. A summary dialog shows how many files and calls were fixed

**Before:**
```kotlin
Log.d("TAG", "debug info")
```
**After:**
```kotlin
if (BuildConfig.DEBUG) {
    Log.d("TAG", "debug info")
}
```

---

## Action: Check Release Readiness

Scans the entire project and produces a release readiness report in one click.

**How to use:**
1. Go to **Tools → Check Release Readiness**
2. A report dialog opens showing:

```
╔══════════════════════════════════════════╗
║        LOGIFY RELEASE READINESS          ║
╚══════════════════════════════════════════╝

Found 5 issue(s):

🔴  Sensitive data leaks : 1
⚠️   Unguarded log calls  : 3
📋  Debug log calls       : 1

📄 MainActivity.kt
   🔴 Line 42: May log sensitive data: password
   ⚠️  Line 55: Log.d() not wrapped in BuildConfig.DEBUG
   📋 Line 60: Log.d() will appear in release

💡 Quick fixes:
   • Remove log calls containing passwords/tokens/secrets
   • Tools → Wrap All Unguarded Logs in Project
   • Tools → Remove Debug Log Calls
```

---

## Action: Convert All Log Calls to Timber

Replaces every `android.util.Log` call across the **entire project** with the equivalent Timber call.

**How to use:**
1. Go to **Tools → Convert All Log Calls to Timber**

**Before:**
```kotlin
Log.d("TAG", "message")
Log.e("TAG", "error", exception)
```
**After:**
```kotlin
Timber.d("message")
Timber.e(exception, "error")
```

> Make sure Timber is added to your `build.gradle` dependencies before using this action.

---

## Action: Remove Debug Log Calls

Deletes all debug-level log calls across the **entire project**. Keeps error and warning logs intact.

**How to use:**
1. Go to **Tools → Remove Debug Log Calls**

**Removed:**
```kotlin
Log.d("TAG", "msg")   // removed
Log.i("TAG", "msg")   // removed
Log.v("TAG", "msg")   // removed
Timber.d("msg")       // removed
Timber.i("msg")       // removed
```

**Kept:**
```kotlin
Log.e("TAG", "error") // kept
Log.w("TAG", "warn")  // kept
```

---

## Code Generators

### Insert Kotlin Log Wrapper

Adds `Any.logd/e/i/w` extension functions at the top of the current Kotlin file. The tag is automatically set to the calling class name at runtime.

**How to use:**
1. Open any `.kt` file
2. Right-click → **Logify Generators → Insert Kotlin Log Wrapper**

**Generated code:**
```kotlin
fun Any.logd(message: String) {
    android.util.Log.d(this::class.simpleName ?: "Unknown", message)
}
fun Any.loge(message: String) { ... }
fun Any.logi(message: String) { ... }
fun Any.logw(message: String) { ... }
```

**How to use the wrapper:**
```kotlin
class MyViewModel : ViewModel() {
    fun loadUser() {
        logd("loading user")   // tag = "MyViewModel"
        loge("load failed")    // tag = "MyViewModel"
    }
}
```

> The action is safe to run multiple times — it will not insert duplicates.

---

### Insert Java Log Wrapper (L.java)

Creates an `L.java` utility class in the same package as the current file.

**How to use:**
1. Open any `.java` file
2. Right-click → **Logify Generators → Insert Java Log Wrapper (L.java)**
3. `L.java` opens automatically

**Generated class:**
```java
public class L {
    public static void d(Object obj, String message) {
        Log.d(obj.getClass().getSimpleName(), message);
    }
    public static void e(Object obj, String message) { ... }
    public static void i(Object obj, String message) { ... }
    public static void w(Object obj, String message) { ... }
}
```

**How to use the wrapper:**
```java
public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        L.d(this, "onCreate");   // tag = "MainActivity"
        L.e(this, "error msg");  // tag = "MainActivity"
    }
}
```

---

## Installation

Install from the [JetBrains Marketplace](https://plugins.jetbrains.com/) by searching for **Logify**.

Or manually:
1. Download the `.zip` from [Releases](https://github.com/Shakibaenur/Logify/releases)
2. In your IDE: **Settings → Plugins → ⚙ → Install Plugin from Disk**
3. Restart the IDE

---

## Requirements

- IntelliJ IDEA 2025.1+ or Android Studio Narwhal+
- Java and Kotlin plugins (bundled)

---

## License

MIT
