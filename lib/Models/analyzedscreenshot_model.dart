class AnalyzedScreenshot {
  final String id;
  final String? screenshotUrl;
  final String? extractedNumber;
  final String? toNumber;
  final String? carrier;
  final bool? isSpam;
  final DateTime? time;
  final bool isDeleted;
  final DateTime? deletedAt;

  AnalyzedScreenshot({
    required this.id,
    this.screenshotUrl,
    this.extractedNumber,
    this.toNumber,
    this.carrier,
    this.isSpam,
    this.time,
    this.isDeleted = false,
    this.deletedAt,
  });

  static String _pickId(Map<String, dynamic> m) {
    final v = m['id'] ?? m['_id'];
    return (v ?? '').toString();
  }

  static DateTime? _parseDt(dynamic v) {
    if (v == null) return null;
    try {
      return DateTime.tryParse(v.toString());
    } catch (_) {
      return null;
    }
  }

  factory AnalyzedScreenshot.fromJson(Map<String, dynamic> m) {
    // backend may use `imageUrl` or `screenshotUrl`
    final img = (m['screenshotUrl'] ?? m['imageUrl'])?.toString();

    // backend may store bool as true/false or 'true'/'false'
    bool? _bool(dynamic x) {
      if (x == null) return null;
      if (x is bool) return x;
      final s = x.toString().toLowerCase();
      if (s == 'true') return true;
      if (s == 'false') return false;
      return null;
    }

    return AnalyzedScreenshot(
      id: _pickId(m),
      screenshotUrl: img,
      extractedNumber: m['extractedNumber']?.toString(),
      toNumber: m['toNumber']?.toString(),
      carrier: m['carrier']?.toString(),
      isSpam: _bool(m['isSpam']),
      time: _parseDt(m['time'] ?? m['createdAt']),
      isDeleted: (m['isDeleted'] is bool) ? m['isDeleted'] as bool : false,
      deletedAt: _parseDt(m['deletedAt']),
    );
  }
}
