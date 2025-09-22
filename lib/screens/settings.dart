import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:get_storage/get_storage.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({Key? key}) : super(key: key);

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final TextEditingController callToController = TextEditingController();
  final box = GetStorage();

  final List<String> carriers = const ['AT&T', 'T-Mobile', 'Verizon'];
  String? selectedCarrier;

  @override
  void initState() {
    super.initState();

    callToController.text = box.read('callTo') ?? '';

    final savedCarrier = box.read('carrier');
    if (savedCarrier != null && carriers.contains(savedCarrier)) {
      selectedCarrier = savedCarrier;
    } else {
      selectedCarrier = carriers.first;
    }
  }

  @override
  void dispose() {
    callToController.dispose();
    super.dispose();
  }

  Future<void> saveSettings() async {
    final callTo = callToController.text.trim().isEmpty
        ? 'Personal'
        : callToController.text.trim();
    final carrier = (selectedCarrier ?? carriers.first).trim();

    box.write('callTo', callTo);
    box.write('carrier', carrier);

    const platform = MethodChannel('com.example.call_detector/channel');
    try {
      await platform.invokeMethod('setLocalCallInfo', {
        'callTo': callTo,
        'carrier': carrier,
      });

      print('✅ Settings saved - callTo: $callTo, carrier: $carrier');

      Get.snackbar(
        '✅ Success',
        'Settings saved successfully',
        snackPosition: SnackPosition.BOTTOM,
        backgroundColor: Colors.green.shade500,
        colorText: Colors.white,
        margin: const EdgeInsets.all(12),
        borderRadius: 12,
      );
    } catch (e) {
      print('❌ Failed to save settings: $e');
      Get.snackbar(
        '⚠️ Error',
        'Failed to send settings to native: $e',
        snackPosition: SnackPosition.BOTTOM,
        backgroundColor: Colors.red.shade500,
        colorText: Colors.white,
        margin: const EdgeInsets.all(12),
        borderRadius: 12,
      );
    }
  }

  InputDecoration _inputDecoration(String label, IconData icon) {
    return InputDecoration(
      prefixIcon: Icon(icon, color: Colors.indigo),
      filled: true,
      hintText: label,
      hintStyle: const TextStyle(color: Colors.grey, fontSize: 12),
      fillColor: Colors.white.withOpacity(0.9),
      contentPadding: const EdgeInsets.symmetric(vertical: 18, horizontal: 20),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(16),
        borderSide: BorderSide(color: Colors.grey.shade300, width: 1),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(16),
        borderSide: const BorderSide(color: Colors.indigo, width: 2),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF6F7FB),
      appBar: AppBar(
        elevation: 0,
        backgroundColor: Colors.transparent,
        title: const Text(
          '⚙️ Settings',
          style: TextStyle(color: Colors.black87, fontWeight: FontWeight.bold),
        ),
        centerTitle: true,
      ),
      body: Padding(
        padding: const EdgeInsets.all(22.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            TextField(
              controller: callToController,
              keyboardType: TextInputType.phone,
              style: const TextStyle(color: Colors.black),
              decoration: _inputDecoration(
                'Call to +1(818)443-6854',
                Icons.phone_outlined,
              ),
            ),
            const SizedBox(height: 20),

            Container(
              decoration: BoxDecoration(
                color: Colors.white.withOpacity(0.9),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: Colors.grey.shade300, width: 1),
              ),
              child: DropdownButtonHideUnderline(
                child: DropdownButtonFormField<String>(
  key: const ValueKey('carrierDropdown'), // sanity key
  value: selectedCarrier ?? carriers.first,
  isExpanded: true,
  menuMaxHeight: 320,
  decoration: InputDecoration(
    prefixIcon: const Icon(Icons.network_cell_outlined, color: Colors.indigo),
    hintText: 'Select Carrier',
    hintStyle: const TextStyle(color: Colors.grey, fontSize: 12),
    filled: true,
    fillColor: Colors.white.withOpacity(0.9),
    contentPadding: const EdgeInsets.symmetric(vertical: 18, horizontal: 20),
    enabledBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(16),
      borderSide: BorderSide(color: Colors.grey.shade300, width: 1),
    ),
    focusedBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(16),
      borderSide: const BorderSide(color: Colors.indigo, width: 2),
    ),
  ),
  items: carriers
      .map((c) => DropdownMenuItem<String>(
            value: c,
            child: Text(c, style: const TextStyle(color: Colors.black)),
          ))
      .toList(),
  onChanged: (val) => setState(() => selectedCarrier = val ?? carriers.first),
  dropdownColor: Colors.white,
  icon: const Icon(Icons.arrow_drop_down, color: Colors.indigo),
)

              ),
            ),

            const Spacer(),
            SizedBox(
              height: 56,
              child: ElevatedButton(
                onPressed: saveSettings,
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xff009688),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                  elevation: 6,
                ),
                child: const Text(
                  "Save Settings",
                  style: TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                    color: Colors.white,
                  ),
                ),
              ),
            ),
            const SizedBox(height: 30),
          ],
        ),
      ),
    );
  }
}
