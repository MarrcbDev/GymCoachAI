import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';
import '../models/user_model.dart';

class DatabaseHelper {
  static const String _dbName = "fitness_app.db";
  static const int _dbVersion = 1;
  static const String _tableName = "user";

  static Future<Database> _getDatabase() async {
    final path = join(await getDatabasesPath(), _dbName);
    return openDatabase(
      path,
      version: _dbVersion,
      onCreate: (db, version) async {
        await db.execute('''
          CREATE TABLE $_tableName(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            imagePath TEXT NOT NULL,
            age INTEGER NOT NULL,
            height REAL NOT NULL,
            weight REAL NOT NULL,
            goals TEXT NOT NULL
          )
        ''');
      },
    );
  }

  /// 🔹 **Insertar un usuario**
  static Future<int> insertUser(UserModel user) async {
    final db = await _getDatabase();
    return await db.insert(_tableName, user.toMap());
  }

  /// 🔹 **Obtener un solo usuario**
  static Future<UserModel?> getUser() async {
    final db = await _getDatabase();
    List<Map<String, dynamic>> results = await db.query(_tableName);
    if (results.isNotEmpty) {
      return UserModel.fromMap(results.first);
    }
    return null;
  }

  /// 🔹 **Obtener todos los usuarios (🚀 Nuevo método compatible con home_screen.dart)**
  static Future<List<UserModel>> getUsers() async {
    final db = await _getDatabase();
    List<Map<String, dynamic>> results = await db.query(_tableName);

    return results.isNotEmpty
        ? results.map((map) => UserModel.fromMap(map)).toList()
        : [];
  }

  /// 🔹 **Actualizar un usuario**
  static Future<int> updateUser(UserModel user) async {
    final db = await _getDatabase();
    return await db.update(
      _tableName,
      user.toMap(),
      where: "id = ?",
      whereArgs: [user.id],
    );
  }

  /// 🔹 **Eliminar un usuario**
  static Future<int> deleteUser(int id) async {
    final db = await _getDatabase();
    return await db.delete(_tableName, where: "id = ?", whereArgs: [id]);
  }

  /// 🔹 **Eliminar todos los usuarios**
  static Future<int> deleteAllUsers() async {
    final db = await _getDatabase();
    return await db.delete(_tableName);
  }
}
