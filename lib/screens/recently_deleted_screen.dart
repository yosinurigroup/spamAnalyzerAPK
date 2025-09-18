import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:intl/intl.dart';
import 'package:spam_analyzer_v6/Controller/network_controller.dart';
import 'package:spam_analyzer_v6/controller/analyzedscreenshot_controller.dart';
import 'package:spam_analyzer_v6/models/analyzedscreenshot_model.dart';
import 'package:spam_analyzer_v6/screens/fullimageview.dart';

class RecentlyDeletedPage extends StatefulWidget {
  const RecentlyDeletedPage({super.key});
  @override
  State<RecentlyDeletedPage> createState() => _RecentlyDeletedPageState();
}

class _RecentlyDeletedPageState extends State<RecentlyDeletedPage> {
  // ❌ Get.put(...)  ->  ✅ reuse existing instance
  late final AnalyzedScreenshotController c;
  final dt = DateFormat('EEE, dd MMM • hh:mm a');

Worker? _netWorker; // optional one-shot retry listener

@override
void initState() {
  super.initState();
  c = Get.find<AnalyzedScreenshotController>();

  // build ke baad run so UI ready rahe
  WidgetsBinding.instance.addPostFrameCallback((_) async {
    final net = NetworkController.to;

    // latest state le lo
    await net.forceCheck();

    // OFFLINE: API skip + optional one-shot retry when back online
    if (!net.hasInternet.value) {
      Get.snackbar('Offline', 'No internet connection.',
          colorText: Colors.white, backgroundColor: Colors.red);

      // auto retry once when internet wapas aaye
      _netWorker = ever<bool>(net.hasInternet, (online) async {
        if (online) {
          _netWorker?.dispose();
          await c.refreshDeleted(); // <-- API call ab
        }
      });
      return;
    }

    // ONLINE: normal API call
    await c.refreshDeleted();
  });
}

@override
void dispose() {
  _netWorker?.dispose();
  super.dispose();
}

  @override
  Widget build(BuildContext context) {

    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_rounded, color: Colors.white),
          onPressed: () => Navigator.pop(context),
        ),
        backgroundColor: Colors.transparent,
        title: const Text('Recently Deleted', style: TextStyle(color: Colors.white)),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh, color: Colors.white),
            onPressed: c.refreshDeleted,
          )
        ],
      ),
      body: Obx(() {
        if (c.isLoading.value) return const Center(child: CircularProgressIndicator());

        if (c.deleted.isEmpty) {
          return const Center(
            child: Text('Trash is empty', style: TextStyle(color: Colors.white70)),
          );
        }

        return RefreshIndicator(
          onRefresh: c.refreshDeleted,
          color: Colors.white,
          backgroundColor: Colors.black87,
          child: ListView.separated(
            padding: const EdgeInsets.all(16),
            itemCount: c.deleted.length,
            separatorBuilder: (_, __) => const SizedBox(height: 20),
            itemBuilder: (_, i) {
              final item = c.deleted[i];
              return _GlassCard(
                item: item,
                onRestore: () => c.restore(item.id),
                onPermanentDelete: () => _confirm(
                  context,
                  title: 'Delete permanently? This cannot be undone.',
                  onYes: () => c.permanentDelete(item.id),
                ),
                onCopy: () {
                  final n = (item.extractedNumber ?? item.toNumber);
                  if (n == null || n.isEmpty) return;
                  Clipboard.setData(ClipboardData(text: n));
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text('Copied: $n'), backgroundColor: Colors.white10),
                  );
                },
                onOpen: () {
                  if (item.screenshotUrl == null) return;
                  Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => FullImagePage(imageUrl: item.screenshotUrl!)),
                  );
                },
                dt: dt,
              );
            },
          ),
        );
      }),
    );
  }
}

Future<void> _confirm(BuildContext context,
    {required String title, required VoidCallback onYes}) async {
  final ok = await showDialog<bool>(
    context: context,
    builder: (_) => AlertDialog(
      backgroundColor: const Color(0xFF111318),
      title: Text(title, style: const TextStyle(color: Colors.white)),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('No')),
        FilledButton(
          onPressed: () => Navigator.pop(context, true),
          style: FilledButton.styleFrom(backgroundColor: Colors.red),
          child: const Text('Yes'),
        ),
      ],
    ),
  );
  if (ok == true) onYes();
}
class _GlassCard extends StatelessWidget {
  final AnalyzedScreenshot item;
  final VoidCallback? onRestore;
  final VoidCallback? onPermanentDelete;
  final VoidCallback? onCopy;
  final VoidCallback? onOpen;
  final DateFormat dt;

  const _GlassCard({
    required this.item,
    this.onRestore,
    this.onPermanentDelete,
    this.onCopy,
    this.onOpen,
    required this.dt,
  });

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(16),
      child: Stack(
        children: [
          BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 12, sigmaY: 12),
            child: Container(
              decoration: BoxDecoration(
                color: Colors.indigo.withOpacity(0.2),
                border: Border.all(color: Colors.white.withOpacity(0.08)),
                borderRadius: BorderRadius.circular(16),
              ),
              child: Column(
                children: [
                  // ---- header image with badges ----
                  Stack(
                    children: [
                      AspectRatio(
                        aspectRatio: 16 / 9,
                        child: item.screenshotUrl != null
                            ? Image.network(item.screenshotUrl!,
                                fit: BoxFit.cover,
                                errorBuilder: (_, __, ___) =>
                                    Container(color: Colors.black26))
                            : Container(color: Colors.black26),
                      ),
                      Positioned.fill(
                          child: Container(color: Colors.black.withOpacity(0.3))),
                      Positioned(
                        left: 10,
                        top: 10,
                        child: Wrap(
                          spacing: 8,
                          children: [
                            _Badge(
                              label: item.isSpam == true ? 'Spam' : 'Clean',
                              color: item.isSpam == true
                                  ? Colors.amber
                                  : Colors.greenAccent,
                              icon: item.isSpam == true
                                  ? Icons.warning_amber_rounded
                                  : Icons.verified_rounded,
                            ),
                            if ((item.carrier ?? '').isNotEmpty)
                              _Badge(
                                label: item.carrier!,
                                color: const Color(0xFF60A5FA),
                                icon: Icons.network_cell_rounded,
                              ),
                          ],
                        ),
                      ),
                      Positioned(
                        top: 6,
                        right: 6,
                        child: Row(
                          children: [
                            IconButton(
                              tooltip: 'Restore',
                              icon: const Icon(Icons.restore, color: Colors.white),
                              onPressed: onRestore,
                            ),
                            IconButton(
                              tooltip: 'Delete permanently',
                              icon: const Icon(Icons.delete_forever_outlined,
                                  color: Colors.redAccent),
                              onPressed: onPermanentDelete,
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),

                  // ---- body info ----
                  Padding(
                    padding: const EdgeInsets.fromLTRB(14, 12, 14, 6),
                    child: Column(
                      children: [
                        _KeyValueRow(
                            icon: Icons.numbers_rounded,
                            label: 'Extracted',
                            value: item.extractedNumber ?? '—'),
                        const SizedBox(height: 12),
                        _KeyValueRow(
                            icon: Icons.call_made_rounded,
                            label: 'To',
                            value: item.toNumber ?? '—'),
                        const SizedBox(height: 12),
                        _KeyValueRow(
                            icon: Icons.access_time_rounded,
                            label: 'Time',
                            value: item.time != null
                                ? dt.format(item.time!.toLocal())
                                : '—'),
                      ],
                    ),
                  ),

                  // ---- footer actions ----
                  if (onCopy != null || onOpen != null)
                    Align(
                      alignment: Alignment.centerRight,
                      child: Padding(
                        padding: const EdgeInsets.only(right: 8, bottom: 4),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            if (onCopy != null)
                              IconButton(
                                  tooltip: 'Copy',
                                  onPressed: onCopy,
                                  icon: const Icon(Icons.copy_rounded,
                                      color: Colors.white70)),
                            if (onOpen != null)
                              IconButton(
                                  tooltip: 'Open',
                                  onPressed: onOpen,
                                  icon: const Icon(Icons.fullscreen_rounded,
                                      color: Colors.white70)),
                          ],
                        ),
                      ),
                    ),
                  const SizedBox(height: 6),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _KeyValueRow extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;

  const _KeyValueRow(
      {required this.icon, required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, size: 18, color: Colors.white70),
        const SizedBox(width: 8),
        Text('$label:',
            style: const TextStyle(
                color: Colors.white70, fontWeight: FontWeight.w600)),
        const SizedBox(width: 6),
        Expanded(
          child: Text(
            value,
            textAlign: TextAlign.right,
            style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w500),
            overflow: TextOverflow.ellipsis,
          ),
        ),
      ],
    );
  }
}

class _Badge extends StatelessWidget {
  final String label;
  final Color color;
  final IconData icon;
  const _Badge({required this.label, required this.color, required this.icon});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: color.withOpacity(0.2),
        border: Border.all(color: color),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: Colors.white),
          const SizedBox(width: 6),
          Text(label,
              style: const TextStyle(
                  color: Colors.white, fontWeight: FontWeight.w700)),
        ],
      ),
    );
  }
}
