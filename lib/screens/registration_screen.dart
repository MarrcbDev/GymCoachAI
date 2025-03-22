import 'dart:io';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:image_picker/image_picker.dart';
import '../services/database_helper.dart';
import '../models/user_model.dart';
import 'home_screen.dart';

class RegistrationScreen extends StatefulWidget {
  @override
  _RegistrationScreenState createState() => _RegistrationScreenState();
}

class _RegistrationScreenState extends State<RegistrationScreen> {
  final TextEditingController _nameController = TextEditingController();
  final TextEditingController _ageController = TextEditingController();
  final TextEditingController _heightController = TextEditingController();
  final TextEditingController _weightController = TextEditingController();
  final TextEditingController _goalsController = TextEditingController();
  String? _imagePath;

  Future<void> _pickImage() async {
    final pickedFile = await ImagePicker().pickImage(
      source: ImageSource.gallery,
    );
    if (pickedFile != null) {
      setState(() {
        _imagePath = pickedFile.path;
      });
    }
  }

  Future<void> _saveUser() async {
    if (_nameController.text.isEmpty) return;

    UserModel user = UserModel(
      name: _nameController.text,
      imagePath: _imagePath ?? "assets/images/default_profile.jpg",
      age: int.parse(_ageController.text),
      height: double.parse(_heightController.text),
      weight: double.parse(_weightController.text),
      goals: _goalsController.text,
    );

    await DatabaseHelper.insertUser(user);

    Navigator.pushReplacement(
      context,
      MaterialPageRoute(builder: (context) => HomeScreen()),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.amber.shade800,
        title: Text(
          "Registro",
          style: GoogleFonts.leagueGothic(
            fontSize: 26,
            fontWeight: FontWeight.bold,
            color: Colors.black,
          ),
        ),
        centerTitle: true,
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: SingleChildScrollView(
          child: Column(
            children: [
              GestureDetector(
                onTap: _pickImage,
                child: CircleAvatar(
                  radius: 65,
                  backgroundColor: Colors.amber.shade700,
                  backgroundImage:
                      _imagePath != null
                          ? FileImage(File(_imagePath!))
                          : const AssetImage(
                                "assets/images/default_profile.jpg",
                              )
                              as ImageProvider,
                  child:
                      _imagePath == null
                          ? Icon(
                            Icons.add_a_photo,
                            color: Colors.black.withOpacity(0.8),
                            size: 32,
                          )
                          : null,
                ),
              ),
              const SizedBox(height: 20),
              _buildTextField(_nameController, "Nombre"),
              _buildTextField(_ageController, "Edad", isNumber: true),
              _buildTextField(_heightController, "Talla (m)", isNumber: true),
              _buildTextField(_weightController, "Peso (kg)", isNumber: true),
              _buildTextField(_goalsController, "Objetivos"),
              const SizedBox(height: 20),
              ElevatedButton(
                onPressed: _saveUser,
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.amber.shade800,
                  padding: const EdgeInsets.symmetric(
                    horizontal: 40,
                    vertical: 15,
                  ),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                  shadowColor: Colors.amberAccent.shade400,
                  elevation: 5,
                ),
                child: Text(
                  "Guardar",
                  style: GoogleFonts.leagueGothic(
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                    color: Colors.black,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildTextField(
    TextEditingController controller,
    String labelText, {
    bool isNumber = false,
  }) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 10),
      child: TextField(
        controller: controller,
        keyboardType: isNumber ? TextInputType.number : TextInputType.text,
        style: GoogleFonts.openSans(color: Colors.amber.shade900, fontSize: 16),
        decoration: InputDecoration(
          labelText: labelText,
          labelStyle: GoogleFonts.openSans(color: Colors.amber.shade900),
          filled: true,
          fillColor: Colors.amber.shade700.withOpacity(0.2),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: BorderSide.none,
          ),
          contentPadding: const EdgeInsets.symmetric(
            horizontal: 16,
            vertical: 14,
          ),
        ),
      ),
    );
  }
}
