import 'dart:convert';
import 'package:http/http.dart' as http;

class ApiService {
  // --------- CHANGE THIS ----------
  static const String baseUrl = 'https://spam-analyzer-backend.onrender.com/api/screenshot';

  static Future<Map<String, dynamic>> getJson(String path) async {
    final res = await http.get(Uri.parse('$baseUrl$path'));
    final body = jsonDecode(res.body);
    if (res.statusCode >= 200 && res.statusCode < 300) return body;
    throw Exception(body is Map && body['error'] != null ? body['error'] : 'Request failed');
  }

  static Future<Map<String, dynamic>> deleteJson(String path) async {
    final res = await http.delete(Uri.parse('$baseUrl$path'));
    final body = jsonDecode(res.body);
    if (res.statusCode >= 200 && res.statusCode < 300) return body;
    throw Exception(body is Map && body['error'] != null ? body['error'] : 'Request failed');
  }

  static Future<Map<String, dynamic>> postJson(String path, {Map<String, String>? headers, Object? body}) async {
    final res = await http.post(
      Uri.parse('$baseUrl$path'),
      headers: headers ?? {'Content-Type': 'application/json'},
      body: body == null ? null : (body is String ? body : jsonEncode(body)),
    );
    final decoded = jsonDecode(res.body);
    if (res.statusCode >= 200 && res.statusCode < 300) return decoded;
    throw Exception(decoded is Map && decoded['error'] != null ? decoded['error'] : 'Request failed');
  }
}
