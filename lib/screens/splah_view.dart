import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:spam_analyzer_v6/controller/splash_controller.dart';

class SplashView extends StatelessWidget {
  final SplashController splashController = Get.put(SplashController());
  SplashView({super.key});
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Center(
        child: Image.asset('assets/spam.png', width: 100, height: 100),
      ),
    );
  }
}
