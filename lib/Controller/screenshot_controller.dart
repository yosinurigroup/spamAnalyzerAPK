import 'dart:io';
import 'package:get/get.dart';
import 'package:http/http.dart' as http;
import 'package:image_picker/image_picker.dart';

class ScreenshotController extends GetxController {
  final picker = ImagePicker();
  var isUploading = false.obs;
  var uploadedUrl = ''.obs;
Future<void> uploadImageFile(File imageFile) async {
  
  if (!imageFile.existsSync()) {
    Get.snackbar('Error', 'File does not exist');
    return;
  }
  try {
    isUploading.value = true;
    var request = http.MultipartRequest(
      'POST',
      Uri.parse('http://192.168.100.172:5000/api/screenshot/screenshot'),
    );
    print("Uploading screenshot...");
    request.files.add(await http.MultipartFile.fromPath('imageUrl', imageFile.path));
    var response = await request.send();
print("Response code: ${response.statusCode}");
print("Response body: ${response.headers.toString()}");
final responseBody = await response.stream.bytesToString();
print("Response body: $responseBody");
    if (response.statusCode == 201) {
      
      final responseData = await http.Response.fromStream(response);
      uploadedUrl.value = responseData.body;
      Get.snackbar('Upload Success', 'Screenshot uploaded successfully');
    } else {
      Get.snackbar('Upload Failed', 'Server responded with ${response.statusCode}');
    }
  } catch (e) {
    Get.snackbar('Error', e.toString());
  } finally {
    isUploading.value = false;
  }
}
}
