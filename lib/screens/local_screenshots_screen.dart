import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:spam_analyzer_v6/screens/fullimageview.dart';

class ScreenshotResult extends StatefulWidget {
  const ScreenshotResult({super.key});
  @override
  State<ScreenshotResult> createState() => _ScreenshotResultState();
}

class _ScreenshotResultState extends State<ScreenshotResult> {
  static const _ch = MethodChannel('com.example.call_detector/channel');
  List<Map<String, dynamic>> _items = [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final List<dynamic> res = await _ch.invokeMethod('getScreenshots');
      _items = res.cast<Map>().map((m) => Map<String, dynamic>.from(m)).toList();
    } catch (e) {
      debugPrint('getScreenshots error: $e');
      _items = [];
    }
    if (mounted) setState(() => _loading = false);
  }

  Future<void> _delete(Map<String, dynamic> row) async {
    try {
      final ok = await _ch.invokeMethod('deleteScreenshot', {
        'id': row['id'],
        'path': row['path'],
      });
      if (ok == true) {
        _items.removeWhere((e) => e['id'] == row['id']);
        if (mounted) setState(() {});
      }
    } catch (e) {
      debugPrint('deleteScreenshot error: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Captured Screenshots'),
        backgroundColor: Colors.black,
      ),
      backgroundColor: Colors.black,
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: _load,
              child: _items.isEmpty
                  ? const Center(child: Text('No screenshots yet', style: TextStyle(color: Colors.white70)))
                  : GridView.builder(
                      padding: const EdgeInsets.all(10),
                      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                        crossAxisCount: 3, crossAxisSpacing: 8, mainAxisSpacing: 8),
                      itemCount: _items.length,
                      itemBuilder: (_, i) {
                        final row = _items[i];
                        final String path = row['path'];
                        final bool uploaded = (row['uploaded'] == true);
                        return GestureDetector(
                          onTap: () => Get.to(() => FullImagePage(imageUrl: path)),
                          child: GestureDetector(
                            onLongPress: () => _delete(row),
                            child: Stack(
                              children: [
                                Positioned.fill(
                                  child: File(path).existsSync()
                                      ? Image.file(File(path), fit: BoxFit.cover)
                                      : Container(color: Colors.grey[800]),
                                ),
                                Positioned(
                                  right: 4, top: 4,
                                  child: Icon(
                                    uploaded ? Icons.cloud_done : Icons.cloud_upload,
                                    size: 18,
                                    color: uploaded ? Colors.lightGreenAccent : Colors.white70,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        );
                      },
                    ),
            ),
      floatingActionButton: FloatingActionButton(
        onPressed: () async {
          try {
            await _ch.invokeMethod('captureScreenshot');
            debugPrint('manual capture triggered');
            await Future.delayed(const Duration(seconds: 1));
            _load();
          } catch (e) {
            debugPrint('manual capture error: $e');
          }
        },
        child: const Icon(Icons.camera),
      ),
    );
  }
}
