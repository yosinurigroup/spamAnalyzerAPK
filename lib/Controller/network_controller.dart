import 'dart:async';
import 'package:get/get.dart';
import 'package:internet_connection_checker_plus/internet_connection_checker_plus.dart';
import 'package:spam_analyzer_v6/screens/no_internet_connection.dart';

class NetworkController extends GetxService {
  static NetworkController get to => Get.find();
  final hasInternet = true.obs;
  late final InternetConnection _internet = InternetConnection();
  StreamSubscription<InternetStatus>? _sub;
  bool _showingNoInternet = false; 
  Future<NetworkController> init() async {
    hasInternet.value = await _internet.hasInternetAccess;
    _handleNav();
    _sub = _internet.onStatusChange.listen((status) async {
      final online = status == InternetStatus.connected;
      if (hasInternet.value == online) return;
      hasInternet.value = online;
      _handleNav();
    });
    return this;
  }

  Future<void> forceCheck() async {
    hasInternet.value = await _internet.hasInternetAccess;
    _handleNav();
  }
  void _handleNav() {
    final offline = !hasInternet.value;
    if (offline) {
      if (!_showingNoInternet) {
        _showingNoInternet = true;
        Get.to(() => const NoInternetPage(),
            transition: Transition.fadeIn, fullscreenDialog: true);
      }
    } else {
      if (_showingNoInternet) {
        if (Get.key.currentState?.canPop() ?? false) {
          Get.back(); 
        }
        _showingNoInternet = false;
      }
    }
  }

  @override
  void onClose() {
    _sub?.cancel();
    super.onClose();
  }
  
}
