import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:spam_analyzer_v6/Controller/auth_controller.dart';

import 'register_screen.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _emailCtrl = TextEditingController();
  final _passCtrl = TextEditingController();
  bool _obscure = true;
  static const kBlue = Color(0xff009688);
  static const kBgBlack = Colors.black;
  static const kCard = Color(0xFF0F1013);
  static const kField = Color(0xFF1A1D21);
  static const kStroke = Color(0xFF2B3036);
  static const kHint = Color(0xFF98A2B3);
  static const kText = Colors.white;
  static const kSubText = Color(0xFFB8C0CC);
  static const kDivider = Color(0xFF1F2430);
  final AuthController authController = Get.put(AuthController());
  InputDecoration _dec({
    required String hint,
    required IconData icon,
    Widget? suffix,
  }) {
    const radius = 14.0;
    final base = OutlineInputBorder(
      borderRadius: BorderRadius.circular(radius),
      borderSide: const BorderSide(color: kStroke, width: 1),
    );
    return InputDecoration(
      hintText: hint,
      hintStyle: const TextStyle(color: kHint, fontWeight: FontWeight.w500),
      filled: true,
      fillColor: kField,
      prefixIcon: Icon(icon, color: Colors.white70),
      suffixIcon: suffix,
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 16),
      enabledBorder: base,
      focusedBorder: base.copyWith(
        borderSide: const BorderSide(color: kBlue, width: 1.2),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: kBgBlack,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 420),
            child: Container(
              decoration: BoxDecoration(
                color: kCard,
                borderRadius: BorderRadius.circular(24),
                boxShadow: const [
                  BoxShadow(
                    color: Colors.black54,
                    blurRadius: 24,
                    offset: Offset(0, 18),
                  ),
                ],
              ),
              padding: const EdgeInsets.fromLTRB(20, 20, 20, 24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const SizedBox(height: 192),

                  const Padding(
                    padding: EdgeInsets.symmetric(horizontal: 6),
                    child: Text(
                      'Sign in to\nyour Account',
                      style: TextStyle(
                        color: kText,
                        fontSize: 28,
                        fontWeight: FontWeight.w800,
                        height: 1.15,
                        letterSpacing: 0.2,
                      ),
                    ),
                  ),
                  const SizedBox(height: 22),

                  TextField(
                    controller: _emailCtrl,
                    keyboardType: TextInputType.phone,
                    textInputAction: TextInputAction.next,
                    style: const TextStyle(
                      color: kText,
                      fontWeight: FontWeight.w600,
                    ),
                    decoration: _dec(hint: '818 123 7654', icon: Icons.phone),
                  ),
                  const SizedBox(height: 14),

                  // Password
                  TextField(
                    controller: _passCtrl,
                    obscureText: _obscure,
                    style: const TextStyle(
                      color: kText,
                      fontWeight: FontWeight.w600,
                    ),
                    decoration: _dec(
                      hint: 'password',
                      icon: Icons.lock_outline,
                      suffix: IconButton(
                        onPressed: () => setState(() => _obscure = !_obscure),
                        icon: Icon(
                          _obscure
                              ? Icons.visibility_off_outlined
                              : Icons.visibility_outlined,
                          color: kHint,
                        ),
                      ),
                    ),
                  ),

                  const SizedBox(height: 36),

                  Obx(
                    () => SizedBox(
                      width: double.infinity,
                      height: 54,
                      child: ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: kBlue,
                          foregroundColor: Colors.white,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(16),
                          ),
                          elevation: 0,
                        ),
                        onPressed: () {
                          authController.login(
                            email: _emailCtrl.text,
                            password: _passCtrl.text,
                          );
                        },
                        child:
                            authController.loggingIn.value
                                ? const CircularProgressIndicator()
                                : Text(
                                  'Sign In',
                                  style: TextStyle(
                                    fontSize: 16,
                                    fontWeight: FontWeight.w700,
                                  ),
                                ),
                      ),
                    ),
                  ),

                  const SizedBox(height: 14),

                  // Don't have account
                  Center(
                    child: RichText(
                      text: TextSpan(
                        text: "Don't have an account? ",
                        style: TextStyle(color: kSubText, fontSize: 14),
                        children: [
                          TextSpan(
                            recognizer:
                                TapGestureRecognizer()
                                  ..onTap =
                                      () =>
                                          Get.to(() => const RegisterScreen()),
                            text: 'Register',
                            style: TextStyle(
                              color: kBlue,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),

                  const SizedBox(height: 18),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
