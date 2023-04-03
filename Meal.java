package mealplanner;

import java.util.ArrayList;
import java.util.List;

public class Meal {
    int mealId;
    String category;
    String name;
    List<Ingredient> ingredients;

    public Meal(String category, String name, String[] ingredients) {
        this.mealId = generateId();
        this.category = category;
        this.name = name;
        this.ingredients = new ArrayList<>();
        for (String ingredient : ingredients) {
            this.ingredients.add(new Ingredient(this.mealId, generateId(), ingredient));
        }
    }
    public Meal(int mealId, String category, String name) {
        this.mealId = mealId;
        this.category = category;
        this.name = name;
        this.ingredients = new ArrayList<>();
    }

    static class Ingredient {
        int mealId;
        int ingredientId;
        String ingredient;

        public Ingredient(int mealId, int ingredientId, String ingredient) {
            this.mealId = mealId;
            this.ingredientId = ingredientId;
            this.ingredient = ingredient;
        }
    }

    public int generateId() {
        return (int)(Math.random()*10000);
    }
}
