import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:intl/intl.dart'; // optional, but looks better
import 'package:spam_analyzer_v6/controller/analyzedscreenshot_controller.dart';
import 'package:spam_analyzer_v6/models/analyzedscreenshot_model.dart';
import 'package:spam_analyzer_v6/screens/fullimageview.dart';
import 'package:spam_analyzer_v6/screens/recently_deleted_screen.dart';

enum SpamFilter { all, spam, clean }

class ScreenshotResultView extends StatefulWidget {
  const ScreenshotResultView({super.key});

  @override
  State<ScreenshotResultView> createState() => _ScreenshotResultViewState();
}

class _ScreenshotResultViewState extends State<ScreenshotResultView> {
  final AnalyzedScreenshotController analyzecontroller =
      Get.put(AnalyzedScreenshotController());

  final Rx<SpamFilter> _filter = SpamFilter.all.obs;
  final RxString _query = ''.obs;

  @override
  void initState() {
    super.initState();
    analyzecontroller.fetchAnalyzedScreenshots();
  }

  @override
  Widget build(BuildContext context) {
    final dt = DateFormat('EEE, dd MMM • hh:mm a'); // needs intl

    return Scaffold(
      floatingActionButton: FloatingActionButton(
        onPressed: () => Get.to(RecentlyDeletedPage()),
        child: const Icon(Icons.delete),
      ),
      backgroundColor: Colors.black,
      extendBodyBehindAppBar: true,
      appBar: _FrostedAppBar(
        title: 'Analyzed Screenshots',
      
        onBack: () => Navigator.pop(context),
      ),
      body: Stack(
        children: [
          // Background gradient
          const _Background(),
          // Content
          Obx(() {
            if (analyzecontroller.isLoading.value) {
              return const _SkeletonList();
            }

            final results = analyzecontroller.screenshots;
            if (results.isEmpty) {
              return const _EmptyState();
            }

            // local filtering & search (client-side, zero backend changes)
            final filtered = results.where((item) {
              final byFilter = switch (_filter.value) {
                SpamFilter.all => true,
                SpamFilter.spam => item.isSpam == true,
                SpamFilter.clean => item.isSpam == false,
              };
              final q = _query.value.trim().toLowerCase();
              if (q.isEmpty) return byFilter;
              final haystack = [
                item.extractedNumber?.toString() ?? '',
                item.toNumber?.toString() ?? '',
                item.carrier?.toString() ?? '',
              ].join(' ').toLowerCase();
              return byFilter && haystack.contains(q);
            }).toList();

            return RefreshIndicator(
              onRefresh: () async => analyzecontroller.fetchAnalyzedScreenshots(),
              color: Colors.white,
              backgroundColor: Colors.black87,
              child: CustomScrollView(
                slivers: [
                  SliverToBoxAdapter(
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(16, 100, 16, 8),
                      child: _Controls(
                        filter: _filter,
                        onFilterChanged: (f) => _filter.value = f,
                        onQueryChanged: (q) => _query.value = q,
                      ),
                    ),
                  ),
                 SliverPadding(
  padding: const EdgeInsets.fromLTRB(16, 8, 16, 36),
  sliver: SliverList.separated(
    itemCount: filtered.length,
    separatorBuilder: (_, __) => const SizedBox(height: 20), // cards ke beech zyada gap
    itemBuilder: (context, index) {
      final item = filtered[index];

      return _GlassCard(
        analyzecontroller: analyzecontroller,
        item: item,
        onTap: () {
          Navigator.push(
            context,
            PageRouteBuilder(
              transitionDuration: const Duration(milliseconds: 300),
              pageBuilder: (_, __, ___) =>
                  FullImagePage(imageUrl: item.screenshotUrl!),
            ),
          );
        },
        imageUrl: item.screenshotUrl!,
        headerBadges: [
          _Badge(
            label: item.isSpam! ? 'Spam' : 'Clean',
            color: item.isSpam! ? Colors.white : Colors.white,
            icon: item.isSpam!
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
        body: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _KeyValueRow(
              icon: Icons.numbers_rounded,
              label: 'Extracted',
              value: item.extractedNumber ?? '—',
            ),
            const SizedBox(height: 12), // 👈 spacing add ki
            _KeyValueRow(
              icon: Icons.call_made_rounded,
              label: 'To',
              value: item.toNumber ?? '—',
            ),
            const SizedBox(height: 12), // 👈 spacing add ki
            _KeyValueRow(
              icon: Icons.access_time_rounded,
              label: 'Time',
              value: _safeFormat(dt, item.time),
            ),
            const SizedBox(height: 12), // 👈 footer se pehle spacing
          ],
        ),
        footerActions: [
          IconButton(
            tooltip: 'Copy number',
            onPressed: () {
              final n = (item.extractedNumber ?? item.toNumber);
              if (n == null || n.isEmpty) return;
              Clipboard.setData(ClipboardData(text: n));
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text('Copied: $n'),
                  backgroundColor: Colors.white10,
                  behavior: SnackBarBehavior.floating,
                ),
              );
            },
            icon: const Icon(Icons.copy_rounded, color: Colors.white70),
          ),
          IconButton(
            tooltip: 'Open image',
            onPressed: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (_) =>
                      FullImagePage(imageUrl: item.screenshotUrl!),
                ),
              );
            },
            icon: const Icon(Icons.fullscreen_rounded,
                color: Colors.white70),
          ),
        ],
      );
    },
  ),
),

                ],
              ),
            );
          }),
        ],
      ),
    );
  }

  String _safeFormat(DateFormat dt, DateTime? t) {
    if (t == null) return '—';
    try {
      return dt.format(t.toLocal());
    } catch (_) {
      return t.toLocal().toString();
    }
  }
}

/// Frosted, floating app bar
class _FrostedAppBar extends StatelessWidget implements PreferredSizeWidget {
  final String title;
  final VoidCallback onBack;

  const _FrostedAppBar({required this.title, required this.onBack});

  @override
  Size get preferredSize => const Size.fromHeight(64);

  @override
  Widget build(BuildContext context) {
    return AppBar(
      elevation: 0,
      backgroundColor: Colors.transparent,
      surfaceTintColor: Colors.transparent,
      flexibleSpace: ClipRRect(
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 14, sigmaY: 14),
          child: Container(
            color: Colors.black.withOpacity(0.35),
          ),
        ),
      ),
      leading: IconButton(
        icon: const Icon(Icons.arrow_back_rounded, color: Colors.white),
        onPressed: onBack,
      ),
      centerTitle: false,
      title: Text(
        title,
        style: const TextStyle(
          color: Colors.white,
          fontWeight: FontWeight.w700,
          letterSpacing: 0.2,
        ),
      ),
    );
  }
}

/// Sexy gradient background
class _Background extends StatelessWidget {
  const _Background();

  @override
  Widget build(BuildContext context) {
    return Container(
   
    );
  }
}

/// Top controls: Segmented filter + Search
class _Controls extends StatelessWidget {
  final Rx<SpamFilter> filter;
  final ValueChanged<SpamFilter> onFilterChanged;
  final ValueChanged<String> onQueryChanged;

  const _Controls({
    required this.filter,
    required this.onFilterChanged,
    required this.onQueryChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Obx(() {
          return Container(
            decoration: BoxDecoration(
              color: Colors.white.withOpacity(0.06),
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: Colors.white10),
            ),
            padding: const EdgeInsets.all(6),
            child: Row(
              children: [
                _SegmentChip(
                  label: 'All',
                  selected: filter.value == SpamFilter.all,
                  onTap: () => onFilterChanged(SpamFilter.all),
                ),
                _SegmentChip(
                  label: 'Spam',
                  selected: filter.value == SpamFilter.spam,
                  onTap: () => onFilterChanged(SpamFilter.spam),
                ),
                _SegmentChip(
                  label: 'Clean',
                  selected: filter.value == SpamFilter.clean,
                  onTap: () => onFilterChanged(SpamFilter.clean),
                ),
              ],
            ),
          );
        }),
        const SizedBox(height: 12),
        _SearchField(onChanged: onQueryChanged),
      ],
    );
  }
}

class _SegmentChip extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;

  const _SegmentChip({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: GestureDetector(
        onTap: onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          padding: const EdgeInsets.symmetric(vertical: 10),
          decoration: BoxDecoration(
            color: selected ? Colors.white.withOpacity(0.14) : Colors.transparent,
            borderRadius: BorderRadius.circular(10),
          ),
          alignment: Alignment.center,
          child: Text(
            label,
            style: TextStyle(
              color: Colors.white.withOpacity(selected ? 1 : 0.7),
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
      ),
    );
  }
}

class _SearchField extends StatelessWidget {
  final ValueChanged<String> onChanged;

  const _SearchField({required this.onChanged});

  @override
  Widget build(BuildContext context) {
    return TextField(
      onChanged: onChanged,
      style: const TextStyle(color: Colors.white),
      cursorColor: Colors.white70,
      decoration: InputDecoration(
        hintText: 'Search number, carrier…',
        hintStyle: const TextStyle(color: Colors.white54),
        prefixIcon: const Icon(Icons.search_rounded, color: Colors.white70),
        filled: true,
        fillColor: Colors.white.withOpacity(0.06),
        enabledBorder: _border(),
        focusedBorder: _border(opacity: 0.3),
        contentPadding: const EdgeInsets.symmetric(vertical: 14, horizontal: 12),
      ),
    );
  }

  OutlineInputBorder _border({double opacity = 0.15}) => OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: BorderSide(color: Colors.white.withOpacity(opacity)),
      );
}

class _GlassCard extends StatelessWidget {
  final VoidCallback onTap;
  final String imageUrl;
  final List<_Badge> headerBadges;
  final Widget body;
  final List<Widget>? footerActions;
  final AnalyzedScreenshot item;
  final AnalyzedScreenshotController analyzecontroller;

  const _GlassCard({
    required this.onTap,
    required this.imageUrl,
    required this.headerBadges,
    required this.body,
    this.footerActions,
    required this.item,
    required this.analyzecontroller,
  });

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(16),
      child: Stack(
        children: [
          // Frosted panel
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
                  // Image header
              GestureDetector(
  onTap: onTap,
  child: Stack(
    children: [
      AspectRatio(
        aspectRatio: 16 / 9,
        child: Image.network(
          imageUrl,
          fit: BoxFit.cover,
          errorBuilder: (_, __, ___) => Container(
            color: Colors.black26,
            alignment: Alignment.center,
            child: const Icon(Icons.broken_image_rounded,
                color: Colors.white54),
          ),
        ),
      ),
      Positioned.fill(
        child: Container(
          color: Colors.black.withOpacity(0.3), // shadow overlay
        ),
      ),
      Positioned.fill(
        child: ClipRRect(
          borderRadius: BorderRadius.circular(8),
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 6, sigmaY: 6), // blur effect
            child: Container(
              color: Colors.black.withOpacity(0.1), // subtle glass look
            ),
          ),
        ),
      ),

      // Badges (top-left)
      Positioned(
        left: 10,
        top: 10,
        child: Wrap(
          spacing: 8,
          runSpacing: 8,
          children: headerBadges,
        ),
      ),

      Positioned(
        top: 6,
        right: 6,
        child: Container(
          padding: const EdgeInsets.all(2),
          decoration: BoxDecoration(
            color: Colors.white, // shadow overlay
            shape: BoxShape.circle,
          ),
          child: IconButton(
            icon:  Icon(Icons.delete_rounded, color: Colors.redAccent, size: 18),
            onPressed: () {
              analyzecontroller.softDelete(item.id);
            },
          ),
        ),
      ),
    ],
  ),
),
                  Padding(
                    padding: const EdgeInsets.fromLTRB(14, 12, 14, 6),
                    child: body,
                  ),
                  if (footerActions != null && footerActions!.isNotEmpty)
                    Align(
                      alignment: Alignment.centerRight,
                      child: Padding(
                        padding:
                            const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: footerActions!,
                        ),
                      ),
                    ),
                  const SizedBox(height: 8),
                ],
              ),
            ),
          ),
          // Subtle gradient border
          IgnorePointer(
            child: Container(
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(16),
                border: Border.all(
                  width: 1.2,
                  color: Colors.white.withOpacity(0.08),
                ),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x3300D1FF),
                    blurRadius: 14,
                    spreadRadius: -4,
                    offset: Offset(0, 6),
                  )
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

  const _KeyValueRow({
    required this.icon,
    required this.label,
    required this.value,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, size: 18, color: Colors.white70),
        const SizedBox(width: 8),
        Text(
          '$label:',
          style: const TextStyle(
            color: Colors.white70,
            fontWeight: FontWeight.w600,
          ),
        ),
        const SizedBox(width: 6),
        Expanded(
          child: Text(
            value,
            textAlign: TextAlign.right,
            style: const TextStyle(
              color: Colors.white,
              fontWeight: FontWeight.w500,
            ),
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
  final IconData? icon;

  const _Badge({required this.label, required this.color, this.icon});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: Colors.grey.shade300,
        border: Border.all(color: color.withOpacity(0.5)),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: 14, color: Colors.black),
            const SizedBox(width: 6),
          ],
          Text(
            label,
            style: TextStyle(
              color: Colors.black,
              fontWeight: FontWeight.w700,
              letterSpacing: 0.2,
            ),
          ),
        ],
      ),
    );
  }
}

/// Loading skeleton (simple, light)
class _SkeletonList extends StatelessWidget {
  const _SkeletonList();

  @override
  Widget build(BuildContext context) {
    return ListView.builder(
      padding: const EdgeInsets.fromLTRB(16, 100, 16, 24),
      itemCount: 6,
      itemBuilder: (_, __) => Padding(
        padding: const EdgeInsets.only(bottom: 14),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(16),
          child: Container(
            height: 220,
            decoration: BoxDecoration(
              color: Colors.white.withOpacity(0.05),
              border: Border.all(color: Colors.white.withOpacity(0.06)),
            ),
          ),
        ),
      ),
    );
  }
}

/// Empty state with gentle CTA
class _EmptyState extends StatelessWidget {
  const _EmptyState();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(24, 120, 24, 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.photo_library_outlined,
                color: Colors.white54, size: 64),
            const SizedBox(height: 14),
            const Text(
              'No analyzed screenshots yet',
              style: TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.w700,
                fontSize: 18,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            Text(
              'Pull to refresh or capture a screenshot to see results here.',
              style: TextStyle(color: Colors.white.withOpacity(0.7)),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}
