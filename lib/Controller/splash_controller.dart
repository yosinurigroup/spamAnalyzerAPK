// lib/controllers/splash_controller.dart
import 'package:get/get.dart';
import 'package:spam_analyzer_v6/main.dart'; // CallScreen

class SplashController extends GetxController {
  @override
  void onInit() {
    super.onInit();
    navigate();
  }

  Future<void> navigate() async {
    // Splash delay
    await Future.delayed(const Duration(seconds: 3));
    
    // Always go directly to CallScreen (no authentication check)
    Get.offAll(() => const CallScreen());
  }
}
