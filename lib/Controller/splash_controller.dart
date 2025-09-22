// lib/controllers/splash_controller.dart
import 'package:get/get.dart';
import 'package:get_storage/get_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:spam_analyzer_v6/main.dart';
import 'package:spam_analyzer_v6/screens/login_screen.dart';

class SplashController extends GetxController {
  static const _tokenKey = 'auth_token';
  final box = GetStorage();

  @override
  void onInit() {
    super.onInit();
    navigate();
  }

  Future<void> navigate() async {
    await Future.delayed(const Duration(seconds: 3));

    final prefs = await SharedPreferences.getInstance();
    final token = (prefs.getString(_tokenKey) ?? '').trim();

    if (token.isNotEmpty) {
      print("mytokensaved  $token");
      Get.offAll(() => const CallScreen());
    } else {
      Get.offAll(() => const LoginScreen());
    }
  }
}
