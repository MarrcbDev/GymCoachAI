import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import '../services/openai_service.dart';
import '../models/user_model.dart';
import '../services/database_helper.dart';

class ChatScreen extends StatefulWidget {
  const ChatScreen({super.key});

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen>
    with AutomaticKeepAliveClientMixin {
  final TextEditingController _inputController = TextEditingController();
  final FocusNode _focusNode = FocusNode();
  final OpenAIService _openAIService = OpenAIService();
  final List<Map<String, String>> _messages = [];
  UserModel? _user;
  bool _isLoading = false;

  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    _loadUser();
  }

  @override
  void dispose() {
    _focusNode.dispose();
    _inputController.dispose();
    super.dispose();
  }

  @override
  void deactivate() {
    _focusNode.unfocus(); // Deseleccionar el input al salir de la pantalla
    super.deactivate();
  }

  Future<void> _loadUser() async {
    final user = await DatabaseHelper.getUser();
    if (mounted) {
      setState(() {
        _user = user;
        _messages.clear();
      });
      if (_user != null) {
        _sendWelcomeMessage();
      }
    }
  }

  void _sendWelcomeMessage() {
    if (_user == null) return;
    String welcomeMessage =
        "💪 ¡Bienvenido, ${_user!.name}! Soy tu coach de gimnasio.\n\nPeso: ${_user!.weight} kg\nTalla: ${_user!.height} m\nObjetivos: ${_user!.goals}\n\n¿Qué deseas hacer hoy?";
    setState(() {
      _messages.add({"sender": "bot", "text": welcomeMessage});
    });
  }

  void _sendMessage() async {
    if (_inputController.text.isEmpty || _user == null) return;

    String messageText = _inputController.text;
    _inputController.clear();

    setState(() {
      _messages.add({"sender": "user", "text": messageText});
      _isLoading = true;
    });

    String userInput =
        "$messageText. Peso: ${_user!.weight} kg, Talla: ${_user!.height} m, Objetivos: ${_user!.goals}. Responde de forma breve.";

    String response = await _openAIService.getCoachAdvice(userInput);
    response = response.replaceAll(RegExp(r'[\*\#]'), '');

    _addMessageGradually(response);
  }

  void _addMessageGradually(String text) async {
    String currentText = "";
    for (int i = 0; i < text.length; i++) {
      await Future.delayed(const Duration(milliseconds: 20));
      setState(() {
        currentText += text[i];
        if (_messages.isNotEmpty && _messages.last["sender"] == "bot") {
          _messages.last["text"] = currentText;
        } else {
          _messages.add({"sender": "bot", "text": currentText});
        }
      });
    }
    setState(() => _isLoading = false);
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return Scaffold(
      backgroundColor: Colors.black87,
      body: Column(
        children: [
          Expanded(
            child: ListView.builder(
              padding: const EdgeInsets.all(16),
              itemCount: _messages.length,
              itemBuilder: (context, index) {
                final message = _messages[index];
                final isUser = message["sender"] == "user";

                return Align(
                  alignment:
                      isUser ? Alignment.centerRight : Alignment.centerLeft,
                  child: Container(
                    margin: const EdgeInsets.symmetric(vertical: 5),
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color:
                          isUser
                              ? Colors.amber.shade800
                              : Colors.brown.shade900,
                      borderRadius: BorderRadius.circular(15),
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withOpacity(0.3),
                          blurRadius: 4,
                          spreadRadius: 1,
                        ),
                      ],
                    ),
                    child: Text(
                      message["text"]!,
                      style: GoogleFonts.openSans(
                        fontSize: 16,
                        color: Colors.white,
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
          if (_isLoading)
            Padding(
              padding: const EdgeInsets.all(10.0),
              child: Text(
                "Escribiendo...",
                style: GoogleFonts.openSans(color: Colors.grey, fontSize: 14),
              ),
            ),
          Padding(
            padding: const EdgeInsets.all(10.0),
            child: Row(
              children: [
                Expanded(
                  child: Container(
                    decoration: BoxDecoration(
                      color: Colors.grey.shade900,
                      borderRadius: BorderRadius.circular(25),
                    ),
                    child: TextField(
                      controller: _inputController,
                      focusNode: _focusNode,
                      minLines: 1,
                      maxLines: 3,
                      style: GoogleFonts.openSans(color: Colors.white),
                      decoration: InputDecoration(
                        hintText: 'Escribe tu mensaje...',
                        hintStyle: GoogleFonts.openSans(color: Colors.grey),
                        border: InputBorder.none,
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 12,
                        ),
                      ),
                      textInputAction: TextInputAction.done,
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                FloatingActionButton(
                  backgroundColor: Colors.amber.shade700,
                  onPressed: _sendMessage,
                  child: const Icon(Icons.send, color: Colors.black, size: 24),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
