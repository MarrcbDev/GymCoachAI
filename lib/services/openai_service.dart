import 'dart:convert';
import 'package:http/http.dart' as http;

class OpenAIService {
  final String apiKey = "YOUR_API_KEY";

  Future<String> getCoachAdvice(String prompt) async {
    final response = await http.post(
      Uri.parse("https://api.openai.com/v1/chat/completions"),
      headers: {
        "Content-Type": "application/json",
        "Authorization": "Bearer $apiKey",
      },
      body: jsonEncode({
        "model": "gpt-4o-mini",
        "messages": [
          {
            "role": "system",
            "content":
                "Eres un coach de gimnasio experto en nutrición y entrenamiento.",
          },
          {"role": "user", "content": prompt},
        ],
      }),
    );

    if (response.statusCode == 200) {
      String rawText =
          jsonDecode(response.body)["choices"][0]["message"]["content"];
      return utf8.decode(
        rawText.runes.toList(),
      ); // 🔥 Decodifica correctamente los caracteres
    } else {
      return "Error al obtener respuesta de OpenAI";
    }
  }
}
