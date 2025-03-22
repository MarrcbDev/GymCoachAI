import 'package:flutter/material.dart';
import 'chat_screen.dart';
import 'menu_screen.dart';
import 'package:google_fonts/google_fonts.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _selectedIndex = 0;
  final ChatScreen _chatScreen = const ChatScreen();
  final MenuScreen _menuScreen = const MenuScreen();

  void _onItemTapped(int index) {
    setState(() {
      _selectedIndex = index;
    });

    // Cerrar el teclado si no estamos en ChatScreen
    if (index != 0) {
      FocusManager.instance.primaryFocus?.unfocus();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Column(
        children: [
          AppBar(
            backgroundColor: Colors.grey.shade900,
            title: Text(
              'Coach Inteligente',
              style: GoogleFonts.leagueGothic(
                fontSize: 28,
                fontWeight: FontWeight.bold,
                color: Colors.yellow.shade700,
                letterSpacing: 1.5,
              ),
            ),
            centerTitle: false,
            elevation: 5,
            shadowColor: Colors.black.withOpacity(0.5),
          ),
          Expanded(
            child: IndexedStack(
              index: _selectedIndex,
              children: [_chatScreen, _menuScreen],
            ),
          ),
        ],
      ),
      bottomNavigationBar: Container(
        decoration: BoxDecoration(
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.8),
              blurRadius: 10,
              spreadRadius: 2,
            ),
          ],
        ),
        child: ClipRRect(
          borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
          child: BottomNavigationBar(
            items: const <BottomNavigationBarItem>[
              BottomNavigationBarItem(icon: Icon(Icons.chat), label: ''),
              BottomNavigationBarItem(icon: Icon(Icons.menu), label: ''),
            ],
            currentIndex: _selectedIndex,
            selectedItemColor: Colors.yellow.shade700,
            unselectedItemColor: Colors.grey.shade500,
            backgroundColor: Colors.grey.shade900,
            onTap: _onItemTapped,
            showSelectedLabels: false,
            showUnselectedLabels: false,
            iconSize: 30,
            elevation: 0,
          ),
        ),
      ),
    );
  }
}
