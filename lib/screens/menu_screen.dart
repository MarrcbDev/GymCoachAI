import 'package:flutter/material.dart';
import '../models/user_model.dart';
import '../services/database_helper.dart';
import 'edit_profile_screen.dart';
import 'package:google_fonts/google_fonts.dart';
import 'dart:io';

class MenuScreen extends StatefulWidget {
  const MenuScreen({super.key});

  @override
  State<MenuScreen> createState() => _MenuScreenState();
}

class _MenuScreenState extends State<MenuScreen> {
  UserModel? _user;

  @override
  void initState() {
    super.initState();
    _loadUser();
  }

  Future<void> _loadUser() async {
    final user = await DatabaseHelper.getUser();
    setState(() {
      _user = user;
    });
  }

  void _navigateToEditProfile() async {
    final updatedUser = await Navigator.push<UserModel>(
      context,
      MaterialPageRoute(builder: (context) => EditProfileScreen(user: _user!)),
    );

    if (updatedUser != null) {
      setState(() {
        _user = updatedUser;
      });
    }
  }

  double _calculateBMI() {
    if (_user == null || _user!.height == 0) return 0;
    return _user!.weight / (_user!.height * _user!.height);
  }

  String _bmiClassification(double bmi) {
    if (bmi < 18.5) {
      return "Bajo peso";
    } else if (bmi < 24.9) {
      return "Peso normal";
    } else if (bmi < 29.9) {
      return "Sobrepeso";
    } else {
      return "Obesidad";
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: GestureDetector(
        onTap: () => FocusScope.of(context).requestFocus(FocusNode()),
        child: SafeArea(
          child: SingleChildScrollView(
            physics: const BouncingScrollPhysics(),
            child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Center(
                child:
                    _user == null
                        ? Text(
                          'No hay usuario registrado',
                          style: GoogleFonts.openSans(
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                            color: Colors.white,
                          ),
                        )
                        : Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          crossAxisAlignment: CrossAxisAlignment.center,
                          children: [
                            Container(
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                boxShadow: [
                                  BoxShadow(
                                    color: Colors.yellow.shade700.withOpacity(
                                      0.5,
                                    ),
                                    blurRadius: 12,
                                    spreadRadius: 3,
                                  ),
                                ],
                              ),
                              child: CircleAvatar(
                                radius: 60,
                                backgroundImage:
                                    _user!.imagePath.isNotEmpty
                                        ? FileImage(File(_user!.imagePath))
                                        : const AssetImage(
                                              'assets/default_avatar.png',
                                            )
                                            as ImageProvider,
                              ),
                            ),
                            const SizedBox(height: 20),
                            Container(
                              padding: const EdgeInsets.all(16),
                              decoration: BoxDecoration(
                                color: Colors.grey.shade900,
                                borderRadius: BorderRadius.circular(12),
                                boxShadow: [
                                  BoxShadow(
                                    color: Colors.yellow.shade700.withOpacity(
                                      0.6,
                                    ),
                                    blurRadius: 8,
                                    spreadRadius: 2,
                                  ),
                                ],
                              ),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  _buildInfoRow('Nombre', _user!.name),
                                  _buildInfoRow('Edad', '${_user!.age} años'),
                                  _buildInfoRow('Talla', '${_user!.height} m'),
                                  _buildInfoRow('Peso', '${_user!.weight} kg'),
                                  _buildInfoRow('Objetivos', _user!.goals),
                                  const SizedBox(height: 10),
                                  _buildInfoRow(
                                    'IMC',
                                    _calculateBMI().toStringAsFixed(1),
                                  ),
                                  _buildInfoRow(
                                    'Clasificación',
                                    _bmiClassification(_calculateBMI()),
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(height: 20),
                            SizedBox(
                              width: double.infinity,
                              child: ElevatedButton(
                                onPressed:
                                    _user == null
                                        ? null
                                        : _navigateToEditProfile,
                                style: ElevatedButton.styleFrom(
                                  backgroundColor: Colors.amber.shade700,
                                  padding: const EdgeInsets.symmetric(
                                    vertical: 14,
                                  ),
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(10),
                                  ),
                                ),
                                child: Text(
                                  'Editar Perfil',
                                  style: GoogleFonts.leagueGothic(
                                    fontSize: 22,
                                    color: Colors.black,
                                    letterSpacing: 1.2,
                                  ),
                                ),
                              ),
                            ),
                          ],
                        ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildInfoRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Text(
        '$label: $value',
        style: GoogleFonts.openSans(
          fontSize: 18,
          fontWeight: FontWeight.w600,
          color: Colors.white,
        ),
      ),
    );
  }
}
