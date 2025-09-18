// AUTHENTICATION DISABLED - This controller is no longer used
// All authentication has been removed from the app

import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:get_storage/get_storage.dart';

class AuthController extends GetxController {
  final nameCtrl = TextEditingController();
  final emailCtrl = TextEditingController();
  final passwordCtrl = TextEditingController();
  final phoneCtrl = TextEditingController();

  final registering = false.obs;
  final loggingIn   = false.obs;

  final box = GetStorage();

  // ─────────────── Carrier selection (STATIC) ───────────────
  final RxList<String> carriers = <String>['T Mobile', 'Verizon', 'AT&T'].obs;
  final RxnString selectedCarrier = RxnString();
  void setCarrier(String? v) => selectedCarrier.value = (v == null || v.trim().isEmpty) ? null : v.trim();

  // ─────────────── AUTHENTICATION METHODS DISABLED ───────────────
  Future<bool> register({
    String? name,
    String? email,
    String? password,
    String? phone,
  }) async {
    // Authentication disabled - always return true
    _toast('Authentication disabled - proceeding without registration');
    return true;
  }

  Future<bool> login({String? email, String? password, String? phone}) async {
    // Authentication disabled - always return true
    _toast('Authentication disabled - proceeding without login');
    return true;
  }

  // ─────────────── Token helpers (disabled) ───────────────
  Future<void> _saveToken(String token) async {
    // No-op - authentication disabled
  }

  Future<String?> getToken() async {
    // Always return null - no authentication
    return null;
  }

  Future<void> clearToken() async {
    // No-op - authentication disabled
  }

  // ─────────────── UI helpers ───────────────
  void _toast(String msg) {
    if (Get.isOverlaysOpen || Get.context != null) {
      Get.snackbar('Auth', msg, snackPosition: SnackPosition.BOTTOM);
    } else {
      print('[Auth] $msg');
    }
  }

  @override
  void onClose() {
    nameCtrl.dispose();
    emailCtrl.dispose();
    passwordCtrl.dispose();
    phoneCtrl.dispose();
    super.onClose();
  }
}
