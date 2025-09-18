// widgets/screenshot_tile.dart
import 'package:flutter/material.dart';
import '../models/analyzedscreenshot_model.dart';

class ScreenshotTile extends StatelessWidget {
  final AnalyzedScreenshot item;
  final Widget? trailing;
  final VoidCallback? onTap;

  const ScreenshotTile({super.key, required this.item, this.trailing, this.onTap});

  @override
  Widget build(BuildContext context) {
    final subtitle = <String>[
      if (item.toNumber?.isNotEmpty == true) 'To: ${item.toNumber}',
      if (item.carrier?.isNotEmpty == true) 'Carrier: ${item.carrier}',
      if (item.isSpam != null) 'Spam: ${item.isSpam! ? 'Yes' : 'No'}',
      if (item.time != null) 'Time: ${item.time}',
    ].join(' • ');

    return ListTile(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      tileColor: Theme.of(context).colorScheme.surfaceVariant.withOpacity(0.25),
      leading: _PreviewThumb(url: item.screenshotUrl),
      title: Text(item.extractedNumber ?? '(no extracted number)'),
      subtitle: Text(subtitle),
      trailing: trailing,
      onTap: onTap,
    );
  }
}

class _PreviewThumb extends StatelessWidget {
  final String? url;
  const _PreviewThumb({this.url});

  @override
  Widget build(BuildContext context) {
    final ph = Container(
      width: 56, height: 56,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(10),
        color: Theme.of(context).colorScheme.surfaceVariant,
      ),
      child: const Icon(Icons.image_outlined),
    );
    if (url == null || url!.isEmpty) return ph;
    return ClipRRect(
      borderRadius: BorderRadius.circular(10),
      child: Image.network(url!, width: 56, height: 56, fit: BoxFit.cover,
        errorBuilder: (_, __, ___) => ph),
    );
  }
}
