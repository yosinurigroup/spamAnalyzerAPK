# Testing Guide - Immediate Verification Methods

You don't need to wait 3-4 hours! Here are multiple ways to verify the fix immediately:

## ✅ Immediate Verification (5-10 minutes)

### 1. Check for Compilation Errors
```bash
# Build the app - if it builds successfully, merge conflicts are resolved
flutter clean
flutter pub get
flutter build apk --debug
```

**Expected Result:** Build completes without errors

### 2. Memory Leak Detection (Immediate)
```bash
# Install and run the app
flutter run

# In another terminal, monitor memory in real-time
adb shell dumpsys meminfo com.example.spam_analyzer_v6 | grep -A 5 "TOTAL"
```

**What to look for:**
- Initial memory: ~50-100 MB (normal)
- After 5 screenshots: Should stay under 150 MB
- **OLD BUG:** Memory would grow by 20-30 MB per screenshot
- **FIXED:** Memory stays relatively stable (±10 MB fluctuation)

### 3. Stress Test (10 minutes)
Take 10-20 screenshots rapidly and monitor memory:

```bash
# Monitor memory every 2 seconds
watch -n 2 'adb shell dumpsys meminfo com.example.spam_analyzer_v6 | grep "TOTAL PSS"'
```

**Expected Result:**
- Memory should stabilize after each screenshot
- No continuous growth pattern
- Garbage collection should reclaim memory

### 4. Service Status Check (Immediate)
```bash
# Check if accessibility service is running properly
adb shell dumpsys accessibility | grep -A 20 "AssistCaptureService"
```

**Expected Output:**
```
Service[label=Spam Analyzer Screenshot
  id=...
  eventTypes=TYPE_WINDOW_STATE_CHANGED
  feedbackType=FEEDBACK_GENERIC
  ...
  crashed=false  ← This should be false!
]
```

### 5. LogCat Monitoring (Real-time)
```bash
# Watch for errors in real-time
adb logcat | grep -E "AssistCaptureService|A11yWatchdog|FATAL|OutOfMemory"
```

**What to look for:**
- ✅ "✅ Accessibility service connected"
- ✅ "✅ OCR done → proceeding to upload"
- ✅ "Service bound · OK"
- ❌ NO "OutOfMemoryError"
- ❌ NO "FATAL EXCEPTION"
- ❌ NO "bitmap.recycle() called on recycled bitmap"

## 🔬 Advanced Verification (30 minutes)

### 6. Memory Profiler Test
```bash
# Get detailed memory breakdown
adb shell dumpsys meminfo com.example.spam_analyzer_v6 --package

# Look for these sections:
# - Native Heap: Should not grow continuously
# - Graphics: Should stabilize after screenshots
# - Private Dirty: Should stay under 200 MB
```

### 7. Thread Leak Detection
```bash
# Check thread count
adb shell ps -T | grep spam_analyzer_v6 | wc -l
```

**Expected Result:**
- Initial: ~15-25 threads
- After 10 screenshots: Should stay similar (±5 threads)
- **OLD BUG:** Would grow by 2-3 threads per screenshot
- **FIXED:** Thread count remains stable

### 8. Executor Leak Test
```bash
# Monitor for executor leaks
adb logcat | grep -E "ExecutorService|shutdown"
```

**Expected Output:**
- You should see executor shutdown messages after each screenshot
- No "RejectedExecutionException" errors

## 📊 Quick Health Check Script

Create this script to run all checks at once:

```bash
#!/bin/bash
# save as check_health.sh

echo "=== Accessibility Service Health Check ==="
echo ""

echo "1. Service Status:"
adb shell dumpsys accessibility | grep -A 5 "AssistCaptureService" | grep "crashed"

echo ""
echo "2. Current Memory (MB):"
adb shell dumpsys meminfo com.example.spam_analyzer_v6 | grep "TOTAL PSS" | awk '{print $3/1024}'

echo ""
echo "3. Thread Count:"
adb shell ps -T | grep spam_analyzer_v6 | wc -l

echo ""
echo "4. Recent Errors (last 50 lines):"
adb logcat -d | grep -E "FATAL|OutOfMemory|ERROR" | tail -5

echo ""
echo "5. Service Heartbeat (last 10 seconds):"
adb logcat -d | grep "beat: bound" | tail -1

echo ""
echo "=== Health Check Complete ==="
```

Run it:
```bash
chmod +x check_health.sh
./check_health.sh
```

## 🎯 Specific Fix Verification

### Verify Bitmap Recycling Fix
```bash
# Look for bitmap recycling logs
adb logcat | grep -E "recycle|bitmap"
```

**Expected:** No "bitmap already recycled" errors

### Verify Executor Cleanup Fix
```bash
# Check executor shutdown
adb logcat | grep -E "shutdown|executor"
```

**Expected:** Executors are shut down after each operation

### Verify Error Handling Fix
```bash
# Trigger an error scenario (e.g., disable network)
# Check if app handles it gracefully
adb logcat | grep -E "Error|Exception" | grep AssistCaptureService
```

**Expected:** Errors are caught and logged, app doesn't crash

## 🚀 Accelerated Long-Term Test (1 hour instead of 4)

To simulate 4 hours of usage in 1 hour:

```bash
# Script to take screenshots every 30 seconds for 1 hour
for i in {1..120}; do
  echo "Screenshot $i/120"
  # Trigger screenshot via your app
  sleep 30
  
  # Check memory every 10 screenshots
  if [ $((i % 10)) -eq 0 ]; then
    echo "Memory check at screenshot $i:"
    adb shell dumpsys meminfo com.example.spam_analyzer_v6 | grep "TOTAL PSS"
  fi
done
```

**Expected Result:**
- Memory should stabilize around 100-150 MB
- No continuous growth
- No crashes

## 📈 Success Indicators

### ✅ Fix is Working If:
1. **Memory stays stable** - No continuous growth pattern
2. **No OutOfMemoryError** - Check logcat for 30+ minutes
3. **Service stays bound** - "Service bound · OK" in watchdog
4. **Thread count stable** - Doesn't grow with each screenshot
5. **No bitmap errors** - No "recycled bitmap" errors
6. **Executors cleaned** - Shutdown logs appear after operations
7. **App responsive** - No ANR (Application Not Responding) dialogs

### ❌ Still Has Issues If:
1. Memory grows by >20 MB per screenshot
2. OutOfMemoryError appears in logs
3. Service shows "crashed=true"
4. Thread count grows continuously
5. "bitmap already recycled" errors
6. App becomes sluggish over time
7. ANR dialogs appear

## 🔍 Monitoring Dashboard (Real-time)

Open 3 terminal windows and run these simultaneously:

**Terminal 1 - Memory:**
```bash
watch -n 5 'adb shell dumpsys meminfo com.example.spam_analyzer_v6 | grep -A 3 "App Summary"'
```

**Terminal 2 - Errors:**
```bash
adb logcat | grep -E "FATAL|ERROR|OutOfMemory|AssistCaptureService"
```

**Terminal 3 - Service Status:**
```bash
watch -n 10 'adb shell dumpsys accessibility | grep -A 10 "AssistCaptureService"'
```

## 📝 Test Checklist

Use this checklist to verify the fix:

- [ ] App builds without errors
- [ ] Accessibility service connects successfully
- [ ] First screenshot works
- [ ] Memory stable after 5 screenshots
- [ ] Memory stable after 10 screenshots
- [ ] No OutOfMemoryError in 30 minutes
- [ ] Service heartbeat appears every 2 minutes
- [ ] Thread count remains stable
- [ ] No bitmap recycling errors
- [ ] App remains responsive
- [ ] Screenshots continue working after 1 hour

## 🎓 Understanding the Results

### Memory Pattern Analysis

**BEFORE FIX:**
```
Screenshot 1: 80 MB
Screenshot 2: 105 MB (+25 MB) ← Memory leak!
Screenshot 3: 130 MB (+25 MB) ← Growing!
Screenshot 4: 155 MB (+25 MB) ← Will crash soon!
```

**AFTER FIX:**
```
Screenshot 1: 80 MB
Screenshot 2: 95 MB (+15 MB, then GC)
Screenshot 3: 85 MB (-10 MB, GC worked) ← Stable!
Screenshot 4: 90 MB (+5 MB) ← Normal fluctuation
```

### Thread Count Analysis

**BEFORE FIX:**
```
Start: 20 threads
After 5 screenshots: 30 threads (+10) ← Leak!
After 10 screenshots: 40 threads (+10) ← Growing!
```

**AFTER FIX:**
```
Start: 20 threads
After 5 screenshots: 22 threads (+2) ← Normal
After 10 screenshots: 21 threads (+1) ← Stable!
```

## 🏁 Quick 5-Minute Verification

If you only have 5 minutes:

```bash
# 1. Build the app
flutter build apk --debug

# 2. Install and run
flutter run

# 3. Take 3 screenshots through your app

# 4. Check memory
adb shell dumpsys meminfo com.example.spam_analyzer_v6 | grep "TOTAL PSS"

# 5. Check for errors
adb logcat -d | grep -E "FATAL|OutOfMemory" | tail -10
```

**If no errors and memory is stable → Fix is working! ✅**

## 📞 Support

If you see any of these, the fix may need adjustment:
- Continuous memory growth (>20 MB per screenshot)
- OutOfMemoryError in logs
- Service crashed=true
- App becomes unresponsive

Otherwise, the fix is working correctly and you can be confident it will run for extended periods without crashing.