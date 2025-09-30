import 'dart:convert';
import 'package:get/get.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter/material.dart';
import 'package:spam_analyzer_v6/main.dart';
import 'package:get_storage/get_storage.dart';

class AuthController extends GetxController {
  final nameCtrl = TextEditingController();
  final emailCtrl = TextEditingController();
  final passwordCtrl = TextEditingController();
  final phoneCtrl = TextEditingController();

  final registering = false.obs;

  final loggingIn = false.obs;

  static const String _baseUrl =
      'https://spam-analyzer-backend-zr1v.onrender.com/api';
  static const String _registerPath = '/user/register';
  static const String _loginPath = '/user/login';
  static const String _tokenKey = 'auth_token';
  final box = GetStorage();
  final RxList<String> carriers = <String>['T Mobile', 'Verizon', 'AT&T'].obs;
  final RxnString selectedCarrier = RxnString();
  void setCarrier(String? v) =>
      selectedCarrier.value = (v == null || v.trim().isEmpty) ? null : v.trim();
  Future<bool> register({String? email, String? password}) async {
    if (registering.value) return false;
    final carrierName = selectedCarrier.value;
    final e = (email ?? emailCtrl.text).trim();
    final p = (password ?? passwordCtrl.text);
    if (carrierName == null || carrierName.isEmpty) {
      _toast('Please select a carrier');
      return false;
    }
    if (e.isEmpty || p.isEmpty) {
      _toast('Please fill all fields');
      return false;
    }
    try {
      registering.value = true;
      final uri = Uri.parse('$_baseUrl$_registerPath');
      final body = {'carrier': carrierName, 'email': e, 'password': p};
      final res = await http.post(
        uri,
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode(body),
      );
      if (res.statusCode == 200 || res.statusCode == 201) {
        final Map<String, dynamic> json = jsonDecode(res.body);
        final Map<String, dynamic> data =
            (json['data'] ?? {}) as Map<String, dynamic>;
        final String token = (data['token'] ?? '').toString();
        if (token.isEmpty) {
          _toast('Token missing in response');
          return false;
        }
        await _saveToken(token);
        try {} catch (_) {}
        _toast('Registered successfully');
        Get.offAll(() => const CallScreen());
        return true;
      }
      final Map<String, dynamic> json = jsonDecode(res.body);
      final msg = json['message']?.toString() ?? 'Registration failed';
      _toast(msg);

      return false;
    } catch (e) {
      _toast('Error: $e');
      return false;
    } finally {
      registering.value = false;
    }
  }

  Future<bool> login({String? email, String? password, String? phone}) async {
    if (loggingIn.value) return false;
    final e = (email ?? emailCtrl.text).trim();
    final p = (password ?? passwordCtrl.text);
    final ph = (phone ?? phoneCtrl.text).trim();
    if (e.isEmpty || p.isEmpty) {
      _toast('Please enter email, password');
      return false;
    }

    try {
      loggingIn.value = true;

      final uri = Uri.parse('$_baseUrl$_loginPath');
      final res = await http.post(
        uri,
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'email': e, 'password': p}),
      );
      if (res.statusCode == 200) {
        final Map<String, dynamic> json = jsonDecode(res.body);
        final Map<String, dynamic> data =
            (json['data'] ?? {}) as Map<String, dynamic>;
        final String token = (data['token'] ?? '').toString();
        if (token.isEmpty) {
          _toast('Token missing in response');
          print('Token missing in response');
          return false;
        }
        await _saveToken(token);
        print("token saved $token");
        _toast('Logged in successfully');
        Get.offAll(() => const CallScreen());
        return true;
      }

      final Map<String, dynamic> json = jsonDecode(res.body);
      final msg = json['message']?.toString() ?? 'Login failed';
      _toast(msg);
      print("msg $msg");
      return false;
    } catch (e) {
      _toast('Error: $e');
      return false;
    } finally {
      loggingIn.value = false;
    }
  }

  Future<void> _saveToken(String token) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_tokenKey, token);
  }

  Future<String?> getToken() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_tokenKey);
  }

  Future<void> clearToken() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_tokenKey);
  }

  void _toast(String msg) {
    if (Get.isOverlaysOpen || Get.context != null) {
      Get.snackbar(
        'Auth',
        msg,
        snackPosition: SnackPosition.BOTTOM,
        colorText: Colors.white,
        backgroundColor: Colors.green,
      );
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
