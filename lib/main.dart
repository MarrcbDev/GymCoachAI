import 'package:flutter/material.dart';
import 'package:connectivity_plus/connectivity_plus.dart';
import 'models/user_model.dart';
import 'services/database_helper.dart';
import 'screens/home_screen.dart';
import 'screens/registration_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  var connectivityResult = await Connectivity().checkConnectivity();
  if (connectivityResult == ConnectivityResult.none) {
    runApp(const NoInternetScreen());
  } else {
    UserModel? user = await DatabaseHelper.getUser();
    runApp(GymCoachApp(userExists: user != null));
  }
}

class GymCoachApp extends StatelessWidget {
  final bool userExists;
  const GymCoachApp({super.key, required this.userExists});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        primaryColor: Colors.red.shade800,
        scaffoldBackgroundColor: Colors.white,
      ),
      home: userExists ? const HomeScreen() : RegistrationScreen(),
    );
  }
}

class NoInternetScreen extends StatelessWidget {
  const NoInternetScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      home: Scaffold(
        backgroundColor: Colors.black,
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.wifi_off, size: 80, color: Colors.red.shade800),
              SizedBox(height: 20),
              Text(
                "No hay conexión a Internet",
                style: TextStyle(color: Colors.white, fontSize: 20),
              ),
              SizedBox(height: 10),
              Text(
                "Verifica tu conexión e intenta de nuevo.",
                style: TextStyle(color: Colors.white70, fontSize: 16),
                textAlign: TextAlign.center,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
