import 'package:spam_analyzer_v6/screens/screenshots_screen.dart';
import 'package:spam_analyzer_v6/screens/settings.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:lottie/lottie.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:phone_state/phone_state.dart';
import 'package:contacts_service_plus/contacts_service_plus.dart';
import 'package:android_intent_plus/android_intent.dart';
import 'package:android_intent_plus/flag.dart';
import 'package:get/get.dart';
import 'package:get_storage/get_storage.dart';
import 'package:spam_analyzer_v6/screens/splah_view.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await GetStorage.init();
  runApp(GetMaterialApp(home: SplashView(), debugShowCheckedModeBanner: false));
}

class CallScreen extends StatefulWidget {
  const CallScreen({super.key});
  @override
  State<CallScreen> createState() => _CallScreenState();
}

class _CallScreenState extends State<CallScreen> with WidgetsBindingObserver {
  String? currentNumber;
  String? callerName;
  PhoneStateStatus? callStatus;
  bool granted = false;

  // ✅ Native bridge
  static const _ch = MethodChannel('com.example.call_detector/channel');

  final box = GetStorage();

  bool _capInFlight = false;
  DateTime? _lastCapAt;
  PhoneStateStatus? _lastStatus;

  // ✅ Companion (keep-alive) status
  bool? _companionOk;
  bool _checkingCompanion = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _oneTimeSetup();
    PhoneState.stream.listen(handleCall);
    getCallStateFromNative();
    _loadAndSendStoredCallInfo();
    _refreshCompanionStatus();
  }

  Future<void> _oneTimeSetup() async {
    await requestPermissions();
    await _ensureOverlayPermission();
    final wasAsked = box.read('accessibility_prompted') == true;
    var enabled = await _isAccessibilityEnabledFromNative();

    if (!enabled && !wasAsked) {
      await _openAccessibilitySettingsFromNative();
      await box.write('accessibility_prompted', true);
    }
  }

  // -------------------- Accessibility helpers --------------------

  Future<bool> _isAccessibilityEnabledFromNative() async {
    try {
      final bool ok = await _ch.invokeMethod('isAccessibilityEnabled');
      return ok;
    } catch (e) {
      debugPrint("❌ isAccessibilityEnabled missing: $e");
      return false;
    }
  }

  Future<void> _openAccessibilitySettingsFromNative() async {
    try {
      await _ch.invokeMethod('openAccessibilitySettings');
    } catch (e) {
      debugPrint("❌ openAccessibilitySettings error: $e");
    }
  }

  Future<void> _triggerAccessibilityCaptureFromNative() async {
    try {
      await _ch.invokeMethod('triggerAccessibilityCapture');
      debugPrint("✅ Accessibility capture broadcast sent");
    } catch (e) {
      debugPrint("❌ triggerAccessibilityCapture error: $e");
    }
  }

  Future<void> _maybeTriggerCapture() async {
    if (_capInFlight) return;
    final now = DateTime.now();
    if (_lastCapAt != null && now.difference(_lastCapAt!).inMilliseconds < 3000) return;

    final enabled = await _isAccessibilityEnabledFromNative();
    if (!enabled) {
      debugPrint('⚠️ Accessibility service is OFF — opening settings…');
      await _openAccessibilitySettingsFromNative();
      return;
    }

    await Future.delayed(const Duration(milliseconds: 800));
    _capInFlight = true;
    try {
      await _triggerAccessibilityCaptureFromNative();
      _lastCapAt = DateTime.now();
    } finally {
      _capInFlight = false;
    }
  }

  // -------------------- Companion (Keep-Alive) helpers --------------------

  Future<void> _refreshCompanionStatus() async {
    setState(() => _checkingCompanion = true);
    try {
      final ok = await _ch.invokeMethod('companionStatus');
      if (mounted) setState(() => _companionOk = (ok == true));
    } catch (e) {
      debugPrint('❌ companionStatus error: $e');
      if (mounted) setState(() => _companionOk = null);
    } finally {
      if (mounted) setState(() => _checkingCompanion = false);
    }
  }

  Future<void> _ensureCompanionAssociation() async {
    try {
      await _ch.invokeMethod('ensureCompanionAssociation');
      // chooser se wapas aane par resume trigger hoga → status refresh wahan bhi
    } catch (e) {
      debugPrint('❌ ensureCompanionAssociation error: $e');
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Companion association failed: $e')),
      );
    }
  }

  // -------------------- Lifecycle --------------------

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      getCallStateFromNative();
      requestPermissions();
      _refreshCompanionStatus(); // ✅ chooser close ke baad yahan update
      setState(() {});
    }
  }

  // -------------------- Permissions / overlay --------------------

  Future<void> _ensureOverlayPermission() async {
    try {
      await _ch.invokeMethod('ensureOverlayPermission');
    } catch (e) {
      debugPrint("❌ ensureOverlayPermission error: $e");
    }
  }

  Future<void> _sendLocalCallInfo({
    required String callTo,
    required String carrier,
  }) async {
    try {
      await _ch.invokeMethod('setLocalCallInfo', {
        'callTo': callTo,
        'carrier': carrier,
      });
      debugPrint("✅ Sent callTo: $callTo, carrier: $carrier to native");
    } catch (e) {
      debugPrint("❌ setLocalCallInfo error: $e");
    }
  }

  Future<void> _loadAndSendStoredCallInfo() async {
    final callTo = box.read('callTo') ?? 'Personal';
    final carrier = box.read('carrier') ?? 'defaultCarrier';
    debugPrint("📱 Loading stored values - callTo: $callTo, carrier: $carrier");
    await _sendLocalCallInfo(callTo: callTo, carrier: carrier);
  }

  Future<void> requestPermissions() async {
    final phoneStatus = await Permission.phone.request();
    final contactsStatus = await Permission.contacts.request();
    if (await Permission.bluetoothScan.isDenied) {
      await Permission.bluetoothScan.request();
    }
    if (await Permission.bluetoothConnect.isDenied) {
      await Permission.bluetoothConnect.request();
    }
    // if (await Permission.locationWhenInUse.isDenied) {
    //   await Permission.locationWhenInUse.request();
    // }

    setState(() {
      granted = phoneStatus.isGranted && contactsStatus.isGranted;
    });
  }

  Future<void> openOverlaySettings() async {
    const packageName = 'com.example.spam_analyzer_v6';
    final intent = AndroidIntent(
      action: 'android.settings.action.MANAGE_OVERLAY_PERMISSION',
      data: 'package:$packageName',
      flags: <int>[Flag.FLAG_ACTIVITY_NEW_TASK],
    );
    await intent.launch();
  }


  Future<void> getCallStateFromNative() async {
    try {
      final result = await _ch.invokeMethod('getCallState');
      final mapped = _mapNativeState(result);
      final String? incomingNumber = await _ch.invokeMethod('getIncomingNumber');

      if ((incomingNumber != null && incomingNumber.isNotEmpty) ||
          mapped != PhoneStateStatus.CALL_ENDED) {
        String? matchedName;
        if (incomingNumber != null && incomingNumber.isNotEmpty) {
          matchedName = await _findContactName(incomingNumber);
        }
        setState(() {
          callStatus = mapped;
          currentNumber = incomingNumber;
          callerName = matchedName ?? "Unknown";
        });
      } else {
        setState(() {
          callStatus = null;
          currentNumber = null;
          callerName = null;
        });
      }
    } catch (e) {
      debugPrint("Error fetching native call state: $e");
    }
  }

  PhoneStateStatus _mapNativeState(String? state) {
    switch (state) {
      case "RINGING":
        return PhoneStateStatus.CALL_INCOMING;
      case "OFFHOOK":
        return PhoneStateStatus.CALL_STARTED;
      case "IDLE":
      default:
        return PhoneStateStatus.CALL_ENDED;
    }
  }

  Future<String?> _findContactName(String incomingNumber) async {
    try {
      final contacts = await ContactsService.getContacts(withThumbnails: false);
      String normalizedIncoming = normalizeNumber(incomingNumber);
      for (final contact in contacts) {
        for (final phone in contact.phones ?? []) {
          final saved = phone.value ?? '';
          String normalizedSaved = normalizeNumber(saved);
          if (normalizedIncoming == normalizedSaved ||
              normalizedSaved.contains(normalizedIncoming) ||
              normalizedIncoming.contains(normalizedSaved)) {
            return contact.displayName;
          }
        }
      }
    } catch (e) {
      debugPrint("Error matching contact name: $e");
    }
    return null;
  }

  String normalizeNumber(String number) {
    return number
        .replaceAll(RegExp(r'[^\d+]'), '')
        .replaceFirst(RegExp(r'^(\+92|0092|92|0)'), '');
  }

  void handleCall(PhoneState state) async {
    if (!granted || !mounted) return;
    final st = state.status;

    if (_lastStatus != PhoneStateStatus.CALL_INCOMING &&
        st == PhoneStateStatus.CALL_INCOMING) {
      await _maybeTriggerCapture();
    }
    _lastStatus = st;

    if (st == PhoneStateStatus.CALL_INCOMING) {
      setState(() => callStatus = st);
    }

    if (st == PhoneStateStatus.CALL_ENDED) {
      setState(() {
        callStatus = st;
        currentNumber = null;
        callerName = null;
      });
    }
  }


  @override
  Widget build(BuildContext context) {
    return FutureBuilder<bool>(
      future: Permission.systemAlertWindow.isGranted,
      builder: (context, snapshot) {
        final overlayGranted = snapshot.data ?? false;

        return Scaffold(
          backgroundColor: Colors.black,
          appBar: AppBar(
            actions: [
              // _companionBadge(),
              const SizedBox(width: 8),
              const Padding(
                padding: EdgeInsets.only(right: 8),
                child: Text("v0.23"),
              ),
            ],
            leading: IconButton(
              icon: const Icon(Icons.settings, color: Colors.white),
              onPressed: () => Navigator.push(
                context,
                MaterialPageRoute(builder: (context) => const SettingsScreen()),
              ),
            ),
            title: const Text(
              'Call Detector',
              style: TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.bold,
              ),
            ),
            backgroundColor: Colors.black,
            elevation: 0,
            centerTitle: true,
            iconTheme: const IconThemeData(color: Colors.white),
          ),
          body: Center(
            child: !granted || !overlayGranted
                ? _PermissionsPanel(
                    onGrantPhoneContacts: requestPermissions,
                    onOpenOverlay: openOverlaySettings,
                    onOpenAccessibility: _openAccessibilitySettingsFromNative,
                  )
                : SingleChildScrollView(
                    padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        // avatar / lottie
                        Container(
                          width: 120,
                          height: 120,
                          decoration: BoxDecoration(
                            color: Colors.grey[300],
                            shape: BoxShape.circle,
                          ),
                          child: Lottie.asset(
                            callStatus == PhoneStateStatus.CALL_INCOMING
                                ? 'assets/call.json'
                                : 'assets/user icon.json',
                          ),
                        ),
                        const SizedBox(height: 10),
                        if (callerName != null)
                          Text(
                            callerName!,
                            style: const TextStyle(
                              fontSize: 16,
                              color: Colors.white70,
                            ),
                          ),
                        const SizedBox(height: 14),

                        // history button
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 23, vertical: 4),
                          decoration: BoxDecoration(
                            color: const Color(0xff009688),
                            borderRadius: BorderRadius.circular(20),
                          ),
                          child: TextButton(
                            onPressed: () => Navigator.push(
                              context,
                              MaterialPageRoute(builder: (context) => ScreenshotResultView()),
                            ),
                            child: const Text(
                              'View Call History',
                              style: TextStyle(color: Colors.white, fontSize: 12),
                            ),
                          ),
                        ),

                        const SizedBox(height: 16),

                       
                       
                        const SizedBox(height: 12),
                        FutureBuilder<bool>(
                          future: _isAccessibilityEnabledFromNative(),
                          builder: (context, snapshot) {
                            final enabled = snapshot.data ?? false;
                            if (!enabled) {
                              return ElevatedButton(
                                onPressed: _openAccessibilitySettingsFromNative,
                                child: const Text('Enable Accessibility Service'),
                              );
                            }
                            return const SizedBox.shrink();
                          },
                        ),
                      ],
                    ),
                  ),
          ),
        );
      },
    );
  }

  // Widget _companionBadge() {
  //   if (_checkingCompanion) {
  //     return const Padding(
  //       padding: EdgeInsets.only(right: 10),
  //       child: SizedBox(
  //         height: 20,
  //         width: 20,
  //         child: CircularProgressIndicator(strokeWidth: 2),
  //       ),
  //     );
  //   }
  //   if (_companionOk == true) {
  //     return Padding(
  //       padding: const EdgeInsets.only(right: 8),
  //       child: _chip('Keep-Alive ON', Colors.green, const TextStyle(color: Colors.white, fontSize: 12)),
  //     );
  //   }
  //   return Padding(
  //     padding: const EdgeInsets.only(right: 8),
  //     child: _chip('Keep-Alive OFF', Colors.orange, const TextStyle(color: Colors.white, fontSize: 12)),
  //   );
  // }

  String _formatPhoneNumber(String number) {
    if (number.length == 10) {
      return '${number.substring(0, 3)} ${number.substring(3, 6)}-${number.substring(6)}';
    }
    return number;
  }

  Color _getStatusColor(PhoneStateStatus status) {
    switch (status) {
      case PhoneStateStatus.CALL_INCOMING:
        return Colors.blue;
      case PhoneStateStatus.CALL_STARTED:
        return Colors.green;
      case PhoneStateStatus.CALL_ENDED:
        return Colors.red;
      default:
        return Colors.grey;
    }
  }

  // String _getStatusText(PhoneStateStatus status) {
  //   switch (status) {
  //     case PhoneStateStatus.CALL_INCOMING:
  //       return "INCOMING CALL";
  //     case PhoneStateStatus.CALL_STARTED:
  //       return "CALL IN PROGRESS";
  //     case PhoneStateStatus.CALL_ENDED":
  //       return "CALL ENDED";
  //     default:
  //       return "IDLE";
  //   }
  // }
}

Widget _chip(String text, Color color, TextStyle style) {
  return Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
    decoration: BoxDecoration(
      color: color.withOpacity(0.18),
      borderRadius: BorderRadius.circular(20),
      border: Border.all(color: color.withOpacity(0.5)),
    ),
    child: Text(text, style: style),
  );
}

class _PermissionsPanel extends StatelessWidget {
  final VoidCallback onGrantPhoneContacts;
  final VoidCallback onOpenOverlay;
  final VoidCallback onOpenAccessibility;

  const _PermissionsPanel({
    required this.onGrantPhoneContacts,
    required this.onOpenOverlay,
    required this.onOpenAccessibility,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        const Icon(Icons.phone, size: 80, color: Colors.grey),
        const SizedBox(height: 20),
        const Text(
          "Please allow all required permissions",
          style: TextStyle(fontSize: 18, color: Colors.white),
        ),
        const SizedBox(height: 20),
        ElevatedButton(
          onPressed: onGrantPhoneContacts,
          style: ElevatedButton.styleFrom(
            backgroundColor: Colors.black,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
            padding: const EdgeInsets.symmetric(horizontal: 30, vertical: 15),
          ),
          child: const Text("Grant Phone & Contact Permissions", style: TextStyle(color: Colors.white)),
        ),
        const SizedBox(height: 10),
        ElevatedButton(
          onPressed: onOpenOverlay,
          style: ElevatedButton.styleFrom(
            backgroundColor: Colors.deepPurple,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
            padding: const EdgeInsets.symmetric(horizontal: 30, vertical: 15),
          ),
          child: const Text("Enable Display Over Apps", style: TextStyle(color: Colors.white)),
        ),
        const SizedBox(height: 10),
        ElevatedButton(
          onPressed: onOpenAccessibility,
          style: ElevatedButton.styleFrom(
            backgroundColor: Colors.blueGrey,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
            padding: const EdgeInsets.symmetric(horizontal: 30, vertical: 15),
          ),
          child: const Text("Open Accessibility Settings", style: TextStyle(color: Colors.white)),
        ),
      ],
    );
  }
}
    