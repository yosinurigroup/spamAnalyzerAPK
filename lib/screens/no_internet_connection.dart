import 'package:flutter/material.dart';
import 'package:spam_analyzer_v6/Controller/network_controller.dart';

class NoInternetPage extends StatelessWidget {
  static const route = '/no-internet';
  const NoInternetPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.wifi_off, size: 72, color: Colors.grey),
              const SizedBox(height: 12),
              const Text(
                'No internet connection',
                style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 8),
              const Text(
                'Please check your network settings.',
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: () => NetworkController.to.forceCheck(),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
