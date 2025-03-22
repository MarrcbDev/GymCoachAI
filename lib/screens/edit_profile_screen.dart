import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:image_picker/image_picker.dart';
import 'dart:io';
import '../models/user_model.dart';
import '../services/database_helper.dart';

class EditProfileScreen extends StatefulWidget {
  final UserModel user;

  const EditProfileScreen({super.key, required this.user});

  @override
  State<EditProfileScreen> createState() => _EditProfileScreenState();
}

class _EditProfileScreenState extends State<EditProfileScreen> {
  final TextEditingController _nameController = TextEditingController();
  final TextEditingController _ageController = TextEditingController();
  final TextEditingController _heightController = TextEditingController();
  final TextEditingController _weightController = TextEditingController();
  final TextEditingController _goalsController = TextEditingController();
  String? _imagePath;

  @override
  void initState() {
    super.initState();
    _nameController.text = widget.user.name;
    _ageController.text = widget.user.age.toString();
    _heightController.text = widget.user.height.toString();
    _weightController.text = widget.user.weight.toString();
    _goalsController.text = widget.user.goals;
    _imagePath = widget.user.imagePath;
  }

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

  Future<void> _saveProfile() async {
    final updatedUser = UserModel(
      id: widget.user.id,
      name: _nameController.text,
      imagePath: _imagePath ?? widget.user.imagePath,
      age: int.tryParse(_ageController.text) ?? widget.user.age,
      height: double.tryParse(_heightController.text) ?? widget.user.height,
      weight: double.tryParse(_weightController.text) ?? widget.user.weight,
      goals: _goalsController.text,
    );

    await DatabaseHelper.updateUser(updatedUser);

    if (mounted) {
      Navigator.pop(context, updatedUser);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        title: Text(
          'Editar Perfil',
          style: GoogleFonts.leagueGothic(
            color: Colors.yellow.shade700,
            fontSize: 28,
            fontWeight: FontWeight.bold,
          ),
        ),
        centerTitle: true,
        backgroundColor: Colors.grey.shade900,
        leading: IconButton(
          icon: Icon(Icons.arrow_back, color: Colors.yellow.shade700),
          onPressed: () {
            FocusScope.of(context).unfocus();
            Navigator.pop(context);
          },
        ),
        elevation: 5,
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
          padding: const EdgeInsets.all(16.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              GestureDetector(
                onTap: _pickImage,
                child: Container(
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    boxShadow: [
                      BoxShadow(
                        color: Colors.yellow.shade700.withOpacity(0.6),
                        blurRadius: 10,
                        spreadRadius: 3,
                      ),
                    ],
                  ),
                  child: CircleAvatar(
                    radius: 60,
                    backgroundImage:
                        _imagePath != null
                            ? FileImage(File(_imagePath!))
                            : null,
                    child:
                        _imagePath == null
                            ? Icon(
                              Icons.camera_alt,
                              color: Colors.yellow.shade700,
                              size: 35,
                            )
                            : null,
                  ),
                ),
              ),
              const SizedBox(height: 20),
              _buildTextField(_nameController, 'Nombre'),
              _buildTextField(
                _ageController,
                'Edad',
                keyboardType: TextInputType.number,
              ),
              _buildTextField(
                _heightController,
                'Talla (m)',
                keyboardType: TextInputType.number,
              ),
              _buildTextField(
                _weightController,
                'Peso (kg)',
                keyboardType: TextInputType.number,
              ),
              _buildTextField(_goalsController, 'Objetivos'),
              const SizedBox(height: 20),
              ElevatedButton(
                onPressed: _saveProfile,
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.amber.shade700,
                  padding: const EdgeInsets.symmetric(
                    vertical: 15,
                    horizontal: 30,
                  ),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10),
                  ),
                ),
                child: Text(
                  'Guardar Cambios',
                  style: GoogleFonts.openSans(
                    color: Colors.black,
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
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
    String label, {
    TextInputType? keyboardType,
  }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: TextField(
        controller: controller,
        keyboardType: keyboardType,
        decoration: InputDecoration(
          labelText: label,
          labelStyle: GoogleFonts.openSans(color: Colors.yellow.shade700),
          filled: true,
          fillColor: Colors.grey.shade900,
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: BorderSide.none,
          ),
        ),
        style: GoogleFonts.openSans(color: Colors.white, fontSize: 16),
      ),
    );
  }
}
