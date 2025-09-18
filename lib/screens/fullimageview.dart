import 'package:flutter/material.dart';
import 'package:photo_view/photo_view.dart';

class FullImagePage extends StatelessWidget {
  final String imageUrl;

  const FullImagePage({
    super.key,
    required this.imageUrl,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        iconTheme: const IconThemeData(color: Colors.white),
      ),
      body: Center(
        child: PhotoView(
          imageProvider: NetworkImage(imageUrl),
          backgroundDecoration: const BoxDecoration(color: Colors.black),
          minScale: PhotoViewComputedScale.contained,
          maxScale: PhotoViewComputedScale.covered * 2,
          errorBuilder: (_, __, ___) =>
              const Center(child: Icon(Icons.error, color: Colors.white)),
        ),
      ),
    );
  }
}
