// widgets/deleted_card.dart
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:spam_analyzer_v6/models/analyzedscreenshot_model.dart';

class DeletedCard extends StatelessWidget {
  final AnalyzedScreenshot item;
  final VoidCallback? onRestore;
  final VoidCallback? onPermanentDelete;
  final VoidCallback? onCopy;
  final VoidCallback? onOpen;

  const DeletedCard({
    super.key,
    required this.item,
    this.onRestore,
    this.onPermanentDelete,
    this.onCopy,
    this.onOpen,
  });

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(18),
        color: const Color(0xFF0B0D12),
        boxShadow: const [
          BoxShadow(
            color: Colors.black54,
            blurRadius: 16,
            offset: Offset(0, 8),
          ),
        ],
        border: Border.all(color: Colors.white10),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(18),
        child: Column(
          children: [
            Stack(
              children: [
                _PreviewBackground(url: item.screenshotUrl),
                Positioned.fill(
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        colors: [
                          Colors.black.withOpacity(0.35),
                          Colors.black.withOpacity(0.55),
                        ],
                      ),
                    ),
                  ),
                ),
                // chips row
                Positioned(
                  top: 10,
                  left: 10,
                  child: Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      _Chip(
                        label: (item.isSpam ?? false) ? 'Spam' : 'Clean',
                        icon:
                            (item.isSpam ?? false)
                                ? Icons.warning_amber_rounded
                                : Icons.shield_moon_outlined,
                        color:
                            (item.isSpam ?? false) ? Colors.amber : cs.primary,
                      ),
                      _Chip(
                        label:
                            item.carrier?.isNotEmpty == true
                                ? item.carrier!
                                : 'Unknown',
                        icon: Icons.cell_tower_rounded,
                        color: Colors.blueGrey.shade300,
                      ),
                    ],
                  ),
                ),
                Positioned(
                  top: 4,
                  right: 4,
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      IconButton(
                        tooltip: 'Restore',
                        onPressed: onRestore,
                        icon: const Icon(Icons.restore, color: Colors.white),
                      ),
                      const SizedBox(width: 6),
                      IconButton(
                        tooltip: 'Delete permanently',
                        onPressed: onPermanentDelete,
                        icon: const Icon(
                          Icons.delete_forever_outlined,
                          color: Colors.redAccent,
                        ),
                      ),
                    ],
                  ),
                ),
                Positioned.fill(
                  child: Align(
                    alignment: Alignment.center,
                    child: Container(
                      height: 120,
                      margin: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 22,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.white.withOpacity(0.06),
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(color: Colors.white12, width: 1),
                      ),
                      child: const Center(
                        child: Icon(
                          Icons.image,
                          color: Colors.white30,
                          size: 36,
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.fromLTRB(16, 14, 12, 10),
              decoration: const BoxDecoration(
                color: Color(0xFF0E1220),
                border: Border(top: BorderSide(color: Colors.white12)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '#  Extracted: ${_safe(item.extractedNumber)}',
                    style: const TextStyle(color: Colors.white70),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    '↗  To: ${_safe(item.toNumber, fallback: 'Unknown')}',
                    style: const TextStyle(color: Colors.white70),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    '🕒  Time: ${_formatTime(item.time)}',
                    style: const TextStyle(color: Colors.white70),
                  ),
                  const SizedBox(height: 10),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      IconButton(
                        tooltip: 'Copy',
                        icon: const Icon(
                          Icons.copy_rounded,
                          color: Colors.white70,
                          size: 20,
                        ),
                        onPressed: onCopy,
                      ),
                      const SizedBox(width: 6),
                      IconButton(
                        tooltip: 'Open',
                        icon: const Icon(
                          Icons.open_in_full,
                          color: Colors.white70,
                          size: 20,
                        ),
                        onPressed: onOpen,
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _safe(String? v, {String fallback = ''}) =>
      (v == null || v.trim().isEmpty) ? fallback : v;

  String _formatTime(DateTime? dt) {
    if (dt == null) return '—';
    return DateFormat('EEE, dd MMM · hh:mm a').format(dt);
  }
}

class _PreviewBackground extends StatelessWidget {
  const _PreviewBackground({this.url});
  final String? url;

  @override
  Widget build(BuildContext context) {
    final ph = Container(color: const Color(0xFF141926), height: 180);
    if (url == null || url!.isEmpty) {
      return _blur(ph);
    }
    return _blur(
      Image.network(
        url!,
        height: 180,
        width: double.infinity,
        fit: BoxFit.cover,
        errorBuilder: (_, __, ___) => ph,
      ),
    );
  }

  Widget _blur(Widget child) {
    return Stack(
      children: [
        SizedBox(height: 180, width: double.infinity, child: child),
        Positioned.fill(
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
            child: Container(color: Colors.black.withOpacity(0.06)),
          ),
        ),
      ],
    );
  }
}

class _Chip extends StatelessWidget {
  final String label;
  final IconData icon;
  final Color color;
  const _Chip({required this.label, required this.icon, required this.color});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: color.withOpacity(0.18),
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: color.withOpacity(0.35)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: Colors.white),
          const SizedBox(width: 6),
          Text(
            label,
            style: const TextStyle(
              color: Colors.white,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}
