import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:spam_analyzer_v6/models/analyzedscreenshot_model.dart';
import 'package:spam_analyzer_v6/service/api_service.dart';

class AnalyzedScreenshotController extends GetxController {
  final isLoading = false.obs;

  final RxList<AnalyzedScreenshot> screenshots = <AnalyzedScreenshot>[].obs;

  final RxList<AnalyzedScreenshot> deleted = <AnalyzedScreenshot>[].obs;

  @override
  void onInit() {
    super.onInit();
    fetchAnalyzedScreenshots();
    // refreshDeleted();
  }

  T _decode<T>(String body) {
    try {
      return jsonDecode(body) as T;
    } catch (e) {
      throw 'Invalid JSON: $e';
    }
  }

  List<AnalyzedScreenshot> _parseList(dynamic data) {
    if (data is List) {
      return data
          .whereType<Map<String, dynamic>>()
          .map(AnalyzedScreenshot.fromJson)
          .toList();
    }
    return const <AnalyzedScreenshot>[];
  }

  AnalyzedScreenshot? _parseOne(dynamic data) {
    if (data is Map<String, dynamic>) {
      return AnalyzedScreenshot.fromJson(data);
    }
    return null;
  }

 Future<void> fetchAnalyzedScreenshots() async {
  isLoading.value = true;
  try {
    // AUTHENTICATION DISABLED - Skip token check and API call
    // Just return empty list or mock data
    screenshots.assignAll(<AnalyzedScreenshot>[]);
    
    Get.snackbar(
      'Info',
      'Authentication disabled - no screenshots loaded',
      colorText: Colors.white,
      backgroundColor: Colors.blue,
    );
  } catch (e) {
    Get.snackbar(
      'Error',
      'Error in getting data: $e',
      colorText: Colors.white,
      backgroundColor: Colors.red,
    );
  } finally {
    isLoading.value = false;
  }
}

  Future<void> refreshDeleted() async {
    isLoading.value = true;
    try {
      final json = await ApiService.getJson('/recently-deleted');
      final items = _parseList(json['data']);
      deleted.assignAll(items); // <-- FIX: fill deleted list
      // Get.snackbar('Success', 'Deleted list refreshed',colorText: Colors.white,backgroundColor: Colors.green);
    } catch (e) {
      Get.snackbar('Error', 'Error in getting deleted data',colorText: Colors.white,backgroundColor: Colors.red);
    } finally {
      isLoading.value = false;
    }
  }

  /// Unified soft delete using ApiService (optional alternative)
  Future<void> softDelete(String id) async {
    try {
      await ApiService.deleteJson('/delete/$id');
      screenshots.removeWhere((x) => x.id == id);
      await fetchAnalyzedScreenshots();
      Get.snackbar('Moved', 'Item Deleted',colorText: Colors.white,backgroundColor: Colors.green);
    } catch (e) {
      Get.snackbar('Delete failed', e.toString(),colorText: Colors.white,backgroundColor: Colors.red);
    }
  }

  Future<void> restore(String id) async {
    try {
      await ApiService.postJson('/restore/$id');
      // Item will leave deleted list and should reappear in all
      deleted.removeWhere((x) => x.id == id);
      await fetchAnalyzedScreenshots();
      Get.snackbar('Restored', 'Item restored successfully',colorText: Colors.white,backgroundColor: Colors.green);
    } catch (e) {
      Get.snackbar('Restore failed', e.toString(),colorText: Colors.white,backgroundColor: Colors.red);
    }
  }
  Future<void> permanentDelete(String id) async {
    try {
      await ApiService.deleteJson('/permanent/$id');
      deleted.removeWhere((x) => x.id == id);
      Get.snackbar('Deleted', 'Item permanently deleted',colorText: Colors.white,backgroundColor: Colors.green);
    } catch (e) {
      Get.snackbar('Delete failed', e.toString(),colorText: Colors.white,backgroundColor: Colors.red);
    }
  }
}
