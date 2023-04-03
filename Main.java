package mealplanner;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.util.*;

public class Main {
  static Scanner scanner = new Scanner(System.in);
  static List<Meal> allMeals = new ArrayList<>();
  final String DB_URL = "jdbc:postgresql://localhost:5432/meals_db";
  final String USER = "postgres";
  final String PASS = "1111";
  Connection connection;


  public static void main(String[] args) throws SQLException {
    Main main = new Main();
    try {
      main.connection = DriverManager.getConnection(main.DB_URL, main.USER, main.PASS);
      main.connection.setAutoCommit(true);
      main.executeSetup();
      main.showMenu(main.connection);
    } catch (SQLException e) {
      e.printStackTrace();
    } finally {
      main.connection.close();
    }
  }

  private void executeSetup() throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate("CREATE table if not exists meals (" +
              "meal_id integer," +
              "meal varchar(1024) NOT NULL," +
              "category varchar(1024) NOT NULL" +
              ")");

      statement.executeUpdate("CREATE table if not exists ingredients (" +
              "meal_id integer," +
              "ingredient_id integer," +
              "ingredient varchar(1024) NOT NULL" +
              ")");

      statement.executeUpdate("CREATE table if not exists plan (" +
              "day_of_the_week varchar(1024) UNIQUE," +
              "breakfast_meal_id integer," +
              "lunch_meal_id integer," +
              "dinner_meal_id integer" +
              ")");

      statement.executeUpdate("INSERT into plan values ('Monday'),('Tuesday'),('Wednesday'),('Thursday'),('Friday'),('Saturday'),('Sunday') ON CONFLICT DO NOTHING;");

      ResultSet mealsResultSets = statement.executeQuery("select * from meals");
      // Read the result set
      while (mealsResultSets.next()) {
        int mealId = mealsResultSets.getInt("meal_id");
        String meal = mealsResultSets.getString("meal");
        String category = mealsResultSets.getString("category");

        allMeals.add(new Meal(mealId, category, meal));
      }

      ResultSet ingredientsResultSets = statement.executeQuery("select * from ingredients");
      // Read the result set
      while (ingredientsResultSets.next()) {
        int mealId = ingredientsResultSets.getInt("meal_id");
        int ingredientId = ingredientsResultSets.getInt("ingredient_id");
        String ingredient = ingredientsResultSets.getString("ingredient");

        Meal correspondingMeal = findMeal(mealId);
        assert correspondingMeal != null;
        correspondingMeal.ingredients.add(new Meal.Ingredient(mealId, ingredientId, ingredient));
      }
    }

  }

  private Meal findMeal(int mealId) {
    for (Meal iterateMeals: allMeals
    ) {
      if (iterateMeals.mealId == mealId) {
        return iterateMeals;
      }
    }
    return null;
  }

  private void showMenu(Connection connection) {
    while (true) {
      System.out.println("What would you like to do (add, show, plan, save, exit)?");
      String decision = scanner.nextLine();
      switch (decision) {
        case "add" -> add(connection);
        case "show" -> show();
        case "plan" -> plan(connection);
        case "save" -> save(connection);
        case "exit" -> {
          System.out.println("Bye!");
          System.exit(100);
        }
        default -> showMenu(connection);
      }
    }
  }

  private void save(Connection connection)  {
    try {
      Statement statement = connection.createStatement();
      ResultSet planResultSets = statement.executeQuery("SELECT count(breakfast_meal_id) AS count FROM plan");
      planResultSets.next();
      // Read the result set
      if (planResultSets.getInt("count") == 0) {
        System.out.println("Unable to save. Plan your meals first.");
      } else {
        System.out.println("Input a filename:");
        String fileName = scanner.nextLine();

        // first get distinct meals from plan
        ResultSet plannedMealsSet = statement.executeQuery("SELECT breakfast_meal_id as meal_id, count(breakfast_meal_id)\n" +
                "FROM plan\n" +
                "group by breakfast_meal_id\n" +
                "union all\n" +
                "SELECT lunch_meal_id as meal, count(lunch_meal_id)\n" +
                "FROM plan\n" +
                "group by lunch_meal_id\n" +
                "union all\n" +
                "SELECT dinner_meal_id as meal, count(dinner_meal_id)\n" +
                "FROM plan\n" +
                "group by dinner_meal_id");
        Map<Meal, Integer> plannedMeals = new HashMap<>();
        while (plannedMealsSet.next()) {
          int mealId = plannedMealsSet.getInt("meal_id");
          int count = plannedMealsSet.getInt("count");

          plannedMeals.put(findMeal(mealId), count);
        }
        Map<String, Integer> ingredientRepeats = new HashMap<>();
        for (Meal meal: plannedMeals.keySet()
        ) {
          for (Meal.Ingredient ingredient : meal.ingredients
               ) {
            if (ingredientRepeats.containsKey(ingredient.ingredient)) {
              ingredientRepeats.put(ingredient.ingredient, ingredientRepeats.get(ingredient.ingredient) + plannedMeals.get(meal));
            } else {
              ingredientRepeats.put(ingredient.ingredient, plannedMeals.get(meal));
            }
          }
        }
        try(FileWriter fileWriter = new FileWriter(fileName)) {
          for (String ingredient: ingredientRepeats.keySet()
          ) {
            int ingredientTimes = ingredientRepeats.get(ingredient);
            if (ingredientTimes == 1) {
              fileWriter.append(ingredient).append("\n");
            } else {
              fileWriter.append(ingredient).append(" x").append(String.valueOf(ingredientTimes)).append("\n");
            }
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
        System.out.println("Saved!");
        // then print and save map
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  private void plan(Connection connection) {
    List<String> days = Arrays.asList("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday");
    for (String day: days
    ) {
      System.out.println(day);
      try {
        Statement statement = connection.createStatement();
        int breakfastId = askForMeal("breakfast", day);
        int lunchId = askForMeal("lunch", day);
        int dinnerId = askForMeal("dinner", day);

        System.out.println("Yeah! We planned the meals for " + day + ".");

        statement.executeUpdate("update plan set breakfast_meal_id = " + breakfastId +
                ", lunch_meal_id = " + lunchId +
                ", dinner_meal_id = " + dinnerId +
                " where day_of_the_week = '" + day + "';");
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    printPlan(connection);
  }

  private void printPlan(Connection connection) {
    List<String> days = Arrays.asList("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday");
    for (String day: days
    ) {
      System.out.println("\n" + day);
      try {
        Statement statement = connection.createStatement();
        ResultSet mealsResultSets = statement.executeQuery("select breakfast_meal_id, lunch_meal_id, dinner_meal_id from plan " +
                "where day_of_the_week = '" + day + "'");
        // Read the result set
        while (mealsResultSets.next()) {
          int breakfastId = mealsResultSets.getInt("breakfast_meal_id");
          int lunchId = mealsResultSets.getInt("lunch_meal_id");
          int dinnerId = mealsResultSets.getInt("dinner_meal_id");

          printMeal("Breakfast", breakfastId);
          printMeal("Lunch", lunchId);
          printMeal("Dinner", dinnerId);
        }
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
  }

  private void printMeal(String category, int mealId) {
    String mealName = "";
    for (Meal meal : allMeals
    ) {
      if (meal.mealId == mealId) {
        mealName = meal.name;
        break;
      }
    }
    System.out.println(category + ": " + mealName);
  }

  private int askForMeal(String category, String day) {
    Map<String, Meal> meals = new HashMap<>();
    for (Meal meal: allMeals
    ) {
      if (meal.category.equals(category)) {
        meals.put(meal.name, meal);
      }
    }
    // Copy all data from hashMap into TreeMap
    TreeMap<String, Meal> sorted = new TreeMap<>(meals);
    // Display the TreeMap which is naturally sorted
    for (Map.Entry<String, Meal> entry : sorted.entrySet()) {
      System.out.println(entry.getValue().name);
    }
    System.out.println("Choose the " + category + " for " + day + " from the list above:");
    return  meals.get(verifyMeal(meals)).mealId;
  }

  private String verifyMeal(Map<String, Meal> meals) {
    String usersInput = scanner.nextLine();
    if (meals.containsKey(usersInput)) {
      return usersInput;
    } else {
      System.out.println("This meal doesn’t exist. Choose a meal from the list above.");
      return verifyMeal(meals);
    }
  }

  private void show() {
    System.out.println("Which category do you want to print (breakfast, lunch, dinner)?");
    String category = scanner.nextLine();
    findMeals(category);
  }

  private void findMeals(String category) {
    List<Meal> foundMeals = new ArrayList<>();
    String verifiedCategory = verifyCategory(category);
    for (Meal meal: allMeals) {
      if (meal.category.equals(verifiedCategory)) {
        foundMeals.add(meal);
      }
    }
    if (foundMeals.size() > 0) {
      System.out.println("Category: " + verifiedCategory);
      printAllMeals(foundMeals);
    } else {
      System.out.println("No meals found.");
    }
  }

  private void printAllMeals(List<Meal> meals) {
    for (Meal meal: meals
    ) {
      System.out.println("\nName: " + meal.name);
      System.out.println("Ingredients:");
      for (Meal.Ingredient ingredient: meal.ingredients
      ) {
        System.out.println(ingredient.ingredient);
      }
      System.out.println();
    }
  }

  private void add(Connection connection) {
    System.out.println("Which meal do you want to add (breakfast, lunch, dinner)?");
    String category = verifyCategory(scanner.nextLine());
    System.out.println("Input the meal's name:");
    String name = verifyName(scanner.nextLine());
    System.out.println("Input the ingredients:");
    String allIngredients = scanner.nextLine().trim();
    String[] ingredients = verifyIngredients(allIngredients.split(","));
    Meal meal = new Meal(category, name, ingredients);
    allMeals.add(meal);
    try (Statement statement = connection.createStatement()){
      statement.executeUpdate("insert into meals (meal_id, meal, category) values (" +
              meal.mealId + ", " +
              "'" + meal.name + "', " +
              "'" + meal.category + "')");
      for (Meal.Ingredient ingredient: meal.ingredients
      ) {
        statement.executeUpdate("insert into ingredients (meal_id, ingredient_id, ingredient) values (" +
                ingredient.mealId + ", " +
                ingredient.ingredientId + ", " +
                "'" + ingredient.ingredient + "')");
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    System.out.println("The meal has been added!");
  }

  private String verifyName(String name) {
    if (checkNameRegex(name)) {
      return name;
    } else {
      System.out.println("Wrong format. Use letters only!");
      return verifyName(scanner.nextLine());
    }
  }

  private String[] verifyIngredients(String[] ingredients) {
    for (String ingredient: ingredients
    ) {
      ingredient = ingredient.trim();
      if (!checkNameRegex(ingredient)) {
        System.out.println("Wrong format. Use letters only!");
        return verifyIngredients(scanner.nextLine().trim().split(", "));
      }
    }
    return ingredients;
  }

  private String verifyCategory(String category) {
    if (category.equals("breakfast") | category.equals("lunch") | category.equals("dinner")) {
      return category;
    } else {
      System.out.println("Wrong meal category! Choose from: breakfast, lunch, dinner.");
      return verifyCategory(scanner.nextLine());
    }
  }
  private boolean checkNameRegex(String name) {
    return name.matches("^([A-Za-z]+) ?([A-Za-z]+)*");
  }
}