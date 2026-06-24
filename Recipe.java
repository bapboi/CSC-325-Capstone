import java.util.List;

public class Recipe {

    private String recipeName;
    private List<Ingredient> ingredients;
    private String instructions;

    public Recipe() {
    }

    public Recipe(String recipeName, List<Ingredient> ingredients, String instructions) {
        this.recipeName = recipeName;
        this.ingredients = ingredients;
        this.instructions = instructions;
    }
}
