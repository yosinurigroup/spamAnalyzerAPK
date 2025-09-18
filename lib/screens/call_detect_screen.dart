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

  static const platform = MethodChannel('com.example.call_detector/channel');

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    startForegroundService();
    requestPermissions();
    requestScreenshotPermissionFromNative(); 
    PhoneState.stream.listen(handleCall);
    getCallStateFromNative();
  }

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
      setState(() {});
    }
  }

  Future<void> requestScreenshotPermissionFromNative() async {
    try {
      print("Requesting screenshot permission...");
    final result = await platform.invokeMethod('reuseOrRequestScreenshotPermission');
    print("✅ Screenshot permission321: $result");
    debugPrint("✅ Screenshot permission: $result");
  } catch (e) {
    debugPrint("❌ Failed to get screenshot permission: $e");
  }
  }

  Future<void> startForegroundService() async {
    try {
      await platform.invokeMethod('startForegroundService');
    } catch (e) {
      print("Error starting foreground service: $e");
    }
  }

  Future<void> requestPermissions() async {
    final phoneStatus = await Permission.phone.request();
    final contactsStatus = await Permission.contacts.request();

    setState(() {
      granted = phoneStatus.isGranted && contactsStatus.isGranted;
    });
  }

  Future<void> openOverlaySettings() async {
    const packageName = 'com.example.call_detector';
    final intent = AndroidIntent(
      action: 'android.settings.action.MANAGE_OVERLAY_PERMISSION',
      data: 'package:$packageName',
      flags: <int>[Flag.FLAG_ACTIVITY_NEW_TASK],
    );
    await intent.launch();
  }

  Future<void> getCallStateFromNative() async {
    try {
      final result = await platform.invokeMethod('getCallState');
      final mapped = _mapNativeState(result);
      String? incomingNumber = await platform.invokeMethod('getIncomingNumber');

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
    return number.replaceAll(RegExp(r'[^\d+]'), '').replaceFirst(RegExp(r'^(\+92|0092|92|0)'), '');
  }

  void handleCall(PhoneState state) async {
    if (!granted || !mounted) return;

    if (state.status == PhoneStateStatus.CALL_INCOMING) {
      setState(() {
        callStatus = state.status;
      });
    }

    if (state.status == PhoneStateStatus.CALL_ENDED) {
      setState(() {
        callStatus = state.status;
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
            title: const Text('Call Detector',
                style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
            actions: [
              IconButton(
                icon:  Icon(Icons.settings, color: Colors.white),
                onPressed: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(builder: (context) =>  SettingsScreen()),
                  );
                },
              ),
              IconButton(
                icon: const Icon(Icons.image, color: Colors.white),
                onPressed: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(builder: (context) =>  ScreenshotResultView()),
                  );
                },
              ),
            ],
            backgroundColor: Colors.black,
            elevation: 0,
            centerTitle: true,
            iconTheme: const IconThemeData(color: Colors.white),
          ),
          body: Center(
            child: !granted || !overlayGranted
                ? Column(
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
                        onPressed: requestPermissions,
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.black,
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                          padding: const EdgeInsets.symmetric(horizontal: 30, vertical: 15),
                        ),
                        child: const Text("Grant Phone & Contact Permissions",
                            style: TextStyle(color: Colors.white)),
                      ),
                      const SizedBox(height: 10),
                      ElevatedButton(
                        onPressed: openOverlaySettings,
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.deepPurple,
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
                          padding: const EdgeInsets.symmetric(horizontal: 30, vertical: 15),
                        ),
                        child: const Text("Enable Display Over Apps",
                            style: TextStyle(color: Colors.white)),
                      ),
                    ],
                  )
                : Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Container(
                        width: 120,
                        height: 120,
                        decoration:
                            BoxDecoration(color: Colors.grey[300], shape: BoxShape.circle),
                        child: Lottie.asset(
                          callStatus == PhoneStateStatus.CALL_INCOMING
                              ? 'assets/call.json'
                              : 'assets/user icon.json',
                        ),
                      ),
                      const SizedBox(height: 10),
                      Text(
                        currentNumber != null && currentNumber!.isNotEmpty
                            ? _formatPhoneNumber(currentNumber!)
                            : "No Active Call",
                        style: const TextStyle(fontSize: 24, color: Colors.white),
                      ),
                      const SizedBox(height: 10),
                      if (callStatus != null)
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                          decoration: BoxDecoration(
                            color: _getStatusColor(callStatus!),
                            borderRadius: BorderRadius.circular(20),
                          ),
                          child: Text(
                            _getStatusText(callStatus!),
                            style: const TextStyle(fontSize: 12, color: Colors.white),
                          ),
                        ),
                    ],
                  ),
          ),
        );
      },
    );
  }

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

  String _getStatusText(PhoneStateStatus status) {
    switch (status) {
      case PhoneStateStatus.CALL_INCOMING:
        return "INCOMING CALL";
      case PhoneStateStatus.CALL_STARTED:
        return "CALL IN PROGRESS";
      case PhoneStateStatus.CALL_ENDED:
        return "CALL ENDED";
      default:
        return "IDLE";
    }
  }
}
