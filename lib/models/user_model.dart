class UserModel {
  int? id;
  String name;
  String imagePath;
  int age;
  double height;
  double weight;
  String goals;

  UserModel({
    this.id,
    required this.name,
    required this.imagePath,
    required this.age,
    required this.height,
    required this.weight,
    required this.goals,
  });

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'name': name,
      'imagePath': imagePath,
      'age': age,
      'height': height,
      'weight': weight,
      'goals': goals,
    };
  }

  factory UserModel.fromMap(Map<String, dynamic> map) {
    return UserModel(
      id: map['id'],
      name: map['name'],
      imagePath: map['imagePath'],
      age: map['age'],
      height: map['height'],
      weight: map['weight'],
      goals: map['goals'],
    );
  }
}
